package com.kasper.vcdistance.server;

import com.kasper.vcdistance.AdminCommands;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.ServerPlayers;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.ZoneOutlines;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What the server side needs from Minecraft on Fabric and NeoForge (both use Minecraft's own
 * classes): players as the voice rules see them, admin rights, zones and the command context.
 */
public final class ServerBridge {

    /** How far {@code /vcd zone pos1 look} reaches, in blocks. */
    static final double LOOK_REACH = 64.0;

    private ServerBridge() {
    }

    /** A player as the voice rules see them. */
    public static ServerPlayers.Info info(ServerPlayer p) {
        return new ServerPlayers.Info(p.getUUID(), p.getName().getString(),
                ServerZones.dimensionId(String.valueOf(p.level().dimension())),
                p.getX(), p.getY(), p.getZ(),
                p.isShiftKeyDown(), p.isAlive(), p.isSpectator(),
                item(p.getMainHandItem()), item(p.getOffhandItem()), List.of(), PlayerLanguage.of(p));
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * Server tick: draws zone borders an admin asked to see, refreshes the players for the voice rules
     * and carries out the addon requirement.
     */
    public static void tick(MinecraftServer server) {
        ZoneOutlines.tick((id, world, points) -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && Zone.sameWorld(ServerZones.dimensionId(String.valueOf(p.level().dimension())), world)) {
                ParticleSender.endRods(p, points);
            }
        });
        if (!ServerHooks.refreshDue()) {
            return;
        }
        List<ServerPlayers.Info> online = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                online.add(info(p));
            } catch (Throwable ignored) {
                // A player half-way through joining or leaving
            }
        }
        ServerHooks.refresh(online, new ServerHooks.Platform() {
            @Override
            public void message(UUID player, String text) {
                ServerPlayer p = server.getPlayerList().getPlayer(player);
                if (p != null) {
                    p.sendSystemMessage(ChatLink.of(text));
                }
            }

            @Override
            public void kick(UUID player, String text) {
                ServerPlayer p = server.getPlayerList().getPlayer(player);
                if (p != null) {
                    p.connection.disconnect(Component.literal(text));
                }
            }
        });
    }

    /** Whether the player may use any part of /vcd (and so gets the Server tab). */
    public static boolean isAdmin(ServerPlayer player) {
        try {
            return VcdPermissions.any(player.createCommandSourceStack());
        } catch (Throwable t) {
            return false;
        }
    }

    /** The zone a player is in right now, or {@code null}. */
    public static Zone zoneOf(ServerPlayer player) {
        return AudioDistancePlugin.SERVER_SETTINGS.zoneOf(info(player));
    }

    /** "NeoForge" or "Fabric", for /vcd status. */
    public static String platform() {
        if (hasClass("net.neoforged.fml.common.Mod")) {
            return "NeoForge";
        }
        return hasClass("net.minecraftforge.fml.common.Mod") ? "Forge" : "Fabric";
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** What /vcd needs from the server, for a command run from {@code source} (a player, the console, a command block). */
    public static AdminCommands.Context context(CommandSourceStack source, VcdCommand.Server hooks) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        UUID sender = player == null ? null : player.getUUID();
        return new AdminCommands.Context() {
            @Override
            public String platform() {
                return ServerBridge.platform();
            }

            @Override
            public int onlinePlayers() {
                return server.getPlayerCount();
            }

            @Override
            public int addonPlayers() {
                int n = 0;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (AudioDistancePlugin.SERVER_WALLS.hasAddon(p.getUUID())) {
                        n++;
                    }
                }
                return n;
            }

            @Override
            public void resendProfiles() {
                hooks.resendProfiles(server);
            }

            @Override
            public UUID sender() {
                return sender;
            }

            @Override
            public boolean allows(String permission) {
                return VcdPermissions.allows(source, permission);
            }

            @Override
            public int[] targetBlock() {
                return player == null ? null : lookedAt(player);
            }

            @Override
            public boolean teleport(String world, double x, double y, double z) {
                return sender != null && ServerBridge.teleport(server, sender, world, x, y, z);
            }

            @Override
            public Collection<String> worlds() {
                List<String> out = new ArrayList<>();
                try {
                    for (ServerLevel level : server.getAllLevels()) {
                        out.add(ServerZones.dimensionId(String.valueOf(level.dimension())));
                    }
                } catch (Throwable ignored) {
                    // no suggestions then
                }
                return out;
            }
        };
    }

    /** The block the player looks at, up to {@link #LOOK_REACH} blocks away, or {@code null}. */
    static int[] lookedAt(ServerPlayer player) {
        try {
            HitResult hit = player.pick(LOOK_REACH, 1.0F, false);
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = block.getBlockPos();
                return new int[]{pos.getX(), pos.getY(), pos.getZ()};
            }
        } catch (Throwable ignored) {
            // this version has no pick(): say nothing is in sight
        }
        return null;
    }

    /**
     * Moves a player with the game's own command, which every version has in the same form:
     * {@code execute in <dimension> run tp <player> x y z}.
     */
    static boolean teleport(MinecraftServer server, UUID player, String world, double x, double y, double z) {
        String dimension = world.indexOf(':') >= 0 ? world : "minecraft:" + world;
        String command = String.format(Locale.ROOT, "execute in %s run tp %s %.2f %.2f %.2f", dimension, player, x, y, z);
        try {
            if (server.getPlayerList().getPlayer(player) == null) {
                return false;
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * A command from a player's Server tab: runs it when the player is an admin.
     *
     * @return the reply to send back, or {@code null}
     */
    public static String admin(ServerPlayer player, String text, VcdCommand.Server hooks) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return null;
        }
        // The command needs the admin's position (zone pos1...): refresh them first
        AudioDistancePlugin.PLAYERS.update(info(player));
        return ServerHooks.admin(player.getUUID(), text, isAdmin(player), context(player.createCommandSourceStack(), hooks));
    }
}
