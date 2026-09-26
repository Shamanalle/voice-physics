package com.kasper.vcdistance.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens and reads the current screen on 1.21.x. */
final class TestScreens {

    private TestScreens() {
    }

    static void open(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    static Screen current(Minecraft client) {
        return client.screen;
    }
}
