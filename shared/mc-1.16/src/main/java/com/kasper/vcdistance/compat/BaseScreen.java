package com.kasper.vcdistance.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minecraft 1.16.5: {@code addButton} and the two widget lists stand in for {@code addRenderableWidget} and {@code clearWidgets}. */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Component title) {
        super(title);
    }

    protected <T extends AbstractWidget> T addRenderableWidget(T widget) {
        return addButton(widget);
    }

    protected void clearWidgets() {
        buttons.clear();
        children.clear();
    }
}
