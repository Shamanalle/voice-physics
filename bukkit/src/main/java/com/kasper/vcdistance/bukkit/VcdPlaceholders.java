package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerPlaceholders;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * {@code %vcd_<name>%} for PlaceholderAPI (see {@link ServerPlaceholders#NAMES}). Loaded only when
 * PlaceholderAPI is installed, so the plugin runs the same without it.
 */
final class VcdPlaceholders extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    private VcdPlaceholders(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the placeholders when PlaceholderAPI is there; says in the log either way. */
    static void registerIfPresent(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            if (new VcdPlaceholders(plugin).register()) {
                plugin.getLogger().info("PlaceholderAPI found: %vcd_...% placeholders are available ("
                        + String.join(", ", ServerPlaceholders.NAMES) + ")");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register the PlaceholderAPI placeholders: " + t);
        }
    }

    @Override
    public String getIdentifier() {
        return "vcd";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Kept across /papi reload: the placeholders belong to this plugin, not to a downloaded expansion. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public List<String> getPlaceholders() {
        return ServerPlaceholders.NAMES.stream().map(n -> "%vcd_" + n + "%").collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        UUID id = player == null ? null : player.getUniqueId();
        Player online = player == null ? null : player.getPlayer();
        return ServerPlaceholders.value(params, id, AudioDistancePlugin.SERVER_SETTINGS, System.nanoTime(),
                System.currentTimeMillis(), other -> {
                    Player o = Bukkit.getPlayer(other);
                    return online == null || o == null || online.canSee(o);
                });
    }
}
