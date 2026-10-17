package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.AdminPermission;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerThickness;
import com.kasper.vcdistance.server.ServerZones;
import com.kasper.vcdistance.server.VcdCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * NeoForge entrypoint (Minecraft 1.20.2 - 1.20.3): the full addon, as on Fabric. The client and server code
 * is shared with the other builds; only the hooks into NeoForge live in this package. The Simple Voice
 * Chat plugin itself is found through {@code @ForgeVoicechatPlugin}.
 */
@Mod(AudioDistanceNeoForge.MOD_ID)
public final class AudioDistanceNeoForge {

    public static final String MOD_ID = "vc_audio_distance";
    private static final int RELOAD_CHECK_TICKS = 40;

    private final ServerThickness thickness = new ServerThickness();
    private int ticks;

    public AudioDistanceNeoForge(IEventBus modBus) {
        NeoNetworking.register();
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> AudioDistancePlugin.ensureServerSettings());
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                VcdCommand.register(e.getDispatcher(), AdminPermission::isAdmin, AudioDistanceNeoForge::resendProfiles));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> ServerHooks.joined(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerHooks.left(e.getEntity().getUUID()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoClient.init(modBus);
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        ServerBridge.tick(server);
        ++ticks;
        if (ticks % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                    NeoNetworking.sendNearby(player);
                    Zone zone = ServerZones.of(player);
                    if (AudioDistancePlugin.ZONES.changed(player.getUUID(), zone)) {
                        NeoNetworking.sendProfile(player, zone);
                    }
                }
            }
        }
        if (ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            resendProfiles(server);
        }
    }

    static void resendProfiles(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                Zone zone = ServerZones.of(player);
                AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
                NeoNetworking.sendProfile(player, zone);
            }
        }
    }
}
