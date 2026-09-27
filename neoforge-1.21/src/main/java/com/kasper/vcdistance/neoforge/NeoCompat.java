package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.client.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What differs between NeoForge versions, for Minecraft 1.21 - 1.21.1. The hooks that use it are shared by
 * every NeoForge build (shared/neoforge).
 */
final class NeoCompat {

    private NeoCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** The key, filed under the addon's own category; null where this Minecraft cannot make one. */
    static KeyMapping key(String name) {
        return KeyMappings.create(name, "key.categories.vc-audio-distance");
    }

    static void registerKeys(RegisterKeyMappingsEvent event, KeyMapping... keys) {
        for (KeyMapping key : keys) {
            if (key != null) {
                event.register(key);
            }
        }
    }
}
