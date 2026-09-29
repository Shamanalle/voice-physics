package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.AudioDistanceLogScreen;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BlockRules;
import com.kasper.vcdistance.ChangeLog;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ServerSettings;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Opens the settings screen in a real client, in a world, on every tab (the Server tab as a pretend admin), in English and in Russian (its
 * texts are among the longest) and in two window sizes, scrolls each tab to its end and takes
 * screenshots (uploaded by CI). With the automatic interface size,
 * 854 x 480 gives a 427 x 240 screen and 1280 x 960 the tightest one, 320 x 240. Any exception while
 * the screen is built or drawn fails the test.
 */
public class SettingsScreenGameTest implements FabricClientGameTest {

    private static final int[][] WINDOWS = {{854, 480}, {1280, 960}};
    private static final String[] LANGUAGES = {"en_us", "ru_ru"};

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(40);
            for (String language : LANGUAGES) {
                context.runOnClient(client -> TestScreens.language(client, language));
                context.waitTicks(5);
                context.waitFor(TestScreens::loaded, 1200);
                // The loading screen fades out after the reload
                context.waitTicks(60);
                for (int[] window : WINDOWS) {
                    shootTabs(context, language, window);
                }
            }
        }
    }

    /**
     * Tells the client it is an admin of a server with a few settings changed and two zones, so the
     * Server tab has something to show. Commands from the tab go nowhere.
     */
    private static void pretendAdmin() {
        ServerSettings settings = new ServerSettings();
        settings.setSneakMultiplier(0.5);
        settings.setGroupSpectatorsApart(true);
        settings.setWallsStrength(0.55);
        settings.setBlockRules(BlockRules.parse("create:andesite_casing=metal,#c:glass_blocks=glass,some_mod:a_block_with_a_long_name=wool"));
        settings.putZone(new Zone(Zone.BOX, "spawn", null, null, Zone.Rules.NONE,
                new Zone.Box("minecraft:overworld", -8, 60, -8, 8, 80, 8), 0));
        settings.putZone(new Zone(Zone.BOX, "arena-north", null, null,
                new Zone.Rules(24.0, null, null, 0.8, null, true, null),
                new Zone.Box("minecraft:overworld", 100, 60, 100, 140, 90, 140), 0));
        AudioDistancePlugin.LINK.setAdminSender(text -> { });
        AudioDistancePlugin.LINK.onProfile(LinkProtocol.profile(settings, null, 48.0, 16.0, true));
        AudioDistancePlugin.LINK.onAdminReply(LinkProtocol.adminReply(java.util.List.of(), settings));
    }

    /** A page of the change log as the server would send it, so the Log screen has lines to show. */
    private static void pretendLog() {
        java.time.Instant now = java.time.Instant.now();
        java.util.List<ChangeLog.Entry> entries = new java.util.ArrayList<>();
        String[][] rows = {
                {"Alex", "/vcd walls 60", "false"}, {"Alex", "/vcd walls 85", "true"},
                {ChangeLog.CONSOLE, "/vcd zone create spawn 8", "false"}, {"Steve", "/vcd rule sneak 0.5", "false"},
                {"Alex", "/vcd lock curve,walls,materials", "false"}, {"SomeoneWithALongName", "/vcd zone set arena-north range 24 echo 0.8 isolated on", "false"},
                {"Steve", "/vcd profile enforce", "false"}, {"Alex", "/vcd preset realistic", "true"},
                {ChangeLog.CONSOLE, "/vcd reload", "false"}, {"Steve", "/vcd walls 40", "false"},
        };
        for (int i = 0; i < rows.length; i++) {
            entries.add(new ChangeLog.Entry(now.minusSeconds(3600L * i), rows[i][0], rows[i][1], Boolean.parseBoolean(rows[i][2])));
        }
        java.util.Map<String, String> state = new java.util.LinkedHashMap<>();
        state.put("undo", "3");
        ChangeLog.writePage(new ChangeLog.Page(entries, 1, 4, 37, ""), state);
        AudioDistancePlugin.LINK.onAdminReply(LinkProtocol.adminReply(java.util.List.of(), new ServerSettings(), state));
    }

    private static void shootTabs(ClientGameTestContext context, String language, int[] window) {
        context.getInput().resizeWindow(window[0], window[1]);
        context.waitTicks(2);
        String size = language + "-" + window[0] + "x" + window[1];
        for (SettingsScreen.Tab tab : SettingsScreen.Tab.values()) {
            if (tab == SettingsScreen.Tab.SERVER) {
                // Only for admins of a server with the addon: the client is told it is one
                context.runOnClient(client -> pretendAdmin());
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
        // The folded parts open: the curve's tuning, the materials, the Server tab's sections
        context.runOnClient(client -> {
            pretendAdmin();
            AudioDistancePlugin.CONFIG.setBlockRules(BlockRules.parse("create:andesite_casing=metal,#c:glass_blocks=glass,mc:x=door"));
            SettingsScreen.unfoldAll();
        });
        for (SettingsScreen.Tab tab : new SettingsScreen.Tab[]{SettingsScreen.Tab.DISTANCE, SettingsScreen.Tab.WALLS,
                SettingsScreen.Tab.SERVER}) {
            String name = tab.name().toLowerCase(java.util.Locale.ROOT) + "-open-" + size;
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
        // The change log on its own screen, with a page of changes
        context.runOnClient(client -> {
            pretendAdmin();
            pretendLog();
            TestScreens.open(client, new AudioDistanceLogScreen(null));
        });
        context.waitTicks(4);
        context.takeScreenshot("log-" + size);
        context.runOnClient(client -> TestScreens.open(client, null));
        context.waitTicks(1);
    }
}
