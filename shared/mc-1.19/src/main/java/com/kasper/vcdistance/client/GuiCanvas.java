package com.kasper.vcdistance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

/**
 * {@link Canvas} over {@link PoseStack} for Minecraft 1.18 - 1.19.2, which draw through
 * {@link GuiComponent}'s static methods (GuiGraphics came in 1.20).
 */
public final class GuiCanvas implements Canvas {

    private final PoseStack pose;
    private final Font font;

    public GuiCanvas(PoseStack pose, Font font) {
        this.pose = pose;
        this.font = font;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (x2 > x1 && y2 > y1) {
            GuiComponent.fill(pose, x1, y1, x2, y2, argb);
        }
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        font.drawShadow(pose, text, x, y, argb);
    }

    @Override
    public void centered(Component text, int centerX, int y, int argb) {
        font.drawShadow(pose, text, centerX - font.width(text) / 2.0F, y, argb);
    }

    @Override
    public int width(Component text) {
        return font.width(text);
    }

    @Override
    public boolean pushScale(float factor) {
        pose.pushPose();
        pose.scale(factor, factor, 1.0F);
        return true;
    }

    @Override
    public void popScale() {
        pose.popPose();
    }
}
