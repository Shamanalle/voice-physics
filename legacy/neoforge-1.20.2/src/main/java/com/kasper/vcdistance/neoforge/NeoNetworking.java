package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerZones;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.custom.payload.SimplePayload;
import net.neoforged.neoforge.network.event.EventNetworkChannel;

import java.util.function.Consumer;

/**
 * The client-server link on NeoForge 1.20.2 - 1.20.3: the same channels and bytes (one UTF-8 string with
 * its length) as on Fabric, Forge and Paper, so a NeoForge client works with any of those servers and the
 * other way round. These versions have the event channels of Forge 1.20.1, which accept a missing other
 * side; the packets are the vanilla custom payload packets around NeoForge's raw payload.
 */
public final class NeoNetworking {

    static final ResourceLocation HELLO = id(LinkProtocol.HELLO);
    static final ResourceLocation PROFILE = id(LinkProtocol.PROFILE);
    static final ResourceLocation NEARBY = id(LinkProtocol.NEARBY);
    static final ResourceLocation ADMIN = id(LinkProtocol.ADMIN);
    static final ResourceLocation ADMIN_REPLY = id(LinkProtocol.ADMIN_REPLY);

    private NeoNetworking() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(LinkProtocol.NAMESPACE, path);
    }

    private static EventNetworkChannel channel(ResourceLocation name) {
        return NetworkRegistry.newEventChannel(name, () -> "1", version -> true, version -> true);
    }

    static void register() {
        listen(channel(HELLO), NeoNetworking::onHello, null);
        listen(channel(ADMIN), NeoNetworking::onAdmin, null);
        listen(channel(PROFILE), null, text -> AudioDistancePlugin.LINK.onProfile(text));
        listen(channel(NEARBY), null, text -> AudioDistancePlugin.LINK.onNearby(text));
        listen(channel(ADMIN_REPLY), null, text -> AudioDistancePlugin.LINK.onAdminReply(text));
    }

    private interface ServerHandler {
        void handle(ServerPlayer player, String text);
    }

    private static void listen(EventNetworkChannel channel, ServerHandler server, Consumer<String> client) {
        if (server != null) {
            channel.addListener((NetworkEvent.ClientCustomPayloadEvent e) -> {
                NetworkEvent.Context context = e.getSource();
                ServerPlayer player = context.getSender();
                String text = read(e.getPayload());
                if (player != null && text != null) {
                    context.enqueueWork(() -> server.handle(player, text));
                }
                context.setPacketHandled(true);
            });
        }
        if (client != null) {
            channel.addListener((NetworkEvent.ServerCustomPayloadEvent e) -> {
                NetworkEvent.Context context = e.getSource();
                String text = read(e.getPayload());
                if (text != null) {
                    client.accept(text);
                }
                context.setPacketHandled(true);
            });
        }
    }

    private static String read(FriendlyByteBuf buf) {
        try {
            return buf == null ? null : buf.readUtf(LinkProtocol.MAX_LENGTH);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static SimplePayload payload(ResourceLocation id, String text) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(text, LinkProtocol.MAX_LENGTH);
        return SimplePayload.outbound(buf, 0, id);
    }

    /** Sends to a player whose client said hello (the server only sends to those). */
    private static void send(ServerPlayer player, ResourceLocation id, String text) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload(id, text)));
    }

    static net.minecraft.network.protocol.Packet<?> toServer(ResourceLocation id, String text) {
        return new ServerboundCustomPayloadPacket(payload(id, text));
    }

    private static void onHello(ServerPlayer player, String text) {
        if (!ServerHooks.hello(player.getUUID(), text)) {
            return;
        }
        Zone zone = ServerZones.of(player);
        AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
        sendProfile(player, zone);
    }

    static void sendProfile(ServerPlayer player, Zone zone) {
        send(player, PROFILE, AudioDistancePlugin.serverProfileMessage(zone, ServerBridge.isAdmin(player)));
    }

    /** A command from the player's Server tab; answered only for admins. */
    private static void onAdmin(ServerPlayer player, String text) {
        String reply = ServerBridge.admin(player, text, AudioDistanceNeoForge::resendProfiles);
        if (reply != null) {
            send(player, ADMIN_REPLY, reply);
        }
    }

    static void sendNearby(ServerPlayer player) {
        String text = AudioDistancePlugin.nearbyMessage(player, NeoNetworking::visible);
        if (text != null) {
            send(player, NEARBY, text);
        }
    }

    /** Spectators are listed only to other spectators. */
    private static boolean visible(Object viewer, Object other) {
        return !((ServerPlayer) other).isSpectator() || ((ServerPlayer) viewer).isSpectator();
    }
}
