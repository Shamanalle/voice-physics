package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.client.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/**
 * What differs between Forge versions, for Minecraft 1.20.2 - 1.20.4. The networking that uses it is
 * shared with Forge 1.20.6+ (shared/forge).
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
}
