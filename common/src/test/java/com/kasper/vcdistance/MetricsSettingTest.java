package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code metrics} switch for bStats in the server settings file. */
public class MetricsSettingTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("On by default, written with its explanation, off when the file says so, and kept through a save")
    void metrics() throws Exception {
        Path file = dir.resolve("server.properties");
        ServerSettings first = new ServerSettings(file);
        first.load();
        assertTrue(first.isMetrics());
        first.save();
        String text = Files.readString(file);
        assertTrue(text.contains("# ---------- 11. Statistics"), text);
        assertTrue(text.contains("bstats.org"), "says where the numbers go");
        assertTrue(text.contains("No names, addresses or chat."), "and what is left out");
        assertTrue(text.contains("\nmetrics=true"), text);

        Files.writeString(file, text.replace("\nmetrics=true", "\nmetrics=false"));
        ServerSettings off = new ServerSettings(file);
        off.load();
        assertFalse(off.isMetrics());
        off.save();
        ServerSettings again = new ServerSettings(file);
        again.load();
        assertFalse(again.isMetrics(), "a save keeps the choice");
    }
}
