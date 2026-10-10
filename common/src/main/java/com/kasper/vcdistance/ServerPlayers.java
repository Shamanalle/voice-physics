package com.kasper.vcdistance;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the server's voice rules need to know about each online player, refreshed by the platform on
 * the server thread every few ticks and read from Simple Voice Chat's thread when voices are sent.
 */
public final class ServerPlayers {

    /**
     * One player at the last refresh.
     *
     * @param world     world name (Paper) or dimension id (Fabric, NeoForge)
     * @param regions   WorldGuard regions at the player's position, highest priority first (Paper only)
     * @param mainHand  item id in the main hand ("minecraft:goat_horn"), or "" when empty
     * @param language  the player's client language ("ru_ru"), or "" when unknown
     * @param underwater the player's head is under water; {@code false} where it is not known
     * @param weather   rain or thunder where the player stands under the open sky
     */
    @com.github.bsideup.jabel.Desugar
    public record Info(UUID id, String name, String world, double x, double y, double z,
                       boolean sneaking, boolean alive, boolean spectator,
                       String mainHand, String offHand, List<String> regions, String language,
                       boolean underwater, EnvironmentEffects.Weather weather) {

        public Info {
            weather = weather == null ? EnvironmentEffects.Weather.CLEAR : weather;
        }

        /** A player whose surroundings (water, weather) are not known. */
        public Info(UUID id, String name, String world, double x, double y, double z,
                    boolean sneaking, boolean alive, boolean spectator,
                    String mainHand, String offHand, List<String> regions, String language) {
            this(id, name, world, x, y, z, sneaking, alive, spectator, mainHand, offHand, regions, language,
                    false, EnvironmentEffects.Weather.CLEAR);
        }

        public double distanceTo(Info other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public boolean holds(String itemId) {
            return itemId != null && !itemId.isEmpty() && (itemId.equals(mainHand) || itemId.equals(offHand));
        }
    }

    private final Map<UUID, Info> players = new ConcurrentHashMap<>();
    /** Each player's velocity in blocks per second, from the last two refreshes: {vx, vy, vz}. */
    private final Map<UUID, double[]> velocities = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshedAt = new ConcurrentHashMap<>();

    public void update(Info info) {
        update(info, System.nanoTime());
    }

    void update(Info info, long nanos) {
        Info old = players.put(info.id(), info);
        if (old == info) {
            return; // the same snapshot again (Folia keeps entries until the player's thread refreshes them)
        }
        Long then = refreshedAt.put(info.id(), nanos);
        double[] v = {0.0, 0.0, 0.0};
        if (old != null && then != null && java.util.Objects.equals(old.world(), info.world())) {
            double dt = (nanos - then) / 1.0e9;
            if (dt > 0.01 && dt < 2.0) {
                v[0] = (info.x() - old.x()) / dt;
                v[1] = (info.y() - old.y()) / dt;
                v[2] = (info.z() - old.z()) / dt;
                if (v[0] * v[0] + v[1] * v[1] + v[2] * v[2] > Doppler.TELEPORT_SPEED * Doppler.TELEPORT_SPEED) {
                    v = new double[]{0.0, 0.0, 0.0}; // a teleport
                }
            }
        }
        velocities.put(info.id(), v);
    }

    /** The player's velocity in blocks per second {vx, vy, vz}, or {@code null} when not known. */
    public double[] velocity(UUID id) {
        return id == null ? null : velocities.get(id);
    }

    public void remove(UUID id) {
        players.remove(id);
        velocities.remove(id);
        refreshedAt.remove(id);
    }

    public void clear() {
        players.clear();
        velocities.clear();
        refreshedAt.clear();
    }

    /** @return the player, or {@code null} when not online (or not refreshed yet) */
    public Info get(UUID id) {
        return id == null ? null : players.get(id);
    }

    public Collection<Info> all() {
        return players.values();
    }

    /** A player by name, ignoring case. */
    public Info byName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (Info i : players.values()) {
            if (i.name() != null && i.name().toLowerCase(Locale.ROOT).equals(n)) {
                return i;
            }
        }
        return null;
    }
}
