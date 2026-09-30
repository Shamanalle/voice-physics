package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.PlayerPrefs;
import com.kasper.vcdistance.ServerPlayers;
import com.kasper.vcdistance.Zone;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * LuckPerms contexts for a player's voice: {@code vcd:mode} (quiet, normal or shout), {@code vcd:walls} (on or off)
 * and {@code vcd:zone} (the sound zone they are in, when there is one). Only given while the server file's
 * {@code server_integrations} is on. Loaded only when LuckPerms is installed.
 */
final class LuckPermsContexts implements ContextCalculator<Player> {

    static final String MODE = "vcd:mode";
    static final String WALLS = "vcd:walls";
    static final String ZONE = "vcd:zone";

    private LuckPermsContexts() {
    }

    /** Registers the contexts when LuckPerms is there; says in the log either way. */
    static void registerIfPresent(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            luckPerms.getContextManager().registerCalculator(new LuckPermsContexts());
            plugin.getLogger().info("LuckPerms found: the contexts " + MODE + ", " + WALLS + " and " + ZONE
                    + " are given while server_integrations is on");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register the LuckPerms contexts: " + t);
        }
    }

    @Override
    public void calculate(Player player, ContextConsumer consumer) {
        if (!AudioDistancePlugin.SERVER_SETTINGS.isServerIntegrations()) {
            return;
        }
        PlayerPrefs.Prefs prefs = AudioDistancePlugin.PLAYER_PREFS.get(player.getUniqueId());
        consumer.accept(MODE, prefs.mode().name().toLowerCase(Locale.ROOT));
        consumer.accept(WALLS, prefs.walls() ? "on" : "off");
        ServerPlayers.Info info = AudioDistancePlugin.PLAYERS.get(player.getUniqueId());
        Zone zone = info == null ? null : AudioDistancePlugin.SERVER_SETTINGS.zoneOf(info);
        if (zone != null) {
            consumer.accept(ZONE, zone.key());
        }
    }

    @Override
    public ContextSet estimatePotentialContexts() {
        ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
        for (PlayerPrefs.Mode mode : PlayerPrefs.Mode.values()) {
            builder.add(MODE, mode.name().toLowerCase(Locale.ROOT));
        }
        builder.add(WALLS, "on").add(WALLS, "off");
        for (Zone zone : AudioDistancePlugin.SERVER_SETTINGS.zones().values()) {
            builder.add(ZONE, zone.key());
        }
        return builder.build();
    }
}
