package com.kasper.vcdistance;

/**
 * Where the listener is, acoustically: how echoey the space around them is, whether their head is
 * under water, the weather over them and the wind. Written by the client tick, read by the audio threads.
 */
public final class ListenerEnvironment {

    private final RoomGlide room = new RoomGlide(false);
    private volatile boolean underWater;
    private volatile EnvironmentEffects.Weather weather = EnvironmentEffects.Weather.CLEAR;
    /** The listener's head in world coordinates, for directions to doorways; NaN when unknown. */
    private volatile double[] position = {Double.NaN, Double.NaN, Double.NaN};
    private volatile PlaceTuning.Tuning place = PlaceTuning.Tuning.NONE;
    private final Wind.Speed speed = new Wind.Speed();
    private volatile double windPlace;

    /** The echo around the listener right now, gliding towards the latest measurement. */
    public RoomEstimate room() {
        return room.get(System.nanoTime());
    }

    public boolean isUnderWater() {
        return underWater;
    }

    public EnvironmentEffects.Weather weather() {
        return weather;
    }

    /** A new room measurement; the echo glides towards it over about {@link RoomGlide#SETTLE_SECONDS}. */
    public void updateRoom(RoomEstimate measured) {
        room.set(measured, System.nanoTime());
    }

    public void update(boolean underWater, EnvironmentEffects.Weather weather) {
        this.underWater = underWater;
        this.weather = weather == null ? EnvironmentEffects.Weather.CLEAR : weather;
    }

    /** What the place does to the sound (before the {@code place_tuning} switch). */
    public PlaceTuning.Tuning place() {
        return place;
    }

    public void updatePlace(PlaceTuning.Place where) {
        place = PlaceTuning.of(where);
        windPlace = Wind.place(where, underWater, weather);
    }

    /** How windy it is where the listener stands ({@link Wind#place}). */
    public double windPlace() {
        return windPlace;
    }

    /** The air rushing past the listener's ears ({@link Wind#airflow}). */
    public double airflow() {
        return Wind.airflow(speed.get(), underWater);
    }

    /** Blocks per second the listener moves, smoothed. */
    public double speed() {
        return speed.get();
    }

    public double[] position() {
        return position;
    }

    public void setPosition(double x, double y, double z) {
        setPosition(x, y, z, System.nanoTime());
    }

    public void setPosition(double x, double y, double z, long nowNanos) {
        position = new double[]{x, y, z};
        speed.position(x, y, z, nowNanos);
    }

    public void reset() {
        position = new double[]{Double.NaN, Double.NaN, Double.NaN};
        room.jump(RoomEstimate.OPEN, System.nanoTime());
        underWater = false;
        weather = EnvironmentEffects.Weather.CLEAR;
        place = PlaceTuning.Tuning.NONE;
        speed.reset();
        windPlace = 0.0;
    }

    /** The stronger of two weathers: a thunderstorm at either end covers the voice. */
    public static EnvironmentEffects.Weather worse(EnvironmentEffects.Weather a, EnvironmentEffects.Weather b) {
        if (a == null) {
            return b == null ? EnvironmentEffects.Weather.CLEAR : b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
