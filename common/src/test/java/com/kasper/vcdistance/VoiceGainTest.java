package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** How the distance curve reaches OpenAL. */
public class VoiceGainTest {

    @Test
    @DisplayName("The curve is applied in OpenALSoundEvent.Post, after Simple Voice Chat writes the volume")
    void curveRunsInPost() {
        // SVC writes AL_GAIN and AL_MAX_GAIN right after the base event; a curve set there is lost
        // and the voice stays at full volume until it cuts off at the edge of the range
        List<Class<?>> registered = new ArrayList<>();
        EventRegistration registration = (EventRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EventRegistration.class}, (proxy, method, args) -> {
                    if (method.getName().equals("registerEvent")) {
                        registered.add((Class<?>) args[0]);
                    }
                    return null;
                });
        new AudioDistancePlugin().registerEvents(registration);
        assertTrue(registered.contains(OpenALSoundEvent.Post.class), "curve must listen to Post: " + registered);
    }

    @Test
    @DisplayName("Gain is the speaker's volume times the curve")
    void gainFollowsCurve() {
        assertEquals(1.0F, AudioDistancePlugin.voiceGain(1.0F, 1.0), 1e-6);
        assertEquals(0.25F, AudioDistancePlugin.voiceGain(1.0F, 0.25), 1e-6);
        assertEquals(0.5F, AudioDistancePlugin.voiceGain(2.0F, 0.25), 1e-6, "volume boosts above 100% keep working");
        assertEquals(2.0F, AudioDistancePlugin.voiceGain(2.0F, 1.0), 1e-6);
    }

    @Test
    @DisplayName("Muted speakers, the range edge and bad numbers give silence")
    void silence() {
        assertEquals(0.0F, AudioDistancePlugin.voiceGain(0.0F, 1.0));
        assertEquals(0.0F, AudioDistancePlugin.voiceGain(1.0F, 0.0));
        assertEquals(0.0F, AudioDistancePlugin.voiceGain(-1.0F, 1.0));
        assertEquals(0.0F, AudioDistancePlugin.voiceGain(1.0F, Double.NaN));
        assertEquals(0.0F, AudioDistancePlugin.voiceGain(Float.NaN, 1.0));
    }

    @Test
    @DisplayName("Default curve: full volume to half the range, then a fade to silence at the edge")
    void defaultCurveFades() {
        DistanceConfig d = new DistanceConfig();
        double near = AudioPhysics.calculateGain(0.4, d.getModel(), d.getAttenuationFactor(), d.getMinVolumeFraction(), d.getOpenalReferenceRatio());
        double mid = AudioPhysics.calculateGain(0.75, d.getModel(), d.getAttenuationFactor(), d.getMinVolumeFraction(), d.getOpenalReferenceRatio());
        double edge = AudioPhysics.calculateGain(1.0, d.getModel(), d.getAttenuationFactor(), d.getMinVolumeFraction(), d.getOpenalReferenceRatio());
        assertEquals(1.0, near, 1e-6);
        assertTrue(mid > 0.2 && mid < 0.8, "half way through the fade: " + mid);
        assertEquals(0.0, edge, 1e-6);
    }

    @Test
    @DisplayName("Simple Radio broadcasts keep their own audio and OpenAL range untouched")
    @SuppressWarnings("unchecked")
    void simpleRadioBroadcastsUntouched() {
        AudioDistancePlugin.SPEAKERS.clear();
        AudioDistancePlugin.markOcclusionProviderAvailable();
        AudioDistancePlugin.ENVIRONMENT.update(true, EnvironmentEffects.Weather.THUNDER);
        try {
            Map<Class<?>, Consumer<Object>> handlers = new HashMap<>();
            EventRegistration registration = (EventRegistration) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{EventRegistration.class}, (proxy, method, args) -> {
                        if (method.getName().equals("registerEvent")) {
                            handlers.put((Class<?>) args[0], (Consumer<Object>) args[1]);
                        }
                        return null;
                    });
            new AudioDistancePlugin().registerEvents(registration);

            UUID channelId = UUID.randomUUID();
            Position pos = (Position) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Position.class}, (p, m, a) -> 10.0);
            short[] original = new short[960];
            Arrays.fill(original, (short) 12000);

            for (String category : new String[]{"radios", "speakers", "walkies", "transceivers"}) {
                UUID id = category.equals("radios") ? channelId : UUID.randomUUID();
                for (int frame = 0; frame < 3; frame++) {
                    short[] audio = original.clone();
                    boolean[] audioReplaced = {false};
                    ClientReceiveSoundEvent.LocationalSound receive = (ClientReceiveSoundEvent.LocationalSound) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{ClientReceiveSoundEvent.LocationalSound.class},
                            (p, m, a) -> {
                                switch (m.getName()) {
                                    case "getId":
                                        return id;
                                    case "getRawAudio":
                                        return audio;
                                    case "getPosition":
                                        return pos;
                                    case "getDistance":
                                        return 4.0F;
                                    case "setRawAudio":
                                        audioReplaced[0] = true;
                                        return null;
                                    default:
                                        return null;
                                }
                            });
                    // Even if occlusion were somehow set on the speaker, Simple Radio must be skipped
                    SpeakerRegistry.Speaker existing = AudioDistancePlugin.SPEAKERS.get(id);
                    if (existing != null) {
                        existing.setOcclusion(4.0, System.nanoTime());
                        existing.setDistance(3.0, System.nanoTime());
                    }
                    handlers.get(ClientReceiveSoundEvent.LocationalSound.class).accept(receive);
                    assertFalse(audioReplaced[0], "Simple Radio PCM must not be modified (" + category + ", frame " + frame + ")");
                    assertArrayEquals(original, audio, "Simple Radio PCM samples must stay intact (" + category + ")");

                    OpenALSoundEvent alEvent = (OpenALSoundEvent) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{OpenALSoundEvent.class, OpenALSoundEvent.Post.class},
                            (p, m, a) -> {
                                switch (m.getName()) {
                                    case "getChannelId":
                                        return id;
                                    case "getPosition":
                                        return pos;
                                    case "getCategory":
                                        return category;
                                    case "getSource":
                                        return 1;
                                    default:
                                        return null;
                                }
                            });
                    assertTrue(AudioDistancePlugin.noteCategoryAndSkipRadio(alEvent), category);
                    handlers.get(OpenALSoundEvent.class).accept(alEvent);
                    handlers.get(OpenALSoundEvent.Post.class).accept(alEvent);
                }
                SpeakerRegistry.Speaker s = AudioDistancePlugin.SPEAKERS.get(id);
                assertNotNull(s);
                assertTrue(s.isSimpleRadio(), category);
                assertFalse(s.isActive(System.nanoTime()), category);
            }
        } finally {
            AudioDistancePlugin.ENVIRONMENT.reset();
            AudioDistancePlugin.SPEAKERS.clear();
        }
    }
}
