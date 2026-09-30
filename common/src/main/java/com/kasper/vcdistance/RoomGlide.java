package com.kasper.vcdistance;

/**
 * The echo of a place, gliding with time towards the latest measurement, so walking from a field
 * into a cave or out of a hall changes the echo smoothly (about {@link #SETTLE_SECONDS}) instead of
 * in steps each time the room is measured. Written by the client tick, read by the audio threads.
 */
public final class RoomGlide {

    /** Time constant of the glide: 63% of the way after this, 95% after three times this. */
    public static final double TAU_SECONDS = 0.5;
    /** About how long the echo takes to settle in a new place. */
    public static final double SETTLE_SECONDS = 3 * TAU_SECONDS;
    /** Reads closer together than this reuse the last result. */
    private static final long STEP_NANOS = 10_000_000L;

    private RoomEstimate current;
    private RoomEstimate target;
    private long lastNanos;
    private final boolean optional;

    /**
     * @param optional {@code true}: "no room of its own" ({@code null}) is a valid value, reached by
     *                 gliding to {@link RoomEstimate#OPEN} (a speaker in the listener's own space)
     */
    public RoomGlide(boolean optional) {
        this.optional = optional;
        this.current = optional ? null : RoomEstimate.OPEN;
        this.target = current;
    }

    /** A new measurement to glide towards. */
    public synchronized void set(RoomEstimate room, long nowNanos) {
        advance(nowNanos);
        if (current == target) {
            // Settled: the glide starts now, not at the last step, which may be long ago
            lastNanos = nowNanos;
        }
        target = room == null && !optional ? RoomEstimate.OPEN : room;
        if (current == null && target != null) {
            current = RoomEstimate.OPEN;
            lastNanos = nowNanos;
        }
    }

    /** The echo right now. */
    public synchronized RoomEstimate get(long nowNanos) {
        advance(nowNanos);
        return current;
    }

    /** Jumps straight to {@code room}, for a new world or a reset. */
    public synchronized void jump(RoomEstimate room, long nowNanos) {
        current = room == null && !optional ? RoomEstimate.OPEN : room;
        target = current;
        lastNanos = nowNanos;
    }

    private void advance(long nowNanos) {
        if (current == null || current == target || nowNanos - lastNanos < STEP_NANOS) {
            return;
        }
        double dt = (nowNanos - lastNanos) / 1e9;
        lastNanos = nowNanos;
        RoomEstimate goal = target == null ? RoomEstimate.OPEN : target;
        double amount = 1.0 - Math.exp(-dt / TAU_SECONDS);
        RoomEstimate next = current.towards(goal, amount);
        if (next.closeTo(goal)) {
            // Arrived: back to the exact value (null for a speaker who shares the listener's space)
            current = target;
        } else {
            current = next;
        }
    }
}
