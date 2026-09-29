package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerZones;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

import java.util.function.Consumer;

/**
 * The client-server link on NeoForge 1.20.4: the same channels and bytes (one UTF-8 string with its
 * length) as on Fabric, Forge and Paper, so a NeoForge client works with any of those servers and the
 * other way round. Registered as optional, so either side may be missing.
 */
public final class NeoNetworking {

    static final ResourceLocation HELLO = id(LinkProtocol.HELLO);
    static final ResourceLocation PROFILE = id(LinkProtocol.PROFILE);
    static final ResourceLocation NEARBY = id(LinkProtocol.NEARBY);
    static final ResourceLocation ADMIN = id(LinkProtocol.ADMIN);
    static final ResourceLocation ADMIN_REPLY = id(LinkProtocol.ADMIN_REPLY);

    /** One message: its channel and its text. */
    public record Message(ResourceLocation id, String text) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(text, LinkProtocol.MAX_LENGTH);
        }
    }

    private NeoNetworking() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(LinkProtocol.NAMESPACE, path);
    }

    private static FriendlyByteBuf.Reader<Message> reader(ResourceLocation id) {
        return buf -> new Message(id, buf.readUtf(LinkProtocol.MAX_LENGTH));
    }

    private interface ServerHandler {
        void handle(ServerPlayer player, String text);
    }

    static void register(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar(LinkProtocol.NAMESPACE).versioned("1").optional();
        toServer(registrar, HELLO, NeoNetworking::onHello);
        toServer(registrar, ADMIN, NeoNetworking::onAdmin);
        toClient(registrar, PROFILE, text -> AudioDistancePlugin.LINK.onProfile(text));
        toClient(registrar, NEARBY, text -> AudioDistancePlugin.LINK.onNearby(text));
        toClient(registrar, ADMIN_REPLY, text -> AudioDistancePlugin.LINK.onAdminReply(text));
    }

    private static void toServer(IPayloadRegistrar registrar, ResourceLocation id, ServerHandler handler) {
        registrar.play(id, reader(id), handlers -> handlers.server((Message message, PlayPayloadContext context) -> {
            Player player = context.player().orElse(null);
            if (player instanceof ServerPlayer serverPlayer) {
                context.workHandler().execute(() -> handler.handle(serverPlayer, message.text()));
            }
        }));
    }

    private static void toClient(IPayloadRegistrar registrar, ResourceLocation id, Consumer<String> handler) {
        registrar.play(id, reader(id), handlers -> handlers.client((Message message, PlayPayloadContext context) ->
                handler.accept(message.text())));
    }

    /** Sends to a player whose client said it has the addon (the channel is optional). */
    private static void send(ServerPlayer player, ResourceLocation id, String text) {
        if (player.connection.isConnected(id)) {
            PacketDistributor.PLAYER.with(player).send(new Message(id, text));
        }
    }

    static void sendToServer(ResourceLocation id, String text) {
        PacketDistributor.SERVER.noArg().send(new Message(id, text));
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
        if (player.connection.isConnected(NEARBY)) {
            String text = AudioDistancePlugin.nearbyMessage(player, NeoNetworking::visible);
            if (text != null) {
                send(player, NEARBY, text);
            }
        }
    }

    /** Spectators are listed only to other spectators. */
    private static boolean visible(Object viewer, Object other) {
        return !((ServerPlayer) other).isSpectator() || ((ServerPlayer) viewer).isSpectator();
    }
}
