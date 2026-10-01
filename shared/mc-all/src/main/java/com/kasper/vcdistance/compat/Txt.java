package com.kasper.vcdistance.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text components by one name on every Minecraft version. Here (1.19 and newer) these are
 * {@link Component}'s own factories; the older bands replace this file with one that builds
 * {@code TextComponent} and {@code TranslatableComponent}.
 */
public final class Txt {

    private Txt() {
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static MutableComponent empty() {
        return Component.empty();
    }
}
