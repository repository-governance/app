package org.cda.archi.governance.views;

import org.cda.archi.governance.models.AuthenticationStatus;
import org.cda.archi.governance.models.GitConfiguration;
import org.cda.archi.governance.models.GitHubRepository;
import org.cda.archi.governance.models.RepositorySearchResult;
import org.cda.archi.governance.services.GovernancePreferences;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/** Main native Governance dialog (View) retaining SWT/JFace-only presentation duties. */
public final class GovernanceView extends TitleAreaDialog {
    public interface Listener {
        void connect(String organization, String topic);
        void refresh(String organization, String topic);
        void cancel();
        void openModel(String organization, GitHubRepository repository);
        void viewOnGithub(GitHubRepository repository);
        void closed();
    }

    private final GovernancePreferences preferences;
    private ConnectionView connection;
    private RepositoryListView repositories;
    private Listener listener;

    public GovernanceView(Shell parentShell, GovernancePreferences preferences) {
        super(parentShell);
        this.preferences = preferences;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Governance - GitHub repositories");
        shell.addListener(SWT.Dispose, event -> {
            if (listener != null) listener.closed();
        });
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("GitHub organization repositories");
        setMessage("Uses the local Git credential helper and only displays repositories available to your GitHub account.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(1, false));
        connection = new ConnectionView(content, preferences.getOrganization(), preferences.getTopic());
        connection.setListener(new ConnectionView.Listener() {
            @Override public void connect(String organization, String topic) {
                if (listener != null) listener.connect(organization, topic);
            }
            @Override public void refresh(String organization, String topic) {
                if (listener != null) listener.refresh(organization, topic);
            }
            @Override public void cancel() {
                if (listener != null) listener.cancel();
            }
        });
        repositories = new RepositoryListView(content);
        repositories.setOpenModelListener(repository -> {
            if (listener != null) listener.openModel(connection.getOrganization(), repository);
        });
        repositories.setViewOnGithubListener(repository -> {
            if (listener != null) listener.viewOnGithub(repository);
        });
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLOSE_ID, "Close", true);
    }

    public void setGitConfiguration(GitConfiguration configuration) {
        if (connection != null && !connection.isDisposed()) connection.setGitConfiguration(configuration);
    }

    public void setAuthenticationStatus(AuthenticationStatus status) {
        if (connection != null && !connection.isDisposed()) connection.setAuthenticationStatus(status);
    }

    public void setBusy(boolean busy, String message) {
        if (connection != null && !connection.isDisposed()) connection.setBusy(busy);
        if (repositories != null && !repositories.isDisposed()) {
            repositories.setBusy(busy);
            if (busy) repositories.showLoading();
        }
        setErrorMessage(null);
        if (busy) setMessage(message);
    }

    public void setModelOpening(boolean busy, String message) {
        if (connection != null && !connection.isDisposed()) connection.setBusy(busy);
        if (repositories != null && !repositories.isDisposed()) repositories.setBusy(busy);
        if (busy) {
            setErrorMessage(null);
            setMessage(message, IMessageProvider.INFORMATION);
        }
    }

    public void showProgress(String message) {
        if (getShell() == null || getShell().isDisposed()) return;
        setErrorMessage(null);
        setMessage(message, IMessageProvider.INFORMATION);
    }

    public void closeAfterModelOpened() {
        if (getShell() != null && !getShell().isDisposed()) close();
    }

    public void showRepositories(RepositorySearchResult result) {
        if (repositories != null && !repositories.isDisposed()) {
            repositories.showRepositories(result.getRepositories(), result.getTotalCount());
        }
        setMessage("Search completed. GitHub returned a complete result set.");
    }

    public void showGitStatus(GitConfiguration configuration) {
        setGitConfiguration(configuration);
        if (!configuration.isAvailable()) {
            showError(configuration.getMessage());
        } else if (!configuration.hasCredentialHelper()) {
            setMessage(configuration.getMessage(), IMessageProvider.INFORMATION);
        }
    }

    public void showError(String message) {
        setErrorMessage(message);
        if (repositories != null && !repositories.isDisposed()) repositories.showMessage(message);
    }

    public void showModelError(String message) {
        setErrorMessage(message);
        if (repositories != null && !repositories.isDisposed()) repositories.showStatus(message);
    }

    public void showCancelled() {
        setMessage("Operation cancelled.", IMessageProvider.INFORMATION);
        if (repositories != null && !repositories.isDisposed()) repositories.showMessage("Operation cancelled.");
    }
}
