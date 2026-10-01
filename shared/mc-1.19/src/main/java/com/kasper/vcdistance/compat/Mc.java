package com.kasper.vcdistance.compat;

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

    public static ServerPlayer player(CommandSourceStack source) {
        return source.getPlayer();
    }

    public static void say(ServerPlayer player, Component text) {
        player.sendSystemMessage(text);
    }

    /** A system message shown as the overlay: the line above the hotbar. */
    public static void overlay(ServerPlayer player, Component text) {
        player.sendSystemMessage(text, true);
    }

    public static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public static InputStream open(Resource resource) throws IOException {
        return resource.open();
    }

    public static net.minecraft.server.packs.resources.ResourceManager resources(MinecraftServer server) {
        return server.getResourceManager();
    }

    /** Resource listing of versions before the 1.19 one (a Collection of locations), {@code null} where it is the newer one. */
    public static java.util.Map<String, java.util.List<Resource>> listOld(net.minecraft.server.packs.resources.ResourceManager resources, String path) {
        return null;
    }

    public static net.minecraft.world.phys.Vec3 eye(Entity entity) {
        return entity.getEyePosition();
    }

    public static float yRot(Entity entity) {
        return entity.getYRot();
    }

    public static String biomeId(Level level, net.minecraft.core.BlockPos pos) {
        return String.valueOf(level.getBiome(pos).unwrapKey().orElse(null));
    }
}
