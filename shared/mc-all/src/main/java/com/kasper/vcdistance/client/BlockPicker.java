package com.kasper.vcdistance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The block the player looks at and the block in their hand, as {@code namespace:path} ids, for the block
 * rules of the settings screen. Both are {@code ""} when there is none (no block in the crosshair, an empty
 * hand or an item that is no block).
 */
final class BlockPicker {

    private BlockPicker() {
    }

    /** The block under the crosshair as it was when the settings screen opened. */
    static String lookedAt() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                return "";
            }
            return idOf(client.level.getBlockState(hit.getBlockPos()).getBlock());
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The block the item in the main hand places. */
    static String inHand() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return "";
            }
            ItemStack stack = client.player.getMainHandItem();
            return stack.isEmpty() ? "" : idOf(Block.byItem(stack.getItem()));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String idOf(Block block) {
        return block == null || block == Blocks.AIR ? "" : String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
    }
}
