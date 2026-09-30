package com.kasper.vcdistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Block materials from {@code voice_physics/materials.json} in resource packs, mod jars and data packs. */
public class BlockDataRulesTest {

    @AfterEach
    void clear() {
        BlockDataRules.setAssets(null);
        BlockDataRules.setData(null);
    }

    private static BlockDataRules.Source file(String name, String json) {
        return new BlockDataRules.Source(name, json);
    }

    @Test
    @DisplayName("JSON: objects in order, arrays, escapes, numbers; mistakes name the line")
    void json() {
        Object v = MiniJson.parse("﻿{ \"a\": [1, -2.5e1, true, false, null], \"b\": \"x\\\"\\u0041\\n\", \"c\": {} }");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(List.of("a", "b", "c"), List.copyOf(m.keySet()));
        assertEquals(java.util.Arrays.asList(1.0, -25.0, true, false, null), m.get("a"));
        assertEquals("x\"A\n", m.get("b"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\n  \"a\": 1,\n  \"b\" 2\n}"));
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\": 1} x"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\": \"open"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[".repeat(100)));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse(""));
    }

    @Test
    @DisplayName("A file: ids and tags, no namespace means minecraft, wrong entries skipped and named")
    void oneFile() {
        BlockDataRules.Loaded loaded = BlockDataRules.read(List.of(file("mymod/materials.json", """
                {
                  "blocks": {
                    "create:andesite_casing": "metal",
                    "#minecraft:beds": "wool",
                    "hay_block": "SOFT",
                    "Bad Id!": "stone",
                    "create:belt": "rubber",
                    "create:shaft": 3
                  }
                }""")));
        assertEquals(1, loaded.files());
        assertEquals(3, loaded.rules().size());
        assertEquals(AcousticMaterial.METAL, loaded.rules().find("create:andesite_casing", t -> false));
        assertEquals(AcousticMaterial.SOFT, loaded.rules().find("minecraft:hay_block", t -> false));
        assertEquals(AcousticMaterial.WOOL, loaded.rules().find("minecraft:red_bed", Set.of("minecraft:beds")::contains));
        assertNull(loaded.rules().find("minecraft:stone", t -> false), "no rule: the automatic guess stays");
        assertEquals(3, loaded.problems().size(), String.valueOf(loaded.problems()));
        assertTrue(loaded.problems().get(1).contains("\"rubber\" for create:belt is not a material"), loaded.problems().get(1));
        assertTrue(loaded.problems().get(1).contains("wool"), "lists the materials");
        assertEquals("3 rules from 1 file, 3 problems", loaded.summary());
    }

    @Test
    @DisplayName("Packs: a higher one wins, replace drops the lower ones, a broken file is skipped")
    void packs() {
        BlockDataRules.Loaded loaded = BlockDataRules.read(List.of(
                file("low", "{\"blocks\": {\"a:x\": \"stone\", \"a:y\": \"wood\", \"#a:t\": \"glass\"}}"),
                file("broken", "{\"blocks\": "),
                file("high", "{\"blocks\": {\"a:x\": \"wool\", \"#a:u\": \"metal\"}}")));
        assertEquals(2, loaded.files());
        assertEquals(1, loaded.problems().size());
        assertTrue(loaded.problems().get(0).startsWith("broken: unexpected end"), loaded.problems().get(0));
        assertEquals(AcousticMaterial.WOOL, loaded.rules().find("a:x", t -> false));
        assertEquals(AcousticMaterial.WOOD, loaded.rules().find("a:y", t -> false));
        assertEquals(AcousticMaterial.METAL, loaded.rules().find("a:z", Set.of("a:t", "a:u")::contains), "the higher pack's tag first");

        loaded = BlockDataRules.read(List.of(
                file("low", "{\"blocks\": {\"a:x\": \"stone\", \"a:y\": \"wood\"}}"),
                file("high", "{\"replace\": true, \"blocks\": {\"a:x\": \"wool\"}}")));
        assertEquals(1, loaded.rules().size());
        assertNull(loaded.rules().find("a:y", t -> false));

        assertEquals(1, BlockDataRules.read(List.of(file("list", "[]"))).problems().size(), "not an object");
        assertSame(BlockDataRules.Loaded.NONE, BlockDataRules.read(List.of()));
    }

    @Test
    @DisplayName("In force: data packs over resource packs; more than 64 rules are fine")
    void current() {
        assertTrue(BlockDataRules.current().isEmpty());
        StringBuilder many = new StringBuilder("{\"blocks\": {");
        for (int i = 0; i < 200; i++) {
            many.append(i == 0 ? "" : ",").append("\"m:b").append(i).append("\": \"wood\"");
        }
        many.append(", \"a:x\": \"glass\"}}");
        BlockDataRules.setAssets(BlockDataRules.read(List.of(file("pack", many.toString()))));
        assertEquals(201, BlockDataRules.current().size());
        BlockDataRules.setData(BlockDataRules.read(List.of(file("datapack", "{\"blocks\": {\"a:x\": \"metal\"}}"))));
        assertEquals(AcousticMaterial.METAL, BlockDataRules.current().find("a:x", t -> false));
        assertEquals(AcousticMaterial.WOOD, BlockDataRules.current().find("m:b150", t -> false));
        BlockRules before = BlockDataRules.current();
        BlockDataRules.setData(BlockDataRules.read(List.of(file("datapack", "{\"blocks\": {\"a:x\": \"metal\"}}"))));
        assertSame(before, BlockDataRules.current(), "the same rules keep the same list, so caches stay");
        BlockDataRules.setData(null);
        assertEquals(AcousticMaterial.GLASS, BlockDataRules.current().find("a:x", t -> false));
    }
}
