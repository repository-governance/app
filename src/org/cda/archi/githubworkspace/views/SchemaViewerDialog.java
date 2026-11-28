package org.cda.archi.githubworkspace.views;

import org.cda.archi.githubworkspace.models.TableSchema;
import org.cda.archi.githubworkspace.services.LocalizationService;
import org.cda.archi.githubworkspace.services.TableSchemaService;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Read-only schema inspector. */
public final class SchemaViewerDialog extends TitleAreaDialog {
    private final TableSchema schema;
    public SchemaViewerDialog(Shell parent, TableSchema schema) {
        super(parent); this.schema = schema;
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
    }
    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell); shell.setText(LocalizationService.text("GitHub Workspace"));
    }
    @Override protected Control createDialogArea(Composite parent) {
        setTitle(LocalizationService.format("View Schema — {0}", schema.name()));
        setMessage(schema.description());
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(1, false));
        Label pk = new Label(content, SWT.NONE);
        pk.setText(LocalizationService.format("Primary key: {0}", schema.primaryKey()));
        Text columns = new Text(content, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL);
        columns.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        StringBuilder text = new StringBuilder();
        for (var column : schema.columns()) {
            text.append(column.name()).append(" — ")
                    .append(TableSchemaService.displayType(column.type()))
                    .append(column.primaryKey() ? " — Primary Key" : "")
                    .append(column.required() ? " — Required" : " — Optional").append('\n');
        }
        columns.setText(text.toString());
        return area;
    }
}
