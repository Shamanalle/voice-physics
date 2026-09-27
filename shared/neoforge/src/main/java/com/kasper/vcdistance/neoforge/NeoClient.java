package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.HudOverlay;
import com.kasper.vcdistance.client.MinecraftWorldAccess;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half on NeoForge 1.20.5 - 1.21.11: the same ticker, settings screen, HUD and keys as the
 * Fabric build, hooked in through NeoForge's events.
 */
final class NeoClient {

    private static KeyMapping openSettingsKey;
    private static KeyMapping toggleHudKey;
    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    private NeoClient() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new MinecraftWorldAccess());
        openSettingsKey = NeoCompat.key("key.vc-audio-distance.open_settings");
        toggleHudKey = NeoCompat.key("key.vc-audio-distance.toggle_hud");
        AudioDistancePlugin.LINK.setAdminSender(text -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null && connection.hasChannel(NeoNetworking.Admin.TYPE)) {
                NeoCompat.sendToServer(new NeoNetworking.Admin(text));
            }
        });
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new AudioDistanceScreen(parent));

        modBus.addListener((RegisterKeyMappingsEvent e) -> NeoCompat.registerKeys(e, openSettingsKey, toggleHudKey));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(
                NeoCompat.id("vc-audio-distance", "voice_hud"), (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    HudOverlay.paint(new GuiCanvas(graphics, mc.font), graphics.guiWidth(), graphics.guiHeight(),
                            mc.level != null && mc.player != null, mc.options.hideGui);
                }));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(ClientHints.openCommand()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            if (!SvcSettingsButton.isSvcSettings(e.getScreen())) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            e.addListener(Button.builder(Component.translatable("message.vc-audio-distance.button"),
                            b -> client.setScreen(new AudioDistanceScreen(e.getScreen())))
                    .bounds(SvcSettingsButton.x(e.getScreen().width), SvcSettingsButton.y(e.getScreen().height),
                            SvcSettingsButton.WIDTH, SvcSettingsButton.HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("message.vc-audio-distance.button.tooltip"))).build());
        });
    }

    private static void tick(Minecraft client) {
        ticker.tick();
        tickServerLink(client);
        if (ClientHints.consumeOpenRequest(client.screen != null)) {
            client.setScreen(new AudioDistanceScreen(null));
        }
        if (openSettingsKey != null) {
            while (openSettingsKey.consumeClick()) {
                client.setScreen(new AudioDistanceScreen(client.screen));
            }
        }
        if (toggleHudKey != null) {
            while (toggleHudKey.consumeClick()) {
                ClientHints.cycleHud();
            }
        }
        ClientHints.tickNotices();
        ClientHints.tickWelcome(client.player != null && client.level != null, openSettingsKey,
                message -> client.player.displayClientMessage(message, false));
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
            if (connection != null && !helloSent && connection.hasChannel(NeoNetworking.Hello.TYPE)) {
                NeoCompat.sendToServer(new NeoNetworking.Hello(LinkProtocol.hello(BuildInfo.version())));
                helloSent = true;
            }
            if (client.player != null && AudioDistancePlugin.LINK.consumeNotice()) {
                client.player.displayClientMessage(ClientHints.serverProfileMessage(AudioDistancePlugin.LINK.isEnforced()), false);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Server link tick failed: {}", t.toString());
        }
    }
}
