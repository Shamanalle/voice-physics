package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigWriterTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Backslashes in a value come back as typed")
    void backslashesRoundTrip() throws IOException {
        String[] values = {"C:\\underworld", "\\o/", "\u00e9 and \\u12", "ends with a slash\\", "\\\\server", "plain"};
        Path file = dir.resolve("values.properties");
        ConfigWriter w = new ConfigWriter();
        for (int i = 0; i < values.length; i++) {
            w.value("k" + i, values[i]);
        }
        Files.writeString(file, w.toString(), StandardCharsets.UTF_8);
        Properties read = ConfigWriter.load(file);
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], read.getProperty("k" + i), "value " + i);
        }
        assertEquals(values.length, read.size(), "a trailing backslash must not swallow the next line");
    }

    @Test
    @DisplayName("A malformed escape is an IOException, which callers handle by keeping their settings")
    void malformedEscape() throws IOException {
        Path file = dir.resolve("broken.properties");
        Files.writeString(file, "zone.x.message=C:\\underworld\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ConfigWriter.load(file));
    }
}
