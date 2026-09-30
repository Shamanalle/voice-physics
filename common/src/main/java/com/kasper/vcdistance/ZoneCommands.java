package com.kasper.vcdistance;

import com.kasper.vcdistance.AdminCommands.Messages;
import com.kasper.vcdistance.AdminCommands.Run;
import com.kasper.vcdistance.CommandReply.Click;
import com.kasper.vcdistance.CommandReply.LineBuilder;
import com.kasper.vcdistance.CommandReply.Span;
import com.kasper.vcdistance.CommandReply.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /vcd zones} and {@code /vcd zone}:
 * <pre>
 * /vcd zones [page]                            every zone, with buttons
 * /vcd zone pos1|pos2 [x y z | ~ ~ ~ | look]   a corner of the box: where you stand, at coordinates, or the block you look at
 * /vcd zone create &lt;name&gt; [radius]            a box from the two corners, or around you
 * /vcd zone info [name]                        a zone's settings (without a name: the zone you are in)
 * /vcd zone set &lt;name&gt; [setting] [value|default]  change a setting (without a value: see it and the choices)
 * /vcd zone show &lt;name&gt;|off                   the box's borders as particles
 * /vcd zone tp &lt;name&gt;                         go to the middle of a box
 * /vcd zone rename &lt;name&gt; &lt;new name&gt;
 * /vcd zone delete &lt;name&gt; [confirm]           asks first
 * </pre>
 */
final class ZoneCommands {

    static final String[] ACTIONS = {"pos1", "pos2", "create", "info", "set", "show", "tp", "rename", "delete", "list"};
    static final String[] SETTINGS = {"mode", "preset", "voice_range", "whisper_range", "range_multiplier", "walls",
            "echo", "isolated", "message", "priority"};
    /** Largest box {@code zone create <name> <radius>} makes around the admin. */
    static final int MAX_RADIUS = 256;
    /** How far {@code pos1 look} reaches. */
    static final int LOOK_REACH = 64;
    /** Zones per page of {@code /vcd zones}. */
    static final int PAGE = 8;
    static final String CONFIRM = "confirm";

    /** Corners picked with {@code zone pos1/pos2}, per admin, until the server stops. */
    private record Corner(String world, int x, int y, int z) {
    }

    private static final Map<UUID, Corner> POS1 = new ConcurrentHashMap<>();
    private static final Map<UUID, Corner> POS2 = new ConcurrentHashMap<>();

    private ZoneCommands() {
    }

    /** Actions that only look ({@link AdminCommands#PERM_STATUS} is enough). */
    static boolean readOnly(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "", "info", "here", "list" -> true;
            default -> false;
        };
    }

    static void zone(Run r) {
        String action = r.arg(1).toLowerCase(Locale.ROOT);
        switch (action) {
            case "pos1", "pos2" -> pos(r, action);
            case "create" -> create(r);
            case "set" -> set(r);
            case "delete", "remove" -> delete(r);
            case "info", "here" -> info(r, r.arg(2));
            case "show" -> show(r);
            case "tp", "teleport" -> tp(r);
            case "rename" -> rename(r);
            case "list", "" -> list(r, r.args.length > 2 ? AdminCommands.parseInt(r.arg(2)) : null);
            default -> r.badValue("zone", action, String.join("|", ACTIONS), "zone");
        }
    }

    // -------------------------------------------------------------------------
    // List and info
    // -------------------------------------------------------------------------

    static void list(Run r, Integer page) {
        Messages m = r.m;
        List<Zone> zones = sorted(r.settings);
        if (zones.isEmpty()) {
            r.line(Style.MUTED, m.get("zones.none"));
            LineBuilder start = CommandReply.line();
            r.button(start, "btn.pos1", Click.RUN, "/vcd zone pos1", AdminCommands.PERM_ZONE);
            r.button(start, "btn.help", Click.RUN, "/vcd help zone", null);
            r.reply.add(start);
            return;
        }
        int pages = (zones.size() + PAGE - 1) / PAGE;
        int p = Math.max(1, Math.min(pages, page == null ? 1 : page));
        r.line(Style.TITLE, m.get("zones.title", zones.size()));
        boolean mayChange = r.ctx.allows(AdminCommands.PERM_ZONE);
        for (Zone z : zones.subList((p - 1) * PAGE, Math.min(zones.size(), p * PAGE))) {
            LineBuilder line = CommandReply.line()
                    .text("• ", Style.MUTED)
                    .add(new Span(z.name(), Style.VALUE, Click.RUN, "/vcd zone info " + ref(z), describe(z, m)))
                    .text(" " + m.get("zones." + z.kind()) + (z.box() != null ? ", " + z.box().world() : "") + " - " + summary(z, m), Style.MUTED);
            line.button(m.get("btn.info"), Click.RUN, "/vcd zone info " + ref(z), m.get("hover.run", "/vcd zone info " + ref(z)));
            if (z.box() != null && mayChange) {
                line.button(m.get("btn.show"), Click.RUN, "/vcd zone show " + ref(z), m.get("hover.run", "/vcd zone show " + ref(z)));
                line.button(m.get("btn.tp"), Click.RUN, "/vcd zone tp " + ref(z), m.get("hover.run", "/vcd zone tp " + ref(z)));
            }
            r.reply.add(line);
        }
        LineBuilder footer = CommandReply.line();
        if (pages > 1) {
            footer.text(m.get("zones.page", p, pages), Style.MUTED);
            if (p > 1) {
                footer.button("‹", Click.RUN, "/vcd zones " + (p - 1), m.get("hover.run", "/vcd zones " + (p - 1)));
            }
            if (p < pages) {
                footer.button("›", Click.RUN, "/vcd zones " + (p + 1), m.get("hover.run", "/vcd zones " + (p + 1)));
            }
        }
        r.button(footer, "btn.new_zone", Click.RUN, "/vcd zone pos1", AdminCommands.PERM_ZONE);
        if (!footer.isEmpty()) {
            r.reply.add(footer);
        }
    }

    /** How commands name a zone: its name, or "claim:steve" for claims (a player may share a world's name). */
    static String ref(Zone z) {
        return Zone.prefixedKind(z.kind() + ":") != null ? z.kind() + ":" + z.name() : z.name();
    }

    /** Boxes, then regions, claims and worlds, each by name. */
    static List<Zone> sorted(ServerSettings settings) {
        List<Zone> zones = new ArrayList<>(settings.zones().values());
        List<String> order = List.of(Zone.BOX, Zone.REGION, Zone.CLAIM, Zone.TOWN, Zone.LAND, Zone.WORLD);
        zones.sort(Comparator.comparingInt((Zone z) -> order.indexOf(z.kind())).thenComparing(Zone::name));
        return zones;
    }

    private static void info(Run r, String name) {
        Messages m = r.m;
        Zone z;
        if (name.isEmpty()) {
            if (r.me == null) {
                r.badValue("zone info", "", "<name>", "zone");
                return;
            }
            z = r.settings.zoneOf(r.me);
            if (z == null) {
                r.line(Style.PLAIN, m.get("zone.nowhere", r.me.world()));
                return;
            }
            r.line(Style.MUTED, m.get("zone.here", z.name()));
        } else {
            z = r.settings.findZone(name);
            if (z == null) {
                unknown(r, name);
                return;
            }
        }
        card(r, z);
    }

    /** Everything about a zone, each setting clickable to change it. */
    static void card(Run r, Zone z) {
        Messages m = r.m;
        r.line(Style.TITLE, m.get("zone.card", m.get("zones." + z.kind()), z.name()));
        if (z.box() != null) {
            Zone.Box b = z.box();
            r.reply.add(CommandReply.line().addAll(m.spans("zone.card_box", Style.PLAIN, b.world(), b.from(), b.to(),
                    size(b), b.volume())));
        }
        boolean mayChange = r.ctx.allows(AdminCommands.PERM_ZONE);
        LineBuilder line = CommandReply.line();
        int inLine = 0;
        for (String setting : SETTINGS) {
            if (inLine == 3) {
                r.reply.add(line);
                line = CommandReply.line();
                inLine = 0;
            }
            if (inLine > 0) {
                line.text(" · ", Style.MUTED);
            }
            line.text(setting + " ", Style.LABEL);
            String value = value(z, setting);
            // Settings the zone leaves to the server are a dash, so the few it changes stand out
            String shown = value == null ? "–" : value;
            Style style = value == null ? Style.MUTED : Style.VALUE;
            String hover = (value == null ? m.get("default") + "\n" : "")
                    + (mayChange ? m.get("hover.change", "/vcd zone set " + ref(z) + " " + setting) : "");
            line.add(mayChange
                    ? new Span(shown, style, Click.SUGGEST, "/vcd zone set " + ref(z) + " " + setting + " ", hover.strip())
                    : new Span(shown, style, null, null, hover.isBlank() ? null : hover.strip()));
            inLine++;
        }
        r.reply.add(line);
        if (!mayChange) {
            return;
        }
        LineBuilder buttons = CommandReply.line();
        if (z.box() != null) {
            buttons.button(m.get("btn.show"), Click.RUN, "/vcd zone show " + ref(z), m.get("hover.run", "/vcd zone show " + ref(z)));
            buttons.button(m.get("btn.tp"), Click.RUN, "/vcd zone tp " + ref(z), m.get("hover.run", "/vcd zone tp " + ref(z)));
            buttons.button(m.get("btn.rename"), Click.SUGGEST, "/vcd zone rename " + ref(z) + " ",
                    m.get("hover.suggest", "/vcd zone rename " + ref(z)));
        }
        buttons.danger(m.get("btn.delete"), "/vcd zone delete " + ref(z), m.get("hover.run", "/vcd zone delete " + ref(z)));
        r.reply.add(buttons);
    }

    /** A zone setting as the command writes it, or {@code null} when the zone leaves it to the server. */
    static String value(Zone z, String setting) {
        Zone.Rules r = z.rules();
        return switch (setting) {
            case "mode" -> z.mode() == null ? null : z.mode().getId();
            case "preset" -> z.preset();
            case "voice_range" -> r.voiceRange() == null ? null : AdminCommands.fmt(r.voiceRange());
            case "whisper_range" -> r.whisperRange() == null ? null : AdminCommands.fmt(r.whisperRange());
            case "range_multiplier" -> r.rangeMultiplier() == null ? null : "×" + AdminCommands.fmt(r.rangeMultiplier());
            case "walls" -> r.wallsStrength() == null ? null : AdminCommands.pct(r.wallsStrength());
            case "echo" -> r.echo() == null ? null : r.echo() <= 0.0 ? "off" : AdminCommands.pct(r.echo());
            case "isolated" -> r.isolated() ? "on" : null;
            case "message" -> r.enterMessage() == null ? null : "\"" + r.enterMessage() + "\"";
            case "priority" -> z.priority() == 0 ? null : String.valueOf(z.priority());
            default -> null;
        };
    }

    /** What the command accepts for a setting. */
    static String allowed(String setting) {
        return switch (setting) {
            case "mode" -> "off|suggest|enforce";
            case "preset" -> "vanilla|realistic|clear|stealth";
            case "voice_range", "whisper_range" -> "1-1000";
            case "range_multiplier" -> "0.05-10";
            case "walls" -> "0-100|off";
            case "echo" -> "auto|off|10-100";
            case "isolated" -> "on|off";
            case "message" -> "<text>";
            case "priority" -> "-100-100";
            default -> "";
        };
    }

    /** Values offered as buttons and suggestions for a setting. */
    static String[] options(String setting) {
        return switch (setting) {
            case "mode" -> AdminCommands.MODES;
            case "preset" -> new String[]{"vanilla", "realistic", "clear", "stealth"};
            case "voice_range" -> new String[]{"8", "16", "24", "48", "96"};
            case "whisper_range" -> new String[]{"2", "4", "8", "16"};
            case "range_multiplier" -> new String[]{"0.4", "0.7", "1.5", "2", "3"};
            case "walls" -> new String[]{"off", "25", "50", "75", "100"};
            case "echo" -> new String[]{"auto", "off", "30", "60", "90"};
            case "isolated" -> AdminCommands.ON_OFF;
            case "priority" -> new String[]{"0", "1", "5", "10"};
            default -> new String[0];
        };
    }

    /** The zone in one line, for hovering. */
    static String describe(Zone z, Messages m) {
        StringBuilder b = new StringBuilder(m.get("zones." + z.kind())).append(' ').append(z.name());
        if (z.box() != null) {
            b.append(": ").append(z.box().world()).append(" [").append(z.box().from()).append(" – ").append(z.box().to()).append(']');
        }
        b.append('\n').append(summary(z, m));
        return b.toString();
    }

    /** The settings a zone changes, or "-". */
    static String summary(Zone z, Messages m) {
        List<String> parts = new ArrayList<>();
        for (String setting : SETTINGS) {
            String v = value(z, setting);
            if (v != null) {
                parts.add(setting.equals("isolated") ? setting : setting + " " + v);
            }
        }
        return parts.isEmpty() ? m.get("zones.nothing") : String.join(", ", parts);
    }

    private static String size(Zone.Box b) {
        return (b.x2() - b.x1() + 1) + "×" + (b.y2() - b.y1() + 1) + "×" + (b.z2() - b.z1() + 1);
    }

    private static void unknown(Run r, String name) {
        LineBuilder line = CommandReply.line().addAll(r.m.spans("zone.unknown", Style.ERROR, name));
        line.button(r.m.get("btn.zones"), Click.RUN, "/vcd zones", r.m.get("hover.run", "/vcd zones"));
        r.reply.add(line);
    }

    private static boolean needPlayer(Run r) {
        if (r.me == null) {
            r.error("zone.need_player");
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Making zones
    // -------------------------------------------------------------------------

    private static void pos(Run r, String which) {
        if (needPlayer(r)) {
            return;
        }
        int[] at = corner(r, 2);
        if (at == null) {
            return;
        }
        Corner c = new Corner(r.me.world(), at[0], at[1], at[2]);
        (which.equals("pos1") ? POS1 : POS2).put(r.me.id(), c);
        Messages m = r.m;
        r.reply.add(CommandReply.line().addAll(m.spans("zone.pos", Style.OK, which, c.x() + "," + c.y() + "," + c.z())));
        Corner a = POS1.get(r.me.id());
        Corner b = POS2.get(r.me.id());
        if (a == null || b == null) {
            String other = which.equals("pos1") ? "pos2" : "pos1";
            LineBuilder next = CommandReply.line().text(m.get("zone.pos_next", other), Style.MUTED);
            next.button(m.get("btn.here"), Click.RUN, "/vcd zone " + other, m.get("hover.run", "/vcd zone " + other));
            next.button(m.get("btn.look"), Click.RUN, "/vcd zone " + other + " look", m.get("hover.run", "/vcd zone " + other + " look"));
            r.reply.add(next);
            return;
        }
        if (!Zone.sameWorld(a.world(), b.world())) {
            r.line(Style.WARN, m.get("zone.pos_worlds"));
            return;
        }
        Zone.Box box = new Zone.Box(a.world(), a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
        ZoneOutlines.show(r.me.id(), box);
        LineBuilder line = CommandReply.line().addAll(m.spans("zone.selection", Style.PLAIN, size(box), box.volume()));
        line.button(m.get("btn.create"), Click.SUGGEST, "/vcd zone create ", m.get("hover.suggest", "/vcd zone create <name>"));
        r.reply.add(line);
    }

    /**
     * The corner given from argument {@code from} on: nothing (where the admin stands), "look" (the
     * block they look at) or x y z, each a number or ~ / ~n relative to the admin. Says what is wrong
     * and returns {@code null} when it does not work.
     */
    private static int[] corner(Run r, int from) {
        int[] feet = {(int) Math.floor(r.me.x()), (int) Math.floor(r.me.y()), (int) Math.floor(r.me.z())};
        int n = r.args.length - from;
        if (n <= 0) {
            return feet;
        }
        if (n == 1 && r.arg(from).equalsIgnoreCase("look")) {
            int[] block = r.ctx.targetBlock();
            if (block == null) {
                r.error("zone.look_none", LOOK_REACH);
            }
            return block;
        }
        if (n == 3) {
            int[] out = new int[3];
            for (int i = 0; i < 3; i++) {
                String s = r.arg(from + i);
                Double d;
                if (s.startsWith("~")) {
                    Double offset = s.length() == 1 ? Double.valueOf(0.0) : AdminCommands.parseNumber(s.substring(1));
                    d = offset == null ? null : feet[i] + offset;
                } else {
                    d = AdminCommands.parseNumber(s);
                }
                if (d == null || Math.abs(d) > 30_000_000) {
                    r.badValue("zone " + r.arg(1), r.rest(from), "x y z|~ ~ ~|look", "zone");
                    return null;
                }
                out[i] = (int) Math.floor(d);
            }
            return out;
        }
        r.badValue("zone " + r.arg(1), r.rest(from), "x y z|~ ~ ~|look", "zone");
        return null;
    }

    private static void create(Run r) {
        Messages m = r.m;
        String name = AdminCommands.clean(r.arg(2));
        if (name.isEmpty()) {
            r.badValue("zone create", r.arg(2), "<name> [radius]", "zone");
            return;
        }
        Zone.Box box;
        if (r.args.length > 3) {
            if (needPlayer(r)) {
                return;
            }
            Integer radius = AdminCommands.parseInt(r.arg(3));
            if (radius == null || radius < 1 || radius > MAX_RADIUS) {
                r.badValue("zone create " + name, r.arg(3), "1-" + MAX_RADIUS, "zone");
                return;
            }
            int x = (int) Math.floor(r.me.x());
            int y = (int) Math.floor(r.me.y());
            int z = (int) Math.floor(r.me.z());
            box = new Zone.Box(r.me.world(), x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        } else {
            UUID id = r.me == null ? null : r.me.id();
            Corner a = id == null ? null : POS1.get(id);
            Corner b = id == null ? null : POS2.get(id);
            if (a == null || b == null) {
                LineBuilder line = CommandReply.line().addAll(m.spans("zone.need_corners", Style.ERROR));
                if (id != null) {
                    line.button(m.get("btn.pos1"), Click.RUN, "/vcd zone pos1", m.get("hover.run", "/vcd zone pos1"));
                }
                r.reply.add(line);
                return;
            }
            if (!Zone.sameWorld(a.world(), b.world())) {
                r.error("zone.pos_worlds");
                return;
            }
            box = new Zone.Box(a.world(), a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
        }
        Zone old = r.settings.zones().get(Zone.BOX + ":" + name);
        Zone zone = old == null
                ? new Zone(Zone.BOX, name, null, null, Zone.Rules.NONE, box, 0)
                : new Zone(Zone.BOX, name, old.mode(), old.preset(), old.rules(), box, old.priority());
        r.settings.putZone(zone);
        LineBuilder line = CommandReply.line().addAll(m.spans(old == null ? "zone.created" : "zone.updated", Style.OK,
                name, box.world(), box.from(), box.to()));
        r.savedLine(line);
        LineBuilder next = CommandReply.line().text(m.get("zone.next"), Style.MUTED);
        next.button(m.get("btn.settings"), Click.RUN, "/vcd zone info " + name, m.get("hover.run", "/vcd zone info " + name));
        next.button(m.get("btn.show"), Click.RUN, "/vcd zone show " + name, m.get("hover.run", "/vcd zone show " + name));
        r.reply.add(next);
    }

    private static void set(Run r) {
        Messages m = r.m;
        String syntax = "<name> <" + String.join("|", SETTINGS) + "> <value|default>";
        if (r.args.length < 3) {
            r.badValue("zone set", "", syntax, "zone");
            return;
        }
        Zone z = r.settings.findZone(r.arg(2));
        // "claim:steve" names the claims of a player (Open Parties and Claims)
        String prefixed = Zone.prefixedKind(r.arg(2));
        String name = AdminCommands.clean(prefixed != null ? r.arg(2).substring(prefixed.length() + 1) : r.arg(2));
        if (name.isEmpty()) {
            r.badValue("zone set", r.arg(2), syntax, "zone");
            return;
        }
        if (r.args.length == 3) {
            // No setting: the whole zone, each setting clickable
            if (z == null) {
                unknown(r, r.arg(2));
            } else {
                card(r, z);
            }
            return;
        }
        String key = canonical(r.arg(3).toLowerCase(Locale.ROOT));
        if (key == null) {
            r.badValue("zone set " + name, r.arg(3), String.join("|", SETTINGS), "zone");
            return;
        }
        if (r.args.length == 4) {
            choices(r, z, prefixed != null ? prefixed + ":" + name : name, key);
            return;
        }
        if (z == null) {
            // A world, or a player's claims, get a zone just by setting something on them
            z = new Zone(prefixed != null ? prefixed : Zone.WORLD, name, null, null);
        }
        String value = r.rest(4).trim();
        boolean reset = value.equalsIgnoreCase("default") || value.equals("-");
        Zone.Rules rules = z.rules();
        ServerSettings.ProfileMode mode = z.mode();
        String preset = z.preset();
        int priority = z.priority();
        Double num = reset ? null : AdminCommands.parseNumber(value);
        boolean bad = false;
        switch (key) {
            case "mode" -> {
                mode = reset ? null : ServerSettings.ProfileMode.fromId(value, null);
                bad = !reset && mode == null;
            }
            case "preset" -> {
                Preset p = reset ? null : ServerSettings.presetByName(value);
                preset = p == null ? null : ServerSettings.nameOf(p);
                bad = !reset && p == null;
            }
            case "voice_range", "whisper_range" -> {
                bad = !reset && (num == null || num < 1 || num > 1000);
                if (!bad) {
                    rules = key.equals("voice_range") ? withVoice(rules, num) : withWhisper(rules, num);
                }
            }
            case "range_multiplier" -> {
                bad = !reset && (num == null || num < 0.05 || num > 10);
                if (!bad) {
                    rules = new Zone.Rules(rules.voiceRange(), rules.whisperRange(), num, rules.wallsStrength(), rules.echo(),
                            rules.isolated(), rules.enterMessage());
                }
            }
            case "walls" -> {
                Double w = reset ? null : AdminCommands.parsePercent(value);
                bad = !reset && w == null;
                if (!bad) {
                    rules = new Zone.Rules(rules.voiceRange(), rules.whisperRange(), rules.rangeMultiplier(), w, rules.echo(),
                            rules.isolated(), rules.enterMessage());
                }
            }
            case "echo" -> {
                Double e = null;
                if (!reset && !value.equalsIgnoreCase("auto")) {
                    Double percent = value.endsWith("%") || (num != null && num > 1.0) ? AdminCommands.parsePercent(value) : null;
                    e = ServerSettings.parseEcho(percent != null ? String.valueOf(percent) : value);
                    bad = e == null;
                }
                if (!bad) {
                    rules = new Zone.Rules(rules.voiceRange(), rules.whisperRange(), rules.rangeMultiplier(), rules.wallsStrength(), e,
                            rules.isolated(), rules.enterMessage());
                }
            }
            case "isolated" -> {
                Boolean on = reset ? Boolean.FALSE : AdminCommands.parseOnOff(value);
                bad = on == null;
                if (!bad) {
                    rules = new Zone.Rules(rules.voiceRange(), rules.whisperRange(), rules.rangeMultiplier(), rules.wallsStrength(),
                            rules.echo(), on, rules.enterMessage());
                }
            }
            case "message" -> rules = new Zone.Rules(rules.voiceRange(), rules.whisperRange(), rules.rangeMultiplier(),
                    rules.wallsStrength(), rules.echo(), rules.isolated(), reset || value.isEmpty() ? null : value);
            case "priority" -> {
                Integer pr = reset ? Integer.valueOf(0) : AdminCommands.parseInt(value);
                bad = pr == null || pr < -100 || pr > 100;
                priority = bad ? priority : pr;
            }
            default -> {
                return;
            }
        }
        if (bad) {
            r.badValue("zone set " + name + " " + key, value, allowed(key), "zone");
            return;
        }
        Zone updated = new Zone(z.kind(), z.name(), mode, preset, rules, z.box(), priority);
        if (updated.box() == null && updated.mode() == null && updated.preset() == null && updated.rules().isEmpty()
                && updated.priority() == 0) {
            // A world or region zone with nothing left is removed
            r.settings.removeZone(updated.key());
        } else {
            r.settings.putZone(updated);
        }
        String shown = value(updated, key);
        r.saved("zone.set", z.name(), key, shown == null ? m.get("default") : shown);
    }

    /** "range" and "walls_strength" are older names that still work. */
    private static String canonical(String key) {
        return switch (key) {
            case "range" -> "range_multiplier";
            case "walls_strength" -> "walls";
            case "enter_message" -> "message";
            case "profile_mode" -> "mode";
            case "profile_preset" -> "preset";
            default -> Arrays.asList(SETTINGS).contains(key) ? key : null;
        };
    }

    private static Zone.Rules withVoice(Zone.Rules r, Double v) {
        return new Zone.Rules(v, r.whisperRange(), r.rangeMultiplier(), r.wallsStrength(), r.echo(), r.isolated(), r.enterMessage());
    }

    private static Zone.Rules withWhisper(Zone.Rules r, Double v) {
        return new Zone.Rules(r.voiceRange(), v, r.rangeMultiplier(), r.wallsStrength(), r.echo(), r.isolated(), r.enterMessage());
    }

    /** A setting's value now and its choices as buttons. */
    private static void choices(Run r, Zone z, String name, String key) {
        Messages m = r.m;
        String now = z == null ? null : value(z, key);
        r.reply.add(CommandReply.line().addAll(m.spans("zone.value", Style.PLAIN, name, key,
                now == null ? new Span(m.get("default"), Style.MUTED) : new Span(now, Style.VALUE),
                new Span(allowed(key), Style.MUTED))));
        LineBuilder line = CommandReply.line();
        String base = "/vcd zone set " + name + " " + key + " ";
        for (String option : options(key)) {
            line.button(option, Click.RUN, base + option, m.get("hover.run", base + option));
        }
        if (key.equals("message")) {
            line.button(m.get("btn.write"), Click.SUGGEST, base, m.get("hover.suggest", base.trim()));
        }
        line.button(m.get("default"), Click.RUN, base + "default", m.get("hover.run", base + "default"));
        r.reply.add(line);
    }

    // -------------------------------------------------------------------------
    // Other actions
    // -------------------------------------------------------------------------

    private static void delete(Run r) {
        Messages m = r.m;
        Zone z = r.args.length > 2 ? r.settings.findZone(r.arg(2)) : null;
        if (z == null) {
            if (r.args.length > 2) {
                unknown(r, r.arg(2));
            } else {
                r.badValue("zone delete", "", "<name>", "zone");
            }
            return;
        }
        if (!r.arg(3).equalsIgnoreCase(CONFIRM)) {
            LineBuilder line = CommandReply.line().addAll(m.spans("zone.delete_ask", Style.WARN, z.name()));
            line.danger(m.get("btn.delete"), "/vcd zone delete " + ref(z) + " " + CONFIRM, m.get("hover.run", "/vcd zone delete " + ref(z)));
            r.reply.add(line);
            return;
        }
        r.settings.removeZone(z.key());
        r.saved("zone.deleted", z.name());
    }

    private static void show(Run r) {
        Messages m = r.m;
        if (needPlayer(r)) {
            return;
        }
        String target = r.arg(2);
        if (target.equalsIgnoreCase("off")) {
            ZoneOutlines.hide(r.me.id());
            r.line(Style.OK, m.get("zone.show_off"));
            return;
        }
        Zone z = target.isEmpty() ? r.settings.zoneOf(r.me) : r.settings.findZone(target);
        if (z == null) {
            if (target.isEmpty()) {
                r.badValue("zone show", "", "<name>|off", "zone");
            } else {
                unknown(r, target);
            }
        } else if (z.box() == null) {
            r.error("zone.show_box");
        } else if (!Zone.sameWorld(z.box().world(), r.me.world())) {
            r.error("zone.show_world", z.name(), z.box().world());
        } else {
            ZoneOutlines.show(r.me.id(), z.box());
            LineBuilder line = CommandReply.line().addAll(m.spans("zone.shown", Style.OK, z.name()));
            line.button(m.get("btn.hide"), Click.RUN, "/vcd zone show off", m.get("hover.run", "/vcd zone show off"));
            r.reply.add(line);
        }
    }

    private static void tp(Run r) {
        if (needPlayer(r)) {
            return;
        }
        String name = r.arg(2);
        Zone z = r.settings.findZone(name);
        if (z == null) {
            if (name.isEmpty()) {
                r.badValue("zone tp", "", "<name>", "zone");
            } else {
                unknown(r, name);
            }
            return;
        }
        if (z.box() == null) {
            r.error("zone.tp_box");
            return;
        }
        Zone.Box b = z.box();
        double x = (b.x1() + b.x2() + 1) / 2.0;
        double zz = (b.z1() + b.z2() + 1) / 2.0;
        // The middle of the box, not its floor: a box made round the admin reaches as far down as up
        double y = (b.y1() + b.y2() + 1) / 2.0;
        if (r.ctx.teleport(b.world(), x, y, zz)) {
            r.line(Style.OK, r.m.get("zone.tp", z.name()));
        } else {
            r.error("zone.tp_failed", z.name());
        }
    }

    private static void rename(Run r) {
        Zone z = r.args.length > 2 ? r.settings.findZone(r.arg(2)) : null;
        String name = AdminCommands.clean(r.arg(3));
        if (z == null) {
            if (r.args.length > 2) {
                unknown(r, r.arg(2));
            } else {
                r.badValue("zone rename", "", "<name> <new name>", "zone");
            }
            return;
        }
        if (name.isEmpty()) {
            r.badValue("zone rename " + z.name(), r.arg(3), "<new name>", "zone");
            return;
        }
        if (z.box() == null) {
            r.error("zone.rename_box");
            return;
        }
        if (r.settings.zones().containsKey(Zone.BOX + ":" + name)) {
            r.error("zone.exists", name);
            return;
        }
        r.settings.removeZone(z.key());
        r.settings.putZone(new Zone(Zone.BOX, name, z.mode(), z.preset(), z.rules(), z.box(), z.priority()));
        r.saved("zone.renamed", z.name(), name);
    }
}
