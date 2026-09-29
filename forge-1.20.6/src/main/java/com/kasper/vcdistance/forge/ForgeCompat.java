package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.client.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * What differs between Forge versions, for Minecraft 1.20.6. The hooks that use it are shared by every
 * Forge build since 1.20.6 (shared/forge, shared/forge-bus6).
 */
final class ForgeCompat {

    private ForgeCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    /** The key, filed under the addon's own category; null where this Minecraft cannot make one. */
    static KeyMapping key(String name) {
        return KeyMappings.create(name, "key.categories.vc-audio-distance");
    }

    /** The settings screen behind the Mods list's Config button. */
    static void configScreen(FMLJavaModLoadingContext context, ConfigScreenHandler.ConfigScreenFactory factory) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> factory);
    }

    /** The voice HUD, drawn over the game. */
    static void hud(IEventBus modBus, ResourceLocation id, LayeredDraw.Layer layer) {
        modBus.addListener((AddGuiOverlayLayersEvent e) -> e.getLayeredDraw().add(id, layer));
    }
}
