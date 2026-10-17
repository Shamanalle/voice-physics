package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerZones;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.event.EventNetworkChannel;

import java.util.function.Consumer;

/**
 * The client-server link on Forge: the same channels and the same bytes (one UTF-8 string with its
 * length) as on Fabric, NeoForge and Paper, so a Forge client works with any of those servers and the
 * other way round. Every channel accepts a missing other side.
 */
final class ForgeNetworking {

    static final ResourceLocation HELLO = id(LinkProtocol.HELLO);
    static final ResourceLocation PROFILE = id(LinkProtocol.PROFILE);
    static final ResourceLocation NEARBY = id(LinkProtocol.NEARBY);
    static final ResourceLocation ADMIN = id(LinkProtocol.ADMIN);
    static final ResourceLocation ADMIN_REPLY = id(LinkProtocol.ADMIN_REPLY);

    private ForgeNetworking() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(LinkProtocol.NAMESPACE, path);
    }

    private static EventNetworkChannel channel(ResourceLocation name) {
        return NetworkRegistry.newEventChannel(name, () -> "1", version -> true, version -> true);
    }

    static void register() {
        listen(channel(HELLO), (player, text) -> onHello(player, text), null);
        listen(channel(ADMIN), (player, text) -> onAdmin(player, text), null);
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
                NetworkEvent.Context context = e.getSource().get();
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
                NetworkEvent.Context context = e.getSource().get();
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

    static FriendlyByteBuf write(String text) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(text, LinkProtocol.MAX_LENGTH);
        return buf;
    }

    private static void send(ServerPlayer player, ResourceLocation channel, String text) {
        player.connection.send(new ClientboundCustomPayloadPacket(channel, write(text)));
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
