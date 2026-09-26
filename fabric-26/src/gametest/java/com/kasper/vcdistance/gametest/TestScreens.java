package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.client.ScreenSwitch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens and reads the current screen on 26.x (the API moved in 26.3). */
final class TestScreens {

    private TestScreens() {
    }

    static void open(Minecraft client, Screen screen) {
        ScreenSwitch.open(client, screen);
    }

    static Screen current(Minecraft client) {
        return ScreenSwitch.current(client);
    }
}
