package picosoft.javafro.ui;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Opens a single dropped YAML file while preserving the editor's text clipboard actions. */
final class YamlFileTransferHandler extends TransferHandler {
    private final TransferHandler textHandler;
    private final BooleanSupplier available;
    private final Consumer<Path> open;
    private final Consumer<Exception> error;

    YamlFileTransferHandler(TransferHandler textHandler, BooleanSupplier available,
                            Consumer<Path> open, Consumer<Exception> error) {
        this.textHandler = textHandler;
        this.available = available;
        this.open = open;
        this.error = error;
    }

    private Path file(Transferable data) {
        try {
            if (!available.getAsBoolean() || !data.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null;
            Object value = data.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(value instanceof List<?> files) || files.size() != 1 || !(files.getFirst() instanceof File item)) return null;
            Path path = item.toPath();
            String name = item.getName().toLowerCase(Locale.ROOT);
            return Files.isRegularFile(path) && (name.endsWith(".yaml") || name.endsWith(".yml")) ? path : null;
        } catch (Exception exception) { return null; }
    }

    @Override
    public boolean canImport(TransferSupport support) {
        // Native file data may only be readable once the drop has been accepted.
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            if (!available.getAsBoolean()) return false;
            if (support.isDrop()) {
                if ((support.getSourceDropActions() & COPY) == 0) return false;
                support.setDropAction(COPY); // Open the file; never ask the source to move/delete it.
            }
            return true;
        }
        return textHandler != null && textHandler.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return textHandler != null && textHandler.importData(support);
        }
        Path path = file(support.getTransferable());
        if (path == null) return false;
        try {
            open.accept(path);
            return true;
        } catch (Exception exception) {
            error.accept(exception);
            return false;
        }
    }

    @Override
    public boolean canImport(JComponent component, DataFlavor[] flavors) {
        for (DataFlavor flavor : flavors) if (flavor.equals(DataFlavor.javaFileListFlavor)) return available.getAsBoolean();
        return textHandler != null && textHandler.canImport(component, flavors);
    }

    @Override
    public boolean importData(JComponent component, Transferable data) {
        return importData(new TransferSupport(component, data));
    }

    @Override
    public int getSourceActions(JComponent component) {
        return textHandler == null ? NONE : textHandler.getSourceActions(component);
    }

    @Override
    public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (textHandler != null) textHandler.exportToClipboard(component, clipboard, action);
    }

    @Override
    public void exportAsDrag(JComponent component, InputEvent event, int action) {
        if (textHandler != null) textHandler.exportAsDrag(component, event, action);
    }
}
