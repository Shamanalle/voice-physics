package com.kasper.vcdistance;

/**
 * The megaphone: it carries the voice furthest the way the speaker looks, and makes it sound like
 * a horn (thin, nasal, a little overdriven), for everyone, also players without the addon.
 */
public final class Megaphone {

    /**
     * Straight behind the speaker the extra range is this share of the extra range in front: enough
     * to make facing the crowd worth it, not so much that turning round silences the megaphone.
     */
    public static final double BACK_SHARE = 0.5;

    private Megaphone() {
    }

    /**
     * The range the megaphone gives towards a listener, times the range without it: the full
     * {@code multiplier} in front, {@code 1 + (multiplier - 1) * BACK_SHARE} straight behind, smoothly in
     * between (the same shape as the voice's facing). When the direction is unknown, the full multiplier.
     *
     * @param cos cosine of the angle between where the speaker looks and the way to the listener
     */
    public static double rangeFactor(double multiplier, double cos) {
        if (Double.isNaN(cos) || multiplier <= 1.0) {
            return multiplier;
        }
        return 1.0 + (multiplier - 1.0) * (1.0 - Directivity.shape(cos) * (1.0 - BACK_SHARE));
    }

    /**
     * The horn sound for 48 kHz mono 16-bit voice frames: a 450 Hz - 3.5 kHz band (24 dB/oct each
     * side), a bump at 2 kHz and a soft clip. Taking the megaphone up or putting it down glides over
     * about 30 ms, so it never clicks.
     * <p>
     * Not thread-safe: one instance per speaker, fed in order.
     */
    public static final class Horn {

        static final double LOW_CUT_HZ = 450.0;
        static final double HIGH_CUT_HZ = 3500.0;
        static final double PEAK_HZ = 2000.0;
        static final double PEAK_DB = 4.0;
        static final double DRIVE = 1.8;
        private static final double GLIDE_SECONDS = 0.03;
        private static final double STEP = 1.0 / (GLIDE_SECONDS * VoiceFilter.SAMPLE_RATE);
        private static final double NORM = 1.0 / Math.tanh(DRIVE);
        private static final double BUTTERWORTH_Q = Math.sqrt(0.5);

        private final Biquad[] chain = {
                Biquad.highPass(LOW_CUT_HZ, BUTTERWORTH_Q),
                Biquad.highPass(LOW_CUT_HZ, BUTTERWORTH_Q),
                Biquad.lowPass(HIGH_CUT_HZ, BUTTERWORTH_Q),
                Biquad.lowPass(HIGH_CUT_HZ, BUTTERWORTH_Q),
                Biquad.peak(PEAK_HZ, 1.0, PEAK_DB)};
        private double wet;
        private boolean engaged;

        /**
         * Processes a frame in place.
         *
         * @param on whether the speaker holds the megaphone now
         * @return the same array
         */
        public short[] process(short[] pcm, boolean on) {
            if (pcm == null || pcm.length == 0) {
                return pcm;
            }
            if (!engaged) {
                if (!on) {
                    return pcm;
                }
                engaged = true;
                wet = 0.0;
                for (Biquad b : chain) {
                    b.clear();
                }
            }
            double target = on ? 1.0 : 0.0;
            for (int i = 0; i < pcm.length; i++) {
                double x = pcm[i] / 32768.0;
                double y = x;
                for (Biquad b : chain) {
                    y = b.next(y);
                }
                y = Math.tanh(DRIVE * y) * NORM;
                if (wet < target) {
                    wet = Math.min(target, wet + STEP);
                } else if (wet > target) {
                    wet = Math.max(target, wet - STEP);
                }
                long s = Math.round((y * wet + x * (1.0 - wet)) * 32768.0);
                pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            }
            if (!on && wet <= 0.0) {
                engaged = false;
            }
            return pcm;
        }

        /** Whether frames still change: held, or still gliding back to the plain voice. */
        public boolean isEngaged() {
            return engaged;
        }

        /**
         * A new talk: no tail of the last one. Held, the horn starts at once (there is nothing to glide
         * from); otherwise it is off.
         */
        public void reset(boolean on) {
            for (Biquad b : chain) {
                b.clear();
            }
            engaged = on;
            wet = on ? 1.0 : 0.0;
        }
    }

    /** One RBJ cookbook section, transposed direct form II. */
    static final class Biquad {
        private final double b0;
        private final double b1;
        private final double b2;
        private final double a1;
        private final double a2;
        private double z1;
        private double z2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad highPass(double hz, double q) {
            double w = 2.0 * Math.PI * hz / VoiceFilter.SAMPLE_RATE;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2.0 * q);
            return new Biquad((1.0 + cos) / 2.0, -(1.0 + cos), (1.0 + cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
        }

        static Biquad lowPass(double hz, double q) {
            double w = 2.0 * Math.PI * hz / VoiceFilter.SAMPLE_RATE;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2.0 * q);
            return new Biquad((1.0 - cos) / 2.0, 1.0 - cos, (1.0 - cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
        }

        static Biquad peak(double hz, double q, double db) {
            double w = 2.0 * Math.PI * hz / VoiceFilter.SAMPLE_RATE;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2.0 * q);
            double a = Math.pow(10.0, db / 40.0);
            return new Biquad(1.0 + alpha * a, -2.0 * cos, 1.0 - alpha * a, 1.0 + alpha / a, -2.0 * cos, 1.0 - alpha / a);
        }

        double next(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        void clear() {
            z1 = 0.0;
            z2 = 0.0;
        }
    }
}
