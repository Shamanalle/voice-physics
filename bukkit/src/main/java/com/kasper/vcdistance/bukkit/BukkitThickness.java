package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.ServerDoorway;
import com.kasper.vcdistance.ServerWalls;
import com.kasper.vcdistance.SoundPath;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Measures walls between a listener and a speaker on Bukkit servers, with the same five-ray bundle
 * and material weights as the Fabric builds. Runs on the main thread; on Folia on the listener's
 * region thread, and only when the speaker is in the same region (otherwise the voice stays clear).
 */
final class BukkitThickness implements ServerWalls.ThicknessProvider {

    /** Beyond this nobody hears proximity voice anyway; do not trace across the map. */
    private static final double MAX_TRACE_DISTANCE = 160.0;

    private final boolean regionized;

    BukkitThickness(boolean regionized) {
        this.regionized = regionized;
    }

    @Override
    public double thickness(Object listener, Object levelObject, UUID speakerEntity, double x, double y, double z) {
        double[] ends = ends(listener, levelObject, speakerEntity, x, y, z);
        if (ends == null) {
            return Double.NaN;
        }
        World world = (World) levelObject;
        return RayBundle.trace(
                (fx, fy, fz, tx, ty, tz) -> BlockAcoustics.traceRay(world, fx, fy, fz, tx, ty, tz,
                        AudioDistancePlugin.SERVER_SETTINGS.profile()),
                (bx, by, bz) -> BlockAcoustics.isOpenForSound(world, bx, by, bz),
                ends[0], ends[1], ends[2], ends[3], ends[4], ends[5]);
    }

    @Override
    public SoundPath.Result doorway(Object listener, Object levelObject, UUID speakerEntity, double x, double y, double z,
                                    double range) {
        double[] ends = ends(listener, levelObject, speakerEntity, x, y, z);
        if (ends == null) {
            return null;
        }
        World world = (World) levelObject;
        double direct = Math.sqrt(sq(ends[3] - ends[0]) + sq(ends[4] - ends[1]) + sq(ends[5] - ends[2]));
        return SoundPath.find((bx, by, bz) -> BlockAcoustics.isOpenForSound(world, bx, by, bz),
                ends[0], ends[1], ends[2], ends[3], ends[4], ends[5], ServerDoorway.limit(direct, range), ServerDoorway.MAX_NODES);
    }

    private static double sq(double v) {
        return v * v;
    }

    /**
     * The listener's ear and the speaker's mouth as {ex, ey, ez, sx, sy, sz}, or {@code null} when they cannot be
     * measured here (another world, another region on Folia, too far).
     */
    private double[] ends(Object listener, Object levelObject, UUID speakerEntity, double x, double y, double z) {
        // Simple Voice Chat on Bukkit hands out Bukkit players and worlds
        if (!(listener instanceof Player player) || !(levelObject instanceof World world)) {
            return null;
        }
        if (regionized && !Bukkit.isOwnedByCurrentRegion(player)) {
            return null;
        }
        Location ear = player.getEyeLocation();
        if (!world.equals(ear.getWorld())) {
            return null;
        }
        double sx = x;
        double sy = y;
        double sz = z;
        if (speakerEntity != null) {
            Entity speaker = Bukkit.getEntity(speakerEntity);
            if (speaker == null || !world.equals(speaker.getWorld())) {
                return null;
            }
            Location mouth = speaker instanceof LivingEntity living ? living.getEyeLocation() : speaker.getLocation();
            sx = mouth.getX();
            sy = mouth.getY();
            sz = mouth.getZ();
        }
        if (regionized && !Bukkit.isOwnedByCurrentRegion(new Location(world, sx, sy, sz))) {
            return null;
        }
        double dx = sx - ear.getX();
        double dy = sy - ear.getY();
        double dz = sz - ear.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_TRACE_DISTANCE * MAX_TRACE_DISTANCE) {
            return null;
        }
        return new double[]{ear.getX(), ear.getY(), ear.getZ(), sx, sy, sz};
    }
}
