package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.ExtractorCanvas;
import com.kasper.vcdistance.client.HudOverlay;
import com.kasper.vcdistance.client.ModernWorldAccess;
import com.kasper.vcdistance.client.ScreenSwitch;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.EventNetworkChannel;

/**
 * The client half on Forge 26.x: the same ticker, settings screen, HUD and keys as the Fabric build,
 * hooked in through Forge's events.
 */
final class ForgeClient {

    // Forge registers no key categories of its own, so the keys go under Miscellaneous
    private static final KeyMapping OPEN_SETTINGS_KEY =
            new KeyMapping("key.vc-audio-distance.open_settings", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    private static final KeyMapping TOGGLE_HUD_KEY =
            new KeyMapping("key.vc-audio-distance.toggle_hud", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);

    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    private ForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new ModernWorldAccess());
        AudioDistancePlugin.LINK.setAdminSender(text -> send(ForgeNetworking.ADMIN, text));
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new AudioDistanceScreen(parent)));

        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            e.register(OPEN_SETTINGS_KEY);
            e.register(TOGGLE_HUD_KEY);
        });
        AddGuiOverlayLayersEvent.BUS.addListener(e -> e.getLayeredDraw().add(
                ForgeCompat.id("vc-audio-distance", "voice_hud"), (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    HudOverlay.paint(new ExtractorCanvas(graphics, mc.font), graphics.guiWidth(), graphics.guiHeight(),
                            mc.level != null && mc.player != null, false);
                }));

        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> tick(Minecraft.getInstance()));
        // /voicephysics opens the settings (also from the clickable link in chat)
        RegisterClientCommandsEvent.BUS.addListener(e -> e.getDispatcher().register(ClientHints.openCommand()));
        ScreenEvent.Init.Post.BUS.addListener(e -> {
            if (!SvcSettingsButton.isSvcSettings(e.getScreen())) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            e.addListener(Button.builder(Component.translatable("message.vc-audio-distance.button"),
                            b -> ScreenSwitch.open(client, new AudioDistanceScreen(e.getScreen())))
                    .bounds(SvcSettingsButton.x(e.getScreen().width), SvcSettingsButton.y(e.getScreen().height),
                            SvcSettingsButton.WIDTH, SvcSettingsButton.HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("message.vc-audio-distance.button.tooltip"))).build());
        });
    }

    private static void send(EventNetworkChannel channel, String text) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            ForgeNetworking.send(channel, connection.getConnection(), text);
        }
    }

    private static void tick(Minecraft client) {
        ticker.tick();
        tickServerLink(client);
        if (ClientHints.consumeOpenRequest(ScreenSwitch.current(client) != null)) {
            ScreenSwitch.open(client, new AudioDistanceScreen(null));
        }
        while (OPEN_SETTINGS_KEY.consumeClick()) {
            ScreenSwitch.open(client, new AudioDistanceScreen(ScreenSwitch.current(client)));
        }
        while (TOGGLE_HUD_KEY.consumeClick()) {
            ClientHints.cycleHud();
        }
        ClientHints.tickNotices();
        ClientHints.tickWelcome(client.player != null && client.level != null, OPEN_SETTINGS_KEY,
                message -> client.player.sendSystemMessage(message));
    }

    /** Says hello to servers that have the addon and announces their profile once. */
    private static void tickServerLink(Minecraft client) {
        try {
            ClientPacketListener connection = client.getConnection();
            if (connection != lastConnection) {
                lastConnection = connection;
                helloSent = false;
                AudioDistancePlugin.LINK.reset();
            }
            // Servers without the addon ignore a message on a channel they do not know
            if (connection != null && client.player != null && !helloSent) {
                helloSent = true;
                send(ForgeNetworking.HELLO, LinkProtocol.hello(BuildInfo.version()));
            }
            if (client.player != null && AudioDistancePlugin.LINK.consumeNotice()) {
                client.player.sendSystemMessage(ClientHints.serverProfileMessage(AudioDistancePlugin.LINK.isEnforced()));
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Server link tick failed: {}", t.toString());
        }
    }
}
