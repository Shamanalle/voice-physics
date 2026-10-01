package com.kasper.vcdistance;

/**
 * The sculk reaction of the Paper plugin: a shout (a range raised with {@code /voice shout}, or a megaphone)
 * is a game event that sculk sensors and wardens react to. Whispers, sneaking and ordinary talking are not.
 * Pure decision; the plugin sends the game event on the player's thread.
 */
public final class ServerSculk {

    /** A player raises the event at most this often: a voice is 50 packets a second. */
    public static final long COOLDOWN_NANOS = 3_000_000_000L;

    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<java.util.UUID> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private ServerSculk() {
    }

    /**
     * Called from the voice thread for a loud voice: remembers that {@code player} should raise a game event,
     * unless they did within the cooldown. The platform collects them with {@link #drain()} on its own thread.
     */
    public static void report(java.util.UUID player, long nowNanos) {
        Long last = LAST.get(player);
        if (due(last == null ? 0L : last, nowNanos)) {
            LAST.put(player, nowNanos);
            PENDING.add(player);
        }
    }

    /** The players whose voice was loud since the last call; each is returned once. */
    public static java.util.List<java.util.UUID> drain() {
        java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
        for (java.util.UUID id : com.kasper.vcdistance.Jv.copyOf(PENDING)) {
            if (PENDING.remove(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** A player left: forget their cooldown. */
    public static void forget(java.util.UUID player) {
        LAST.remove(player);
        PENDING.remove(player);
    }

    /** Whether {@code speaker}'s voice right now is loud enough to be heard by the sculk. */
    public static boolean loud(ServerSettings s, PlayerPrefs prefs, ServerPlayers.Info speaker, boolean whispering) {
        if (!s.isServerSculk() || speaker == null || whispering || speaker.sneaking() || !speaker.alive() || speaker.spectator()) {
            return false;
        }
        return ServerRange.isMegaphone(s, speaker) || prefs.rangeFactor(speaker.id()) > 1.0;
    }

    /** Whether the cooldown since {@code lastNanos} (0 = never) is over at {@code nowNanos}. */
    public static boolean due(long lastNanos, long nowNanos) {
        return lastNanos == 0L || nowNanos - lastNanos >= COOLDOWN_NANOS;
    }
}
