package org.cda.archi.githubworkspace.views;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/** Resizable modal Data Manager window using native SWT and JFace viewers. */
public final class DataManagerDialog extends TitleAreaDialog {
    public interface Listener {
        void refresh();
        void selectTable(TableSchema schema);
        void createTable(TableSchema schema);
        void updateTableSchema(TableSchema original, TableSchema updated);
        void createRecord(TableSchema schema, java.util.Map<String, String> values);
        void updateRecord(TableSchema schema, DataRecord original, java.util.Map<String, String> values);
        void deleteRecord(TableSchema schema, DataRecord record);
        void closed();
    }

    private Listener listener;
    private TableViewer tables;
    private TableViewer records;
    private TableViewer schemaColumns;
    private Text searchTables;
    private Text searchRecords;
    private Label tableDescription;
    private Label status;
    private Label count;
    private ToolItem newRecord;
    private ToolItem editRecord;
    private ToolItem deleteRecord;
    private ToolItem schemaButton;
    private ToolItem editSchemaButton;
    private ToolItem refreshRecords;
    private Button refreshTables;
    private Button newTable;
    private Image windowIcon;
    private TableSchema selectedSchema;
    private List<TableSchema> allSchemas = List.of();
    private List<DataRecord> allRecords = List.of();
    private boolean publicDataRepository;
    private boolean busyState;

    public DataManagerDialog(Shell parent) {
        super(parent);
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
        setBlockOnOpen(true);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void centerOnParent() {
        Shell shell = getShell();
        Shell parent = shell == null || shell.isDisposed() || !(shell.getParent() instanceof Shell owner)
                ? null : owner;
        if (parent == null || parent.isDisposed()) return;
        Rectangle bounds = parent.getBounds();
        var size = shell.getSize();
        shell.setLocation(bounds.x + (bounds.width - size.x) / 2, bounds.y + (bounds.height - size.y) / 2);
    }

    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("GitHub Workspace");
        try (InputStream stream = DataManagerDialog.class.getResourceAsStream("/icons/github.png")) {
            if (stream != null) windowIcon = new Image(shell.getDisplay(), stream);
        } catch (IOException ignored) { }
        if (windowIcon != null) shell.setImage(windowIcon);
        shell.addListener(SWT.Dispose, event -> {
            if (windowIcon != null && !windowIcon.isDisposed()) windowIcon.dispose();
            windowIcon = null;
            if (listener != null) listener.closed();
        });
    }

    @Override protected Control createDialogArea(Composite parent) {
        setTitle("Data Manager");
        setMessage("Manage table schemas and JSON records in the configured GitHub repository.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite root = new Composite(area, SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));
        status = new Label(root, SWT.WRAP);
        status.setText("Loading tables from the configured data repository...");
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        SashForm sash = new SashForm(root, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createLeft(sash);
        createRight(sash);
        sash.setWeights(28, 72);
        return area;
    }

    @Override protected Control createButtonBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
        bar.setLayout(layout);

        Button help = new Button(bar, SWT.PUSH);
        Image helpImage = Dialog.getImage(Dialog.DLG_IMG_HELP);
        if (helpImage != null) help.setImage(helpImage);
        else help.setText("?");
        help.setToolTipText("View Log");
        help.getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override public void getName(AccessibleEvent event) { event.result = "View Log"; }
        });
        help.addListener(SWT.Selection, event -> {
            PluginLog.info("Data Manager", "Opened the log viewer.");
            new PluginLogDialog(getShell()).open();
        });
        help.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

        Composite spacer = new Composite(bar, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button close = createButton(bar, IDialogConstants.CLOSE_ID, "Close", true);
        close.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        return bar;
    }

    @Override protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CLOSE_ID) close();
        else super.buttonPressed(buttonId);
    }

    public void showCatalog(List<TableSchema> values, boolean isPrivate, boolean emptyRepository) {
        if (tables == null || tables.getControl().isDisposed()) return;
        publicDataRepository = !isPrivate;
        allSchemas = List.copyOf(values);
        tables.setInput(allSchemas);
        String privacy = isPrivate ? ""
                : " Warning: Data Repository is public; do not store sensitive or personal data.";
        if (allSchemas.isEmpty()) {
            status.setText(emptyRepository
                    ? "The data repository is empty. Use New Table to initialize it with your first table." + privacy
                    : "No tables found. Use New Table to create the first one." + privacy);
        } else {
            status.setText("Loaded " + allSchemas.size() + " table(s)." + privacy);
        }
        rootLayout();
    }

    public void showRecords(TableSchema schema, List<DataRecord> values) {
        if (records == null || records.getControl().isDisposed()) return;
        selectedSchema = schema;
        allRecords = List.copyOf(values);
        records.getTable().removeAll();
        records.getTable().setRedraw(false);
        records.getTable().setRedraw(true);
        rebuildRecordColumns(schema);
        records.setInput(allRecords);
        schemaColumns.setInput(schema.columns());
        if (editRecord != null && !editRecord.isDisposed()) editRecord.setEnabled(false);
        if (deleteRecord != null && !deleteRecord.isDisposed()) deleteRecord.setEnabled(false);
        tableDescription.setText(schema.name() + (schema.description().isBlank()
                ? "" : " — " + schema.description()));
        schemaButton.setEnabled(true);
        editSchemaButton.setEnabled(true);
        updateRecordCount();
        rootLayout();
    }

    public void setBusy(boolean busy, String message) {
        if (status == null || status.isDisposed()) return;
        busyState = busy;
        if (message != null && !message.isBlank()) status.setText(message);
        if (newRecord != null && !newRecord.isDisposed()) newRecord.setEnabled(!busy && selectedSchema != null);
        if (editRecord != null && !editRecord.isDisposed()) editRecord.setEnabled(!busy && selectedRecord() != null);
        if (deleteRecord != null && !deleteRecord.isDisposed()) deleteRecord.setEnabled(!busy && selectedRecord() != null);
        if (schemaButton != null && !schemaButton.isDisposed()) schemaButton.setEnabled(!busy && selectedSchema != null);
        if (editSchemaButton != null && !editSchemaButton.isDisposed()) {
            editSchemaButton.setEnabled(!busy && selectedSchema != null);
        }
        if (refreshRecords != null && !refreshRecords.isDisposed()) refreshRecords.setEnabled(!busy);
        if (refreshTables != null && !refreshTables.isDisposed()) refreshTables.setEnabled(!busy);
        if (newTable != null && !newTable.isDisposed()) newTable.setEnabled(!busy);
        rootLayout();
    }

    public void showError(String message) {
        if (status != null && !status.isDisposed()) {
            status.setText((message == null || message.isBlank() ? "The operation failed." : message)
                    + privacyWarning());
            rootLayout();
        }
    }

    public void showNotice(String message) {
        if (status != null && !status.isDisposed()) {
            status.setText(message + privacyWarning());
            rootLayout();
        }
    }

    private String privacyWarning() {
        return publicDataRepository
                ? " Warning: Data Repository is public; do not store sensitive or personal data." : "";
    }

    private void createLeft(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        panel.setLayout(new GridLayout(1, false));
        Label heading = new Label(panel, SWT.NONE);
        heading.setText("TABLES");
        Composite search = new Composite(panel, SWT.NONE);
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.setLayout(new GridLayout(2, false));
        searchTables = new Text(search, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        searchTables.setMessage("Search tables");
        searchTables.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refreshTables = new Button(search, SWT.PUSH);
        refreshTables.setText("Refresh");
        refreshTables.addListener(SWT.Selection, event -> { if (listener != null) listener.refresh(); });
        tables = new TableViewer(panel, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        tables.setContentProvider(ArrayContentProvider.getInstance());
        tables.getTable().setHeaderVisible(true);
        tables.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tableColumn(tables, "Table", 155, value -> ((TableSchema) value).name());
        tableColumn(tables, "Columns", 70, value -> Integer.toString(((TableSchema) value).columns().size()));
        tables.addFilter(new ViewerFilter() {
            @Override public boolean select(Viewer viewer, Object parentElement, Object element) {
                String needle = searchTables.getText().strip().toLowerCase(java.util.Locale.ROOT);
                TableSchema schema = (TableSchema) element;
                return needle.isEmpty() || schema.name().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || schema.description().toLowerCase(java.util.Locale.ROOT).contains(needle);
            }
        });
        searchTables.addModifyListener(event -> tables.refresh());
        tables.addSelectionChangedListener(event -> {
            Object selected = tables.getStructuredSelection().getFirstElement();
            if (selected instanceof TableSchema schema && listener != null) listener.selectTable(schema);
        });
        newTable = new Button(panel, SWT.PUSH);
        newTable.setText("New Table");
        newTable.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        newTable.addListener(SWT.Selection, event -> new CreateTableDialog(getShell(),
                allSchemas.stream().map(TableSchema::name).collect(java.util.stream.Collectors.toSet()),
                schema -> { if (listener != null) listener.createTable(schema); }).open());
    }

    private void createRight(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        panel.setLayout(new GridLayout(1, false));
        tableDescription = new Label(panel, SWT.WRAP);
        tableDescription.setText("Select a table to view its records.");
        tableDescription.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        TabFolder tabs = new TabFolder(panel, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        TabItem recordsTab = new TabItem(tabs, SWT.NONE);
        recordsTab.setText("Records");
        Composite recordPanel = new Composite(tabs, SWT.NONE);
        recordPanel.setLayout(new GridLayout(1, false));
        recordsTab.setControl(recordPanel);

        TabItem schemaTab = new TabItem(tabs, SWT.NONE);
        schemaTab.setText("Schema");
        Composite schemaPanel = new Composite(tabs, SWT.NONE);
        schemaPanel.setLayout(new GridLayout(1, false));
        schemaTab.setControl(schemaPanel);

        Label schemaIntro = new Label(schemaPanel, SWT.WRAP);
        schemaIntro.setText("Review and edit this table's structure. The table name is fixed to its GitHub folder.");
        schemaIntro.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        ToolBar schemaToolbar = new ToolBar(schemaPanel, SWT.FLAT | SWT.RIGHT);
        schemaToolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        schemaButton = toolItem(schemaToolbar, "View Schema JSON", () -> {
            if (selectedSchema != null) new SchemaViewerDialog(getShell(), selectedSchema).open();
        });
        editSchemaButton = toolItem(schemaToolbar, "Edit Schema", () -> {
            TableSchema original = selectedSchema;
            if (original == null) return;
            new CreateTableDialog(getShell(),
                    allSchemas.stream().map(TableSchema::name).collect(java.util.stream.Collectors.toSet()),
                    original, updated -> {
                        if (listener != null) listener.updateTableSchema(original, updated);
                    }).open();
        });

        schemaColumns = new TableViewer(schemaPanel, SWT.BORDER | SWT.FULL_SELECTION);
        schemaColumns.setContentProvider(ArrayContentProvider.getInstance());
        schemaColumns.getTable().setHeaderVisible(true);
        schemaColumns.getTable().setLinesVisible(true);
        schemaColumns.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        schemaColumn(schemaColumns, "Column", 140, value -> value.name());
        schemaColumn(schemaColumns, "Type", 100, value -> value.type().name());
        schemaColumn(schemaColumns, "Required", 80, value -> value.required() ? "Yes" : "No");
        schemaColumn(schemaColumns, "Primary Key", 100, value -> value.primaryKey() ? "Yes" : "No");
        schemaColumn(schemaColumns, "Regex", 220, value -> value.pattern());
        schemaColumn(schemaColumns, "Validation Message", 260, value -> value.validationMessage());

        ToolBar toolbar = new ToolBar(recordPanel, SWT.FLAT | SWT.RIGHT);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        newRecord = toolItem(toolbar, "New Record", () -> {
            if (selectedSchema != null) new RecordEditorDialog(getShell(), selectedSchema, null,
                    values -> { if (listener != null) listener.createRecord(selectedSchema, values); }).open();
        });
        editRecord = toolItem(toolbar, "Edit Record", this::editSelected);
        deleteRecord = toolItem(toolbar, "Delete Record", this::deleteSelected);
        refreshRecords = toolItem(toolbar, "Refresh", () -> {
            if (selectedSchema != null && listener != null) listener.selectTable(selectedSchema);
            else if (listener != null) listener.refresh();
        });
        refreshRecords.setToolTipText("Reload tables and selected records");
        Composite recordOptions = new Composite(recordPanel, SWT.NONE);
        recordOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        recordOptions.setLayout(new GridLayout(2, false));
        searchRecords = new Text(recordOptions, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        searchRecords.setMessage("Search records");
        searchRecords.setLayoutData(new GridData(170, SWT.DEFAULT));
        searchRecords.addModifyListener(event -> filterRecords());
        count = new Label(recordOptions, SWT.NONE);
        count.setText("0 records");
        records = new TableViewer(recordPanel, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        records.setContentProvider(ArrayContentProvider.getInstance());
        records.getTable().setHeaderVisible(true);
        records.getTable().setLinesVisible(true);
        records.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        records.addSelectionChangedListener(event -> {
            DataRecord selected = selectedRecord();
            editRecord.setEnabled(selected != null);
            deleteRecord.setEnabled(selected != null);
        });
        newRecord.setEnabled(false);
        editRecord.setEnabled(false);
        deleteRecord.setEnabled(false);
        schemaButton.setEnabled(false);
        editSchemaButton.setEnabled(false);
    }

    private void rebuildRecordColumns(TableSchema schema) {
        for (var column : records.getTable().getColumns()) column.dispose();
        for (var definition : schema.columns()) {
            TableViewerColumn column = new TableViewerColumn(records, SWT.LEFT);
            column.getColumn().setText(definition.name());
            column.getColumn().setWidth(150);
            column.setLabelProvider(new ColumnLabelProvider() {
                @Override public String getText(Object element) {
                    Object value = ((DataRecord) element).value(definition.name());
                    return value == null ? "" : String.valueOf(value);
                }
            });
        }
    }

    private static void schemaColumn(TableViewer viewer, String title, int width,
            java.util.function.Function<org.cda.archi.githubworkspace.models.ColumnDefinition, String> text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.LEFT);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return text.apply((org.cda.archi.githubworkspace.models.ColumnDefinition) element);
            }
        });
    }

    private void filterRecords() {
        if (selectedSchema == null) return;
        String needle = searchRecords.getText().strip().toLowerCase(java.util.Locale.ROOT);
        List<DataRecord> filtered = new ArrayList<>();
        for (DataRecord record : allRecords) {
            if (needle.isEmpty() || record.values().values().stream()
                    .anyMatch(value -> value != null && String.valueOf(value).toLowerCase(java.util.Locale.ROOT)
                            .contains(needle))) filtered.add(record);
        }
        records.setInput(filtered);
        updateRecordCount(filtered.size());
    }

    private void editSelected() {
        DataRecord record = selectedRecord();
        if (record != null) new RecordEditorDialog(getShell(), selectedSchema, record,
                values -> { if (listener != null) listener.updateRecord(selectedSchema, record, values); }).open();
    }

    private void deleteSelected() {
        DataRecord record = selectedRecord();
        if (record == null) return;
        String key = record.primaryKey(selectedSchema);
        if (MessageDialog.openConfirm(getShell(), "Delete Record",
                "Delete record " + selectedSchema.name() + "/" + key + "? This cannot be undone.")) {
            if (listener != null) listener.deleteRecord(selectedSchema, record);
        }
    }

    private DataRecord selectedRecord() {
        if (records == null || records.getControl().isDisposed()) return null;
        Object value = records.getStructuredSelection().getFirstElement();
        return value instanceof DataRecord record ? record : null;
    }

    private void updateRecordCount() { updateRecordCount(allRecords.size()); }
    private void updateRecordCount(int number) {
        if (count != null && !count.isDisposed()) count.setText(number + (number == 1 ? " record" : " records"));
        if (newRecord != null && !newRecord.isDisposed()) newRecord.setEnabled(!busyState && selectedSchema != null);
    }

    private void rootLayout() {
        if (getShell() != null && !getShell().isDisposed()) getShell().layout(true, true);
    }

    private static Button button(Composite parent, String title, Runnable action) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(title);
        button.addListener(SWT.Selection, event -> action.run());
        return button;
    }

    private static ToolItem toolItem(ToolBar parent, String title, Runnable action) {
        ToolItem item = new ToolItem(parent, SWT.PUSH);
        item.setText(title);
        item.addListener(SWT.Selection, event -> action.run());
        return item;
    }

    private static void tableColumn(TableViewer viewer, String title, int width,
            java.util.function.Function<Object, String> text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.LEFT);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) { return text.apply(element); }
        });
    }
}
