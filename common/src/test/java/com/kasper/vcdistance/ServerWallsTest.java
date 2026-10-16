package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives {@link ServerWalls} through fake Simple Voice Chat objects. The fake "Opus" codec stores
 * raw PCM, so the test can see exactly what the listener would hear.
 */
public class ServerWallsTest {

    private static final int FRAME = 960;

    @TempDir
    Path dir;

    private ServerSettings settings;
    private ServerWalls walls;
    private final List<EntitySoundPacket> sent = new ArrayList<>();
    private final UUID channel = UUID.randomUUID();
    private final UUID speaker = UUID.randomUUID();
    private final UUID listener = UUID.randomUUID();
    private final Object listenerPlayer = new Object();
    private final Object listenerLevel = new Object();
    private Consumer<EntitySoundPacket> onSend = p -> { };
    private boolean failEncoding;
    private int encodersCreated;

    @BeforeEach
    void setUp() {
        settings = new ServerSettings(dir.resolve("server.properties"));
        settings.load();
        walls = new ServerWalls(settings);
        walls.markWorldAvailable();
    }

    // ---- fake codec ---------------------------------------------------------

    private static byte[] pack(short[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(pcm.length * 2);
        for (short s : pcm) {
            b.putShort(s);
        }
        return b.array();
    }

    private static short[] unpack(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        short[] pcm = new short[data.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = b.getShort();
        }
        return pcm;
    }

    private static short[] tone(int frameIndex) {
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            long n = (long) frameIndex * FRAME + i;
            pcm[i] = (short) Math.round(8000 * Math.sin(2 * Math.PI * 3000 * n / 48000.0));
        }
        return pcm;
    }

    private static double rms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / pcm.length);
    }

    // ---- fake SVC objects ---------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, java.util.function.Function<Object[], Object>> methods) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, m, args) -> {
            java.util.function.Function<Object[], Object> f = methods.get(m.getName());
            if (f == null) {
                throw new UnsupportedOperationException(m.getName());
            }
            return f.apply(args);
        });
    }

    private OpusEncoder encoder() {
        encodersCreated++;
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("encode", a -> {
            if (failEncoding) {
                throw new IllegalStateException("boom");
            }
            return pack((short[]) a[0]);
        });
        m.put("close", a -> null);
        m.put("resetState", a -> null);
        return proxy(OpusEncoder.class, m);
    }

    private OpusDecoder decoder() {
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("decode", a -> unpack((byte[]) a[0]));
        m.put("close", a -> null);
        m.put("resetState", a -> null);
        return proxy(OpusDecoder.class, m);
    }

    /** Builder that starts as a copy of the original packet, like Simple Voice Chat's. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private EntitySoundPacket.Builder builder(UUID ch, long seq, byte[] data, boolean whisper, float distance) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("channelId", ch);
        fields.put("sequenceNumber", seq);
        fields.put("opusEncodedData", data);
        fields.put("whispering", whisper);
        fields.put("distance", distance);
        Object[] self = new Object[1];
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        for (String name : new String[]{"channelId", "sender", "opusEncodedData", "sequenceNumber", "category",
                "entityUuid", "whispering", "distance"}) {
            m.put(name, a -> {
                fields.put(name, a[0]);
                return self[0];
            });
        }
        m.put("build", a -> packet((UUID) fields.get("channelId"), (long) fields.get("sequenceNumber"),
                (byte[]) fields.get("opusEncodedData"), (boolean) fields.get("whispering"), (float) fields.get("distance")));
        self[0] = proxy(EntitySoundPacket.Builder.class, m);
        return (EntitySoundPacket.Builder) self[0];
    }

    private EntitySoundPacket packet(UUID ch, long seq, byte[] data, boolean whisper, float distance) {
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getChannelId", a -> ch);
        m.put("getSender", a -> speaker);
        m.put("getOpusEncodedData", a -> data);
        m.put("getSequenceNumber", a -> seq);
        m.put("getCategory", a -> null);
        m.put("getEntityUuid", a -> speaker);
        m.put("isWhispering", a -> whisper);
        m.put("getDistance", a -> distance);
        m.put("entitySoundPacketBuilder", a -> builder(ch, seq, data, whisper, distance));
        return proxy(EntitySoundPacket.class, m);
    }

    private VoicechatServerApi api() {
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("createEncoder", a -> encoder());
        m.put("createDecoder", a -> decoder());
        m.put("sendEntitySoundPacketTo", a -> {
            EntitySoundPacket p = (EntitySoundPacket) a[1];
            sent.add(p);
            onSend.accept(p);
            return null;
        });
        return proxy(VoicechatServerApi.class, m);
    }

    private VoicechatConnection connection(UUID player) {
        Map<String, java.util.function.Function<Object[], Object>> pm = new HashMap<>();
        pm.put("getUuid", a -> player);
        pm.put("getPlayer", a -> listenerPlayer);
        Map<String, java.util.function.Function<Object[], Object>> lm = new HashMap<>();
        lm.put("getServerLevel", a -> listenerLevel);
        de.maxhenkel.voicechat.api.ServerLevel level = proxy(de.maxhenkel.voicechat.api.ServerLevel.class, lm);
        pm.put("getServerLevel", a -> level);
        ServerPlayer sp = proxy(ServerPlayer.class, pm);
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getPlayer", a -> sp);
        return proxy(VoicechatConnection.class, m);
    }

    /** @return {cancelled, packet} */
    private boolean fire(EntitySoundPacket packet, UUID to, String source) {
        boolean[] cancelled = {false};
        VoicechatServerApi api = api();
        VoicechatConnection receiver = connection(to);
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getPacket", a -> packet);
        m.put("getSource", a -> source);
        m.put("getReceiverConnection", a -> receiver);
        m.put("getSenderConnection", a -> null);
        m.put("getVoicechat", a -> api);
        m.put("cancel", a -> {
            cancelled[0] = true;
            return true;
        });
        m.put("isCancelled", a -> cancelled[0]);
        m.put("isCancellable", a -> true);
        walls.onEntitySound(proxy(EntitySoundPacketEvent.class, m));
        return cancelled[0];
    }

    private boolean fireFrame(int i) {
        return fire(packet(channel, i, pack(tone(i)), false, 48F), listener, SoundPacketEvent.SOURCE_PROXIMITY);
    }

    private void setThickness(double thickness) {
        try {
            Thread.sleep(120); // walls are re-measured at most every 100 ms per pair
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        walls.tick((player, level, entity, x, y, z) -> thickness);
    }

    // ---- tests --------------------------------------------------------------

    /** RMS the listener gets behind a stone wall over the last frames of a voice, whispered or not. */
    private double behindOneWall(boolean whisper) {
        fire(packet(channel, 0, pack(tone(0)), whisper, 48F), listener, SoundPacketEvent.SOURCE_PROXIMITY);
        setThickness(1.0);
        double out = 0;
        for (int i = 1; i <= 80; i++) {
            assertTrue(fire(packet(channel, i, pack(tone(i)), whisper, 48F), listener, SoundPacketEvent.SOURCE_PROXIMITY));
            if (i > 60) {
                out += rms(unpack(sent.get(sent.size() - 1).getOpusEncodedData()));
            }
        }
        return out;
    }

    private double whisperToTalk() {
        double talk = behindOneWall(false);
        walls.clear();
        sent.clear();
        walls.markWorldAvailable();
        return behindOneWall(true) / talk;
    }

    @Test
    @DisplayName("Behind a wall a whisper loses more than talking, and the same with the switch off")
    void whisperLostBehindWall() {
        double ratio = whisperToTalk();
        // One stone wall takes about 13 dB more off a whisper: a fifth of the level
        assertTrue(ratio > 0.12 && ratio < 0.35, "whisper against talking behind one wall: " + ratio);
        walls.clear();
        sent.clear();
        settings.setWallLoudness(false);
        walls.markWorldAvailable();
        double same = whisperToTalk();
        assertTrue(same > 0.95 && same < 1.05, "switched off: " + same);
    }

    @Test
    @DisplayName("Before the wall is measured, packets pass through untouched")
    void unknownThicknessPassesThrough() {
        assertFalse(fireFrame(0));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("Behind a wall the listener gets a muffled copy with the same metadata")
    void muffledBehindWall() {
        fireFrame(0);
        setThickness(2.0);
        double inRms = 0;
        double outRms = 0;
        for (int i = 1; i <= 30; i++) {
            assertTrue(fireFrame(i), "original packet must be replaced");
            EntitySoundPacket out = sent.get(sent.size() - 1);
            assertEquals(channel, out.getChannelId());
            assertEquals(i, out.getSequenceNumber());
            assertEquals(speaker, out.getEntityUuid());
            assertEquals(48F, out.getDistance());
            if (i > 10) {
                inRms += rms(tone(i));
                outRms += rms(unpack(out.getOpusEncodedData()));
            }
        }
        assertTrue(outRms < inRms * 0.1, "3 kHz behind two walls should be far quieter: " + outRms / inRms);
        assertEquals(1, walls.activeStreams());
    }

    @Test
    @DisplayName("Listeners with the addon are never processed by the server")
    void addonListenersAreSkipped() {
        walls.markAddonListener(listener);
        fireFrame(0);
        setThickness(3.0);
        assertFalse(fireFrame(1));
        assertTrue(sent.isEmpty());
        assertTrue(walls.hasAddon(listener));
        // Reconnecting voice chat keeps the addon marker; leaving the game drops it
        walls.releaseListener(listener);
        assertTrue(walls.hasAddon(listener));
        walls.forgetPlayer(listener);
        assertFalse(walls.hasAddon(listener));
    }

    @Test
    @DisplayName("Group, spectator and plugin audio pass through")
    void onlyProximityIsProcessed() {
        fireFrame(0);
        setThickness(3.0);
        for (String source : new String[]{SoundPacketEvent.SOURCE_GROUP, SoundPacketEvent.SOURCE_SPECTATOR, SoundPacketEvent.SOURCE_PLUGIN}) {
            assertFalse(fire(packet(channel, 5, pack(tone(5)), false, 48F), listener, source), source);
        }
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("Our own re-sent packet is not processed again (no loop)")
    void noRecursion() {
        fireFrame(0);
        setThickness(3.0);
        int[] nested = {0};
        onSend = p -> {
            nested[0]++;
            // Simple Voice Chat dispatches plugin sends through the same event
            assertFalse(fire(p, listener, SoundPacketEvent.SOURCE_PROXIMITY));
        };
        assertTrue(fireFrame(1));
        assertEquals(1, nested[0]);
        assertEquals(1, sent.size());
    }

    @Test
    @DisplayName("The stream cap is respected")
    void streamCap() {
        settings.setMaxStreams(1);
        UUID other = UUID.randomUUID();
        fireFrame(0);
        fire(packet(channel, 0, pack(tone(0)), false, 48F), other, SoundPacketEvent.SOURCE_PROXIMITY);
        setThickness(3.0);
        assertTrue(fireFrame(1));
        assertFalse(fire(packet(channel, 1, pack(tone(1)), false, 48F), other, SoundPacketEvent.SOURCE_PROXIMITY),
                "second listener exceeds the cap and passes through");
        assertEquals(1, walls.activeStreams());
    }

    @Test
    @DisplayName("With more pairs than one tick can measure, every pair is measured in turn")
    void manyPairsAllMeasured() throws InterruptedException {
        settings.setMaxStreams(300);
        List<UUID> listeners = new ArrayList<>();
        for (int i = 0; i < 130; i++) {
            UUID to = UUID.randomUUID();
            listeners.add(to);
            fire(packet(channel, 0, pack(tone(0)), false, 48F), to, SoundPacketEvent.SOURCE_PROXIMITY);
        }
        // 48 traces per tick: three rounds cover 130 pairs only if each round takes the ones waiting longest
        for (int round = 0; round < 3; round++) {
            Thread.sleep(120);
            walls.tick((player, level, entity, x, y, z) -> 3.0);
        }
        int muffled = 0;
        for (UUID to : listeners) {
            if (fire(packet(channel, 1, pack(tone(1)), false, 48F), to, SoundPacketEvent.SOURCE_PROXIMITY)) {
                muffled++;
            }
        }
        assertEquals(130, muffled);
    }

    @Test
    @DisplayName("Encoder failures fall back to the original packet")
    void failureFallsBack() {
        fireFrame(0);
        setThickness(3.0);
        failEncoding = true;
        assertFalse(fireFrame(1));
        assertTrue(sent.isEmpty());
        assertEquals(0, walls.activeStreams());
    }

    @Test
    @DisplayName("Disabled server walls or disabled profile walls do nothing")
    void switches() {
        fireFrame(0);
        setThickness(3.0);
        settings.setServerWalls(false);
        assertFalse(fireFrame(1));
        settings.setServerWalls(true);
        settings.profile().setOcclusionEnabled(false);
        assertFalse(fireFrame(2));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("When the wall disappears the stream returns to pass-through and frees its encoder")
    void returnsToPassThrough() {
        fireFrame(0);
        setThickness(2.0);
        for (int i = 1; i < 10; i++) {
            fireFrame(i);
        }
        assertEquals(1, walls.activeStreams());
        fireFrame(10); // keep the pair active
        setThickness(0.0);
        int i = 11;
        while (walls.activeStreams() > 0 && i < 200) {
            fireFrame(i++);
        }
        assertEquals(0, walls.activeStreams(), "encoder should be released once the filter is open again");
        int before = sent.size();
        assertFalse(fireFrame(i));
        assertEquals(before, sent.size());
    }

    @Test
    @DisplayName("One encoder per listener, not per frame")
    void encoderReused() {
        fireFrame(0);
        setThickness(2.0);
        for (int i = 1; i < 20; i++) {
            fireFrame(i);
        }
        assertEquals(1, encodersCreated);
        walls.clear();
        assertEquals(0, walls.activeStreams());
    }

    // ---- water, weather, air and echo (server_effects, server_air) ---------------

    private ServerPlayers realismPlayers(double distance, boolean listenerUnderwater) {
        ServerPlayers players = new ServerPlayers();
        walls = new ServerWalls(settings, players);
        walls.markWorldAvailable();
        players.update(new ServerPlayers.Info(speaker, "Speaker", "world", 0, 64, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR));
        players.update(new ServerPlayers.Info(listener, "Listener", "world", distance, 64, 0, false, true, false, "", "",
                List.of(), "", listenerUnderwater, EnvironmentEffects.Weather.CLEAR));
        return players;
    }

    private double heardLevel(int from, int to) {
        double in = 0;
        double out = 0;
        for (int i = from; i < to; i++) {
            assertTrue(fireFrame(i), "frame " + i + " should be replaced");
            in += rms(tone(i));
            out += rms(unpack(sent.get(sent.size() - 1).getOpusEncodedData()));
        }
        return out / in;
    }

    @Test
    @DisplayName("Realism off: nobody is processed for water, weather or air, even under water")
    void realismOffChangesNothing() {
        realismPlayers(40, true);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i));
        }
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("A listener under water hears the voice dull and quiet, with no wall in between")
    void underwaterListener() {
        settings.setServerEffects(true);
        realismPlayers(5, true);
        double level = heardLevel(0, 30);
        assertTrue(level < 0.1, "3 kHz under water should be far quieter: " + level);
        assertEquals(1, walls.activeStreams());
        assertEquals(1, encodersCreated);
    }

    @Test
    @DisplayName("Climbing out of the water gives the voice back, and the encoder is freed")
    void surfacing() {
        settings.setServerEffects(true);
        ServerPlayers players = realismPlayers(5, true);
        heardLevel(0, 20);
        players.update(new ServerPlayers.Info(listener, "Listener", "world", 5, 64, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR));
        int i = 20;
        while (walls.activeStreams() > 0 && i < 300) {
            fireFrame(i++);
        }
        assertEquals(0, walls.activeStreams(), "the filter opens again and the stream passes through");
        int before = sent.size();
        assertFalse(fireFrame(i));
        assertEquals(before, sent.size());
    }

    @Test
    @DisplayName("Air: a far voice is duller than a near one, and a near one is left alone")
    void airDullsFarVoices() {
        settings.setServerAir(true);
        realismPlayers(3, false);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "3 blocks away the air changes nothing");
        }
        assertTrue(sent.isEmpty());

        realismPlayers(46, false);
        double far = heardLevel(0, 30);
        assertTrue(far < 0.5, "3 kHz at the edge of the range loses most of its level: " + far);
    }

    @Test
    @DisplayName("Realism does not need the walls: switched off in the profile it still works")
    void independentOfWalls() {
        settings.setServerEffects(true);
        settings.profile().setOcclusionEnabled(false);
        realismPlayers(5, true);
        assertTrue(heardLevel(0, 25) < 0.1);
    }

    /** Speaker at the origin looking along ({@code lookX}, 0, {@code lookZ}), listener {@code distance} blocks along +x. */
    private ServerPlayers facingPlayers(double distance, double lookX, double lookZ) {
        ServerPlayers players = new ServerPlayers();
        walls = new ServerWalls(settings, players);
        walls.markWorldAvailable();
        players.update(new ServerPlayers.Info(speaker, "Speaker", "world", 0, 64, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR, lookX, 0.0, lookZ));
        players.update(new ServerPlayers.Info(listener, "Listener", "world", distance, 64, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR));
        return players;
    }

    /** Facing is optional and gentle by default; these tests measure it switched on at full strength. */
    private void facingOn() {
        settings.profile().setDirectivityEnabled(true);
        settings.profile().setDirectivityStrength(1.0);
    }

    @Test
    @DisplayName("Facing: behind the speaker's back the voice is quieter and duller; in front it is left alone")
    void facingAway() {
        settings.setServerEffects(true);
        facingOn();
        facingPlayers(5, 1.0, 0.0);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "the speaker faces the listener");
        }
        assertTrue(sent.isEmpty());

        sent.clear();
        facingPlayers(5, -1.0, 0.0);
        heardLevel(0, 20); // the filter glides in
        double behind = heardLevel(20, 40);
        // 3 kHz behind the head: 5 dB less, and the cutoff near 4.2 kHz takes about 2 dB more
        assertTrue(behind > 0.38 && behind < 0.52, "3 kHz behind the speaker: " + behind);
        assertEquals(1, walls.activeStreams());
    }

    @Test
    @DisplayName("Facing: turning back to the listener frees the stream; off in the profile it does nothing")
    void facingTurnsBackAndSwitchesOff() {
        settings.setServerEffects(true);
        facingOn();
        ServerPlayers players = facingPlayers(5, -1.0, 0.0);
        heardLevel(0, 20);
        players.update(new ServerPlayers.Info(speaker, "Speaker", "world", 0, 64, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR, 1.0, 0.0, 0.0));
        int i = 20;
        while (walls.activeStreams() > 0 && i < 300) {
            fireFrame(i++);
        }
        assertEquals(0, walls.activeStreams(), "facing the listener again, the stream passes through");

        settings.profile().setDirectivityEnabled(false);
        facingPlayers(5, -1.0, 0.0);
        for (int j = 0; j < 5; j++) {
            assertFalse(fireFrame(j), "facing switched off in the profile");
        }
    }

    @Test
    @DisplayName("Facing: a small turn is not worth a re-encoded stream, and an unknown facing changes nothing")
    void facingThreshold() {
        settings.setServerEffects(true);
        facingOn();
        // 60 degrees off: a fraction of a decibel
        facingPlayers(5, 0.5, Math.sqrt(0.75));
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "60 degrees off");
        }
        realismPlayers(5, false);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "where the speaker looks is not known");
        }
        assertTrue(sent.isEmpty());
    }

    private static double voiceRange() {
        double v = AudioDistancePlugin.serverVoiceDistance();
        return v > 0.0 ? v : AudioDistancePlugin.FALLBACK_DISTANCE;
    }

    /** Speaker at the origin, listener {@code distance} blocks along +x, both at {@code y}, under the open sky or not. */
    private ServerPlayers windPlayers(double distance, double y, boolean sky) {
        ServerPlayers players = new ServerPlayers();
        walls = new ServerWalls(settings, players);
        walls.markWorldAvailable();
        players.update(new ServerPlayers.Info(speaker, "Speaker", "world", 0, y, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR, Double.NaN, Double.NaN, Double.NaN, sky));
        players.update(new ServerPlayers.Info(listener, "Listener", "world", distance, y, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR, Double.NaN, Double.NaN, Double.NaN, sky));
        return players;
    }

    /** The listener, low down under a roof, flies along +z at {@code speed} blocks per second, measured afresh. */
    private void listenerFlies(ServerPlayers players, double distance, double speed, long start) {
        players.update(new ServerPlayers.Info(listener, "Listener", "world", distance, 64, 0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR), start);
        players.update(new ServerPlayers.Info(listener, "Listener", "world", distance, 64, speed / 20.0, false, true, false, "", "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR), start + 50_000_000L);
    }

    @Test
    @DisplayName("Wind: on a peak a far voice is blown away; in the valley, under a roof or with the wind off nothing happens")
    void windOnAPeak() {
        settings.setServerEffects(true);
        double range = voiceRange();
        windPlayers(range, 200, true);
        heardLevel(0, 20); // the filter glides in
        double far = heardLevel(20, 40);
        // About 6 dB at the edge of the range, give or take the gusts (0.65 - 1.35 of it)
        assertTrue(far > 0.37 && far < 0.66, "at the edge of the range on a peak: " + far);
        assertEquals(1, walls.activeStreams());

        sent.clear();
        windPlayers(range, 64, true);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "no wind down in the valley");
        }
        windPlayers(range, 200, false);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "no wind under a roof");
        }
        settings.profile().setWindEnabled(false);
        windPlayers(range, 200, true);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "the wind switched off in the profile");
        }
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("Wind: flying past a listener, the air rushing by covers the voice; a slow glide starts no stream but keeps one going")
    void windInFlight() {
        settings.setServerEffects(true);
        double range = voiceRange();
        ServerPlayers players = windPlayers(range, 64, false);
        long t = System.nanoTime();
        listenerFlies(players, range, 30.0, t);
        assertTrue(players.airflow(listener) > 0.8, "elytra speed: " + players.airflow(listener));
        assertEquals(0.0, players.airflow(speaker), "the speaker stands still");
        heardLevel(0, 20);
        double flying = heardLevel(20, 40);
        // airflow 0.84 at the edge of the range: 5 dB, no gusts
        assertTrue(flying > 0.50 && flying < 0.62, "flying at 30 blocks per second: " + flying);

        // Slower: 0.7 dB, under the 1 dB it takes to start a stream, over the 0.5 dB that keeps one
        listenerFlies(players, range, 17.0, t + 3_000_000_000L);
        for (int i = 40; i < 80; i++) {
            assertTrue(fireFrame(i), "the stream keeps going at frame " + i);
        }
        assertEquals(1, walls.activeStreams());

        sent.clear();
        players = windPlayers(range, 64, false);
        listenerFlies(players, range, 17.0, t + 6_000_000_000L);
        for (int i = 0; i < 5; i++) {
            assertFalse(fireFrame(i), "a slow glide is not worth a stream of its own");
        }
        listenerFlies(players, range, 5.0, t + 9_000_000_000L);
        assertEquals(0.0, players.airflow(listener), "on foot");
    }

    @Test
    @DisplayName("The cave a player stands in adds an echo to the voice; leaving it takes the echo away")
    void echoInACave() throws InterruptedException {
        settings.setServerEffects(true);
        realismPlayers(20, false);
        AudioDistancePlugin.SERVER_ROOMS.clear();
        try {
            long now = System.nanoTime() - 10_000_000_000L;
            AudioDistancePlugin.SERVER_ROOMS.update(listener, RoomEstimate.forced(0.6), null, now);
            for (int i = 0; i < 10; i++) {
                assertTrue(fireFrame(i), "the echo is added to every frame");
            }
            // The tail keeps ringing after the tone: the echo of the last frames is in the output
            EntitySoundPacket last = sent.get(sent.size() - 1);
            assertEquals(speaker, last.getEntityUuid());
            assertEquals(FRAME, unpack(last.getOpusEncodedData()).length);
            double dry = 0;
            double wet = 0;
            for (int i = 5; i < 10; i++) {
                dry += rms(tone(i));
                wet += rms(unpack(sent.get(i).getOpusEncodedData()));
            }
            assertTrue(Math.abs(wet - dry) > dry * 0.01, "the room has changed the sound");

            AudioDistancePlugin.SERVER_ROOMS.update(listener, RoomEstimate.OPEN, null, System.nanoTime());
            // The echo glides away over a few seconds (RoomGlide), then the tail rings out
            int i = 10;
            long give = System.nanoTime() + 12_000_000_000L;
            while (walls.activeStreams() > 0 && System.nanoTime() < give) {
                fireFrame(i++);
                Thread.sleep(20);
            }
            assertEquals(0, walls.activeStreams(), "no echo left: back to pass-through");
        } finally {
            AudioDistancePlugin.SERVER_ROOMS.clear();
        }
    }

    @Test
    @DisplayName("A voice that pauses does not bring its old echo back as a ghost")
    void echoDoesNotHauntAfterPause() throws InterruptedException {
        settings.setServerEffects(true);
        realismPlayers(20, false);
        AudioDistancePlugin.SERVER_ROOMS.clear();
        try {
            AudioDistancePlugin.SERVER_ROOMS.update(listener, RoomEstimate.forced(0.9), null, System.nanoTime() - 10_000_000_000L);
            for (int i = 0; i < 20; i++) {
                fireFrame(i);
            }
            Thread.sleep(450);
            assertTrue(fireFrame(20));
            double silent = rms(unpack(sent.get(sent.size() - 1).getOpusEncodedData()));
            assertTrue(silent > 0.0, "the new frame is the voice");
            // A fresh reverb starts empty: the first samples of the frame are not ringing from before the pause
            short[] pcm = unpack(sent.get(sent.size() - 1).getOpusEncodedData());
            double head = 0;
            for (int k = 0; k < 24; k++) {
                head += Math.abs(pcm[k]);
            }
            assertTrue(head / 24 < 8000, "no loud ghost at the start: " + head / 24);
        } finally {
            AudioDistancePlugin.SERVER_ROOMS.clear();
        }
    }

    @Test
    @DisplayName("A zone that sets its own echo skips the measuring but still echoes")
    void zoneEchoNeedsNoMeasuring() {
        Zone hall = new Zone(Zone.BOX, "hall", ServerSettings.ProfileMode.OFF, "",
                new Zone.Rules(null, null, null, null, 0.7, false, null), null, 0);
        assertFalse(ServerRooms.needsMeasuring(hall));
        settings.setServerEffects(true);
        realismPlayers(20, false);
        AudioDistancePlugin.SERVER_ROOMS.clear();
        try {
            AudioDistancePlugin.SERVER_ROOMS.update(listener, RoomEstimate.OPEN, hall, System.nanoTime() - 10_000_000_000L);
            assertTrue(fireFrame(0));
        } finally {
            AudioDistancePlugin.SERVER_ROOMS.clear();
        }
    }

    @Test
    @DisplayName("A muted player's microphone packets are cancelled before Simple Voice Chat sends them anywhere")
    void mutedMicrophone() {
        boolean[] cancelled = {false};
        Map<String, java.util.function.Function<Object[], Object>> pm = new HashMap<>();
        pm.put("isWhispering", a -> false);
        pm.put("getOpusEncodedData", a -> pack(tone(0)));
        de.maxhenkel.voicechat.api.packets.MicrophonePacket mic = proxy(de.maxhenkel.voicechat.api.packets.MicrophonePacket.class, pm);
        VoicechatConnection player = connection(speaker);
        Map<String, java.util.function.Function<Object[], Object>> cm = new HashMap<>();
        cm.put("getPlayer", a -> player.getPlayer());
        cm.put("isInGroup", a -> false);
        VoicechatConnection sender = proxy(VoicechatConnection.class, cm);
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getPacket", a -> mic);
        m.put("getSenderConnection", a -> sender);
        m.put("getVoicechat", a -> api());
        m.put("cancel", a -> {
            cancelled[0] = true;
            return true;
        });
        de.maxhenkel.voicechat.api.events.MicrophonePacketEvent event =
                proxy(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, m);

        walls.onMicrophone(event);
        assertFalse(cancelled[0]);
        assertTrue(AudioDistancePlugin.TALK.isTalking(speaker, System.nanoTime()));
        AudioDistancePlugin.TALK.forget(speaker);

        settings.mute(new VoiceMute(speaker, "Speaker", 0L, "Admin", ""));
        walls.onMicrophone(event);
        assertTrue(cancelled[0]);
        assertFalse(AudioDistancePlugin.TALK.isTalking(speaker, System.nanoTime()), "a muted player does not show as talking");
        assertTrue(AudioDistancePlugin.MUTED_TALK.isTalking(speaker, System.nanoTime()));
        AudioDistancePlugin.MUTED_TALK.forget(speaker);
    }

    /** A microphone frame whose audio the addon may replace, as on Simple Voice Chat 2.5.27+. */
    private final class Mic {
        byte[] data;
        boolean settable = true;
        final de.maxhenkel.voicechat.api.events.MicrophonePacketEvent event;

        Mic() {
            Map<String, java.util.function.Function<Object[], Object>> pm = new HashMap<>();
            pm.put("isWhispering", a -> false);
            pm.put("getOpusEncodedData", a -> data);
            pm.put("setOpusEncodedData", a -> {
                if (!settable) {
                    throw new AbstractMethodError("setOpusEncodedData");
                }
                data = (byte[]) a[0];
                return null;
            });
            de.maxhenkel.voicechat.api.packets.MicrophonePacket mic =
                    proxy(de.maxhenkel.voicechat.api.packets.MicrophonePacket.class, pm);
            VoicechatConnection player = connection(speaker);
            Map<String, java.util.function.Function<Object[], Object>> cm = new HashMap<>();
            cm.put("getPlayer", a -> player.getPlayer());
            cm.put("isInGroup", a -> false);
            VoicechatConnection sender = proxy(VoicechatConnection.class, cm);
            VoicechatServerApi api = api();
            Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
            m.put("getPacket", a -> mic);
            m.put("getSenderConnection", a -> sender);
            m.put("getVoicechat", a -> api);
            event = proxy(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, m);
        }

        /** Sends one frame of 8 kHz tone, well above the horn's band; returns what goes out. */
        short[] send(int i) {
            short[] pcm = new short[FRAME];
            for (int k = 0; k < FRAME; k++) {
                long n = (long) i * FRAME + k;
                pcm[k] = (short) Math.round(8000 * Math.sin(2 * Math.PI * 8000 * n / 48000.0));
            }
            data = pack(pcm);
            walls.onMicrophone(event);
            return unpack(data);
        }

        void end() {
            data = new byte[0];
            walls.onMicrophone(event);
        }
    }

    private void holder(ServerPlayers players, String hand) {
        players.update(new ServerPlayers.Info(speaker, "Herald", "world", 0, 64, 0, false, true, false, hand, "",
                List.of(), "", false, EnvironmentEffects.Weather.CLEAR, 1, 0, 0));
    }

    @Test
    @DisplayName("Megaphone: the held horn changes the voice for everyone; put down, the voice is plain and the stream closes")
    void megaphoneSound() {
        ServerPlayers players = new ServerPlayers();
        walls = new ServerWalls(settings, players);
        walls.markWorldAvailable();
        settings.setMegaphoneItem("minecraft:goat_horn");
        AudioDistancePlugin.TALK.forget(speaker);
        try {
            holder(players, "");
            Mic mic = new Mic();
            short[] out = mic.send(0);
            assertEquals(0, encodersCreated, "without the megaphone nothing is decoded");
            assertTrue(rms(out) > 5000, "and the voice goes out as it is");

            holder(players, "minecraft:goat_horn");
            mic.end();
            AudioDistancePlugin.TALK.forget(speaker);
            for (int i = 0; i < 3; i++) {
                out = mic.send(i);
            }
            assertEquals(1, walls.activeStreams());
            assertTrue(rms(out) < 1500, "the horn cuts what is far above a voice: " + rms(out));

            // Put down mid-talk: glides back to plain, then passes the voice through until the talk ends
            holder(players, "");
            for (int i = 3; i < 10; i++) {
                out = mic.send(i);
            }
            assertTrue(rms(out) > 5000, "plain again: " + rms(out));
            assertEquals(1, walls.activeStreams(), "kept until the talk ends, so the codec does not switch mid-word");
            mic.end();
            mic.send(10);
            assertEquals(0, walls.activeStreams(), "a new talk without the megaphone closes the stream");

            // Turned off by the admin
            settings.setMegaphoneSound(false);
            holder(players, "minecraft:goat_horn");
            mic.end();
            out = mic.send(11);
            assertEquals(0, walls.activeStreams());
            assertTrue(rms(out) > 5000);
            settings.setMegaphoneSound(true);

            // An old Simple Voice Chat cannot take new audio: the horn turns itself off, the voice is untouched
            mic.settable = false;
            mic.end();
            AudioDistancePlugin.TALK.forget(speaker);
            out = mic.send(12);
            assertTrue(rms(out) > 5000);
            assertEquals(0, walls.activeStreams(), "the stream is closed again");
            int created = encodersCreated;
            mic.end();
            mic.send(13);
            assertEquals(created, encodersCreated, "and is not tried again");
        } finally {
            AudioDistancePlugin.TALK.forget(speaker);
        }
    }

    @Test
    @DisplayName("Group voices: untouched by default, cancelled by the group rules the admin turned on")
    void groupRules() {
        ServerPlayers players = new ServerPlayers();
        walls = new ServerWalls(settings, players);
        players.update(new ServerPlayers.Info(speaker, "Dead", "world", 0, 64, 0, false, false, false, "", "", List.of(), ""));
        players.update(new ServerPlayers.Info(listener, "Friend", "world", 500, 64, 0, false, true, false, "", "", List.of(), ""));
        EntitySoundPacket p = packet(channel, 0, pack(tone(0)), false, 48F);

        settings.setDeadSilent(true);
        assertFalse(fire(p, listener, SoundPacketEvent.SOURCE_GROUP), "dead_players_silent alone leaves groups alone");
        settings.setGroupDeadSilent(true);
        assertTrue(fire(p, listener, SoundPacketEvent.SOURCE_GROUP));
        assertTrue(sent.isEmpty());

        // Proximity packets from far away are Simple Voice Chat's business; group rules only touch group audio
        settings.setDeadSilent(false);
        assertFalse(fire(p, listener, SoundPacketEvent.SOURCE_SPECTATOR));
    }
}
