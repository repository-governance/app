package org.cda.archi.githubworkspace.views;

import java.io.IOException;
import java.nio.file.Path;

import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.WorkspaceService;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Captures a plan branch name and previews its safe local destination. */
public final class CloneRepositoryDialog extends TitleAreaDialog {
    @FunctionalInterface
    public interface Listener {
        void clone(GitHubRepository repository, String planId);
    }

    private final GitHubRepository repository;
    private final Listener listener;
    private final PreferencesService preferences = new PreferencesService();
    private Text planId;
    private Label destination;

    public CloneRepositoryDialog(Shell parent, GitHubRepository repository, Listener listener) {
        super(parent);
        this.repository = repository;
        this.listener = listener;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Clone Repository");
        setMessage("Create or open an independent local plan branch.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(2, false));
        value(content, "Repository:", repository.getFullName());
        value(content, "Default Branch:", repository.getDefaultBranch().isBlank()
                ? "Unavailable" : repository.getDefaultBranch());
        new Label(content, SWT.NONE).setText("Plan ID:");
        planId = new Text(content, SWT.BORDER);
        planId.setMessage("D-09999");
        planId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        planId.addModifyListener(event -> updateDestination());
        value(content, "Workspace Location:", preferences.getWorkspaceRoot().toString());
        Label destinationTitle = new Label(content, SWT.NONE);
        destinationTitle.setText("Destination:");
        destination = new Label(content, SWT.WRAP);
        destination.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateDestination();
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        createButton(parent, IDialogConstants.OK_ID, "Clone", true);
    }

    @Override
    protected void okPressed() {
        String value = planId.getText().trim();
        try {
            WorkspaceService.validatePlanIdSyntax(value);
            if (repository.getDefaultBranch().isBlank()) {
                throw new IllegalArgumentException("The repository default branch is unavailable.");
            }
            listener.clone(repository, value);
        } catch (IllegalArgumentException ex) {
            setErrorMessage(ex.getMessage());
            return;
        }
        super.okPressed();
    }

    private void updateDestination() {
        if (destination == null || destination.isDisposed()) return;
        String value = planId.getText().trim();
        try {
            Path path = new WorkspaceService(preferences).planPath(repository, value);
            destination.setText(path.toString());
            setErrorMessage(null);
        } catch (IllegalArgumentException | IOException ex) {
            destination.setText(preferences.getWorkspaceRoot().resolve("github.com")
                    .resolve(repository.getFullName().replace('/', java.io.File.separatorChar))
                    .resolve(value.isBlank() ? "<Plan ID>" : value).toString());
            if (!value.isBlank()) setErrorMessage(ex.getMessage());
        }
        destination.getParent().layout(true, true);
    }

    private static void value(Composite parent, String title, String value) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(title);
        Label text = new Label(parent, SWT.WRAP);
        text.setText(value == null || value.isBlank() ? "Not available" : value);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
}
