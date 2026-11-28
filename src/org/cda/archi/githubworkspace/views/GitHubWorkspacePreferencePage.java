package org.cda.archi.githubworkspace.views;

import java.io.IOException;
import java.nio.file.Path;

import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.services.GitService;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.WorkspaceService;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.cda.archi.githubworkspace.infrastructure.CancellationToken;
import org.cda.archi.githubworkspace.services.LocalizationService;

/** Native Archi/Eclipse preference page for discovery, local workspace and read-only auth status. */
public final class GitHubWorkspacePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    public static final String PAGE_ID = "org.cda.archi.githubworkspace.preferences";
    private final PreferencesService preferences = new PreferencesService();
    private String savedLanguageChoice = preferences.getLanguage();
    private Text organization;
    private Text topic;
    private Text templateRepository;
    private Text dataRepository;
    private Combo language;
    private Button useDefault;
    private Text workspaceLocation;
    private Button browse;
    private Table authenticationTable;
    private TableItem gitStatus;
    private TableItem gitVersion;
    private TableItem credentialStatus;
    private TableItem account;

    public GitHubWorkspacePreferencePage() {
        setTitle(LocalizationService.text("GitHub Workspace"));
        setDescription(LocalizationService.text("Repository discovery, local workspace and authentication status."));
    }

    @Override
    public void init(IWorkbench workbench) { }

    @Override
    protected Composite createContents(Composite parent) {
        Composite content = new Composite(parent, SWT.NONE);
        content.setLayout(new GridLayout(3, false));

        groupTitle(content, "Language");
        Label languageLabel = new Label(content, SWT.NONE);
        languageLabel.setText(LocalizationService.text("Language:"));
        language = new Combo(content, SWT.DROP_DOWN | SWT.READ_ONLY);
        language.setItems(LocalizationService.text("Automatic (system)"),
                LocalizationService.text("English"), LocalizationService.text("Español"));
        language.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(content, SWT.NONE);
        String choice = preferences.getLanguage();
        language.select(LocalizationService.SPANISH.equals(choice) ? 2
                : LocalizationService.ENGLISH.equals(choice) ? 1 : 0);

        groupTitle(content, "Repository Discovery");
        organization = row(content, "Organization URL:", preferences.getOrganizationUrl());
        topic = row(content, "Topic by default:", PreferencesService.DEFAULT_TOPIC);
        topic.setEnabled(false);
        topic.setToolTipText(LocalizationService.text(
                "Fixed default topic used for repository discovery and newly created repositories."));

        groupTitle(content, "Repository Templates");
        templateRepository = row(content, "Template Repository URL:", preferences.getTemplateRepository());
        templateRepository.setToolTipText(LocalizationService.text(
                "GitHub repository whose root folders provide repository templates."));

        Group dataGroup = new Group(content, SWT.NONE);
        dataGroup.setText(LocalizationService.text("Data Management"));
        dataGroup.setLayout(new GridLayout(3, false));
        dataGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        dataRepository = row(dataGroup, "Data Repository URL:", preferences.getDataRepository());
        dataRepository.setToolTipText(LocalizationService.text(
                "GitHub repository used to store table schemas and JSON records."));

        groupTitle(content, "Local Workspace");
        useDefault = new Button(content, SWT.CHECK);
        useDefault.setText(LocalizationService.text("Use default location"));
        useDefault.setSelection(preferences.useDefaultWorkspace());
        GridData checkboxData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        checkboxData.horizontalSpan = 3;
        useDefault.setLayoutData(checkboxData);
        Label locationLabel = new Label(content, SWT.NONE);
        locationLabel.setText(LocalizationService.text("Workspace Location:"));
        workspaceLocation = new Text(content, SWT.BORDER);
        workspaceLocation.setText(initialWorkspaceText());
        workspaceLocation.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workspaceLocation.addModifyListener(event -> validateWorkspaceField());
        browse = new Button(content, SWT.PUSH);
        browse.setText(LocalizationService.text("Browse..."));
        browse.addListener(SWT.Selection, event -> browseForWorkspace());
        useDefault.addListener(SWT.Selection, event -> updateWorkspaceControls());
        updateWorkspaceControls();

        groupTitle(content, "Git Authentication");
        authenticationTable = new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        authenticationTable.setHeaderVisible(true);
        authenticationTable.setLinesVisible(true);
        authenticationTable.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        TableColumn settingColumn = new TableColumn(authenticationTable, SWT.LEFT);
        settingColumn.setText(LocalizationService.text("Setting"));
        settingColumn.setWidth(270);
        TableColumn valueColumn = new TableColumn(authenticationTable, SWT.LEFT);
        valueColumn.setText(LocalizationService.text("Value"));
        valueColumn.setWidth(500);
        gitStatus = authRow("Git installation");
        gitVersion = authRow("Git version");
        credentialStatus = authRow("Git Credential Manager / helper");
        account = authRow("Authenticated GitHub account");
        loadGitStatusAsync();

        if (new WorkspaceService(preferences).hasLegacyGovernanceWorkspace()) {
            Label notice = new Label(content, SWT.WRAP);
            notice.setText(LocalizationService.text(
                    "An existing Governance workspace was found. It will not be moved, modified, or deleted."));
            GridData noticeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            noticeData.horizontalSpan = 3;
            notice.setLayoutData(noticeData);
        }
        return content;
    }

    @Override
    public boolean performOk() {
        if (!saveAndHandleLanguageChange()) return false;
        return super.performOk();
    }

    @Override
    protected void performApply() {
        saveAndHandleLanguageChange();
    }

    @Override
    protected void performDefaults() {
        organization.setText("https://github.com/" + PreferencesService.DEFAULT_ORGANIZATION);
        topic.setText(PreferencesService.DEFAULT_TOPIC);
        language.select(0);
        templateRepository.setText(PreferencesService.DEFAULT_TEMPLATE_REPOSITORY);
        dataRepository.setText(PreferencesService.DEFAULT_DATA_REPOSITORY);
        useDefault.setSelection(true);
        workspaceLocation.setText(preferences.defaultWorkspace().toString());
        language.select(0);
        updateWorkspaceControls();
        setErrorMessage(null);
        super.performDefaults();
    }

    private boolean saveSettings() {
        try {
            validateDiscovery();
            org.cda.archi.githubworkspace.services.RepositoryManagementService
                    .normalizeTemplateRepository(templateRepository.getText());
            PreferencesService.normalizeDataRepositoryUrl(dataRepository.getText());
            Path workspace;
            if (useDefault.getSelection()) {
                workspace = preferences.defaultWorkspace();
            } else {
                if (workspaceLocation.getText().isBlank()) throw new IOException("Choose a workspace location.");
                workspace = Path.of(workspaceLocation.getText().trim()).toAbsolutePath().normalize();
            }
            PreferencesService.validateWorkspaceLocation(workspace.toString());
            preferences.saveSearch(organization.getText().trim());
            preferences.saveTemplateRepository(templateRepository.getText().trim());
            preferences.saveDataRepository(dataRepository.getText().trim());
            preferences.saveWorkspace(useDefault.getSelection(), workspaceLocation.getText().trim());
            preferences.saveLanguage(switch (language.getSelectionIndex()) {
                case 1 -> LocalizationService.ENGLISH;
                case 2 -> LocalizationService.SPANISH;
                default -> LocalizationService.AUTOMATIC;
            });
            setErrorMessage(null);
            return true;
        } catch (Exception ex) {
            setErrorMessage(LocalizationService.text(
                    ex.getMessage() == null ? "Could not save GitHub Workspace preferences." : ex.getMessage()));
            return false;
        }
    }

    private boolean saveAndHandleLanguageChange() {
        String previousChoice = savedLanguageChoice;
        if (!saveSettings()) return false;
        String newChoice = preferences.getLanguage();
        savedLanguageChoice = newChoice;
        String previousLanguage = LocalizationService.effectiveLanguage(previousChoice, java.util.Locale.getDefault());
        String newLanguage = LocalizationService.effectiveLanguage(newChoice, java.util.Locale.getDefault());
        if (!previousLanguage.equals(newLanguage)
                && MessageDialog.openQuestion(getShell(),
                        LocalizationService.text("Restart required"),
                        LocalizationService.text(
                                "The language change will take effect after restarting Archi. Restart now?"))) {
            org.eclipse.ui.PlatformUI.getWorkbench().restart();
        }
        return true;
    }

    private void validateDiscovery() {
        new org.cda.archi.githubworkspace.models.GitHubOrganization(organization.getText().trim());
    }

    private void browseForWorkspace() {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setText(LocalizationService.text("Select GitHub Workspace Location"));
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
            setErrorMessage(LocalizationService.text(
                    ex.getMessage() == null ? "Enter a valid workspace path." : ex.getMessage()));
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
                if (authenticationTable == null || authenticationTable.isDisposed()) return;
                gitStatus.setText(new String[] {LocalizationService.text("Git installation"),
                        LocalizationService.text(configuration.isAvailable() ? "Available" : "Not available")});
                gitVersion.setText(new String[] {LocalizationService.text("Git version"),
                        LocalizationService.text(configuration.getVersion().isBlank()
                                ? "Unknown" : configuration.getVersion())});
                credentialStatus.setText(new String[] {LocalizationService.text("Git Credential Manager / helper"),
                        LocalizationService.text(configuration.hasCredentialHelper()
                                ? "Configured" : "Not configured")});
                account.setText(new String[] {LocalizationService.text("Authenticated GitHub account"),
                        preferences.getLastAccount().isBlank()
                                ? LocalizationService.text("Not connected") : preferences.getLastAccount()});
                ((Composite) getControl()).layout(true, true);
            });
        }, "GitHub Workspace preference status");
        thread.setDaemon(true);
        thread.start();
    }

    private static Text row(Composite parent, String title, String value) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(LocalizationService.text(title));
        Text text = new Text(parent, SWT.BORDER);
        text.setText(value == null ? "" : value);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = 2;
        text.setLayoutData(data);
        return text;
    }

    private TableItem authRow(String title) {
        TableItem row = new TableItem(authenticationTable, SWT.NONE);
        row.setText(new String[] {LocalizationService.text(title), LocalizationService.text("Checking...")});
        return row;
    }

    private static void groupTitle(Composite parent, String title) {
        Label label = new Label(parent, SWT.BOLD);
        label.setText(LocalizationService.text(title));
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = 3;
        label.setLayoutData(data);
    }
}
