package picosoft.javafro.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Help PDFs bundled from web/public (same files as the web app's Súbor menu).
 * A PDF inside the JAR cannot be opened directly, so it is copied to a temporary file first.
 */
final class HelpDocuments {
    enum Document {
        GUIDE("Dokumentácia", "navod-pre-zaciatocnikov.pdf"),
        VISUAL_GUIDE("Obrázkový návod", "obrazovy-navod.pdf");

        final String title;
        final String file;
        Document(String title, String file) { this.title = title; this.file = file; }
    }

    interface Opener { void open(Path file) throws IOException; }

    private final Opener opener;

    HelpDocuments() { this(HelpDocuments::openWithDesktop); }

    HelpDocuments(Opener opener) { this.opener = opener; }

    /** Copies the bundled PDF to a temporary file and hands it to the system viewer. */
    Path open(Document document) throws IOException {
        Path file;
        try (InputStream input = HelpDocuments.class.getResourceAsStream("/docs/" + document.file)) {
            if (input == null) throw new IOException("V aplikácii chýba súbor " + document.file + ".");
            file = Files.createTempFile("javafro-" + document.file.replace(".pdf", "") + "-", ".pdf");
            file.toFile().deleteOnExit();
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
        opener.open(file);
        return file;
    }

    private static void openWithDesktop(Path file) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Systém nevie otvoriť PDF. Súbor je uložený tu:\n" + file);
        }
        Desktop.getDesktop().open(file.toFile());
    }
}
