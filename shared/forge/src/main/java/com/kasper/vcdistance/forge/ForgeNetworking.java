package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerZones;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;

import java.util.function.Consumer;

/**
 * The client-server link on Forge 1.20.6 and newer: the same channels and the same bytes (one UTF-8
 * string with its length) as on Fabric, NeoForge and Paper, so a Forge client works with any of those
 * servers and the other way round. Every channel accepts a missing other side.
 */
final class ForgeNetworking {

    static final EventNetworkChannel HELLO = channel(LinkProtocol.HELLO);
    static final EventNetworkChannel PROFILE = channel(LinkProtocol.PROFILE);
    static final EventNetworkChannel NEARBY = channel(LinkProtocol.NEARBY);
    static final EventNetworkChannel ADMIN = channel(LinkProtocol.ADMIN);
    static final EventNetworkChannel ADMIN_REPLY = channel(LinkProtocol.ADMIN_REPLY);

    private ForgeNetworking() {
    }

    private static EventNetworkChannel channel(String path) {
        return ChannelBuilder.named(ForgeCompat.id(LinkProtocol.NAMESPACE, path)).optional().eventNetworkChannel();
    }

    static void register() {
        listen(HELLO, ForgeNetworking::onHello, null);
        listen(ADMIN, ForgeNetworking::onAdmin, null);
        listen(PROFILE, null, text -> AudioDistancePlugin.LINK.onProfile(text));
        listen(NEARBY, null, text -> AudioDistancePlugin.LINK.onNearby(text));
        listen(ADMIN_REPLY, null, text -> AudioDistancePlugin.LINK.onAdminReply(text));
    }

    private interface ServerHandler {
        void handle(ServerPlayer player, String text);
    }

    private static void listen(EventNetworkChannel channel, ServerHandler server, Consumer<String> client) {
        channel.addListener((CustomPayloadEvent e) -> {
            CustomPayloadEvent.Context context = e.getSource();
            String text = read(e.getPayload());
            if (text != null) {
                if (context.isServerSide() && server != null) {
                    ServerPlayer player = context.getSender();
                    if (player != null) {
                        context.enqueueWork(() -> server.handle(player, text));
                    }
                } else if (context.isClientSide() && client != null) {
                    client.accept(text);
                }
            }
            context.setPacketHandled(true);
        });
    }

    private static String read(FriendlyByteBuf buf) {
        try {
            return buf == null ? null : buf.readUtf(LinkProtocol.MAX_LENGTH);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static FriendlyByteBuf write(String text) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(text, LinkProtocol.MAX_LENGTH);
        return buf;
    }

    /** Sends one message; a connection that cannot take it (the other side lacks the addon) is skipped. */
    static void send(EventNetworkChannel channel, Connection connection, String text) {
        if (connection != null) {
            channel.send(write(text), connection);
        }
    }

    private static void send(ServerPlayer player, EventNetworkChannel channel, String text) {
        send(channel, player.connection.getConnection(), text);
    }

    private static void onHello(ServerPlayer player, String text) {
        if (!ServerHooks.hello(player.getUUID(), text)) {
            return;
        }
        Zone zone = ServerZones.of(player);
        AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
        sendProfile(player, zone);
    }

    /** Sent only to players who said hello, so clients without the addon never get unknown packets. */
    static void sendProfile(ServerPlayer player, Zone zone) {
        send(player, PROFILE, AudioDistancePlugin.serverProfileMessage(zone, ServerBridge.isAdmin(player)));
    }

    /** A command from the player's Server tab; answered only for admins. */
    private static void onAdmin(ServerPlayer player, String text) {
        String reply = ServerBridge.admin(player, text, AudioDistanceForge::resendProfiles);
        if (reply != null) {
            send(player, ADMIN_REPLY, reply);
        }
    }

    static void sendNearby(ServerPlayer player) {
        String text = AudioDistancePlugin.nearbyMessage(player, ForgeNetworking::visible);
        if (text != null) {
            send(player, NEARBY, text);
        }
    }

    /** Spectators are listed only to other spectators. */
    private static boolean visible(Object viewer, Object other) {
        return !((ServerPlayer) other).isSpectator() || ((ServerPlayer) viewer).isSpectator();
    }
}
