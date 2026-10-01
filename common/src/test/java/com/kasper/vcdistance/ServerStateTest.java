package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** What the Server tab gets for its effects and extras sections, and that the commands its buttons send work. */
public class ServerStateTest {

    @TempDir
    Path dir;

    private ServerSettings settings() throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, "");
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    private static AdminCommands.Context ctx(boolean plugin) {
        return new AdminCommands.Context() {
            public String platform() {
                return "Test";
            }

            public boolean plugin() {
                return plugin;
            }

            public int onlinePlayers() {
                return 0;
            }

            public int addonPlayers() {
                return 0;
            }

            public void resendProfiles() {
            }

            public boolean allows(String permission) {
                return true;
            }
        };
    }

    private static Properties state(ServerSettings s) {
        Properties p = new Properties();
        s.writeState(p, "");
        return p;
    }

    @Test
    @DisplayName("The state carries every effect and extra; a strength of an effect that is off is 0")
    void state() throws IOException {
        ServerSettings s = settings();
        Properties p = state(s);
        for (String key : new String[]{"server_effects", "server_air", "server_radio", "server_speakers", "server_eavesdrop",
                "server_sculk", "server_doorway", "server_integrations"}) {
            assertEquals("false", p.getProperty(key), key);
        }
        assertNotNull(p.getProperty("eavesdrop_factor"));

        AdminCommands.run("effects water 80%", s, ctx(true));
        AdminCommands.run("effects weather off", s, ctx(true));
        AdminCommands.run("effects echo 40%", s, ctx(true));
        p = state(s);
        assertEquals(0.8, Double.parseDouble(p.getProperty("water_strength")), 1e-6);
        assertEquals(0.0, Double.parseDouble(p.getProperty("weather_strength")), 1e-6);
        assertEquals(0.4, Double.parseDouble(p.getProperty("echo_strength")), 1e-6);
    }

    @Test
    @DisplayName("The commands the buttons send change what the state shows")
    void buttons() throws IOException {
        ServerSettings s = settings();
        AdminCommands.Context ctx = ctx(true);
        AdminCommands.run("effects on", s, ctx);
        AdminCommands.run("effects air on", s, ctx);
        AdminCommands.run("extras sculk on", s, ctx);
        AdminCommands.run("eavesdrop factor 0.5", s, ctx);
        Properties p = state(s);
        assertEquals("true", p.getProperty("server_effects"));
        assertEquals("true", p.getProperty("server_air"));
        assertEquals("true", p.getProperty("server_sculk"));
        assertEquals(0.5, Double.parseDouble(p.getProperty("eavesdrop_factor")), 1e-6);
        // each of them needs only the settings permission, so a viewer's buttons are greyed
        for (String command : new String[]{"effects water 10%", "effects air on", "extras radio on", "eavesdrop factor 0.1"}) {
            String[] a = command.split(" ");
            assertEquals(AdminCommands.PERM_SETTINGS, AdminCommands.permissionFor(a[0], a[1]), command);
        }
    }

    @Test
    @DisplayName("Only the plugin tells the tab it has the extras")
    void pluginFlag() throws IOException {
        ServerSettings s = settings();
        Map<String, String> plugin = ServerHooks.tabState(s, ctx(true));
        Map<String, String> mod = ServerHooks.tabState(s, ctx(false));
        assertEquals("true", plugin.get("plugin"));
        assertEquals("false", mod.get("plugin"));
    }
}
