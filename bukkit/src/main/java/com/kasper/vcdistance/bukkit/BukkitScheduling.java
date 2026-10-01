package com.kasper.vcdistance.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit, Spigot, Paper and Purpur: one main thread for everything. */
final class BukkitScheduling implements Scheduling {

    private final JavaPlugin plugin;

    BukkitScheduling(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isRegionized() {
        return false;
    }

    @Override
    public void everyTick(Runnable tick) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, tick, 1L, 1L);
    }

    @Override
    public void everyPlayerTick(Player player, Runnable tick) {
        // The main tick covers every player
    }

    @Override
    public void onPlayer(Player player, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public boolean ownsPlayer(Player player) {
        return true;
    }

    @Override
    public boolean ownsLocation(Location location) {
        return true;
    }

    @Override
    public void cancelAll() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }
}
