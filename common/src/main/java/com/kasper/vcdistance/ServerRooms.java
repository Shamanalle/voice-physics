package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The echo of the place each player stands in, for the server's own echo ({@code server_effects}):
 * measured by the platform every second or so on the thread that owns the player, glided towards so
 * walking into a cave changes the echo smoothly, and read from Simple Voice Chat's thread for every
 * voice. A zone that sets its own {@code echo} decides instead of the measurement.
 */
public final class ServerRooms {

    private final Map<UUID, RoomGlide> glides = new ConcurrentHashMap<>();

    /** Whether the platform needs to measure {@code zone}'s players (a zone with a set echo needs no rays). */
    public static boolean needsMeasuring(Zone zone) {
        return zone == null || zone.rules().echo() == null;
    }

    /**
     * A new measurement for {@code player}, or, in a zone with its own echo, that echo (no rays needed).
     *
     * @param measured what the rays found; ignored where the zone sets the echo
     */
    public void update(UUID player, RoomEstimate measured, Zone zone, long nowNanos) {
        RoomEstimate room = measured;
        if (zone != null && zone.rules().echo() != null) {
            double size = zone.rules().echo();
            room = size <= 0.0 ? RoomEstimate.OPEN : RoomEstimate.forced(size);
        }
        glides.computeIfAbsent(player, id -> new RoomGlide(false)).set(room == null ? RoomEstimate.OPEN : room, nowNanos);
    }

    /** The echo where {@code player} stands right now; open air when nothing was measured yet. */
    public RoomEstimate of(UUID player, long nowNanos) {
        RoomGlide g = player == null ? null : glides.get(player);
        return g == null ? RoomEstimate.OPEN : g.get(nowNanos);
    }

    public void forget(UUID player) {
        glides.remove(player);
    }

    public void clear() {
        glides.clear();
    }
}
