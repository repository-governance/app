package org.cda.archi.githubworkspace.views;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositoryTemplateFolder;
import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/** Organization repository grid and create-from-template action surface. */
public final class RepositoryManagementView extends TitleAreaDialog {
    public interface Listener {
        void refresh();
        void requestCreate();
        void createFromTemplate(String folder, String name, String description, boolean isPrivate);
        void closed();
    }

    private Listener listener;
    private Label status;
    private Table repositories;
    private Button refresh;
    private Button create;
    private Image windowIcon;

    public RepositoryManagementView(Shell parent) {
        super(parent);
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
        setBlockOnOpen(true);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void centerOnParent() {
        Shell shell = getShell();
        Shell parent = shell == null || shell.isDisposed() || !(shell.getParent() instanceof Shell parentShell)
                ? null : parentShell;
        if (parent == null || parent.isDisposed()) return;
        Rectangle bounds = parent.getBounds();
        org.eclipse.swt.graphics.Point size = shell.getSize();
        shell.setLocation(bounds.x + (bounds.width - size.x) / 2,
                bounds.y + (bounds.height - size.y) / 2);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Management Repositories - GitHub Workspace");
        try (InputStream stream = RepositoryManagementView.class.getResourceAsStream("/icons/github.png")) {
            if (stream != null) {
                windowIcon = new Image(shell.getDisplay(), stream);
                shell.setImage(windowIcon);
            }
        } catch (IOException ignored) {
            // The management screen remains available if its optional icon cannot be read.
        }
        shell.addListener(SWT.Dispose, event -> {
            if (windowIcon != null && !windowIcon.isDisposed()) windowIcon.dispose();
            windowIcon = null;
            if (listener != null) listener.closed();
        });
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Management Repositories");
        setMessage("All repositories in the organization configured in Preferences.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(1, false));

        Composite toolbar = new Composite(content, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        toolbar.setLayout(new GridLayout(3, false));
        status = new Label(toolbar, SWT.WRAP);
        status.setText("Connecting to GitHub...");
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refresh = new Button(toolbar, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.addListener(SWT.Selection, event -> {
            if (listener != null) listener.refresh();
        });
        create = new Button(toolbar, SWT.PUSH);
        create.setText("Create Repository...");
        create.addListener(SWT.Selection, event -> {
            if (listener != null) listener.requestCreate();
        });
        repositories = new Table(content, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        repositories.setHeaderVisible(true);
        repositories.setLinesVisible(true);
        repositories.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        addColumn("Repository", 220);
        addColumn("Description", 380);
        addColumn("Visibility", 100);
        addColumn("Default branch", 150);
        setBusy(true, "Connecting to GitHub...");
        return area;
    }

    private void addColumn(String title, int width) {
        TableColumn column = new TableColumn(repositories, SWT.LEFT);
        column.setText(title);
        column.setWidth(width);
    }

    public void showRepositories(List<GitHubRepository> items, String organization) {
        if (repositories == null || repositories.isDisposed()) return;
        repositories.removeAll();
        for (GitHubRepository repository : items) {
            TableItem item = new TableItem(repositories, SWT.NONE);
            item.setText(new String[] {
                    repository.getName(),
                    repository.getDescription(),
                    repository.getVisibility(),
                    repository.getDefaultBranch()
            });
            item.setData(repository);
        }
        status.setText(items.size() + (items.size() == 1 ? " repository in " : " repositories in ")
                + organization);
        repositories.getParent().layout(true, true);
    }

    public void openCreateDialog(List<RepositoryTemplateFolder> folders) {
        if (folders.isEmpty()) {
            showError("The configured template repository has no folders at its root.");
            return;
        }
        CreateRepositoryDialog dialog = new CreateRepositoryDialog(getShell(), folders,
                (folder, name, description, isPrivate) -> {
                    if (listener != null) listener.createFromTemplate(folder, name, description, isPrivate);
                });
        dialog.setBlockOnOpen(true);
        dialog.open();
    }

    public void setBusy(boolean busy, String message) {
        if (status == null || status.isDisposed()) return;
        if (message != null && !message.isBlank()) status.setText(message);
        if (refresh != null && !refresh.isDisposed()) refresh.setEnabled(!busy);
        if (create != null && !create.isDisposed()) create.setEnabled(!busy);
    }

    public void showError(String message) {
        setErrorMessage(message);
        if (status != null && !status.isDisposed()) status.setText("Could not complete repository management.");
    }

    public void showNotice(String message) {
        setErrorMessage(null);
        if (status != null && !status.isDisposed()) status.setText(message);
    }

    @Override
    protected Control createButtonBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
        bar.setLayout(layout);

        Button help = new Button(bar, SWT.PUSH);
        Image helpImage = Dialog.getImage(Dialog.DLG_IMG_HELP);
        if (helpImage != null) help.setImage(helpImage);
        else help.setText("?");
        help.setToolTipText("View Log");
        help.getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override public void getName(AccessibleEvent event) { event.result = "View Log"; }
        });
        help.addListener(SWT.Selection, event -> {
            PluginLog.info("Repository Management", "Opened the log viewer.");
            new PluginLogDialog(getShell()).open();
        });
        help.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

        Composite spacer = new Composite(bar, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button close = createButton(bar, IDialogConstants.CLOSE_ID, "Close", false);
        close.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        return bar;
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CLOSE_ID) close();
        else super.buttonPressed(buttonId);
    }
}
