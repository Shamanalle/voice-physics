package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The extras of the Paper plugin: five switches, all off until played, in the server file and in /vcd extras. */
public class ExtrasTest {

    @TempDir
    Path dir;

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    private static AdminCommands.Context ctx(String... permissions) {
        List<String> allowed = List.of(permissions);
        return new AdminCommands.Context() {
            public String platform() {
                return "Test";
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
                return allowed.isEmpty() || allowed.contains(permission);
            }
        };
    }

    @Test
    @DisplayName("Every extra is off by default, is written to the file and read back")
    void file() throws IOException {
        ServerSettings s = settings("");
        for (String name : AdminCommands.EXTRAS) {
            assertFalse(AdminCommands.extra(s, name), name);
        }
        String file = Files.readString(s.getPath());
        for (String key : new String[]{"server_radio", "server_speakers", "server_eavesdrop", "server_sculk", "server_doorway"}) {
            assertTrue(file.contains(key + "=false"), key);
        }
        ServerSettings on = settings("server_radio=true\nserver_doorway=true\nserver_sculk=maybe\n");
        assertTrue(on.isServerRadio());
        assertTrue(on.isServerDoorway());
        assertFalse(on.isServerSculk(), "a value that is not true/false keeps the default");
        assertFalse(on.isServerSpeakers());
        on.setServerSpeakers(true);
        on.save();
        ServerSettings again = new ServerSettings(on.getPath());
        again.load();
        assertTrue(again.isServerRadio() && again.isServerSpeakers() && again.isServerDoorway());
        assertFalse(again.isServerEavesdrop() || again.isServerSculk());
    }

    @Test
    @DisplayName("/vcd extras: view, switch, refuse nonsense, undo")
    void command() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        List<String> view = AdminCommands.run("extras", s, ctx);
        assertEquals(AdminCommands.EXTRAS.length + 2, view.size(), view.toString());
        assertTrue(view.stream().anyMatch(l -> l.contains("Radio") && l.contains("off")), view.toString());

        List<String> on = AdminCommands.run("extras radio on", s, ctx);
        assertTrue(s.isServerRadio());
        assertTrue(on.stream().anyMatch(l -> l.contains("Radio is on")), on.toString());
        assertTrue(on.stream().anyMatch(l -> l.contains("not been tried")), "says plainly that it is untested: " + on);

        assertTrue(Files.readString(s.getPath()).contains("server_radio=true"));
        AdminCommands.run("extras radio off", s, ctx);
        assertFalse(s.isServerRadio());

        AdminCommands.run("extras doorway on", s, ctx);
        assertTrue(s.isServerDoorway());
        AdminCommands.run("undo", s, ctx);
        assertFalse(s.isServerDoorway(), "undo takes the switch back");

        List<String> bad = AdminCommands.run("extras laser on", s, ctx);
        assertTrue(bad.get(0).contains("laser") && bad.get(0).contains("radio|speakers"), bad.toString());
        List<String> missing = AdminCommands.run("extras sculk maybe", s, ctx);
        assertTrue(missing.get(0).contains("on|off"), missing.toString());
        assertFalse(s.isServerSculk());
    }

    @Test
    @DisplayName("/vcd extras: looking needs vcd.status, changing needs vcd.settings; Tab completes names and on/off")
    void permissionsAndTab() {
        assertEquals(AdminCommands.PERM_STATUS, AdminCommands.permissionFor("extras", ""));
        assertEquals(AdminCommands.PERM_STATUS, AdminCommands.permissionFor("extras", "status"));
        assertEquals(AdminCommands.PERM_SETTINGS, AdminCommands.permissionFor("extras", "radio"));
        assertEquals(List.of("radio", "speakers", "eavesdrop", "sculk", "doorway", "integrations"), AdminCommands.suggest("extras "));
        assertEquals(List.of("sculk", "speakers"), AdminCommands.suggest("extras s").stream().sorted().toList());
        assertEquals(List.of("on", "off"), AdminCommands.suggest("extras radio "));
        assertTrue(AdminCommands.suggest("ext").contains("extras"));
        assertTrue(CommandHelp.EXAMPLES.containsKey("extras"));
    }
}
