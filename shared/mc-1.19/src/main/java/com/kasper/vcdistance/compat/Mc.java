package com.kasper.vcdistance.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Minecraft 1.18 - 1.19.2 shapes of the calls in the modern {@code Mc}. */
public final class Mc {

    private Mc() {
    }

    public static Level level(Entity entity) {
        return entity.level;
    }

    public static String blockId(Block block) {
        return String.valueOf(Registry.BLOCK.getKey(block));
    }

    public static String itemId(Item item) {
        return String.valueOf(Registry.ITEM.getKey(item));
    }

    public static int x(AbstractWidget widget) {
        return widget.x;
    }

    public static int y(AbstractWidget widget) {
        return widget.y;
    }

    public static void setX(AbstractWidget widget, int x) {
        widget.x = x;
    }

    public static void setY(AbstractWidget widget, int y) {
        widget.y = y;
    }

    public static void success(CommandSourceStack source, Component text) {
        source.sendSuccess(text, false);
    }
}
