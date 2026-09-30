package com.kasper.vcdistance;

import java.util.concurrent.atomic.AtomicLong;

/**
 * How long the addon's own work takes per game tick (wall rays, ways round, the room's echo, the
 * server's wall filter), as a smoothed average in milliseconds. Work may be added from any thread;
 * {@link #endTick} is called by the thread that owns the tick.
 */
public final class PerfMeter {

    /** Above this many milliseconds per tick the client spaces its work out. */
    public static final double BUSY_MS = 2.0;
    /** Weight of the newest tick in the average (about the last two seconds count). */
    private static final double SMOOTHING = 0.05;
    /** Most the work is spread out: a quarter as much per tick. */
    public static final int MAX_SLOWDOWN = 4;
    /** Ticks between two steps of the slowdown, so each step has time to show in the average. */
    private static final int STEP_UP_TICKS = 20;
    private static final int STEP_DOWN_TICKS = 60;

    private final AtomicLong tickNanos = new AtomicLong();
    private volatile double averageMs;
    private volatile int slowdown = 1;
    private int sinceStep;

    /** Adds work done during the current tick. */
    public void add(long nanos) {
        if (nanos > 0) {
            tickNanos.addAndGet(nanos);
        }
    }

    /** Closes the current tick. */
    public void endTick() {
        averageMs += (tickNanos.getAndSet(0) / 1_000_000.0 - averageMs) * SMOOTHING;
        sinceStep++;
        if (averageMs > BUSY_MS && slowdown < MAX_SLOWDOWN && sinceStep >= STEP_UP_TICKS) {
            slowdown++;
            sinceStep = 0;
        } else if (averageMs < BUSY_MS * 0.4 && slowdown > 1 && sinceStep >= STEP_DOWN_TICKS) {
            slowdown--;
            sinceStep = 0;
        }
    }

    /**
     * How much the work is spread out right now, 1 (not at all) to {@link #MAX_SLOWDOWN}: goes up a step
     * a second while the average stays above {@link #BUSY_MS}, and back down slowly once it is well below.
     */
    public int slowdown() {
        return slowdown;
    }

    public double averageMs() {
        return averageMs;
    }

    public boolean isBusy() {
        return averageMs > BUSY_MS || slowdown > 1;
    }

    public void reset() {
        tickNanos.set(0);
        averageMs = 0.0;
        slowdown = 1;
        sinceStep = 0;
    }
}
