package org.cda.archi.githubworkspace.controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.models.AuthenticationStatus;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitHubOrganization;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositorySearchResult;
import org.cda.archi.githubworkspace.services.GitCredentialService;
import org.cda.archi.githubworkspace.services.GitHubApiException;
import org.cda.archi.githubworkspace.services.GitHubAuthenticationService;
import org.cda.archi.githubworkspace.services.GitHubRepositoryService;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.WorkspaceService;
import org.cda.archi.githubworkspace.views.RepositoryBrowserView;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.HandlerUtil;

/** MVC controller for repository discovery, plan checkouts and native model opening. */
public final class GitHubWorkspaceController extends AbstractHandler implements RepositoryBrowserView.Listener {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "GitHub Workspace worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> thumbnailRequests = ConcurrentHashMap.newKeySet();
    private final GitService git;
    private final GitHubRepositoryService github;
    private final AuthenticationController authentication;
    private final RepositoryController repositories;
    private final PreferencesService preferences;
    private volatile RepositoryBrowserView view;
    private volatile Display display;
    private volatile GitConfiguration gitConfiguration;
    private volatile GitHubSession session;
    private volatile CancellationToken cancellation;
    private volatile Future<?> activeTask;

    public GitHubWorkspaceController() {
        RestClient client = new RestClient();
        git = new GitService();
        github = new GitHubRepositoryService(client);
        authentication = new AuthenticationController(git,
                new GitHubAuthenticationService(new GitCredentialService(), client));
        preferences = new PreferencesService();
        WorkspaceService workspace = new WorkspaceService(preferences);
        repositories = new RepositoryController(github, git, workspace,
                new org.cda.archi.githubworkspace.services.ArchiModelService());
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        display = shell.getDisplay();
        view = new RepositoryBrowserView(shell);
        view.setListener(this);
        view.create();
        view.getShell().setSize(1060, 760);
        initialize();
        view.open();
        return null;
    }

    private void initialize() {
        worker.submit(() -> {
            GitConfiguration configuration = git.inspect(new CancellationToken());
            gitConfiguration = configuration;
            ui(() -> view.showGitStatus(configuration));
            if (new WorkspaceService(preferences).hasLegacyGovernanceWorkspace()) {
                ui(() -> view.showProgress("Legacy Governance workspace detected. It will remain untouched."));
            }
        });
    }

    @Override
    public void connect() {
        submit("Authenticating with the local Git credential helper...", token -> {
            String organization = preferences.getOrganization();
            String topic = preferences.getTopic();
            validateSearch(organization, topic);
            GitConfiguration configuration = authentication.inspectGit(token);
            gitConfiguration = configuration;
            ui(() -> view.showGitStatus(configuration));
            if (!configuration.isAvailable()) throw new IOException(configuration.getMessage());
            closeSession();
            ui(() -> view.setAuthenticationStatus(new AuthenticationStatus(
                    AuthenticationStatus.State.AUTHENTICATING, "Waiting for Git Credential Manager...", null)));
            GitHubSession authenticated = authentication.authenticate(configuration, token);
            token.throwIfCancelled();
            session = authenticated;
            try {
                preferences.saveLastAccount(authenticated.getUser().getLogin());
            } catch (org.osgi.service.prefs.BackingStoreException ex) {
                throw new IOException("Could not save the last authenticated GitHub account.");
            }
            ui(() -> view.setAuthenticationStatus(new AuthenticationStatus(
                    AuthenticationStatus.State.AUTHENTICATED, "Authenticated with GitHub.", authenticated.getUser())));
            searchRepositories(authenticated, organization, topic, token);
        });
    }

    @Override
    public void refresh() {
        GitHubSession current = session;
        if (current == null) {
            connect();
            return;
        }
        submit("Refreshing authorized repositories...", token -> {
            String organization = preferences.getOrganization();
            String topic = preferences.getTopic();
            validateSearch(organization, topic);
            try {
                searchRepositories(current, organization, topic, token);
            } catch (GitHubApiException ex) {
                if (ex.getStatusCode() == 401) {
                    closeSession();
                    ui(() -> view.setAuthenticationStatus(new AuthenticationStatus(
                            AuthenticationStatus.State.FAILED, ex.getMessage(), null)));
                }
                throw ex;
            }
        });
    }

    @Override
    public void cancel() {
        CancellationToken current = cancellation;
        Future<?> task = activeTask;
        if (current != null) current.cancel();
        if (task != null) task.cancel(true);
    }

    @Override
    public void cloneRepository(GitHubRepository repository, String planId) {
        startPlanOperation("Preparing " + planId + "...", token -> {
            Path model = repositories.preparePlan(repository, planId, token,
                    message -> ui(() -> view.showProgress(message)));
            token.throwIfCancelled();
            openModelOnUi(model, token);
        });
    }

    @Override
    public void openModel(GitHubRepository repository, String planId) {
        startPlanOperation("Validating local plan " + planId + "...", token -> {
            Path model = repositories.existingPlanModel(repository, planId, token);
            token.throwIfCancelled();
            openModelOnUi(model, token);
        });
    }

    private void openModelOnUi(Path model, CancellationToken token) {
        ui(() -> {
            if (token.isCancelled()) {
                finishOperation(token);
                return;
            }
            try {
                view.showProgress("Opening source/design.archimate in Archi...");
                repositories.openModel(model);
                view.closeAfterModelOpened();
            } catch (Exception ex) {
                view.showError(userFacingError(ex));
            } finally {
                finishOperation(token);
            }
        });
    }

    @Override
    public void viewOnGithub(GitHubRepository repository) {
        worker.submit(() -> {
            try {
                repositories.openInBrowser(repository);
            } catch (IOException ex) {
                ui(() -> view.showError(ex.getMessage()));
            }
        });
    }

    @Override
    public void requestLocalPlans(GitHubRepository repository) {
        worker.submit(() -> {
            try {
                List<String> plans = repositories.localPlans(repository, new CancellationToken());
                ui(() -> view.showLocalPlans(repository, plans));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                ui(() -> view.showError(ex.getMessage()));
            }
        });
    }

    @Override
    public void requestThumbnail(GitHubRepository repository) {
        GitHubSession current = session;
        if (current == null || !thumbnailRequests.add(repository.getId())) return;
        worker.submit(() -> {
            byte[] bytes = new byte[0];
            try {
                bytes = repositories.loadThumbnail(current, repository, new CancellationToken());
                byte[] result = bytes;
                ui(() -> view.showThumbnail(repository, result));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                java.util.Arrays.fill(bytes, (byte) 0);
            } catch (Exception ex) {
                java.util.Arrays.fill(bytes, (byte) 0);
                ui(() -> view.showThumbnail(repository, new byte[0]));
            }
        });
    }

    @Override
    public void closed() {
        cancel();
        closeSession();
        worker.shutdownNow();
    }

    private void searchRepositories(GitHubSession authenticated, String organization, String topic,
            CancellationToken token) throws IOException, InterruptedException {
        thumbnailRequests.clear();
        RepositorySearchResult result = repositories.search(authenticated, organization, topic, token);
        token.throwIfCancelled();
        ui(() -> {
            view.showRepositories(result);
            view.setAuthenticationStatus(new AuthenticationStatus(
                    AuthenticationStatus.State.AUTHENTICATED, "Authenticated with GitHub.",
                    authenticated.getUser()));
        });
    }

    private void validateSearch(String organization, String topic) {
        new GitHubOrganization(organization);
        GitHubRepositoryService.validateTopic(topic);
    }

    private void startPlanOperation(String message, Operation operation) {
        if (!busy.compareAndSet(false, true)) return;
        CancellationToken token = new CancellationToken();
        cancellation = token;
        ui(() -> view.setBusy(true, message));
        activeTask = worker.submit(() -> {
            try {
                operation.run(token);
            } catch (CancellationException ex) {
                ui(() -> {
                    view.showCancelled();
                    finishOperation(token);
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ui(() -> {
                    view.showCancelled();
                    finishOperation(token);
                });
            } catch (Exception ex) {
                ui(() -> {
                    view.showError(userFacingError(ex));
                    finishOperation(token);
                });
            }
        });
    }

    private void submit(String message, Operation operation) {
        if (!busy.compareAndSet(false, true)) return;
        CancellationToken token = new CancellationToken();
        cancellation = token;
        ui(() -> view.setBusy(true, message));
        activeTask = worker.submit(() -> {
            try {
                operation.run(token);
            } catch (CancellationException ex) {
                ui(view::showCancelled);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ui(view::showCancelled);
            } catch (Exception ex) {
                String errorMessage = ex.getMessage() == null ? "The operation failed." : ex.getMessage();
                ui(() -> {
                    if (session == null) {
                        view.setAuthenticationStatus(new AuthenticationStatus(
                                AuthenticationStatus.State.FAILED, errorMessage, null));
                    }
                    view.showError(errorMessage);
                });
            } finally {
                busy.set(false);
                if (cancellation == token) cancellation = null;
                activeTask = null;
                ui(() -> view.setBusy(false, ""));
            }
        });
    }

    private void finishOperation(CancellationToken token) {
        busy.set(false);
        if (cancellation == token) cancellation = null;
        activeTask = null;
        if (view != null && !view.getShell().isDisposed()) view.setBusy(false, "");
    }

    private void ui(Runnable update) {
        Display current = display;
        if (current == null || current.isDisposed()) return;
        try {
            current.asyncExec(() -> {
                if (view != null && !view.getShell().isDisposed()) update.run();
            });
        } catch (SWTException ignored) {
            // The Archi workbench can dispose its Display while background work completes.
        }
    }

    private void closeSession() {
        GitHubSession current = session;
        session = null;
        if (current != null) current.close();
        github.clearThumbnailCache();
    }

    private static String userFacingError(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? "GitHub Workspace could not complete the requested operation." : message;
    }

    @FunctionalInterface
    private interface Operation {
        void run(CancellationToken cancellation) throws Exception;
    }
}
