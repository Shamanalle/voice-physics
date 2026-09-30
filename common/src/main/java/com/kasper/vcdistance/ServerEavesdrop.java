package com.kasper.vcdistance;

/**
 * The eavesdrop item of the Paper plugin: a listener who holds it hears walls as thinner. Pure logic over
 * {@link ServerPlayers.Info}; {@link ServerWalls} applies the factor to the wall strength.
 */
public final class ServerEavesdrop {

    private ServerEavesdrop() {
    }

    /** Whether {@code listener} is eavesdropping now: the switch is on and they hold the item. */
    public static boolean active(ServerSettings s, ServerPlayers.Info listener) {
        return s.isServerEavesdrop() && listener != null && !s.getEavesdropItem().isEmpty()
                && listener.holds(s.getEavesdropItem());
    }

    /** What the wall muffling is multiplied by for {@code listener}: 1 normally, the configured factor while eavesdropping. */
    public static double factor(ServerSettings s, ServerPlayers.Info listener) {
        return active(s, listener) ? s.getEavesdropFactor() : 1.0;
    }
}
