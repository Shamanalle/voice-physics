package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The shared block cache for the searches round walls and the load-based slowdown. */
public class PerformanceTest {

    @Test
    @DisplayName("Grid cache: the same answers as the world, each block read once per tick, forgotten next tick")
    void gridCache() {
        Random random = new Random(11);
        Map<String, Boolean> world = new HashMap<>();
        int[] worldReads = {0};
        SoundPath.Grid source = (x, y, z) -> {
            worldReads[0]++;
            return world.computeIfAbsent(x + "," + y + "," + z, k -> random.nextBoolean());
        };
        GridCache cache = new GridCache();
        cache.newTick(source);
        int[][] points = new int[20_000][];
        for (int i = 0; i < points.length; i++) {
            points[i] = new int[]{random.nextInt(200) - 100, random.nextInt(128) - 64, random.nextInt(200) - 100};
        }
        for (int[] p : points) {
            assertEquals(world.getOrDefault(p[0] + "," + p[1] + "," + p[2], source.open(p[0], p[1], p[2])), cache.open(p[0], p[1], p[2]));
        }
        worldReads[0] = 0;
        for (int[] p : points) {
            assertEquals(world.get(p[0] + "," + p[1] + "," + p[2]), cache.open(p[0], p[1], p[2]), "kept after growing");
        }
        assertEquals(0, worldReads[0], "a second read of the same block does not touch the world");
        assertTrue(cache.size() > 4096, "grew past its first size");

        cache.newTick(source);
        assertEquals(0, cache.size());
        cache.open(points[0][0], points[0][1], points[0][2]);
        assertEquals(1, worldReads[0], "a new tick reads again (a door may have opened)");
        assertEquals(cache.open(-30_000_000, 300, 29_999_999), world.get("-30000000,300,29999999"), "far coordinates");
    }

    @Test
    @DisplayName("Slowdown: a step a second while busy up to 4x, back down slowly when it is quiet again")
    void slowdown() {
        PerfMeter m = new PerfMeter();
        assertEquals(1, m.slowdown());
        for (int i = 0; i < 40; i++) {
            m.add(1_000_000);
            m.endTick();
        }
        assertEquals(1, m.slowdown(), "1 ms per tick is fine");
        int ticks = 0;
        while (m.slowdown() < PerfMeter.MAX_SLOWDOWN && ticks < 1000) {
            m.add(6_000_000);
            m.endTick();
            ticks++;
        }
        assertEquals(PerfMeter.MAX_SLOWDOWN, m.slowdown());
        assertTrue(ticks < 150, "reached the most within a few seconds: " + ticks);
        for (int i = 0; i < 300; i++) {
            m.add(6_000_000);
            m.endTick();
        }
        assertEquals(PerfMeter.MAX_SLOWDOWN, m.slowdown(), "never more than the most");
        for (int i = 0; i < 40; i++) {
            m.add(1_500_000);
            m.endTick();
        }
        assertEquals(PerfMeter.MAX_SLOWDOWN, m.slowdown(), "just under busy is not quiet enough to speed up");
        ticks = 0;
        while (m.slowdown() > 1 && ticks < 2000) {
            m.endTick();
            ticks++;
        }
        assertEquals(1, m.slowdown());
        assertTrue(ticks > 100, "back down slowly, not at once: " + ticks);
        m.reset();
        assertEquals(1, m.slowdown());
    }
}
