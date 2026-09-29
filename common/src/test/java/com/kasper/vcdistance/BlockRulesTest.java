package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlockRulesTest {

    @Test
    @DisplayName("Ids and tags are normalized: lower case, minecraft: by default, # kept")
    void normalize() {
        assertEquals("minecraft:stone", BlockRules.normalize("  Stone "));
        assertEquals("create:andesite_casing", BlockRules.normalize("create:Andesite_Casing"));
        assertEquals("#c:glass_blocks", BlockRules.normalize("#c:glass_blocks"));
        assertEquals("#minecraft:logs", BlockRules.normalize("#logs"));
        assertNull(BlockRules.normalize(""));
        assertNull(BlockRules.normalize("#"));
        assertNull(BlockRules.normalize("two words"));
        assertNull(BlockRules.normalize("a=b"));
        assertNull(BlockRules.normalize("a,b"));
        assertNull(BlockRules.normalize(null));
    }

    @Test
    @DisplayName("A list is written in one line and read back the same; broken parts are dropped")
    void roundTrip() {
        BlockRules rules = BlockRules.EMPTY
                .with("create:andesite_casing", AcousticMaterial.METAL)
                .with("#c:glass_blocks", AcousticMaterial.GLASS)
                .with("obsidian", AcousticMaterial.STONE);
        assertEquals("create:andesite_casing=metal,#c:glass_blocks=glass,minecraft:obsidian=stone", rules.serialize());
        assertEquals(rules, BlockRules.parse(rules.serialize()));

        BlockRules broken = BlockRules.parse("create:a=metal,,nonsense,x=notamaterial,=stone, Y = WOOL ,create:a=wool");
        assertEquals(2, broken.size(), broken.toString());
        assertEquals(AcousticMaterial.METAL, broken.get("create:a").material(), "the first rule for a key stays");
        assertEquals(AcousticMaterial.WOOL, broken.get("minecraft:y").material());
        assertSame(BlockRules.EMPTY, BlockRules.parse(null));
        assertSame(BlockRules.EMPTY, BlockRules.parse("  "));
    }

    @Test
    @DisplayName("Adding replaces a rule for the same key; removing takes it out; the list is full at 64")
    void changes() {
        BlockRules a = BlockRules.EMPTY.with("stone", AcousticMaterial.WOOL);
        BlockRules b = a.with("minecraft:stone", AcousticMaterial.GLASS);
        assertEquals(1, b.size());
        assertEquals(AcousticMaterial.GLASS, b.get("stone").material());
        assertEquals(AcousticMaterial.WOOL, a.get("stone").material(), "the old list is untouched");
        assertSame(b, b.without("dirt"), "nothing to take out");
        assertSame(BlockRules.EMPTY, b.without("stone"));
        assertNull(a.with("not valid", AcousticMaterial.WOOL));

        BlockRules full = BlockRules.EMPTY;
        for (int i = 0; i < BlockRules.MAX_RULES; i++) {
            full = full.with("mod:block_" + i, AcousticMaterial.STONE);
        }
        assertEquals(BlockRules.MAX_RULES, full.size());
        assertNull(full.with("mod:one_more", AcousticMaterial.STONE));
        assertNotNull(full.with("mod:block_3", AcousticMaterial.WOOL), "an existing rule can still change");
        assertEquals(BlockRules.MAX_RULES, BlockRules.parse(full.serialize() + ",mod:extra=stone").size());
    }

    @Test
    @DisplayName("A block's own id beats a tag; tags go in the order they were added; no rule means null")
    void find() {
        BlockRules rules = BlockRules.EMPTY
                .with("#c:glass_blocks", AcousticMaterial.GLASS)
                .with("#c:ores", AcousticMaterial.METAL)
                .with("mod:tinted_glass", AcousticMaterial.STONE);
        Set<String> tags = Set.of("c:glass_blocks", "c:ores");
        assertEquals(AcousticMaterial.STONE, rules.find("mod:tinted_glass", tags::contains));
        assertEquals(AcousticMaterial.GLASS, rules.find("mod:other", tags::contains), "the first tag added");
        assertEquals(AcousticMaterial.METAL, rules.find("mod:other", Set.of("c:ores")::contains));
        assertNull(rules.find("mod:other", t -> false));
        assertNull(BlockRules.EMPTY.find("mod:other", t -> true));
    }
}
