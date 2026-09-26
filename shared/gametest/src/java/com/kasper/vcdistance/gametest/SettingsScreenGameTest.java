package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Opens the settings screen in a real client, in a world, on every tab and at a normal and a large
 * interface size, scrolls each tab to its end and takes screenshots (uploaded by CI). Any exception
 * while the screen is built or drawn fails the test.
 */
public class SettingsScreenGameTest implements FabricClientGameTest {

    private static final int[] GUI_SCALES = {2, 4};

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(854, 480);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            for (int scale : GUI_SCALES) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(scale);
                    client.resizeDisplay();
                });
                for (SettingsScreen.Tab tab : SettingsScreen.Tab.values()) {
                    if (tab == SettingsScreen.Tab.SERVER) {
                        continue; // only for admins of a server with the addon
                    }
                    String name = tab.name().toLowerCase(java.util.Locale.ROOT) + "-scale" + scale;
                    context.runOnClient(client -> {
                        SettingsScreen.openOn(tab);
                        TestScreens.open(client, new AudioDistanceScreen(null));
                    });
                    context.waitTicks(3);
                    context.takeScreenshot("settings-" + name);
                    context.runOnClient(client -> {
                        if (TestScreens.current(client) instanceof SettingsScreen screen) {
                            screen.scrollToEnd();
                        }
                    });
                    context.waitTicks(2);
                    context.takeScreenshot("settings-" + name + "-end");
                    context.runOnClient(client -> TestScreens.open(client, null));
                    context.waitTicks(1);
                }
            }
        }
    }
}
