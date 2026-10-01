package com.kasper.vcdistance.bukkit;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Where the plugin's work runs. On Bukkit, Spigot and Paper everything runs on the main thread; on
 * Folia each region of the world ticks on its own thread, so a player's work runs on the thread that
 * owns the player and the rest on the global region.
 */
interface Scheduling {

    /** Whether each region ticks on its own thread (Folia). */
    boolean isRegionized();

    /** Runs {@code tick} every server tick: on the main thread, or on Folia's global region. */
    void everyTick(Runnable tick);

    /** Folia: runs {@code tick} every tick on the thread that owns {@code player}, until they leave. Elsewhere nothing. */
    void everyPlayerTick(Player player, Runnable tick);

    /** Runs {@code task} on the thread that owns {@code player}: now when that is this thread, otherwise soon. */
    void onPlayer(Player player, Runnable task);

    /** Whether this thread owns {@code player}: always on Bukkit, the player's region on Folia. */
    boolean ownsPlayer(Player player);

    /** Whether this thread owns the blocks around {@code location}. */
    boolean ownsLocation(Location location);

    void cancelAll();

    static Scheduling create(JavaPlugin plugin) {
        if (isFolia()) {
            // FoliaScheduling is compiled against a newer API (source set folia), so main code reaches it by name
            try {
                return (Scheduling) Class.forName("com.kasper.vcdistance.bukkit.FoliaScheduling")
                        .getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia scheduling is unavailable", e);
            }
        }
        return new BukkitScheduling(plugin);
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
