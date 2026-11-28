package org.cda.archi.githubworkspace.views;

import java.util.Map;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.models.DataRecord;
import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.services.LocalizationService;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/** Create/edit dialog with schema-driven controls. */
public final class RecordEditorDialog extends TitleAreaDialog {
    private final TableSchema schema;
    private final DataRecord record;
    private final Consumer<Map<String, String>> saved;
    private DynamicFormRenderer form;
    private org.eclipse.swt.widgets.Button save;

    public RecordEditorDialog(Shell parent, TableSchema schema, DataRecord record,
            Consumer<Map<String, String>> saved) {
        super(parent);
        this.schema = schema;
        this.record = record;
        this.saved = saved;
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE | SWT.MAX);
        setBlockOnOpen(true);
    }

    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(LocalizationService.text("GitHub Workspace"));
    }

    @Override protected Control createDialogArea(Composite parent) {
        setTitle(LocalizationService.text(record == null ? "New Record" : "Edit Record"));
        setMessage(LocalizationService.text(record == null
                ? "Enter values for " + schema.name() + "." : "Primary key cannot be changed."));
        Composite area = (Composite) super.createDialogArea(parent);
        form = new DynamicFormRenderer(area, schema, record == null ? Map.of() : record.values(),
                record != null, this::validityChanged);
        form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        return area;
    }

    @Override protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, LocalizationService.text("Cancel"), false);
        save = createButton(parent, IDialogConstants.OK_ID, LocalizationService.text("Save"), true);
        validityChanged(form != null && form.validate());
    }

    @Override protected void okPressed() {
        if (form == null || !form.validate()) return;
        saved.accept(form.values());
        super.okPressed();
    }

    private void validityChanged(boolean valid) {
        if (save != null && !save.isDisposed()) save.setEnabled(valid);
    }
}
