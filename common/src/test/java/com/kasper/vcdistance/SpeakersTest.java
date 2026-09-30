package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Loudspeakers: the settings file, who is picked up and who hears, and /vcd speaker. */
public class SpeakersTest {

    @TempDir
    Path dir;

    private static ServerPlayers.Info player(String name, double x, double z) {
        return new ServerPlayers.Info(UUID.nameUUIDFromBytes(name.getBytes()), name, "world", x, 64, z,
                false, true, false, "", "", List.of(), "");
    }

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    @Test
    @DisplayName("Speakers are written to the settings file and read back; damaged ones are skipped")
    void file() throws IOException {
        ServerSettings s = settings("");
        s.putSpeaker(new Loudspeaker("Stage", "world", 10.5, 64, -3, 4, 80));
        s.putSpeaker(new Loudspeaker("hall", "world_nether", 0, 70, 0, 99, 9999));
        s.save();
        String text = Files.readString(s.getPath());
        assertTrue(text.contains("speaker.Stage=world|10.5|64.0|-3.0|4.0|80.0"), text);

        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals(2, again.speakers().size());
        assertEquals(4.0, again.speaker("STAGE").pickup());
        assertEquals(Loudspeaker.MAX_PICKUP, again.speaker("hall").pickup(), "limits apply");
        assertEquals(Loudspeaker.MAX_RADIUS, again.speaker("hall").radius());

        ServerSettings bad = settings("speaker.ok=w|1|2|3\nspeaker.bad=w|x|2|3\nspeaker.short=w|1\nspeaker.b@d=w|1|2|3\n");
        assertEquals(List.of("ok"), List.copyOf(bad.speakers().keySet()));
        assertEquals(Loudspeaker.DEFAULT_RADIUS, bad.speaker("ok").radius(), "missing numbers get defaults");

        assertTrue(again.removeSpeaker("Stage"));
        assertFalse(again.removeSpeaker("Stage"));
        assertFalse(Loudspeaker.validName("a b"));
        assertFalse(Loudspeaker.validName(""));
    }

    @Test
    @DisplayName("A talker at a speaker is heard by players around it, not by those who hear them anyway")
    void deliveries() throws IOException {
        ServerSettings s = settings("server_speakers=true\n");
        s.putSpeaker(new Loudspeaker("stage", "world", 0, 64, 0, 3, 50));
        PlayerPrefs prefs = new PlayerPrefs();
        ServerPlayers players = new ServerPlayers();
        ServerPlayers.Info anna = player("Anna", 1, 0);
        ServerPlayers.Info bob = player("Bob", 40, 0);
        ServerPlayers.Info carl = player("Carl", 5, 0);
        ServerPlayers.Info dora = player("Dora", 200, 0);
        for (ServerPlayers.Info p : List.of(anna, bob, carl, dora)) {
            players.update(p);
        }

        List<ServerSpeakers.Delivery> got = ServerSpeakers.deliveries(s, prefs, players, anna, 16.0);
        assertEquals(1, got.size());
        assertEquals(List.of(bob), got.get(0).listeners(), "Carl is within 16 blocks of Anna, Dora is out of the radius");

        assertTrue(ServerSpeakers.deliveries(s, prefs, players, carl, 16.0).isEmpty(), "Carl is 5 blocks from the speaker: no pickup");

        prefs.setVolume(bob.id(), anna.id(), 0);
        assertTrue(ServerSpeakers.deliveries(s, prefs, players, anna, 16.0).isEmpty(), "a listener who ignores the talker gets nothing");
        prefs.setVolume(bob.id(), anna.id(), 100);

        s.setServerSpeakers(false);
        assertTrue(ServerSpeakers.deliveries(s, prefs, players, anna, 16.0).isEmpty(), "the switch is off");
        s.setServerSpeakers(true);

        ServerPlayers.Info dead = new ServerPlayers.Info(anna.id(), "Anna", "world", 1, 64, 0, false, false, false, "", "", List.of(), "");
        s.setDeadSilent(true);
        assertTrue(ServerSpeakers.deliveries(s, prefs, players, dead, 16.0).isEmpty());
        s.setDeadSilent(false);

        ServerPlayers.Info elsewhere = new ServerPlayers.Info(anna.id(), "Anna", "world_nether", 1, 64, 0, false, true, false, "", "", List.of(), "");
        assertTrue(ServerSpeakers.deliveries(s, prefs, players, elsewhere, 16.0).isEmpty(), "another world: not at the speaker");
    }

    @Test
    @DisplayName("/vcd speaker: add where the admin stands, bad values, list, move, remove, undo, permissions, Tab")
    void command() throws IOException {
        ServerSettings s = settings("");
        ServerPlayers players = new ServerPlayers();
        ServerPlayers.Info admin = player("Admin", 12, -7);
        players.update(admin);
        AdminCommands.Context ctx = new AdminCommands.Context() {
            public String platform() {
                return "Test";
            }

            public int onlinePlayers() {
                return 1;
            }

            public int addonPlayers() {
                return 0;
            }

            public void resendProfiles() {
            }

            public boolean allows(String permission) {
                return true;
            }

            public UUID sender() {
                return admin.id();
            }

            public ServerPlayers players() {
                return players;
            }
        };

        String none = String.join("\n", AdminCommands.run("speaker", s, ctx));
        assertTrue(none.contains("No loudspeakers"), none);

        String bad = String.join("\n", AdminCommands.run("speaker add bad!name", s, ctx));
        assertTrue(s.speakers().isEmpty(), bad);
        AdminCommands.run("speaker add stage 9999", s, ctx);
        assertTrue(s.speakers().isEmpty(), "a radius out of range adds nothing");
        AdminCommands.run("speaker add stage 40 0", s, ctx);
        assertTrue(s.speakers().isEmpty(), "a pickup out of range adds nothing");

        AdminCommands.run("speaker add stage 40 5", s, ctx);
        Loudspeaker sp = s.speaker("stage");
        assertNotNull(sp);
        assertEquals(12.0, sp.x());
        assertEquals(-7.0, sp.z());
        assertEquals(40.0, sp.radius());
        assertEquals(5.0, sp.pickup());
        assertTrue(Files.readString(s.getPath()).contains("speaker.stage=world|12.0|64.0|-7.0|5.0|40.0"));

        String list = String.join("\n", AdminCommands.run("speaker list", s, ctx));
        assertTrue(list.contains("stage"), list);

        AdminCommands.run("undo", s, ctx);
        assertNull(s.speaker("stage"), "undo takes the speaker back");

        AdminCommands.run("speaker add stage", s, ctx);
        assertEquals(Loudspeaker.DEFAULT_RADIUS, s.speaker("stage").radius());
        AdminCommands.run("speaker remove stage", s, ctx);
        assertNull(s.speaker("stage"));
        String unknown = String.join("\n", AdminCommands.run("speaker remove stage", s, ctx));
        assertTrue(unknown.contains("stage"), unknown);

        assertEquals(AdminCommands.PERM_STATUS, AdminCommands.permissionFor("speaker", ""));
        assertEquals(AdminCommands.PERM_STATUS, AdminCommands.permissionFor("speaker", "list"));
        assertEquals(AdminCommands.PERM_SPEAKER, AdminCommands.permissionFor("speaker", "add"));
        assertEquals(AdminCommands.PERM_SPEAKER, AdminCommands.permissionFor("speaker", "remove"));
        assertTrue(List.of(AdminCommands.PERMISSIONS).contains("vcd.speaker"));
        assertTrue(AdminCommands.suggest("speaker ").containsAll(List.of("add", "remove", "list")));
        assertTrue(CommandHelp.EXAMPLES.containsKey("speaker"));
    }
}
