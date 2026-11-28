package org.cda.archi.githubworkspace.controllers;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.CatalogWorker;
import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositoryTemplateFolder;
import org.cda.archi.githubworkspace.services.GitCredentialService;
import org.cda.archi.githubworkspace.services.GitHubAuthenticationService;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.RepositoryManagementService;
import org.cda.archi.githubworkspace.views.RepositoryManagementView;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/** Coordinates authentication, organization listing, and template-based repository creation. */
public final class RepositoryManagementController extends AbstractHandler
        implements RepositoryManagementView.Listener {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final CatalogWorker worker = new CatalogWorker("GitHub Workspace repository management");
    private final GitService git = new GitService();
    private final PreferencesService preferences = new PreferencesService();
    private final RestClient api = new RestClient();
    private final GitHubAuthenticationService authentication =
            new GitHubAuthenticationService(new GitCredentialService(), api);
    private final RepositoryManagementService management = new RepositoryManagementService(api);
    private volatile RepositoryManagementView view;
    private volatile Display display;
    private volatile GitHubSession session;
    private volatile CancellationToken cancellation;
    private volatile Future<?> activeTask;
    private volatile long generation;

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        if (view != null && view.getShell() != null && !view.getShell().isDisposed()) {
            view.getShell().forceActive();
            PluginLog.info("Repository Management", "Activated the existing management window.");
            return null;
        }
        org.eclipse.ui.IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        Shell parent = window == null ? HandlerUtil.getActiveShell(event) : window.getShell();
        generation++;
        display = parent.getDisplay();
        view = new RepositoryManagementView(parent);
        view.setListener(this);
        view.create();
        view.getShell().setSize(1000, 680);
        view.centerOnParent();
        PluginLog.info("Repository Management", "Opened management for organization "
                + preferences.getOrganization() + ".");
        refresh();
        view.open();
        return null;
    }

    @Override
    public void refresh() {
        submit("Loading repositories tagged with " + PreferencesService.DEFAULT_TOPIC + "...", token -> {
            GitHubSession current = ensureSession(token);
            List<GitHubRepository> repositories = management.listOrganizationRepositories(
                    current, preferences.getOrganization(), token);
            ui(generation, () -> view.showRepositories(repositories, preferences.getOrganization()));
        });
    }

    @Override
    public void requestCreate() {
        long taskGeneration = generation;
        if (!busy.compareAndSet(false, true)) return;
        PluginLog.info("Repository Management", "Loading configured template folders.");
        CancellationToken token = new CancellationToken();
        cancellation = token;
        ui(taskGeneration, () -> view.setBusy(true, "Loading template folders..."));
        activeTask = worker.submit(() -> {
            try {
                GitHubSession current = ensureSession(token);
                List<RepositoryTemplateFolder> folders = management.listTemplateFolders(
                        current, preferences.getTemplateRepository(), token);
                PluginLog.info("Repository Management", "Loaded " + folders.size() + " root template folders.");
                busy.set(false);
                cancellation = null;
                activeTask = null;
                ui(taskGeneration, () -> {
                    view.setBusy(false, "Choose a template folder and repository name.");
                    view.openCreateDialog(folders);
                });
            } catch (CancellationException ex) {
                PluginLog.info("Repository Management", "Template folder loading was cancelled.");
                finish(taskGeneration, token, null, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                PluginLog.warn("Repository Management", "Template folder loading was interrupted.", ex);
                finish(taskGeneration, token, null, null);
            } catch (Exception ex) {
                PluginLog.error("Repository Management", "Could not load template folders.", ex);
                finish(taskGeneration, token, null, message(ex));
            }
        });
    }

    @Override
    public void createFromTemplate(String folder, String name, String description) {
        PluginLog.info("Repository Management", "Creating private repository " + name
                + " from template folder " + folder + ".");
        submit("Preparing repository from template...", token -> {
            GitHubRepository created = management.createFromTemplateFolder(ensureSession(token),
                    preferences.getOrganization(), preferences.getTemplateRepository(), folder,
                    name, description, token,
                    progress -> {
                        PluginLog.info("Repository Management", progress);
                        ui(generation, () -> view.setBusy(true, progress));
                    });
            List<GitHubRepository> repositories = management.listOrganizationRepositories(
                    ensureSession(token), preferences.getOrganization(), token);
            ui(generation, () -> {
                view.showRepositories(repositories, preferences.getOrganization());
                view.showNotice("Created " + created.getFullName() + " from folder " + folder + ".");
            });
            PluginLog.info("Repository Management", "Created " + created.getFullName() + " successfully.");
        });
    }

    @Override
    public void closed() {
        PluginLog.info("Repository Management", "Closed management window.");
        generation++;
        CancellationToken token = cancellation;
        Future<?> task = activeTask;
        if (token != null) token.cancel();
        if (task != null) task.cancel(true);
        closeSession();
        busy.set(false);
        cancellation = null;
        activeTask = null;
        worker.shutdownNow();
        view = null;
        display = null;
    }

    private GitHubSession ensureSession(CancellationToken token) throws IOException, InterruptedException {
        GitHubSession current = session;
        if (current != null) return current;
        GitConfiguration configuration = git.inspect(token);
        if (!configuration.isAvailable()) throw new IOException(configuration.getMessage());
        GitHubSession authenticated = authentication.authenticate(configuration, token);
        token.throwIfCancelled();
        session = authenticated;
        try {
            preferences.saveLastAccount(authenticated.getUser().getLogin());
        } catch (org.osgi.service.prefs.BackingStoreException ex) {
            closeSession();
            throw new IOException("Could not save the authenticated GitHub account.");
        }
        return authenticated;
    }

    private void submit(String message, Operation operation) {
        if (!busy.compareAndSet(false, true)) return;
        PluginLog.info("Repository Management", message);
        long taskGeneration = generation;
        CancellationToken token = new CancellationToken();
        cancellation = token;
        ui(taskGeneration, () -> view.setBusy(true, message));
        activeTask = worker.submit(() -> {
            try {
                operation.run(token);
                finish(taskGeneration, token, null, null);
            } catch (CancellationException ex) {
                PluginLog.info("Repository Management", "Operation cancelled.");
                finish(taskGeneration, token, null, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                PluginLog.warn("Repository Management", "Operation interrupted.", ex);
                finish(taskGeneration, token, null, null);
            } catch (Exception ex) {
                PluginLog.error("Repository Management", "Operation failed.", ex);
                finish(taskGeneration, token, null, message(ex));
            }
        });
    }

    private void finish(long taskGeneration, CancellationToken token, String notice, String error) {
        if (cancellation != token) return;
        busy.set(false);
        cancellation = null;
        activeTask = null;
        ui(taskGeneration, () -> {
            if (error != null) view.showError(error);
            else if (notice != null) view.showNotice(notice);
            view.setBusy(false, error == null ? "" : "Repository management could not complete.");
        });
    }

    private void ui(long expectedGeneration, Runnable update) {
        Display current = display;
        RepositoryManagementView expectedView = view;
        if (current == null || current.isDisposed()) return;
        try {
            current.asyncExec(() -> {
                if (generation == expectedGeneration && view == expectedView && expectedView != null
                        && !expectedView.getShell().isDisposed()) update.run();
            });
        } catch (SWTException ignored) {
            // The Archi workbench can dispose its Display while background work completes.
        }
    }

    private void closeSession() {
        GitHubSession current = session;
        session = null;
        if (current != null) current.close();
    }

    private static String message(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "GitHub Workspace could not complete repository management." : error.getMessage();
    }

    @FunctionalInterface
    private interface Operation {
        void run(CancellationToken cancellation) throws Exception;
    }
}
