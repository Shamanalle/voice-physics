package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.client.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.lang.reflect.Method;

/**
 * What differs between NeoForge versions, for Minecraft 1.21.6 - 1.21.8. The hooks that use it are shared by
 * every NeoForge build (shared/neoforge).
 */
final class NeoCompat {

    private NeoCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static Method sendToServer;

    /**
     * NeoForge 21.8 moved {@code sendToServer} from {@code PacketDistributor} to the client's
     * {@code ClientPacketDistributor}; the 1.21.6 - 1.21.7 releases still have the old one.
     */
    static void sendToServer(CustomPacketPayload payload) {
        try {
            if (sendToServer == null) {
                sendToServer = find("net.neoforged.neoforge.client.network.ClientPacketDistributor");
                if (sendToServer == null) {
                    sendToServer = find("net.neoforged.neoforge.network.PacketDistributor");
                }
            }
            sendToServer.invoke(null, payload, new CustomPacketPayload[0]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot send to the server", e);
        }
    }

    private static Method find(String owner) {
        try {
            return Class.forName(owner).getMethod("sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
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
