package org.cda.archi.githubworkspace.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.cda.archi.githubworkspace.clients.GitHubContentsClient;
import org.cda.archi.githubworkspace.clients.RestClient;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.CatalogWorker;
import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.repositories.DataRecordRepository;
import org.cda.archi.githubworkspace.repositories.TableSchemaRepository;
import org.cda.archi.githubworkspace.services.DataRecordService;
import org.cda.archi.githubworkspace.services.DataValidationService;
import org.cda.archi.githubworkspace.services.GitCredentialService;
import org.cda.archi.githubworkspace.services.GitHubAuthenticationService;
import org.cda.archi.githubworkspace.services.GitHubSession;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.RepositoryManagementService;
import org.cda.archi.githubworkspace.services.TableSchemaManager;
import org.cda.archi.githubworkspace.services.TableSchemaService;
import org.cda.archi.githubworkspace.views.DataManagerDialog;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/** MVC coordinator for asynchronous Data Manager catalog and record operations. */
public final class DataManagerController extends AbstractHandler implements DataManagerDialog.Listener {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final CatalogWorker worker = new CatalogWorker("GitHub Workspace data manager");
    private final PreferencesService preferences = new PreferencesService();
    private final GitService git = new GitService();
    private final RestClient api = new RestClient();
    private final GitHubAuthenticationService authentication =
            new GitHubAuthenticationService(new GitCredentialService(), api);
    private final TableSchemaService schemaService = new TableSchemaService();
    private final GitHubContentsClient contents = new GitHubContentsClient(api);
    private final TableSchemaManager tables = new TableSchemaManager(new TableSchemaRepository(contents, schemaService));
    private final DataRecordService records =
            new DataRecordService(new DataRecordRepository(contents), new DataValidationService());
    private volatile DataManagerDialog view;
    private volatile Display display;
    private volatile GitHubSession session;
    private volatile CancellationToken cancellation;
    private volatile Future<?> activeTask;
    private volatile long generation;
    private volatile String owner;
    private volatile String repository;
    private volatile String branch = "";
    private volatile boolean emptyRepository;
    private volatile List<TableSchema> currentSchemas = List.of();
    private volatile Map<String, String> schemaShas = Map.of();
    private volatile TableSchema selectedSchema;

    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        if (view != null && view.getShell() != null && !view.getShell().isDisposed()) {
            view.getShell().forceActive();
            return null;
        }
        org.eclipse.ui.IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        Shell parent = window == null ? HandlerUtil.getActiveShell(event) : window.getShell();
        try {
            String url = PreferencesService.normalizeDataRepositoryUrl(preferences.getDataRepository());
            String fullName = RepositoryManagementService.normalizeTemplateRepository(url);
            String[] parts = fullName.split("/", 2);
            owner = parts[0];
            repository = parts[1];
        } catch (IOException ex) {
            throw new ExecutionException(ex.getMessage(), ex);
        }
        PluginLog.info("Data Manager", "Opening Data Manager for " + owner + "/" + repository + ".");
        generation++;
        display = parent.getDisplay();
        view = new DataManagerDialog(parent);
        view.setListener(this);
        view.create();
        view.getShell().setSize(1120, 740);
        view.centerOnParent();
        refresh();
        view.open();
        return null;
    }

    @Override public void refresh() {
        long taskGeneration = generation;
        PluginLog.info("Data Manager", "Refreshing table catalog.");
        submit("Loading Data Repository...", token -> {
            GitHubSession current = ensureSession(token);
            TableSchemaRepository.Catalog catalog = tables.discover(owner, repository, current, token);
            if (generation == taskGeneration) {
                branch = catalog.branch();
                emptyRepository = catalog.emptyRepository();
                currentSchemas = catalog.schemas();
                schemaShas = catalog.schemaShas();
            }
            ui(taskGeneration, () -> view.showCatalog(catalog.schemas(), catalog.isPrivate(), catalog.emptyRepository()));
        });
    }

    @Override public void selectTable(TableSchema schema) {
        selectedSchema = schema;
        long taskGeneration = generation;
        submit("Loading records from " + schema.name() + "...", token -> {
            GitHubSession current = ensureSession(token);
            List<DataRecord> loaded = records.list(owner, repository, branch, schema, current, token);
            ui(taskGeneration, () -> view.showRecords(schema, loaded));
            ui(taskGeneration, () -> view.setBusy(false, loaded.isEmpty()
                    ? "No records yet. Select New Record to add the first record."
                    : "Loaded " + loaded.size() + " record(s) from " + schema.name() + "."));
        });
    }

    @Override public void createTable(TableSchema schema) {
        long taskGeneration = generation;
        submit("Creating table " + schema.name() + "...", token -> {
            tables.create(owner, repository, branch, emptyRepository, schema, currentSchemas, ensureSession(token), token);
            PluginLog.info("Data Manager", "Created table " + schema.name() + ".");
            refreshAfterMutation(schema, taskGeneration, token);
        });
    }

    @Override public void updateTableSchema(TableSchema original, TableSchema updated) {
        long taskGeneration = generation;
        String schemaSha = schemaShas.get(original.name());
        submit("Validating and saving schema " + original.name() + "...", token -> {
            if (!original.name().equalsIgnoreCase(updated.name())
                    && currentSchemas.stream().anyMatch(schema -> schema.name().equalsIgnoreCase(updated.name()))) {
                throw new IOException("A table named " + updated.name() + " already exists.");
            }
            GitHubSession current = ensureSession(token);
            List<DataRecord> existingRecords =
                    records.list(owner, repository, branch, original, current, token);
            tables.update(owner, repository, branch, original, updated, schemaSha,
                    existingRecords, current, token);
            PluginLog.info("Data Manager", "Updated schema " + original.name() + ".");
            TableSchemaRepository.Catalog catalog = tables.discover(owner, repository, current, token);
            TableSchema saved = catalog.schemas().stream()
                    .filter(schema -> schema.name().equals(updated.name())).findFirst()
                    .orElseThrow(() -> new IOException("Updated schema was not returned by GitHub. Refresh and retry."));
            List<DataRecord> refreshedRecords = records.list(
                    owner, repository, catalog.branch(), saved, current, token);
            if (generation == taskGeneration) {
                branch = catalog.branch();
                emptyRepository = catalog.emptyRepository();
                currentSchemas = catalog.schemas();
                schemaShas = catalog.schemaShas();
                selectedSchema = saved;
            }
            ui(taskGeneration, () -> {
                view.showCatalog(catalog.schemas(), catalog.isPrivate(), catalog.emptyRepository());
                view.showRecords(saved, refreshedRecords);
                view.showNotice(original.name().equals(saved.name())
                        ? "Updated schema for table " + saved.name() + "."
                        : "Renamed table " + original.name() + " to " + saved.name() + ".");
            });
        });
    }

    @Override public void createRecord(TableSchema schema, Map<String, String> values) {
        long taskGeneration = generation;
        submit("Creating record...", token -> {
            DataRecord created = records.create(owner, repository, branch, schema, values, ensureSession(token), token);
            PluginLog.info("Data Manager", "Created record in " + schema.name() + ".");
            reloadRecords(schema, taskGeneration, token,
                    "Created record " + schema.name() + "/" + created.primaryKey(schema) + ".");
        });
    }

    @Override public void updateRecord(TableSchema schema, DataRecord original, Map<String, String> values) {
        long taskGeneration = generation;
        submit("Updating record...", token -> {
            DataRecord updated = records.update(owner, repository, branch, schema, original, values,
                    ensureSession(token), token);
            PluginLog.info("Data Manager", "Updated record in " + schema.name() + ".");
            reloadRecords(schema, taskGeneration, token,
                    "Updated record " + schema.name() + "/" + updated.primaryKey(schema) + ".");
        });
    }

    @Override public void deleteRecord(TableSchema schema, DataRecord record) {
        long taskGeneration = generation;
        submit("Deleting record...", token -> {
            records.delete(owner, repository, branch, schema, record, ensureSession(token), token);
            PluginLog.info("Data Manager", "Deleted record from " + schema.name() + ".");
            reloadRecords(schema, taskGeneration, token,
                    "Deleted record " + schema.name() + "/" + record.primaryKey(schema) + ".");
        });
    }

    @Override public void closed() {
        PluginLog.info("Data Manager", "Closed Data Manager.");
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
        selectedSchema = null;
    }

    private void refreshAfterMutation(TableSchema created, long taskGeneration, CancellationToken token) throws Exception {
        TableSchemaRepository.Catalog catalog = tables.discover(owner, repository, ensureSession(token), token);
        if (generation == taskGeneration) {
            branch = catalog.branch();
            emptyRepository = catalog.emptyRepository();
            currentSchemas = catalog.schemas();
            schemaShas = catalog.schemaShas();
        }
        ui(taskGeneration, () -> {
            view.showCatalog(catalog.schemas(), catalog.isPrivate(), catalog.emptyRepository());
            view.showRecords(created, List.of());
            view.showNotice("Created table " + created.name() + ".");
        });
    }

    private void reloadRecords(TableSchema schema, long taskGeneration, CancellationToken token, String message)
            throws Exception {
        List<DataRecord> loaded = records.list(owner, repository, branch, schema, ensureSession(token), token);
        ui(taskGeneration, () -> {
            view.showRecords(schema, loaded);
            view.showNotice(message);
        });
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
        long taskGeneration = generation;
        CancellationToken token = new CancellationToken();
        cancellation = token;
        ui(taskGeneration, () -> view.setBusy(true, message));
        activeTask = worker.submit(() -> {
            try {
                operation.run(token);
                finish(taskGeneration, token, null);
            } catch (CancellationException ex) {
                finish(taskGeneration, token, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                finish(taskGeneration, token, null);
            } catch (Exception ex) {
                PluginLog.error("Data Manager", message, ex);
                finish(taskGeneration, token, userMessage(ex));
            }
        });
    }

    private void finish(long taskGeneration, CancellationToken token, String error) {
        if (generation != taskGeneration) return;
        if (cancellation == token) cancellation = null;
        activeTask = null;
        busy.set(false);
        ui(taskGeneration, () -> {
            if (error != null) {
                view.showError(error);
                view.setBusy(false, error);
            } else {
                view.setBusy(false, null);
            }
        });
    }

    private String userMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "Data Manager operation failed." : message;
    }

    private void closeSession() {
        GitHubSession current = session;
        session = null;
        if (current != null) current.close();
    }

    private void ui(long expected, Runnable action) {
        Display current = display;
        if (current == null || current.isDisposed()) return;
        try {
            current.asyncExec(() -> {
                DataManagerDialog active = view;
                if (generation != expected || active == null || active.getShell() == null
                        || active.getShell().isDisposed()) return;
                try { action.run(); }
                catch (SWTException ex) { PluginLog.warn("Data Manager", "Window closed during an update.", ex); }
            });
        } catch (SWTException ignored) { }
    }

    @FunctionalInterface private interface Operation {
        void run(CancellationToken token) throws Exception;
    }
}
