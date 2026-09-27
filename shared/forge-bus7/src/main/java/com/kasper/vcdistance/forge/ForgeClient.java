package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.MinecraftWorldAccess;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.EventNetworkChannel;

/**
 * The client half on Forge 1.21.6 - 1.21.11: the same ticker, settings screen, HUD and keys as the
 * Fabric build, hooked in through Forge's events.
 */
final class ForgeClient {

    private static KeyMapping openSettingsKey;
    private static KeyMapping toggleHudKey;
    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    private ForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new MinecraftWorldAccess());
        openSettingsKey = ForgeCompat.key("key.vc-audio-distance.open_settings");
        toggleHudKey = ForgeCompat.key("key.vc-audio-distance.toggle_hud");
        AudioDistancePlugin.LINK.setAdminSender(text -> send(ForgeNetworking.ADMIN, text));
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new AudioDistanceScreen(parent)));

        RegisterKeyMappingsEvent.getBus(modBus).addListener(e -> {
            if (openSettingsKey != null) {
                e.register(openSettingsKey);
            }
            if (toggleHudKey != null) {
                e.register(toggleHudKey);
            }
        });
        try {
            ForgeHud.register(modBus);
        } catch (LinkageError e) {
            // Forge for 1.21.6 - 1.21.7 has no HUD layers event yet: everything but the voice HUD works
            DistanceConfig.LOGGER.info("This Forge cannot add HUD layers, the voice HUD is off: {}", e.toString());
        }

        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> tick(Minecraft.getInstance()));
        RegisterClientCommandsEvent.BUS.addListener(e -> e.getDispatcher().register(ClientHints.openCommand()));
        ScreenEvent.Init.Post.BUS.addListener(e -> {
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

    private static void send(EventNetworkChannel channel, String text) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            ForgeNetworking.send(channel, connection.getConnection(), text);
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
            // Servers without the addon ignore a message on a channel they do not know
            if (connection != null && client.player != null && !helloSent) {
                helloSent = true;
                send(ForgeNetworking.HELLO, LinkProtocol.hello(BuildInfo.version()));
            }
            if (client.player != null && AudioDistancePlugin.LINK.consumeNotice()) {
                client.player.displayClientMessage(ClientHints.serverProfileMessage(AudioDistancePlugin.LINK.isEnforced()), false);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Server link tick failed: {}", t.toString());
        }
    }
}
