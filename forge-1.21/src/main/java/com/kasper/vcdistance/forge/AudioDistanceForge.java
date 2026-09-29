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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge entrypoint for Minecraft 1.21 - 1.21.1: the shared one (shared/forge-bus6) with a constructor
 * without arguments, as Forge 51 (1.21) creates mods only that way; the mod context comes from
 * {@code FMLJavaModLoadingContext.get()}, which Forge 52 still has.
 */
@Mod(AudioDistanceForge.MOD_ID)
public final class AudioDistanceForge {

    public static final String MOD_ID = "vc_audio_distance";
    private static final int RELOAD_CHECK_TICKS = 40;

    private final ServerThickness thickness = new ServerThickness();
    private int ticks;

    @SuppressWarnings("removal")
    public AudioDistanceForge() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        ForgeNetworking.register();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> AudioDistancePlugin.ensureServerSettings());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post e) -> onServerTick());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                VcdCommand.register(e.getDispatcher(), AdminPermission::isAdmin, AudioDistanceForge::resendProfiles));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> ServerHooks.joined(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerHooks.left(e.getEntity().getUUID()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.init(context);
        }
    }

    private void onServerTick() {
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
