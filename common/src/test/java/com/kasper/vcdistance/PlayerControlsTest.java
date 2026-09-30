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

        assertEquals("", ServerHooks.talkingText(s, anna, online, now));
        AudioDistancePlugin.TALK.spoke(bob.id(), now);
        AudioDistancePlugin.TALK.spoke(far.id(), now);
        assertEquals("» Bob 12m", ServerHooks.talkingText(s, anna, online, now));
        // Not talking any more a second later
        assertEquals("", ServerHooks.talkingText(s, anna, online, now + 2_000_000_000L));
        // An ignored speaker is not listed
        AudioDistancePlugin.PLAYER_PREFS.setVolume(anna.id(), bob.id(), 0);
        assertEquals("", ServerHooks.talkingText(s, anna, online, now));
    }
}
