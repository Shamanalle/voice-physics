package com.kasper.vcdistance;

/**
 * What water, weather, distance and the space around the speaker and the listener do to a voice for a
 * player without the addon, worked out on the server. The same physics as the addon's own
 * ({@link EnvironmentEffects}, {@link RoomEstimate}), so a player with the addon and one without hear
 * about the same; only the way it reaches them differs (the server changes the audio, the addon does it
 * at home).
 * <p>
 * Pure logic over {@link ServerPlayers.Info}, so it runs on Simple Voice Chat's thread.
 */
public final class ServerEffects {

    /** The dullest a voice gets from the air at the edge of its range (a muffle of 0.55 is about 2 kHz). */
    static final double AIR_MUFFLE_MAX = 0.55;
    /** Air dulls a voice only beyond this share of its range. */
    static final double AIR_START = 0.3;

    private ServerEffects() {
    }

    /**
     * Water, weather and air between two players, as muffle and loss (to be combined with the walls').
     *
     * @param range how far the voice carries here, in blocks (rain and air work on the share of it)
     */
    public static EnvironmentEffects.Effect atmosphere(ServerSettings s, ServerPlayers.Info speaker, ServerPlayers.Info listener,
                                                        double range) {
        if (speaker == null || listener == null || !s.hasServerRealism()) {
            return EnvironmentEffects.Effect.NONE;
        }
        EnvironmentEffects.Effect out = EnvironmentEffects.Effect.NONE;
        double share = range > 0.0 ? speaker.distanceTo(listener) / range : 0.0;
        if (s.isServerEffects()) {
            DistanceConfig profile = s.profile();
            if (profile.isUnderwaterEnabled()) {
                out = out.plus(EnvironmentEffects.water(listener.underwater(), speaker.underwater(), profile.getUnderwaterStrength()));
            }
            if (profile.isWeatherEnabled()) {
                out = out.plus(EnvironmentEffects.weather(ListenerEnvironment.worse(listener.weather(), speaker.weather()),
                        share, profile.getWeatherStrength()));
            }
        }
        if (s.isServerAir()) {
            out = out.plus(air(share));
        }
        return out;
    }

    /** The dulling of a voice {@code share} (0 - 1) of the way to the edge of its range: none near, a smooth rise, most at the edge. */
    static EnvironmentEffects.Effect air(double share) {
        double t = Math.max(0.0, Math.min(1.0, (share - AIR_START) / (1.0 - AIR_START)));
        double smooth = t * t * (3.0 - 2.0 * t);
        return new EnvironmentEffects.Effect(AIR_MUFFLE_MAX * smooth, 0.0);
    }

    /**
     * The space a voice echoes in for one listener: the listener's own and the speaker's together (a
     * friend shouting in a cave echoes for someone outside it). {@code null} when the echo is off.
     */
    public static RoomEstimate room(ServerSettings s, ServerRooms rooms, ServerPlayers.Info speaker, ServerPlayers.Info listener,
                                    long nowNanos) {
        if (!s.isServerEffects() || !s.profile().isReverbEnabled() || listener == null) {
            return null;
        }
        RoomEstimate mine = rooms.of(listener.id(), nowNanos);
        if (speaker == null || speaker.id().equals(listener.id())) {
            return mine;
        }
        // Standing next to each other they share one space
        if (speaker.distanceTo(listener) <= SAME_ROOM_DISTANCE) {
            return mine;
        }
        return RoomEstimate.combine(mine, rooms.of(speaker.id(), nowNanos));
    }

    /** Closer than this two players are in the same room. */
    static final double SAME_ROOM_DISTANCE = 4.0;

    /**
     * How loud the room's echo and the repeats off cliffs are for one voice, 0 - 1 each. A voice next to
     * you stays clear and a far one sounds like the room (the echo share grows with distance); the voice
     * fades with distance while a real room's echo stays about as loud, so far voices get part of that
     * back (at most twice), as with the addon.
     *
     * @return {wet, echo}
     */
    public static double[] echoLevels(RoomEstimate room, double distance, double range, double strength) {
        if (room == null || !room.isAudible() || strength <= 0.0) {
            return new double[]{0.0, 0.0};
        }
        double share = room.distanceShare(distance);
        double boost = 1.0;
        if (distance >= 0.0 && range > 0.0) {
            double curve = Math.max(0.0, 1.0 - distance / range);
            boost = Math.min(2.0, 1.0 / Math.sqrt(Math.max(0.25, curve)));
        }
        double wet = Math.min(1.0, room.wet() * strength * share * boost);
        double echo = room.echoes().isEmpty() ? 0.0 : Math.min(1.0, strength * (0.4 + 0.6 * share) * boost);
        return new double[]{wet, echo};
    }
}
