package org.cda.archi.githubworkspace.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.infrastructure.CatalogWorker;

/**
 * Headless regression checks for SWT wiring plus executable worker lifecycle tests.
 * Native widget clicks still require manual validation in Archi.
 */
public final class CatalogRegressionTests {
    private CatalogRegressionTests() { }

    public static int run() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        String controller = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/controllers/GitHubWorkspaceController.java"));
        String view = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/views/RepositoryBrowserView.java"));
        String cloneDialog = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/views/CloneRepositoryDialog.java"));
        String preferencesPage = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/views/GitHubWorkspacePreferencePage.java"));
        String managementController = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/controllers/RepositoryManagementController.java"));
        String plugin = Files.readString(root.resolve("plugin.xml"));
        String dataView = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/views/DataManagerDialog.java"));
        String dataController = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/controllers/DataManagerController.java"));
        String columnDialog = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/views/ColumnDefinitionDialog.java"));
        String dynamicForm = Files.readString(root.resolve(
                "src/org/cda/archi/githubworkspace/views/DynamicFormRenderer.java"));
        int assertions = 0;

        assertions += sourceContains(plugin, "name=\"Data Manager...\"",
                "Data Manager is contributed under the existing File menu");
        assertions += sourceContains(plugin,
                "class=\"org.cda.archi.githubworkspace.controllers.DataManagerController\"",
                "Data Manager menu command is handled by its MVC controller");
        assertions += sourceContains(dataView, "new SashForm(root, SWT.HORIZONTAL)",
                "Data Manager uses a resizable two-panel SashForm");
        assertions += sourceContains(dataView, "new TableViewer(panel",
                "Data Manager uses JFace TableViewer for table and record grids");
        assertions += sourceContains(dataView, "new PluginLogDialog(getShell()).open()",
                "Data Manager exposes the shared diagnostic log viewer");
        assertions += sourceContains(dataView, "PluginLog.info(\"Data Manager\", \"Opened the log viewer.\")",
                "Opening the Data Manager log viewer is included in diagnostics");
        assertions += sourceContains(dataView, "recordsTab.setText(\"Records\")",
                "Data Manager separates record maintenance into its own tab");
        assertions += sourceContains(dataView, "schemaTab.setText(\"Schema\")",
                "Data Manager separates schema maintenance into its own tab");
        assertions += sourceContains(dataView, "toolItem(schemaToolbar, \"Edit Schema\"",
                "Schema editing actions are contained in the Schema tab");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/CreateTableDialog.java"),
                "grid.addListener(SWT.Selection, e -> validateForm());\n        redraw();",
                "Opening an existing schema renders its loaded columns in the editor grid");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/CreateTableDialog.java"),
                "Renaming moves the table and its records.",
                "Schema editor explains that renaming moves the table's data");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/clients/GitHubContentsClient.java"),
                "api.patch(refUri, token",
                "Table rename publishes all path changes through one non-forced Git ref update");
        assertions += sourceContains(dataController, "tables.update(owner, repository, branch, original, updated, schemaSha",
                "Schema editing writes against the schema SHA to prevent lost concurrent updates");
        assertions += sourceContains(columnDialog, "Validate with regular expression",
                "Column designer exposes optional String regex validation");
        assertions += sourceContains(dynamicForm, "column.patternFailureMessage()",
                "Dynamic forms display the configured regex failure message");
        assertions += sourceContains(dataController, "ensureSession(token)",
                "Data Manager reuses Git Credential Manager authentication");
        assertions += sourceContains(preferencesPage, "dataRepository = row(dataGroup, \"Data Repository URL:\"",
                "Preferences expose the configurable Data Repository URL");

        assertions += sourceContains(controller, "private void initialize() {\n        connect();",
                "Opening the catalog automatically starts authentication");
        assertions += sourceContains(controller, "String organization = preferences.getOrganization();",
                "Automatic connection reads the persisted organization");
        assertions += sourceContains(controller, "String topic = preferences.getTopic();",
                "Automatic connection reads the persisted topic");
        assertions += sourceContains(controller, "searchRepositories(authenticated, organization, topic, token);",
                "Successful authentication automatically loads repositories");
        assertions += sourceContains(controller, "public void retryConnection() {\n        connect();",
                "Authentication retry reuses the connection flow");
        assertions += sourceContains(view, "status.getState() == AuthenticationStatus.State.FAILED",
                "Retry action is shown only for a failed connection");
        assertions += sourceContains(controller, "view.showAuthenticationFailure(errorMessage);",
                "Authentication errors are presented with an explicit retry state");
        assertions += sourceContains(view, "Could not connect to GitHub. ",
                "Authentication failure message explains the connection problem");
        assertions += sourceContains(view, "listener.retryConnection();",
                "Retry action reaches the controller listener");
        assertions += sourceContains(controller, "GitHubSession current = session;\n        if (current == null) {\n            connect();",
                "Refresh reconnects when no valid session exists");
        assertions += sourceContains(view, "|| !topic.equals(preferences.getTopic())",
                "Preference changes are detected after the preference dialog closes");
        assertions += sourceContains(preferencesPage,
                "organization = row(content, \"Organization URL:\", preferences.getOrganizationUrl());",
                "Organization Preferences accepts a GitHub organization URL");
        assertions += sourceContains(preferencesPage, "topic.setEnabled(false);",
                "Topic by default is locked in Preferences");
        assertions += sourceContains(preferencesPage,
                "new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE)",
                "Git authentication status is displayed in a structured table");
        assertions += sourceContains(view, "if (changed && listener != null) {\n                search.setText(\"\");\n                listener.refresh();",
                "Effective preference changes automatically refresh the catalog");
        assertions += sourceContains(view, "cloneButton.addListener(SWT.Selection",
                "Clone is connected to the SWT selection event");
        assertions += sourceContains(view,
                "new CloneRepositoryDialog(\n                            RepositoryBrowserView.this.getShell(), repository, connectedLogin,",
                "Clone opens the branch workspace dialog with the catalog Shell and selected repository");
        assertions += sourceContains(view, "(selected, plan) -> listener.cloneRepository(selected, plan)",
                "Branch name submission propagates the selected repository to the controller");
        assertions += sourceContains(cloneDialog, "SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE",
                "Branch workspace dialog is explicitly modal");
        assertions += sourceContains(cloneDialog, "setBlockOnOpen(true);",
                "Branch workspace dialog is opened synchronously from its SWT selection handler");
        assertions += sourceContains(cloneDialog, "planId.setMessage(\"D-09999\");",
                "Branch name example remains a hint and is not prefilled");
        assertions += sourceContains(cloneDialog, "new Label(content, SWT.NONE).setText(\"Branch name:\");",
                "The user-facing field identifies the value as a branch name");
        assertions += sourceContains(cloneDialog, "new Text(content, SWT.BORDER | SWT.READ_ONLY)",
                "Solution Architect Code is read-only");
        assertions += sourceContains(cloneDialog,
                "architectCode.setText(githubLogin.isBlank() ? \"Not connected\" : githubLogin);",
                "Solution Architect Code is populated from the authenticated GitHub login");
        assertions += sourceContains(cloneDialog,
                "Create a local branch from the repository's default branch",
                "The branch is described as local and based on the repository's default branch");
        assertions += sourceContains(controller, "public void closed() {\n        PluginLog.info(\"Browse\", \"Closed repository catalog.\");\n"
                        + "        RepositoryBrowserView closingView = view;\n        generation++;",
                "Closing the catalog invalidates callbacks before cancellation");
        assertions += sourceContains(controller, "view.getShell().forceActive();\n            PluginLog.info(\"Browse\", "
                        + "\"Activated the existing repository catalog.\");\n            return null;",
                "Reopening the command focuses the current catalog instead of creating a second controller view");
        assertions += sourceContains(controller,
                "HandlerUtil.getActiveWorkbenchWindow(event);\n        Shell shell = workbenchWindow == null",
                "The catalog is parented to Archi's workbench window");
        assertions += sourceContains(controller, "view.getShell().setSize(1060, 760);\n        view.centerOnParent();",
                "The catalog is centered after its initial size is applied");
        assertions += sourceContains(view, "shell.setLocation(parentBounds.x + (parentBounds.width - size.x) / 2,",
                "The catalog window is centered relative to the parent shell bounds");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/controllers/RepositoryManagementController.java"),
                "management.listOrganizationRepositories(",
                "Management Repositories loads repositories using the fixed default topic filter");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/services/RepositoryManagementService.java"),
                "DEFAULT_TOPIC.equalsIgnoreCase(repositoryTopic.trim())",
                "Management filtering uses the fixed default topic");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/RepositoryManagementView.java"),
                "new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE)",
                "Management Repositories presents the organization list in a grid");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/CreateRepositoryDialog.java"),
                "buildingBlock.setText(\"Building Block\");",
                "Repository creation fields are grouped under Building Block");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/CreateRepositoryDialog.java"),
                "type.setItems(\"AM - Application Module\", \"SD - Service Domain\");",
                "Building Block type offers the required AM and SD choices");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/CreateRepositoryDialog.java"),
                "new Text(buildingBlock, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL)",
                "Building Block description is a multiline text area");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/CreateRepositoryDialog.java"),
                "WorkspaceService.buildingBlockRepositoryName(",
                "Repository name combines validated code, type, and title");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/services/RepositoryManagementService.java"),
                "+ \",\\\"private\\\":true,\\\"auto_init\\\":true}\"",
                "Repositories created from the dialog are always private");
        assertions += sourceContains(controller, "if (current != null) current.cancel();\n        if (task != null) task.cancel(true);",
                "Closing the catalog cancels active work without leaving old callbacks live");
        assertions += sourceContains(controller, "worker.shutdownNow();",
                "Closing the catalog shuts down its worker");
        assertions += sourceContains(root.resolve("src/org/cda/archi/githubworkspace/infrastructure/CatalogWorker.java"),
                "if (executor.isShutdown()) executor = newExecutor();",
                "A reused Eclipse handler obtains a fresh worker after reopening");
        assertions += sourceContains(controller, "if (current != null) current.cancel();\n        if (task != null) {\n            task.cancel(true);",
                "Cancel propagates to both operation token and active Future");
        assertions += sourceContains(view, "protected org.eclipse.swt.widgets.Control createButtonBar(Composite parent)",
                "Repository catalog customizes the bottom button bar");
        assertions += sourceContains(view, "Dialog.getImage(Dialog.DLG_IMG_HELP)",
                "Repository catalog help icon opens the diagnostic log");
        assertions += sourceContains(view, "shell.setText(\"GitHub Workspace\");",
                "Browse dialog title uses the plugin name without repeating the inner heading");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/RepositoryManagementView.java"),
                "shell.setText(\"GitHub Workspace\");",
                "Management dialog title uses the plugin name");
        assertions += sourceContains(view, "cancelButton.setVisible(false);",
                "Cancel is hidden outside a cancelable operation");
        assertions += sourceContains(view, "if (busy) return;\n                if (listener == null)",
                "Clone event is guarded against active work and reports missing controller wiring");
        if (view.contains("connectButton") || view.contains("CLOSE_ID")) {
            throw new AssertionError("Permanent Connect and Close controls must not return.");
        }
        assertions++;
        assertions += sourceContains(view, "new GridLayout(1, false)",
                "Repository catalog uses a single card column");
        assertions += sourceContains(view, "new GridLayout(3, false)",
                "Repository cards use a horizontal three-section layout");
        assertions += sourceContains(view, "Dialog.getImage(Dialog.DLG_IMG_HELP)",
                "Browse Repositories exposes the log through a help icon in the lower-left button bar");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/RepositoryManagementView.java"),
                "Dialog.getImage(Dialog.DLG_IMG_HELP)",
                "Management Repositories exposes the log through a help icon in the lower-left button bar");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/RepositoryManagementView.java"),
                "addColumn(\"Topic\", 150);",
                "Management Repositories displays the topic column");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/RepositoryManagementView.java"),
                "String.join(\", \", repository.getTopics())",
                "Management Repositories displays all topics on each repository row");
        assertions += sourceContains(
                root.resolve("src/org/cda/archi/githubworkspace/views/PluginLogDialog.java"),
                "SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL",
                "View Log displays all persisted trace lines in a scrollable multiline field");
        assertions += sourceContains(view, "String placeholder = \"Preview unavailable\";",
                "Missing thumbnails show a clear, neutral placeholder message");
        if (view.contains("event.gc.drawLine(")) {
            throw new AssertionError("The thumbnail placeholder must not contain hand-drawn scribbles.");
        }
        assertions++;
        assertions += sourceContains(view, "shell.setImage(windowIcon);",
                "The repository catalog window uses the GitHub icon");
        assertions += sourceContains(view, "if (windowIcon != null && !windowIcon.isDisposed()) windowIcon.dispose();",
                "The catalog window icon is disposed with its SWT resources");

        assertions += testWorkerCanBeReopened();
        assertions += testActiveWorkerTaskCanBeCancelled();
        return assertions;
    }

    private static int sourceContains(String source, String text, String description) {
        if (!source.contains(text)) throw new AssertionError(description);
        return 1;
    }

    private static int sourceContains(Path file, String text, String description) throws Exception {
        return sourceContains(Files.readString(file), text, description);
    }

    private static int testWorkerCanBeReopened() throws Exception {
        CatalogWorker worker = new CatalogWorker("GitHub Workspace regression worker");
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        worker.submit(() -> {
            completed.incrementAndGet();
            first.countDown();
        });
        if (!first.await(5, TimeUnit.SECONDS)) throw new AssertionError("First worker task did not complete.");
        worker.shutdownNow();
        CountDownLatch reopened = new CountDownLatch(1);
        worker.submit(() -> {
            completed.incrementAndGet();
            reopened.countDown();
        });
        if (!reopened.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker did not restart after close.");
        worker.shutdownNow();
        if (completed.get() != 2) throw new AssertionError("Worker task count mismatch.");
        return 3;
    }

    private static int testActiveWorkerTaskCanBeCancelled() throws Exception {
        CatalogWorker worker = new CatalogWorker("GitHub Workspace cancellation regression worker");
        CancellationToken cancellation = new CancellationToken();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        java.util.concurrent.Future<?> task = worker.submit(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        if (!started.await(5, TimeUnit.SECONDS)) throw new AssertionError("Cancelable task did not start.");
        cancellation.cancel();
        task.cancel(true);
        if (!interrupted.await(5, TimeUnit.SECONDS)) throw new AssertionError("Active worker was not interrupted.");
        if (!cancellation.isCancelled()) throw new AssertionError("Cancellation token was not cancelled.");
        worker.shutdownNow();
        return 2;
    }
}
