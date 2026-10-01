package com.kasper.vcdistance.compat;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Text components on Minecraft 1.16 - 1.18.2, which have no {@code Component.literal} (1.19). */
public final class Txt {

    private Txt() {
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
    }
}
