package com.kasper.vcdistance;

import com.kasper.vcdistance.AdminCommands.Messages;
import com.kasper.vcdistance.AdminCommands.Run;
import com.kasper.vcdistance.CommandReply.Click;
import com.kasper.vcdistance.CommandReply.LineBuilder;
import com.kasper.vcdistance.CommandReply.Span;
import com.kasper.vcdistance.CommandReply.Style;

import java.util.Locale;
import java.util.Map;

/** {@code /vcd help}: every command in one line each, or one command with examples to click. */
final class CommandHelp {

    /** Examples per topic; clicking one puts it in the chat box. They are commands, so the same in every language. */
    static final Map<String, String[]> EXAMPLES = Map.ofEntries(
            Map.entry("status", new String[]{"status"}),
            Map.entry("reload", new String[]{"reload"}),
            Map.entry("undo", new String[]{"undo"}),
            Map.entry("profile", new String[]{"profile suggest", "profile enforce", "profile off"}),
            Map.entry("preset", new String[]{"preset realistic", "preset stealth", "preset export", "preset import VP1:"}),
            Map.entry("walls", new String[]{"walls 60", "walls 85", "walls off"}),
            Map.entry("serverwalls", new String[]{"serverwalls on", "serverwalls off"}),
            Map.entry("lock", new String[]{"lock all", "lock curve,walls", "lock none"}),
            Map.entry("monitor", new String[]{"monitor on", "monitor off"}),
            Map.entry("zones", new String[]{"zones", "zones 2"}),
            Map.entry("zone", new String[]{"zone pos1", "zone pos2 look", "zone create stage", "zone create lobby 10",
                    "zone info stage", "zone set stage range_multiplier 2", "zone set stage message Welcome!",
                    "zone set world_nether voice_range 16", "zone show stage", "zone tp stage", "zone rename stage arena",
                    "zone delete stage"}),
            Map.entry("rule", new String[]{"rule sneak 0.5", "rule dead on", "rule spectators on", "rule megaphone minecraft:goat_horn",
                    "rule megaphone_range 3"}),
            Map.entry("group", new String[]{"group dead on", "group spectators on", "group zones on", "group open_range off"}),
            Map.entry("require", new String[]{"require suggest", "require warn", "require kick " + BuildInfo.version()}),
            Map.entry("debug", new String[]{"debug", "debug Steve"}));

    private CommandHelp() {
    }

    static void help(Run r, String topic) {
        String t = topic.toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            list(r);
        } else if (EXAMPLES.containsKey(t)) {
            topic(r, t);
        } else {
            unknown(r, t);
        }
    }

    /** Each command the sender may use, clickable for its details. */
    private static void list(Run r) {
        Messages m = r.m;
        r.line(Style.TITLE, m.get("help.title"));
        for (String topic : AdminCommands.TOPICS) {
            // zone also has what only looks (info), so it is listed for everyone who may see the status
            String permission = AdminCommands.permissionFor(topic, "info");
            if (permission != null && !r.ctx.allows(permission)) {
                continue;
            }
            String[] parts = split(m.get("help." + topic));
            r.reply.add(CommandReply.line()
                    .add(new Span("/vcd " + topic, Style.VALUE, Click.RUN, "/vcd help " + topic, m.get("hover.help", "/vcd " + topic)))
                    .text(parts[1].isEmpty() ? "" : " - " + parts[1], Style.MUTED));
        }
        r.line(Style.MUTED, m.get("help.hint"));
    }

    /** One command: its syntax, what it does and examples. */
    private static void topic(Run r, String topic) {
        Messages m = r.m;
        String[] parts = split(m.get("help." + topic));
        r.line(Style.TITLE, "/vcd " + topic);
        r.reply.add(CommandReply.line().text(parts[0], Style.VALUE));
        if (!parts[1].isEmpty()) {
            r.line(Style.PLAIN, parts[1]);
        }
        r.line(Style.MUTED, m.get("help.examples"));
        for (String example : EXAMPLES.get(topic)) {
            r.reply.add(CommandReply.line().text("  ")
                    .add(new Span("/vcd " + example, Style.VALUE, Click.SUGGEST, "/vcd " + example, m.get("hover.suggest", "/vcd " + example))));
        }
        LineBuilder back = CommandReply.line();
        back.button(m.get("btn.all_commands"), Click.RUN, "/vcd help", m.get("hover.run", "/vcd help"));
        r.reply.add(back);
    }

    /** A word that is not a command: the nearest command, if any is close, and the help. */
    static void unknown(Run r, String word) {
        Messages m = r.m;
        LineBuilder line = CommandReply.line().addAll(m.spans("error.unknown", Style.ERROR, word));
        String near = nearest(word);
        if (near != null) {
            line.text(" ", Style.PLAIN).addAll(m.spans("error.did_you_mean", Style.ERROR,
                    new Span("/vcd " + near, Style.VALUE, Click.SUGGEST, "/vcd " + near + " ", m.get("hover.suggest", "/vcd " + near))));
        }
        line.button(m.get("btn.help"), Click.RUN, "/vcd help", m.get("hover.run", "/vcd help"));
        r.reply.add(line);
    }

    /** The command closest to {@code word}, when two letters or fewer are off; otherwise {@code null}. */
    static String nearest(String word) {
        String best = null;
        int bestDistance = 3;
        for (String s : AdminCommands.SUBCOMMANDS) {
            int d = distance(word, s);
            if (d < bestDistance) {
                best = s;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Edits (insert, delete, change, swap two neighbours) that turn {@code a} into {@code b}. */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    /** "/vcd walls 0-100|off - wall strength..." into the syntax and what it does. */
    static String[] split(String help) {
        int dash = help.indexOf(" - ");
        return dash < 0 ? new String[]{help, ""} : new String[]{help.substring(0, dash), help.substring(dash + 3)};
    }
}
