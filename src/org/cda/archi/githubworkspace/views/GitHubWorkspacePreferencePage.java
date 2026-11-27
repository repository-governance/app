package org.cda.archi.githubworkspace.views;

import java.io.IOException;
import java.nio.file.Path;

import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.WorkspaceService;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;

/** Native Archi/Eclipse preference page for discovery, local workspace and read-only auth status. */
public final class GitHubWorkspacePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    public static final String PAGE_ID = "org.cda.archi.githubworkspace.preferences";
    private final PreferencesService preferences = new PreferencesService();
    private Text organization;
    private Text topic;
    private Button useDefault;
    private Text workspaceLocation;
    private Button browse;
    private Label gitStatus;
    private Label gitVersion;
    private Label credentialStatus;
    private Label account;

    public GitHubWorkspacePreferencePage() {
        setTitle("GitHub Workspace");
        setDescription("Repository discovery, local workspace and authentication status.");
    }

    @Override
    public void init(IWorkbench workbench) { }

    @Override
    protected Composite createContents(Composite parent) {
        Composite content = new Composite(parent, SWT.NONE);
        content.setLayout(new GridLayout(3, false));

        groupTitle(content, "Repository Discovery");
        organization = row(content, "Organization:", preferences.getOrganization());
        topic = row(content, "Repository Topic:", preferences.getTopic());

        groupTitle(content, "Local Workspace");
        useDefault = new Button(content, SWT.CHECK);
        useDefault.setText("Use default location");
        useDefault.setSelection(preferences.useDefaultWorkspace());
        GridData checkboxData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        checkboxData.horizontalSpan = 3;
        useDefault.setLayoutData(checkboxData);
        Label locationLabel = new Label(content, SWT.NONE);
        locationLabel.setText("Workspace Location:");
        workspaceLocation = new Text(content, SWT.BORDER);
        workspaceLocation.setText(initialWorkspaceText());
        workspaceLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workspaceLocation.addModifyListener(event -> validateWorkspaceField());
        browse = new Button(content, SWT.PUSH);
        browse.setText("Browse...");
        browse.addListener(SWT.Selection, event -> browseForWorkspace());
        useDefault.addListener(SWT.Selection, event -> updateWorkspaceControls());
        updateWorkspaceControls();

        groupTitle(content, "Authentication");
        gitStatus = readonlyRow(content, "Git installation:");
        gitVersion = readonlyRow(content, "Git version:");
        credentialStatus = readonlyRow(content, "Git Credential Manager / helper:");
        account = readonlyRow(content, "Authenticated GitHub account:");
        loadGitStatusAsync();

        if (new WorkspaceService(preferences).hasLegacyGovernanceWorkspace()) {
            Label notice = new Label(content, SWT.WRAP);
            notice.setText("An existing Governance workspace was found. It will not be moved, modified, or deleted.");
            GridData noticeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            noticeData.horizontalSpan = 3;
            notice.setLayoutData(noticeData);
        }
        return content;
    }

    @Override
    public boolean performOk() {
        if (!saveSettings()) return false;
        return super.performOk();
    }

    @Override
    protected void performApply() {
        saveSettings();
    }

    @Override
    protected void performDefaults() {
        organization.setText(PreferencesService.DEFAULT_ORGANIZATION);
        topic.setText(PreferencesService.DEFAULT_TOPIC);
        useDefault.setSelection(true);
        workspaceLocation.setText(preferences.defaultWorkspace().toString());
        updateWorkspaceControls();
        setErrorMessage(null);
        super.performDefaults();
    }

    private boolean saveSettings() {
        try {
            validateDiscovery();
            Path workspace;
            if (useDefault.getSelection()) {
                workspace = preferences.defaultWorkspace();
            } else {
                if (workspaceLocation.getText().isBlank()) throw new IOException("Choose a workspace location.");
                workspace = Path.of(workspaceLocation.getText().trim()).toAbsolutePath().normalize();
            }
            PreferencesService.validateWorkspaceLocation(workspace.toString());
            preferences.saveSearch(organization.getText().trim(), topic.getText().trim());
            preferences.saveWorkspace(useDefault.getSelection(), workspaceLocation.getText().trim());
            setErrorMessage(null);
            return true;
        } catch (Exception ex) {
            setErrorMessage(ex.getMessage() == null ? "Could not save GitHub Workspace preferences." : ex.getMessage());
            return false;
        }
    }

    private void validateDiscovery() {
        new org.cda.archi.githubworkspace.models.GitHubOrganization(organization.getText().trim());
        org.cda.archi.githubworkspace.services.GitHubRepositoryService.validateTopic(topic.getText().trim());
    }

    private void browseForWorkspace() {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setText("Select GitHub Workspace Location");
        dialog.setFilterPath(workspaceLocation.getText().trim());
        String selected = dialog.open();
        if (selected != null) {
            workspaceLocation.setText(Path.of(selected).toAbsolutePath().normalize().toString());
            validateWorkspaceField();
        }
    }

    private void updateWorkspaceControls() {
        boolean custom = !useDefault.getSelection();
        if (useDefault.getSelection()) workspaceLocation.setText(preferences.defaultWorkspace().toString());
        workspaceLocation.setEnabled(custom);
        browse.setEnabled(custom);
        validateWorkspaceField();
    }

    private void validateWorkspaceField() {
        if (useDefault == null || workspaceLocation == null) return;
        if (useDefault.getSelection()) {
            setErrorMessage(null);
            return;
        }
        try {
            Path path = Path.of(workspaceLocation.getText().trim());
            if (java.nio.file.Files.exists(path)) {
                if (!java.nio.file.Files.isDirectory(path) || !java.nio.file.Files.isWritable(path)) {
                    throw new java.io.IOException("The workspace location is not a writable directory.");
                }
            }
            setErrorMessage(null);
        } catch (Exception ex) {
            setErrorMessage(ex.getMessage() == null ? "Enter a valid workspace path." : ex.getMessage());
        }
    }

    private String initialWorkspaceText() {
        return preferences.useDefaultWorkspace()
                ? preferences.defaultWorkspace().toString()
                : preferences.getWorkspaceRoot().toString();
    }

    private void loadGitStatusAsync() {
        Thread thread = new Thread(() -> {
            GitConfiguration configuration = new GitService().inspect(new CancellationToken());
            org.eclipse.swt.widgets.Display display = org.eclipse.swt.widgets.Display.getDefault();
            if (!display.isDisposed()) display.asyncExec(() -> {
                if (gitStatus == null || gitStatus.isDisposed()) return;
                gitStatus.setText(configuration.isAvailable() ? "Available" : "Not available");
                gitVersion.setText(configuration.getVersion().isBlank() ? "Unknown" : configuration.getVersion());
                credentialStatus.setText(configuration.hasCredentialHelper() ? "Configured" : "Not configured");
                account.setText(preferences.getLastAccount().isBlank() ? "Not connected" : preferences.getLastAccount());
                ((Composite) getControl()).layout(true, true);
            });
        }, "GitHub Workspace preference status");
        thread.setDaemon(true);
        thread.start();
    }

    private static Text row(Composite parent, String title, String value) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(title);
        Text text = new Text(parent, SWT.BORDER);
        text.setText(value == null ? "" : value);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = 2;
        text.setLayoutData(data);
        return text;
    }

    private static Label readonlyRow(Composite parent, String title) {
        Label caption = new Label(parent, SWT.NONE);
        caption.setText(title);
        Label value = new Label(parent, SWT.WRAP);
        value.setText("Checking...");
        value.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        return value;
    }

    private static void groupTitle(Composite parent, String title) {
        Label label = new Label(parent, SWT.BOLD);
        label.setText(title);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = 3;
        label.setLayoutData(data);
    }
}
