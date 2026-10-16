package com.kasper.vcdistance.forge;

import net.minecraft.resources.Identifier;

/** What differs between Forge versions, for Minecraft 26.x (see shared/forge). */
final class ForgeCompat {

    private ForgeCompat() {
    }

    static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
