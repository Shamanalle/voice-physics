package com.kasper.vcdistance.forge;

import net.minecraft.resources.ResourceLocation;

/** What differs between Forge versions, for Minecraft 1.20.2 - 1.20.4 (see shared/forge). */
final class ForgeCompat {

    private ForgeCompat() {
    }

    static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
