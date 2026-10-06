package com.kasper.vcdistance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Water, weather, air and echo for players without the addon: the pure logic of {@link ServerEffects} and
 * {@link ServerRooms}, and how the settings switch it.
 */
public class ServerEffectsTest {

    @TempDir
    Path dir;

    private ServerSettings settings;
    private final ServerRooms rooms = new ServerRooms();

    @BeforeEach
    void setUp() {
        settings = new ServerSettings(dir.resolve("server.properties"));
        settings.load();
    }

    private static ServerPlayers.Info player(UUID id, double x, boolean underwater, EnvironmentEffects.Weather weather) {
        return new ServerPlayers.Info(id, "P", "world", x, 64, 0, false, true, false, "", "", List.of(), "", underwater, weather);
    }

    private static ServerPlayers.Info player(UUID id, double x) {
        return player(id, x, false, EnvironmentEffects.Weather.CLEAR);
    }

    @Test
    @DisplayName("Everything is off until the admin turns it on")
    void offByDefault() {
        assertFalse(settings.isServerEffects());
        assertFalse(settings.isServerAir());
        assertFalse(settings.hasServerRealism());
        ServerPlayers.Info diver = player(UUID.randomUUID(), 0, true, EnvironmentEffects.Weather.THUNDER);
        assertEquals(EnvironmentEffects.Effect.NONE, ServerEffects.atmosphere(settings, diver, player(UUID.randomUUID(), 40), 48));
        assertNull(ServerEffects.room(settings, rooms, diver, player(UUID.randomUUID(), 40), 0));
    }

    @Test
    @DisplayName("Under water, for the listener or the speaker, voices are dull and quiet")
    void water() {
        settings.setServerEffects(true);
        ServerPlayers.Info dry = player(UUID.randomUUID(), 0);
        ServerPlayers.Info diver = player(UUID.randomUUID(), 5, true, EnvironmentEffects.Weather.CLEAR);
        EnvironmentEffects.Effect listenerDives = ServerEffects.atmosphere(settings, dry, diver, 48);
        EnvironmentEffects.Effect speakerDives = ServerEffects.atmosphere(settings, diver, dry, 48);
        assertEquals(EnvironmentEffects.WATER_MUFFLE, listenerDives.muffle(), 1e-9);
        assertEquals(EnvironmentEffects.WATER_LOSS_DB, listenerDives.lossDb(), 1e-9);
        assertEquals(listenerDives, speakerDives);
        assertEquals(EnvironmentEffects.Effect.NONE, ServerEffects.atmosphere(settings, dry, player(UUID.randomUUID(), 5), 48));
    }

    @Test
    @DisplayName("The profile decides which effects and how strong: the server follows it")
    void followsProfile() {
        settings.setServerEffects(true);
        ServerPlayers.Info dry = player(UUID.randomUUID(), 0);
        ServerPlayers.Info diver = player(UUID.randomUUID(), 5, true, EnvironmentEffects.Weather.CLEAR);
        settings.profile().setUnderwaterEnabled(false);
        assertEquals(EnvironmentEffects.Effect.NONE, ServerEffects.atmosphere(settings, dry, diver, 48));
        settings.profile().setUnderwaterEnabled(true);
        settings.profile().setUnderwaterStrength(0.5);
        EnvironmentEffects.Effect half = ServerEffects.atmosphere(settings, dry, diver, 48);
        assertEquals(EnvironmentEffects.WATER_LOSS_DB * 0.5, half.lossDb(), 1e-9);
        assertTrue(half.muffle() < EnvironmentEffects.WATER_MUFFLE);
    }

    @Test
    @DisplayName("Rain covers far voices more than near ones, thunder more than rain")
    void weather() {
        settings.setServerEffects(true);
        ServerPlayers.Info me = player(UUID.randomUUID(), 0);
        ServerPlayers.Info rain = player(UUID.randomUUID(), 12, false, EnvironmentEffects.Weather.RAIN);
        ServerPlayers.Info farRain = player(UUID.randomUUID(), 48, false, EnvironmentEffects.Weather.RAIN);
        ServerPlayers.Info farThunder = player(UUID.randomUUID(), 48, false, EnvironmentEffects.Weather.THUNDER);
        double near = ServerEffects.atmosphere(settings, me, rain, 48).lossDb();
        double far = ServerEffects.atmosphere(settings, me, farRain, 48).lossDb();
        double storm = ServerEffects.atmosphere(settings, me, farThunder, 48).lossDb();
        assertTrue(near > 0.0 && near < far, near + " < " + far);
        assertTrue(storm > far);
        settings.profile().setWeatherEnabled(false);
        assertEquals(EnvironmentEffects.Effect.NONE, ServerEffects.atmosphere(settings, me, farThunder, 48));
    }

    @Test
    @DisplayName("Air leaves near voices alone and dulls far ones smoothly, most at the edge of the range")
    void air() {
        settings.setServerAir(true);
        ServerPlayers.Info me = player(UUID.randomUUID(), 0);
        double previous = -1.0;
        for (int meters : new int[]{0, 10, 14, 20, 30, 40, 48}) {
            double muffle = ServerEffects.atmosphere(settings, me, player(UUID.randomUUID(), meters), 48).muffle();
            assertTrue(muffle >= previous, meters + " m: " + muffle + " after " + previous);
            previous = muffle;
        }
        assertEquals(0.0, ServerEffects.atmosphere(settings, me, player(UUID.randomUUID(), 14), 48).muffle(), 1e-9,
                "a voice within a third of its range is clear");
        assertEquals(ServerEffects.AIR_MUFFLE_MAX, previous, 1e-9);
        // Beyond the range it does not get worse
        assertEquals(ServerEffects.AIR_MUFFLE_MAX, ServerEffects.atmosphere(settings, me, player(UUID.randomUUID(), 90), 48).muffle(), 1e-9);
        // Water and weather stay off while only the air is on
        assertEquals(EnvironmentEffects.Effect.NONE, ServerEffects.atmosphere(settings, me,
                player(UUID.randomUUID(), 0, true, EnvironmentEffects.Weather.THUNDER), 48));
    }

    @Test
    @DisplayName("Without a known range the air does nothing rather than divide by zero")
    void airWithoutRange() {
        settings.setServerAir(true);
        ServerPlayers.Info me = player(UUID.randomUUID(), 0);
        assertEquals(0.0, ServerEffects.atmosphere(settings, me, player(UUID.randomUUID(), 30), 0).muffle(), 1e-9);
        assertEquals(EnvironmentEffects.Effect.NONE, ServerEffects.atmosphere(settings, null, me, 48));
    }

    @Test
    @DisplayName("Air and water add up like two filters in a row")
    void combined() {
        settings.setServerEffects(true);
        settings.setServerAir(true);
        ServerPlayers.Info me = player(UUID.randomUUID(), 0);
        ServerPlayers.Info diver = player(UUID.randomUUID(), 48, true, EnvironmentEffects.Weather.CLEAR);
        EnvironmentEffects.Effect e = ServerEffects.atmosphere(settings, me, diver, 48);
        double expected = 1.0 - (1.0 - EnvironmentEffects.WATER_MUFFLE) * (1.0 - ServerEffects.AIR_MUFFLE_MAX);
        assertEquals(expected, e.muffle(), 1e-9);
    }

    // ---- echo ---------------------------------------------------------------

    private static RoomEstimate cave() {
        return RoomEstimate.forced(0.6);
    }

    @Test
    @DisplayName("A zone with its own echo decides: a size forces the room, 0 turns the echo off, none measures")
    void zoneForcesRoom() {
        UUID id = UUID.randomUUID();
        Zone plain = new Zone(Zone.WORLD, "world", ServerSettings.ProfileMode.OFF, "");
        assertTrue(ServerRooms.needsMeasuring(null));
        assertTrue(ServerRooms.needsMeasuring(plain));

        Zone hall = new Zone(Zone.BOX, "hall", ServerSettings.ProfileMode.OFF, "",
                new Zone.Rules(null, null, null, null, 0.8, false, null), null, 0);
        assertFalse(ServerRooms.needsMeasuring(hall));
        rooms.update(id, RoomEstimate.OPEN, hall, 0);
        RoomEstimate forced = rooms.of(id, 10_000_000_000L);
        assertTrue(forced.isAudible());
        assertEquals(RoomEstimate.forced(0.8).decaySeconds(), forced.decaySeconds(), 1e-6);

        Zone dead = new Zone(Zone.BOX, "dead", ServerSettings.ProfileMode.OFF, "",
                new Zone.Rules(null, null, null, null, 0.0, false, null), null, 0);
        assertFalse(ServerRooms.needsMeasuring(dead));
        rooms.update(id, cave(), dead, 20_000_000_000L);
        assertFalse(rooms.of(id, 40_000_000_000L).isAudible(), "echo 0 keeps the room open even where the rays found a cave");

        rooms.update(id, cave(), plain, 50_000_000_000L);
        assertTrue(rooms.of(id, 70_000_000_000L).isAudible(), "no zone echo: the measurement counts");
    }

    @Test
    @DisplayName("The echo glides to a new place instead of jumping, and unknown players are in the open")
    void glides() {
        UUID id = UUID.randomUUID();
        assertFalse(rooms.of(id, 0).isAudible());
        assertFalse(rooms.of(null, 0).isAudible());
        long t = 5_000_000_000L;
        rooms.update(id, cave(), null, t);
        double early = rooms.of(id, t + 100_000_000L).wet();
        double later = rooms.of(id, t + 5_000_000_000L).wet();
        assertTrue(early < later, early + " < " + later);
        assertEquals(cave().wet(), later, 0.01);
        rooms.forget(id);
        assertFalse(rooms.of(id, t + 6_000_000_000L).isAudible());
    }

    @Test
    @DisplayName("The room is the listener's, or the mix of both places when the speaker is somewhere else")
    void roomOfTwoPlayers() {
        settings.setServerEffects(true);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long t = 5_000_000_000L;
        long later = t + 10_000_000_000L;
        rooms.update(b, cave(), null, t);
        ServerPlayers.Info listener = player(a, 0);
        ServerPlayers.Info shouting = player(b, 30);

        RoomEstimate heard = ServerEffects.room(settings, rooms, shouting, listener, later);
        assertTrue(heard.isAudible(), "a friend shouting in a cave echoes for someone standing in the open");
        // Next to each other they share the listener's space
        RoomEstimate together = ServerEffects.room(settings, rooms, player(b, 2), listener, later);
        assertFalse(together.isAudible());
        // Own voice: just the listener's room
        assertFalse(ServerEffects.room(settings, rooms, listener, listener, later).isAudible());

        settings.profile().setReverbEnabled(false);
        assertNull(ServerEffects.room(settings, rooms, shouting, listener, later));
    }

    @Test
    @DisplayName("Echo levels: none in the open, a clear voice up close, more room for far voices, never above 1")
    void echoLevels() {
        double[] none = ServerEffects.echoLevels(RoomEstimate.OPEN, 20, 48, 1.0);
        assertEquals(0.0, none[0], 1e-9);
        assertEquals(0.0, none[1], 1e-9);
        assertEquals(0.0, ServerEffects.echoLevels(null, 20, 48, 1.0)[0], 1e-9);
        assertEquals(0.0, ServerEffects.echoLevels(cave(), 20, 48, 0.0)[0], 1e-9);

        double close = ServerEffects.echoLevels(cave(), 1, 48, 1.0)[0];
        double mid = ServerEffects.echoLevels(cave(), 15, 48, 1.0)[0];
        double far = ServerEffects.echoLevels(cave(), 40, 48, 1.0)[0];
        assertTrue(close < mid && mid < far, close + " " + mid + " " + far);
        for (double d : new double[]{0, 5, 47, 48, 200}) {
            for (double level : ServerEffects.echoLevels(cave(), d, 48, 1.5)) {
                assertTrue(level >= 0.0 && level <= 1.0, d + " m: " + level);
            }
        }
        // Unknown distance and range still give something sane
        assertTrue(ServerEffects.echoLevels(cave(), -1, 0, 1.0)[0] > 0.0);
    }

    // ---- the profile's curve (server_curve) ------------------------------------------------

    @Test
    @DisplayName("Simple Voice Chat's own fade: full to half the range, silent at the edge")
    void svcGain() {
        assertEquals(1.0, ServerEffects.svcGain(0, 48), 1e-9);
        assertEquals(1.0, ServerEffects.svcGain(24, 48), 1e-9);
        assertEquals(0.5, ServerEffects.svcGain(36, 48), 1e-9);
        assertEquals(0.0, ServerEffects.svcGain(48, 48), 1e-9);
        assertEquals(0.0, ServerEffects.svcGain(60, 48), 1e-9);
    }

    @Test
    @DisplayName("Curve: off by default, then only the difference to Simple Voice Chat's line, never louder")
    void curveLoss() {
        assertFalse(settings.isServerCurve());
        assertEquals(0.0, ServerEffects.curveLossDb(settings, 36, 48, false));
        settings.setServerCurve(true);
        assertTrue(settings.hasServerRealism());
        DistanceConfig p = settings.profile();
        // The same line as Simple Voice Chat's: nothing to change
        p.setModel(AttenuationModel.LINEAR);
        p.setAttenuationFactor(1.0);
        p.setMinVolumeFraction(0.0);
        p.setOpenalReferenceRatio(0.5);
        for (double d = 0; d <= 48; d += 3) {
            assertEquals(0.0, ServerEffects.curveLossDb(settings, d, 48, false), 0.05, "at " + d);
        }
        // A steeper curve takes the difference off past the full-volume part, and nothing inside it
        p.setModel(AttenuationModel.EXPONENTIAL);
        assertEquals(0.0, ServerEffects.curveLossDb(settings, 10, 48, false), 1e-9);
        double want = AudioPhysics.calculateGain(36.0 / 48, AttenuationModel.EXPONENTIAL, 1.0, 0.0, 0.5);
        assertEquals(-20 * Math.log10(want / 0.5), ServerEffects.curveLossDb(settings, 36, 48, false), 1e-6);
        // A whisper falls off faster
        assertTrue(ServerEffects.curveLossDb(settings, 36, 48, true) >= ServerEffects.curveLossDb(settings, 36, 48, false));
        // Louder than the line (a flat curve): the server cannot add volume
        p.setAttenuationFactor(0.0);
        assertEquals(0.0, ServerEffects.curveLossDb(settings, 40, 48, false), 1e-9);
        // Unknown range or distance: untouched
        assertEquals(0.0, ServerEffects.curveLossDb(settings, 40, 0, false));
        assertEquals(0.0, ServerEffects.curveLossDb(settings, Double.NaN, 48, false));
    }

    @Test
    @DisplayName("Curve: saved and read back")
    void curveSaved() {
        settings.setServerCurve(true);
        settings.save();
        ServerSettings again = new ServerSettings(dir.resolve("server.properties"));
        again.load();
        assertTrue(again.isServerCurve());
    }
}
