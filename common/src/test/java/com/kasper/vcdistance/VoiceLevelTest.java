package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** A voice's effort against the speaker's usual level, and what a wall then takes off a whisper and a shout. */
public class VoiceLevelTest {

    private static final int FRAME = 960;
    private static final long FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    private static final double STRENGTH = DistanceConfig.DEFAULT_OCCLUSION_STRENGTH;

    /** A 300 Hz tone at this RMS level in dBFS. */
    private static short[] tone(double db) {
        double amp = Math.pow(10.0, db / 20.0) * Math.sqrt(2.0) * 32767.0;
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            pcm[i] = (short) Math.round(amp * Math.sin(2.0 * Math.PI * 300.0 * i / VoiceLevel.SAMPLE_RATE));
        }
        return pcm;
    }

    /** Feeds frames at this level for this long; returns the clock after them. */
    private static long speak(VoiceLevel.Tracker t, double db, double seconds, boolean whisper, long now) {
        short[] pcm = tone(db);
        int frames = (int) Math.round(seconds / 0.02);
        for (int i = 0; i < frames; i++) {
            t.update(pcm, whisper, now);
            now += FRAME_NANOS;
        }
        return now;
    }

    private static double wall(double stone) {
        return OcclusionModel.lossDb(stone, STRENGTH);
    }

    @Test
    @DisplayName("The frame level is its RMS in dBFS")
    void frameLevel() {
        assertEquals(-20.0, VoiceLevel.levelDb(tone(-20.0)), 0.1);
        assertEquals(-90.0, VoiceLevel.levelDb(new short[FRAME]));
        assertEquals(-90.0, VoiceLevel.levelDb(null));
    }

    @Test
    @DisplayName("Out in the open and through an open doorway nothing changes")
    void noWallNoChange() {
        assertEquals(0.0, VoiceLevel.extraLossDb(0.0, -20.0));
        assertEquals(0.0, VoiceLevel.extraLossDb(Double.NaN, -20.0));
        assertEquals(0.0, VoiceLevel.extraLossDb(-3.0, -20.0));
    }

    @Test
    @DisplayName("A normal voice: nothing more through one or two stone walls, a little through three")
    void normalVoice() {
        assertEquals(0.0, VoiceLevel.extraLossDb(wall(1), 0.0));
        assertEquals(0.0, VoiceLevel.extraLossDb(wall(2), 0.0));
        double three = VoiceLevel.extraLossDb(wall(3), 0.0);
        assertTrue(three > 2.0 && three < 6.0, "three walls: " + three);
    }

    @Test
    @DisplayName("A whisper is lost behind a wall; a shout carries through three")
    void whisperAndShout() {
        double w = VoiceLevel.WHISPER_EFFORT_DB;
        double one = VoiceLevel.extraLossDb(wall(1), w);
        double two = VoiceLevel.extraLossDb(wall(2), w);
        assertTrue(one > 10.0 && one < 16.0, "whisper, one wall: " + one);
        assertTrue(two > 20.0 && two < 28.0, "whisper, two walls: " + two);
        assertEquals(VoiceLevel.MAX_EXTRA_DB, VoiceLevel.extraLossDb(wall(3), w), 1e-9);
        assertEquals(0.0, VoiceLevel.extraLossDb(wall(3), 12.0));
        assertEquals(VoiceLevel.MAX_EXTRA_DB, VoiceLevel.extraLossDb(OcclusionModel.MAX_LOSS_DB, -100.0), 1e-9,
                "never more than the cap");
    }

    @Test
    @DisplayName("The loss grows smoothly with the wall and the quietness, with no step anywhere")
    void smooth() {
        for (double effort = -20.0; effort <= 15.0; effort += 0.5) {
            double previous = 0.0;
            for (double loss = 0.0; loss <= OcclusionModel.MAX_LOSS_DB; loss += 0.05) {
                double extra = VoiceLevel.extraLossDb(loss, effort);
                assertTrue(extra >= previous - 1e-12, "grows with the wall at " + loss + " dB, effort " + effort);
                assertTrue(extra - previous < 0.4, "no step at " + loss + " dB, effort " + effort);
                previous = extra;
            }
        }
        for (double loss = 1.0; loss <= OcclusionModel.MAX_LOSS_DB; loss += 1.0) {
            double previous = Double.MAX_VALUE;
            for (double effort = -20.0; effort <= 15.0; effort += 0.1) {
                double extra = VoiceLevel.extraLossDb(loss, effort);
                assertTrue(extra <= previous + 1e-12, "a louder voice never loses more, " + loss + " dB at " + effort);
                previous = extra;
            }
        }
        assertEquals(VoiceLevel.extraLossDb(wall(1), 0.0), VoiceLevel.extraLossDb(wall(1), Double.NaN),
                "an unknown effort counts as a normal voice");
    }

    @Test
    @DisplayName("Talking normally is effort 0 whatever the microphone gain")
    void normalIsZeroAtAnyGain() {
        for (double usual : new double[]{-34.0, -24.0, -14.0}) {
            VoiceLevel.Tracker t = new VoiceLevel.Tracker();
            speak(t, usual, 30.0, false, 1_000L);
            assertEquals(0.0, t.effortDb(false), 0.5, "usual level " + usual);
            assertEquals(usual, t.baselineDb(), 0.5);
        }
    }

    @Test
    @DisplayName("Speech with syllables and gaps between words stays near effort 0, so walls do not pump it")
    void syllablesStayNearZero() {
        VoiceLevel.Tracker t = new VoiceLevel.Tracker();
        long now = 1_000L;
        // Four syllables a second, loud and soft ones, and a short gap after every word of three
        double[] pattern = {-20, -22, -26, -30, -21, -24, -28, -33, -19, -23, -27, -90, -90, -90};
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < 40 * 50; i++) {
            t.update(tone(pattern[i % pattern.length]), false, now);
            now += FRAME_NANOS;
            if (i > 20 * 50) {
                min = Math.min(min, t.effortDb(false));
                max = Math.max(max, t.effortDb(false));
            }
        }
        assertTrue(min > -6.0 && max < 6.0, "effort over normal speech: " + min + " to " + max);
        assertTrue(VoiceLevel.extraLossDb(wall(2), min) < 3.0, "two walls take little more even on soft syllables");
    }

    @Test
    @DisplayName("Raising the voice reads as effort within a few frames, and dropping it a little slower")
    void raisingAndDropping() {
        VoiceLevel.Tracker t = new VoiceLevel.Tracker();
        long now = speak(t, -26.0, 30.0, false, 1_000L);
        now = speak(t, -14.0, 0.2, false, now);
        assertTrue(t.effortDb(false) > 10.0, "a shout after 0.2 s: " + t.effortDb(false));
        now = speak(t, -36.0, 0.1, false, now);
        assertTrue(t.effortDb(false) > -5.0, "the level falls slower than it rises: " + t.effortDb(false));
        speak(t, -36.0, 1.5, false, now);
        assertTrue(t.effortDb(false) < -8.0, "a quiet voice after 1.5 s: " + t.effortDb(false));
    }

    @Test
    @DisplayName("Pauses change nothing, and a shout now and then does not become the usual level")
    void pausesAndShouts() {
        VoiceLevel.Tracker t = new VoiceLevel.Tracker();
        long now = speak(t, -24.0, 30.0, false, 1_000L);
        double usual = t.baselineDb();
        now = speak(t, -12.0, 0.5, false, now);
        double shout = t.effortDb(false);
        // Silence and the noise floor between words: the shout's effort holds
        now = speak(t, -90.0, 2.0, false, now);
        now = speak(t, -58.0, 1.0, false, now);
        assertEquals(shout, t.effortDb(false), 1e-9);
        assertEquals(usual, t.baselineDb(), 1.0, "half a second of shouting barely moves the usual level");
        // After the pause the next word is measured afresh, not glided into
        speak(t, -24.0, 0.04, false, now);
        assertEquals(0.0, t.effortDb(false), 1.0);
    }

    @Test
    @DisplayName("A whisper counts as quiet, even at full microphone level, and does not move the usual level")
    void whisperFlag() {
        VoiceLevel.Tracker t = new VoiceLevel.Tracker();
        long now = speak(t, -24.0, 20.0, false, 1_000L);
        double usual = t.baselineDb();
        now = speak(t, -24.0, 10.0, true, now);
        assertEquals(VoiceLevel.WHISPER_EFFORT_DB, t.effortDb(true), 1e-9);
        now = speak(t, -40.0, 10.0, true, now);
        assertTrue(t.effortDb(true) <= VoiceLevel.WHISPER_EFFORT_DB);
        assertEquals(usual, t.baselineDb(), 1e-9, "whispers are not the usual voice");
        speak(t, -24.0, 0.5, false, now);
        assertEquals(0.0, t.effortDb(false), 1.0, "back to talking: back to normal");
    }

    @Test
    @DisplayName("Before the voice is learnt, the usual level starts from a typical one and stays in bounds")
    void startAndBounds() {
        VoiceLevel.Tracker fresh = new VoiceLevel.Tracker();
        assertEquals(0.0, fresh.effortDb(false));
        assertEquals(VoiceLevel.WHISPER_EFFORT_DB, fresh.effortDb(true));
        assertEquals(VoiceLevel.PRIOR_DB, fresh.baselineDb());

        VoiceLevel.Tracker shouter = new VoiceLevel.Tracker();
        speak(shouter, -1.0, 120.0, false, 1_000L);
        assertEquals(VoiceLevel.PRIOR_DB + VoiceLevel.BASELINE_SPAN_DB, shouter.baselineDb(), 1e-9);
        assertTrue(shouter.effortDb(false) > 5.0, "someone who only shouts still shouts");

        VoiceLevel.Tracker loud = new VoiceLevel.Tracker();
        speak(loud, 0.0, 0.2, false, 1_000L);
        assertTrue(loud.effortDb(false) <= VoiceLevel.EFFORT_MAX_DB);
        // A new speaker's first words land near the usual level quickly
        VoiceLevel.Tracker quietMic = new VoiceLevel.Tracker();
        speak(quietMic, -34.0, 6.0, false, 1_000L);
        assertTrue(Math.abs(quietMic.effortDb(false)) < 3.0, "after 6 s: " + quietMic.effortDb(false));
    }

    @Test
    @DisplayName("Empty and broken frames are pauses")
    void brokenFrames() {
        VoiceLevel.Tracker t = new VoiceLevel.Tracker();
        assertEquals(0.0, t.update((short[]) null, false, 1L));
        assertEquals(0.0, t.update(new short[0], false, 2L));
        assertEquals(0.0, t.update(Double.NaN, 0.02, false, 3L));
        assertEquals(0.0, t.update(-20.0, 0.0, false, 4L));
        assertEquals(VoiceLevel.PRIOR_DB, t.baselineDb());
    }

    @Test
    @DisplayName("The book keeps a speaker's voice across pauses and forgets it after ten minutes")
    void book() {
        VoiceLevel.Book book = new VoiceLevel.Book();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long now = 1_000L;
        VoiceLevel.Tracker ta = book.of(a, now);
        assertSame(ta, book.of(a, now + TimeUnit.SECONDS.toNanos(30)), "same speaker, same tracker");
        book.of(b, now);
        assertEquals(2, book.size());
        now += VoiceLevel.Book.FORGET_NANOS - TimeUnit.SECONDS.toNanos(1);
        speak(ta, -24.0, 0.1, false, now);
        // b was last heard more than ten minutes before this
        book.of(UUID.randomUUID(), now + TimeUnit.MINUTES.toNanos(1));
        assertSame(ta, book.get(a));
        assertNull(book.get(b));
        book.forget(a);
        assertNull(book.get(a));
        assertNull(book.get(null));
        book.clear();
        assertEquals(0, book.size());
    }
}
