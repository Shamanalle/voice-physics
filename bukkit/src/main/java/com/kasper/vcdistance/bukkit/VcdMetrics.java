package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerSettings;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Anonymous usage numbers for bStats (https://bstats.org): which versions and options are in use, so the
 * ones worth keeping can be told from the ones nobody runs. No names, addresses or chat. Off with
 * {@code metrics=false} in the server settings file, or for every plugin with plugins/bStats/config.yml.
 */
final class VcdMetrics {

    /**
     * The plugin's id on bstats.org (Plugins - Add plugin). While it is 0 nothing is sent.
     */
    static final int SERVICE_ID = 0;

    private VcdMetrics() {
    }

    /** Starts the reporting when it is wanted and this build has an id; never throws. */
    static void start(JavaPlugin plugin) {
        if (SERVICE_ID <= 0) {
            return;
        }
        try {
            ServerSettings settings = AudioDistancePlugin.SERVER_SETTINGS;
            if (!settings.isMetrics()) {
                plugin.getLogger().info("bStats is off (metrics=false)");
                return;
            }
            Metrics metrics = new Metrics(plugin, SERVICE_ID);
            metrics.addCustomChart(new SimplePie("profile_mode", () -> AudioDistancePlugin.SERVER_SETTINGS.getProfileMode().getId()));
            metrics.addCustomChart(new SimplePie("server_walls", () -> onOff(AudioDistancePlugin.SERVER_SETTINGS.isServerWalls())));
            metrics.addCustomChart(new SimplePie("server_realism", () -> onOff(AudioDistancePlugin.SERVER_SETTINGS.hasServerRealism())));
            metrics.addCustomChart(new SimplePie("zones", () -> AudioDistancePlugin.SERVER_SETTINGS.zones().isEmpty() ? "none" : "some"));
        } catch (Throwable t) {
            plugin.getLogger().warning("bStats could not start: " + t);
        }
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }
}
