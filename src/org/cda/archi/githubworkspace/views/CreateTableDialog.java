package org.cda.archi.githubworkspace.views;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.models.ColumnDefinition;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/** Runtime table designer. Creation is disabled until the schema is valid. */
public final class CreateTableDialog extends TitleAreaDialog {
    private final BiConsumer<TableSchema, java.util.Map<String, String>> saved;
    private final java.util.Set<String> existingNames;
    private final TableSchema initialSchema;
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private final java.util.Map<String, String> columnOrigins = new java.util.LinkedHashMap<>();
    private Text name;
    private Text description;
    private Table grid;
    private Button create;

    public CreateTableDialog(Shell parent, java.util.Set<String> existingNames, Consumer<TableSchema> created) {
        this(parent, existingNames, null, (schema, ignored) -> created.accept(schema));
    }

    public CreateTableDialog(Shell parent, java.util.Set<String> existingNames, TableSchema initialSchema,
            BiConsumer<TableSchema, java.util.Map<String, String>> saved) {
        super(parent);
        this.saved = saved;
        this.initialSchema = initialSchema;
        this.existingNames = existingNames.stream().map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (initialSchema != null) {
            columns.addAll(initialSchema.columns());
            initialSchema.columns().forEach(column -> columnOrigins.put(column.name(), column.name()));
        }
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
        setBlockOnOpen(true);
    }

    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("GitHub Workspace");
    }

    @Override protected Control createDialogArea(Composite parent) {
        setTitle(initialSchema == null ? "Create Table" : "Edit Table Schema");
        setMessage(initialSchema == null
                ? "Define a table schema. Exactly one String, Integer, or UUID primary key is required."
                : "Update the schema. Existing records must remain compatible. Renames migrate their stored values.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(1, false));
        Group properties = new Group(content, SWT.NONE);
        properties.setText("Table Properties");
        properties.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        properties.setLayout(new GridLayout(2, false));
        new Label(properties, SWT.NONE).setText("Table Name:");
        name = new Text(properties, SWT.BORDER);
        name.setMessage("architects");
        if (initialSchema != null) name.setText(initialSchema.name());
        name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(properties, SWT.NONE).setText("Description:");
        description = new Text(properties, SWT.BORDER);
        if (initialSchema != null) description.setText(initialSchema.description());
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Group columnsGroup = new Group(content, SWT.NONE);
        columnsGroup.setText("Columns");
        columnsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        columnsGroup.setLayout(new GridLayout(2, false));
        grid = new Table(columnsGroup, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        grid.setHeaderVisible(true);
        grid.setLinesVisible(true);
        grid.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (String heading : List.of("Column Name", "Data Type", "Primary Key", "Required")) {
            TableColumn column = new TableColumn(grid, SWT.LEFT);
            column.setText(heading);
            column.setWidth(120);
        }
        Composite buttons = new Composite(columnsGroup, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        buttons.setLayout(new GridLayout(1, true));
        action(buttons, "Add Column", () -> editColumn(-1));
        action(buttons, "Edit", () -> editColumn(grid.getSelectionIndex()));
        action(buttons, "Remove", this::removeColumn);
        action(buttons, "Move Up", () -> move(-1));
        action(buttons, "Move Down", () -> move(1));
        name.addModifyListener(e -> validateForm());
        grid.addListener(SWT.Selection, e -> validateForm());
        redraw();
        return area;
    }

    @Override protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        create = createButton(parent, IDialogConstants.OK_ID,
                initialSchema == null ? "Create Table" : "Save Schema", true);
        validateForm();
    }

    @Override protected void okPressed() {
        try {
            TableSchema schema = new TableSchema("1.0", name.getText().trim(),
                    description.getText().trim(), null, columns);
            saved.accept(schema, renamedColumns());
            super.okPressed();
        } catch (IllegalArgumentException ex) {
            setErrorMessage(ex.getMessage());
        }
    }

    private void editColumn(int index) {
        if (index < -1 || index >= columns.size()) return;
        ColumnDefinition old = index < 0 ? null : columns.get(index);
        String originalColumnName = old == null ? null : columnOrigins.get(old.name());
        new ColumnDefinitionDialog(getShell(), old, value -> {
            if (index < 0) columns.add(value);
            else {
                columnOrigins.remove(old.name());
                columns.set(index, value);
            }
            if (originalColumnName != null) columnOrigins.put(value.name(), originalColumnName);
            redraw();
        }).open();
    }

    private void removeColumn() {
        int index = grid.getSelectionIndex();
        if (index >= 0) {
            columnOrigins.remove(columns.get(index).name());
            columns.remove(index);
            redraw();
        }
    }

    private void move(int direction) {
        int index = grid.getSelectionIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= columns.size()) return;
        java.util.Collections.swap(columns, index, target);
        redraw();
        grid.setSelection(target);
    }

    private void redraw() {
        grid.removeAll();
        for (ColumnDefinition column : columns) {
            TableItem item = new TableItem(grid, SWT.NONE);
            item.setText(new String[] {column.name(),
                    org.cda.archi.githubworkspace.services.TableSchemaService.displayType(column.type()),
                    column.primaryKey() ? "Yes" : "No", column.required() ? "Yes" : "No"});
        }
        validateForm();
    }

    private void validateForm() {
        String error = null;
        try {
            if (name == null || name.isDisposed() || name.getText().isBlank()) {
                throw new IllegalArgumentException("Table Name is required.");
            }
            if (!TableSchema.isSafeTableName(name.getText().trim())) {
                throw new IllegalArgumentException(
                        "Use 1–48 letters, digits, underscores, or hyphens; begin with a letter or underscore.");
            }
            boolean sameExistingName = initialSchema != null
                    && initialSchema.name().equalsIgnoreCase(name.getText().trim());
            if (existingNames.contains(name.getText().trim().toLowerCase(java.util.Locale.ROOT))
                    && !sameExistingName) {
                throw new IllegalArgumentException("A table with this name already exists.");
            }
            new TableSchema("1.0", name.getText().trim(), "", null, columns);
        } catch (IllegalArgumentException ex) {
            error = ex.getMessage();
        }
        if (error == null) {
            java.util.Set<String> unique = new java.util.HashSet<>();
            for (ColumnDefinition column : columns) {
                if (!unique.add(column.name().toLowerCase(java.util.Locale.ROOT))) {
                    error = "Column names must be unique.";
                    break;
                }
            }
        }
        setErrorMessage(error);
        if (create != null && !create.isDisposed()) create.setEnabled(error == null);
    }

    private java.util.Map<String, String> renamedColumns() {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        columnOrigins.forEach((currentName, originalName) -> {
            if (!currentName.equals(originalName)) result.put(originalName, currentName);
        });
        return java.util.Map.copyOf(result);
    }

    private static void action(Composite parent, String title, Runnable task) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(title);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button.addListener(SWT.Selection, event -> task.run());
    }
}
