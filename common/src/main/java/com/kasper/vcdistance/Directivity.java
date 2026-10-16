package com.kasper.vcdistance;

/**
 * Which way the speaker faces: a voice is loudest in front of the mouth, and behind the head it is
 * quieter and duller, the highs most of all.
 * <p>
 * Measurements of speech (Chu and Warnock 2002, Monson et al. 2012) give about the same level
 * within 40 degrees of the front, a decibel or so less to the side and about 6 dB less behind,
 * where 1 kHz drops by about 5 dB and 4 kHz by 10 dB or more. A broadband loss and a low-pass
 * come close to that: at the default strength, behind the speaker, 5 dB and a cutoff near 4.2 kHz
 * (about -5 dB at 1 kHz and -10 dB at 4 kHz; below 500 Hz a few decibels more than measured).
 * <p>
 * Only the sound that goes straight from the speaker to the listener has a direction. A voice heard
 * round a wall left the speaker towards the doorway, and the echo of a room comes from every side,
 * so {@link #effect} is scaled down by the share of both.
 */
public final class Directivity {

    public static final double STRENGTH_MIN = 0.0;
    public static final double STRENGTH_MAX = 1.5;
    /** Behind the speaker at strength 1. */
    static final double BACK_LOSS_DB = 5.0;
    /** Behind the speaker at strength 1: a cutoff near 4.2 kHz (see {@link OcclusionModel#cutoffHz}). */
    static final double BACK_MUFFLE = 0.35;
    /** Within this many degrees of the front the voice is as loud as in front. */
    static final double FRONT_DEGREES = 40.0;

    private Directivity() {
    }

    /**
     * How much of the effect behind the speaker applies (0 - 1) at this angle: none in front, a
     * smooth rise past {@link #FRONT_DEGREES}, all of it straight behind.
     *
     * @param cos cosine of the angle between where the speaker looks and the way to the listener
     */
    public static double shape(double cos) {
        if (!(cos >= -1.0 && cos <= 1.0)) {
            return Double.isNaN(cos) ? 0.0 : (cos > 1.0 ? 0.0 : 1.0);
        }
        double degrees = Math.toDegrees(Math.acos(cos));
        double t = (degrees - FRONT_DEGREES) / (180.0 - FRONT_DEGREES);
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Cosine of the angle between where the speaker looks and the way from the speaker to the
     * listener, or NaN when either is unknown or too short to have a direction.
     */
    public static double cos(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
        double look = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        double to = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (!(look > 1e-6) || !(to > 0.05)) {
            return Double.NaN; // also NaN
        }
        double c = (lookX * toX + lookY * toY + lookZ * toZ) / (look * to);
        return Math.max(-1.0, Math.min(1.0, c));
    }

    /**
     * What the speaker's facing does to the voice.
     *
     * @param cos      {@link #cos}, NaN when not known (then nothing changes)
     * @param strength 1 = as measured, up to {@link #STRENGTH_MAX}
     * @param indirect share of the voice that does not come straight from the speaker (round a
     *                 wall, or the room's echo), 0 - 1
     */
    public static EnvironmentEffects.Effect effect(double cos, double strength, double indirect) {
        if (!(strength > 0.0)) {
            return EnvironmentEffects.Effect.NONE;
        }
        double direct = 1.0 - Math.max(0.0, Math.min(1.0, Double.isNaN(indirect) ? 0.0 : indirect));
        double k = shape(cos) * Math.min(strength, STRENGTH_MAX) * direct;
        if (!(k > 0.0)) {
            return EnvironmentEffects.Effect.NONE;
        }
        return new EnvironmentEffects.Effect(Math.min(1.0, BACK_MUFFLE * k), BACK_LOSS_DB * k);
    }
}
