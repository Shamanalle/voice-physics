package com.kasper.vcdistance.forge;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge entrypoint (Minecraft 1.19.4): the full addon, as on Fabric. The client and server code is
 * shared with the Fabric builds; only the hooks into Forge live in this package. The Simple Voice
 * Chat plugin itself is found through {@code @ForgeVoicechatPlugin}.
 */
@Mod(AudioDistanceForge.MOD_ID)
public final class AudioDistanceForge {

    public static final String MOD_ID = "vc_audio_distance";
    private static final int RELOAD_CHECK_TICKS = 40;

    private final ServerThickness thickness = new ServerThickness();
    private int ticks;

    @SuppressWarnings("removal")
    public AudioDistanceForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgeNetworking.register();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> AudioDistancePlugin.ensureServerSettings());
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                VcdCommand.register(e.getDispatcher(), AdminPermission::isAdmin, AudioDistanceForge::resendProfiles));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> ServerHooks.joined(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerHooks.left(e.getEntity().getUUID()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(modBus);
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
                    ForgeNetworking.sendNearby(player);
                    Zone zone = ServerZones.of(player);
                    if (AudioDistancePlugin.ZONES.changed(player.getUUID(), zone)) {
                        ForgeNetworking.sendProfile(player, zone);
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
                ForgeNetworking.sendProfile(player, zone);
            }
        }
    }
}
