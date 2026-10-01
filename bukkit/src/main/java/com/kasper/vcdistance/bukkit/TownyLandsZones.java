package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.DistanceConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * The Towny town and the Lands land at a location, looked up by reflection so the plugin neither needs nor ships
 * them. Without the plugin (or on an API it does not know) the answer is {@code null} and that kind of zone simply
 * never matches. Both are looked up for zones written as {@code zone.town.<name>} and {@code zone.land.<name>}.
 */
final class TownyLandsZones {

    private static boolean townyInit;
    private static Object townyApi;
    private static Method townName;

    private static boolean landsInit;
    private static Object landsApi;
    private static Method landByChunk;
    private static Method landName;

    private TownyLandsZones() {
    }

    static boolean townyPresent() {
        return Bukkit.getPluginManager().getPlugin("Towny") != null;
    }

    static boolean landsPresent() {
        return Bukkit.getPluginManager().getPlugin("Lands") != null;
    }

    /** The name of the Towny town at {@code at}, or {@code null} in the wilderness or without Towny. */
    static String townAt(Location at) {
        if (!initTowny()) {
            return null;
        }
        try {
            Object name = townName.invoke(townyApi, at);
            return name instanceof String s && !com.kasper.vcdistance.Jv.isBlank(s) ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The name of the Lands land that claimed the chunk at {@code at}, or {@code null} when none or without Lands. */
    static String landAt(Location at) {
        if (!initLands()) {
            return null;
        }
        try {
            World world = at.getWorld();
            if (world == null) {
                return null;
            }
            Object land = landByChunk.invoke(landsApi, world, at.getBlockX() >> 4, at.getBlockZ() >> 4);
            if (land == null) {
                return null;
            }
            Object name = landName.invoke(land);
            return name instanceof String s && !com.kasper.vcdistance.Jv.isBlank(s) ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static synchronized boolean initTowny() {
        if (townyInit) {
            return townyApi != null;
        }
        townyInit = true;
        try {
            Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object instance = api.getMethod("getInstance").invoke(null);
            townName = api.getMethod("getTownName", Location.class);
            townyApi = instance;
            DistanceConfig.LOGGER.info("Towny found: zone.town.* settings are active while server_integrations is on");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            DistanceConfig.LOGGER.warn("Towny is installed but could not be read, town zones are off: {}", e.toString());
            return false;
        }
    }

    private static synchronized boolean initLands() {
        if (landsInit) {
            return landsApi != null;
        }
        landsInit = true;
        try {
            Plugin lands = Bukkit.getPluginManager().getPlugin("Lands");
            Plugin self = Bukkit.getPluginManager().getPlugin("VoicePhysics");
            if (lands == null || self == null) {
                return false;
            }
            Class<?> integration = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            Object api;
            try {
                api = integration.getMethod("of", Plugin.class).invoke(null, self);
            } catch (NoSuchMethodException e) {
                api = integration.getConstructor(Plugin.class).newInstance(self);
            }
            landByChunk = integration.getMethod("getLandByChunk", World.class, int.class, int.class);
            landName = Class.forName("me.angeschossen.lands.api.land.Land").getMethod("getName");
            landsApi = api;
            DistanceConfig.LOGGER.info("Lands found: zone.land.* settings are active while server_integrations is on");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            DistanceConfig.LOGGER.warn("Lands is installed but could not be read, land zones are off: {}", e.toString());
            return false;
        }
    }
}
