package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.EnvironmentEffects;
import com.kasper.vcdistance.RoomEstimate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * What a player stands in: the space around them (rays in every direction, the blocks they hit) for the
 * server's echo, whether their head is under water, and whether it rains on them. Run on the thread that
 * owns the player (the main thread, or their region on Folia).
 */
final class RoomProbe {

    private RoomProbe() {
    }

    /** The echo of the space around {@code player}'s head, or {@code null} when it cannot be measured now. */
    static RoomEstimate measure(Player player) {
        try {
            Location eye = player.getEyeLocation();
            World world = eye.getWorld();
            if (world == null) {
                return null;
            }
            Vector origin = eye.toVector();
            RoomEstimate.Hit[] hits = new RoomEstimate.Hit[RoomEstimate.DIRECTIONS.length];
            for (int i = 0; i < hits.length; i++) {
                double[] d = RoomEstimate.DIRECTIONS[i];
                RayTraceResult result = world.rayTraceBlocks(eye, new Vector(d[0], d[1], d[2]).normalize(), RoomEstimate.rayLength(i),
                        FluidCollisionMode.NEVER, true);
                Block block = result == null ? null : result.getHitBlock();
                if (block != null) {
                    hits[i] = new RoomEstimate.Hit(result.getHitPosition().distance(origin), BlockAcoustics.classify(block));
                }
            }
            return RoomEstimate.of(hits);
        } catch (Throwable t) {
            // A chunk another region owns (Folia), or a player half-way through leaving: measure again next time
            return null;
        }
    }

    /** Whether the player's head is in water (a water block, or a waterlogged one). */
    static boolean underwater(Player player) {
        try {
            Block block = player.getEyeLocation().getBlock();
            Material type = block.getType();
            return type == Material.WATER || type == Material.BUBBLE_COLUMN
                    || block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Rain or thunder on the player: a storm, in a biome where it rains, with nothing overhead. */
    @SuppressWarnings("deprecation")
    static EnvironmentEffects.Weather weather(Player player) {
        try {
            World world = player.getWorld();
            if (!world.hasStorm()) {
                return EnvironmentEffects.Weather.CLEAR;
            }
            Block feet = player.getLocation().getBlock();
            double temperature = feet.getTemperature();
            // Snow falls in the cold and deserts have no rain at all
            if (temperature < 0.15 || temperature >= 1.5 || feet.getLightFromSky() < 15) {
                return EnvironmentEffects.Weather.CLEAR;
            }
            return world.isThundering() ? EnvironmentEffects.Weather.THUNDER : EnvironmentEffects.Weather.RAIN;
        } catch (Throwable t) {
            return EnvironmentEffects.Weather.CLEAR;
        }
    }
}
