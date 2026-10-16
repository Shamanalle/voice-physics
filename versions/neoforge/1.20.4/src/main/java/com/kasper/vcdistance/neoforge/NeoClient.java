package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.HudOverlay;
import com.kasper.vcdistance.client.KeyMappings;
import com.kasper.vcdistance.client.MinecraftWorldAccess;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

/**
 * The client half on NeoForge 1.20.4: the same ticker, settings screen, HUD and keys as the Fabric
 * build, hooked in through NeoForge's events. The HUD is a GUI overlay (HUD layers came in 1.20.5).
 */
final class NeoClient {

    private static final String CATEGORY = "key.categories.vc-audio-distance";

    private static KeyMapping openSettingsKey;
    private static KeyMapping toggleHudKey;
    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    private NeoClient() {
    }

    static void init(IEventBus modBus) {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new MinecraftWorldAccess());
        openSettingsKey = KeyMappings.create("key.vc-audio-distance.open_settings", CATEGORY);
        toggleHudKey = KeyMappings.create("key.vc-audio-distance.toggle_hud", CATEGORY);
        AudioDistancePlugin.LINK.setAdminSender(text -> send(NeoNetworking.ADMIN, text));
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new AudioDistanceScreen(parent)));

        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            if (openSettingsKey != null) {
                e.register(openSettingsKey);
            }
            if (toggleHudKey != null) {
                e.register(toggleHudKey);
            }
        });
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll(
                new ResourceLocation("vc-audio-distance", "voice_hud"), (gui, graphics, partialTick, width, height) -> {
                    Minecraft mc = Minecraft.getInstance();
                    HudOverlay.paint(new GuiCanvas(graphics, mc.font), width, height,
                            mc.level != null && mc.player != null, mc.options.hideGui);
                }));

        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                tick(Minecraft.getInstance());
            }
        });
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

    /** Sends to a server that has the addon (the channel is optional). */
    private static void send(ResourceLocation id, String text) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.isConnected(id)) {
            NeoNetworking.sendToServer(id, text);
        }
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
            if (connection != null && client.player != null && !helloSent && connection.isConnected(NeoNetworking.HELLO)) {
                helloSent = true;
                NeoNetworking.sendToServer(NeoNetworking.HELLO, LinkProtocol.hello(BuildInfo.version()));
            }
            if (client.player != null && AudioDistancePlugin.LINK.consumeNotice()) {
                client.player.displayClientMessage(ClientHints.serverProfileMessage(AudioDistancePlugin.LINK.isEnforced()), false);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Server link tick failed: {}", t.toString());
        }
    }
}
