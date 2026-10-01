package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.compat.Btn;
import com.kasper.vcdistance.compat.Tip;
import com.kasper.vcdistance.compat.Txt;
import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.HudOverlay;
import com.kasper.vcdistance.client.KeyMappings;
import com.kasper.vcdistance.client.MinecraftWorldAccess;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * The client half on Forge: the same ticker, settings screen, HUD and keys as the Fabric build,
 * hooked in through Forge's events.
 */
final class ForgeClient {

    private static final String CATEGORY = "key.categories.vc-audio-distance";

    private static KeyMapping openSettingsKey;
    private static KeyMapping toggleHudKey;
    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    private ForgeClient() {
    }

    static void init() {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new MinecraftWorldAccess());
        openSettingsKey = KeyMappings.create("key.vc-audio-distance.open_settings", CATEGORY);
        toggleHudKey = KeyMappings.create("key.vc-audio-distance.toggle_hud", CATEGORY);
        AudioDistancePlugin.LINK.setAdminSender(text -> send(ForgeNetworking.ADMIN, text));
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, parent) -> new AudioDistanceScreen(parent));

        ClientRegistry.registerKeyBinding(openSettingsKey);
        ClientRegistry.registerKeyBinding(toggleHudKey);
        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post e) -> {
            if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            HudOverlay.paint(new GuiCanvas(e.getMatrixStack(), mc.font), e.getWindow().getGuiScaledWidth(),
                    e.getWindow().getGuiScaledHeight(), mc.level != null && mc.player != null, mc.options.hideGui);
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                tick(Minecraft.getInstance());
            }
        });
        // Forge 1.16.5 has no client commands: the chat message is taken before it is sent
        CommandDispatcher<Object> commands = new CommandDispatcher<>();
        commands.register(ClientHints.<Object>openCommand());
        MinecraftForge.EVENT_BUS.addListener((ClientChatEvent e) -> {
            String message = e.getMessage();
            String prefix = "/" + ClientHints.COMMAND;
            if (!message.equals(prefix) && !message.startsWith(prefix + " ")) {
                return;
            }
            e.setCanceled(true);
            Minecraft client = Minecraft.getInstance();
            client.gui.getChat().addRecentChat(message);
            try {
                commands.execute(message.substring(1), new Object());
            } catch (CommandSyntaxException ex) {
                if (client.player != null) {
                    client.player.displayClientMessage(Txt.literal(ex.getMessage()), false);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((GuiScreenEvent.InitGuiEvent.Post e) -> {
            if (!SvcSettingsButton.isSvcSettings(e.getGui())) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            e.addWidget(Btn.builder(Txt.translatable("message.vc-audio-distance.button"),
                            b -> client.setScreen(new AudioDistanceScreen(e.getGui())))
                    .bounds(SvcSettingsButton.x(e.getGui().width), SvcSettingsButton.y(e.getGui().height),
                            SvcSettingsButton.WIDTH, SvcSettingsButton.HEIGHT)
                    .tooltip(Tip.create(Txt.translatable("message.vc-audio-distance.button.tooltip"))).build());
        });
    }

    private static void send(net.minecraft.resources.ResourceLocation channel, String text) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(channel, ForgeNetworking.write(text)));
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
                send(ForgeNetworking.HELLO, LinkProtocol.hello(BuildInfo.version()));
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
