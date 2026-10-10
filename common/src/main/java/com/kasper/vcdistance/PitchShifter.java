package com.kasper.vcdistance;

/**
 * Changes the pitch of 48 kHz mono 16-bit voice frames without changing their timing, for the
 * Doppler effect.
 * <p>
 * A voice played faster sounds higher but runs ahead of the speaker, and one played slower falls
 * behind. Here the voice is read from a short delay line at the new speed: read faster, the delay
 * shrinks, read slower, it grows. When it reaches its edge (5 or 35 ms) the reading jumps 25 ms
 * back or forward, at the point where the waveform matches best, with a 10 ms crossfade. Between
 * jumps a single reader plays, so the voice stays clean (no phasing), and the timing never drifts.
 * <p>
 * When the pitch is 1 the shifter is bypassed; entering and leaving are crossfaded. A pause in the
 * voice resets it, so every sentence starts without delay.
 * <p>
 * An instance is not thread-safe: the frames of one voice arrive one after another.
 */
public final class PitchShifter {

    private static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;
    /** Shortest and longest delay while shifting, in samples (5.3 ms and 35 ms). */
    static final int MIN_DELAY = 256;
    static final int JUMP = 1200;
    static final int SEARCH = 240;
    static final int MAX_DELAY = MIN_DELAY + JUMP + SEARCH;
    /** Samples compared to find where the waveforms match. */
    private static final int MATCH = 240;
    private static final int FADE = 480;
    private static final int BLOCK = 48;
    private static final double GLIDE_SECONDS = 0.06;
    private static final double GLIDE_COEF = 1.0 - Math.exp(-BLOCK / (GLIDE_SECONDS * VoiceFilter.SAMPLE_RATE));
    private static final double EPSILON = 0.002;
    /** How long the pitch must stay at 1 before the shifter steps out. */
    private static final int IDLE_SAMPLES = VoiceFilter.SAMPLE_RATE / 2;
    /** A gap between frames this long is a pause in speech. */
    static final long PAUSE_NANOS = 250_000_000L;

    private final float[] buf = new float[SIZE];
    /** Samples written in total, and since the last reset. */
    private long written;
    private long history;
    private long lastFrameNanos = Long.MIN_VALUE / 2;

    private boolean engaged;
    private double pitch = 1.0;
    /** Where the reader is, as an absolute sample index (fractional). */
    private double read;
    /** The reader being faded out, and where the fade is; FADE = no fade. */
    private double oldRead;
    private boolean oldIsDry;
    private boolean toDry;
    private int fade = FADE;
    private int idle;

    /**
     * Shifts a frame in place.
     *
     * @param pcm       16-bit mono samples, modified in place
     * @param target    the pitch to glide to, {@link Doppler#MIN_PITCH} - {@link Doppler#MAX_PITCH}
     * @param nowNanos  when the frame arrived ({@link System#nanoTime()})
     * @return whether the frame was changed
     */
    public boolean process(short[] pcm, double target, long nowNanos) {
        if (pcm == null || pcm.length == 0) {
            return false;
        }
        if (nowNanos - lastFrameNanos > PAUSE_NANOS) {
            reset();
        }
        lastFrameNanos = nowNanos;
        target = Double.isNaN(target) ? 1.0 : Math.max(Doppler.MIN_PITCH, Math.min(Doppler.MAX_PITCH, target));
        boolean wanted = Math.abs(target - 1.0) > EPSILON;

        // Without a past to read from (a fresh start on the server) the first frame plays as it came,
        // otherwise the start would fade in silence
        if (!engaged && (!wanted || history < MIN_DELAY + MATCH + 4)) {
            for (short s : pcm) {
                write(s);
            }
            return false;
        }
        if (!engaged) {
            engage(target);
        }
        for (int i = 0; i < pcm.length; i++) {
            write(pcm[i]);
            if ((i % BLOCK) == 0) {
                pitch += (target - pitch) * GLIDE_COEF;
                if (Math.abs(target - 1.0) <= EPSILON && Math.abs(pitch - 1.0) <= EPSILON) {
                    pitch = 1.0;
                }
            }
            double out = tap(read);
            if (fade < FADE) {
                double g = 0.5 - 0.5 * Math.cos(Math.PI * (fade + 0.5) / FADE);
                double dry = pcm[i] / 32768.0;
                double other = oldIsDry ? dry : tap(oldRead);
                out = toDry ? out * (1.0 - g) + dry * g : other * (1.0 - g) + out * g;
                oldRead += pitch;
                fade++;
                if (fade == FADE && toDry) {
                    // The rest of the frame plays as it came
                    pcm[i] = clip(out);
                    engaged = false;
                    toDry = false;
                    pitch = 1.0;
                    for (int j = i + 1; j < pcm.length; j++) {
                        write(pcm[j]);
                    }
                    return true;
                }
            }
            read += pitch;
            pcm[i] = clip(out);
            if (fade >= FADE) {
                spliceIfDue();
                idleCheck();
            }
        }
        return true;
    }

    /** Whether frames are being shifted (or faded back to the original). */
    public boolean isEngaged() {
        return engaged;
    }

    /** Forgets the voice: the next frame starts without delay. */
    public void reset() {
        java.util.Arrays.fill(buf, 0.0F);
        history = 0;
        engaged = false;
        toDry = false;
        fade = FADE;
        pitch = 1.0;
        idle = 0;
    }

    private void engage(double target) {
        engaged = true;
        pitch = 1.0;
        idle = 0;
        // Going up the delay shrinks, so start long; going down it grows, so start short
        int wantedDelay = target > 1.0 ? MIN_DELAY + JUMP : MIN_DELAY;
        long available = Math.max(MIN_DELAY, history - MATCH - 4);
        int nominal = (int) Math.min(wantedDelay, available);
        int delay = bestDelay(0, nominal, Math.max(MIN_DELAY, nominal - SEARCH), Math.min(MAX_DELAY, Math.max(MIN_DELAY, nominal + SEARCH)));
        read = written - delay;
        oldIsDry = true;
        toDry = false;
        fade = 0;
    }

    /** At the delay's edge: jump back or forward to where the waveform matches. */
    private void spliceIfDue() {
        double delay = written - read;
        int nominal;
        if (delay < MIN_DELAY) {
            nominal = (int) Math.round(delay) + JUMP;
        } else if (delay > MAX_DELAY) {
            nominal = (int) Math.round(delay) - JUMP;
        } else {
            return;
        }
        int lo = Math.max(MIN_DELAY / 2, nominal - SEARCH);
        int hi = Math.min(MAX_DELAY, nominal + SEARCH);
        if (history < hi + MATCH + 4) {
            hi = (int) Math.max(lo, Math.min(hi, history - MATCH - 4));
        }
        double frac = read - Math.floor(read);
        int from = (int) Math.round(delay);
        int to = bestDelay(from, nominal, lo, hi);
        oldRead = read;
        oldIsDry = false;
        read = written - to + frac;
        fade = 0;
    }

    /** After half a second at pitch 1 the shifter fades back to the original voice. */
    private void idleCheck() {
        if (pitch != 1.0) {
            idle = 0;
            return;
        }
        if (++idle >= IDLE_SAMPLES) {
            idle = 0;
            toDry = true;
            fade = 0;
        }
    }

    /**
     * The delay between {@code lo} and {@code hi} whose last samples look most like the last
     * samples at {@code from}: normalised cross-correlation, every second sample.
     */
    private int bestDelay(int from, int nominal, int lo, int hi) {
        if (hi <= lo) {
            return Math.max(lo, Math.min(hi, nominal));
        }
        long refEnd = written - 1 - from;
        double refEnergy = 0.0;
        for (int k = 0; k < MATCH; k += 2) {
            double a = buf[(int) ((refEnd - k) & MASK)];
            refEnergy += a * a;
        }
        if (refEnergy < 1.0e-6) {
            return nominal; // silence: any place will do
        }
        int best = nominal;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int d = lo; d <= hi; d += 2) {
            long end = written - 1 - d;
            double dot = 0.0;
            double energy = 0.0;
            for (int k = 0; k < MATCH; k += 2) {
                double a = buf[(int) ((refEnd - k) & MASK)];
                double b = buf[(int) ((end - k) & MASK)];
                dot += a * b;
                energy += b * b;
            }
            double score = energy > 1.0e-9 ? dot / Math.sqrt(energy) : Double.NEGATIVE_INFINITY;
            // Ties go to the nominal jump
            score -= Math.abs(d - nominal) * 1.0e-9;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    private void write(short s) {
        buf[(int) (written & MASK)] = s / 32768.0F;
        written++;
        history++;
    }

    /** The voice at a fractional position, by four-point Hermite interpolation. */
    private double tap(double position) {
        long i = (long) Math.floor(position);
        double t = position - i;
        // Never read ahead of what was written
        if (i + 2 > written - 1) {
            i = written - 3;
            t = 0.0;
        }
        double ym1 = buf[(int) ((i - 1) & MASK)];
        double y0 = buf[(int) (i & MASK)];
        double y1 = buf[(int) ((i + 1) & MASK)];
        double y2 = buf[(int) ((i + 2) & MASK)];
        double c1 = 0.5 * (y1 - ym1);
        double c2 = ym1 - 2.5 * y0 + 2.0 * y1 - 0.5 * y2;
        double c3 = 0.5 * (y2 - ym1) + 1.5 * (y0 - y1);
        return ((c3 * t + c2) * t + c1) * t + y0;
    }

    private static short clip(double v) {
        double s = v * 32768.0;
        if (s > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (s < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) Math.round(s);
    }
}
