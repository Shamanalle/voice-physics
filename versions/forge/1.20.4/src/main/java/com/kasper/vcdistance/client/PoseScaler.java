package com.kasper.vcdistance.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Scales what is drawn next (Forge 1.20.2 - 1.20.4). This jar is compiled against Minecraft's names and
 * remapped to Forge's, so the pose stack is called directly instead of by name as on Fabric.
 */
final class PoseScaler {

    private PoseScaler() {
    }

    static boolean push(Object graphics, float factor) {
        if (!(graphics instanceof GuiGraphics g)) {
            return false;
        }
        g.pose().pushPose();
        g.pose().scale(factor, factor, 1.0F);
        return true;
    }

    static void pop(Object graphics) {
        ((GuiGraphics) graphics).pose().popPose();
    }
}
