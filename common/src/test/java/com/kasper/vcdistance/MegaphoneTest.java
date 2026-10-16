package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The megaphone's range by direction and its horn sound. */
public class MegaphoneTest {

    private static final int FRAME = 960;

    private static short[] tone(double hz, double amplitude, int frames) {
        short[] pcm = new short[FRAME * frames];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * hz * i / VoiceFilter.SAMPLE_RATE));
        }
        return pcm;
    }

    /** RMS of the last frame, after the filters have settled. */
    private static double tail(short[] pcm) {
        double sum = 0;
        for (int i = pcm.length - FRAME; i < pcm.length; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return Math.sqrt(sum / FRAME);
    }

    private static double gainDb(double hz) {
        short[] in = tone(hz, 3000, 10);
        double before = tail(in);
        short[] out = in.clone();
        Megaphone.Horn horn = new Megaphone.Horn();
        horn.reset(true);
        for (int f = 0; f < 10; f++) {
            short[] frame = new short[FRAME];
            System.arraycopy(out, f * FRAME, frame, 0, FRAME);
            horn.process(frame, true);
            System.arraycopy(frame, 0, out, f * FRAME, FRAME);
        }
        return 20 * Math.log10(tail(out) / before);
    }

    @Test
    @DisplayName("Range: the full multiplier ahead, half of the extra behind, in between to the side, all round when unknown")
    void rangeFactor() {
        assertEquals(2.5, Megaphone.rangeFactor(2.5, 1.0), 1e-9);
        assertEquals(1.0 + 1.5 * Megaphone.BACK_SHARE, Megaphone.rangeFactor(2.5, -1.0), 1e-9);
        double side = Megaphone.rangeFactor(2.5, 0.0);
        assertTrue(side > 1.0 + 1.5 * Megaphone.BACK_SHARE && side < 2.5, String.valueOf(side));
        assertEquals(2.5, Megaphone.rangeFactor(2.5, Double.NaN), 1e-9);
        assertEquals(1.0, Megaphone.rangeFactor(1.0, -1.0), 1e-9);
        assertEquals(0.5, Megaphone.rangeFactor(0.5, -1.0), 1e-9, "a megaphone that makes quieter is left as it is");
        double last = Double.MAX_VALUE;
        for (int deg = 0; deg <= 180; deg += 5) {
            double f = Megaphone.rangeFactor(3.0, Math.cos(Math.toRadians(deg)));
            assertTrue(f <= last + 1e-12, "never further when turning away, at " + deg);
            last = f;
        }
    }

    @Test
    @DisplayName("Horn: lows and highs are cut, the voice band stays")
    void band() {
        assertTrue(gainDb(100) < -12, "100 Hz: " + gainDb(100));
        assertTrue(gainDb(200) < -6, "200 Hz: " + gainDb(200));
        assertTrue(gainDb(1500) > -3, "1.5 kHz: " + gainDb(1500));
        assertTrue(gainDb(2000) > -1, "2 kHz: " + gainDb(2000));
        assertTrue(gainDb(8000) < -12, "8 kHz: " + gainDb(8000));
    }

    @Test
    @DisplayName("Horn: a full-scale voice is clipped softly, never wraps around")
    void loud() {
        short[] pcm = tone(2000, 32767, 4);
        Megaphone.Horn horn = new Megaphone.Horn();
        horn.reset(true);
        for (int f = 0; f < 4; f++) {
            short[] frame = new short[FRAME];
            System.arraycopy(pcm, f * FRAME, frame, 0, FRAME);
            horn.process(frame, true);
            int peak = 0;
            for (int i = 1; i < FRAME; i++) {
                peak = Math.max(peak, Math.abs(frame[i]));
                assertTrue(Math.abs(frame[i] - frame[i - 1]) < 40000, "no jump from wrapping at " + i);
            }
            if (f > 0) {
                assertTrue(peak > 20000, "still loud: " + peak);
            }
        }
    }

    @Test
    @DisplayName("Horn: taking it up and putting it down glide, without clicks; unheld it leaves frames alone")
    void glide() {
        Megaphone.Horn horn = new Megaphone.Horn();
        short[] plain = tone(1000, 6000, 1);
        short[] frame = plain.clone();
        horn.process(frame, false);
        assertArrayEquals(plain, frame, "not held: untouched");
        assertFalse(horn.isEngaged());

        // The largest step between samples while held, without gliding: what the horn itself does to this tone
        Megaphone.Horn steady = new Megaphone.Horn();
        steady.reset(true);
        short[] warm = tone(1000, 6000, 4);
        int heldStep = 0;
        for (int f = 0; f < 4; f++) {
            short[] fr = new short[FRAME];
            System.arraycopy(warm, f * FRAME, fr, 0, FRAME);
            steady.process(fr, true);
            for (int i = 1; f > 1 && i < FRAME; i++) {
                heldStep = Math.max(heldStep, Math.abs(fr[i] - fr[i - 1]));
            }
        }
        int plainStep = (int) Math.ceil(6000 * 2 * Math.PI * 1000 / VoiceFilter.SAMPLE_RATE);

        short[] voice = tone(1000, 6000, 12);
        int prev = 0;
        int maxStep = 0;
        boolean[] on = {false, true, true, true, true, true, false, false, false, false, false, false};
        for (int f = 0; f < 12; f++) {
            short[] fr = new short[FRAME];
            System.arraycopy(voice, f * FRAME, fr, 0, FRAME);
            horn.process(fr, on[f]);
            for (int i = 0; i < FRAME; i++) {
                if (f > 0 || i > 0) {
                    maxStep = Math.max(maxStep, Math.abs(fr[i] - prev));
                }
                prev = fr[i];
            }
        }
        // While gliding the plain and the horn voice add up, out of phase at most; a click would be far more
        assertTrue(maxStep <= heldStep + plainStep, "largest step " + maxStep + ", held " + heldStep + ", plain " + plainStep);
        assertFalse(horn.isEngaged(), "put down, after the glide it lets go");

        horn.reset(true);
        assertTrue(horn.isEngaged());
        short[] loudHigh = tone(8000, 6000, 1);
        horn.process(loudHigh, true);
        double rms = 0;
        for (int i = FRAME / 2; i < FRAME; i++) {
            rms += (double) loudHigh[i] * loudHigh[i];
        }
        assertTrue(Math.sqrt(rms / (FRAME / 2.0)) < 6000 / Math.sqrt(2) / 4, "after reset(true) the horn sounds at once");
        horn.reset(false);
        assertFalse(horn.isEngaged());
    }
}
