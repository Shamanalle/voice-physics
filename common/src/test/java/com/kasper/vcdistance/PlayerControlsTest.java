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

/** 2.8.0: what a player sets with /voice, and how the voice rules and the talking line use it. */
public class PlayerControlsTest {

    @TempDir
    Path dir;

    private static final UUID ANNA = UUID.nameUUIDFromBytes("Anna".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("Bob".getBytes());

    @AfterEach
    void clear() {
        AudioDistancePlugin.PLAYER_PREFS.reset(ANNA);
        AudioDistancePlugin.PLAYER_PREFS.reset(BOB);
        AudioDistancePlugin.PLAYER_PREFS.forget(ANNA);
        AudioDistancePlugin.PLAYER_PREFS.forget(BOB);
        AudioDistancePlugin.PLAYERS.clear();
        AudioDistancePlugin.TALK.clear();
    }

    private static ServerPlayers.Info player(String name, double x) {
        return new ServerPlayers.Info(UUID.nameUUIDFromBytes(name.getBytes()), name, "world", x, 64, 0,
                false, true, false, "", "", List.of(), "");
    }

    private ServerSettings settings() throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, "");
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    @Test
    @DisplayName("Choices are written to the file and read back, including several volumes")
    void roundTrip() {
        Path file = dir.resolve(PlayerPrefs.FILE);
        PlayerPrefs a = new PlayerPrefs();
        a.load(file);
        a.setMode(ANNA, PlayerPrefs.Mode.SHOUT);
        a.setWalls(ANNA, false);
        a.setHud(ANNA, true);
        a.setVolume(ANNA, BOB, 40);
        a.setVolume(BOB, ANNA, 0);

        PlayerPrefs b = new PlayerPrefs();
        b.load(file);
        assertEquals(PlayerPrefs.Mode.SHOUT, b.get(ANNA).mode());
        assertFalse(b.wallsFor(ANNA));
        assertTrue(b.hudFor(ANNA));
        assertEquals(40, b.volume(ANNA, BOB));
        assertEquals(0, b.volume(BOB, ANNA));
        assertEquals(100, b.volume(ANNA, UUID.randomUUID()));
        assertTrue(b.anyRules());
    }

    @Test
    @DisplayName("Back to the defaults leaves nothing behind")
    void resetClears() {
        PlayerPrefs p = new PlayerPrefs();
        p.setMode(ANNA, PlayerPrefs.Mode.QUIET);
        assertTrue(p.anyRules());
        p.reset(ANNA);
        assertFalse(p.anyRules());
        assertEquals(1.0, p.rangeFactor(ANNA));
    }

    @Test
    @DisplayName("A broken line in the file does not stop the rest from loading")
    void brokenLine() throws IOException {
        Path file = dir.resolve(PlayerPrefs.FILE);
        Files.writeString(file, "not-a-uuid=mode:shout\n" + ANNA + "=mode:quiet;vol:bad@x," + BOB + "@30\n");
        PlayerPrefs p = new PlayerPrefs();
        p.load(file);
        assertEquals(PlayerPrefs.Mode.QUIET, p.get(ANNA).mode());
        assertEquals(30, p.volume(ANNA, BOB));
    }

    @Test
    @DisplayName("Volume below 100 % becomes a loss in dB; 0 is ignored")
    void volumeLoss() {
        PlayerPrefs p = new PlayerPrefs();
        assertEquals(0.0, p.lossDb(ANNA, BOB));
        p.setVolume(ANNA, BOB, 50);
        assertEquals(6.02, p.lossDb(ANNA, BOB), 0.01);
        p.setVolume(ANNA, BOB, 0);
        assertEquals(0, p.volume(ANNA, BOB));
        assertTrue(p.lossDb(ANNA, BOB) >= 40.0);
    }

    @Test
    @DisplayName("Quiet halves and shout doubles the range; an ignored speaker is not heard")
    void rangeAndIgnore() throws IOException {
        ServerSettings s = settings();
        ServerPlayers.Info anna = player("Anna", 0);
        ServerPlayers.Info bob = player("Bob", 30);
        assertEquals(48.0, ServerRange.rangeOf(s, anna, false, 48, 24));

        AudioDistancePlugin.PLAYER_PREFS.setMode(anna.id(), PlayerPrefs.Mode.QUIET);
        assertEquals(24.0, ServerRange.rangeOf(s, anna, false, 48, 24));
        assertEquals(ServerRange.Reason.RANGE, ServerRange.decide(s, anna, bob, false, 48, 24).reason());

        AudioDistancePlugin.PLAYER_PREFS.setMode(anna.id(), PlayerPrefs.Mode.SHOUT);
        assertEquals(96.0, ServerRange.rangeOf(s, anna, false, 48, 24));
        assertTrue(ServerRange.decide(s, anna, bob, false, 48, 24).hears());

        AudioDistancePlugin.PLAYER_PREFS.setVolume(bob.id(), anna.id(), 0);
        assertEquals(ServerRange.Reason.IGNORED, ServerRange.decide(s, anna, bob, false, 48, 24).reason());
        // Ignoring is one way: Anna still hears Bob
        assertTrue(ServerRange.decide(s, bob, anna, false, 48, 24).hears());
    }

    @Test
    @DisplayName("/voice: mode, shout permission, walls, hud, volume, ignore, reset")
    void commands() {
        ServerPlayers.Info anna = player("Anna", 0);
        AudioDistancePlugin.PLAYERS.update(anna);
        AudioDistancePlugin.PLAYERS.update(player("Bob", 5));
        PlayerCommands.Context everything = p -> true;
        PlayerCommands.Context noShout = p -> !p.equals(PlayerCommands.PERM_SHOUT);
        PlayerCommands.Context nothing = p -> false;
        PlayerPrefs prefs = AudioDistancePlugin.PLAYER_PREFS;

        assertTrue(PlayerCommands.run(ANNA, "", "mode quiet", everything).get(0).contains("0.5"));
        assertEquals(PlayerPrefs.Mode.QUIET, prefs.get(ANNA).mode());

        PlayerCommands.run(ANNA, "", "mode shout", noShout);
        assertEquals(PlayerPrefs.Mode.QUIET, prefs.get(ANNA).mode(), "no shout without the permission");
        PlayerCommands.run(ANNA, "", "mode shout", everything);
        assertEquals(PlayerPrefs.Mode.SHOUT, prefs.get(ANNA).mode());

        PlayerCommands.run(ANNA, "", "walls off", everything);
        assertFalse(prefs.wallsFor(ANNA));
        PlayerCommands.run(ANNA, "", "hud on", everything);
        assertTrue(prefs.hudFor(ANNA));

        assertTrue(PlayerCommands.run(ANNA, "", "volume Bob 50", everything).get(0).contains("Bob"));
        assertEquals(50, prefs.volume(ANNA, BOB));
        assertTrue(PlayerCommands.run(ANNA, "", "volume Bob 150", everything).get(0).contains("150"));
        assertEquals(50, prefs.volume(ANNA, BOB), "a bad value changes nothing");
        assertTrue(PlayerCommands.run(ANNA, "", "volume Nobody 50", everything).get(0).contains("Nobody"));
        PlayerCommands.run(ANNA, "", "ignore Bob", everything);
        assertEquals(0, prefs.volume(ANNA, BOB));
        PlayerCommands.run(ANNA, "", "unignore Bob", everything);
        assertEquals(100, prefs.volume(ANNA, BOB));
        assertTrue(PlayerCommands.run(ANNA, "", "ignore Anna", everything).get(0).contains("you"));

        PlayerCommands.run(ANNA, "", "reset", everything);
        assertEquals(PlayerPrefs.Mode.NORMAL, prefs.get(ANNA).mode());
        assertTrue(prefs.wallsFor(ANNA));

        assertTrue(PlayerCommands.run(ANNA, "", "mode quiet", nothing).get(0).contains(PlayerCommands.PERM));
        assertEquals(PlayerPrefs.Mode.NORMAL, prefs.get(ANNA).mode());
    }

    @Test
    @DisplayName("Walls count as locked only for an enforced profile with walls locked; /voice walls works otherwise")
    void wallsLocked() throws IOException {
        Path file = dir.resolve("locked.properties");
        Files.writeString(file, "profile_mode=enforce\nprofile_locked=walls\n");
        ServerSettings locked = new ServerSettings(file);
        locked.load();
        assertTrue(locked.wallsLocked());
        assertFalse(settings().wallsLocked());
        // The command reads the server's own settings: with them not locked it works
        PlayerCommands.run(ANNA, "", "walls off", p -> true);
        assertFalse(AudioDistancePlugin.PLAYER_PREFS.wallsFor(ANNA));
    }

    @Test
    @DisplayName("A zone that sets walls decides for the players in it: their /voice walls choice does not apply there")
    void zoneWallsOverridePlayer() throws IOException {
        ServerSettings s = settings();
        Zone strong = new Zone(Zone.WORLD, "world", ServerSettings.ProfileMode.OFF, "",
                new Zone.Rules(null, null, null, 0.9, null, false, null), null, 0);
        Zone none = new Zone(Zone.WORLD, "world", ServerSettings.ProfileMode.OFF, "",
                new Zone.Rules(null, null, null, 0.0, null, false, null), null, 0);
        assertTrue(s.wallsApply(strong, false), "the zone's walls, although the player turned theirs off");
        assertFalse(s.wallsApply(none, true), "a zone with 0 has no walls, although the player wants them");
        assertFalse(s.wallsApply(null, false), "outside a zone the player's choice counts");
        assertTrue(s.wallsApply(null, true));
        assertNull(s.wallsZoneOf(player("Anna", 0)), "no zones: nothing decides for the player");

        // The command refuses to change it inside such a zone and says which zone decides
        ServerSettings live = AudioDistancePlugin.SERVER_SETTINGS;
        live.putZone(strong);
        try {
            AudioDistancePlugin.PLAYERS.update(new ServerPlayers.Info(ANNA, "Anna", "world", 0, 64, 0,
                    false, true, false, "", "", List.of(), ""));
            assertNotNull(live.wallsZoneOf(AudioDistancePlugin.PLAYERS.get(ANNA)));
            List<String> reply = PlayerCommands.run(ANNA, "", "walls off", p -> true);
            assertTrue(AudioDistancePlugin.PLAYER_PREFS.wallsFor(ANNA), "the choice was not stored: " + reply);
            assertTrue(String.join(" ", reply).contains("world"), String.join(" ", reply));
            assertTrue(String.join(" ", PlayerCommands.run(ANNA, "", "status", p -> true)).contains("set by the zone world"));
        } finally {
            live.removeZone(strong.key());
        }
    }

    @Test
    @DisplayName("A shout does not stretch a zone's range or add to a megaphone; quiet always applies")
    void shoutLimits() throws IOException {
        Path file = dir.resolve("zone.properties");
        Files.writeString(file, "zone.world.world.voice_range=10\nmegaphone_item=minecraft:goat_horn\n");
        ServerSettings s = new ServerSettings(file);
        s.load();
        ServerPlayers.Info inZone = player("Anna", 0);
        AudioDistancePlugin.PLAYER_PREFS.setMode(ANNA, PlayerPrefs.Mode.SHOUT);
        assertEquals(10.0, ServerRange.rangeOf(s, inZone, false, 48, 24), "zone range stays");
        AudioDistancePlugin.PLAYER_PREFS.setMode(ANNA, PlayerPrefs.Mode.QUIET);
        assertEquals(5.0, ServerRange.rangeOf(s, inZone, false, 48, 24));
    }

    @Test
    @DisplayName("Tab completion: subcommands, modes without shout when not allowed, online players")
    void suggestions() {
        AudioDistancePlugin.PLAYERS.update(player("Bob", 5));
        PlayerCommands.Context noShout = p -> !p.equals(PlayerCommands.PERM_SHOUT);
        assertTrue(PlayerCommands.suggest("", noShout).contains("volume"));
        assertEquals(List.of("mode"), PlayerCommands.suggest("mo", noShout));
        assertEquals(List.of("quiet", "normal"), PlayerCommands.suggest("mode ", noShout));
        assertEquals(List.of("quiet", "normal", "shout"), PlayerCommands.suggest("mode ", p -> true));
        assertEquals(List.of("Bob"), PlayerCommands.suggest("ignore b", noShout));
        assertEquals(List.of(), PlayerCommands.suggest("mode ", p -> false));
        // A part the server took away is not suggested
        assertFalse(PlayerCommands.suggest("", p -> !p.equals(PlayerCommands.PERM_VOLUME)).contains("volume"));
        assertTrue(PlayerCommands.suggest("help ", p -> true).contains("mode"));
    }

    private static List<CommandReply.Span> buttons(CommandReply reply) {
        List<CommandReply.Span> out = new java.util.ArrayList<>();
        for (CommandReply.Line line : reply.lines()) {
            for (CommandReply.Span s : line.spans()) {
                if (s.click() != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("Status: clickable modes, toggles, one line per volume, and Undo only after a change")
    void clickableStatus() {
        AudioDistancePlugin.PLAYERS.update(player("Anna", 0));
        AudioDistancePlugin.PLAYERS.update(player("Bob", 5));
        CommandReply fresh = PlayerCommands.execute(ANNA, "", "", p -> true);
        List<String> actions = buttons(fresh).stream().map(CommandReply.Span::action).toList();
        assertTrue(actions.contains("/voice mode quiet"));
        assertTrue(actions.contains("/voice mode shout"));
        assertFalse(actions.contains("/voice mode normal"), "the current mode is not a button");
        assertTrue(actions.contains("/voice walls off"));
        assertTrue(actions.contains("/voice hud on"));
        assertFalse(actions.contains("/voice undo"), "nothing to undo yet");
        assertFalse(actions.contains("/voice reset"), "nothing to reset yet");

        AudioDistancePlugin.PLAYER_PREFS.setVolume(ANNA, BOB, 40);
        CommandReply changed = PlayerCommands.execute(ANNA, "", "status", p -> true);
        List<String> after = buttons(changed).stream().map(CommandReply.Span::action).toList();
        assertTrue(after.contains("/voice undo"));
        assertTrue(after.contains("/voice reset"));
        assertTrue(after.contains("/voice unignore Bob"), "a button brings a player back to full volume");
        assertTrue(changed.plain().stream().anyMatch(l -> l.contains("Bob 40%")));

        // Without the shout permission it is not offered
        List<String> noShout = buttons(PlayerCommands.execute(ANNA, "", "", p -> !p.equals(PlayerCommands.PERM_SHOUT))).stream()
                .map(CommandReply.Span::action).toList();
        assertFalse(noShout.contains("/voice mode shout"));
    }

    @Test
    @DisplayName("Every change has an Undo; undo takes it back and asking again puts it back")
    void undo() {
        PlayerPrefs prefs = AudioDistancePlugin.PLAYER_PREFS;
        assertTrue(PlayerCommands.run(ANNA, "", "undo", p -> true).get(0).toLowerCase().contains("nothing"));
        CommandReply reply = PlayerCommands.execute(ANNA, "", "mode quiet", p -> true);
        assertTrue(buttons(reply).stream().anyMatch(s -> "/voice undo".equals(s.action())));
        assertEquals(PlayerPrefs.Mode.QUIET, prefs.get(ANNA).mode());

        PlayerCommands.run(ANNA, "", "undo", p -> true);
        assertEquals(PlayerPrefs.Mode.NORMAL, prefs.get(ANNA).mode());
        PlayerCommands.run(ANNA, "", "undo", p -> true);
        assertEquals(PlayerPrefs.Mode.QUIET, prefs.get(ANNA).mode(), "the second undo puts the change back");

        // A reset can be taken back too, volumes included
        prefs.setVolume(ANNA, BOB, 20);
        PlayerCommands.run(ANNA, "", "reset", p -> true);
        assertEquals(100, prefs.volume(ANNA, BOB));
        PlayerCommands.run(ANNA, "", "undo", p -> true);
        assertEquals(20, prefs.volume(ANNA, BOB));
        assertEquals(PlayerPrefs.Mode.QUIET, prefs.get(ANNA).mode());

        prefs.forget(ANNA);
        assertFalse(prefs.canUndo(ANNA));
    }

    @Test
    @DisplayName("Help: the list links to each command, a command shows examples, a typo suggests the command")
    void help() {
        CommandReply list = PlayerCommands.execute(ANNA, "", "help", p -> true);
        assertTrue(buttons(list).stream().anyMatch(s -> "/voice help mode".equals(s.action())));
        CommandReply topic = PlayerCommands.execute(ANNA, "", "help volume", p -> true);
        assertTrue(buttons(topic).stream().anyMatch(s -> "/voice volume Steve 50".equals(s.action())));
        assertTrue(buttons(topic).stream().anyMatch(s -> "/voice help".equals(s.action())), "a way back to the list");
        // A part the server took away is left out of the list
        CommandReply limited = PlayerCommands.execute(ANNA, "", "help", p -> !p.equals(PlayerCommands.PERM_MENU));
        assertFalse(buttons(limited).stream().anyMatch(s -> "/voice help menu".equals(s.action())));

        CommandReply typo = PlayerCommands.execute(ANNA, "", "mdoe quiet", p -> true);
        assertTrue(buttons(typo).stream().anyMatch(s -> s.action().startsWith("/voice mode")), "did you mean /voice mode");
        assertEquals(PlayerPrefs.Mode.NORMAL, AudioDistancePlugin.PLAYER_PREFS.get(ANNA).mode());
    }

    @Test
    @DisplayName("Each part of /voice has its own permission on top of vcd.player")
    void partPermissions() {
        PlayerCommands.Context noVolume = p -> !p.equals(PlayerCommands.PERM_VOLUME);
        AudioDistancePlugin.PLAYERS.update(player("Bob", 5));
        List<String> reply = PlayerCommands.run(ANNA, "", "volume Bob 50", noVolume);
        assertTrue(reply.get(0).contains(PlayerCommands.PERM_VOLUME));
        assertEquals(100, AudioDistancePlugin.PLAYER_PREFS.volume(ANNA, BOB));
        PlayerCommands.run(ANNA, "", "mode quiet", noVolume);
        assertEquals(PlayerPrefs.Mode.QUIET, AudioDistancePlugin.PLAYER_PREFS.get(ANNA).mode(), "the other parts still work");
        assertNull(PlayerCommands.permissionFor("status"));
        assertEquals(PlayerCommands.PERM_VOLUME, PlayerCommands.permissionFor("ignore"));
    }

    @Test
    @DisplayName("A bad value comes back with what is allowed and buttons to retype or get help")
    void badValueButtons() {
        CommandReply reply = PlayerCommands.execute(ANNA, "", "mode loud", p -> true);
        assertTrue(reply.plain().get(0).contains("loud"));
        List<String> actions = buttons(reply).stream().map(CommandReply.Span::action).toList();
        assertTrue(actions.contains("/voice mode "));
        assertTrue(actions.contains("/voice help mode"));
        assertEquals(PlayerPrefs.Mode.NORMAL, AudioDistancePlugin.PLAYER_PREFS.get(ANNA).mode());
    }

    @Test
    @DisplayName("Talking line: the nearest players talking, with distance; nobody talking gives nothing")
    void talkingLine() throws IOException {
        ServerSettings s = settings();
        ServerPlayers.Info anna = player("Anna", 0);
        ServerPlayers.Info bob = player("Bob", 12);
        ServerPlayers.Info far = player("Far", 500);
        List<ServerPlayers.Info> online = List.of(anna, bob, far);
        long now = System.nanoTime();

        assertEquals("", ServerHooks.talkingText(s, anna, online, now, id -> true));
        AudioDistancePlugin.TALK.spoke(bob.id(), now);
        AudioDistancePlugin.TALK.spoke(far.id(), now);
        assertEquals("» Bob 12m", ServerHooks.talkingText(s, anna, online, now, id -> true));
        // Not talking any more a second later
        assertEquals("", ServerHooks.talkingText(s, anna, online, now + 2_000_000_000L, id -> true));
        // A hidden player (vanish) is not listed
        assertEquals("", ServerHooks.talkingText(s, anna, online, now, id -> !id.equals(bob.id())));
        // An ignored speaker is not listed
        AudioDistancePlugin.PLAYER_PREFS.setVolume(anna.id(), bob.id(), 0);
        assertEquals("", ServerHooks.talkingText(s, anna, online, now, id -> true));
    }
}
