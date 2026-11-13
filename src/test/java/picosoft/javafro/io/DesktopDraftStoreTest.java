package picosoft.javafro.io;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public class DesktopDraftStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void restoresRawInvalidAndEmptyTextCaretControlsAndPreviousDocument() throws Exception {
        var directory = temporary.getRoot().toPath();
        var draft = new DesktopDraftStore.Draft("Koncept: [é", "C:/Môj rytmus.yaml", 8, "pôvodný text",
                new DesktopDraftStore.Controls("Koncept", 143, List.of(0., -.2, 0., .2)));
        var first = new DesktopDraftStore(directory);
        first.save(draft, false);
        var reloaded = new DesktopDraftStore(directory);
        assertEquals(draft, reloaded.current());
        assertTrue(reloaded.current().dirty());
        var empty = new DesktopDraftStore.Draft("", "", 0, "", null);
        reloaded.save(empty, true);
        var afterReplacement = new DesktopDraftStore(directory);
        assertEquals(empty, afterReplacement.current());
        assertEquals(draft, afterReplacement.previous());
        assertFalse(afterReplacement.current().dirty());
    }

    @Test public void refusesToOverwriteNewerWorkFromAnotherWindow() throws Exception {
        var directory = temporary.getRoot().toPath();
        var first = new DesktopDraftStore(directory);
        var second = new DesktopDraftStore(directory);
        var newest = new DesktopDraftStore.Draft("novší text", "", 3, "", null);
        first.save(newest, false);
        byte[] before = Files.readAllBytes(directory.resolve("desktop-draft.yaml"));
        assertThrows(IOException.class, () -> second.save(new DesktopDraftStore.Draft("staršie okno", "", 0, "", null), false));
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("desktop-draft.yaml")));
        assertEquals(newest, new DesktopDraftStore(directory).current());
    }

    @Test public void damagedAndUnknownRecordsArePreserved() throws Exception {
        var directory = temporary.getRoot().toPath();
        var file = directory.resolve("desktop-draft.yaml");
        for (String broken : List.of("version: 8\n", "version: 1\ncurrent: [", "version: 1\ncurrent: {source: hi, caret: -1}")) {
            Files.writeString(file, broken);
            assertThrows(IOException.class, () -> new DesktopDraftStore(directory));
            assertEquals(broken, Files.readString(file));
        }
    }

    @Test public void failedWriteDoesNotAdvanceInMemoryHistory() throws Exception {
        var directory = temporary.getRoot().toPath();
        var store = new DesktopDraftStore(directory);
        var draft = new DesktopDraftStore.Draft("work", "", 0, "", null);
        store.save(draft, false);
        Files.delete(directory.resolve("desktop-draft.lock"));
        Files.createDirectory(directory.resolve("desktop-draft.lock"));
        assertThrows(IOException.class, () -> store.save(new DesktopDraftStore.Draft("other", "", 0, "", null), true));
        assertEquals(draft, store.current());
        assertNull(store.previous());
        assertEquals(draft, new DesktopDraftStore(directory).current());
    }
}
