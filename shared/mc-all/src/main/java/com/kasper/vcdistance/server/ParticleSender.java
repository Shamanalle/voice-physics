package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Mc;
import com.kasper.vcdistance.DistanceConfig;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Particles that only one player sees, for zone borders.
 * <p>
 * {@code ServerLevel.sendParticles(ServerPlayer, ...)} gained a second flag in 1.21.4, and one jar
 * serves 1.21 - 1.21.11 under obfuscated names, so the method is found by its parameter types:
 * (player, particle, boolean, [boolean,] x, y, z, count, dx, dy, dz, speed). When neither shape is
 * there the borders are simply not drawn.
 */
final class ParticleSender {

    private static volatile Method method;
    private static volatile boolean twoFlags;
    private static volatile boolean failed;

    private ParticleSender() {
    }

    /** End rod particles at {@code points} for {@code player}. */
    static void endRods(ServerPlayer player, List<double[]> points) {
        if (failed) {
            return;
        }
        try {
            Method m = method();
            if (m == null) {
                failed = true;
                DistanceConfig.LOGGER.info("Zone borders cannot be drawn in this Minecraft version");
                return;
            }
            ServerLevel level = (ServerLevel) Mc.level(player);
            for (double[] p : points) {
                if (twoFlags) {
                    m.invoke(level, player, ParticleTypes.END_ROD, true, false, p[0], p[1], p[2], 1, 0.0, 0.0, 0.0, 0.0);
                } else {
                    m.invoke(level, player, ParticleTypes.END_ROD, true, p[0], p[1], p[2], 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        } catch (Throwable t) {
            failed = true;
            DistanceConfig.LOGGER.info("Zone borders cannot be drawn: {}", t.toString());
        }
    }

    private static Method method() {
        Method m = method;
        if (m != null) {
            return m;
        }
        for (Method candidate : ServerLevel.class.getMethods()) {
            Class<?>[] t = candidate.getParameterTypes();
            if (t.length < 11 || t[0] != ServerPlayer.class || !ParticleOptions.class.isAssignableFrom(t[1])
                    || t[2] != boolean.class) {
                continue;
            }
            int i = t[3] == boolean.class ? 4 : 3;
            if (t.length == i + 8 && t[i] == double.class && t[i + 1] == double.class && t[i + 2] == double.class
                    && t[i + 3] == int.class && t[i + 4] == double.class && t[i + 5] == double.class
                    && t[i + 6] == double.class && t[i + 7] == double.class) {
                twoFlags = i == 4;
                method = candidate;
                return candidate;
            }
        }
        return null;
    }
}
