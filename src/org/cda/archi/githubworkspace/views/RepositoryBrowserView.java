package org.cda.archi.githubworkspace.views;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.cda.archi.githubworkspace.models.AuthenticationStatus;
import org.cda.archi.githubworkspace.models.GitConfiguration;
import org.cda.archi.githubworkspace.models.GitHubRepository;
import org.cda.archi.githubworkspace.models.RepositorySearchResult;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
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
import org.eclipse.ui.dialogs.PreferencesUtil;

/** Native SWT/JFace visual repository catalog. Effects are delegated to its MVC listener. */
public final class RepositoryBrowserView extends TitleAreaDialog {
    public interface Listener {
        void connect();
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
    private Label accountLabel;
    private Label gitLabel;
    private Label statusLabel;
    private Text search;
    private Button connectButton;
    private Button refreshButton;
    private Button cancelButton;
    private ScrolledComposite scroller;
    private Composite catalog;
    private boolean busy;
    private boolean authenticated;

    public RepositoryBrowserView(Shell parentShell) {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Browse Repositories - GitHub Workspace");
        shell.addListener(SWT.Dispose, event -> {
            disposeImages();
            if (listener != null) listener.closed();
        });
    }

    @Override
    protected org.eclipse.swt.widgets.Control createDialogArea(Composite parent) {
        setTitle("Browse Repositories");
        setMessage("Connect to GitHub to browse repositories configured in Preferences.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite root = new Composite(area, SWT.NONE);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        root.setLayout(new GridLayout(1, false));

        Composite toolbar = new Composite(root, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        toolbar.setLayout(new GridLayout(7, false));
        accountLabel = new Label(toolbar, SWT.NONE);
        accountLabel.setText("GitHub: Not connected");
        gitLabel = new Label(toolbar, SWT.NONE);
        gitLabel.setText("Git: Checking...");
        search = new Text(toolbar, SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL | SWT.BORDER);
        search.setMessage("Filter repositories");
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.addModifyListener(event -> applyFilter());
        refreshButton = new Button(toolbar, SWT.PUSH);
        refreshButton.setText("Refresh");
        refreshButton.addListener(SWT.Selection, event -> {
            if (listener != null && !busy) listener.refresh();
        });
        connectButton = new Button(toolbar, SWT.PUSH);
        connectButton.setText("Connect");
        connectButton.addListener(SWT.Selection, event -> {
            if (listener != null && !busy) listener.connect();
        });
        Button preferencesButton = new Button(toolbar, SWT.PUSH);
        preferencesButton.setText("Preferences...");
        preferencesButton.addListener(SWT.Selection, event -> openPreferences());
        cancelButton = new Button(toolbar, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addListener(SWT.Selection, event -> {
            if (listener != null && busy) listener.cancel();
        });

        statusLabel = new Label(root, SWT.WRAP);
        statusLabel.setText("Configure your organization and topic in Preferences, then Connect.");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (new org.cda.archi.githubworkspace.services.WorkspaceService(preferences)
                .hasLegacyGovernanceWorkspace()) {
            statusLabel.setText("A legacy Governance workspace exists and will not be changed. "
                    + "Connect to browse configured repositories.");
        }

        scroller = new ScrolledComposite(root, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        scroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scroller.setExpandHorizontal(true);
        scroller.setExpandVertical(true);
        catalog = new Composite(scroller, SWT.NONE);
        catalog.setLayout(new GridLayout(2, true));
        scroller.setContent(catalog);
        scroller.addListener(SWT.Resize, event -> scheduleVisibleThumbnails());
        scroller.getVerticalBar().addListener(SWT.Selection, event -> scheduleVisibleThumbnails());
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLOSE_ID, "Close", true);
    }

    public void showGitStatus(GitConfiguration configuration) {
        if (gitLabel == null || gitLabel.isDisposed()) return;
        gitLabel.setText(configuration.isAvailable()
                ? "Git: " + configuration.getVersion()
                : "Git: Not available");
        gitLabel.setToolTipText(configuration.isAvailable()
                ? (configuration.hasCredentialHelper() ? "Git Credential Manager configured"
                        : "No Git credential helper configured")
                : configuration.getMessage());
        gitLabel.getParent().layout(true, true);
    }

    public void setAuthenticationStatus(AuthenticationStatus status) {
        if (accountLabel == null || accountLabel.isDisposed()) return;
        authenticated = status.isAuthenticated();
        accountLabel.setText(status.isAuthenticated()
                ? "GitHub: " + status.getUser().getLogin()
                : "GitHub: " + status.getMessage());
        accountLabel.getParent().layout(true, true);
        connectButton.setEnabled(!busy && !authenticated);
        refreshButton.setEnabled(!busy && authenticated);
    }

    public void setBusy(boolean busy, String message) {
        this.busy = busy;
        if (connectButton != null && !connectButton.isDisposed()) connectButton.setEnabled(!busy && !authenticated);
        if (refreshButton != null && !refreshButton.isDisposed()) refreshButton.setEnabled(!busy && authenticated);
        if (cancelButton != null && !cancelButton.isDisposed()) cancelButton.setEnabled(busy);
        cards.values().forEach(card -> card.setBusy(busy));
        if (busy && statusLabel != null && !statusLabel.isDisposed()) statusLabel.setText(message);
    }

    public void showProgress(String message) {
        if (statusLabel != null && !statusLabel.isDisposed()) statusLabel.setText(message);
    }

    public void showRepositories(RepositorySearchResult result) {
        disposeCards();
        List<GitHubRepository> repositories = result.getRepositories();
        for (GitHubRepository repository : repositories) {
            RepositoryCard card = new RepositoryCard(catalog, repository);
            cards.put(repository.getId(), card);
            if (listener != null) listener.requestLocalPlans(repository);
        }
        catalog.setSize(catalog.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        statusLabel.setText(repositories.isEmpty()
                ? "No authorized repositories matched the configured organization and topic."
                : repositories.size() + " repositories. Thumbnails load as cards enter the viewport.");
        applyFilter();
        scheduleVisibleThumbnails();
    }

    public void showLocalPlans(GitHubRepository repository, List<String> plans) {
        RepositoryCard card = cards.get(repository.getId());
        if (card == null || card.isDisposed()) return;
        card.setPlans(plans);
        catalog.layout(true, true);
        catalog.setSize(catalog.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    public void showThumbnail(GitHubRepository repository, byte[] bytes) {
        try {
            RepositoryCard card = cards.get(repository.getId());
            if (card == null || card.isDisposed() || bytes == null || bytes.length == 0) return;
            ImageData data = new ImageData(new ByteArrayInputStream(bytes));
            if (data.width <= 0 || data.height <= 0 || data.width > 8192 || data.height > 8192
                    || (long) data.width * data.height > 16_000_000) return;
            double factor = Math.min(1.0, Math.min(230.0 / data.width, 145.0 / data.height));
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
        setErrorMessage(message);
        if (statusLabel != null && !statusLabel.isDisposed()) statusLabel.setText(message);
    }

    public void showCancelled() {
        showProgress("Operation cancelled.");
    }

    public void closeAfterModelOpened() {
        if (getShell() != null && !getShell().isDisposed()) close();
    }

    private void openPreferences() {
        try {
            PreferencesUtil.createPreferenceDialogOn(getShell(),
                    GitHubWorkspacePreferencePage.PAGE_ID, null, null).open();
            search.setText("");
            setMessage("Preferences saved. Refresh to use updated repository filters.");
        } catch (RuntimeException ex) {
            showError("Could not open GitHub Workspace Preferences.");
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
            catalog.setSize(catalog.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        }
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
    }

    private final class RepositoryCard extends Composite {
        private final GitHubRepository repository;
        private final Canvas thumbnail;
        private final Composite planList;
        private final Button cloneButton;
        private final List<Button> openButtons = new ArrayList<>();
        private Image image;

        private RepositoryCard(Composite parent, GitHubRepository repository) {
            super(parent, SWT.BORDER);
            this.repository = repository;
            GridData cardData = new GridData(SWT.FILL, SWT.FILL, true, false);
            setLayoutData(cardData);
            setLayout(new GridLayout(2, false));

            thumbnail = new Canvas(this, SWT.BORDER);
            GridData imageData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            imageData.heightHint = 150;
            imageData.horizontalSpan = 2;
            thumbnail.setLayoutData(imageData);
            thumbnail.addPaintListener(event -> {
                org.eclipse.swt.graphics.Rectangle bounds = thumbnail.getClientArea();
                if (image != null && !image.isDisposed()) {
                    org.eclipse.swt.graphics.Rectangle size = image.getBounds();
                    event.gc.drawImage(image, (bounds.width - size.width) / 2, (bounds.height - size.height) / 2);
                } else {
                    event.gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
                    int centerX = bounds.width / 2;
                    int centerY = Math.max(48, bounds.height / 2 - 8);
                    event.gc.drawLine(centerX - 48, centerY - 7, centerX - 18, centerY + 11);
                    event.gc.drawLine(centerX - 18, centerY + 11, centerX + 17, centerY - 11);
                    event.gc.drawLine(centerX + 17, centerY - 11, centerX + 47, centerY + 7);
                    event.gc.drawRectangle(centerX - 58, centerY - 20, 20, 24);
                    event.gc.drawRectangle(centerX - 27, centerY + 2, 20, 24);
                    event.gc.drawRectangle(centerX + 7, centerY - 20, 20, 24);
                    event.gc.drawRectangle(centerX + 37, centerY - 3, 20, 24);
                    String placeholder = "No thumbnail";
                    org.eclipse.swt.graphics.Point extent = event.gc.textExtent(placeholder);
                    event.gc.drawString(placeholder, Math.max(2, centerX - extent.x / 2),
                            Math.min(bounds.height - extent.y - 3, centerY + 35), true);
                }
            });

            Label name = new Label(this, SWT.WRAP);
            name.setText(repository.getName());
            name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
            Label description = new Label(this, SWT.WRAP);
            description.setText(repository.getDescription().isBlank()
                    ? "No description" : repository.getDescription());
            description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
            Label metadata = new Label(this, SWT.WRAP);
            metadata.setText((repository.isPrivate() ? "Private" : "Public")
                    + "  ·  Default branch: " + repository.getDefaultBranch());
            metadata.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

            planList = new Composite(this, SWT.NONE);
            planList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            planList.setLayout(new GridLayout(2, false));
            cloneButton = new Button(this, SWT.PUSH);
            cloneButton.setText("Clone");
            cloneButton.addListener(SWT.Selection, event -> {
                if (!busy && listener != null) new CloneRepositoryDialog(getShell(), repository,
                        (selected, plan) -> listener.cloneRepository(selected, plan)).open();
            });
            Menu menu = new Menu(this);
            MenuItem viewItem = new MenuItem(menu, SWT.PUSH);
            viewItem.setText("View on GitHub");
            viewItem.addListener(SWT.Selection, event -> {
                if (!busy && listener != null) listener.viewOnGithub(repository);
            });
            setMenu(menu);
            thumbnail.setMenu(menu);
            name.setMenu(menu);
            description.setMenu(menu);
            metadata.setMenu(menu);
        }

        private void setPlans(List<String> plans) {
            for (Button button : openButtons) if (!button.isDisposed()) button.dispose();
            openButtons.clear();
            for (org.eclipse.swt.widgets.Control child : planList.getChildren()) child.dispose();
            if (!plans.isEmpty()) {
                Label heading = new Label(planList, SWT.NONE);
                heading.setText("Local Plans: " + plans.size());
                heading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
                for (String plan : plans) {
                    Label planName = new Label(planList, SWT.NONE);
                    planName.setText(plan);
                    Button open = new Button(planList, SWT.PUSH);
                    open.setText("Open Model");
                    open.setEnabled(!busy);
                    open.addListener(SWT.Selection, event -> {
                        if (!busy && listener != null) listener.openModel(repository, plan);
                    });
                    openButtons.add(open);
                }
            }
            planList.layout(true, true);
            layout(true, true);
        }

        private void setThumbnail(Image value) {
            if (image != null && !image.isDisposed()) image.dispose();
            image = value;
            thumbnail.redraw();
            thumbnail.getParent().layout(true, true);
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
        }
    }
}
