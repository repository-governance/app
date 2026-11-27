package org.cda.archi.governance.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.services.EditorManager;
import com.archimatetool.editor.views.tree.TreeModelView;
import com.archimatetool.model.IArchimateModel;

/**
 * Opens models through Archi 5.11.0's own File > Open model manager, preserving the
 * physical source path and native model/editor lifecycle.
 */
public final class ArchiModelService {
    public interface NativeAccess {
        Path findLoaded(Path normalizedPhysicalPath) throws IOException;
        Path openAndActivate(Path normalizedPhysicalPath) throws IOException;
        void activate(Path normalizedPhysicalPath) throws IOException;
    }

    private final NativeAccess nativeAccess;

    public ArchiModelService() {
        this(new ArchiNativeAccess());
    }

    public ArchiModelService(NativeAccess nativeAccess) {
        this.nativeAccess = nativeAccess;
    }

    /** Must be called on Archi's SWT UI thread. */
    public Path openOrActivate(Path modelFile) throws IOException {
        Path physical = validateModelPath(modelFile);
        Path existing = nativeAccess.findLoaded(physical);
        if (existing != null) {
            if (!existing.toRealPath().equals(physical)) {
                throw new IOException("Archi returned a different already-open model path.");
            }
            nativeAccess.activate(physical);
            return physical;
        }
        Path opened = nativeAccess.openAndActivate(physical);
        if (opened == null || !opened.toRealPath().equals(physical)) {
            throw new IOException("Archi could not open source/design.archimate as a native model.");
        }
        return physical;
    }

    private static Path validateModelPath(Path file) throws IOException {
        if (file == null || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("source/design.archimate is missing or is not a regular file.");
        }
        return file.toRealPath();
    }

    private static final class ArchiNativeAccess implements NativeAccess {
        private static final String MODEL_TREE_VIEW = "com.archimatetool.editor.treeModelView";

        @Override
        public Path findLoaded(Path physical) throws IOException {
            for (IArchimateModel model : IEditorModelManager.INSTANCE.getModels()) {
                File file = model.getFile();
                if (file != null && samePhysicalFile(file.toPath(), physical)) return file.toPath().toRealPath();
            }
            return null;
        }

        @Override
        public Path openAndActivate(Path physical) throws IOException {
            if (Display.getCurrent() == null || !PlatformUI.isWorkbenchRunning()) {
                throw new IOException("The Archi workbench is not available on the SWT UI thread.");
            }
            final IArchimateModel[] opened = new IArchimateModel[1];
            BusyIndicator.showWhile(Display.getCurrent(),
                    () -> opened[0] = IEditorModelManager.INSTANCE.openModel(physical.toFile()));
            if (opened[0] == null) return null;
            activateModel(opened[0]);
            return opened[0].getFile().toPath().toRealPath();
        }

        @Override
        public void activate(Path physical) throws IOException {
            for (IArchimateModel model : IEditorModelManager.INSTANCE.getModels()) {
                File file = model.getFile();
                if (file != null && samePhysicalFile(file.toPath(), physical)) {
                    activateModel(model);
                    return;
                }
            }
            throw new IOException("The already-open Archi model is no longer available.");
        }

        private static void activateModel(IArchimateModel model) throws IOException {
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            IViewPart part;
            try {
                part = page.showView(MODEL_TREE_VIEW, null, IWorkbenchPage.VIEW_ACTIVATE);
            } catch (org.eclipse.ui.PartInitException ex) {
                throw new IOException("Archi could not activate the Models tree.", ex);
            }
            if (part instanceof TreeModelView tree) {
                tree.getViewer().setSelection(new StructuredSelection(model), true);
                tree.setFocus();
            }
            if (!model.getDiagramModels().isEmpty()) {
                EditorManager.openDiagramEditor(model.getDiagramModels().get(0), true);
            }
        }

        private static boolean samePhysicalFile(Path first, Path second) throws IOException {
            try {
                return first.toRealPath().equals(second.toRealPath());
            } catch (java.nio.file.NoSuchFileException ex) {
                return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
            }
        }
    }
}
