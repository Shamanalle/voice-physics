package com.kasper.vcdistance.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

/** The screens' base class: a plain {@link Screen} here, with the widget calls of 1.17 and newer added in the 1.16 band. */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Component title) {
        super(title);
    }
}
