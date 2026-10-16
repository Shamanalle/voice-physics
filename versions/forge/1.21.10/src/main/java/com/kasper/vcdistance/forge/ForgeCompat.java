package com.kasper.vcdistance.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

/**
 * What differs between Forge versions, for Minecraft 1.21.9 - 1.21.10. The hooks that use it are shared by every
 * Forge build since 1.20.6 (shared/forge, shared/forge-bus7).
 */
final class ForgeCompat {

    private ForgeCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** Since 1.21.9 a key's category is an object; Forge registers none of its own, so the key goes under Miscellaneous. */
    static KeyMapping key(String name) {
        return new KeyMapping(name, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    }
}
