package com.kasper.vcdistance;

import com.kasper.vcdistance.AdminCommands.Messages;
import com.kasper.vcdistance.CommandReply.Click;
import com.kasper.vcdistance.CommandReply.LineBuilder;
import com.kasper.vcdistance.CommandReply.Span;
import com.kasper.vcdistance.CommandReply.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /voice}: what any player can set for themselves without the client mod. How loud they talk
 * (quiet, normal, shout), whether walls muffle what they hear, how loud each other player is to them
 * (0 = ignored) and a "who is talking" line above the hotbar. The choices are kept in
 * {@link PlayerPrefs}; the voice rules and the wall filter read them for every voice packet.
 * <pre>
 * /voice [status]                          your settings, with buttons
 * /voice mode quiet|normal|shout           how far your voice carries
 * /voice walls on|off | hud on|off         walls muffling what you hear | the talking line
 * /voice volume &lt;player&gt; 0-100 | ignore | unignore
 * /voice undo | reset | menu | help [command]
 * </pre>
 * Replies are {@link CommandReply} lines in the player's language ({@link ServerText}) with buttons
 * for the next step, and every change comes with an Undo; the platform shows them.
 */
public final class PlayerCommands {

    public static final String NAME = "voice";
    /** Use /voice at all (default: everyone). */
    public static final String PERM = "vcd.player";
    /** Shout: a longer voice range (default: operators). */
    public static final String PERM_SHOUT = "vcd.shout";
    /** The parts of /voice, each on its own so a server can take one away (all default to everyone). */
    public static final String PERM_MODE = "vcd.player.mode";
    public static final String PERM_WALLS = "vcd.player.walls";
    public static final String PERM_HUD = "vcd.player.hud";
    public static final String PERM_VOLUME = "vcd.player.volume";
    public static final String PERM_MENU = "vcd.player.menu";

    static final String[] SUBCOMMANDS = {"status", "mode", "walls", "hud", "volume", "ignore", "unignore", "undo", "reset", "menu", "help"};
    private static final String[] ON_OFF = {"on", "off"};
    /** Players listed in the status. */
    static final int LIST_VOLUMES = 8;

    /** Examples per topic of {@code /voice help}; commands, so the same in every language. */
    static final Map<String, String[]> EXAMPLES = Map.ofEntries(
            Map.entry("status", new String[]{"status"}),
            Map.entry("mode", new String[]{"mode quiet", "mode normal", "mode shout"}),
            Map.entry("walls", new String[]{"walls on", "walls off"}),
            Map.entry("hud", new String[]{"hud on", "hud off"}),
            Map.entry("volume", new String[]{"volume Steve 50", "volume Steve 100"}),
            Map.entry("ignore", new String[]{"ignore Steve"}),
            Map.entry("unignore", new String[]{"unignore Steve"}),
            Map.entry("undo", new String[]{"undo"}),
            Map.entry("reset", new String[]{"reset"}),
            Map.entry("menu", new String[]{"menu"}));
    /** The topics in the order {@code /voice help} lists them. */
    static final String[] TOPICS = {"status", "mode", "walls", "hud", "volume", "ignore", "unignore", "undo", "reset", "menu"};

    /** What the platform tells the command about the player running it. */
    public interface Context {

        boolean allows(String permission);

        /** Opens the settings as a menu; {@code false} when this platform has none. */
        default boolean openMenu() {
            return false;
        }
    }

    private PlayerCommands() {
    }

    /**
     * Runs one command.
     *
     * @param language the player's client language ("ru_ru"), or ""
     * @param input    what follows {@code /voice}
     * @return the reply lines, with buttons
     */
    public static CommandReply execute(UUID player, String language, String input, Context ctx) {
        Run r = new Run(player, language, input, ctx);
        if (!ctx.allows(PERM)) {
            r.error("no_permission", "/voice", PERM);
            return r.reply;
        }
        String sub = r.args.length == 0 ? "status" : r.args[0].toLowerCase(Locale.ROOT);
        String needed = permissionFor(sub);
        if (needed != null && !ctx.allows(needed)) {
            r.error("no_permission", "/voice " + sub, needed);
            return r.reply;
        }
        switch (sub) {
            case "status" -> status(r);
            case "mode" -> mode(r);
            case "walls" -> walls(r);
            case "hud" -> hud(r);
            case "volume" -> {
                if (r.args.length < 3) {
                    r.badValue("volume " + r.arg(1), r.arg(2), "<player> 0-100", "volume");
                } else {
                    volume(r, r.args[1], r.args[2]);
                }
            }
            case "ignore" -> {
                if (r.args.length < 2) {
                    r.badValue("ignore", "", "<player>", "ignore");
                } else {
                    volume(r, r.args[1], "0");
                }
            }
            case "unignore" -> {
                if (r.args.length < 2) {
                    r.badValue("unignore", "", "<player>", "unignore");
                } else {
                    volume(r, r.args[1], "100");
                }
            }
            case "undo" -> undo(r);
            case "reset" -> {
                AudioDistancePlugin.PLAYER_PREFS.reset(player);
                r.saved("voice.reset");
            }
            case "menu" -> {
                if (!ctx.openMenu()) {
                    r.line(Style.WARN, r.m.get("voice.menu.unavailable"));
                }
            }
            case "help", "?" -> help(r, r.arg(1).toLowerCase(Locale.ROOT));
            default -> unknown(r, sub);
        }
        return r.reply;
    }

    /** Runs one command and gives the reply as text lines, buttons included (the tests). */
    public static List<String> run(UUID player, String language, String input, Context ctx) {
        return execute(player, language, input, ctx).plain();
    }

    /** A range factor as text: 0.5, 1, 2. */
    public static String factorText(double factor) {
        return ConfigWriter.number(factor);
    }

    /** The permission a subcommand needs on top of {@link #PERM}, or {@code null} when it needs none. */
    public static String permissionFor(String sub) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "mode" -> PERM_MODE;
            case "walls" -> PERM_WALLS;
            case "hud" -> PERM_HUD;
            case "volume", "ignore", "unignore" -> PERM_VOLUME;
            case "menu" -> PERM_MENU;
            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private static void status(Run r) {
        Messages m = r.m;
        PlayerPrefs prefs = AudioDistancePlugin.PLAYER_PREFS;
        PlayerPrefs.Prefs p = prefs.get(r.player);
        ServerSettings settings = AudioDistancePlugin.SERVER_SETTINGS;
        r.line(Style.TITLE, m.get("voice.status.title"));
        long now = System.currentTimeMillis();
        VoiceMute mute = settings.muteOf(r.player, now);
        if (mute != null) {
            LineBuilder muted = CommandReply.line().addAll(m.spans("voice.status.muted", Style.WARN, AdminCommands.muteTime(m, mute, now)));
            if (!mute.reason().isEmpty()) {
                muted.text(" - " + mute.reason(), Style.MUTED);
            }
            r.reply.add(muted);
        }

        // How you talk: the current one as a value, the others as buttons
        LineBuilder modes = CommandReply.line().text(m.get("voice.status.mode") + " ", Style.LABEL);
        for (PlayerPrefs.Mode mode : PlayerPrefs.Mode.values()) {
            String id = mode.name().toLowerCase(Locale.ROOT);
            String label = m.get("voice.btn." + id);
            if (mode == p.mode()) {
                modes.text("<" + label + ">", Style.VALUE).text(" ", Style.PLAIN);
            } else if (mode != PlayerPrefs.Mode.SHOUT || r.ctx.allows(PERM_SHOUT)) {
                modes.button(label, Click.RUN, "/voice mode " + id, m.get("hover.run", "/voice mode " + id));
            }
        }
        r.reply.add(modes);

        ServerPlayers.Info me = AudioDistancePlugin.PLAYERS.get(r.player);
        if (me != null) {
            double voice = AudioDistancePlugin.serverVoiceDistance();
            double whisper = AudioDistancePlugin.serverWhisperDistance();
            if (voice <= 0.0) {
                voice = AudioDistancePlugin.FALLBACK_DISTANCE;
                whisper = voice / 2.0;
            }
            r.reply.add(CommandReply.line().addAll(m.spans("voice.status.range", Style.MUTED,
                    AdminCommands.fmt(ServerRange.rangeOf(settings, me, false, voice, whisper)),
                    AdminCommands.fmt(ServerRange.rangeOf(settings, me, true, voice, whisper)))));
        }

        // Walls
        boolean locked = settings.wallsLocked();
        LineBuilder walls = CommandReply.line().addAll(m.spans(locked ? "voice.status.walls_locked" : "voice.status.walls",
                Style.PLAIN, AdminCommands.onOff(m, p.walls() || locked)));
        if (!locked) {
            toggleButton(r, walls, "walls", p.walls());
        }
        r.reply.add(walls);

        // The talking line
        if (AudioDistancePlugin.SERVER_WALLS.hasAddon(r.player)) {
            r.line(Style.MUTED, m.get("voice.status.hud_addon"));
        } else if (!settings.isMonitorAllowed()) {
            r.line(Style.MUTED, m.get("voice.status.hud_server_off"));
        } else {
            LineBuilder hud = CommandReply.line().addAll(m.spans("voice.status.hud", Style.PLAIN, AdminCommands.onOff(m, p.hud())));
            toggleButton(r, hud, "hud", p.hud());
            r.reply.add(hud);
        }

        // Volumes, one line each with a button to bring the player back to full volume
        if (p.volumes().isEmpty()) {
            r.reply.add(CommandReply.line().text(m.get("voice.status.volumes_none") + " ", Style.PLAIN)
                    .add(new Span("[" + m.get("voice.btn.set_volume") + "]", Style.BUTTON, Click.SUGGEST, "/voice volume ",
                            m.get("hover.suggest", "/voice volume <player> 50"))));
        } else {
            r.line(Style.PLAIN, m.get("voice.status.volumes"));
            List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(p.volumes().entrySet());
            entries.sort(Map.Entry.comparingByValue());
            for (int i = 0; i < Math.min(LIST_VOLUMES, entries.size()); i++) {
                Map.Entry<UUID, Integer> e = entries.get(i);
                ServerPlayers.Info other = AudioDistancePlugin.PLAYERS.get(e.getKey());
                String name = other != null ? other.name() : e.getKey().toString().substring(0, 8);
                LineBuilder line = CommandReply.line().text("  ")
                        .add(new Span(name + " " + e.getValue() + "%", Style.VALUE, Click.SUGGEST, "/voice volume " + name + " ",
                                m.get("hover.change", "/voice volume " + name)));
                if (other != null) {
                    line.button("×", Click.RUN, "/voice unignore " + name, m.get("hover.run", "/voice unignore " + name));
                }
                r.reply.add(line);
            }
            if (entries.size() > LIST_VOLUMES) {
                r.line(Style.MUTED, m.get("voice.status.more", entries.size() - LIST_VOLUMES));
            }
        }

        LineBuilder buttons = CommandReply.line();
        if (r.ctx.allows(PERM_MENU)) {
            buttons.button(m.get("voice.btn.menu"), Click.RUN, "/voice menu", m.get("hover.run", "/voice menu"));
        }
        buttons.button(m.get("btn.help"), Click.RUN, "/voice help", m.get("hover.run", "/voice help"));
        if (prefs.canUndo(r.player)) {
            buttons.button(m.get("btn.undo"), Click.RUN, "/voice undo", m.get("hover.run", "/voice undo"));
        }
        if (!p.equals(PlayerPrefs.Prefs.DEFAULT)) {
            buttons.danger(m.get("voice.btn.reset"), "/voice reset", m.get("hover.run", "/voice reset"));
        }
        r.reply.add(buttons);
    }

    /** "[turn off]" when it is on, "[turn on]" when it is off. */
    private static void toggleButton(Run r, LineBuilder line, String what, boolean on) {
        String command = "/voice " + what + (on ? " off" : " on");
        line.button(r.m.get(on ? "voice.btn.off" : "voice.btn.on"), Click.RUN, command, r.m.get("hover.run", command));
    }

    private static void mode(Run r) {
        PlayerPrefs.Mode mode = r.args.length > 1 ? modeOf(r.args[1]) : null;
        if (mode == null) {
            r.badValue("mode", r.arg(1), r.ctx.allows(PERM_SHOUT) ? "quiet|normal|shout" : "quiet|normal", "mode");
            return;
        }
        if (mode == PlayerPrefs.Mode.SHOUT && !r.ctx.allows(PERM_SHOUT)) {
            r.error("voice.mode.denied", PERM_SHOUT);
            return;
        }
        AudioDistancePlugin.PLAYER_PREFS.setMode(r.player, mode);
        double factor = mode == PlayerPrefs.Mode.QUIET ? PlayerPrefs.QUIET_FACTOR
                : mode == PlayerPrefs.Mode.SHOUT ? PlayerPrefs.SHOUT_FACTOR : 1.0;
        r.saved("voice.mode.set", r.m.get("voice.mode." + mode.name().toLowerCase(Locale.ROOT)), ConfigWriter.number(factor));
    }

    private static PlayerPrefs.Mode modeOf(String word) {
        for (PlayerPrefs.Mode m : PlayerPrefs.Mode.values()) {
            if (m.name().equalsIgnoreCase(word)) {
                return m;
            }
        }
        return null;
    }

    private static void walls(Run r) {
        if (AudioDistancePlugin.SERVER_SETTINGS.wallsLocked()) {
            r.line(Style.WARN, r.m.get("voice.walls.locked"));
            return;
        }
        toggle(r, "walls", on -> AudioDistancePlugin.PLAYER_PREFS.setWalls(r.player, on));
    }

    private static void hud(Run r) {
        toggle(r, "hud", on -> AudioDistancePlugin.PLAYER_PREFS.setHud(r.player, on));
    }

    private static void toggle(Run r, String what, java.util.function.Consumer<Boolean> set) {
        Boolean on = r.args.length > 1 ? AdminCommands.parseOnOff(r.args[1]) : null;
        if (on == null) {
            r.badValue(what, r.arg(1), "on|off", what);
            return;
        }
        set.accept(on);
        r.saved("voice." + what + (on ? ".on" : ".off"));
    }

    private static void volume(Run r, String who, String value) {
        Messages m = r.m;
        ServerPlayers.Info other = AudioDistancePlugin.PLAYERS.byName(who);
        if (other == null) {
            r.error("voice.unknown_player", who);
            return;
        }
        if (other.id().equals(r.player)) {
            r.error("voice.self");
            return;
        }
        int percent = parsePercent(value);
        if (percent < 0) {
            r.badValue("volume " + other.name(), value, "0-100", "volume");
            return;
        }
        AudioDistancePlugin.PLAYER_PREFS.setVolume(r.player, other.id(), percent);
        if (percent <= 0) {
            r.saved("voice.ignore.set", other.name());
        } else if (percent >= 100) {
            r.saved("voice.ignore.unset", other.name());
        } else {
            r.saved("voice.volume.set", other.name(), percent);
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(r.player)) {
                r.line(Style.MUTED, m.get("voice.volume.addon"));
            }
        }
    }

    private static void undo(Run r) {
        if (!AudioDistancePlugin.PLAYER_PREFS.undo(r.player)) {
            r.line(Style.WARN, r.m.get("voice.undo.none"));
            return;
        }
        // Asking again puts the change back, so the line offers that
        LineBuilder line = CommandReply.line().addAll(r.m.spans("voice.undo.done", Style.OK));
        line.button(r.m.get("voice.btn.redo"), Click.RUN, "/voice undo", r.m.get("hover.run", "/voice undo"));
        line.button(r.m.get("btn.settings"), Click.RUN, "/voice status", r.m.get("hover.run", "/voice status"));
        r.reply.add(line);
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

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private static void help(Run r, String topic) {
        Messages m = r.m;
        if (topic.isEmpty()) {
            r.line(Style.TITLE, m.get("voice.help.title"));
            for (String t : TOPICS) {
                String needed = permissionFor(t);
                if (needed != null && !r.ctx.allows(needed)) {
                    continue;
                }
                String[] parts = CommandHelp.split(m.get("voice.help." + t));
                r.reply.add(CommandReply.line()
                        .add(new Span("/voice " + (t.equals("status") ? "" : t), Style.VALUE, Click.RUN, "/voice help " + t,
                                m.get("hover.help", "/voice " + t)))
                        .text(parts[1].isEmpty() ? "" : " - " + parts[1], Style.MUTED));
            }
            r.line(Style.MUTED, m.get("help.hint"));
            return;
        }
        if (!EXAMPLES.containsKey(topic)) {
            unknown(r, topic);
            return;
        }
        String[] parts = CommandHelp.split(m.get("voice.help." + topic));
        r.line(Style.TITLE, "/voice " + topic);
        r.reply.add(CommandReply.line().text(parts[0], Style.VALUE));
        if (!parts[1].isEmpty()) {
            r.line(Style.PLAIN, parts[1]);
        }
        r.line(Style.MUTED, m.get("help.examples"));
        for (String example : EXAMPLES.get(topic)) {
            r.reply.add(CommandReply.line().text("  ")
                    .add(new Span("/voice " + example, Style.VALUE, Click.SUGGEST, "/voice " + example,
                            m.get("hover.suggest", "/voice " + example))));
        }
        r.reply.add(CommandReply.line().button(m.get("btn.all_commands"), Click.RUN, "/voice help", m.get("hover.run", "/voice help")));
    }

    /** A word that is not a command: the nearest command, if any is close, and the help. */
    private static void unknown(Run r, String word) {
        Messages m = r.m;
        LineBuilder line = CommandReply.line().addAll(m.spans("error.unknown", Style.ERROR, word));
        String near = nearest(word);
        if (near != null) {
            line.text(" ", Style.PLAIN).addAll(m.spans("error.did_you_mean", Style.ERROR,
                    new Span("/voice " + near, Style.VALUE, Click.SUGGEST, "/voice " + near + " ", m.get("hover.suggest", "/voice " + near))));
        }
        line.button(m.get("btn.help"), Click.RUN, "/voice help", m.get("hover.run", "/voice help"));
        r.reply.add(line);
    }

    static String nearest(String word) {
        String best = null;
        int bestDistance = 3;
        for (String s : SUBCOMMANDS) {
            int d = CommandHelp.distance(word, s);
            if (d < bestDistance) {
                best = s;
                bestDistance = d;
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    /** Completions for the last word of {@code input}, only for what the player may use. */
    public static List<String> suggest(String input, Context ctx) {
        if (!ctx.allows(PERM)) {
            return List.of();
        }
        String text = input == null ? "" : input;
        String[] words = text.split("\\s+", -1);
        String last = words[words.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (words.length <= 1) {
            for (String s : SUBCOMMANDS) {
                String needed = permissionFor(s);
                if (needed == null || ctx.allows(needed)) {
                    options.add(s);
                }
            }
        } else {
            String sub = words[0].toLowerCase(Locale.ROOT);
            String needed = permissionFor(sub);
            if (needed == null || ctx.allows(needed)) {
                switch (sub) {
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
                        } else if (words.length == 3 && sub.equals("volume")) {
                            options.addAll(List.of("0", "25", "50", "75", "100"));
                        }
                    }
                    case "help", "?" -> {
                        if (words.length == 2) {
                            options.addAll(List.of(TOPICS));
                        }
                    }
                    default -> {
                    }
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

    // -------------------------------------------------------------------------
    // One run of a command
    // -------------------------------------------------------------------------

    /** The state of one command: who runs it, in which language, the reply so far. */
    private static final class Run {

        final UUID player;
        final Context ctx;
        final String[] args;
        final Messages m;
        final CommandReply reply = new CommandReply();

        Run(UUID player, String language, String input, Context ctx) {
            this.player = player;
            this.ctx = ctx;
            String text = input == null ? "" : input.trim();
            this.args = text.isEmpty() ? new String[0] : text.split("\\s+");
            this.m = new Messages(AudioDistancePlugin.SERVER_SETTINGS.languageFor(language));
        }

        String arg(int i) {
            return i < args.length ? args[i] : "";
        }

        void line(Style style, String text) {
            reply.add(text, style);
        }

        void error(String key, Object... args) {
            reply.add(CommandReply.line().addAll(m.spans(key, Style.ERROR, args)));
        }

        /** A change made: says so, with a button that takes it back. */
        void saved(String key, Object... args) {
            LineBuilder line = CommandReply.line().addAll(m.spans(key, Style.OK, args));
            if (AudioDistancePlugin.PLAYER_PREFS.canUndo(player)) {
                line.button(m.get("btn.undo"), Click.RUN, "/voice undo", m.get("hover.run", "/voice undo"));
            }
            reply.add(line);
        }

        /** "/voice mode: "loud" does not work. Allowed: quiet|normal|shout" with buttons to retype and for help. */
        void badValue(String command, String value, String allowed, String topic) {
            LineBuilder line = CommandReply.line();
            if (value == null || value.isBlank()) {
                line.addAll(m.spans("error.missing", Style.ERROR, "/voice " + command, new Span(allowed, Style.VALUE)));
            } else {
                line.addAll(m.spans("error.value", Style.ERROR, "/voice " + command, value, new Span(allowed, Style.VALUE)));
            }
            line.button(m.get("btn.fix"), Click.SUGGEST, "/voice " + command + " ", m.get("hover.suggest", "/voice " + command));
            line.button("?", Click.RUN, "/voice help " + topic, m.get("hover.help", "/voice " + topic));
            reply.add(line);
        }
    }
}
