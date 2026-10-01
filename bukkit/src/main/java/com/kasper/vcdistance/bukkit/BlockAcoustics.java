package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.BlockRules;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.VoxelRay;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acoustic ray casting through blocks with the Bukkit API. Mirrors the Fabric version: a block only
 * counts when the ray crosses its collision shape, and materials are classified the same way.
 * Safe to call from several threads (Folia traces each player's walls on the region's own thread);
 * unloaded chunks are never loaded, they count as open air.
 */
final class BlockAcoustics {

    /** Longest ray walked, in blocks (the tracer never asks for more than 160). */
    private static final int MAX_BLOCKS = 512;

    private static final Map<Material, AcousticMaterial> MATERIALS = new ConcurrentHashMap<>();

    /** The block rules the cached materials were worked out with; a change of rules drops the cache. */
    private static volatile BlockRules appliedRules = BlockRules.EMPTY;

    private static void refreshRules() {
        BlockRules rules = AudioDistancePlugin.SERVER_SETTINGS.getBlockRules();
        if (rules != appliedRules) {
            appliedRules = rules;
            MATERIALS.clear();
        }
    }

    /** The material the server's block rules give this block, or {@code null}. */
    private static AcousticMaterial custom(Material m) {
        BlockRules rules = appliedRules;
        if (rules.isEmpty()) {
            return null;
        }
        return rules.find(m.getKey().toString(), tag -> {
            NamespacedKey key = NamespacedKey.fromString(tag);
            Tag<Material> blockTag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
            return blockTag != null && blockTag.isTagged(m);
        });
    }

    private BlockAcoustics() {
    }

    /** Acoustic thickness (in stone blocks) along one straight ray, using {@code weights} per material. */
    static double traceRay(World world, double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                           DistanceConfig weights) {
        refreshRules();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        double[] thickness = {0.0};
        VoxelRay.walk(fromX, fromY, fromZ, toX, toY, toZ, MAX_BLOCKS, (x, y, z) -> {
            if (y < minY || y >= maxY || !world.isChunkLoaded(x >> 4, z >> 4)) {
                return false;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (block.isLiquid() || isWaterlogged(block)) {
                thickness[0] += weights.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!type.isAir() && !block.isLiquid()) {
                AcousticMaterial material = MATERIALS.computeIfAbsent(type, m -> classify(block));
                double share = share(block, type, material, fromX, fromY, fromZ, toX, toY, toZ);
                if (share > 0.0) {
                    thickness[0] += weights.getMaterialWeight(material) * share;
                }
            }
            return thickness[0] >= RayBundle.MAX_RAY_THICKNESS;
        });
        return thickness[0];
    }

    /** Block tags can change with data packs, so the material cache is dropped on settings reload. */
    static void clearCache() {
        MATERIALS.clear();
    }

    private static boolean isWaterlogged(Block block) {
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged w && w.isWaterlogged();
    }

    /**
     * How much of the block's weight the ray takes, 0 when it misses the collision shape (slabs, open
     * doors and carpets by their real size). A ray that only grazes a corner counts less than one
     * crossing the block; doors, trapdoors, fences and bars count fully whenever they are crossed, and
     * open doors and trapdoors a tenth.
     */
    private static double share(Block block, Material type, AcousticMaterial material, double fromX, double fromY,
                                double fromZ, double toX, double toY, double toZ) {
        boolean thin = material == AcousticMaterial.DOOR || material == AcousticMaterial.THIN
                || (material == AcousticMaterial.METAL && (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type)));
        double panel = thin ? AcousticMaterial.panelShare(block.getBlockData() instanceof Openable o && o.isOpen()) : 1.0;
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        if (type.isOccluding()) {
            // Full opaque cube
            return thin ? panel : RayBundle.chordWeight(VoxelRay.chord(fromX, fromY, fromZ, toX, toY, toZ,
                    bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0));
        }
        double best = 0.0;
        for (BoundingBox box : collisionBoxes(block)) {
            // Collision boxes are relative to the block
            double chord = VoxelRay.chord(fromX, fromY, fromZ, toX, toY, toZ,
                    bx + box.getMinX(), by + box.getMinY(), bz + box.getMinZ(),
                    bx + box.getMaxX(), by + box.getMaxY(), bz + box.getMaxZ());
            if (chord > 0.0 || VoxelRay.intersects(fromX, fromY, fromZ, toX, toY, toZ,
                    bx + box.getMinX(), by + box.getMinY(), bz + box.getMinZ(),
                    bx + box.getMaxX(), by + box.getMaxY(), bz + box.getMaxZ())) {
                if (thin) {
                    return panel;
                }
                best = Math.max(best, Math.max(0.05, RayBundle.chordWeight(chord)));
            }
        }
        return best;
    }

    /** {@code true} when sound passes this block freely: air, water, open doors and gates, fences, bars. */
    static boolean isOpenForSound(World world, int x, int y, int z) {
        refreshRules();
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return true;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.isPassable()) {
            return true;
        }
        if (block.getBlockData() instanceof Openable openable && openable.isOpen()) {
            return true;
        }
        return MATERIALS.computeIfAbsent(block.getType(), m -> classify(block)) == AcousticMaterial.THIN;
    }

    static AcousticMaterial classify(Block block) {
        Material m = block.getType();
        AcousticMaterial custom = custom(m);
        if (custom != null) {
            return custom;
        }
        String carpet = m.name();
        // Tag.WOOL_CARPETS is 1.19.3+, so wool carpets are found by name (moss carpet is not wool)
        if (Tag.WOOL.isTagged(m) || (carpet.endsWith("_CARPET") && !carpet.startsWith("MOSS"))) {
            return AcousticMaterial.WOOL;
        }
        if (Tag.LEAVES.isTagged(m)) {
            return AcousticMaterial.LEAVES;
        }
        if (Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m)) {
            // Iron and copper doors (mined with a pickaxe) are metal
            return mineable("pickaxe", m) ? AcousticMaterial.METAL : AcousticMaterial.DOOR;
        }
        if (Tag.FENCES.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) {
            return AcousticMaterial.THIN;
        }
        String name = m.name().toLowerCase(Locale.ROOT);
        Sound sound = breakSound(block);
        // Ice sounds like glass, so it is checked first
        if (Tag.ICE.isTagged(m)) {
            return AcousticMaterial.ICE;
        }
        if (name.contains("glass") || isGlass(sound)) {
            return AcousticMaterial.GLASS;
        }
        if (name.endsWith("_bars")) {
            return AcousticMaterial.THIN;
        }
        if (isMetal(sound) || name.contains("copper") || name.equals("iron_block") || name.equals("gold_block")
                || name.equals("netherite_block") || name.endsWith("anvil")) {
            return AcousticMaterial.METAL;
        }
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m) || mineable("axe", m) || name.contains("bamboo")
                || isWood(sound)) {
            return AcousticMaterial.WOOD;
        }
        // Everything else by the tool that mines it
        if (mineable("hoe", m)) {
            return AcousticMaterial.SOFT;
        }
        if (mineable("shovel", m)) {
            return AcousticMaterial.EARTH;
        }
        if (mineable("pickaxe", m)) {
            return AcousticMaterial.STONE;
        }
        return AcousticMaterial.OTHER;
    }

    // Minecraft 1.16 - 1.16.5 have no mineable/* tags (1.17+), so the lookup returns null there and the
    // sound and name checks decide
    private static boolean mineable(String tool, Material m) {
        try {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("mineable/" + tool), Material.class);
            return tag != null && tag.isTagged(m);
        } catch (Throwable t) {
            return false;
        }
    }

    // Block#getCollisionShape is missing from the 1.16 API: there the block's bounding box (absolute) stands in
    private static Collection<BoundingBox> collisionBoxes(Block block) {
        try {
            Object shape = Block.class.getMethod("getCollisionShape").invoke(block);
            Object boxes = shape.getClass().getMethod("getBoundingBoxes").invoke(shape);
            @SuppressWarnings("unchecked")
            Collection<BoundingBox> list = (Collection<BoundingBox>) boxes;
            return list;
        } catch (ReflectiveOperationException | RuntimeException e) {
            BoundingBox box = block.getBoundingBox();
            return java.util.Collections.singletonList(new BoundingBox(box.getMinX() - block.getX(), box.getMinY() - block.getY(),
                    box.getMinZ() - block.getZ(), box.getMaxX() - block.getX(), box.getMaxY() - block.getY(), box.getMaxZ() - block.getZ()));
        }
    }

    // Sound was an enum and became a registry interface in 1.21.3, so sounds are compared through
    // Objects.equals (no enum-only bytecode) and every use is guarded: when it fails, the tag and
    // name checks above still classify the block.

    private static Sound breakSound(Block block) {
        try {
            SoundGroup group = block.getBlockData().getSoundGroup();
            return group.getBreakSound();
        } catch (Throwable t) {
            return null;
        }
    }

    // Sounds newer than 1.16 (copper) are looked up by name
    private static Object named(String field) {
        try {
            return Sound.class.getField(field).get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isGlass(Sound sound) {
        try {
            return sound != null && Objects.equals(sound, Sound.BLOCK_GLASS_BREAK);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMetal(Sound sound) {
        try {
            return sound != null && (Objects.equals(sound, Sound.BLOCK_METAL_BREAK)
                    || Objects.equals(sound, named("BLOCK_COPPER_BREAK"))
                    || Objects.equals(sound, Sound.BLOCK_NETHERITE_BLOCK_BREAK)
                    || Objects.equals(sound, Sound.BLOCK_ANVIL_BREAK));
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isWood(Sound sound) {
        try {
            return sound != null && (Objects.equals(sound, Sound.BLOCK_WOOD_BREAK) || OTHER_WOOD_BREAKS.contains(sound));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Wood break sounds that older servers lack (Sound constants of 1.19.3 and 1.20), looked up by name. */
    private static final java.util.List<Object> OTHER_WOOD_BREAKS = lookup(
            "BLOCK_NETHER_WOOD_BREAK", "BLOCK_BAMBOO_WOOD_BREAK", "BLOCK_CHERRY_WOOD_BREAK");

    private static java.util.List<Object> lookup(String... names) {
        java.util.List<Object> found = new java.util.ArrayList<>();
        for (String name : names) {
            try {
                found.add(Sound.class.getField(name).get(null));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // not on this server version
            }
        }
        return found;
    }
}
