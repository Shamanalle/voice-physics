package com.kasper.vcdistance.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * What differs between NeoForge versions, for Minecraft 1.21.9 - 1.21.10. The hooks that use it are shared by
 * every NeoForge build (shared/neoforge).
 */
final class NeoCompat {

    private NeoCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    /**
     * Since 1.21.9 a key's category is an object that NeoForge registers. Held apart, so a server
     * never loads the client's KeyMapping.
     */
    private static final class Category {
        static final KeyMapping.Category VALUE =
                new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath("vc-audio-distance", "general"));
    }

    static KeyMapping key(String name) {
        return new KeyMapping(name, InputConstants.UNKNOWN.getValue(), Category.VALUE);
    }

    static void registerKeys(RegisterKeyMappingsEvent event, KeyMapping... keys) {
        event.registerCategory(Category.VALUE);
        for (KeyMapping key : keys) {
            event.register(key);
        }
    }
}
