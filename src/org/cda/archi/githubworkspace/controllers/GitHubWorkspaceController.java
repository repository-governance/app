package org.cda.archi.githubworkspace.controllers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.CatalogWorker;
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
    private final CatalogWorker worker = new CatalogWorker("GitHub Workspace worker");
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
    private volatile AtomicBoolean activeTaskStarted = new AtomicBoolean();
    private volatile long generation;

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
        if (view != null && view.getShell() != null && !view.getShell().isDisposed()) {
            view.getShell().forceActive();
            return null;
        }
        org.eclipse.ui.IWorkbenchWindow workbenchWindow = HandlerUtil.getActiveWorkbenchWindow(event);
        Shell shell = workbenchWindow == null
                ? HandlerUtil.getActiveShell(event) : workbenchWindow.getShell();
        generation++;
        display = shell.getDisplay();
        view = new RepositoryBrowserView(shell);
        view.setListener(this);
        view.create();
        view.getShell().setSize(1060, 760);
        view.centerOnParent();
        initialize();
        view.open();
        return null;
    }

    private void initialize() {
        connect();
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
    public void retryConnection() {
        connect();
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
                    ui(generation, () -> view.showAuthenticationFailure(ex.getMessage()));
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
        if (task != null) {
            task.cancel(true);
            if (!activeTaskStarted.get()) {
                ui(generation, () -> {
                    view.showCancelled();
                    finishOperation(current);
                });
            }
        }
    }

    @Override
    public void cloneRepository(GitHubRepository repository, String planId) {
        long taskGeneration = generation;
        startPlanOperation("Preparing " + planId + "...", token -> {
            Path model = repositories.preparePlan(repository, planId, token,
                    message -> ui(taskGeneration, () -> view.showProgress(message)));
            token.throwIfCancelled();
            openModelOnUi(model, token, taskGeneration);
        });
    }

    @Override
    public void openModel(GitHubRepository repository, String planId) {
        long taskGeneration = generation;
        startPlanOperation("Validating local plan " + planId + "...", token -> {
            Path model = repositories.existingPlanModel(repository, planId, token);
            token.throwIfCancelled();
            openModelOnUi(model, token, taskGeneration);
        });
    }

    private void openModelOnUi(Path model, CancellationToken token, long taskGeneration) {
        ui(taskGeneration, () -> {
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
        long taskGeneration = generation;
        submitWorker(() -> {
            try {
                repositories.openInBrowser(repository);
            } catch (IOException ex) {
                ui(taskGeneration, () -> view.showError(ex.getMessage()));
            }
        });
    }

    @Override
    public void requestLocalPlans(GitHubRepository repository) {
        long taskGeneration = generation;
        submitWorker(() -> {
            try {
                List<String> plans = repositories.localPlans(repository, new CancellationToken());
                ui(taskGeneration, () -> view.showLocalPlans(repository, plans));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                ui(taskGeneration, () -> view.showError(ex.getMessage()));
            }
        });
    }

    @Override
    public void requestThumbnail(GitHubRepository repository) {
        GitHubSession current = session;
        if (current == null || !thumbnailRequests.add(repository.getId())) return;
        long taskGeneration = generation;
        submitWorker(() -> {
            byte[] bytes = new byte[0];
            try {
                bytes = repositories.loadThumbnail(current, repository, new CancellationToken());
                byte[] result = bytes;
                ui(taskGeneration, () -> view.showThumbnail(repository, result));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                java.util.Arrays.fill(bytes, (byte) 0);
            } catch (Exception ex) {
                java.util.Arrays.fill(bytes, (byte) 0);
                ui(taskGeneration, () -> view.showThumbnail(repository, new byte[0]));
            }
        });
    }

    @Override
    public void closed() {
        RepositoryBrowserView closingView = view;
        generation++;
        CancellationToken current = cancellation;
        Future<?> task = activeTask;
        if (current != null) current.cancel();
        if (task != null) task.cancel(true);
        closeSession();
        thumbnailRequests.clear();
        busy.set(false);
        cancellation = null;
        activeTask = null;
        activeTaskStarted = new AtomicBoolean();
        worker.shutdownNow();
        if (view == closingView) {
            view = null;
            display = null;
        }
    }

    private void searchRepositories(GitHubSession authenticated, String organization, String topic,
            CancellationToken token) throws IOException, InterruptedException {
        long taskGeneration = generation;
        thumbnailRequests.clear();
        RepositorySearchResult result = repositories.search(authenticated, organization, topic, token);
        token.throwIfCancelled();
        ui(taskGeneration, () -> {
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
        long taskGeneration = generation;
        AtomicBoolean started = new AtomicBoolean();
        activeTaskStarted = started;
        cancellation = token;
        ui(taskGeneration, () -> view.setBusy(true, message));
        activeTask = submitWorker(() -> {
            started.set(true);
            try {
                operation.run(token);
            } catch (CancellationException ex) {
                ui(taskGeneration, () -> {
                    view.showCancelled();
                    finishOperation(token);
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ui(taskGeneration, () -> {
                    view.showCancelled();
                    finishOperation(token);
                });
            } catch (Exception ex) {
                ui(taskGeneration, () -> {
                    view.showError(userFacingError(ex));
                    finishOperation(token);
                });
            }
        });
    }

    private void submit(String message, Operation operation) {
        if (!busy.compareAndSet(false, true)) return;
        CancellationToken token = new CancellationToken();
        long taskGeneration = generation;
        AtomicBoolean started = new AtomicBoolean();
        activeTaskStarted = started;
        cancellation = token;
        ui(taskGeneration, () -> view.setBusy(true, message));
        activeTask = submitWorker(() -> {
            started.set(true);
            try {
                operation.run(token);
            } catch (CancellationException ex) {
                ui(taskGeneration, view::showCancelled);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ui(taskGeneration, view::showCancelled);
            } catch (Exception ex) {
                String errorMessage = userFacingError(ex);
                ui(taskGeneration, () -> {
                    if (session == null) {
                        view.showAuthenticationFailure(errorMessage);
                    } else {
                        view.showError(errorMessage);
                    }
                });
            } finally {
                if (generation == taskGeneration) {
                    busy.set(false);
                    if (cancellation == token) cancellation = null;
                    activeTask = null;
                    activeTaskStarted = new AtomicBoolean();
                    ui(taskGeneration, () -> view.setBusy(false, ""));
                }
            }
        });
    }

    private void finishOperation(CancellationToken token) {
        if (token == null || cancellation != token) return;
        busy.set(false);
        cancellation = null;
        activeTask = null;
        activeTaskStarted = new AtomicBoolean();
        if (view != null && !view.getShell().isDisposed()) view.setBusy(false, "");
    }

    private void ui(Runnable update) {
        ui(generation, update);
    }

    private void ui(long expectedGeneration, Runnable update) {
        Display current = display;
        RepositoryBrowserView expectedView = view;
        if (current == null || current.isDisposed()) return;
        try {
            current.asyncExec(() -> {
                if (generation == expectedGeneration && view == expectedView
                        && expectedView != null && !expectedView.getShell().isDisposed()) update.run();
            });
        } catch (SWTException ignored) {
            // The Archi workbench can dispose its Display while background work completes.
        }
    }

    private Future<?> submitWorker(Runnable task) {
        return worker.submit(task);
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
