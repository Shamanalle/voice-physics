package com.kasper.vcdistance;

import com.kasper.vcdistance.client.Compat;
import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.LogScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Change log screen for Minecraft 1.19 - 1.19.2. All logic lives in {@link LogScreen}.
 */
public class AudioDistanceLogScreen extends LogScreen {

    public AudioDistanceLogScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void openScreen(Screen screen) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(screen);
        }
    }

    @Override
    protected void writeClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public void render(PoseStack graphics, int mouseX, int mouseY, float delta) {
        Compat.renderBackground(this, graphics);
        super.render(graphics, mouseX, mouseY, delta);
        paint(new GuiCanvas(graphics, this.font), mouseX, mouseY);
    }
}
