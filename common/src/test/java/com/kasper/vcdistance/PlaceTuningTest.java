package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The place the listener is in: caves, the Nether, the End, snow, jungles. */
public class PlaceTuningTest {

    private static final String OVERWORLD = "ResourceKey[minecraft:dimension / minecraft:overworld]";
    private static final String NETHER = "ResourceKey[minecraft:dimension / minecraft:the_nether]";
    private static final String END = "ResourceKey[minecraft:dimension / minecraft:the_end]";

    private static String biome(String id) {
        return "ResourceKey[minecraft:worldgen/biome / minecraft:" + id + "]";
    }

    private static PlaceTuning.Tuning at(String dimension, String biome, double y, boolean sky) {
        return PlaceTuning.of(new PlaceTuning.Place(dimension, biome, y, sky));
    }

    @Test
    @DisplayName("Ordinary places change nothing; unknown places neither")
    void ordinary() {
        assertTrue(at(OVERWORLD, biome("plains"), 70, true).isNone());
        assertTrue(at(OVERWORLD, biome("desert"), 70, true).isNone());
        assertTrue(at(OVERWORLD, biome("plains"), 70, false).isNone(), "a house on the surface");
        assertTrue(PlaceTuning.of(PlaceTuning.Place.UNKNOWN).isNone());
        assertTrue(PlaceTuning.of(null).isNone());
        assertTrue(at(null, null, 0, true).isNone());
    }

    @Test
    @DisplayName("Underground: caves ring more, deeper more, the deep dark most; lush caves less")
    void underground() {
        PlaceTuning.Tuning cave = at(OVERWORLD, biome("plains"), 30, false);
        PlaceTuning.Tuning deep = at(OVERWORLD, biome("dripstone_caves"), -30, false);
        assertEquals("cave", cave.where());
        assertTrue(cave.echo() > 1.0 && deep.echo() > cave.echo());
        assertTrue(at(OVERWORLD, biome("deep_dark"), -40, false).echo() >= deep.echo());
        assertTrue(at(OVERWORLD, biome("lush_caves"), 20, false).echo() < 1.0);
        assertTrue(at(OVERWORLD, biome("plains"), -30, true).isNone(), "a ravine open to the sky is measured, not guessed");
    }

    @Test
    @DisplayName("Nether is smoky, basalt deltas the most; the End's open void has less echo")
    void dimensions() {
        PlaceTuning.Tuning nether = at(NETHER, biome("nether_wastes"), 60, false);
        assertTrue(nether.air() > 0.0 && nether.echo() > 1.0);
        assertTrue(at(NETHER, biome("basalt_deltas"), 60, false).air() > nether.air());
        assertTrue(at(NETHER, biome("soul_sand_valley"), 60, false).echo() > nether.echo());
        assertTrue(at(END, biome("the_end"), 60, true).echo() < 1.0);
        assertTrue(at(END, biome("end_highlands"), 60, false).isNone(), "inside an End city");
    }

    @Test
    @DisplayName("Snow and dense plants swallow sound under the open sky; all values stay mild")
    void surface() {
        PlaceTuning.Tuning jungle = at(OVERWORLD, biome("bamboo_jungle"), 70, true);
        PlaceTuning.Tuning snow = at(OVERWORLD, biome("snowy_plains"), 70, true);
        assertTrue(jungle.air() > 0.0 && jungle.echo() < 1.0);
        assertTrue(snow.echo() < 1.0);
        assertTrue(at(OVERWORLD, biome("mangrove_swamp"), 70, true).air() > 0.0);
        for (String b : new String[]{"plains", "jungle", "snowy_slopes", "deep_dark", "basalt_deltas", "soul_sand_valley"}) {
            for (String d : new String[]{OVERWORLD, NETHER, END}) {
                for (boolean sky : new boolean[]{true, false}) {
                    PlaceTuning.Tuning t = at(d, biome(b), -20, sky);
                    assertTrue(t.echo() >= 0.5 && t.echo() <= 1.5 && t.air() >= 0.0 && t.air() <= 0.5, b + " " + d + " " + t);
                }
            }
        }
    }

    @Test
    @DisplayName("The switch is saved and follows the effects part")
    void setting() {
        DistanceConfig c = new DistanceConfig();
        assertTrue(c.isPlaceTuning());
        c.setPlaceTuning(false);
        java.util.Properties p = new java.util.Properties();
        c.writeTo(p, "");
        DistanceConfig back = new DistanceConfig();
        back.readFrom(p, "");
        assertFalse(back.isPlaceTuning());
        DistanceConfig other = new DistanceConfig();
        other.copyPart(DistanceConfig.Part.EFFECTS, c);
        assertFalse(other.isPlaceTuning());
        other.resetPart(DistanceConfig.Part.EFFECTS);
        assertTrue(other.isPlaceTuning());
    }
}
