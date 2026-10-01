package com.kasper.vcdistance.compat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Builds a {@link Button} the way {@code Button.builder} does (1.19.3 and newer). The older bands
 * replace this file with one that calls the button constructor of their version.
 */
public final class Btn {

    private final Button.Builder builder;

    private Btn(Component label, Button.OnPress onPress) {
        this.builder = Button.builder(label, onPress);
    }

    public static Btn builder(Component label, Button.OnPress onPress) {
        return new Btn(label, onPress);
    }

    public Btn bounds(int x, int y, int width, int height) {
        builder.bounds(x, y, width, height);
        return this;
    }

    public Btn tooltip(Tip tip) {
        builder.tooltip(tip == null ? null : tip.raw());
        return this;
    }

    public Button build() {
        return builder.build();
    }
}
