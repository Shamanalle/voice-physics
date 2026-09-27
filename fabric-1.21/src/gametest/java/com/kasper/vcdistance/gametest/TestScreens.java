package com.kasper.vcdistance.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens and reads the current screen on 1.21.x. */
final class TestScreens {

    private static volatile java.util.concurrent.CompletableFuture<?> reload;

    private TestScreens() {
    }

    static void open(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    static Screen current(Minecraft client) {
        return client.screen;
    }

    /** Switches the game's language; the resources reload after it. */
    static void language(Minecraft client, String code) {
        client.options.languageCode = code;
        client.getLanguageManager().setSelected(code);
        reload = client.reloadResourcePacks();
    }

    /** Whether the game has finished loading resources. */
    static boolean loaded(Minecraft client) {
        return reload == null || reload.isDone();
    }
}
