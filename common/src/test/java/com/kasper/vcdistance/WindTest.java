package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Wind up high and in fast flight: where it blows, how much it takes off a voice, gusts and speed. */
public class WindTest {

    private static final EnvironmentEffects.Weather CLEAR = EnvironmentEffects.Weather.CLEAR;
    private static final long SECOND = 1_000_000_000L;

    @Test
    @DisplayName("Calm below y 90, a smooth rise to the full wind at y 180, more in rain and the most in a storm")
    void placeOverHeight() {
        assertEquals(0.0, Wind.place(true, false, 64, CLEAR));
        assertEquals(0.0, Wind.place(true, false, Wind.LOW_Y, CLEAR), 1e-12);
        assertEquals(1.0, Wind.place(true, false, Wind.HIGH_Y, CLEAR), 1e-12);
        assertEquals(1.0, Wind.place(true, false, 320, CLEAR), 1e-12);
        assertEquals(0.5, Wind.place(true, false, (Wind.LOW_Y + Wind.HIGH_Y) / 2, CLEAR), 1e-12);
        double previous = 0.0;
        for (int y = 91; y <= 180; y++) {
            double w = Wind.place(true, false, y, CLEAR);
            assertTrue(w >= previous, "grows with the height at " + y);
            assertTrue(w - previous < 0.02, "no step at " + y);
            previous = w;
        }
        assertEquals(Wind.RAIN_FACTOR, Wind.place(true, false, 200, EnvironmentEffects.Weather.RAIN), 1e-12);
        assertEquals(Wind.THUNDER_FACTOR, Wind.place(true, false, 200, EnvironmentEffects.Weather.THUNDER), 1e-12);
        assertEquals(0.0, Wind.place(true, false, 64, EnvironmentEffects.Weather.THUNDER), "a storm in the valley brings no wind here");
    }

    @Test
    @DisplayName("No wind under a roof, under water, in the Nether or the End, or where the place is unknown")
    void noWindIndoors() {
        assertEquals(0.0, Wind.place(false, false, 200, CLEAR), "under a roof");
        assertEquals(0.0, Wind.place(true, true, 200, CLEAR), "under water");
        assertEquals(0.0, Wind.place(true, false, Double.NaN, CLEAR));
        assertEquals(0.0, Wind.place((PlaceTuning.Place) null, false, CLEAR));
        assertEquals(1.0, Wind.place(new PlaceTuning.Place("ResourceKey[minecraft:dimension / minecraft:overworld]",
                "minecraft:stony_peaks", 200, true), false, CLEAR), 1e-12);
        assertEquals(0.0, Wind.place(new PlaceTuning.Place("ResourceKey[minecraft:dimension / minecraft:the_nether]",
                "minecraft:basalt_deltas", 200, true), false, CLEAR));
        assertEquals(0.0, Wind.place(new PlaceTuning.Place("minecraft:the_end", "minecraft:the_end", 200, true), false, CLEAR));
        assertEquals(0.0, Wind.place(PlaceTuning.Place.UNKNOWN, false, CLEAR), "an unknown place is taken as low ground");
        assertTrue(Wind.windyWorld(null));
        assertTrue(Wind.windyWorld("minecraft:overworld"));
        assertTrue(Wind.windyWorld("mymod:sky_islands"));
        assertFalse(Wind.windyWorld("MINECRAFT:THE_NETHER"));
    }

    @Test
    @DisplayName("Air rushing past: nothing on foot or on a horse at a trot, all of it on elytra with a firework")
    void airflowOverSpeed() {
        assertEquals(0.0, Wind.airflow(0.0, false));
        assertEquals(0.0, Wind.airflow(5.6, false), "sprinting");
        assertEquals(0.0, Wind.airflow(Wind.AIRFLOW_FROM, false), 1e-12);
        assertTrue(Wind.airflow(30, false) > 0.7, "elytra glide");
        assertEquals(1.0, Wind.airflow(Wind.AIRFLOW_FULL, false), 1e-12);
        assertEquals(1.0, Wind.airflow(78, false), 1e-12, "a long fall");
        assertEquals(0.0, Wind.airflow(40, true), "under water");
        assertEquals(0.0, Wind.airflow(Double.NaN, false));
        assertEquals(0.0, Wind.airflow(-3, false));
    }

    @Test
    @DisplayName("Loss: about 6 dB at the edge of the range in a full wind, a third of it close by, never past 12 dB")
    void lossOverDistance() {
        assertEquals(0.0, Wind.lossDb(0, 0, 0, 0, 1.0, 1.0, 1.0), "calm");
        assertEquals(Wind.LOSS_DB, Wind.lossDb(1, 0, 0, 0, 1.0, 1.0, 1.0), 1e-12);
        assertEquals(Wind.LOSS_DB * Wind.NEAR_SHARE, Wind.lossDb(1, 0, 0, 0, 0.0, 1.0, 1.0), 1e-12);
        assertEquals(Wind.lossDb(1, 0, 0, 0, 1.0, 1.0, 1.0), Wind.lossDb(1, 0, 0, 0, 3.0, 1.0, 1.0), 1e-12, "past the range as at its edge");
        double previous = 0.0;
        for (int i = 0; i <= 20; i++) {
            double loss = Wind.lossDb(1, 0, 0, 0, i / 20.0, 1.0, 1.0);
            assertTrue(loss > previous, "a farther voice loses more");
            previous = loss;
        }
        assertEquals(Wind.MAX_LOSS_DB, Wind.lossDb(Wind.THUNDER_FACTOR, 0, 0, 0, 1.0, 1.5, 1.0 + Wind.GUST_DEPTH), 1e-12);
        assertEquals(0.0, Wind.lossDb(1, 1, 1, 1, 1.0, 0.0, 1.0), "strength 0");
        assertEquals(Wind.LOSS_DB * 0.5, Wind.lossDb(1, 0, 0, 0, 1.0, 0.5, 1.0), 1e-12);
    }

    @Test
    @DisplayName("Loss: the windier end counts, the speaker's own flight counts half, gusts swing only the wind of the place")
    void lossFromEitherEnd() {
        assertEquals(Wind.lossDb(1, 0, 0, 0, 1.0, 1.0, 1.0), Wind.lossDb(0, 1, 0, 0, 1.0, 1.0, 1.0), 1e-12,
                "a voice from a peak down to the valley crosses the wind as much as one going up");
        assertEquals(Wind.lossDb(1, 0, 0, 0, 1.0, 1.0, 1.0), Wind.lossDb(1, 0.4, 0, 0, 1.0, 1.0, 1.0), 1e-12, "not added up");
        assertEquals(Wind.LOSS_DB, Wind.lossDb(0, 0, 1, 0, 1.0, 1.0, 1.0), 1e-12, "the listener in flight");
        assertEquals(Wind.LOSS_DB * Wind.SPEAKER_AIRFLOW_SHARE, Wind.lossDb(0, 0, 0, 1, 1.0, 1.0, 1.0), 1e-12, "the speaker in flight");
        assertEquals(Wind.LOSS_DB * 1.2, Wind.lossDb(1, 0, 0, 0, 1.0, 1.0, 1.2), 1e-12, "a gust");
        assertEquals(Wind.LOSS_DB, Wind.lossDb(0, 0, 1, 0, 1.0, 1.0, 1.3), 1e-12, "flight has no gusts");
        assertEquals(Wind.LOSS_DB, Wind.lossDb(Double.NaN, Double.NaN, 1, Double.NaN, 1.0, 1.0, Double.NaN), 1e-12);
        assertEquals(Wind.LOSS_DB * Wind.NEAR_SHARE, Wind.lossDb(1, 0, 0, 0, Double.NaN, 1.0, 1.0), 1e-12);
        assertEquals(0.0, Wind.lossDb(1, 1, 1, 1, 1.0, Double.NaN, 1.0));
    }

    @Test
    @DisplayName("Gusts: 1 on average, within the depth, smooth, and different for each voice")
    void gusts() {
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double previous = Wind.gust(0, 42);
        int steps = 0;
        // 10 ms steps over 10 minutes: far longer than the slowest swing
        for (long t = 10_000_000L; t <= 600 * SECOND; t += 10_000_000L) {
            double g = Wind.gust(t, 42);
            sum += g;
            min = Math.min(min, g);
            max = Math.max(max, g);
            assertTrue(Math.abs(g - previous) < 0.01, "no jump in 10 ms");
            previous = g;
            steps++;
        }
        assertEquals(1.0, sum / steps, 0.01);
        assertTrue(min >= 1.0 - Wind.GUST_DEPTH - 1e-9 && max <= 1.0 + Wind.GUST_DEPTH + 1e-9, min + " .. " + max);
        assertTrue(max - min > Wind.GUST_DEPTH, "it does swing: " + min + " .. " + max);
        int differ = 0;
        for (long seed = 0; seed < 20; seed++) {
            if (Math.abs(Wind.gust(5 * SECOND, seed) - Wind.gust(5 * SECOND, seed + 1)) > 0.02) {
                differ++;
            }
        }
        assertTrue(differ >= 15, "neighbouring seeds get their own gusts: " + differ);
        assertEquals(Wind.gust(7 * SECOND, 9), Wind.gust(7 * SECOND, 9), "the same voice at the same time");
    }

    @Test
    @DisplayName("Speed: the first step after a pause counts as it is, then it is smoothed over about half a second")
    void speedSmoothing() {
        Wind.Speed speed = new Wind.Speed();
        assertEquals(0.0, speed.get());
        long t = 100 * SECOND;
        speed.position(0, 100, 0, t);
        assertEquals(0.0, speed.get(), "one position is no speed yet");
        // Elytra at 30 blocks per second, one position per tick
        double x = 0.0;
        for (int i = 1; i <= 2; i++) {
            x += 1.5;
            t += SECOND / 20;
            speed.position(x, 100, 0, t);
        }
        assertEquals(30.0, speed.get(), 1e-6, "someone who starts talking in flight is flying");
        // Lands and stands still: the speed falls over about half a second, not at once
        t += SECOND / 20;
        speed.position(x, 100, 0, t);
        assertTrue(speed.get() > 25.0 && speed.get() < 30.0, "one still tick: " + speed.get());
        for (int i = 0; i < 40; i++) {
            t += SECOND / 20;
            speed.position(x, 100, 0, t);
        }
        assertTrue(speed.get() < 1.0, "two seconds later: " + speed.get());
    }

    @Test
    @DisplayName("Speed: a jump or a short drop is not flight")
    void speedJump() {
        Wind.Speed speed = new Wind.Speed();
        long t = 0;
        speed.position(0, 64, 0, t);
        double peak = 0.0;
        // Walking at 4.3 blocks per second with one fast tick (a 1-block drop in a tick, 20 blocks per second)
        for (int i = 1; i <= 40; i++) {
            t += SECOND / 20;
            double y = i == 20 ? 63 : (i > 20 ? 63 : 64);
            speed.position(i * 0.215, y, 0, t);
            peak = Math.max(peak, speed.get());
        }
        assertTrue(Wind.airflow(peak, false) == 0.0, "the speed stays below the airflow: " + peak);
    }

    @Test
    @DisplayName("Speed: a teleport, a long pause, an unknown position or a reset start again from zero")
    void speedResets() {
        Wind.Speed speed = new Wind.Speed();
        long t = 0;
        speed.position(0, 100, 0, t);
        for (int i = 1; i <= 10; i++) {
            t += SECOND / 20;
            speed.position(i * 1.5, 100, 0, t);
        }
        assertEquals(30.0, speed.get(), 1e-6);
        t += SECOND / 20;
        speed.position(5000, 100, 0, t);
        assertEquals(0.0, speed.get(), "a teleport is no flight");
        t += SECOND / 20;
        speed.position(5001.5, 100, 0, t);
        assertEquals(30.0, speed.get(), 1e-6, "measured afresh from the new place");

        t += 3 * SECOND;
        speed.position(5100, 100, 0, t);
        assertEquals(0.0, speed.get(), "after a long pause");

        speed.position(Double.NaN, Double.NaN, Double.NaN, t + SECOND / 20);
        assertEquals(0.0, speed.get());
        speed.position(0, 0, 0, t + SECOND / 10);
        assertEquals(0.0, speed.get(), "the first position after an unknown one is no speed yet");

        speed.position(1.5, 0, 0, t + SECOND / 10 + SECOND / 20);
        assertEquals(30.0, speed.get(), 1e-6);
        speed.reset();
        assertEquals(0.0, speed.get());
    }

    @Test
    @DisplayName("Speed: positions closer than 20 ms are measured over the longer time")
    void speedTooSoon() {
        Wind.Speed speed = new Wind.Speed();
        speed.position(0, 100, 0, 0);
        speed.position(0.15, 100, 0, 5_000_000L);
        assertEquals(0.0, speed.get(), "5 ms is too soon to tell");
        speed.position(1.5, 100, 0, SECOND / 20);
        assertEquals(30.0, speed.get(), 1e-6, "measured from the first position");
    }

    @Test
    @DisplayName("The listener's place: the wind follows the height, the sky, the water and the weather")
    void listenerEnvironment() {
        ListenerEnvironment env = new ListenerEnvironment();
        PlaceTuning.Place peak = new PlaceTuning.Place("minecraft:overworld", "minecraft:jagged_peaks", 200, true);
        env.updatePlace(peak);
        assertEquals(1.0, env.windPlace(), 1e-12);
        env.update(false, EnvironmentEffects.Weather.THUNDER);
        env.updatePlace(peak);
        assertEquals(Wind.THUNDER_FACTOR, env.windPlace(), 1e-12);
        env.update(true, EnvironmentEffects.Weather.THUNDER);
        env.updatePlace(peak);
        assertEquals(0.0, env.windPlace(), "head under water");

        long t = 0;
        env.setPosition(0, 200, 0, t);
        for (int i = 1; i <= 3; i++) {
            env.setPosition(i * 1.8, 200, 0, t + i * SECOND / 20);
        }
        assertEquals(0.0, env.airflow(), "under water nothing rushes past the ears");
        env.update(false, CLEAR);
        assertTrue(env.airflow() > 0.9, "36 blocks per second: " + env.speed());
        env.reset();
        assertEquals(0.0, env.windPlace());
        assertEquals(0.0, env.speed());
    }
}
