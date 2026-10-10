package com.kasper.vcdistance;

/**
 * The Doppler effect: a voice coming closer sounds higher, one moving away lower.
 * <p>
 * Only the speed at which the distance between speaker and listener changes matters (the closing
 * speed), so riding in the same minecart or flying side by side changes nothing. Sound travels 343
 * blocks a second, as in air; at that speed running barely changes a voice (1.6%), elytra and boats
 * on ice do (9 - 25%), so the strength scales the speeds up to three times.
 * <p>
 * The pitch itself is changed by {@link PitchShifter}, which keeps the voice's timing.
 */
public final class Doppler {

    /** Speed of sound in blocks per second (one block = one metre). */
    public static final double SOUND_SPEED = 343.0;
    public static final double STRENGTH_MIN = 0.0;
    public static final double STRENGTH_MAX = 3.0;
    public static final double MIN_PITCH = 0.7;
    public static final double MAX_PITCH = 1.4;
    /** Closing speeds below this (walking about, turning round) change nothing, so voices do not waver. */
    static final double DEADZONE = 1.0;
    /** Faster than anything moves in the game: a teleport, a respawn or a world change. */
    static final double TELEPORT_SPEED = 120.0;
    /** How quickly the measured closing speed follows the movement. */
    static final double SMOOTHING_SECONDS = 0.25;

    private Doppler() {
    }

    /**
     * How much higher (above 1) or lower the voice sounds.
     *
     * @param closing  blocks per second the distance shrinks; negative while it grows
     * @param strength 1 = as in air, up to {@link #STRENGTH_MAX}
     */
    public static double pitch(double closing, double strength) {
        if (!(Math.abs(closing) > DEADZONE) || !(strength > 0.0)) {
            return 1.0; // also NaN
        }
        // Grows from zero at the deadzone's edge instead of jumping
        double u = (Math.abs(closing) - DEADZONE) * Math.signum(closing) * strength;
        if (u >= SOUND_SPEED * (1.0 - 1.0 / MAX_PITCH)) {
            return MAX_PITCH;
        }
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, SOUND_SPEED / (SOUND_SPEED - u)));
    }

    /**
     * Closing speed from the velocities of the speaker and the listener (blocks per second) and
     * the direction from the listener to the speaker.
     */
    public static double closing(double dx, double dy, double dz,
                                 double speakerVx, double speakerVy, double speakerVz,
                                 double listenerVx, double listenerVy, double listenerVz) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(d > 0.01)) {
            return 0.0;
        }
        return ((listenerVx - speakerVx) * dx + (listenerVy - speakerVy) * dy + (listenerVz - speakerVz) * dz) / d;
    }

    /**
     * The smoothed closing speed of one speaker for one listener. Fed either with distances (the
     * client, every tick) or with closing speeds worked out from velocities (the server). Not
     * thread-safe: one thread feeds it; {@link #get} may be read from any thread.
     */
    public static final class Closing {
        private double lastDistance = Double.NaN;
        private long lastNanos;
        private boolean smoothing;
        private volatile double speed;

        /** A new distance, in blocks; a negative one (unknown position) forgets the movement. */
        public void distance(double distance, long nanos) {
            if (!(distance >= 0.0)) {
                reset();
                return;
            }
            if (Double.isNaN(lastDistance)) {
                lastDistance = distance;
                lastNanos = nanos;
                return;
            }
            double dt = (nanos - lastNanos) / 1.0e9;
            if (dt < 0.02) {
                return; // too soon to tell; the next tick measures over the longer time
            }
            double rate = (lastDistance - distance) / dt;
            lastDistance = distance;
            lastNanos = nanos;
            if (Math.abs(rate) > TELEPORT_SPEED || dt > 1.0) {
                speed = 0.0; // a jump, or a long pause: start again from here
                smoothing = false;
                return;
            }
            smooth(rate, dt);
        }

        /** A closing speed measured from velocities, in blocks per second. */
        public void speed(double closing, long nanos) {
            if (Double.isNaN(closing) || Math.abs(closing) > TELEPORT_SPEED) {
                reset();
                return;
            }
            double dt = smoothing ? (nanos - lastNanos) / 1.0e9 : 0.0;
            lastNanos = nanos;
            if (dt > 1.0) {
                speed = 0.0;
                smoothing = false;
            }
            smooth(closing, Math.max(0.0, dt));
        }

        private void smooth(double rate, double dt) {
            if (!smoothing) {
                // From standing still: half way at once, the rest glides
                speed = rate * 0.5;
                smoothing = true;
                return;
            }
            double k = 1.0 - Math.exp(-dt / SMOOTHING_SECONDS);
            speed += (rate - speed) * k;
        }

        /** Blocks per second the distance shrinks (negative while it grows). */
        public double get() {
            return speed;
        }

        public void reset() {
            lastDistance = Double.NaN;
            smoothing = false;
            speed = 0.0;
        }
    }
}
