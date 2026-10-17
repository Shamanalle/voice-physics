package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.AudioDistanceLogScreen;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BlockRules;
import com.kasper.vcdistance.ChangeLog;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ServerSettings;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.VoiceState;
import com.kasper.vcdistance.client.SettingsScreen;
import com.kasper.vcdistance.client.SpeakerTicker;
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
    /** The store pictures: 4K at GUI scale 8 is the 1080p layout (480 x 270 units) with twice the pixels. */
    private static final int STORE_WIDTH = 3840;
    private static final int STORE_HEIGHT = 2160;
    private static final int STORE_GUI_SCALE = 8;

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
                if (language.equals("en_us")) {
                    shootStore(context);
                }
            }
        }
    }

    /**
     * Tells the client it is an admin of a server with a few settings changed and two zones, so the
     * Server tab has something to show. Commands from the tab go nowhere.
     */
    private static void pretendAdmin() {
        pretendAdmin(false);
    }

    /** @param plugin a Paper server with a few of its extras and the megaphone on, so those sections show too */
    private static void pretendAdmin(boolean plugin) {
        ServerSettings settings = new ServerSettings();
        if (plugin) {
            settings.setMegaphoneItem("minecraft:goat_horn");
        }
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
        java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
        if (plugin) {
            extra.put("plugin", "true");
            extra.put("server_radio", "true");
            extra.put("server_eavesdrop", "true");
        }
        AudioDistancePlugin.LINK.onAdminReply(LinkProtocol.adminReply(com.kasper.vcdistance.Jv.listOf(), settings, extra));
    }

    /**
     * A few players around you for the Monitor tab: two talking (one whispering), the rest quiet, one with
     * the sound off. Call it right before the shot: a voice counts as talking for a moment after its last frame.
     */
    private static void pretendMonitor() {
        SpeakerTicker.frozen = true;
        String[] names = {"Alex", "Steve", "Sam", "Kai", "Noor"};
        double[] distance = {6.4, 13.8, 21.5, 29.0, 37.2};
        double[] bearing = {-35, 70, 150, -110, 10};
        java.util.List<NearbyPlayers.Player> nearby = new java.util.ArrayList<>();
        java.util.Map<java.util.UUID, VoiceState> states = new java.util.LinkedHashMap<>();
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (6000 * Math.sin(i * 2 * Math.PI * 220 / 48000.0));
        }
        for (int i = 0; i < names.length; i++) {
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(("store-" + names[i]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            nearby.add(new NearbyPlayers.Player(id, names[i], distance[i], bearing[i]));
            states.put(id, i == 3 ? VoiceState.SOUND_OFF : VoiceState.CONNECTED);
            if (i < 2) {
                com.kasper.vcdistance.SpeakerRegistry.Speaker s = AudioDistancePlugin.SPEAKERS.onEntityFrame(id, id, i == 1, 48.0F, pcm);
                s.setDisplayName(names[i]);
                s.setDistance(distance[i], System.nanoTime());
                s.setBearing(bearing[i]);
            }
        }
        AudioDistancePlugin.NEARBY.update(nearby);
        AudioDistancePlugin.LINK.onNearby(LinkProtocol.nearby(states));
    }

    /**
     * A server with a long change log that answers the Log screen as a real one does, so the screen fills
     * its height and the pager has somewhere to go.
     */
    private static void pretendLog() {
        java.time.Instant now = java.time.Instant.now();
        String[][] rows = {
                {"Alex", "/vcd walls 60", "false"}, {"Alex", "/vcd walls 85", "true"},
                {ChangeLog.CONSOLE, "/vcd zone create spawn 8", "false"}, {"Steve", "/vcd rule sneak 0.5", "false"},
                {"Alex", "/vcd lock curve,walls,materials", "false"}, {"SomeoneWithALongName", "/vcd zone set arena-north range 24 echo 0.8 isolated on", "false"},
                {"Steve", "/vcd profile enforce", "false"}, {"Alex", "/vcd preset realistic", "true"},
                {ChangeLog.CONSOLE, "/vcd reload", "false"}, {"Steve", "/vcd walls 40", "false"},
                {"Alex", "/vcd megaphone item minecraft:goat_horn", "false"}, {"Steve", "/vcd zone set spawn notices on", "false"},
                {"Alex", "/vcd block add create:andesite_casing metal", "false"}, {ChangeLog.CONSOLE, "/vcd doppler on", "false"},
                {"Steve", "/vcd range 48", "false"}, {"Alex", "/vcd whisper 16", "false"},
        };
        java.util.List<ChangeLog.Entry> all = new java.util.ArrayList<>();
        for (int i = 0; i < 37; i++) {
            String[] row = rows[i % rows.length];
            all.add(new ChangeLog.Entry(now.minusSeconds(2700L * i), row[0], row[1], Boolean.parseBoolean(row[2])));
        }
        java.util.Map<String, String> state = new java.util.LinkedHashMap<>();
        state.put("undo", "3");
        state.put("logrows", String.valueOf(ChangeLog.MAX_ROWS));
        AudioDistancePlugin.LINK.setAdminSender(text -> {
            String command = LinkProtocol.parseAdminRequest(text);
            ChangeLog.Window window = command == null ? null : ChangeLog.parseWindow(command.trim().split("\\s+"));
            java.util.Map<String, String> reply = new java.util.LinkedHashMap<>(state);
            ChangeLog.writePage(window == null ? ChangeLog.page(all, new ChangeLog.View(1, null)) : ChangeLog.window(all, window), reply);
            AudioDistancePlugin.LINK.onAdminReply(LinkProtocol.adminReply(com.kasper.vcdistance.Jv.listOf(), new ServerSettings(), reply));
        });
        AudioDistancePlugin.LINK.onAdminReply(LinkProtocol.adminReply(com.kasper.vcdistance.Jv.listOf(), new ServerSettings(), state));
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
            context.runOnClient(client -> {
                TestScreens.open(client, null);
                SpeakerTicker.frozen = false;
            });
            context.waitTicks(1);
        }
        // The folded parts open: the materials, the Server tab's sections and one of its zones
        context.runOnClient(client -> {
            pretendAdmin(true);
            AudioDistancePlugin.CONFIG.setBlockRules(BlockRules.parse("create:andesite_casing=metal,#c:glass_blocks=glass,mc:x=door"));
            SettingsScreen.unfoldAll();
            SettingsScreen.pickZone("arena-north");
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
            // Page by page, so every section is seen once
            for (int page = 2; page < 12 && context.computeOnClient(client ->
                    TestScreens.current(client) instanceof SettingsScreen screen && screen.scrollPage()); page++) {
                context.waitTicks(2);
                context.takeScreenshot("settings-" + name + "-" + page);
            }
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

    /**
     * The pictures for the store pages and the README: one look (3840 x 2160, English, no game HUD or
     * chat behind the screen), named {@code store-<name>}. The folded parts are still open from the shots above.
     */
    private static void shootStore(ClientGameTestContext context) {
        context.getInput().resizeWindow(STORE_WIDTH, STORE_HEIGHT);
        context.waitTicks(4);
        context.runOnClient(client -> TestScreens.guiScale(client, STORE_GUI_SCALE));
        context.waitTicks(2);
        context.runOnClient(client -> {
            // Only the screen in the picture: no hotbar, hearts or chat behind it
            TestScreens.hideHud(client, true);
            TestScreens.clearChat(client);
            pretendAdmin();
            pretendLog();
            // The optional effects on, so the Effects tab shows their sliders
            AudioDistancePlugin.CONFIG.setDopplerEnabled(true);
            AudioDistancePlugin.CONFIG.setDirectivityEnabled(true);
        });
        for (SettingsScreen.Tab tab : new SettingsScreen.Tab[]{SettingsScreen.Tab.DISTANCE, SettingsScreen.Tab.WALLS,
                SettingsScreen.Tab.EFFECTS, SettingsScreen.Tab.HUD, SettingsScreen.Tab.MONITOR, SettingsScreen.Tab.SERVER}) {
            context.runOnClient(client -> {
                SettingsScreen.openOn(tab);
                TestScreens.open(client, new AudioDistanceScreen(null));
            });
            parkCursor(context);
            if (tab == SettingsScreen.Tab.MONITOR) {
                context.runOnClient(client -> pretendMonitor());
            }
            context.takeScreenshot("store-" + tab.name().toLowerCase(java.util.Locale.ROOT));
            context.runOnClient(client -> TestScreens.open(client, null));
            context.waitTicks(1);
        }
        context.runOnClient(client -> TestScreens.open(client, new AudioDistanceLogScreen(null)));
        parkCursor(context);
        context.takeScreenshot("store-log");
        context.runOnClient(client -> TestScreens.open(client, null));
        context.waitTicks(1);
        // A server that enforces its profile: the curve and the walls are locked
        context.runOnClient(client -> {
            TestScreens.clearChat(client);
            ServerSettings settings = new ServerSettings();
            settings.setProfileMode(ServerSettings.ProfileMode.ENFORCE);
            settings.setLockedParts(java.util.EnumSet.of(DistanceConfig.Part.CURVE, DistanceConfig.Part.WALLS));
            settings.setWallsStrength(0.7);
            AudioDistancePlugin.LINK.onProfile(LinkProtocol.profile(settings, null, 32.0, 12.0, false));
            SettingsScreen.openOn(SettingsScreen.Tab.DISTANCE);
            TestScreens.open(client, new AudioDistanceScreen(null));
        });
        context.runOnClient(client -> TestScreens.clearChat(client));
        parkCursor(context);
        context.takeScreenshot("store-locked");
        context.runOnClient(client -> {
            TestScreens.open(client, null);
            AudioDistancePlugin.LINK.reset();
            AudioDistancePlugin.CONFIG.setDopplerEnabled(false);
            AudioDistancePlugin.CONFIG.setDirectivityEnabled(false);
            TestScreens.hideHud(client, false);
            // Automatic again for the shots that follow
            TestScreens.guiScale(client, 0);
        });
        context.waitTicks(1);
    }

    /** Moves the pointer to an empty corner, so no tooltip or hover highlight is in the picture. */
    private static void parkCursor(ClientGameTestContext context) {
        context.waitTicks(3);
        context.getInput().setCursorPos(STORE_WIDTH - 40, STORE_HEIGHT - 20);
        context.waitTicks(3);
    }
}
