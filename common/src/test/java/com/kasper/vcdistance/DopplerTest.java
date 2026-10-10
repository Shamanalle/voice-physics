package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Doppler effect: how fast speaker and listener close in, and the pitch shift that follows. */
public class DopplerTest {

    private static final int FRAME = 960;
    private static final long FRAME_NANOS = 20_000_000L;

    @Test
    @DisplayName("Coming closer raises the voice, moving away lowers it, walking about changes nothing")
    void pitchFollowsClosingSpeed() {
        assertEquals(1.0, Doppler.pitch(0.0, 1.0));
        assertEquals(1.0, Doppler.pitch(0.8, 1.0), "below the deadzone");
        assertEquals(1.0, Doppler.pitch(30.0, 0.0), "strength 0 is off");
        assertEquals(1.0, Doppler.pitch(Double.NaN, 1.0));
        double up = Doppler.pitch(30.0, 1.0);
        assertEquals(343.0 / (343.0 - 29.0), up, 1e-9);
        assertTrue(Doppler.pitch(-30.0, 1.0) < 1.0);
        assertTrue(Doppler.pitch(30.0, 2.0) > up, "strength scales the speeds");
        assertEquals(Doppler.MAX_PITCH, Doppler.pitch(300.0, 3.0));
        assertEquals(Doppler.MIN_PITCH, Doppler.pitch(-300.0, 3.0));
    }

    @Test
    @DisplayName("Closing speed from velocities: only movement along the line between the two counts")
    void closingFromVelocities() {
        // Speaker 10 blocks east, flying west towards the listener at 20 b/s
        assertEquals(20.0, Doppler.closing(10, 0, 0, -20, 0, 0, 0, 0, 0), 1e-9);
        // Listener walks west, away from the speaker
        assertEquals(-5.0, Doppler.closing(10, 0, 0, 0, 0, 0, -5, 0, 0), 1e-9);
        // Side by side, same velocity: nothing
        assertEquals(0.0, Doppler.closing(10, 0, 0, 0, 0, 15, 0, 0, 15), 1e-9);
        // Passing sideways: nothing at the closest point
        assertEquals(0.0, Doppler.closing(10, 0, 0, 0, 0, 30, 0, 0, 0), 1e-9);
    }

    @Test
    @DisplayName("Distances every tick give a smoothed closing speed; teleports and lost positions reset it")
    void closingFromDistances() {
        Doppler.Closing c = new Doppler.Closing();
        long t = 0;
        double d = 40.0;
        for (int i = 0; i < 40; i++) {
            c.distance(d, t);
            d -= 0.5; // 10 blocks a second
            t += 50_000_000L;
        }
        assertEquals(10.0, c.get(), 0.2);
        c.distance(d + 80.0, t); // teleported far away
        assertEquals(0.0, c.get());
        c.distance(-1.0, t + 50_000_000L);
        assertEquals(0.0, c.get());
    }

    @Test
    @DisplayName("Closing speeds from the server are smoothed too")
    void closingFromSpeeds() {
        Doppler.Closing c = new Doppler.Closing();
        long t = 0;
        for (int i = 0; i < 60; i++) {
            c.speed(-12.0, t);
            t += 20_000_000L;
        }
        assertEquals(-12.0, c.get(), 0.1);
        c.speed(500.0, t);
        assertEquals(0.0, c.get(), "impossible speed: a teleport");
    }

    @Test
    @DisplayName("At pitch 1 frames pass untouched and nothing is delayed")
    void bypass() {
        PitchShifter s = new PitchShifter();
        short[] frame = sine(500, 0, 10000);
        short[] copy = frame.clone();
        assertFalse(s.process(frame, 1.0, 0));
        assertArrayEquals(copy, frame);
        assertFalse(s.isEngaged());
    }

    @Test
    @DisplayName("A shifted voice has the new pitch, the same length and no clicks")
    void shiftsPitch() {
        for (double pitch : new double[]{1.2, 0.85, 1.05}) {
            PitchShifter s = new PitchShifter();
            int frames = 100; // two seconds
            short[] out = new short[frames * FRAME];
            for (int f = 0; f < frames; f++) {
                short[] frame = sine(500, f * FRAME, 10000);
                s.process(frame, pitch, f * FRAME_NANOS);
                assertEquals(FRAME, frame.length);
                System.arraycopy(frame, 0, out, f * FRAME, FRAME);
            }
            // The last second, after the glide
            double freq = frequency(out, 50 * FRAME, out.length);
            assertEquals(500 * pitch, freq, 500 * pitch * 0.02, "pitch " + pitch);
            // No step bigger than the steepest slope of the shifted sine (with room for the crossfades)
            double slope = 2 * Math.PI * 500 * pitch * 10000 / VoiceFilter.SAMPLE_RATE;
            int maxStep = 0;
            for (int i = 1; i < out.length; i++) {
                maxStep = Math.max(maxStep, Math.abs(out[i] - out[i - 1]));
            }
            assertTrue(maxStep < slope * 1.6, "pitch " + pitch + ": step " + maxStep + " vs slope " + slope);
        }
    }

    @Test
    @DisplayName("Back at pitch 1 the shifter fades out and the voice plays as it came")
    void stepsOut() {
        PitchShifter s = new PitchShifter();
        int f = 0;
        for (; f < 30; f++) {
            s.process(sine(500, f * FRAME, 10000), 1.15, f * FRAME_NANOS);
        }
        assertTrue(s.isEngaged());
        for (; f < 90; f++) {
            s.process(sine(500, f * FRAME, 10000), 1.0, f * FRAME_NANOS);
        }
        assertFalse(s.isEngaged());
        short[] frame = sine(500, f * FRAME, 10000);
        short[] copy = frame.clone();
        assertFalse(s.process(frame, 1.0, f * FRAME_NANOS));
        assertArrayEquals(copy, frame);
    }

    @Test
    @DisplayName("A pause in speech resets the shifter")
    void pauseResets() {
        PitchShifter s = new PitchShifter();
        for (int f = 0; f < 10; f++) {
            s.process(sine(500, f * FRAME, 10000), 1.2, f * FRAME_NANOS);
        }
        assertTrue(s.isEngaged());
        short[] frame = sine(500, 0, 10000);
        short[] copy = frame.clone();
        assertFalse(s.process(frame, 1.0, 10 * FRAME_NANOS + PitchShifter.PAUSE_NANOS + 1));
        assertArrayEquals(copy, frame);
        assertFalse(s.isEngaged());
    }

    @Test
    @DisplayName("The server measures each player's velocity between refreshes; teleports and world changes reset it")
    void serverVelocities() {
        ServerPlayers players = new ServerPlayers();
        java.util.UUID id = java.util.UUID.randomUUID();
        players.update(at(id, "world", 0), 0);
        assertArrayEquals(new double[]{0, 0, 0}, players.velocity(id));
        players.update(at(id, "world", 5), 250_000_000L);
        assertEquals(20.0, players.velocity(id)[0], 1e-9);
        ServerPlayers.Info same = players.get(id);
        players.update(same, 400_000_000L);
        assertEquals(20.0, players.velocity(id)[0], 1e-9, "the same snapshot again changes nothing");
        players.update(at(id, "world", 500), 500_000_000L);
        assertEquals(0.0, players.velocity(id)[0], "teleport");
        players.update(at(id, "nether", 502), 750_000_000L);
        assertEquals(0.0, players.velocity(id)[0], "world change");
        players.remove(id);
        assertNull(players.velocity(id));
    }

    private static ServerPlayers.Info at(java.util.UUID id, String world, double x) {
        return new ServerPlayers.Info(id, "P", world, x, 64, 0, false, true, false, "", "", java.util.List.of(), "");
    }

    private static short[] sine(double freq, int offset, double amplitude) {
        short[] s = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            s[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * freq * (offset + i) / VoiceFilter.SAMPLE_RATE));
        }
        return s;
    }

    /** Frequency from the rising zero crossings between {@code from} and {@code to}. */
    private static double frequency(short[] s, int from, int to) {
        int first = -1;
        int last = -1;
        int count = 0;
        for (int i = from + 1; i < to; i++) {
            if (s[i - 1] < 0 && s[i] >= 0) {
                if (first < 0) {
                    first = i;
                } else {
                    count++;
                }
                last = i;
            }
        }
        return count * (double) VoiceFilter.SAMPLE_RATE / (last - first);
    }
}
