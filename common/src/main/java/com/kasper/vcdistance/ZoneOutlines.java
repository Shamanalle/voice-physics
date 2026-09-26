package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Zone borders drawn with particles for the admin who asked ({@code /vcd zone show}). Each box's 12
 * edges get a particle about every block, refreshed a few times a second for 30 seconds. The platform
 * calls {@link #tick} every server tick and draws the points it is handed.
 */
public final class ZoneOutlines {

    /** Draws particles at {@code points} ({x, y, z} each) that only {@code player} sees. */
    public interface Drawer {
        void draw(UUID player, String world, List<double[]> points);
    }

    static final long SHOW_NANOS = TimeUnit.SECONDS.toNanos(30);
    static final int REFRESH_TICKS = 10;
    /** Larger boxes get sparser edges, so a refresh never sends more than this. */
    static final int MAX_POINTS = 600;

    private record Shown(Zone.Box box, long untilNanos) {
    }

    private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();
    private static int ticks;

    private ZoneOutlines() {
    }

    public static void show(UUID player, Zone.Box box) {
        SHOWN.put(player, new Shown(box, System.nanoTime() + SHOW_NANOS));
    }

    public static void hide(UUID player) {
        SHOWN.remove(player);
    }

    public static void clear() {
        SHOWN.clear();
    }

    /** Whether any border is being shown, so a platform can skip its work. */
    public static boolean isEmpty() {
        return SHOWN.isEmpty();
    }

    /** Called every server tick; draws the borders every {@link #REFRESH_TICKS} ticks. */
    public static void tick(Drawer drawer) {
        if (SHOWN.isEmpty() || ++ticks % REFRESH_TICKS != 0) {
            return;
        }
        long now = System.nanoTime();
        for (Map.Entry<UUID, Shown> e : SHOWN.entrySet()) {
            Shown shown = e.getValue();
            if (now - shown.untilNanos() > 0) {
                SHOWN.remove(e.getKey(), shown);
                continue;
            }
            Zone.Box box = shown.box();
            drawer.draw(e.getKey(), box.world(), points(box));
        }
    }

    /** Points along the 12 edges of the box's outer faces (blocks x1 to x2 inclusive), at most {@link #MAX_POINTS}. */
    static List<double[]> points(Zone.Box box) {
        double x1 = box.x1();
        double y1 = box.y1();
        double z1 = box.z1();
        double x2 = box.x2() + 1.0;
        double y2 = box.y2() + 1.0;
        double z2 = box.z2() + 1.0;
        double total = 4.0 * ((x2 - x1) + (y2 - y1) + (z2 - z1));
        double step = Math.max(1.0, Math.ceil(total / MAX_POINTS));
        List<double[]> out = new ArrayList<>();
        for (double y : new double[]{y1, y2}) {
            for (double z : new double[]{z1, z2}) {
                edge(out, x1, y, z, x2, y, z, step);
            }
        }
        for (double x : new double[]{x1, x2}) {
            for (double z : new double[]{z1, z2}) {
                edge(out, x, y1, z, x, y2, z, step);
            }
            for (double y : new double[]{y1, y2}) {
                edge(out, x, y, z1, x, y, z2, step);
            }
        }
        return out;
    }

    /** Points from a to b, both ends included, about {@code step} apart. */
    private static void edge(List<double[]> out, double ax, double ay, double az, double bx, double by, double bz, double step) {
        double length = Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az);
        int n = Math.max(1, (int) Math.round(length / step));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            out.add(new double[]{ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t});
        }
    }
}
