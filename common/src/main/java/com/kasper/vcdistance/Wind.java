package com.kasper.vcdistance;

/**
 * Wind: high up under the open sky, and in fast flight, far voices are blown away.
 * <p>
 * Two things make it. On a mountain or a tall tower the air moves more than down in a valley, and
 * more still as a storm comes; and a player who flies on elytra, falls or races a boat over ice has
 * the air rush past their ears. Wind noise covers the quiet parts of speech, so a far voice drowns in
 * it more than a near one, and gusts make it come and go. The game has no wind direction, so none is
 * made up: downwind and upwind sound the same.
 * <p>
 * The result is a loss in dB like the rain's ({@link EnvironmentEffects#weather}), never a muffle:
 * wind noise covers the lows of a voice as much as its highs.
 */
public final class Wind {

    /** The wind starts above this height and is at its strongest at {@link #HIGH_Y} (mountain peaks reach 200 - 250). */
    static final double LOW_Y = 90.0;
    static final double HIGH_Y = 180.0;
    /** Rain brings more wind, a thunderstorm the most. */
    static final double RAIN_FACTOR = 1.25;
    static final double THUNDER_FACTOR = 1.6;
    /**
     * Air rushing past, blocks per second: from a galloping horse (barely) up to elytra with a firework
     * (all of it). Elytra glide at about 30, a fall reaches 78.
     */
    static final double AIRFLOW_FROM = 12.0;
    static final double AIRFLOW_FULL = 36.0;
    /** The speaker's own flight counts half: the air rushes past their mouth, not the listener's ears. */
    static final double SPEAKER_AIRFLOW_SHARE = 0.5;
    /** Loss in a full wind at the edge of the range; a voice right next to you loses {@link #NEAR_SHARE} of it. */
    static final double LOSS_DB = 6.0;
    static final double NEAR_SHARE = 0.3;
    public static final double MAX_LOSS_DB = 12.0;
    /** Gusts swing the loss of the wind up there this far either way, over a few seconds. */
    static final double GUST_DEPTH = 0.35;

    private Wind() {
    }

    /** Worlds with weather and open air: not the Nether or the End. */
    public static boolean windyWorld(String dimension) {
        if (dimension == null) {
            return true;
        }
        String d = dimension.toLowerCase(java.util.Locale.ROOT);
        return !d.contains("the_nether") && !d.contains("the_end");
    }

    /**
     * How windy the place is, 0 calm to {@link #THUNDER_FACTOR} (a thunderstorm on a peak).
     *
     * @param sky        the open sky is over the head, in a world with weather ({@link #windyWorld})
     * @param underWater the head is under water: no wind there
     * @param y          height of the head
     */
    public static double place(boolean sky, boolean underWater, double y, EnvironmentEffects.Weather weather) {
        if (!sky || underWater || Double.isNaN(y)) {
            return 0.0;
        }
        double height = smooth((y - LOW_Y) / (HIGH_Y - LOW_Y));
        if (weather == EnvironmentEffects.Weather.THUNDER) {
            return height * THUNDER_FACTOR;
        }
        return weather == EnvironmentEffects.Weather.RAIN ? height * RAIN_FACTOR : height;
    }

    /** {@link #place} at a place the game describes. */
    public static double place(PlaceTuning.Place where, boolean underWater, EnvironmentEffects.Weather weather) {
        if (where == null || !windyWorld(where.dimension())) {
            return 0.0;
        }
        return place(where.sky(), underWater, where.y(), weather);
    }

    /** Air rushing past someone moving this fast (blocks per second), 0 - 1; none under water. */
    public static double airflow(double speed, boolean underWater) {
        if (underWater || !(speed > 0.0)) {
            return 0.0;
        }
        return smooth((speed - AIRFLOW_FROM) / (AIRFLOW_FULL - AIRFLOW_FROM));
    }

    /**
     * What the wind takes off a voice, in dB.
     *
     * @param placeListener {@link #place} at the listener
     * @param placeSpeaker  {@link #place} at the speaker: the voice crosses the wind at either end
     * @param flowListener  {@link #airflow} at the listener
     * @param flowSpeaker   {@link #airflow} at the speaker
     * @param distFraction  distance as a share of the voice range
     * @param strength      0 - 1.5, the player's setting (1 = the usual)
     * @param gust          {@link #gust} now: 1 on average
     */
    public static double lossDb(double placeListener, double placeSpeaker, double flowListener, double flowSpeaker,
                                double distFraction, double strength, double gust) {
        if (!(strength > 0.0)) {
            return 0.0;
        }
        double place = Math.max(finite(placeListener), finite(placeSpeaker)) * (Double.isNaN(gust) ? 1.0 : gust);
        double flow = Math.max(finite(flowListener), SPEAKER_AIRFLOW_SHARE * finite(flowSpeaker));
        double wind = Math.max(place, flow);
        if (!(wind > 0.0)) {
            return 0.0;
        }
        double f = Double.isNaN(distFraction) ? 0.0 : Math.max(0.0, Math.min(1.0, distFraction));
        return Math.min(MAX_LOSS_DB, LOSS_DB * strength * wind * (NEAR_SHARE + (1.0 - NEAR_SHARE) * f));
    }

    /**
     * The gusts: about 1 on average, from {@code 1 - GUST_DEPTH} to {@code 1 + GUST_DEPTH}, changing
     * smoothly over a few seconds. {@code seed} gives each voice its own gusts, as the wind blows
     * differently where each speaker stands.
     */
    public static double gust(long nanos, long seed) {
        double t = nanos / 1.0e9;
        // Hashed to a phase in turns, so neighbouring seeds get unrelated gusts
        long h = seed * 0x9E3779B97F4A7C15L;
        double p1 = ((h >>> 11) & 0xFFFF) / 65536.0;
        double p2 = ((h >>> 27) & 0xFFFF) / 65536.0;
        double p3 = ((h >>> 43) & 0xFFFF) / 65536.0;
        double n = 0.5 * Math.sin(2.0 * Math.PI * (0.11 * t + p1))
                + 0.3 * Math.sin(2.0 * Math.PI * (0.23 * t + p2))
                + 0.2 * Math.sin(2.0 * Math.PI * (0.47 * t + p3));
        return 1.0 + GUST_DEPTH * n;
    }

    /**
     * A player's speed through the air, smoothed over about half a second so a jump or a short
     * drop does not count as flight. Fed positions from one thread, read from others.
     */
    public static final class Speed {
        static final double SMOOTHING_SECONDS = 0.5;

        private double lastX = Double.NaN;
        private double lastY;
        private double lastZ;
        private long lastNanos;
        private boolean measured;
        private volatile double speed;

        public synchronized void position(double x, double y, double z, long nanos) {
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                reset();
                return;
            }
            if (Double.isNaN(lastX)) {
                set(x, y, z, nanos);
                return;
            }
            double dt = (nanos - lastNanos) / 1.0e9;
            if (dt < 0.02) {
                return; // too soon to tell; the next tick measures over the longer time
            }
            double dx = x - lastX;
            double dy = y - lastY;
            double dz = z - lastZ;
            double v = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
            set(x, y, z, nanos);
            if (v > Doppler.TELEPORT_SPEED || dt > 1.0) {
                speed = 0.0; // a teleport, or a long pause: start again from here
                measured = false;
                return;
            }
            if (!measured) {
                // The first measurement after a pause is taken as it is: someone who starts talking in flight is flying
                measured = true;
                speed = v;
                return;
            }
            speed += (v - speed) * (1.0 - Math.exp(-dt / SMOOTHING_SECONDS));
        }

        private void set(double x, double y, double z, long nanos) {
            lastX = x;
            lastY = y;
            lastZ = z;
            lastNanos = nanos;
        }

        /** Blocks per second. */
        public double get() {
            return speed;
        }

        public synchronized void reset() {
            lastX = Double.NaN;
            measured = false;
            speed = 0.0;
        }
    }

    private static double finite(double v) {
        return Double.isNaN(v) ? 0.0 : v;
    }

    private static double smooth(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }
}
