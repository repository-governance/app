package org.cda.archi.githubworkspace.views;

import java.util.List;

import org.cda.archi.githubworkspace.models.RepositoryTemplateFolder;
import org.cda.archi.githubworkspace.services.WorkspaceService;
import org.eclipse.jface.dialogs.IDialogConstants;
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

/** Captures the template folder and metadata for a new organization repository. */
final class CreateRepositoryDialog extends TitleAreaDialog {
    interface Listener {
        void create(String folder, String name, String description, boolean isPrivate);
    }

    private final List<RepositoryTemplateFolder> folders;
    private final Listener listener;
    private Combo folder;
    private Text name;
    private Text description;
    private Button privateRepository;

    CreateRepositoryDialog(Shell parent, List<RepositoryTemplateFolder> folders, Listener listener) {
        super(parent);
        this.folders = List.copyOf(folders);
        this.listener = listener;
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        setBlockOnOpen(true);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Create Repository from Template");
        setMessage("Choose a root folder from the configured template repository.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(2, false));

        new Label(content, SWT.NONE).setText("Template folder:");
        folder = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        folder.setItems(folders.stream().map(RepositoryTemplateFolder::name).toArray(String[]::new));
        if (!folders.isEmpty()) folder.select(0);
        folder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(content, SWT.NONE).setText("New repository name:");
        name = new Text(content, SWT.BORDER);
        name.setMessage("solution-block-name");
        name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(content, SWT.NONE).setText("Description:");
        description = new Text(content, SWT.BORDER);
        description.setTextLimit(350);
        description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label visibilityLabel = new Label(content, SWT.NONE);
        visibilityLabel.setText("Visibility:");
        Composite visibility = new Composite(content, SWT.NONE);
        visibility.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        visibility.setLayout(new GridLayout(2, false));
        privateRepository = new Button(visibility, SWT.RADIO);
        privateRepository.setText("Private");
        privateRepository.setSelection(true);
        Button publicRepository = new Button(visibility, SWT.RADIO);
        publicRepository.setText("Public");
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        createButton(parent, IDialogConstants.OK_ID, "Create Repository", true).setEnabled(!folders.isEmpty());
    }

    @Override
    protected void okPressed() {
        if (folder.getSelectionIndex() < 0) {
            setErrorMessage("Choose a template folder.");
            return;
        }
        String repositoryName = name.getText().trim();
        try {
            WorkspaceService.validateRepositoryName(repositoryName);
        } catch (IllegalArgumentException ex) {
            setErrorMessage(ex.getMessage());
            return;
        }
        listener.create(folder.getText(), repositoryName, description.getText().trim(),
                privateRepository.getSelection());
        super.okPressed();
    }
}
