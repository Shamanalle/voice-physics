package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.List;

/**
 * The radio of the Paper plugin: players tuned to the same frequency (1-9999, {@code /voice radio})
 * hear each other at any distance, in any world, like a voice in their ear. With {@code radio_item} set
 * they must also hold that item. Pure logic over {@link ServerPlayers.Info}: who gets a copy of a
 * voice. Sending it is {@link ServerWalls#onMicrophone}'s job.
 */
public final class ServerRadio {

    private ServerRadio() {
    }

    /** The frequency {@code player} uses right now, or 0: the switch is off, the radio is off, or the item is not held. */
    public static int frequency(ServerSettings s, PlayerPrefs prefs, ServerPlayers.Info player) {
        if (!s.isServerRadio() || player == null) {
            return 0;
        }
        int frequency = prefs.radioOf(player.id());
        if (frequency <= 0) {
            return 0;
        }
        String item = s.getRadioItem();
        return item.isEmpty() || player.holds(item) ? frequency : 0;
    }

    /**
     * Who gets {@code speaker}'s voice over the radio: everyone else on the same frequency, except those who
     * already hear the voice by distance ({@code hearRange}) and those who ignore the speaker. A dead speaker
     * is silent when the server says dead players are.
     */
    public static List<ServerPlayers.Info> receivers(ServerSettings s, PlayerPrefs prefs, ServerPlayers players,
                                                     ServerPlayers.Info speaker, double hearRange) {
        int frequency = frequency(s, prefs, speaker);
        if (frequency == 0 || (s.isDeadSilent() && !speaker.alive())) {
            return Jv.listOf();
        }
        List<ServerPlayers.Info> out = new ArrayList<>();
        for (ServerPlayers.Info other : players.all()) {
            if (other.id().equals(speaker.id()) || frequency(s, prefs, other) != frequency
                    || prefs.volume(other.id(), speaker.id()) <= 0) {
                continue;
            }
            boolean near = speaker.world() != null && other.world() != null
                    && Zone.sameWorld(speaker.world(), other.world()) && speaker.distanceTo(other) <= hearRange;
            if (!near) {
                out.add(other);
            }
        }
        return out;
    }
}
