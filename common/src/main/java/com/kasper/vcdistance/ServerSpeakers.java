package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.List;

/**
 * The loudspeakers of the Paper plugin: who is picked up by which speaker, and who hears it. Pure logic over
 * {@link ServerPlayers.Info}; sending the voice is {@link ServerWalls#onMicrophone}'s job.
 */
public final class ServerSpeakers {

    /** One speaker that picked a talker up, and the players who should hear it from there. */
    public record Delivery(Loudspeaker speaker, List<ServerPlayers.Info> listeners) {
    }

    private ServerSpeakers() {
    }

    /**
     * The speakers that pick up {@code talker} and, for each, the players within its radius, except the talker
     * and those who already hear the talker by distance ({@code hearRange}), or ignore them. A dead talker is
     * silent when the server says dead players are.
     */
    public static List<Delivery> deliveries(ServerSettings s, PlayerPrefs prefs, ServerPlayers players,
                                            ServerPlayers.Info talker, double hearRange) {
        if (!s.isServerSpeakers() || s.speakers().isEmpty() || (s.isDeadSilent() && !talker.alive())) {
            return List.of();
        }
        List<Delivery> out = new ArrayList<>();
        for (Loudspeaker speaker : s.speakers().values()) {
            if (!speaker.picksUp(talker)) {
                continue;
            }
            List<ServerPlayers.Info> listeners = new ArrayList<>();
            for (ServerPlayers.Info other : players.all()) {
                if (!other.id().equals(talker.id()) && speaker.reaches(other) && prefs.volume(other.id(), talker.id()) > 0
                        && !(Zone.sameWorld(talker.world(), other.world()) && talker.distanceTo(other) <= hearRange)) {
                    listeners.add(other);
                }
            }
            if (!listeners.isEmpty()) {
                out.add(new Delivery(speaker, listeners));
            }
        }
        return out;
    }
}
