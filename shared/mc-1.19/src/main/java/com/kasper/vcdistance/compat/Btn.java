package com.kasper.vcdistance.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds a {@link Button} on Minecraft 1.19 - 1.19.2, which has no {@code Button.builder} (1.19.3): the
 * constructor takes the position, the press action and the tooltip hook, and the hook draws the
 * button's {@link Tip} on the current screen.
 */
public final class Btn {

    private final Component label;
    private final Button.OnPress onPress;
    private int x;
    private int y;
    private int width = 150;
    private int height = 20;
    private Tip tip;

    private Btn(Component label, Button.OnPress onPress) {
        this.label = label;
        this.onPress = onPress;
    }

    public static Btn builder(Component label, Button.OnPress onPress) {
        return new Btn(label, onPress);
    }

    public Btn bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public Btn tooltip(Tip tip) {
        this.tip = tip;
        return this;
    }

    public Button build() {
        Button[] self = new Button[1];
        self[0] = new Button(x, y, width, height, label, onPress, (button, poseStack, mouseX, mouseY) -> show(self[0], poseStack, mouseX, mouseY));
        if (tip != null) {
            Tip.set(self[0], tip);
        }
        return self[0];
    }

    private static void show(Button button, PoseStack poseStack, int mouseX, int mouseY) {
        Tip tip = Tip.of(button);
        Screen screen = Minecraft.getInstance().screen;
        if (tip != null && screen != null) {
            screen.renderTooltip(poseStack, Minecraft.getInstance().font.split(tip.text(), 200), mouseX, mouseY);
        }
    }
}
