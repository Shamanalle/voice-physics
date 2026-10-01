package com.kasper.vcdistance.compat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.InputStream;

/** Minecraft 1.18.2 shapes of the calls in the modern {@code Mc}. */
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

    public static ServerPlayer player(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    public static void say(ServerPlayer player, Component text) {
        player.sendMessage(text, Util.NIL_UUID);
    }

    /** A system message shown as the overlay: the line above the hotbar. */
    public static void overlay(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    public static void run(MinecraftServer server, String command) {
        server.getCommands().performCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public static InputStream open(Resource resource) throws IOException {
        return resource.getInputStream();
    }
}
