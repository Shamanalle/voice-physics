package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Opens the settings screen in a real client, in a world, on every tab and in two window sizes, scrolls
 * each tab to its end and takes screenshots (uploaded by CI). With the automatic interface size,
 * 854 x 480 gives a 427 x 240 screen and 1280 x 960 the tightest one, 320 x 240. Any exception while
 * the screen is built or drawn fails the test.
 */
public class SettingsScreenGameTest implements FabricClientGameTest {

    private static final int[][] WINDOWS = {{854, 480}, {1280, 960}};

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(40);
            for (int[] window : WINDOWS) {
                context.getInput().resizeWindow(window[0], window[1]);
                context.waitTicks(2);
                String size = window[0] + "x" + window[1];
                for (SettingsScreen.Tab tab : SettingsScreen.Tab.values()) {
                    if (tab == SettingsScreen.Tab.SERVER) {
                        continue; // only for admins of a server with the addon
                    }
                    String name = tab.name().toLowerCase(java.util.Locale.ROOT) + "-" + size;
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
