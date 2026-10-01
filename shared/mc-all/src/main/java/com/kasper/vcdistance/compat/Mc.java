package com.kasper.vcdistance.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Small Minecraft calls whose shape differs between versions. Older bands replace this file with
 * their own copy of the same path.
 */
public final class Mc {

    private Mc() {
    }

    public static Level level(Entity entity) {
        return entity.level();
    }

    public static String blockId(Block block) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static String itemId(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }

    public static int x(AbstractWidget widget) {
        return widget.getX();
    }

    public static int y(AbstractWidget widget) {
        return widget.getY();
    }

    public static void setX(AbstractWidget widget, int x) {
        widget.setX(x);
    }

    public static void setY(AbstractWidget widget, int y) {
        widget.setY(y);
    }

    public static void success(CommandSourceStack source, Component text) {
        source.sendSuccess(() -> text, false);
    }
}
