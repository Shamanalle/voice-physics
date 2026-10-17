package com.kasper.vcdistance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;

/**
 * Minecraft 1.18.2 specifics.
 */
public final class Compat {

    private Compat() {
    }

    /** 1.18.2 screens draw their own background before the widgets. */
    public static void renderBackground(Screen screen, PoseStack graphics) {
        screen.renderBackground(graphics);
    }
}
