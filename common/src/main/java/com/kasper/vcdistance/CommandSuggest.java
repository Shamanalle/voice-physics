package com.kasper.vcdistance;

import com.kasper.vcdistance.AdminCommands.Context;
import com.kasper.vcdistance.AdminCommands.Messages;
import com.kasper.vcdistance.AdminCommands.Suggestion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tab completion for {@code /vcd}: the words that fit where the cursor is, with a short explanation
 * for commands and zones. With settings and a context it also offers zone, world and player names and
 * leaves out what the sender may not use; without them only the fixed words.
 */
final class CommandSuggest {

    private CommandSuggest() {
    }

    static List<Suggestion> suggest(String input, ServerSettings settings, Context ctx) {
        String text = input == null ? "" : input;
        String[] args = text.stripLeading().split("\\s+", -1);
        String typed = args[args.length - 1];
        Messages m = settings == null ? null : new Messages(language(settings, ctx));
        List<Suggestion> out = new ArrayList<>();
        if (args.length <= 1) {
            for (String sub : AdminCommands.SUBCOMMANDS) {
                String permission = AdminCommands.permissionFor(sub, "info");
                if (ctx == null || permission == null || ctx.allows(permission)) {
                    add(out, sub, typed, m == null ? null : CommandHelp.split(m.get("help." + sub))[1]);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String action = args.length > 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        String permission = AdminCommands.permissionFor(sub, args.length > 2 ? action : "info");
        if (ctx != null && permission != null && !ctx.allows(permission)) {
            return out;
        }
        if (args.length == 2) {
            switch (sub) {
                case "help", "?" -> addAll(out, AdminCommands.TOPICS, typed);
                case "profile" -> addAll(out, AdminCommands.MODES, typed);
                case "preset" -> addAll(out, AdminCommands.PRESETS, typed);
                case "walls" -> addAll(out, AdminCommands.WALLS_STEPS, typed);
                case "serverwalls", "monitor", "notices" -> addAll(out, AdminCommands.ON_OFF, typed);
                case "lock" -> lockParts(out, typed);
                case "rule" -> addAll(out, AdminCommands.RULES, typed);
                case "group" -> addAll(out, AdminCommands.GROUP_RULES, typed);
                case "require" -> addAll(out, AdminCommands.REQUIRE, typed);
                case "debug" -> players(out, ctx, typed);
                case "zones" -> pages(out, settings, typed);
                case "zone" -> {
                    for (String a : ZoneCommands.ACTIONS) {
                        if (ctx == null || ZoneCommands.readOnly(a) || ctx.allows(AdminCommands.PERM_ZONE)) {
                            add(out, a, typed, null);
                        }
                    }
                }
                default -> {
                }
            }
            return out;
        }
        if (sub.equals("lock")) {
            lockParts(out, typed);
        } else if (sub.equals("rule") && args.length == 3) {
            addAll(out, switch (action) {
                case "sneak" -> new String[]{"1", "0.7", "0.5", "0.3"};
                case "dead", "spectators" -> AdminCommands.ON_OFF;
                case "megaphone" -> new String[]{"off", "minecraft:goat_horn", "minecraft:bell"};
                case "megaphone_range" -> new String[]{"2", "2.5", "3", "4"};
                default -> new String[0];
            }, typed);
        } else if (sub.equals("group") && args.length == 3) {
            addAll(out, AdminCommands.ON_OFF, typed);
        } else if (sub.equals("require") && args.length == 3) {
            addAll(out, new String[]{"-", BuildInfo.version()}, typed);
        } else if (sub.equals("zone")) {
            zone(out, args, action, typed, settings, ctx, m);
        }
        return out;
    }

    private static void zone(List<Suggestion> out, String[] args, String action, String typed, ServerSettings settings, Context ctx,
                             Messages m) {
        switch (action) {
            case "pos1", "pos2" -> {
                if (args.length == 3) {
                    add(out, "look", typed, null);
                    add(out, "~ ~ ~", typed, null);
                }
            }
            case "create" -> {
                if (args.length == 4) {
                    addAll(out, new String[]{"5", "10", "20", "50"}, typed);
                }
            }
            case "info", "show", "tp", "teleport", "delete", "remove", "rename" -> {
                if (args.length == 3) {
                    zoneNames(out, settings, m, typed, !action.equals("info") && !action.equals("delete") && !action.equals("remove"));
                    if (action.equals("show")) {
                        add(out, "off", typed, null);
                    }
                } else if (args.length == 4 && (action.equals("delete") || action.equals("remove"))) {
                    add(out, ZoneCommands.CONFIRM, typed, null);
                }
            }
            case "set" -> {
                if (args.length == 3) {
                    zoneNames(out, settings, m, typed, false);
                    if (ctx != null) {
                        for (String world : ctx.worlds()) {
                            if (settings == null || settings.findZone(world) == null) {
                                add(out, world, typed, null);
                            }
                        }
                    }
                    if (ctx != null && ctx.claims()) {
                        claims(out, ctx, settings, typed);
                    }
                } else if (args.length == 4) {
                    addAll(out, ZoneCommands.SETTINGS, typed);
                } else if (args.length == 5) {
                    String setting = args[3].toLowerCase(Locale.ROOT);
                    addAll(out, ZoneCommands.options(setting), typed);
                    add(out, "default", typed, null);
                }
            }
            default -> {
            }
        }
    }

    private static void zoneNames(List<Suggestion> out, ServerSettings settings, Messages m, String typed, boolean boxesOnly) {
        if (settings == null) {
            return;
        }
        for (Zone z : ZoneCommands.sorted(settings)) {
            if (!boxesOnly || z.box() != null) {
                add(out, ZoneCommands.ref(z), typed, m == null ? null : ZoneCommands.describe(z, m));
            }
        }
    }

    /** "claim:" and, once it is typed, "claim:<player>" for each player online and "claim:server". */
    private static void claims(List<Suggestion> out, Context ctx, ServerSettings settings, String typed) {
        String prefix = Zone.CLAIM + ":";
        if (!typed.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            add(out, prefix, typed, null);
            return;
        }
        Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.add("server");
        for (ServerPlayers.Info p : ctx.players().all()) {
            if (p.name() != null) {
                names.add(p.name().toLowerCase(Locale.ROOT));
            }
        }
        for (String name : names) {
            if (settings == null || settings.findZone(prefix + name) == null) {
                add(out, prefix + name, typed, null);
            }
        }
    }

    private static void players(List<Suggestion> out, Context ctx, String typed) {
        if (ctx == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (ServerPlayers.Info p : ctx.players().all()) {
            if (p.name() != null) {
                names.add(p.name());
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        addAll(out, sorted.toArray(new String[0]), typed);
    }

    private static void pages(List<Suggestion> out, ServerSettings settings, String typed) {
        if (settings == null) {
            return;
        }
        int pages = (settings.zones().size() + ZoneCommands.PAGE - 1) / ZoneCommands.PAGE;
        for (int p = 2; p <= pages; p++) {
            add(out, String.valueOf(p), typed, null);
        }
    }

    /** "curve,wa" offers "curve,walls": the parts not yet in the list, after the last comma. */
    private static void lockParts(List<Suggestion> out, String typed) {
        int comma = typed.lastIndexOf(',');
        String before = comma < 0 ? "" : typed.substring(0, comma + 1);
        String word = typed.substring(comma + 1);
        for (String part : AdminCommands.LOCK_PARTS) {
            boolean listed = before.contains(part + ",");
            boolean alone = part.equals("all") || part.equals("none");
            if (!listed && (before.isEmpty() || !alone)) {
                add(out, before + part, before + word, null);
            }
        }
    }

    private static String language(ServerSettings settings, Context ctx) {
        ServerPlayers.Info me = ctx == null || ctx.sender() == null ? null : ctx.players().get(ctx.sender());
        return settings.languageFor(me == null ? "" : me.language());
    }

    private static void addAll(List<Suggestion> out, String[] options, String typed) {
        for (String o : options) {
            add(out, o, typed, null);
        }
    }

    private static void add(List<Suggestion> out, String option, String typed, String tooltip) {
        if (option.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
            out.add(new Suggestion(option, tooltip == null || tooltip.isEmpty() ? null : tooltip));
        }
    }
}
