package org.cda.archi.governance.views;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import org.cda.archi.governance.models.GitHubRepository;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/** Authorized repository table and browser action. */
public final class RepositoryListView extends Composite {
    private static final DateTimeFormatter UPDATED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final TableViewer viewer;
    private final Label resultsLabel;
    private final Button openButton;
    private Consumer<GitHubRepository> openListener;

    public RepositoryListView(Composite parent) {
        super(parent, SWT.NONE);
        setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        setLayout(new GridLayout(1, false));

        resultsLabel = new Label(this, SWT.NONE);
        resultsLabel.setText("Connect to GitHub to search repositories.");
        resultsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        viewer = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        viewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        addColumn("Repository name", 185, GitHubRepository::getFullName);
        addColumn("Description", 270, GitHubRepository::getDescription);
        addColumn("Visibility", 85, GitHubRepository::getVisibility);
        addColumn("Default branch", 120, GitHubRepository::getDefaultBranch);
        addColumn("Last updated", 140, repository -> repository.getUpdatedAt() == null
                ? "" : UPDATED_FORMAT.format(repository.getUpdatedAt()));

        Composite actions = new Composite(this, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        actions.setLayout(new GridLayout(2, false));
        openButton = new Button(actions, SWT.PUSH);
        openButton.setText("Open selected repository");
        openButton.setEnabled(false);
        openButton.addListener(SWT.Selection, event -> openSelection());
        viewer.addSelectionChangedListener(event -> openButton.setEnabled(!event.getSelection().isEmpty()));
        viewer.addDoubleClickListener(event -> openSelection());
    }

    public void setOpenListener(Consumer<GitHubRepository> listener) { this.openListener = listener; }

    public void showLoading() {
        if (isDisposed()) return;
        viewer.setInput(List.of());
        resultsLabel.setText("Searching authorized repositories…");
        openButton.setEnabled(false);
    }

    public void showRepositories(List<GitHubRepository> repositories, long totalCount) {
        if (isDisposed()) return;
        viewer.setInput(repositories);
        resultsLabel.setText(repositories.isEmpty()
                ? "Search completed successfully: no authorized repositories matched."
                : "Complete search: " + repositories.size() + " authorized repositories.");
        openButton.setEnabled(false);
    }

    public void showMessage(String message) {
        if (isDisposed()) return;
        viewer.setInput(List.of());
        resultsLabel.setText(message);
        openButton.setEnabled(false);
    }

    private void openSelection() {
        Object selected = ((IStructuredSelection) viewer.getSelection()).getFirstElement();
        if (selected instanceof GitHubRepository repository && openListener != null) {
            openListener.accept(repository);
        }
    }

    private void addColumn(String title, int width,
            java.util.function.Function<GitHubRepository, String> text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(new ColumnLabelProvider() {
            @Override public String getText(Object element) {
                return text.apply((GitHubRepository) element);
            }
        });
    }
}
