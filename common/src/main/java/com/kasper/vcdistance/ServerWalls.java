package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side wall muffling for players who do not have the addon.
 * <p>
 * Simple Voice Chat sends every proximity voice packet to each listener separately and fires a
 * {@link SoundPacketEvent} for each of them. When a wall stands between the speaker and a listener
 * without the addon, the packet is decoded once per speaker frame, run through that listener's
 * {@link VoiceFilter}, re-encoded with that listener's encoder and sent in place of the original.
 * Everything else passes through untouched:
 * <ul>
 *     <li>listeners who have the addon (they muffle locally, with better quality and no server cost),</li>
 *     <li>listeners with a clear line of sight,</li>
 *     <li>group, spectator and plugin audio,</li>
 *     <li>anything above {@link ServerSettings#getMaxStreams()} voices at once.</li>
 * </ul>
 * Any failure falls back to the original packet, so a problem here can never silence voice chat.
 * <p>
 * Threading: packet events arrive on Simple Voice Chat's server thread; {@link #tick} runs on the
 * Minecraft server thread and is the only place that touches the world (through
 * {@link ThicknessProvider}).
 */
public final class ServerWalls {

    /** Resolves the acoustic thickness between a listener and a sound source, on the server thread. */
    @FunctionalInterface
    public interface ThicknessProvider {
        /**
         * @param listener      the listener's Minecraft player object ({@code ServerPlayer})
         * @param level         the listener's Minecraft level ({@code ServerLevel}), as reported by Simple Voice Chat
         * @param speakerEntity the speaking entity, or {@code null} for a fixed position
         * @return thickness in stone blocks, or {@code NaN} when it cannot be determined
         */
        double thickness(Object listener, Object level, UUID speakerEntity, double x, double y, double z);
    }

    private static final long ACTIVE_NANOS = TimeUnit.MILLISECONDS.toNanos(800);
    private static final long FORGET_NANOS = TimeUnit.SECONDS.toNanos(4);
    private static final long PRUNE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long TRACE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    /** A pause this long in a voice drops its echo tail. */
    private static final long REVERB_STALE_NANOS = TimeUnit.MILLISECONDS.toNanos(400);
    private static final int MAX_TRACES_PER_TICK = 48;
    /** Per listener and tick, when each player's walls are measured on their own thread (Folia). */
    private static final int MAX_TRACES_PER_LISTENER = 8;

    private final PerfMeter perf = new PerfMeter();

    private record PairKey(UUID channel, UUID listener) {
    }

    static final class Pair {
        final UUID listener;
        final VoiceFilter filter = new VoiceFilter();
        volatile Object listenerPlayer;
        volatile Object listenerLevel;
        volatile UUID speakerEntity;
        volatile double x;
        volatile double y;
        volatile double z;
        volatile long lastSeenNanos;
        volatile long lastTraceNanos;
        volatile double thickness = Double.NaN;
        OpusEncoder encoder;
        /** The echo of this pair's space; made when a voice first needs one, dropped with the encoder. */
        Reverb reverb;
        long lastFrameNanos;

        Pair(UUID listener) {
            this.listener = listener;
        }
    }

    private static final class Stream {
        OpusDecoder decoder;
        long lastSequence = Long.MIN_VALUE;
        short[] pcm;
        long lastSeenNanos;
    }

    private final ServerSettings settings;
    private final Set<UUID> addonListeners = ConcurrentHashMap.newKeySet();
    private final Map<PairKey, Pair> pairs = new ConcurrentHashMap<>();
    private final Map<UUID, Stream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger encoding = new AtomicInteger();
    private final ThreadLocal<Boolean> resending = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private volatile boolean worldAvailable;
    private volatile long lastPruneNanos = System.nanoTime();
    private volatile boolean loggedFailure;

    private final ServerPlayers players;

    public ServerWalls(ServerSettings settings) {
        this(settings, new ServerPlayers());
    }

    public ServerWalls(ServerSettings settings, ServerPlayers players) {
        this.settings = settings;
        this.players = players;
    }

    // -------------------------------------------------------------------------
    // State from the rest of the addon
    // -------------------------------------------------------------------------

    /** Called once a server tick feeds wall data; without it nothing is processed. */
    public void markWorldAvailable() {
        worldAvailable = true;
    }

    /** A listener announced the addon: it muffles locally, so the server leaves its audio alone. */
    public void markAddonListener(UUID player) {
        addonListeners.add(player);
    }

    public boolean hasAddon(UUID player) {
        return addonListeners.contains(player);
    }

    /** The player left the game: forget everything about them. */
    public void forgetPlayer(UUID player) {
        addonListeners.remove(player);
        releaseListener(player);
    }

    /**
     * The player's voice connection closed. They may reconnect voice without rejoining the game,
     * so whether they have the addon is kept until they leave.
     */
    public void releaseListener(UUID player) {
        for (Pair p : pairs.values()) {
            if (p.listener.equals(player)) {
                p.lastSeenNanos = 0L;
            }
        }
    }

    /** Number of voices currently re-encoded. */
    public int activeStreams() {
        return encoding.get();
    }

    // -------------------------------------------------------------------------
    // Packet events (Simple Voice Chat server thread)
    // -------------------------------------------------------------------------

    // Simple Voice Chat's builders start as a copy of the original packet (channel, sender,
    // sequence number, category, entity, whisper flag, distance, position), so only the audio is
    // replaced. Only opusEncodedData(...) and build() are called: the published 2.6.0 API has no
    // setters for the other fields.

    public void onEntitySound(EntitySoundPacketEvent event) {
        EntitySoundPacket p = event.getPacket();
        if (p == null || groupSilenced(event, p.getEntityUuid())) {
            return;
        }
        long start = System.nanoTime();
        // The server's voice rules first: the voice may not reach this listener at all, or carry another range
        float distance = p.getDistance();
        boolean retarget = false;
        if (!resending.get() && SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource())) {
            ServerRange.Decision rule = voiceRule(p.getEntityUuid(), event.getReceiverConnection(), p.isWhispering());
            if (rule != null && !rule.hears()) {
                perf.add(System.nanoTime() - start);
                event.cancel();
                return;
            }
            if (rule != null && Math.abs(rule.distance() - distance) > 0.01) {
                distance = (float) rule.distance();
                retarget = true;
            }
        }
        byte[] processed = process(event, p.getChannelId(), p.getSequenceNumber(), p.getOpusEncodedData(),
                p.getEntityUuid(), null);
        perf.add(System.nanoTime() - start);
        if (processed == null && !retarget) {
            return;
        }
        byte[] audio = processed != null ? processed : p.getOpusEncodedData();
        EntitySoundPacket rebuilt = rebuild(p, audio, retarget ? distance : Float.NaN);
        if (rebuilt == null) {
            return; // this Simple Voice Chat cannot change the range: the original packet goes out
        }
        resend(event, () -> event.getVoicechat().sendEntitySoundPacketTo(event.getReceiverConnection(), rebuilt));
    }

    /** The packet with new audio and, unless NaN, a new range; {@code null} when the range cannot be set. */
    private EntitySoundPacket rebuild(EntitySoundPacket p, byte[] audio, float distance) {
        if (Float.isNaN(distance)) {
            return p.entitySoundPacketBuilder().opusEncodedData(audio).build();
        }
        try {
            return p.entitySoundPacketBuilder().opusEncodedData(audio).distance(distance).build();
        } catch (Throwable t) {
            // Simple Voice Chat before 2.6 has no distance on the builder
            return audio == p.getOpusEncodedData() ? null : p.entitySoundPacketBuilder().opusEncodedData(audio).build();
        }
    }

    /** Group voices sent without a position (normal and isolated groups): only the group rules apply. */
    public void onStaticSound(StaticSoundPacketEvent event) {
        StaticSoundPacket p = event.getPacket();
        if (p != null) {
            groupSilenced(event, senderOf(p));
        }
    }

    /**
     * Applies the rules the admin chose for Simple Voice Chat groups to a group voice.
     *
     * @return {@code true} when the packet was cancelled
     */
    private boolean groupSilenced(SoundPacketEvent<?> event, UUID speaker) {
        if (resending.get() || !SoundPacketEvent.SOURCE_GROUP.equals(event.getSource()) || !settings.hasGroupRules()) {
            return false;
        }
        long start = System.nanoTime();
        try {
            VoicechatConnection receiver = event.getReceiverConnection();
            if (speaker == null || receiver == null || receiver.getPlayer() == null) {
                return false;
            }
            ServerPlayers.Info from = players.get(speaker);
            ServerPlayers.Info to = players.get(receiver.getPlayer().getUuid());
            if (from == null || to == null || ServerRange.decideGroup(settings, from, to).hears()) {
                return false;
            }
            event.cancel();
            return true;
        } catch (Throwable t) {
            logFailure(t);
            return false;
        } finally {
            perf.add(System.nanoTime() - start);
        }
    }

    /** The player who spoke: the sender, or the channel on Simple Voice Chat versions without it. */
    private static UUID senderOf(SoundPacket p) {
        try {
            UUID sender = p.getSender();
            if (sender != null) {
                return sender;
            }
        } catch (Throwable ignored) {
            // older Simple Voice Chat
        }
        return p.getChannelId();
    }

    /**
     * Whether {@code sender}'s nearby voice follows the range rules: yes outside a group, and in an
     * open group (heard by nearby players too) when the admin allows it.
     */
    private boolean rangeRulesFor(VoicechatConnection sender) {
        if (!sender.isInGroup()) {
            return true;
        }
        if (!settings.isOpenGroupRange()) {
            return false;
        }
        try {
            Group group = sender.getGroup();
            return group != null && group.getType() == Group.Type.OPEN;
        } catch (Throwable t) {
            return false; // Simple Voice Chat without group types
        }
    }

    /**
     * What the server's voice rules say about {@code speaker}'s voice reaching this receiver, or
     * {@code null} when there are no rules or either player is unknown.
     */
    private ServerRange.Decision voiceRule(UUID speaker, VoicechatConnection receiver, boolean whispering) {
        if (!voiceRulesOn() || speaker == null || receiver == null || receiver.getPlayer() == null) {
            return null;
        }
        ServerPlayers.Info from = players.get(speaker);
        ServerPlayers.Info to = players.get(receiver.getPlayer().getUuid());
        if (from == null || to == null) {
            return null;
        }
        return ServerRange.decide(settings, from, to, whispering, voiceRange(), whisperRange());
    }

    /**
     * A voice that carries further than Simple Voice Chat's own range (a stage zone, a megaphone):
     * the players beyond Simple Voice Chat's range but within the voice's get it from here.
     */
    public void onMicrophone(MicrophonePacketEvent event) {
        MicrophonePacket packet = event.getPacket();
        VoicechatConnection sender = event.getSenderConnection();
        if (packet != null && sender != null && sender.getPlayer() != null) {
            UUID talking = sender.getPlayer().getUuid();
            if (settings.muteOf(talking, System.currentTimeMillis()) != null) {
                // Muted with /vcd mute: the voice goes nowhere, nearby or in a group
                AudioDistancePlugin.MUTED_TALK.spoke(talking, System.nanoTime());
                event.cancel();
                return;
            }
            AudioDistancePlugin.TALK.spoke(talking, System.nanoTime());
        }
        if (packet == null || sender == null || sender.getPlayer() == null || !rangeRulesFor(sender)
                || !voiceRulesOn()) {
            return;
        }
        long start = System.nanoTime();
        try {
            UUID id = sender.getPlayer().getUuid();
            ServerPlayers.Info speaker = players.get(id);
            if (speaker == null) {
                return;
            }
            boolean whispering = packet.isWhispering();
            double own = whispering ? whisperRange() : voiceRange();
            double range = ServerRange.rangeOf(settings, speaker, whispering, voiceRange(), whisperRange());
            if (range <= own + 0.01) {
                return;
            }
            VoicechatServerApi voicechat = event.getVoicechat();
            EntitySoundPacket out = null;
            for (ServerPlayers.Info other : players.all()) {
                double d = other.distanceTo(speaker);
                if (other.id().equals(id) || d <= own || d > range
                        || !ServerRange.decide(settings, speaker, other, whispering, voiceRange(), whisperRange()).hears()) {
                    continue;
                }
                VoicechatConnection c = voicechat.getConnectionOf(other.id());
                if (c == null || !c.isConnected() || c.isDisabled()) {
                    continue;
                }
                if (out == null) {
                    out = packet.entitySoundPacketBuilder()
                            .channelId(id)
                            .entityUuid(id)
                            .whispering(whispering)
                            .distance((float) range)
                            .opusEncodedData(packet.getOpusEncodedData())
                            .build();
                }
                EntitySoundPacket send = out;
                resending.set(Boolean.TRUE);
                try {
                    voicechat.sendEntitySoundPacketTo(c, send);
                } finally {
                    resending.set(Boolean.FALSE);
                }
            }
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            perf.add(System.nanoTime() - start);
        }
    }

    /** The admin's voice rules, or a player's own choice (a range mode, a volume, an ignored player). */
    private boolean voiceRulesOn() {
        return settings.hasVoiceRules() || AudioDistancePlugin.PLAYER_PREFS.anyRules();
    }

    private static double voiceRange() {
        double v = AudioDistancePlugin.serverVoiceDistance();
        return v > 0.0 ? v : AudioDistancePlugin.FALLBACK_DISTANCE;
    }

    private static double whisperRange() {
        double w = AudioDistancePlugin.serverWhisperDistance();
        return w > 0.0 ? w : voiceRange() / 2.0;
    }

    public void onLocationalSound(LocationalSoundPacketEvent event) {
        LocationalSoundPacket p = event.getPacket();
        if (p == null || groupSilenced(event, senderOf(p)) || p.getPosition() == null) {
            return;
        }
        long start = System.nanoTime();
        byte[] processed = process(event, p.getChannelId(), p.getSequenceNumber(), p.getOpusEncodedData(),
                null, p.getPosition());
        perf.add(System.nanoTime() - start);
        if (processed == null) {
            return;
        }
        resend(event, () -> event.getVoicechat().sendLocationalSoundPacketTo(event.getReceiverConnection(),
                p.locationalSoundPacketBuilder()
                        .opusEncodedData(processed)
                        .build()));
    }

    /**
     * @return the re-encoded frame, or {@code null} to let the original packet through
     */
    private byte[] process(SoundPacketEvent<?> event, UUID channel, long sequence, byte[] opus,
                           UUID speakerEntity, Position position) {
        if (resending.get() || !SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource())) {
            return null;
        }
        if (channel == null || opus == null || opus.length == 0) {
            return null;
        }
        VoicechatConnection receiver = event.getReceiverConnection();
        if (receiver == null || receiver.getPlayer() == null) {
            return null;
        }
        UUID listener = receiver.getPlayer().getUuid();
        if (listener == null || addonListeners.contains(listener)) {
            return null;
        }
        // Walls (the admin's switch, and this player's own), how loud this speaker is to this listener, and
        // what water, weather, distance and rooms do to it
        PlayerPrefs prefs = AudioDistancePlugin.PLAYER_PREFS;
        DistanceConfig profile = settings.profile();
        boolean walls = worldAvailable && settings.isServerWalls() && profile.isOcclusionEnabled()
                && (prefs.wallsFor(listener) || settings.wallsLocked());
        UUID speakerId = speakerEntity != null ? speakerEntity : channel;
        double volumeLoss = prefs.lossDb(listener, speakerId);
        boolean realism = settings.hasServerRealism();
        if (!walls && volumeLoss <= 0.0 && !realism) {
            return null;
        }

        long now = System.nanoTime();
        maybePrune(now);
        EnvironmentEffects.Effect air = EnvironmentEffects.Effect.NONE;
        RoomEstimate room = null;
        double roomDistance = -1.0;
        if (realism) {
            ServerPlayers.Info from = players.get(speakerId);
            ServerPlayers.Info to = players.get(listener);
            if (from != null && to != null && Zone.sameWorld(from.world(), to.world())) {
                double range = voiceRange();
                air = ServerEffects.atmosphere(settings, from, to, range);
                room = ServerEffects.room(settings, AudioDistancePlugin.SERVER_ROOMS, from, to, now);
                roomDistance = from.distanceTo(to);
            }
        }

        Pair pair = pairs.computeIfAbsent(new PairKey(channel, listener), k -> new Pair(listener));
        pair.lastSeenNanos = now;
        pair.listenerPlayer = receiver.getPlayer().getPlayer();
        pair.listenerLevel = receiver.getPlayer().getServerLevel() != null
                ? receiver.getPlayer().getServerLevel().getServerLevel()
                : null;
        pair.speakerEntity = speakerEntity;
        if (position != null) {
            pair.x = position.getX();
            pair.y = position.getY();
            pair.z = position.getZ();
        }

        synchronized (pair) {
            double thickness = pair.thickness;
            // A zone can make walls stronger or weaker for the listeners in it
            double strength = settings.wallsStrengthIn(settings.zoneOf(players.get(listener)));
            boolean measured = walls && !Double.isNaN(thickness);
            double muffle = measured ? OcclusionModel.muffle(thickness, strength) : 0.0;
            double loss = (measured ? OcclusionModel.lossDb(thickness, strength) : 0.0) + volumeLoss;
            // Water, rain and distance on top of the walls: filters in a row
            muffle = 1.0 - (1.0 - muffle) * (1.0 - air.muffle());
            loss += air.lossDb();
            double[] levels = ServerEffects.echoLevels(room, roomDistance, voiceRange(), profile.getReverbStrength());
            boolean echoing = levels[0] > 0.002 || levels[1] > 0.002;
            boolean wanted = muffle > 0.002 || loss > 0.05 || echoing;

            if (pair.encoder == null) {
                if (!wanted || encoding.get() >= settings.getMaxStreams()) {
                    return null;
                }
                pair.encoder = event.getVoicechat().createEncoder();
                encoding.incrementAndGet();
            }

            try {
                short[] pcm = decode(event.getVoicechat(), channel, sequence, opus, now);
                if (pcm == null) {
                    stop(pair);
                    return null;
                }
                short[] frame = pair.filter.process(pcm.clone(), muffle, loss);
                if (pair.reverb != null && now - pair.lastFrameNanos > REVERB_STALE_NANOS) {
                    // The voice paused: the old tail would come back as a ghost
                    pair.reverb = null;
                }
                pair.lastFrameNanos = now;
                if (pair.reverb == null && echoing) {
                    pair.reverb = new Reverb();
                }
                if (pair.reverb != null) {
                    pair.reverb.process(frame, room != null ? room : RoomEstimate.OPEN, levels[0], levels[1]);
                }
                byte[] encoded = pair.encoder.encode(frame);
                if (!pair.filter.isEngaged() && (pair.reverb == null || !pair.reverb.isActive())) {
                    // The filter has glided back open and no echo is left: this frame is the crossfade to dry,
                    // then the voice passes through again
                    stop(pair);
                }
                return encoded;
            } catch (Throwable t) {
                logFailure(t);
                stop(pair);
                return null;
            }
        }
    }

    private short[] decode(VoicechatServerApi api, UUID channel, long sequence, byte[] opus, long now) {
        Stream stream = streams.computeIfAbsent(channel, c -> new Stream());
        synchronized (stream) {
            stream.lastSeenNanos = now;
            if (stream.pcm != null && stream.lastSequence == sequence) {
                return stream.pcm;
            }
            if (stream.decoder == null) {
                stream.decoder = api.createDecoder();
            } else if (stream.lastSequence != sequence - 1) {
                // Frames were skipped while nobody needed this stream: start clean
                stream.decoder.resetState();
            }
            stream.pcm = stream.decoder.decode(opus);
            stream.lastSequence = sequence;
            return stream.pcm;
        }
    }

    private void resend(SoundPacketEvent<?> event, Runnable send) {
        resending.set(Boolean.TRUE);
        try {
            send.run();
        } catch (Throwable t) {
            // The original packet was not cancelled yet and still goes out
            logFailure(t);
            return;
        } finally {
            resending.set(Boolean.FALSE);
        }
        event.cancel();
    }

    private void stop(Pair pair) {
        pair.reverb = null;
        if (pair.encoder != null) {
            try {
                pair.encoder.close();
            } catch (Throwable ignored) {
            }
            pair.encoder = null;
            encoding.decrementAndGet();
        }
    }

    // -------------------------------------------------------------------------
    // Server tick (Minecraft server thread)
    // -------------------------------------------------------------------------

    /** Measures walls for every active listener/speaker pair. */
    public void tick(ThicknessProvider provider) {
        long start = System.nanoTime();
        try {
            tickUnmeasured(provider);
        } finally {
            perf.add(System.nanoTime() - start);
            perf.endTick();
        }
    }

    /** Time per server tick spent on wall rays and on filtering voices (both threads together). */
    public PerfMeter perf() {
        return perf;
    }

    private void tickUnmeasured(ThicknessProvider provider) {
        worldAvailable = true;
        if (!settings.isServerWalls() || !settings.profile().isOcclusionEnabled()) {
            return;
        }
        long now = System.nanoTime();
        int budget = MAX_TRACES_PER_TICK;
        java.util.Collection<Pair> order = pairs.values();
        if (order.size() > MAX_TRACES_PER_TICK) {
            // More pairs than one tick can measure: the one measured longest ago goes first, so in
            // hash order the same first few dozen would be refreshed every time and the rest never
            java.util.List<Pair> oldestFirst = new java.util.ArrayList<>(order);
            oldestFirst.sort(java.util.Comparator.comparingLong(p -> p.lastTraceNanos));
            order = oldestFirst;
        }
        for (Pair pair : order) {
            if (budget <= 0) {
                break;
            }
            if (trace(pair, provider, now)) {
                budget--;
            }
        }
    }

    /**
     * For servers that tick each region on its own thread (Folia): measures only the walls between
     * {@code listener} and the voices they hear, on that player's thread. The load meter is left
     * alone, since several threads would share it.
     */
    public void tickListener(UUID listener, ThicknessProvider provider) {
        worldAvailable = true;
        if (!settings.isServerWalls() || !settings.profile().isOcclusionEnabled()) {
            return;
        }
        long now = System.nanoTime();
        int budget = MAX_TRACES_PER_LISTENER;
        for (Pair pair : pairs.values()) {
            if (budget <= 0) {
                break;
            }
            if (listener.equals(pair.listener) && trace(pair, provider, now)) {
                budget--;
            }
        }
    }

    /** Measures the pair's walls when it is active and due. @return whether it was measured */
    private boolean trace(Pair pair, ThicknessProvider provider, long now) {
        if (now - pair.lastSeenNanos > ACTIVE_NANOS || now - pair.lastTraceNanos < TRACE_INTERVAL_NANOS) {
            return false;
        }
        Object listener = pair.listenerPlayer;
        Object level = pair.listenerLevel;
        if (listener == null || level == null) {
            return false;
        }
        try {
            pair.thickness = provider.thickness(listener, level, pair.speakerEntity, pair.x, pair.y, pair.z);
        } catch (Throwable t) {
            pair.thickness = Double.NaN;
            logFailure(t);
        }
        pair.lastTraceNanos = now;
        return true;
    }

    // -------------------------------------------------------------------------
    // Housekeeping
    // -------------------------------------------------------------------------

    private void maybePrune(long now) {
        if (now - lastPruneNanos < PRUNE_INTERVAL_NANOS) {
            return;
        }
        lastPruneNanos = now;
        pairs.entrySet().removeIf(e -> {
            Pair p = e.getValue();
            if (now - p.lastSeenNanos <= FORGET_NANOS) {
                return false;
            }
            synchronized (p) {
                stop(p);
            }
            return true;
        });
        streams.entrySet().removeIf(e -> {
            Stream s = e.getValue();
            if (now - s.lastSeenNanos <= FORGET_NANOS) {
                return false;
            }
            synchronized (s) {
                if (s.decoder != null) {
                    try {
                        s.decoder.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return true;
        });
    }

    /** Releases every encoder and decoder, e.g. when the voice chat server stops. */
    public void clear() {
        for (Pair p : pairs.values()) {
            synchronized (p) {
                stop(p);
            }
        }
        pairs.clear();
        for (Stream s : streams.values()) {
            synchronized (s) {
                if (s.decoder != null) {
                    try {
                        s.decoder.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        streams.clear();
        addonListeners.clear();
    }

    private void logFailure(Throwable t) {
        Problems.record("Server voice processing", t);
        if (!loggedFailure) {
            loggedFailure = true;
            DistanceConfig.LOGGER.warn("Server-side wall muffling failed, passing voices through unchanged: {}", t.toString());
        } else {
            DistanceConfig.LOGGER.debug("Server-side wall muffling failed: {}", t.toString());
        }
    }
}
