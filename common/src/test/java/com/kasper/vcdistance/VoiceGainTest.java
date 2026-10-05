package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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
}
