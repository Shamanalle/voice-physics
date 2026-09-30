package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Known signals through the sound code, measured and held to limits, so a change that alters how voices
 * sound shows up here instead of only by ear. The filter is held to its own design (two Butterworth
 * low-pass stages, -6 dB at the cutoff); the echo to the decay time it is asked for.
 */
public class AudioRegressionTest {

    private static final int RATE = VoiceFilter.SAMPLE_RATE;
    private static final int FRAME = 960;
    /** Test tones: bass to treble, each a whole number of periods in the measuring window. */
    private static final double[] BANDS = {125, 250, 500, 1000, 2000, 4000, 8000};
    private static final double TONE = 3000.0;

    // ---------- the wall / water / air filter ----------

    @Test
    @DisplayName("Filter: every band matches the designed response within 0.5 dB, at any muffle and loss")
    void filterMatchesDesign() {
        double[][] cases = {{0.0, 0.0}, {0.0, 6.0}, {0.25, 0.0}, {0.5, 0.0}, {0.5, 9.0}, {0.75, 0.0}, {1.0, 0.0}, {1.0, 12.0}};
        for (double[] c : cases) {
            double[] measured = filterResponse(c[0], c[1]);
            for (int b = 0; b < BANDS.length; b++) {
                double expected = designDb(BANDS[b], c[0], c[1]);
                String where = "muffle " + c[0] + ", loss " + c[1] + " dB, " + (int) BANDS[b] + " Hz: expected "
                        + round(expected) + " dB, got " + round(measured[b]) + " dB";
                if (expected > -50.0) {
                    assertEquals(expected, measured[b], 0.5, where);
                } else {
                    assertTrue(measured[b] < -45.0, where);
                }
            }
        }
    }

    @Test
    @DisplayName("Filter: nothing to muffle is a bit-exact copy, and it stays so after a wall comes and goes")
    void filterBypass() {
        VoiceFilter filter = new VoiceFilter();
        short[] input = tones(RATE);
        short[] out = run(filter, input.clone(), 0.0, 0.0);
        assertArrayEquals(input, out, "bypass");
        run(filter, tones(RATE), 0.8, 6.0);
        run(filter, tones(RATE * 2), 0.0, 0.0);
        assertFalse(filter.isEngaged(), "settled back to bypass");
        assertArrayEquals(input, run(filter, input.clone(), 0.0, 0.0), "bypass after a wall");
    }

    @Test
    @DisplayName("Places: air is dull only far away, water is duller than the edge of the range, bass stays")
    void places() {
        double[] near = effect(ServerEffects.air(0.2));
        double[] edge = effect(ServerEffects.air(1.0));
        double[] water = effect(EnvironmentEffects.water(true, false, 1.0));
        int bass = 1;
        int treble = 5;
        assertEquals(0.0, near[treble], 0.01, "a near voice is untouched");
        assertEquals(0.0, edge[bass], 1.0, "air keeps the bass");
        assertTrue(edge[treble] < -12.0, "air dulls the treble at the edge: " + round(edge[treble]));
        assertTrue(water[treble] < edge[treble] - 6.0, "water is duller than air: " + round(water[treble]) + " vs " + round(edge[treble]));
        for (double[] r : new double[][]{near, edge, water}) {
            for (int b = 1; b < r.length; b++) {
                assertTrue(r[b] <= r[b - 1] + 0.1, "a low-pass never lifts a higher band");
            }
        }
    }

    // ---------- the echo ----------

    @Test
    @DisplayName("Echo: turned off it is a bit-exact copy")
    void echoOff() {
        Reverb reverb = new Reverb();
        short[] input = tones(RATE);
        short[] out = input.clone();
        for (int at = 0; at < out.length; at += FRAME) {
            short[] frame = java.util.Arrays.copyOfRange(out, at, at + FRAME);
            reverb.process(frame, 0.0, 2.0);
            System.arraycopy(frame, 0, out, at, FRAME);
        }
        assertArrayEquals(input, out);
        assertFalse(reverb.isActive());
    }

    @Test
    @DisplayName("Echo: the tail fades in about the time asked for, longer rooms ring longer")
    void echoDecayTime() {
        double previous = 0.0;
        for (double asked : new double[]{0.5, 1.0, 2.0}) {
            double rt60 = measureRt60(asked);
            assertTrue(rt60 > asked * 0.6 && rt60 < asked * 1.25, "asked " + asked + " s, measured " + round(rt60) + " s");
            assertTrue(rt60 > previous * 1.5, "longer than the shorter room: " + round(rt60) + " vs " + round(previous));
            previous = rt60;
        }
    }

    @Test
    @DisplayName("Echo: high notes die away faster than low ones (soft walls)")
    void echoDamping() {
        double low = tailShare(250.0);
        double high = tailShare(4000.0);
        assertTrue(high < low * 0.5, "treble tail " + round(high) + " vs bass tail " + round(low));
    }

    @Test
    @DisplayName("Echo: loud input for a long time stays finite, fades to silence and switches off")
    void echoStable() {
        Reverb reverb = new Reverb();
        Random random = new Random(7);
        for (int f = 0; f < 500; f++) {
            short[] frame = new short[FRAME];
            for (int i = 0; i < FRAME; i++) {
                frame[i] = (short) (((i / 40) % 2 == 0 ? 1 : -1) * 20000 + random.nextInt(2000) - 1000);
            }
            reverb.process(frame, 1.0, Reverb.MAX_DECAY_SECONDS);
        }
        double lastRms = Double.MAX_VALUE;
        for (int f = 0; f < 600 && reverb.isActive(); f++) {
            short[] frame = new short[FRAME];
            reverb.process(frame, f < 400 ? 1.0 : 0.0, Reverb.MAX_DECAY_SECONDS);
            if (f == 399) {
                lastRms = rms(frame, 0, FRAME);
            }
        }
        assertTrue(lastRms < 4.0, "the tail is gone 8 s after a 4 s room: rms " + round(lastRms));
        assertFalse(reverb.isActive(), "switches itself off once turned down and quiet");
    }

    // ---------- measuring ----------

    /** dB per band after the filter settled, relative to the same band going in. */
    private static double[] filterResponse(double muffle, double lossDb) {
        VoiceFilter filter = new VoiceFilter();
        short[] out = run(filter, tones(RATE * 2), muffle, lossDb);
        short[] in = tones(RATE * 2);
        int from = RATE * 3 / 2;
        double[] db = new double[BANDS.length];
        for (int b = 0; b < BANDS.length; b++) {
            db[b] = 20.0 * Math.log10(Math.max(1e-9, level(out, from, RATE / 2, BANDS[b]) / level(in, from, RATE / 2, BANDS[b])));
        }
        return db;
    }

    private static double[] effect(EnvironmentEffects.Effect e) {
        return filterResponse(e.muffle(), e.lossDb());
    }

    /** The response the filter is built to have: two Butterworth stages (bilinear, pre-warped) and a flat loss. */
    private static double designDb(double hz, double muffle, double lossDb) {
        double omega = Math.tan(Math.PI * hz / RATE) / Math.tan(Math.PI * OcclusionModel.cutoffHz(muffle) / RATE);
        return -20.0 * Math.log10(1.0 + Math.pow(omega, 4)) - lossDb;
    }

    /** Filters {@code pcm} in voice-sized frames. */
    private static short[] run(VoiceFilter filter, short[] pcm, double muffle, double lossDb) {
        for (int at = 0; at + FRAME <= pcm.length; at += FRAME) {
            short[] frame = java.util.Arrays.copyOfRange(pcm, at, at + FRAME);
            filter.process(frame, muffle, lossDb);
            System.arraycopy(frame, 0, pcm, at, FRAME);
        }
        return pcm;
    }

    /** All test tones together. */
    private static short[] tones(int samples) {
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            double s = 0.0;
            for (int b = 0; b < BANDS.length; b++) {
                s += TONE * Math.sin(2.0 * Math.PI * BANDS[b] * i / RATE + b);
            }
            out[i] = (short) Math.round(s);
        }
        return out;
    }

    /** Amplitude of one frequency in a window (Goertzel). */
    private static double level(short[] pcm, int from, int length, double hz) {
        double k = 2.0 * Math.cos(2.0 * Math.PI * hz / RATE);
        double s1 = 0.0;
        double s2 = 0.0;
        for (int i = from; i < from + length; i++) {
            double s = pcm[i] + k * s1 - s2;
            s2 = s1;
            s1 = s;
        }
        double power = s1 * s1 + s2 * s2 - k * s1 * s2;
        return 2.0 * Math.sqrt(Math.max(0.0, power)) / length;
    }

    /** Seconds for the echo of a short burst to fall by 60 dB, from the fall between -5 and -25 dB (Schroeder). */
    private static double measureRt60(double decaySeconds) {
        Reverb reverb = new Reverb();
        // Let the level settle first, so the burst meets the full echo
        for (int f = 0; f < 60; f++) {
            reverb.process(new short[FRAME], 1.0, decaySeconds);
        }
        Random random = new Random(3);
        int total = (int) ((decaySeconds * 2.0 + 0.2) * RATE / FRAME) * FRAME;
        double[] tail = new double[total];
        for (int at = 0; at < total; at += FRAME) {
            short[] frame = new short[FRAME];
            short[] dry = new short[FRAME];
            if (at == 0) {
                for (int i = 0; i < 480; i++) {
                    dry[i] = frame[i] = (short) (random.nextGaussian() * 8000.0);
                }
            }
            reverb.process(frame, 1.0, decaySeconds);
            for (int i = 0; i < FRAME; i++) {
                tail[at + i] = frame[i] - dry[i];
            }
        }
        double[] left = new double[total + 1];
        for (int i = total - 1; i >= 0; i--) {
            left[i] = left[i + 1] + tail[i] * tail[i];
        }
        int t5 = -1;
        int t25 = -1;
        for (int i = 0; i < total; i++) {
            double db = 10.0 * Math.log10(Math.max(1e-30, left[i] / left[0]));
            if (t5 < 0 && db <= -5.0) {
                t5 = i;
            }
            if (t25 < 0 && db <= -25.0) {
                t25 = i;
                break;
            }
        }
        assertTrue(t5 >= 0 && t25 > t5, "the tail falls by 25 dB within the window");
        return 3.0 * (t25 - t5) / (double) RATE;
    }

    /** Share of a tone's echo still ringing 0.4 s after the tone stops (energy). */
    private static double tailShare(double hz) {
        Reverb reverb = new Reverb();
        for (int f = 0; f < 60; f++) {
            reverb.process(new short[FRAME], 1.0, 2.0);
        }
        int burst = 10;
        int n = 0;
        double atStop = 0.0;
        double later = 0.0;
        for (int f = 0; f < burst + 30; f++) {
            short[] frame = new short[FRAME];
            if (f < burst) {
                for (int i = 0; i < FRAME; i++) {
                    frame[i] = (short) Math.round(8000.0 * Math.sin(2.0 * Math.PI * hz * n++ / RATE));
                }
                reverb.process(frame, 1.0, 2.0);
                continue;
            }
            reverb.process(frame, 1.0, 2.0);
            if (f == burst) {
                atStop = rms(frame, 0, FRAME);
            } else if (f == burst + 20) {
                later = rms(frame, 0, FRAME);
            }
        }
        return (later * later) / (atStop * atStop);
    }

    private static double rms(short[] pcm, int from, int length) {
        double sum = 0.0;
        for (int i = from; i < from + length; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return Math.sqrt(sum / length);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
