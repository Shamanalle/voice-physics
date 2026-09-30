package com.kasper.vcdistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The eavesdrop item and the sculk reaction to shouts. */
public class EavesdropSculkTest {

    @TempDir
    Path dir;

    private static final UUID ANNA = UUID.nameUUIDFromBytes("Anna".getBytes());

    @AfterEach
    void clear() {
        AudioDistancePlugin.PLAYER_PREFS.reset(ANNA);
        AudioDistancePlugin.PLAYER_PREFS.forget(ANNA);
        ServerSculk.forget(ANNA);
    }

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    private static ServerPlayers.Info anna(String hand, boolean sneaking, boolean alive) {
        return new ServerPlayers.Info(ANNA, "Anna", "world", 0, 64, 0, sneaking, alive, false, hand, "", List.of(), "");
    }

    @Test
    @DisplayName("Eavesdrop: off by default, needs the item, factor is limited, settings are kept in the file")
    void eavesdrop() throws IOException {
        ServerSettings s = settings("");
        assertEquals("minecraft:spyglass", s.getEavesdropItem());
        assertEquals(0.3, s.getEavesdropFactor());
        assertEquals(1.0, ServerEavesdrop.factor(s, anna("minecraft:spyglass", false, true)), "the switch is off");

        s.setServerEavesdrop(true);
        assertEquals(0.3, ServerEavesdrop.factor(s, anna("minecraft:spyglass", false, true)));
        assertEquals(1.0, ServerEavesdrop.factor(s, anna("minecraft:stick", false, true)), "without the item");
        assertEquals(1.0, ServerEavesdrop.factor(s, null));

        s.setEavesdropFactor(0.0);
        assertEquals(ServerSettings.MIN_EAVESDROP_FACTOR, s.getEavesdropFactor());
        s.setEavesdropFactor(7);
        assertEquals(1.0, s.getEavesdropFactor());
        s.setEavesdropItem("");
        assertEquals(1.0, ServerEavesdrop.factor(s, anna("", false, true)), "an empty item means nobody can");

        s.setEavesdropItem("minecraft:amethyst_shard");
        s.setEavesdropFactor(0.5);
        s.save();
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals("minecraft:amethyst_shard", again.getEavesdropItem());
        assertEquals(0.5, again.getEavesdropFactor());
    }

    @Test
    @DisplayName("/vcd eavesdrop: the view, item, factor, bad values, undo, permissions and Tab")
    void command() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = new AdminCommands.Context() {
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
                return true;
            }
        };
        List<String> view = AdminCommands.run("eavesdrop", s, ctx);
        assertTrue(view.stream().anyMatch(l -> l.contains("Eavesdropping: off")), view.toString());
        assertTrue(view.stream().anyMatch(l -> l.contains("minecraft:spyglass")), view.toString());

        AdminCommands.run("eavesdrop factor 0.5", s, ctx);
        assertEquals(0.5, s.getEavesdropFactor());
        AdminCommands.run("eavesdrop factor 5", s, ctx);
        AdminCommands.run("eavesdrop factor abc", s, ctx);
        assertEquals(0.5, s.getEavesdropFactor(), "nonsense changes nothing");
        AdminCommands.run("undo", s, ctx);
        assertEquals(0.3, s.getEavesdropFactor());

        AdminCommands.run("eavesdrop item minecraft:bell", s, ctx);
        assertEquals("minecraft:bell", s.getEavesdropItem());
        AdminCommands.run("eavesdrop item none", s, ctx);
        assertEquals("", s.getEavesdropItem());
        AdminCommands.run("extras eavesdrop on", s, ctx);
        assertTrue(s.isServerEavesdrop());

        assertEquals(AdminCommands.PERM_STATUS, AdminCommands.permissionFor("eavesdrop", ""));
        assertEquals(AdminCommands.PERM_SETTINGS, AdminCommands.permissionFor("eavesdrop", "factor"));
        assertEquals(List.of("item", "factor"), AdminCommands.suggest("eavesdrop "));
        assertTrue(AdminCommands.suggest("eavesdrop item minecraft:s").contains("minecraft:spyglass"));
        assertTrue(CommandHelp.EXAMPLES.containsKey("eavesdrop"));
    }

    @Test
    @DisplayName("Sculk: only shouts and megaphones, not whispers, sneaking, talking or the dead; once per cooldown")
    void sculk() throws IOException {
        ServerSettings s = settings("");
        PlayerPrefs prefs = new PlayerPrefs();
        prefs.setMode(ANNA, PlayerPrefs.Mode.SHOUT);
        assertFalse(ServerSculk.loud(s, prefs, anna("", false, true), false), "the switch is off");

        s.setServerSculk(true);
        assertTrue(ServerSculk.loud(s, prefs, anna("", false, true), false));
        assertFalse(ServerSculk.loud(s, prefs, anna("", false, true), true), "a whisper");
        assertFalse(ServerSculk.loud(s, prefs, anna("", true, true), false), "sneaking");
        assertFalse(ServerSculk.loud(s, prefs, anna("", false, false), false), "dead");
        assertFalse(ServerSculk.loud(s, new PlayerPrefs(), anna("", false, true), false), "ordinary talking");

        s.setMegaphoneItem("minecraft:goat_horn");
        assertTrue(ServerSculk.loud(s, new PlayerPrefs(), anna("minecraft:goat_horn", false, true), false), "a megaphone");

        assertTrue(ServerSculk.due(0, 5));
        assertFalse(ServerSculk.due(1_000, 1_000 + ServerSculk.COOLDOWN_NANOS - 1));
        assertTrue(ServerSculk.due(1_000, 1_000 + ServerSculk.COOLDOWN_NANOS));

        ServerSculk.report(ANNA, 10L);
        ServerSculk.report(ANNA, 20L);
        assertEquals(List.of(ANNA), ServerSculk.drain(), "once, the second report was inside the cooldown");
        assertTrue(ServerSculk.drain().isEmpty());
        ServerSculk.report(ANNA, 20L + ServerSculk.COOLDOWN_NANOS);
        assertEquals(List.of(ANNA), ServerSculk.drain());
    }
}
