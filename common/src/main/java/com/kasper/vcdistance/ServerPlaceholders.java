package com.kasper.vcdistance;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The values behind the PlaceholderAPI placeholders {@code %vcd_<name>%} (Paper): for scoreboards, tab
 * lists, holograms and chat formats. Pure logic over what the server already knows, so it can be asked
 * from any thread.
 */
public final class ServerPlaceholders {

    /** Every placeholder, in the order the documentation lists them. */
    public static final List<String> NAMES = List.of(
            "mode", "mode_name", "talking", "muted", "mute_left", "mute_reason", "range", "whisper_range",
            "walls", "zone", "addon", "talking_near", "muted_count");

    private ServerPlaceholders() {
    }

    /**
     * The value of {@code %vcd_<name>%} for {@code player} ({@code null} for a placeholder without a player).
     *
     * @return the text, or {@code null} for a name that is not a placeholder (PlaceholderAPI then leaves it as typed)
     */
    public static String value(String name, UUID player, ServerSettings settings, long nowNanos, long nowMillis) {
        return value(name, player, settings, nowNanos, nowMillis, other -> true);
    }

    /**
     * As above; {@code visible} says whom the player may see (vanish plugins), for {@code talking_near}, which
     * also stays empty while the server hides who is near ({@code allow_monitor=false}).
     */
    public static String value(String name, UUID player, ServerSettings settings, long nowNanos, long nowMillis,
                               java.util.function.Predicate<UUID> visible) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (key.equals("muted_count")) {
            return String.valueOf(settings.mutes(nowMillis).size());
        }
        if (!NAMES.contains(key)) {
            return null;
        }
        if (player == null) {
            return "";
        }
        ServerPlayers.Info me = AudioDistancePlugin.PLAYERS.get(player);
        PlayerPrefs.Prefs prefs = AudioDistancePlugin.PLAYER_PREFS.get(player);
        VoiceMute mute = settings.muteOf(player, nowMillis);
        return switch (key) {
            case "mode" -> prefs.mode().name().toLowerCase(Locale.ROOT);
            case "mode_name" -> ServerText.get(language(settings, me), "voice.btn." + prefs.mode().name().toLowerCase(Locale.ROOT));
            case "talking" -> String.valueOf(mute == null && AudioDistancePlugin.TALK.isTalking(player, nowNanos));
            case "muted" -> String.valueOf(mute != null);
            case "mute_left" -> mute == null ? "" : mute.isPermanent() ? "perm" : VoiceMute.formatDuration(mute.leftAt(nowMillis));
            case "mute_reason" -> mute == null ? "" : mute.reason();
            case "range", "whisper_range" -> me == null ? "" : AdminCommands.fmt(range(settings, me, key.equals("whisper_range")));
            case "walls" -> String.valueOf(settings.isServerWalls() && settings.profile().isOcclusionEnabled()
                    && (prefs.walls() || settings.wallsLocked()));
            case "zone" -> {
                Zone zone = settings.zoneOf(me);
                yield zone == null ? "" : zone.name();
            }
            case "addon" -> String.valueOf(AudioDistancePlugin.SERVER_WALLS.hasAddon(player));
            case "talking_near" -> me == null || !settings.isMonitorAllowed() ? ""
                    : ServerHooks.talkingText(settings, me, AudioDistancePlugin.PLAYERS.all(), nowNanos, visible);
            default -> "";
        };
    }

    private static double range(ServerSettings settings, ServerPlayers.Info me, boolean whispering) {
        double voice = AudioDistancePlugin.serverVoiceDistance();
        double whisper = AudioDistancePlugin.serverWhisperDistance();
        if (voice <= 0.0) {
            voice = AudioDistancePlugin.FALLBACK_DISTANCE;
            whisper = voice / 2.0;
        }
        if (whisper <= 0.0) {
            whisper = voice / 2.0;
        }
        return ServerRange.rangeOf(settings, me, whispering, voice, whisper);
    }

    private static String language(ServerSettings settings, ServerPlayers.Info me) {
        return settings.languageFor(me == null ? "" : me.language());
    }
}
