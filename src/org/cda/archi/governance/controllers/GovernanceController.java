package org.cda.archi.governance.controllers;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cda.archi.governance.clients.RestClient;
import org.cda.archi.governance.infrastructure.CancellationToken;
import org.cda.archi.governance.models.AuthenticationStatus;
import org.cda.archi.governance.models.GitConfiguration;
import org.cda.archi.governance.models.GitHubRepository;
import org.cda.archi.governance.models.RepositorySearchResult;
import org.cda.archi.governance.services.GitCredentialService;
import org.cda.archi.governance.services.GitHubApiException;
import org.cda.archi.governance.services.GitHubAuthenticationService;
import org.cda.archi.governance.services.GitHubRepositoryService;
import org.cda.archi.governance.services.GitHubSession;
import org.cda.archi.governance.services.GitService;
import org.cda.archi.governance.services.GovernancePreferences;
import org.cda.archi.governance.views.GovernanceView;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/** Main MVC controller and Eclipse command handler. */
public final class GovernanceController extends AbstractHandler implements GovernanceView.Listener {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Governance GitHub worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AuthenticationController authentication;
    private final RepositoryController repositories;
    private final GovernancePreferences preferences;
    private volatile GovernanceView view;
    private volatile Display display;
    private volatile GitConfiguration gitConfiguration;
    private volatile GitHubSession session;
    private volatile CancellationToken cancellation;
    private volatile Future<?> activeTask;

    public GovernanceController() {
        RestClient client = new RestClient();
        GitService git = new GitService();
        authentication = new AuthenticationController(git,
                new GitHubAuthenticationService(new GitCredentialService(), client));
        repositories = new RepositoryController(new GitHubRepositoryService(client));
        preferences = new GovernancePreferences();
    }

    GovernanceController(AuthenticationController authentication, RepositoryController repositories,
            GovernancePreferences preferences) {
        this.authentication = authentication;
        this.repositories = repositories;
        this.preferences = preferences;
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        display = shell.getDisplay();
        view = new GovernanceView(shell, preferences);
        view.setListener(this);
        view.create();
        view.getShell().setSize(920, 690);
        initialize();
        view.open();
        return null;
    }

    public void initialize() {
        submit("Checking local Git…", token -> {
            GitConfiguration configuration = authentication.inspectGit(token);
            gitConfiguration = configuration;
            ui(() -> view.showGitStatus(configuration));
        });
    }

    @Override
    public void connect(String organization, String topic) {
        submit("Authenticating with the local Git credential helper…", token -> {
            validateSearch(organization, topic);
            savePreferences(organization, topic);
            GitConfiguration configuration = authentication.inspectGit(token);
            gitConfiguration = configuration;
            ui(() -> view.setGitConfiguration(configuration));
            if (!configuration.isAvailable()) throw new IOException(configuration.getMessage());

            closeSession();
            ui(() -> view.setAuthenticationStatus(new AuthenticationStatus(
                    AuthenticationStatus.State.AUTHENTICATING, "Waiting for Git Credential Manager…", null)));
            GitHubSession newSession = authentication.authenticate(configuration, token);
            token.throwIfCancelled();
            session = newSession;
            ui(() -> view.setAuthenticationStatus(new AuthenticationStatus(
                    AuthenticationStatus.State.AUTHENTICATED, "Authenticated with GitHub.", newSession.getUser())));
            searchRepositories(newSession, organization, topic, token);
        });
    }

    @Override
    public void refresh(String organization, String topic) {
        GitHubSession current = session;
        if (current == null) {
            connect(organization, topic);
            return;
        }
        submit("Refreshing authorized repositories…", token -> {
            validateSearch(organization, topic);
            savePreferences(organization, topic);
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
    public void openRepository(GitHubRepository repository) {
        worker.submit(() -> {
            try {
                repositories.openInBrowser(repository);
            } catch (IOException ex) {
                ui(() -> view.showError(ex.getMessage()));
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
        RepositorySearchResult result = repositories.search(authenticated, organization, topic, token);
        token.throwIfCancelled();
        ui(() -> view.showRepositories(result));
    }

    private void validateSearch(String organization, String topic) {
        new org.cda.archi.governance.models.GitHubOrganization(organization);
        GitHubRepositoryService.validateTopic(topic);
    }

    private void savePreferences(String organization, String topic) throws IOException {
        try {
            preferences.saveSearch(organization, topic);
        } catch (org.osgi.service.prefs.BackingStoreException ex) {
            throw new IOException("Could not save the organization and topic preferences.");
        }
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
                ui(() -> view.showCancelled());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ui(() -> view.showCancelled());
            } catch (Exception ex) {
                String messageText = ex.getMessage() == null ? "The operation failed." : ex.getMessage();
                ui(() -> {
                    if (view != null && !view.getShell().isDisposed()) {
                        if (session == null) {
                            view.setAuthenticationStatus(new AuthenticationStatus(
                                    AuthenticationStatus.State.FAILED, messageText, null));
                        }
                        view.showError(messageText);
                    }
                });
            } finally {
                busy.set(false);
                if (cancellation == token) cancellation = null;
                if (activeTask != null && activeTask.isDone()) activeTask = null;
                ui(() -> {
                    if (view != null && !view.getShell().isDisposed()) view.setBusy(false, "");
                });
            }
        });
    }

    private void ui(Runnable update) {
        Display current = display;
        if (current == null || current.isDisposed()) return;
        try {
            current.asyncExec(() -> {
                if (view != null && !view.getShell().isDisposed()) update.run();
            });
        } catch (SWTException ignored) {
            // Archi may dispose the Display while a process or request is completing.
        }
    }

    private void closeSession() {
        GitHubSession current = session;
        session = null;
        if (current != null) current.close();
    }

    @FunctionalInterface
    private interface Operation {
        void run(CancellationToken cancellation) throws Exception;
    }
}
