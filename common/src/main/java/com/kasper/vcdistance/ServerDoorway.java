package com.kasper.vcdistance;

/**
 * Doorway sound for players without the addon (Paper): behind a wall, a voice that has a way round through an open
 * door or a gap reaches the listener from that opening, muffled only by the bends of the way, instead of through the
 * wall. Pure decisions over {@link SoundPath.Result}; {@link ServerWalls} sends the voice from the opening, and any
 * failure leaves the muffled straight path.
 */
public final class ServerDoorway {

    /** A wall thinner than this (in stone blocks) is not worth looking for a way round. */
    public static final double MIN_WALL = 0.5;
    /** The way round must muffle at most this share of the wall, or the voice stays on the straight path. */
    public static final double BETTER_THAN = 0.7;
    public static final int MAX_NODES = 1200;

    private ServerDoorway() {
    }

    /** Whether to look for a way round a wall of {@code thickness} stone blocks. */
    public static boolean worthLooking(double thickness) {
        return !Double.isNaN(thickness) && thickness >= MIN_WALL;
    }

    /** The longest way round worth finding for a voice {@code direct} blocks away within {@code range}. */
    public static double limit(double direct, double range) {
        return Math.min(Math.max(16.0, range), Math.min(48.0, direct * 2.0 + 12.0));
    }

    /**
     * @return {@code path} when it is a real way round that muffles clearly less than the wall, else {@code null}
     */
    public static SoundPath.Result choose(double thickness, SoundPath.Result path) {
        if (path == null || path.corners() == 0 || !worthLooking(thickness)) {
            return null;
        }
        return path.thickness() < thickness * BETTER_THAN ? path : null;
    }
}
