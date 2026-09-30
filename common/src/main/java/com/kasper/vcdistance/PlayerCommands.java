package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /voice}: what any player can set for themselves without the client mod. How loud they talk
 * (quiet, normal, shout), whether walls muffle what they hear, how loud each other player is to them
 * (0 = ignored) and a "who is talking" line above the hotbar. The choices are kept in
 * {@link PlayerPrefs}; the voice rules and the wall filter read them for every voice packet.
 * <p>
 * Replies are plain text lines in the player's language ({@link ServerText}); the platform sends them.
 */
public final class PlayerCommands {

    public static final String NAME = "voice";
    /** Use /voice at all (default: everyone). */
    public static final String PERM = "vcd.player";
    /** Shout: a longer voice range (default: operators). */
    public static final String PERM_SHOUT = "vcd.shout";

    private static final String[] SUBCOMMANDS = {"status", "mode", "walls", "hud", "volume", "ignore", "unignore", "reset", "help"};
    private static final String[] ON_OFF = {"on", "off"};

    /** What the platform tells the command about the player running it. */
    public interface Context {

        boolean allows(String permission);
    }

    private PlayerCommands() {
    }

    /**
     * Runs one command.
     *
     * @param language the player's client language ("ru_ru"), or ""
     * @param input    what follows {@code /voice}
     * @return the reply lines
     */
    public static List<String> run(UUID player, String language, String input, Context ctx) {
        String lang = AudioDistancePlugin.SERVER_SETTINGS.languageFor(language);
        if (!ctx.allows(PERM)) {
            return List.of(ServerText.get(lang, "no_permission", "/voice", PERM));
        }
        String[] args = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        PlayerPrefs prefs = AudioDistancePlugin.PLAYER_PREFS;
        switch (sub) {
            case "status":
                return status(player, lang, prefs);
            case "mode":
                return mode(player, lang, args, ctx, prefs);
            case "walls":
                if (AudioDistancePlugin.SERVER_SETTINGS.wallsLocked()) {
                    return List.of(ServerText.get(lang, "voice.walls.locked"));
                }
                return toggle(args, lang, "voice.walls", on -> prefs.setWalls(player, on));
            case "hud":
                return toggle(args, lang, "voice.hud", on -> prefs.setHud(player, on));
            case "volume":
                if (args.length < 3) {
                    return List.of(ServerText.get(lang, "voice.help"));
                }
                return volume(player, lang, args[1], args[2], prefs);
            case "ignore":
                return args.length < 2 ? List.of(ServerText.get(lang, "voice.help"))
                        : volume(player, lang, args[1], "0", prefs);
            case "unignore":
                return args.length < 2 ? List.of(ServerText.get(lang, "voice.help"))
                        : volume(player, lang, args[1], "100", prefs);
            case "reset":
                prefs.reset(player);
                return List.of(ServerText.get(lang, "voice.reset"));
            default:
                return List.of(ServerText.get(lang, "voice.help"));
        }
    }

    private static List<String> status(UUID player, String lang, PlayerPrefs prefs) {
        PlayerPrefs.Prefs p = prefs.get(player);
        List<String> out = new ArrayList<>();
        out.add(ServerText.get(lang, "voice.status", modeName(lang, p.mode()),
                ServerText.get(lang, p.walls() ? "on" : "off"), ServerText.get(lang, p.hud() ? "on" : "off")));
        if (!p.volumes().isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (var e : p.volumes().entrySet()) {
                ServerPlayers.Info other = AudioDistancePlugin.PLAYERS.get(e.getKey());
                parts.add((other != null ? other.name() : e.getKey().toString().substring(0, 8)) + " " + e.getValue() + "%");
            }
            out.add(ServerText.get(lang, "voice.status.volumes", String.join(", ", parts)));
        }
        out.add(ServerText.get(lang, "voice.help"));
        return out;
    }

    private static List<String> mode(UUID player, String lang, String[] args, Context ctx, PlayerPrefs prefs) {
        if (args.length < 2) {
            return List.of(ServerText.get(lang, "voice.help"));
        }
        PlayerPrefs.Mode mode = PlayerPrefs.Mode.of(args[1]);
        if (!mode.name().equalsIgnoreCase(args[1])) {
            return List.of(ServerText.get(lang, "voice.help"));
        }
        if (mode == PlayerPrefs.Mode.SHOUT && !ctx.allows(PERM_SHOUT)) {
            return List.of(ServerText.get(lang, "voice.mode.denied", PERM_SHOUT));
        }
        prefs.setMode(player, mode);
        double factor = mode == PlayerPrefs.Mode.QUIET ? PlayerPrefs.QUIET_FACTOR
                : mode == PlayerPrefs.Mode.SHOUT ? PlayerPrefs.SHOUT_FACTOR : 1.0;
        return List.of(ServerText.get(lang, "voice.mode.set", modeName(lang, mode), ConfigWriter.number(factor)));
    }

    private static List<String> toggle(String[] args, String lang, String key, java.util.function.Consumer<Boolean> set) {
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            return List.of(ServerText.get(lang, "voice.help"));
        }
        boolean on = args[1].equalsIgnoreCase("on");
        set.accept(on);
        return List.of(ServerText.get(lang, key + (on ? ".on" : ".off")));
    }

    private static List<String> volume(UUID player, String lang, String who, String value, PlayerPrefs prefs) {
        ServerPlayers.Info other = AudioDistancePlugin.PLAYERS.byName(who);
        if (other == null) {
            return List.of(ServerText.get(lang, "voice.unknown_player", who));
        }
        if (other.id().equals(player)) {
            return List.of(ServerText.get(lang, "voice.self"));
        }
        int percent = parsePercent(value);
        if (percent < 0) {
            return List.of(ServerText.get(lang, "voice.bad_value", value));
        }
        prefs.setVolume(player, other.id(), percent);
        List<String> out = new ArrayList<>();
        if (percent <= 0) {
            out.add(ServerText.get(lang, "voice.ignore.set", other.name()));
        } else if (percent >= 100) {
            out.add(ServerText.get(lang, "voice.ignore.unset", other.name()));
        } else {
            out.add(ServerText.get(lang, "voice.volume.set", other.name(), percent));
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player)) {
                out.add(ServerText.get(lang, "voice.volume.addon"));
            }
        }
        return out;
    }

    /** 0-100 with an optional %, or -1 when it is not that. */
    static int parsePercent(String text) {
        String t = text.endsWith("%") ? text.substring(0, text.length() - 1) : text;
        try {
            int v = Integer.parseInt(t.trim());
            return v < 0 || v > 100 ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String modeName(String lang, PlayerPrefs.Mode mode) {
        return ServerText.get(lang, "voice.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** Completions for the last word of {@code input}. */
    public static List<String> suggest(String input, Context ctx) {
        if (!ctx.allows(PERM)) {
            return List.of();
        }
        String text = input == null ? "" : input;
        String[] words = text.split("\\s+", -1);
        String last = words[words.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (words.length <= 1) {
            options.addAll(List.of(SUBCOMMANDS));
        } else {
            switch (words[0].toLowerCase(Locale.ROOT)) {
                case "mode" -> {
                    if (words.length == 2) {
                        options.add("quiet");
                        options.add("normal");
                        if (ctx.allows(PERM_SHOUT)) {
                            options.add("shout");
                        }
                    }
                }
                case "walls", "hud" -> {
                    if (words.length == 2) {
                        options.addAll(List.of(ON_OFF));
                    }
                }
                case "volume", "ignore", "unignore" -> {
                    if (words.length == 2) {
                        for (ServerPlayers.Info p : AudioDistancePlugin.PLAYERS.all()) {
                            options.add(p.name());
                        }
                    } else if (words.length == 3 && words[0].equalsIgnoreCase("volume")) {
                        options.addAll(List.of("0", "25", "50", "75", "100"));
                    }
                }
                default -> {
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o != null && o.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(o);
            }
        }
        return out;
    }
}
