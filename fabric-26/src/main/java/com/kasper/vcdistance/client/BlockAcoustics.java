package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.BlockRules;
import net.minecraft.core.registries.BuiltInRegistries;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.VoxelRay;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acoustic ray casting through blocks for Minecraft 26.x, used by the client and the server.
 * <p>
 * Every voxel the ray passes through counts. Blocks that do not occlude light (glass, leaves, doors,
 * fences) are included explicitly through their material class. A ray that only grazes a block's
 * corner counts it by the short way it runs inside; doors, trapdoors, fences and bars count fully,
 * and open doors, trapdoors and gates a tenth.
 */
public final class BlockAcoustics {

    /** Marker for blocks that do not stop sound (ConcurrentHashMap cannot store null). */
    private static final Object NONE = new Object();
    private static final Map<BlockState, Object> MATERIALS = new ConcurrentHashMap<>();

    /** The block rules the cached materials were worked out with; a change of rules drops the cache. */
    private static volatile BlockRules appliedRules = BlockRules.EMPTY;

    /** Takes the rules of the settings the trace runs with (the player's, or the server's when the server does the walls). */
    private static void refreshRules(BlockRules rules) {
        if (rules != appliedRules) {
            appliedRules = rules;
            MATERIALS.clear();
        }
    }

    /** The material the player's or the server's block rules give this block, or {@code null}. */
    private static AcousticMaterial custom(BlockState state) {
        BlockRules rules = appliedRules;
        if (rules.isEmpty()) {
            return null;
        }
        String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return rules.find(id, tag -> state.getTags().anyMatch(t -> tag.equals(String.valueOf(t.location()))));
    }

    private BlockAcoustics() {
    }

    /** Acoustic thickness (in stone blocks) along one straight ray, using {@code weights} per material. */
    public static double traceRay(BlockGetter level, Vec3 from, Vec3 to, DistanceConfig weights) {
        if (level == null) {
            return 0.0;
        }
        refreshRules(weights.getBlockRules());
        double[] thickness = {0.0};
        BlockGetter.traverseBlocks(from, to, thickness, (acc, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty()) {
                acc[0] += weights.getMaterialWeight(AcousticMaterial.LIQUID);
            }
            if (!state.isAir()) {
                Object material = MATERIALS.computeIfAbsent(state, s -> {
                    AcousticMaterial m = classify(s);
                    return m == null ? NONE : m;
                });
                if (material instanceof AcousticMaterial m) {
                    acc[0] += weights.getMaterialWeight(m) * share(state, m, pos, from, to);
                }
            }
            return acc[0] >= WorldAccess.MAX_RAY_THICKNESS ? Boolean.TRUE : null;
        }, acc -> null);
        return thickness[0];
    }

    /** How much of the block's weight the ray takes: grazing a corner counts less than crossing it. */
    static double share(BlockState state, AcousticMaterial material, BlockPos pos, Vec3 from, Vec3 to) {
        if (isPanel(state, material)) {
            return AcousticMaterial.panelShare(state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN));
        }
        return RayBundle.chordWeight(VoxelRay.chord(from.x, from.y, from.z, to.x, to.y, to.z,
                pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
    }

    /** Doors, trapdoors (wooden or metal), fences, gates and bars: thin by nature. */
    private static boolean isPanel(BlockState state, AcousticMaterial material) {
        return material == AcousticMaterial.DOOR || material == AcousticMaterial.THIN
                || (material == AcousticMaterial.METAL && (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)));
    }

    /** {@code true} when sound passes this block freely: air, water, open doors and gates, fences, bars. */
    public static boolean isOpenForSound(BlockGetter level, int x, int y, int z) {
        if (level == null) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            return true;
        }
        return classify(state) == AcousticMaterial.THIN;
    }

    /** The material of a surface an echo bounces off (blocks that let sound through by their tool). */
    public static AcousticMaterial echoMaterial(BlockState state) {
        AcousticMaterial m = classify(state);
        return m != null ? m : byTool(state);
    }

    /** Block tags can differ between servers, so the material cache is dropped on world change. */
    public static void clearCache() {
        MATERIALS.clear();
    }

    /**
     * @return the material, or {@code null} for blocks that do not stop sound (plants, carpets, rails...)
     */
    static AcousticMaterial classify(BlockState state) {
        AcousticMaterial custom = custom(state);
        if (custom != null) {
            return custom;
        }
        Block block = state.getBlock();
        if (state.is(BlockTags.WOOL)) {
            return AcousticMaterial.WOOL;
        }
        if (state.is(BlockTags.LEAVES)) {
            return AcousticMaterial.LEAVES;
        }
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
            // Iron and copper doors (mined with a pickaxe) are metal
            return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? AcousticMaterial.METAL : AcousticMaterial.DOOR;
        }
        // Ice sounds like glass, so it is checked first
        if (state.is(BlockTags.ICE)) {
            return AcousticMaterial.ICE;
        }
        if (block instanceof TransparentBlock || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock) {
            return AcousticMaterial.GLASS;
        }
        if (block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof IronBarsBlock) {
            return AcousticMaterial.THIN;
        }
        if (!state.canOcclude()) {
            return null;
        }
        return byTool(state);
    }

    /** Solid blocks by their sound and by the tool that mines them. */
    static AcousticMaterial byTool(BlockState state) {
        SoundType sound = state.getSoundType();
        if (sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.NETHERITE_BLOCK
                || sound == SoundType.ANVIL) {
            return AcousticMaterial.METAL;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return AcousticMaterial.WOOD;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return AcousticMaterial.SOFT;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return AcousticMaterial.EARTH;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return AcousticMaterial.STONE;
        }
        return AcousticMaterial.OTHER;
    }
}
