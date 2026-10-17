package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * How loud someone speaks compared with how they usually speak, and what that does behind a wall.
 * <p>
 * A wall takes the same decibels off a whisper and a shout, but a whisper starts much lower: behind
 * one wall it already sinks under the noise of the world and is lost, while a shout still carries
 * through two. Simple Voice Chat sends a whisper at the speaker's normal level (only its range is
 * shorter) and every microphone is set differently, so the level of a frame alone says little.
 * {@link Tracker} learns each speaker's usual level and measures the effort against it; the
 * whisper flag counts as a quiet voice whatever the microphone heard.
 * <p>
 * {@link #extraLossDb} adds loss once what is left of the voice behind the wall falls below what
 * a normal voice keeps through two or three stone walls, growing twice as fast as the shortfall.
 * In the open, and through an open doorway, nothing changes.
 */
public final class VoiceLevel {

    /** Simple Voice Chat's sample rate. */
    static final double SAMPLE_RATE = 48000.0;
    /** A usual speaking level before anything is heard from the speaker, in dBFS. */
    static final double PRIOR_DB = -24.0;
    /** The usual level stays this close to {@link #PRIOR_DB}: a speaker who only ever shouts still shouts. */
    static final double BASELINE_SPAN_DB = 15.0;
    /** Frames below the usual level by more than this, or below {@link #SILENCE_DB}, are pauses. */
    static final double GATE_BELOW_DB = 30.0;
    static final double SILENCE_DB = -60.0;
    /** The level follows a louder voice quickly and a quieter one slowly, across the gaps between words. */
    static final double ATTACK_SECONDS = 0.05;
    static final double RELEASE_SECONDS = 0.3;
    /**
     * The usual level averages the speech heard so far, over at most this long; the first seconds
     * move it faster.
     */
    static final double BASELINE_MIN_SECONDS = 2.0;
    static final double BASELINE_MAX_SECONDS = 20.0;
    /** A shout moves the usual level no more than a voice this much louder would. */
    static final double BASELINE_STEP_DB = 10.0;
    /** A pause this long: the next word is measured afresh. */
    static final long PAUSE_NANOS = TimeUnit.MILLISECONDS.toNanos(400);
    public static final double EFFORT_MIN_DB = -20.0;
    public static final double EFFORT_MAX_DB = 15.0;
    /** A whisper is at least this much quieter than the speaker's usual voice. */
    public static final double WHISPER_EFFORT_DB = -15.0;

    /** Wall loss a normal voice takes without losing more (between two and three stone walls at the default strength). */
    static final double FREE_DB = 16.0;
    /** Width of the soft start around {@link #FREE_DB}. */
    static final double KNEE_DB = 4.0;
    /** Decibels lost per decibel below {@link #FREE_DB}. */
    static final double SLOPE = 2.0;
    public static final double MAX_EXTRA_DB = 30.0;
    /** The extra loss fades in over the first decibels of wall, so a thin pane does not switch it on. */
    static final double FADE_IN_DB = 3.0;

    private VoiceLevel() {
    }

    /**
     * Loss to add for this voice behind this wall, in dB.
     *
     * @param wallLossDb what the walls take off the voice (through them and round them), dB
     * @param effortDb   {@link Tracker#effortDb}: 0 the speaker's usual voice, negative quieter
     */
    public static double extraLossDb(double wallLossDb, double effortDb) {
        if (!(wallLossDb > 0.0)) {
            return 0.0; // also NaN
        }
        double effort = Double.isNaN(effortDb) ? 0.0 : clamp(effortDb, EFFORT_MIN_DB, EFFORT_MAX_DB);
        double shortfall = wallLossDb - (FREE_DB + effort);
        double half = KNEE_DB / 2.0;
        double knee;
        if (shortfall <= -half) {
            return 0.0;
        } else if (shortfall >= half) {
            knee = shortfall;
        } else {
            knee = (shortfall + half) * (shortfall + half) / (2.0 * KNEE_DB);
        }
        return Math.min(MAX_EXTRA_DB, SLOPE * knee) * Math.min(1.0, wallLossDb / FADE_IN_DB);
    }

    /** RMS level of a frame in dBFS, -90 for silence. */
    public static double levelDb(short[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return -90.0;
        }
        double sum = 0.0;
        for (short v : pcm) {
            sum += (double) v * v;
        }
        double rms = Math.sqrt(sum / pcm.length) / 32768.0;
        if (rms <= 1e-5) {
            return -90.0;
        }
        return Math.max(-90.0, 20.0 * Math.log10(rms));
    }

    /**
     * One speaker's voice, frame by frame: how loud it is now against how loud it usually is. Fed
     * the frames as they arrive, before anything else changes them. Safe to feed from one thread and
     * read from others.
     */
    public static final class Tracker {
        private double baselineDb = PRIOR_DB;
        private double speechSeconds;
        private double envelopeDb = Double.NaN;
        private long lastSpeechNanos;
        private volatile double effortDb;
        volatile long lastUsedNanos;

        /**
         * @param pcm     the frame as it came, 48 kHz mono
         * @param whisper the speaker is whispering
         * @return {@link #effortDb} after this frame
         */
        public double update(short[] pcm, boolean whisper, long nowNanos) {
            if (pcm == null || pcm.length == 0) {
                lastUsedNanos = nowNanos;
                return effortDb(whisper);
            }
            return update(levelDb(pcm), pcm.length / SAMPLE_RATE, whisper, nowNanos);
        }

        /**
         * @param levelDb RMS level of the frame, dBFS ({@link #levelDb})
         * @param seconds how long the frame is
         */
        public synchronized double update(double levelDb, double seconds, boolean whisper, long nowNanos) {
            lastUsedNanos = nowNanos;
            if (Double.isNaN(levelDb) || !(seconds > 0.0)
                    || levelDb < Math.max(SILENCE_DB, baselineDb - GATE_BELOW_DB)) {
                // A pause: the effort of the last words holds
                return effortDb(whisper);
            }
            if (Double.isNaN(envelopeDb) || nowNanos - lastSpeechNanos > PAUSE_NANOS) {
                envelopeDb = levelDb;
            } else {
                double tau = levelDb > envelopeDb ? ATTACK_SECONDS : RELEASE_SECONDS;
                envelopeDb += (levelDb - envelopeDb) * (1.0 - Math.exp(-seconds / tau));
            }
            lastSpeechNanos = nowNanos;
            // A whisper is not how the speaker usually sounds
            if (!whisper) {
                speechSeconds += seconds;
                double tau = clamp(speechSeconds, BASELINE_MIN_SECONDS, BASELINE_MAX_SECONDS);
                double toward = clamp(envelopeDb, baselineDb - BASELINE_STEP_DB, baselineDb + BASELINE_STEP_DB);
                baselineDb += (toward - baselineDb) * (1.0 - Math.exp(-seconds / tau));
                baselineDb = clamp(baselineDb, PRIOR_DB - BASELINE_SPAN_DB, PRIOR_DB + BASELINE_SPAN_DB);
            }
            effortDb = clamp(envelopeDb - baselineDb, EFFORT_MIN_DB, EFFORT_MAX_DB);
            return effortDb(whisper);
        }

        /**
         * Decibels above (positive) or below the speaker's usual voice, from {@link #EFFORT_MIN_DB}
         * to {@link #EFFORT_MAX_DB}; a whisper is at most {@link #WHISPER_EFFORT_DB}.
         */
        public double effortDb(boolean whisper) {
            double e = effortDb;
            return whisper ? Math.min(e, WHISPER_EFFORT_DB) : e;
        }

        /** The speaker's usual level as learnt so far, dBFS. */
        public synchronized double baselineDb() {
            return baselineDb;
        }
    }

    /**
     * Trackers by voice channel. They outlive the short pauses that make the rest of a speaker's
     * state be forgotten, so the usual level is not learnt again after every sentence.
     */
    public static final class Book {
        static final long FORGET_NANOS = TimeUnit.MINUTES.toNanos(10);
        private static final long PRUNE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

        private final Map<UUID, Tracker> trackers = new ConcurrentHashMap<>();
        private volatile long lastPruneNanos;
        private volatile boolean pruned;

        public Tracker of(UUID channel, long nowNanos) {
            if (!pruned || nowNanos - lastPruneNanos > PRUNE_INTERVAL_NANOS) {
                pruned = true;
                lastPruneNanos = nowNanos;
                trackers.values().removeIf(t -> nowNanos - t.lastUsedNanos > FORGET_NANOS);
            }
            Tracker t = trackers.computeIfAbsent(channel, c -> new Tracker());
            t.lastUsedNanos = nowNanos;
            return t;
        }

        public Tracker get(UUID channel) {
            return channel == null ? null : trackers.get(channel);
        }

        public void forget(UUID channel) {
            if (channel != null) {
                trackers.remove(channel);
            }
        }

        public int size() {
            return trackers.size();
        }

        public void clear() {
            trackers.clear();
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
