package com.kasper.vcdistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What {@code /voicephysics report} copies. */
public class ClientReportTest {

    @AfterEach
    void clear() {
        Problems.clear();
    }

    @Test
    @DisplayName("Without the server part: versions, own settings, range, load, problems")
    void noServer() {
        DistanceConfig own = new DistanceConfig();
        List<String> lines = ClientReport.lines("1.21.4", own, new ServerLink(), 48.0);
        String text = String.join("\n", lines);
        assertTrue(lines.get(0).startsWith("Voice Physics " + BuildInfo.version()), text);
        assertTrue(lines.get(0).endsWith("on Minecraft 1.21.4"), text);
        assertTrue(text.contains("Server: no Voice Physics on the server"), text);
        assertTrue(text.contains("Range: voice 48"), text);
        assertTrue(text.contains("Own: preset "), text);
        assertTrue(text.contains("Problems: none since the start"), text);
        assertFalse(text.contains("In effect (server)"), "nothing enforced without a server");
        assertTrue(lines.get(lines.size() - 1).startsWith("Made "), text);

        assertFalse(ClientReport.lines("", own, new ServerLink(), 48.0).get(0).contains("Minecraft"), "unknown game version left out");

        Problems.record("Tracer failed: IllegalStateException");
        Problems.record("Tracer failed: IllegalStateException");
        text = String.join("\n", ClientReport.lines("", own, new ServerLink(), 48.0));
        assertTrue(text.contains("Problems (newest first):"), text);
        assertTrue(text.contains("x2 Tracer failed"), text);
    }

    @Test
    @DisplayName("One line for the settings that change the sound")
    void describe() {
        DistanceConfig c = new DistanceConfig();
        String line = ClientReport.describe(c);
        for (String part : new String[]{"model ", "walls ", "corners ", "echo ", "water ", "weather ", "block rules 0"}) {
            assertTrue(line.contains(part), line);
        }
    }
}
