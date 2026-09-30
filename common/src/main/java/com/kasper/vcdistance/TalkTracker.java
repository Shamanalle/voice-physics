package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Who has spoken in the last moment, from the microphone packets Simple Voice Chat delivers. Players
 * without the addon have no monitor, so this feeds the line above their hotbar. Written from Simple
 * Voice Chat's thread, read from the server thread.
 */
public final class TalkTracker {

    /** A player counts as talking this long after their last voice packet. */
    public static final long HOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(600);

    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public void spoke(UUID player, long nowNanos) {
        if (player != null) {
            last.put(player, nowNanos);
        }
    }

    public boolean isTalking(UUID player, long nowNanos) {
        Long at = player == null ? null : last.get(player);
        return at != null && nowNanos - at <= HOLD_NANOS;
    }

    public void forget(UUID player) {
        last.remove(player);
    }

    public void clear() {
        last.clear();
    }
}
