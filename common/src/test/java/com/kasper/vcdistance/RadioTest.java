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

/** The radio: frequencies in the players' file, who gets a copy of a voice, /voice radio and /vcd radio. */
public class RadioTest {

    @TempDir
    Path dir;

    private static final UUID ANNA = UUID.nameUUIDFromBytes("Anna".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("Bob".getBytes());
    private static final UUID CARL = UUID.nameUUIDFromBytes("Carl".getBytes());

    @AfterEach
    void clear() {
        for (UUID id : new UUID[]{ANNA, BOB, CARL}) {
            AudioDistancePlugin.PLAYER_PREFS.reset(id);
            AudioDistancePlugin.PLAYER_PREFS.forget(id);
        }
        AudioDistancePlugin.PLAYERS.clear();
        AudioDistancePlugin.SERVER_SETTINGS.setServerRadio(false);
        AudioDistancePlugin.SERVER_SETTINGS.setRadioItem("");
    }

    private static ServerPlayers.Info player(String name, double x, String hand) {
        return new ServerPlayers.Info(UUID.nameUUIDFromBytes(name.getBytes()), name, "world", x, 64, 0,
                false, true, false, hand, "", List.of(), "");
    }

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    @Test
    @DisplayName("A frequency is stored with the player's choices, read back, and only 1-9999 counts")
    void file() {
        Path file = dir.resolve(PlayerPrefs.FILE);
        PlayerPrefs a = new PlayerPrefs();
        a.load(file);
        assertFalse(a.anyRadio());
        a.setRadio(ANNA, 1200);
        a.setRadio(BOB, 1200);
        a.setRadio(CARL, 77);
        assertTrue(a.anyRadio());
        assertEquals("{77=1, 1200=2}", a.radioChannels().toString());

        PlayerPrefs b = new PlayerPrefs();
        b.load(file);
        assertEquals(1200, b.radioOf(ANNA));
        assertEquals(77, b.radioOf(CARL));
        assertTrue(b.anyRadio());

        b.setRadio(ANNA, 0);
        b.setRadio(BOB, 10000);
        b.setRadio(CARL, -5);
        assertFalse(b.anyRadio(), "out of range and 0 turn the radio off");
        assertEquals(0, b.radioOf(BOB));

        b.setRadio(ANNA, 5);
        b.reset(ANNA);
        assertEquals(0, b.radioOf(ANNA), "back to the defaults switches the radio off too");
        assertEquals(PlayerPrefs.Prefs.DEFAULT, PlayerPrefs.decode("radio:junk"), "a damaged entry leaves the radio off");
    }

    @Test
    @DisplayName("The switch, the item and the frequency decide who uses the radio")
    void frequency() throws IOException {
        ServerSettings s = settings("");
        PlayerPrefs prefs = new PlayerPrefs();
        prefs.setRadio(ANNA, 500);
        ServerPlayers.Info empty = player("Anna", 0, "");
        ServerPlayers.Info clock = player("Anna", 0, "minecraft:clock");

        assertEquals(0, ServerRadio.frequency(s, prefs, empty), "the switch is off by default");
        s.setServerRadio(true);
        assertEquals(500, ServerRadio.frequency(s, prefs, empty), "no item needed");
        s.setRadioItem("minecraft:clock");
        assertEquals(0, ServerRadio.frequency(s, prefs, empty));
        assertEquals(500, ServerRadio.frequency(s, prefs, clock));
        assertEquals(0, ServerRadio.frequency(s, prefs, player("Bob", 0, "minecraft:clock")), "Bob did not tune his radio");
    }

    @Test
    @DisplayName("Who gets the voice: the same frequency, from anywhere, except players close enough to hear it anyway")
    void receivers() throws IOException {
        ServerSettings s = settings("server_radio=true\n");
        PlayerPrefs prefs = new PlayerPrefs();
        ServerPlayers players = new ServerPlayers();
        ServerPlayers.Info anna = player("Anna", 0, "");
        ServerPlayers.Info bob = player("Bob", 500, "");
        ServerPlayers.Info carl = player("Carl", 10, "");
        ServerPlayers.Info dora = player("Dora", 900, "");
        for (ServerPlayers.Info p : List.of(anna, bob, carl, dora)) {
            players.update(p);
        }
        prefs.setRadio(anna.id(), 1);
        prefs.setRadio(bob.id(), 1);
        prefs.setRadio(carl.id(), 1);
        prefs.setRadio(dora.id(), 2);

        List<ServerPlayers.Info> got = ServerRadio.receivers(s, prefs, players, anna, 48.0);
        assertEquals(List.of(bob), got, "Carl is 10 blocks away and hears her anyway; Dora is on another frequency");

        prefs.setVolume(bob.id(), anna.id(), 0);
        assertTrue(ServerRadio.receivers(s, prefs, players, anna, 48.0).isEmpty(), "a listener who ignores the speaker gets nothing");
        prefs.setVolume(bob.id(), anna.id(), 100);

        // A dead speaker is silent when the server says dead players are
        ServerPlayers.Info dead = new ServerPlayers.Info(anna.id(), "Anna", "world", 0, 64, 0, false, false, false, "", "", List.of(), "");
        players.update(dead);
        assertEquals(List.of(bob), ServerRadio.receivers(s, prefs, players, dead, 48.0));
        s.setDeadSilent(true);
        assertTrue(ServerRadio.receivers(s, prefs, players, dead, 48.0).isEmpty());

        // Another world is no reason not to hear the radio
        ServerPlayers.Info far = new ServerPlayers.Info(bob.id(), "Bob", "world_nether", 0, 64, 0, false, true, false, "", "", List.of(), "");
        players.update(far);
        assertEquals(List.of(far), ServerRadio.receivers(s, prefs, players, anna, 48.0));
    }

    @Test
    @DisplayName("/voice radio: refused while the switch is off, tunes, refuses nonsense, says what to hold, undoes")
    void voiceCommand() {
        ServerSettings live = AudioDistancePlugin.SERVER_SETTINGS;
        String off = String.join(" ", PlayerCommands.run(ANNA, "", "radio 1200", p -> true));
        assertTrue(off.contains("not turned the radio on"), off);
        assertEquals(0, AudioDistancePlugin.PLAYER_PREFS.radioOf(ANNA));

        live.setServerRadio(true);
        live.setRadioItem("minecraft:clock");
        String set = String.join(" ", PlayerCommands.run(ANNA, "", "radio 1200", p -> true));
        assertTrue(set.contains("1200") && set.contains("minecraft:clock"), set);
        assertEquals(1200, AudioDistancePlugin.PLAYER_PREFS.radioOf(ANNA));
        assertTrue(String.join(" ", PlayerCommands.run(ANNA, "", "status", p -> true)).contains("Radio frequency: 1200"));

        String bad = String.join(" ", PlayerCommands.run(ANNA, "", "radio 99999", p -> true));
        assertTrue(bad.contains("99999") && bad.contains("1-9999|off"), bad);
        assertEquals(1200, AudioDistancePlugin.PLAYER_PREFS.radioOf(ANNA), "nonsense changes nothing");

        PlayerCommands.run(ANNA, "", "radio off", p -> true);
        assertEquals(0, AudioDistancePlugin.PLAYER_PREFS.radioOf(ANNA));
        PlayerCommands.run(ANNA, "", "undo", p -> true);
        assertEquals(1200, AudioDistancePlugin.PLAYER_PREFS.radioOf(ANNA), "undo takes the change back");

        // Its own permission
        String denied = String.join(" ", PlayerCommands.run(ANNA, "", "radio 5", p -> !p.equals(PlayerCommands.PERM_RADIO)));
        assertTrue(denied.contains(PlayerCommands.PERM_RADIO), denied);
        assertEquals(1200, AudioDistancePlugin.PLAYER_PREFS.radioOf(ANNA));
        assertEquals(PlayerCommands.PERM_RADIO, PlayerCommands.permissionFor("radio"));
        assertTrue(PlayerCommands.suggest("radio ", p -> true).contains("off"));
        assertTrue(CommandHelpChecks.voiceHelpHas("radio"));
    }

    @Test
    @DisplayName("/vcd radio: the view, the item with undo, permissions and Tab")
    void adminCommand() throws IOException {
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
        List<String> view = AdminCommands.run("radio", s, ctx);
        assertTrue(view.stream().anyMatch(l -> l.contains("Radio: off")), view.toString());
        assertTrue(view.stream().anyMatch(l -> l.contains("Nobody is on the air")), view.toString());

        List<String> set = AdminCommands.run("radio item minecraft:clock", s, ctx);
        assertEquals("minecraft:clock", s.getRadioItem(), set.toString());
        assertTrue(Files.readString(s.getPath()).contains("radio_item=minecraft:clock"));
        AdminCommands.run("undo", s, ctx);
        assertEquals("", s.getRadioItem(), "undo takes the item back");
        AdminCommands.run("radio item minecraft:clock", s, ctx);
        AdminCommands.run("radio item none", s, ctx);
        assertEquals("", s.getRadioItem());

        assertEquals(AdminCommands.PERM_STATUS, AdminCommands.permissionFor("radio", ""));
        assertEquals(AdminCommands.PERM_SETTINGS, AdminCommands.permissionFor("radio", "item"));
        assertEquals(List.of("item"), AdminCommands.suggest("radio "));
        assertTrue(AdminCommands.suggest("radio item min").contains("minecraft:clock"));
        assertTrue(CommandHelp.EXAMPLES.containsKey("radio"));
    }

    /** The /voice help has a line for each topic in every language (a missing key would show as the key). */
    private static final class CommandHelpChecks {
        static boolean voiceHelpHas(String topic) {
            return PlayerCommands.EXAMPLES.containsKey(topic) && List.of(PlayerCommands.TOPICS).contains(topic);
        }
    }
}
