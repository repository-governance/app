package org.cda.archi.governance.views;

import org.cda.archi.governance.models.AuthenticationStatus;
import org.cda.archi.governance.models.GitConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Connection and search inputs; all effects are delegated to its listener. */
public final class ConnectionView extends Composite {
    public interface Listener {
        void connect(String organization, String topic);
        void refresh(String organization, String topic);
        void cancel();
    }

    private final Text organization;
    private final Text topic;
    private final Label gitStatus;
    private final Label gitIdentity;
    private final Label githubStatus;
    private Button connectButton;
    private Button refreshButton;
    private Button cancelButton;
    private Listener listener;
    private boolean authenticated;

    public ConnectionView(Composite parent, String savedOrganization, String savedTopic) {
        super(parent, SWT.NONE);
        setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        setLayout(new GridLayout(2, false));

        gitStatus = valueRow(this, "Local Git:", "Checking Git installation…");
        gitIdentity = valueRow(this, "Git user.name / user.email:", "Not configured");
        githubStatus = valueRow(this, "GitHub account:", "Not connected");

        Label orgLabel = new Label(this, SWT.NONE);
        orgLabel.setText("Organization:");
        organization = new Text(this, SWT.BORDER);
        organization.setMessage("GitHub organization name");
        organization.setText(savedOrganization == null ? "" : savedOrganization);
        organization.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label topicLabel = new Label(this, SWT.NONE);
        topicLabel.setText("Repository Topic:");
        topic = new Text(this, SWT.BORDER);
        topic.setText(savedTopic == null || savedTopic.isBlank() ? "archimate" : savedTopic);
        topic.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite actions = new Composite(this, SWT.NONE);
        GridData actionData = new GridData(SWT.END, SWT.CENTER, true, false);
        actionData.horizontalSpan = 2;
        actions.setLayoutData(actionData);
        actions.setLayout(new GridLayout(3, true));
        connectButton = button(actions, "Connect");
        refreshButton = button(actions, "Refresh");
        cancelButton = button(actions, "Cancel");
        refreshButton.setEnabled(false);
        cancelButton.setEnabled(false);

        connectButton.addListener(SWT.Selection, event -> {
            if (listener != null) listener.connect(getOrganization(), getTopic());
        });
        refreshButton.addListener(SWT.Selection, event -> {
            if (listener != null) listener.refresh(getOrganization(), getTopic());
        });
        cancelButton.addListener(SWT.Selection, event -> {
            if (listener != null) listener.cancel();
        });
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public String getOrganization() { return organization.getText().trim(); }
    public String getTopic() { return topic.getText().trim(); }

    public void setGitConfiguration(GitConfiguration configuration) {
        if (isDisposed()) return;
        String gitMessage = configuration.isAvailable()
                ? configuration.getVersion() + (configuration.hasCredentialHelper()
                    ? " — credential helper configured" : " — no credential helper configured")
                : configuration.getMessage();
        gitStatus.setText(gitMessage);
        gitIdentity.setText(identityText(configuration));
        layout(true, true);
    }

    public void setAuthenticationStatus(AuthenticationStatus status) {
        if (isDisposed()) return;
        authenticated = status.isAuthenticated();
        if (status.isAuthenticated()) {
            githubStatus.setText(status.getUser().getLogin()
                    + (status.getUser().getName().isBlank() ? "" : " (" + status.getUser().getName() + ")"));
        } else {
            githubStatus.setText(status.getMessage());
        }
        refreshButton.setEnabled(status.isAuthenticated());
        layout(true, true);
    }

    public void setBusy(boolean busy) {
        if (isDisposed()) return;
        connectButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy && authenticated);
        organization.setEnabled(!busy);
        topic.setEnabled(!busy);
        cancelButton.setEnabled(busy);
    }

    public void setRefreshAvailable(boolean available) {
        authenticated = available;
        if (!isDisposed()) refreshButton.setEnabled(available);
    }

    private static String identityText(GitConfiguration configuration) {
        String name = configuration.getUserName();
        String email = configuration.getUserEmail();
        if (name.isBlank() && email.isBlank()) return "Not configured";
        if (name.isBlank()) return email;
        if (email.isBlank()) return name;
        return name + " <" + email + ">";
    }

    private static Label valueRow(Composite parent, String caption, String value) {
        Label key = new Label(parent, SWT.NONE);
        key.setText(caption);
        Label text = new Label(parent, SWT.WRAP);
        text.setText(value);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return text;
    }

    private static Button button(Composite parent, String label) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(label);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return button;
    }
}
