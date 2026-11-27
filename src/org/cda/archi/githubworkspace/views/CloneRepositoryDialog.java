package org.cda.archi.githubworkspace.views;

import java.io.IOException;
import java.nio.file.InvalidPathException;
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

/** Captures a local branch name and previews its safe workspace destination. */
public final class CloneRepositoryDialog extends TitleAreaDialog {
    @FunctionalInterface
    public interface Listener {
        void clone(GitHubRepository repository, String planId);
    }

    private final GitHubRepository repository;
    private final String githubLogin;
    private final Listener listener;
    private final PreferencesService preferences = new PreferencesService();
    private Text planId;
    private Label branch;
    private Label destination;

    public CloneRepositoryDialog(Shell parent, GitHubRepository repository, String githubLogin, Listener listener) {
        super(parent);
        this.repository = repository;
        this.githubLogin = githubLogin == null ? "" : githubLogin;
        this.listener = listener;
        setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        setBlockOnOpen(true);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Create Branch Workspace");
        setMessage("Create a local branch from the selected repository's default branch.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(2, false));

        Label sourceHeading = new Label(content, SWT.NONE);
        sourceHeading.setText("SOURCE REPOSITORY");
        sourceHeading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        Label source = new Label(content, SWT.WRAP);
        source.setText(repository.getFullName()
                + (repository.getDescription().isBlank() ? "" : " — " + repository.getDescription()));
        source.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        new Label(content, SWT.NONE).setText("Branch name:");
        planId = new Text(content, SWT.BORDER);
        planId.setMessage("D-09999");
        planId.setToolTipText("This becomes the name of the local branch and workspace folder.");
        planId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        planId.addModifyListener(event -> updateDestination());

        new Label(content, SWT.NONE).setText("Solution Architect Code:");
        Text architectCode = new Text(content, SWT.BORDER | SWT.READ_ONLY);
        architectCode.setText(githubLogin.isBlank() ? "Not connected" : githubLogin);
        architectCode.setToolTipText("GitHub account authenticated with Git Credential Manager.");
        architectCode.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label creationModeLabel = new Label(content, SWT.NONE);
        creationModeLabel.setText("Creation mode:");
        creationModeLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        Label creationMode = new Label(content, SWT.WRAP);
        creationMode.setText("Create a local branch from the repository's default branch");
        creationMode.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Label resultHeading = new Label(content, SWT.NONE);
        resultHeading.setText("Result");
        resultHeading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        value(content, "Repository:", repository.getName());
        new Label(content, SWT.NONE).setText("Branch:");
        branch = new Label(content, SWT.NONE);
        branch.setText("(enter branch name)");
        branch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(content, SWT.NONE).setText("Local workspace:");
        destination = new Label(content, SWT.WRAP);
        destination.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        updateDestination();
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
        createButton(parent, IDialogConstants.OK_ID, "Create Branch Workspace", true);
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
            WorkspaceService workspace = new WorkspaceService(preferences);
            destination.setText(destinationPreview(workspace, repository, value));
            branch.setText(value.isBlank() ? "(enter branch name)"
                    : isValidPlanId(value) ? value : "(invalid branch name)");
            if (value.isBlank()) {
                setErrorMessage(null);
            } else if (isValidPlanId(value)) {
                setErrorMessage(null);
            } else {
                setErrorMessage("Branch name must match D-[0-9]+ (for example, D-09999).");
            }
        } catch (IOException | InvalidPathException ex) {
            branch.setText(value.isBlank() ? "(enter branch name)" : "(invalid branch name)");
            destination.setText("Destination unavailable");
            setErrorMessage(ex.getMessage() == null ? "The destination path is invalid." : ex.getMessage());
        }
        destination.getParent().layout(true, true);
    }

    static String destinationPreview(WorkspaceService workspace, GitHubRepository repository, String planId)
            throws IOException {
        Path repositoryPath = workspace.repositoryPath(repository);
        if (planId == null || planId.isBlank()) {
            return repositoryPath + java.io.File.separator + "(enter branch name)";
        }
        try {
            return workspace.planPath(repository, planId).toString();
        } catch (IllegalArgumentException ex) {
            return repositoryPath + java.io.File.separator + "(invalid branch name)";
        }
    }

    private static boolean isValidPlanId(String value) {
        try {
            WorkspaceService.validatePlanIdSyntax(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void value(Composite parent, String title, String value) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(title);
        Label text = new Label(parent, SWT.WRAP);
        text.setText(value == null || value.isBlank() ? "Not available" : value);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
}
