package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Which way the speaker faces: the shape over the angle and what the filter makes of it. */
public class DirectivityTest {

    private static final int FRAME = 960;

    private static double cosOf(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }

    @Test
    @DisplayName("Nothing in front, a smooth rise past 40 degrees, all of it behind")
    void shapeOverTheAngle() {
        assertEquals(0.0, Directivity.shape(1.0));
        assertEquals(0.0, Directivity.shape(cosOf(30)));
        assertEquals(0.0, Directivity.shape(cosOf(40)), 1e-12);
        assertEquals(1.0, Directivity.shape(-1.0), 1e-12);
        double previous = 0.0;
        for (int deg = 41; deg <= 180; deg++) {
            double s = Directivity.shape(cosOf(deg));
            assertTrue(s >= previous, "grows with the angle at " + deg);
            assertTrue(s - previous < 0.02, "no step at " + deg);
            previous = s;
        }
        assertEquals(0.0, Directivity.shape(Double.NaN), "unknown: nothing");
    }

    @Test
    @DisplayName("The cosine from the look and the way to the listener; too short or unknown is NaN")
    void cosine() {
        assertEquals(1.0, Directivity.cos(0, 0, 1, 0, 0, 5), 1e-12);
        assertEquals(-1.0, Directivity.cos(0, 0, 1, 0, 0, -5), 1e-12);
        assertEquals(0.0, Directivity.cos(1, 0, 0, 0, 0, 3), 1e-12);
        assertEquals(1.0, Directivity.cos(0, 0, 2, 0, 0, 0.5), 1e-12, "lengths do not matter");
        assertTrue(Double.isNaN(Directivity.cos(0, 0, 0, 0, 0, 5)));
        assertTrue(Double.isNaN(Directivity.cos(0, 0, 1, 0, 0, 0.01)));
        assertTrue(Double.isNaN(Directivity.cos(Double.NaN, 0, 1, 0, 0, 5)));
    }

    @Test
    @DisplayName("Strength scales it, the indirect share takes it away, unknown or off changes nothing")
    void effectScales() {
        EnvironmentEffects.Effect back = Directivity.effect(-1.0, 1.0, 0.0);
        assertEquals(Directivity.BACK_LOSS_DB, back.lossDb(), 1e-9);
        assertEquals(Directivity.BACK_MUFFLE, back.muffle(), 1e-9);
        assertEquals(1.5 * Directivity.BACK_LOSS_DB, Directivity.effect(-1.0, 1.5, 0.0).lossDb(), 1e-9);
        assertEquals(1.5 * Directivity.BACK_LOSS_DB, Directivity.effect(-1.0, 9.0, 0.0).lossDb(), 1e-9, "capped");
        assertEquals(0.5 * Directivity.BACK_LOSS_DB, Directivity.effect(-1.0, 1.0, 0.5).lossDb(), 1e-9);
        assertSame(EnvironmentEffects.Effect.NONE, Directivity.effect(-1.0, 1.0, 1.0));
        assertSame(EnvironmentEffects.Effect.NONE, Directivity.effect(1.0, 1.0, 0.0));
        assertSame(EnvironmentEffects.Effect.NONE, Directivity.effect(Double.NaN, 1.0, 0.0));
        assertSame(EnvironmentEffects.Effect.NONE, Directivity.effect(-1.0, 0.0, 0.0));
        assertSame(EnvironmentEffects.Effect.NONE, Directivity.effect(-1.0, Double.NaN, 0.0));
        assertEquals(Directivity.BACK_LOSS_DB, Directivity.effect(-1.0, 1.0, Double.NaN).lossDb(), 1e-9);
    }

    @Test
    @DisplayName("Through the filter: close to measured speech behind and to the side, the front untouched")
    void heardThroughTheFilter() {
        EnvironmentEffects.Effect back = Directivity.effect(-1.0, 1.0, 0.0);
        double low = toneChangeDb(250, back);
        double mid = toneChangeDb(1000, back);
        double high = toneChangeDb(4000, back);
        assertTrue(low > -5.5 && low < -4.5, "250 Hz behind: " + low);
        assertTrue(mid > -6.0 && mid < -4.0, "1 kHz behind: " + mid);
        assertTrue(high > -12.0 && high < -8.5, "4 kHz behind: " + high);

        EnvironmentEffects.Effect side = Directivity.effect(0.0, 1.0, 0.0);
        double sideMid = toneChangeDb(1000, side);
        double sideHigh = toneChangeDb(4000, side);
        assertTrue(sideMid > -2.0 && sideMid < -0.5, "1 kHz to the side: " + sideMid);
        assertTrue(sideHigh < sideMid && sideHigh > -3.0, "4 kHz to the side: " + sideHigh);

        VoiceFilter front = new VoiceFilter();
        EnvironmentEffects.Effect none = Directivity.effect(cosOf(20), 1.0, 0.0);
        short[] pcm = sine(1000, 0);
        short[] copy = pcm.clone();
        front.process(pcm, none.muffle(), none.lossDb());
        assertArrayEquals(copy, pcm, "in front the voice passes bit for bit");
    }

    /** Level change of a sine through a settled filter, in dB. */
    private static double toneChangeDb(double hz, EnvironmentEffects.Effect e) {
        VoiceFilter filter = new VoiceFilter();
        double in = 0.0;
        double out = 0.0;
        for (int f = 0; f < 50; f++) {
            short[] pcm = sine(hz, f);
            short[] dry = pcm.clone();
            filter.process(pcm, e.muffle(), e.lossDb());
            if (f >= 25) {
                for (int i = 0; i < FRAME; i++) {
                    in += (double) dry[i] * dry[i];
                    out += (double) pcm[i] * pcm[i];
                }
            }
        }
        return 10.0 * Math.log10(out / in);
    }

    private static short[] sine(double hz, int frameIndex) {
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            long n = (long) frameIndex * FRAME + i;
            pcm[i] = (short) Math.round(8000 * Math.sin(2.0 * Math.PI * hz * n / VoiceFilter.SAMPLE_RATE));
        }
        return pcm;
    }
}
