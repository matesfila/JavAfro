package picosoft.javafro;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class AfroJavaPlayerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void readsUtf8FromDiskWithSpacesAndAccentsInPath() throws Exception {
        var file = temporary.newFile("Môj rytmus.yaml").toPath();
        String yaml = "rytmus:\n";
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        assertEquals(yaml, AfroJavaPlayer.readYaml(new String[]{file.toString()}));
        Files.writeString(file, "upravený obsah", StandardCharsets.UTF_8);
        assertEquals("upravený obsah", AfroJavaPlayer.readYaml(new String[]{file.toString()}));
    }

    @Test
    public void allowsEmptyEditorButRejectsInvalidArguments() throws Exception {
        assertEquals("", AfroJavaPlayer.readYaml(new String[]{}));
        assertThrows(IllegalArgumentException.class, () -> AfroJavaPlayer.readYaml(new String[]{" "}));
        assertThrows(IllegalArgumentException.class, () -> AfroJavaPlayer.readYaml(new String[]{"a.yaml", "b.yaml"}));
    }

    @Test
    public void rejectsMissingFilesAndDirectories() {
        assertThrows(IOException.class, () -> AfroJavaPlayer.readYaml(new String[]{temporary.getRoot().toString()}));
        assertThrows(IOException.class, () -> AfroJavaPlayer.readYaml(new String[]{temporary.getRoot() + "/missing.yaml"}));
    }
}
