package com.kasper.vcdistance.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * What differs between Forge versions, for Minecraft 1.21.11. The hooks that use it are shared by every
 * Forge build since 1.20.6 (shared/forge, shared/forge-bus7).
 */
final class ForgeCompat {

    private ForgeCompat() {
    }

    static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    /** Since 1.21.9 a key's category is an object; Forge registers none of its own, so the key goes under Miscellaneous. */
    static KeyMapping key(String name) {
        return new KeyMapping(name, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    }
}
