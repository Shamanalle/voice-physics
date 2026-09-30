package com.kasper.vcdistance;

import java.util.Locale;
import java.util.UUID;

/**
 * A player an admin muted with {@code /vcd mute}: nobody hears their voice (nearby or in a group) until
 * the time runs out or {@code /vcd unmute}. Kept in the server's settings file, so it survives a restart,
 * {@code /vcd undo} takes it back and the change log shows who did it.
 *
 * @param player the muted player
 * @param name   their name when muted (for lists and for unmuting after they left)
 * @param until  when it ends, in epoch milliseconds; 0 = until unmuted
 * @param by     who muted them (a name, or {@link ChangeLog#CONSOLE})
 * @param reason why, or ""
 */
public record VoiceMute(UUID player, String name, long until, String by, String reason) {

    /** Shortest and longest mute that can be given with a time. */
    static final long MIN_MILLIS = 1_000L;
    static final long MAX_MILLIS = 365L * 24 * 3600 * 1000;

    public VoiceMute {
        name = name == null ? "" : name.trim();
        by = by == null ? "" : by.trim();
        reason = reason == null ? "" : reason.trim();
        until = Math.max(0L, until);
    }

    public boolean isPermanent() {
        return until <= 0L;
    }

    /** Whether the mute still holds at {@code nowMillis}. */
    public boolean activeAt(long nowMillis) {
        return isPermanent() || nowMillis < until;
    }

    /** Time left at {@code nowMillis}; 0 for a mute without end or one that is over. */
    public long leftAt(long nowMillis) {
        return isPermanent() ? 0L : Math.max(0L, until - nowMillis);
    }

    /** "until|name|by|reason" for the settings file; the reason goes last, so it may hold anything. */
    String encode() {
        return until + "|" + name.replace("|", "") + "|" + by.replace("|", "") + "|" + reason.replace('\n', ' ');
    }

    /** @return the mute, or {@code null} when the value is damaged */
    static VoiceMute decode(UUID player, String text) {
        if (player == null || text == null) {
            return null;
        }
        String[] parts = text.split("\\|", 4);
        try {
            long until = Long.parseLong(parts[0].trim());
            return new VoiceMute(player, parts.length > 1 ? parts[1] : "", until, parts.length > 2 ? parts[2] : "",
                    parts.length > 3 ? parts[3] : "");
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A time as typed in a command: "30s", "10m", "2h", "1d", "1w", or combined "1h30m"; "perm",
     * "forever" or "permanent" for no end (0).
     *
     * @return milliseconds, 0 for no end, or {@code null} when {@code text} is not a time
     */
    public static Long parseDuration(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.equals("perm") || t.equals("forever") || t.equals("permanent")) {
            return 0L;
        }
        if (t.isEmpty() || !Character.isDigit(t.charAt(0))) {
            return null;
        }
        long total = 0L;
        long number = -1L;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) {
                number = (number < 0 ? 0 : number) * 10 + (c - '0');
                if (number > 1_000_000L) {
                    return null;
                }
                continue;
            }
            long unit = switch (c) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 7 * 86_400_000L;
                default -> -1L;
            };
            if (unit < 0 || number < 0) {
                return null;
            }
            total += number * unit;
            number = -1L;
        }
        if (number >= 0) {
            // A bare number is minutes
            total += number * 60_000L;
        }
        if (total < MIN_MILLIS || total > MAX_MILLIS) {
            return null;
        }
        return total;
    }

    /** "1d 2h", "15m", "40s": the two largest units, rounded up so a mute never shows "0s" while it holds. */
    public static String formatDuration(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        long[] sizes = {7 * 86_400L, 86_400L, 3_600L, 60L, 1L};
        String[] units = {"w", "d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < sizes.length && shown < 2; i++) {
            long n = seconds / sizes[i];
            if (n > 0) {
                out.append(shown == 0 ? "" : " ").append(n).append(units[i]);
                seconds -= n * sizes[i];
                shown++;
            } else if (shown > 0) {
                break;
            }
        }
        return out.toString();
    }
}
