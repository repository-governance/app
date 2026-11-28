package org.cda.archi.githubworkspace.views;

import java.io.IOException;

import org.cda.archi.githubworkspace.infrastructure.PluginLog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Displays the complete persisted diagnostic log in a selectable multiline text area. */
public final class PluginLogDialog extends TitleAreaDialog {
    private Text logText;

    public PluginLogDialog(Shell parent) {
        super(parent);
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
        setBlockOnOpen(true);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("GitHub Workspace Log");
        setMessage("Diagnostic trace saved at " + pathLabel() + ". Tokens and credentials are not logged.");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setLayout(new GridLayout(1, false));

        logText = new Text(content, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData logData = new GridData(SWT.FILL, SWT.FILL, true, true);
        logData.widthHint = 900;
        logData.heightHint = 520;
        logText.setLayoutData(logData);
        try {
            String log = PluginLog.readAll();
            logText.setText(log.isEmpty() ? "No log entries yet." : log);
        } catch (IOException ex) {
            logText.setText("Could not read the log file: " + ex.getMessage());
        }
        logText.setSelection(logText.getCharCount());
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLOSE_ID, "Close", true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CLOSE_ID) close();
        else super.buttonPressed(buttonId);
    }

    private static String pathLabel() {
        try {
            return PluginLog.logFile().toString();
        } catch (IOException ex) {
            return "the user profile log directory";
        }
    }
}
