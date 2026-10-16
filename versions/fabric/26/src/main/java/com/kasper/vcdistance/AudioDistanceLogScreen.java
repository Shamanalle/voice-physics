package com.kasper.vcdistance;

import com.kasper.vcdistance.client.ExtractorCanvas;
import com.kasper.vcdistance.client.LogScreen;
import com.kasper.vcdistance.client.ScreenSwitch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Change log screen for Minecraft 26.x. All logic lives in {@link LogScreen}.
 */
public class AudioDistanceLogScreen extends LogScreen {

    public AudioDistanceLogScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void openScreen(Screen screen) {
        if (this.minecraft != null) {
            ScreenSwitch.open(this.minecraft, screen);
        }
    }

    @Override
    protected void writeClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        paint(new ExtractorCanvas(graphics, this.font), mouseX, mouseY);
    }
}
