package org.cda.archi.githubworkspace.views;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.cda.archi.githubworkspace.models.AuthenticationStatus;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositorySearchResult;
import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.custom.ScrolledComposite;
import org.cda.archi.githubworkspace.services.PreferencesService;
import org.cda.archi.githubworkspace.services.LocalizationService;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;

/** Native SWT/JFace visual repository catalog. Effects are delegated to its MVC listener. */
public final class RepositoryBrowserView extends TitleAreaDialog {
    public interface Listener {
        void connect();
        void retryConnection();
        void refresh();
        void cancel();
        void cloneRepository(GitHubRepository repository, String planId);
        void openModel(GitHubRepository repository, String planId);
        void viewOnGithub(GitHubRepository repository);
        void requestLocalPlans(GitHubRepository repository);
        void requestThumbnail(GitHubRepository repository);
        void closed();
    }

    private final PreferencesService preferences = new PreferencesService();
    private final Map<Long, RepositoryCard> cards = new HashMap<>();
    private final Set<Long> requestedThumbnails = new HashSet<>();
    private Listener listener;
    private Label statusLabel;
    private String statusMessage = "Loading authorized repositories...";
    private Text search;
    private Button refreshButton;
    private Button preferencesButton;
    private Button cancelButton;
    private Button retryButton;
    private ScrolledComposite scroller;
    private Composite catalog;
    private boolean busy;
    private boolean authenticated;
    private String connectedLogin = "";
    private Image windowIcon;

    public RepositoryBrowserView(Shell parentShell) {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    /** Centers the catalog window over its Archi workbench parent after its initial size is set. */
    public void centerOnParent() {
        Shell shell = getShell();
        Shell parent = shell == null || shell.isDisposed() || !(shell.getParent() instanceof Shell parentShell)
                ? null : parentShell;
        if (parent == null || parent.isDisposed()) return;
        Rectangle parentBounds = parent.getBounds();
        org.eclipse.swt.graphics.Point size = shell.getSize();
        shell.setLocation(parentBounds.x + (parentBounds.width - size.x) / 2,
                parentBounds.y + (parentBounds.height - size.y) / 2);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(LocalizationService.text("GitHub Workspace"));
        try (InputStream iconStream = RepositoryBrowserView.class.getResourceAsStream("/icons/github.png")) {
            if (iconStream != null) {
                windowIcon = new Image(shell.getDisplay(), iconStream);
                shell.setImage(windowIcon);
            }
        } catch (IOException ignored) {
            // The catalog remains usable if an optional window icon cannot be read.
        }
        shell.addListener(SWT.Dispose, event -> {
            disposeImages();
            if (listener != null) listener.closed();
        });
    }

    @Override
    protected org.eclipse.swt.widgets.Control createDialogArea(Composite parent) {
        setTitle(LocalizationService.text("Browse Repositories"));
        setMessage(LocalizationService.text("Repositories in the organization and topic configured in Preferences."));
        Composite area = (Composite) super.createDialogArea(parent);
        Composite root = new Composite(area, SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        Composite toolbar = new Composite(root, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        toolbar.setLayout(new GridLayout(3, false));
        search = new Text(toolbar, SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL | SWT.BORDER);
        search.setMessage(LocalizationService.text("Filter repositories..."));
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.addModifyListener(event -> applyFilter());
        refreshButton = new Button(toolbar, SWT.PUSH);
        refreshButton.setText(LocalizationService.text("Refresh"));
        refreshButton.setToolTipText(LocalizationService.text(
                "Refresh repositories using the saved organization and topic."));
        refreshButton.addListener(SWT.Selection, event -> {
            if (listener != null && !busy) listener.refresh();
        });
        preferencesButton = new Button(toolbar, SWT.PUSH);
        preferencesButton.setText(LocalizationService.text("Preferences"));
        preferencesButton.setToolTipText(LocalizationService.text("Edit repository filters and workspace location."));
        preferencesButton.addListener(SWT.Selection, event -> openPreferences());
        Composite statusRow = new Composite(root, SWT.NONE);
        statusRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
         statusRow.setLayout(new GridLayout(2, false));
         retryButton = new Button(statusRow, SWT.PUSH);
        retryButton.setText(LocalizationService.text("Retry Connection"));
        retryButton.setToolTipText(LocalizationService.text("Retry authentication with Git Credential Manager."));
        retryButton.setVisible(false);
        GridData retryData = new GridData(SWT.END, SWT.CENTER, false, false);
        retryData.exclude = true;
        retryButton.setLayoutData(retryData);
        retryButton.addListener(SWT.Selection, event -> {
            if (listener != null && !busy) listener.retryConnection();
        });
        cancelButton = new Button(statusRow, SWT.PUSH);
        cancelButton.setText(LocalizationService.text("Cancel"));
        cancelButton.setToolTipText(LocalizationService.text("Cancel the current operation."));
        cancelButton.setEnabled(false);
        cancelButton.setVisible(false);
        GridData cancelData = new GridData(SWT.END, SWT.CENTER, false, false);
        cancelData.exclude = true;
        cancelButton.setLayoutData(cancelData);
        cancelButton.addListener(SWT.Selection, event -> {
            if (listener != null && busy) listener.cancel();
        });

        statusLabel = new Label(root, SWT.WRAP);
        setStatus("Loading authorized repositories...");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (new org.cda.archi.githubworkspace.services.WorkspaceService(preferences)
                .hasLegacyGovernanceWorkspace()) {
            setStatus("A legacy Governance workspace exists and will not be changed. "
                    + "Loading configured repositories.");
        }

        scroller = new ScrolledComposite(root, SWT.V_SCROLL);
        scroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scroller.setExpandHorizontal(true);
        scroller.setExpandVertical(true);
        catalog = new Composite(scroller, SWT.NONE);
        catalog.setLayout(new GridLayout(1, false));
        scroller.setContent(catalog);
        scroller.addListener(SWT.Resize, event -> {
            layoutCatalog();
            scheduleVisibleThumbnails();
        });
        scroller.getVerticalBar().addListener(SWT.Selection, event -> scheduleVisibleThumbnails());
        return area;
    }

    @Override
    protected org.eclipse.swt.widgets.Control createButtonBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
        layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
        bar.setLayout(layout);

        Button help = new Button(bar, SWT.PUSH);
        Image helpImage = Dialog.getImage(Dialog.DLG_IMG_HELP);
        if (helpImage != null) help.setImage(helpImage);
        else help.setText(LocalizationService.text("?"));
        help.setToolTipText(LocalizationService.text("View Log"));
        help.getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override public void getName(AccessibleEvent event) {
                event.result = LocalizationService.text("View Log");
            }
        });
        help.addListener(SWT.Selection, event -> {
            PluginLog.info("Browse", "Opened the log viewer.");
            new PluginLogDialog(getShell()).open();
        });
        help.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        return bar;
    }

    public void showGitStatus(GitConfiguration configuration) {
        if (statusLabel == null || statusLabel.isDisposed()) return;
        if (!configuration.isAvailable()) {
            setStatus(configuration.getMessage());
        } else if (!configuration.hasCredentialHelper()) {
            setStatus("Git is available, but no Git credential helper is configured.");
        }
    }

    public void setAuthenticationStatus(AuthenticationStatus status) {
        if (retryButton == null || retryButton.isDisposed()) return;
        authenticated = status.isAuthenticated();
        connectedLogin = status.isAuthenticated() ? status.getUser().getLogin() : "";
        boolean canRetry = status.getState() == AuthenticationStatus.State.FAILED;
        retryButton.setVisible(canRetry);
        ((GridData) retryButton.getLayoutData()).exclude = !canRetry;
        retryButton.getParent().layout(true, true);
        retryButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
    }

    public void setBusy(boolean busy, String message) {
        this.busy = busy;
        if (refreshButton != null && !refreshButton.isDisposed()) refreshButton.setEnabled(!busy);
        if (retryButton != null && !retryButton.isDisposed()) retryButton.setEnabled(!busy);
        if (cancelButton != null && !cancelButton.isDisposed()) {
            cancelButton.setEnabled(busy);
            cancelButton.setVisible(busy);
            ((GridData) cancelButton.getLayoutData()).exclude = !busy;
            cancelButton.getParent().layout(true, true);
        }
        cards.values().forEach(card -> card.setBusy(busy));
        if (busy && statusLabel != null && !statusLabel.isDisposed()) {
            setStatus(message);
        }
    }

    public void showProgress(String message) {
        setStatus(message);
    }

    public void showRepositories(RepositorySearchResult result) {
        disposeCards();
        List<GitHubRepository> repositories = result.getRepositories();
        for (GitHubRepository repository : repositories) {
            RepositoryCard card = new RepositoryCard(catalog, repository);
            cards.put(repository.getId(), card);
            if (listener != null) listener.requestLocalPlans(repository);
        }
        layoutCatalog();
        setStatus(repositories.size() == 1
                ? "1 repository found" : LocalizationService.format("{0} repositories found", repositories.size()));
        applyFilter();
        scheduleVisibleThumbnails();
    }

    public void showLocalPlans(GitHubRepository repository, List<String> plans) {
        RepositoryCard card = cards.get(repository.getId());
        if (card == null || card.isDisposed()) return;
        card.setPlans(plans);
        layoutCatalog();
    }

    public void showThumbnail(GitHubRepository repository, byte[] bytes) {
        try {
            RepositoryCard card = cards.get(repository.getId());
            if (card == null || card.isDisposed() || bytes == null || bytes.length == 0) return;
            ImageData data = new ImageData(new ByteArrayInputStream(bytes));
            if (data.width <= 0 || data.height <= 0 || data.width > 8192 || data.height > 8192
                    || (long) data.width * data.height > 16_000_000) return;
            double factor = Math.min(1.0, Math.min(200.0 / data.width, 140.0 / data.height));
            ImageData scaled = data.scaledTo(Math.max(1, (int) (data.width * factor)),
                    Math.max(1, (int) (data.height * factor)));
            card.setThumbnail(new Image(getShell().getDisplay(), scaled));
        } catch (IllegalArgumentException | org.eclipse.swt.SWTException ignored) {
            // Invalid or unsupported image data remains represented by the card placeholder.
        } finally {
            if (bytes != null) java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    public void showError(String message) {
        setErrorMessage(LocalizationService.text(message));
        setStatus(message);
    }

    public void showAuthenticationFailure(String message) {
        setAuthenticationStatus(new AuthenticationStatus(
                AuthenticationStatus.State.FAILED, message, null));
        showError(LocalizationService.format("Could not connect to GitHub. {0}", message));
    }

    public void showCancelled() {
        showProgress("Operation cancelled.");
    }

    public void closeAfterModelOpened() {
        if (getShell() != null && !getShell().isDisposed()) close();
    }

    private void openPreferences() {
        try {
            String organization = preferences.getOrganization();
            String topic = preferences.getTopic();
            String language = preferences.getLanguage();
            java.nio.file.Path workspace = preferences.getWorkspaceRoot();
            int result = PreferencesUtil.createPreferenceDialogOn(getShell(),
                    GitHubWorkspacePreferencePage.PAGE_ID, null, null).open();
            boolean changed = !organization.equals(preferences.getOrganization())
                    || !topic.equals(preferences.getTopic())
                    || !workspace.equals(preferences.getWorkspaceRoot());
            boolean languageChanged = !language.equals(preferences.getLanguage());
            if (languageChanged) relocalize();
            if (changed && listener != null) {
                search.setText("");
                listener.refresh();
            } else if (result == Window.OK) {
                setMessage(LocalizationService.text("Preferences saved."));
            }
        } catch (RuntimeException ex) {
            showError(LocalizationService.text("Could not open GitHub Workspace Preferences."));
        }
    }

    private void relocalize() {
        setTitle(LocalizationService.text("Browse Repositories"));
        setMessage(LocalizationService.text("Repositories in the organization and topic configured in Preferences."));
        if (search != null && !search.isDisposed()) search.setMessage(LocalizationService.text("Filter repositories..."));
        if (refreshButton != null && !refreshButton.isDisposed()) refreshButton.setText(LocalizationService.text("Refresh"));
        if (preferencesButton != null && !preferencesButton.isDisposed()) {
            preferencesButton.setText(LocalizationService.text("Preferences"));
        }
        if (retryButton != null && !retryButton.isDisposed()) retryButton.setText(LocalizationService.text("Retry Connection"));
        if (cancelButton != null && !cancelButton.isDisposed()) cancelButton.setText(LocalizationService.text("Cancel"));
        if (statusLabel != null && !statusLabel.isDisposed()) setStatus(statusMessage);
        if (refreshButton != null && !refreshButton.isDisposed()) refreshButton.setToolTipText(
                LocalizationService.text("Refresh repositories using the saved organization and topic."));
        if (preferencesButton != null && !preferencesButton.isDisposed()) preferencesButton.setToolTipText(
                LocalizationService.text("Edit repository filters and workspace location."));
        if (retryButton != null && !retryButton.isDisposed()) retryButton.setToolTipText(
                LocalizationService.text("Retry authentication with Git Credential Manager."));
        if (cancelButton != null && !cancelButton.isDisposed()) cancelButton.setToolTipText(
                LocalizationService.text("Cancel the current operation."));
        for (RepositoryCard card : cards.values()) card.relocalize();
        if (getShell() != null && !getShell().isDisposed()) getShell().layout(true, true);
    }

    private void setStatus(String message) {
        statusMessage = message == null ? "" : message;
        if (statusLabel != null && !statusLabel.isDisposed()) {
            statusLabel.setText(LocalizationService.text(statusMessage));
        }
    }

    private void applyFilter() {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        for (RepositoryCard card : cards.values()) {
            GitHubRepository repository = card.repository;
            boolean visible = query.isEmpty()
                    || repository.getFullName().toLowerCase(Locale.ROOT).contains(query)
                    || repository.getDescription().toLowerCase(Locale.ROOT).contains(query);
            card.setVisible(visible);
            ((GridData) card.getLayoutData()).exclude = !visible;
        }
        if (catalog != null && !catalog.isDisposed()) {
            catalog.layout(true, true);
            layoutCatalog();
        }
    }

    private void layoutCatalog() {
        if (catalog == null || catalog.isDisposed() || scroller == null || scroller.isDisposed()) return;
        int width = Math.max(1, scroller.getClientArea().width);
        catalog.setSize(width, Math.max(1, catalog.getSize().y));
        catalog.layout(true, true);
        org.eclipse.swt.graphics.Point preferred = catalog.computeSize(width, SWT.DEFAULT);
        int height = Math.max(preferred.y, scroller.getClientArea().height);
        catalog.setSize(width, height);
        scroller.setMinSize(width, height);
    }

    private void scheduleVisibleThumbnails() {
        if (scroller == null || scroller.isDisposed()) return;
        scroller.getDisplay().asyncExec(this::requestVisibleThumbnails);
    }

    private void requestVisibleThumbnails() {
        if (scroller == null || scroller.isDisposed() || listener == null) return;
        org.eclipse.swt.graphics.Point origin = scroller.getOrigin();
        org.eclipse.swt.graphics.Rectangle viewport = scroller.getClientArea();
        int top = origin.y;
        int bottom = top + viewport.height;
        for (RepositoryCard card : cards.values()) {
            if (card.isDisposed() || !card.getVisible()) continue;
            org.eclipse.swt.graphics.Rectangle bounds = card.getBounds();
            if (bounds.y + bounds.height < top || bounds.y > bottom) continue;
            if (requestedThumbnails.add(card.repository.getId())) listener.requestThumbnail(card.repository);
        }
    }

    private void disposeCards() {
        requestedThumbnails.clear();
        for (RepositoryCard card : cards.values()) {
            if (!card.isDisposed()) card.disposeImage();
            if (!card.isDisposed()) card.dispose();
        }
        cards.clear();
        if (catalog != null && !catalog.isDisposed()) {
            for (org.eclipse.swt.widgets.Control child : catalog.getChildren()) child.dispose();
        }
    }

    private void disposeImages() {
        cards.values().forEach(RepositoryCard::disposeImage);
        cards.clear();
        if (windowIcon != null && !windowIcon.isDisposed()) windowIcon.dispose();
        windowIcon = null;
    }

    private final class RepositoryCard extends Composite {
        private final GitHubRepository repository;
        private final Canvas thumbnail;
        private final Composite planList;
        private final Composite actions;
        private final Button cloneButton;
        private final List<Button> openButtons = new ArrayList<>();
        private final Label plansSummary;
        private List<String> localPlans = List.of();
        private final Font repositoryNameFont;
        private final Label descriptionLabel;
        private final Label visibilityLabel;
        private final MenuItem viewItem;
        private Image image;

        private RepositoryCard(Composite parent, GitHubRepository repository) {
            super(parent, SWT.BORDER);
            this.repository = repository;
            GridData cardData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            setLayoutData(cardData);
            GridLayout cardLayout = new GridLayout(3, false);
            cardLayout.marginWidth = 12;
            cardLayout.marginHeight = 10;
            cardLayout.horizontalSpacing = 14;
            cardLayout.verticalSpacing = 5;
            setLayout(cardLayout);

            thumbnail = new Canvas(this, SWT.DOUBLE_BUFFERED);
            GridData imageData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
            imageData.widthHint = 200;
            imageData.heightHint = 140;
            thumbnail.setLayoutData(imageData);
            thumbnail.addPaintListener(event -> {
                org.eclipse.swt.graphics.Rectangle bounds = thumbnail.getClientArea();
                if (image != null && !image.isDisposed()) {
                    org.eclipse.swt.graphics.Rectangle size = image.getBounds();
                    event.gc.drawImage(image, (bounds.width - size.width) / 2, (bounds.height - size.height) / 2);
                } else {
                    int width = Math.max(0, bounds.width - 1);
                    int height = Math.max(0, bounds.height - 1);
                    event.gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
                    event.gc.fillRoundRectangle(0, 0, width, height, 12, 12);
                    event.gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
                    event.gc.drawRoundRectangle(0, 0, width, height, 12, 12);
                    event.gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
                    String placeholder = LocalizationService.text("Preview unavailable");
                    org.eclipse.swt.graphics.Point extent = event.gc.textExtent(placeholder);
                    event.gc.drawString(placeholder, (bounds.width - extent.x) / 2,
                            (bounds.height - extent.y) / 2, true);
                }
            });

            Composite details = new Composite(this, SWT.NONE);
            details.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
            GridLayout detailsLayout = new GridLayout(1, false);
            detailsLayout.marginWidth = 0;
            detailsLayout.marginHeight = 0;
            detailsLayout.verticalSpacing = 6;
            details.setLayout(detailsLayout);
            Label name = new Label(details, SWT.WRAP);
            name.setText(repository.getName());
            FontData[] fontData = name.getFont().getFontData();
            for (FontData data : fontData) data.setStyle(data.getStyle() | SWT.BOLD);
            repositoryNameFont = new Font(getDisplay(), fontData);
            name.setFont(repositoryNameFont);
            name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            descriptionLabel = new Label(details, SWT.WRAP);
            descriptionLabel.setText(repository.getDescription().isBlank()
                    ? LocalizationService.text("No description") : repository.getDescription());
            descriptionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            Composite metadata = new Composite(details, SWT.NONE);
            metadata.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            GridLayout metadataLayout = new GridLayout(2, false);
            metadataLayout.marginWidth = 0;
            metadataLayout.marginHeight = 0;
            metadata.setLayout(metadataLayout);
            visibilityLabel = new Label(metadata, SWT.NONE);
            visibilityLabel.setText(LocalizationService.text(repository.isPrivate() ? "Private" : "Public")
                    + "  ·  " + repository.getDefaultBranch());
            visibilityLabel.setToolTipText(LocalizationService.text("Visibility and default branch"));

            planList = new Composite(details, SWT.NONE);
            planList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            GridLayout planLayout = new GridLayout(1, false);
            planLayout.marginWidth = 0;
            planLayout.marginHeight = 0;
            planList.setLayout(planLayout);
            plansSummary = new Label(planList, SWT.WRAP);
            plansSummary.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            plansSummary.setVisible(false);
            ((GridData) plansSummary.getLayoutData()).exclude = true;

            actions = new Composite(this, SWT.NONE);
            actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, true));
            GridLayout actionsLayout = new GridLayout(1, false);
            actionsLayout.marginWidth = 0;
            actionsLayout.marginHeight = 0;
            actionsLayout.verticalSpacing = 4;
            actions.setLayout(actionsLayout);
            cloneButton = new Button(actions, SWT.PUSH);
            cloneButton.setText(LocalizationService.text("Clone"));
            cloneButton.setToolTipText(LocalizationService.text(
                    "Choose a local branch name and clone this repository from its default branch."));
            cloneButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            cloneButton.addListener(SWT.Selection, event -> {
                if (busy) return;
                if (listener == null) {
                    showError("The repository catalog is not connected to its controller.");
                    return;
                }
                try {
                    CloneRepositoryDialog dialog = new CloneRepositoryDialog(
                            RepositoryBrowserView.this.getShell(), repository, connectedLogin,
                            (selected, plan) -> listener.cloneRepository(selected, plan));
                    dialog.open();
                } catch (RuntimeException ex) {
                    PluginLog.error("Browse", "Could not open the branch workspace dialog.", ex);
                    showError("Could not open the branch workspace dialog: "
                            + (ex.getMessage() == null ? "unexpected SWT/JFace error." : ex.getMessage()));
                }
            });
            Menu menu = new Menu(this);
            viewItem = new MenuItem(menu, SWT.PUSH);
            viewItem.setText(LocalizationService.text("View on GitHub"));
            viewItem.addListener(SWT.Selection, event -> {
                if (!busy && listener != null) listener.viewOnGithub(repository);
            });
            setMenu(menu);
            thumbnail.setMenu(menu);
            details.setMenu(menu);
            name.setMenu(menu);
            descriptionLabel.setMenu(menu);
            metadata.setMenu(menu);
        }

        private void relocalize() {
            if (isDisposed()) return;
            descriptionLabel.setText(repository.getDescription().isBlank()
                    ? LocalizationService.text("No description") : repository.getDescription());
            visibilityLabel.setText(LocalizationService.text(repository.isPrivate() ? "Private" : "Public")
                    + "  ·  " + repository.getDefaultBranch());
            visibilityLabel.setToolTipText(LocalizationService.text("Visibility and default branch"));
            cloneButton.setText(LocalizationService.text("Clone"));
            cloneButton.setToolTipText(LocalizationService.text(
                    "Choose a local branch name and clone this repository from its default branch."));
            viewItem.setText(LocalizationService.text("View on GitHub"));
            setPlans(localPlans);
        }

        private void setPlans(List<String> plans) {
            localPlans = List.copyOf(plans);
            for (Button button : openButtons) if (!button.isDisposed()) button.dispose();
            openButtons.clear();
            if (!plans.isEmpty()) {
                plansSummary.setText(LocalizationService.format(
                        "Local branches ({0}): {1}", plans.size(), String.join(", ", plans)));
                plansSummary.setVisible(true);
                ((GridData) plansSummary.getLayoutData()).exclude = false;
                for (String plan : plans) {
                    Button open = new Button(actions, SWT.PUSH);
                    open.setText(LocalizationService.format("Open {0}", plan));
                    open.setToolTipText(LocalizationService.format(
                            "Open the local workspace for branch {0}.", plan));
                    open.setEnabled(!busy);
                    open.addListener(SWT.Selection, event -> {
                        if (!busy && listener != null) listener.openModel(repository, plan);
                    });
                    openButtons.add(open);
                }
            } else {
                plansSummary.setText("");
                plansSummary.setVisible(false);
                ((GridData) plansSummary.getLayoutData()).exclude = true;
            }
            actions.layout(true, true);
            planList.layout(true, true);
            layout(true, true);
        }

        private void setThumbnail(Image value) {
            if (image != null && !image.isDisposed()) image.dispose();
            image = value;
            thumbnail.redraw();
            layout(true, true);
        }

        private void setBusy(boolean value) {
            if (cloneButton != null && !cloneButton.isDisposed()) cloneButton.setEnabled(!value);
            openButtons.forEach(button -> {
                if (!button.isDisposed()) button.setEnabled(!value);
            });
        }

        private void disposeImage() {
            if (image != null && !image.isDisposed()) image.dispose();
            image = null;
            if (repositoryNameFont != null && !repositoryNameFont.isDisposed()) repositoryNameFont.dispose();
        }
    }
}
