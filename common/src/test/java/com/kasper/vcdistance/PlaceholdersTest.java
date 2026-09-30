package com.kasper.vcdistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The values behind %vcd_...% (PlaceholderAPI on Paper). */
public class PlaceholdersTest {

    private static final ServerPlayers.Info ANNA = new ServerPlayers.Info(UUID.nameUUIDFromBytes("PAnna".getBytes()), "Anna",
            "world", 0, 64, 0, false, true, false, "", "", List.of(), "ru_ru");
    private static final ServerPlayers.Info BOB = new ServerPlayers.Info(UUID.nameUUIDFromBytes("PBob".getBytes()), "Bob",
            "world", 10, 64, 0, false, true, false, "", "", List.of(), "en_us");

    @AfterEach
    void clear() {
        AudioDistancePlugin.PLAYERS.clear();
        AudioDistancePlugin.TALK.clear();
        AudioDistancePlugin.PLAYER_PREFS.reset(ANNA.id());
        AudioDistancePlugin.PLAYER_PREFS.forget(ANNA.id());
        AudioDistancePlugin.SERVER_SETTINGS.unmute(BOB.id());
    }

    private static String v(String name, UUID player) {
        return ServerPlaceholders.value(name, player, AudioDistancePlugin.SERVER_SETTINGS, System.nanoTime(), System.currentTimeMillis());
    }

    @Test
    @DisplayName("Mode, range, talking, mute and the rest, for a player; unknown names stay as typed")
    void values() {
        AudioDistancePlugin.PLAYERS.update(ANNA);
        AudioDistancePlugin.PLAYERS.update(BOB);
        assertEquals("normal", v("mode", ANNA.id()));
        AudioDistancePlugin.PLAYER_PREFS.setMode(ANNA.id(), PlayerPrefs.Mode.QUIET);
        assertEquals("quiet", v("MODE", ANNA.id()));
        assertEquals(ServerText.get("ru_ru", "voice.btn.quiet"), v("mode_name", ANNA.id()), "in the player's language");
        double full = AudioDistancePlugin.FALLBACK_DISTANCE;
        assertEquals(AdminCommands.fmt(full * PlayerPrefs.QUIET_FACTOR), v("range", ANNA.id()));
        assertEquals(AdminCommands.fmt(full), v("range", BOB.id()));

        assertEquals("false", v("talking", BOB.id()));
        assertEquals("", v("talking_near", ANNA.id()));
        AudioDistancePlugin.TALK.spoke(BOB.id(), System.nanoTime());
        assertEquals("true", v("talking", BOB.id()));
        assertTrue(v("talking_near", ANNA.id()).contains("Bob 10m"), v("talking_near", ANNA.id()));
        assertEquals("", ServerPlaceholders.value("talking_near", ANNA.id(), AudioDistancePlugin.SERVER_SETTINGS, System.nanoTime(),
                System.currentTimeMillis(), other -> false), "vanished players stay hidden");

        assertEquals("false", v("muted", BOB.id()));
        assertEquals("", v("mute_left", BOB.id()));
        AudioDistancePlugin.SERVER_SETTINGS.mute(new VoiceMute(BOB.id(), "Bob", System.currentTimeMillis() + 3_600_000L, "Admin", "spam"));
        assertEquals("true", v("muted", BOB.id()));
        assertEquals("false", v("talking", BOB.id()), "a muted player never shows as talking");
        assertEquals("1h", v("mute_left", BOB.id()));
        assertEquals("spam", v("mute_reason", BOB.id()));
        assertEquals("1", v("muted_count", null));

        assertEquals("false", v("addon", ANNA.id()));
        assertEquals("", v("zone", ANNA.id()));
        assertEquals("", v("range", UUID.randomUUID()), "an unknown player has no range");
        assertEquals("", v("mode", null), "no player: empty");
        assertNull(v("banana", ANNA.id()));
        assertEquals(13, ServerPlaceholders.NAMES.size());
    }
}
