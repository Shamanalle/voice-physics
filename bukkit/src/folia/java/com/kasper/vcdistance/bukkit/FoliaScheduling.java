package com.kasper.vcdistance.bukkit;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Folia: the global region for the plugin's own bookkeeping, each player's scheduler for what
 * touches that player or the blocks around them. Loaded only on Folia.
 */
final class FoliaScheduling implements Scheduling {

    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTask> playerTasks = new ConcurrentHashMap<>();

    FoliaScheduling(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isRegionized() {
        return true;
    }

    @Override
    public void everyTick(Runnable tick) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick.run(), 1L, 1L);
    }

    @Override
    public void everyPlayerTick(Player player, Runnable tick) {
        UUID id = player.getUniqueId();
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> tick.run(), () -> playerTasks.remove(id), 1L, 1L);
        if (task != null) {
            ScheduledTask old = playerTasks.put(id, task);
            if (old != null) {
                old.cancel();
            }
        }
    }

    @Override
    public void onPlayer(Player player, Runnable task) {
        if (ownsPlayer(player)) {
            task.run();
        } else {
            player.getScheduler().run(plugin, t -> task.run(), null);
        }
    }

    @Override
    public boolean ownsPlayer(Player player) {
        return Bukkit.isOwnedByCurrentRegion(player);
    }

    @Override
    public boolean ownsLocation(Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        for (ScheduledTask task : playerTasks.values()) {
            task.cancel();
        }
        playerTasks.clear();
    }
}
