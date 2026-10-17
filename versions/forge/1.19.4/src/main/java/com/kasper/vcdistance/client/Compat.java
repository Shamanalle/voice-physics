package com.kasper.vcdistance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;

/**
 * Minecraft 1.19.4 specifics.
 */
public final class Compat {

    private Compat() {
    }

    /** 1.19.4 screens draw their own background before the widgets. */
    public static void renderBackground(Screen screen, PoseStack graphics) {
        screen.renderBackground(graphics);
    }
}
