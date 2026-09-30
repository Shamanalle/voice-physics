package com.kasper.vcdistance;

import java.util.Locale;

/**
 * How the place the listener is in changes the sound, on top of what the rays measure: deep caves
 * ring more, the Nether's smoke dulls far voices, the End's void gives nothing back, snow and dense
 * jungles swallow sound. Mild on purpose; off with {@code place_tuning=false}.
 */
public final class PlaceTuning {

    /**
     * Where the listener is.
     *
     * @param dimension the dimension's key as the game prints it ({@code ...minecraft:the_nether...})
     * @param biome     the biome's key as the game prints it, or ""
     * @param y         the height of the head
     * @param sky       whether the open sky is above
     */
    public record Place(String dimension, String biome, double y, boolean sky) {
        public static final Place UNKNOWN = new Place("", "", 64.0, true);

        public Place {
            dimension = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
            biome = biome == null ? "" : biome.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What the place does.
     *
     * @param echo  multiplies the echo's strength (1 = as measured)
     * @param air   how much far voices lose their treble, 0 - 1 (1 = the most, at the edge of the range)
     * @param where a short English name of the place for reports and the monitor, or "" for an ordinary place
     */
    public record Tuning(double echo, double air, String where) {
        public static final Tuning NONE = new Tuning(1.0, 0.0, "");

        public boolean isNone() {
            return echo == 1.0 && air == 0.0;
        }
    }

    /** Below this height, under a roof, is a deep cave. */
    static final double DEEP_Y = 0.0;
    /** Below this height, under a roof, is a cave. */
    static final double CAVE_Y = 50.0;

    private PlaceTuning() {
    }

    public static Tuning of(Place p) {
        if (p == null) {
            return Tuning.NONE;
        }
        String biome = p.biome();
        if (p.dimension().contains("the_nether")) {
            if (biome.contains("soul_sand_valley")) {
                return new Tuning(1.35, 0.35, "soul sand valley");
            }
            if (biome.contains("basalt_deltas")) {
                return new Tuning(1.2, 0.5, "basalt deltas");
            }
            return new Tuning(1.2, 0.35, "nether");
        }
        if (p.dimension().contains("the_end")) {
            return p.sky() ? new Tuning(0.6, 0.0, "end, open void") : Tuning.NONE;
        }
        if (biome.contains("deep_dark")) {
            return new Tuning(1.3, 0.0, "deep dark");
        }
        if (biome.contains("lush_caves")) {
            return new Tuning(0.9, 0.0, "lush cave");
        }
        if (!p.sky()) {
            if (p.y() < DEEP_Y) {
                return new Tuning(1.3, 0.0, "deep cave");
            }
            if (p.y() < CAVE_Y) {
                return new Tuning(1.15, 0.0, "cave");
            }
            return Tuning.NONE;
        }
        if (has(biome, "jungle", "bamboo", "swamp", "mangrove")) {
            return new Tuning(0.9, 0.35, "dense plants");
        }
        if (has(biome, "snow", "frozen", "ice_spikes", "grove", "peaks")) {
            return new Tuning(0.8, 0.2, "snow");
        }
        return Tuning.NONE;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
