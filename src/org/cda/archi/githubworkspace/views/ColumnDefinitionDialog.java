package org.cda.archi.githubworkspace.views;

import java.util.List;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.models.ColumnDefinition;
import org.cda.archi.githubworkspace.models.DataType;
import org.cda.archi.githubworkspace.services.TableSchemaService;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Small editor for one column row in the table designer. */
final class ColumnDefinitionDialog extends TitleAreaDialog {
    private final ColumnDefinition initial;
    private final Consumer<ColumnDefinition> result;
    private Text name;
    private Combo type;
    private Button primary;
    private Button required;
    private Button regexEnabled;
    private Text pattern;
    private Text validationMessage;

    ColumnDefinitionDialog(Shell parent, ColumnDefinition initial, Consumer<ColumnDefinition> result) {
        super(parent);
        this.initial = initial;
        this.result = result;
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    }

    @Override protected Control createDialogArea(Composite parent) {
        setTitle(initial == null ? "Add Column" : "Edit Column");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(2, false));
        new Label(content, SWT.NONE).setText("Column Name:");
        name = new Text(content, SWT.BORDER);
        name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(content, SWT.NONE).setText("Data Type:");
        type = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        type.setItems(java.util.Arrays.stream(DataType.values())
                .map(TableSchemaService::displayType).toArray(String[]::new));
        type.select(0);
        type.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        primary = new Button(content, SWT.CHECK);
        primary.setText("Primary Key");
        primary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        required = new Button(content, SWT.CHECK);
        required.setText("Required");
        required.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        primary.addListener(SWT.Selection, e -> {
            if (primary.getSelection()) required.setSelection(true);
        });
        regexEnabled = new Button(content, SWT.CHECK);
        regexEnabled.setText("Validate with regular expression");
        regexEnabled.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        new Label(content, SWT.NONE).setText("Regex Pattern:");
        pattern = new Text(content, SWT.BORDER);
        pattern.setMessage("Example: ^[A-Z]{3}-[0-9]{4}$");
        pattern.setToolTipText("Java regular expression; the complete value must match.");
        pattern.setTextLimit(256);
        pattern.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(content, SWT.NONE).setText("Validation Message:");
        validationMessage = new Text(content, SWT.BORDER);
        validationMessage.setMessage("Shown when the value does not match");
        validationMessage.setToolTipText("This message appears next to the field when the regex does not match.");
        validationMessage.setTextLimit(300);
        validationMessage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        type.addListener(SWT.Selection, event -> updateRegexControls());
        regexEnabled.addListener(SWT.Selection, event -> updateRegexControls());
        if (initial != null) {
            name.setText(initial.name());
            type.select(java.util.Arrays.asList(type.getItems())
                    .indexOf(TableSchemaService.displayType(initial.type())));
            primary.setSelection(initial.primaryKey());
            required.setSelection(initial.required());
            pattern.setText(initial.pattern());
            validationMessage.setText(initial.validationMessage());
            regexEnabled.setSelection(!initial.pattern().isEmpty());
        }
        updateRegexControls();
        return area;
    }

    @Override protected void okPressed() {
        try {
            if (type.getSelectionIndex() < 0) throw new IllegalArgumentException("Choose a data type.");
            DataType selected = DataType.parse(type.getItem(type.getSelectionIndex()));
            if (selected == DataType.STRING && regexEnabled.getSelection() && pattern.getText().isEmpty()) {
                throw new IllegalArgumentException("Enter a regular expression pattern or disable regex validation.");
            }
            result.accept(new ColumnDefinition(name.getText().trim(), selected,
                    primary.getSelection(), required.getSelection(),
                    selected == DataType.STRING && regexEnabled.getSelection() ? pattern.getText() : "",
                    selected == DataType.STRING && regexEnabled.getSelection()
                            ? validationMessage.getText() : ""));
            super.okPressed();
        } catch (IllegalArgumentException ex) {
            setErrorMessage(ex.getMessage());
        }
    }

    private void updateRegexControls() {
        if (regexEnabled == null || regexEnabled.isDisposed()) return;
        boolean stringType = type.getSelectionIndex() >= 0
                && DataType.parse(type.getItem(type.getSelectionIndex())) == DataType.STRING;
        regexEnabled.setEnabled(stringType);
        pattern.setEnabled(stringType && regexEnabled.getSelection());
        validationMessage.setEnabled(stringType && regexEnabled.getSelection());
    }
}
