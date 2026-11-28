package org.cda.archi.githubworkspace.views;

import java.util.List;

import org.cda.archi.githubworkspace.models.RepositoryTemplateFolder;
import org.cda.archi.githubworkspace.services.WorkspaceService;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Captures the template folder and metadata for a new organization repository. */
final class CreateRepositoryDialog extends TitleAreaDialog {
    interface Listener {
        void create(String folder, String name, String description);
    }

    private final List<RepositoryTemplateFolder> folders;
    private final Listener listener;
    private Combo template;
    private Combo type;
    private Text code;
    private Text title;
    private Text description;

    CreateRepositoryDialog(Shell parent, List<RepositoryTemplateFolder> folders, Listener listener) {
        super(parent);
        this.folders = List.copyOf(folders);
        this.listener = listener;
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        setBlockOnOpen(true);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("GitHub Workspace");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Create Repository from Template");
        setMessage("Enter the Building Block details and choose a template.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(1, false));

        Group buildingBlock = new Group(content, SWT.NONE);
        buildingBlock.setText("Building Block");
        buildingBlock.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        buildingBlock.setLayout(new GridLayout(2, false));

        new Label(buildingBlock, SWT.NONE).setText("Código:");
        code = new Text(buildingBlock, SWT.BORDER);
        code.setMessage("SBB-0099999");
        code.setTextLimit(11);
        code.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(buildingBlock, SWT.NONE).setText("Tipo:");
        type = new Combo(buildingBlock, SWT.DROP_DOWN | SWT.READ_ONLY);
        type.setItems("AM - Application Module", "SD - Service Domain");
        type.select(0);
        type.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(buildingBlock, SWT.NONE).setText("Título:");
        title = new Text(buildingBlock, SWT.BORDER);
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        title.addVerifyListener(event -> {
            String candidate = title.getText().substring(0, event.start) + event.text
                    + title.getText().substring(event.end);
            event.doit = candidate.isEmpty() || WorkspaceService.isValidBuildingBlockTitle(candidate);
        });

        new Label(buildingBlock, SWT.NONE).setText("Descripción:");
        description = new Text(buildingBlock, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        description.setTextLimit(350);
        GridData descriptionData = new GridData(SWT.FILL, SWT.FILL, true, true);
        descriptionData.heightHint = 80;
        description.setLayoutData(descriptionData);

        new Label(buildingBlock, SWT.NONE).setText("Plantilla:");
        template = new Combo(buildingBlock, SWT.DROP_DOWN | SWT.READ_ONLY);
        template.setItems(folders.stream().map(RepositoryTemplateFolder::name).toArray(String[]::new));
        if (!folders.isEmpty()) template.select(0);
        template.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        createButton(parent, IDialogConstants.OK_ID, "Create Repository", true).setEnabled(!folders.isEmpty());
    }

    @Override
    protected void okPressed() {
        if (template.getSelectionIndex() < 0) {
            setErrorMessage("Choose a template.");
            return;
        }
        String buildingBlockCode = code.getText().trim().toUpperCase(java.util.Locale.ROOT);
        String buildingBlockType = type.getSelectionIndex() == 0 ? "AM" : "SD";
        String repositoryName;
        try {
            repositoryName = WorkspaceService.buildingBlockRepositoryName(
                    buildingBlockCode, buildingBlockType, title.getText().trim());
        } catch (IllegalArgumentException ex) {
            setErrorMessage(ex.getMessage());
            return;
        }
        listener.create(template.getText(), repositoryName, description.getText().trim());
        super.okPressed();
    }
}
