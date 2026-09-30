package com.kasper.vcdistance;

import com.kasper.vcdistance.CommandReply.Click;
import com.kasper.vcdistance.CommandReply.LineBuilder;
import com.kasper.vcdistance.CommandReply.Span;
import com.kasper.vcdistance.CommandReply.Style;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code /vcd} command for server admins, the same on Fabric, Forge, NeoForge and Paper (and
 * behind the Server tab): the platform only passes the typed text and shows the reply. Replies are
 * {@link CommandReply} lines with coloured values and buttons; every change is saved to the settings
 * file, sent to the players who have the addon right away, and can be taken back with {@code /vcd undo}.
 * Replies come in the admin's own game language, or the one set in the settings file.
 * <pre>
 * /vcd status                                  what the addon is doing now
 * /vcd help [topic]                            commands, or one command with examples
 * /vcd reload | undo                           re-read the settings file | take back the last change
 * /vcd log [page]                              who changed the settings, and when
 * /vcd profile off|suggest|enforce             how the profile is offered
 * /vcd preset vanilla|realistic|clear|stealth|custom | export | import &lt;code&gt;
 * /vcd walls 0-100|off                         wall strength for everyone, in %
 * /vcd serverwalls on|off                      walls for players without the addon
 * /vcd lock all|none|curve,walls,...           what players cannot change while the profile is enforced
 * /vcd group dead|spectators|zones|open_range on|off  rules for Simple Voice Chat groups
 * /vcd monitor on|off                          monitor, radar and nearby players in the HUD
 * /vcd notices on|off                          zone names above the hotbar when players enter or leave
 * /vcd zones [page]                            every zone
 * /vcd zone ...                                see {@link ZoneCommands}
 * /vcd rule sneak|dead|spectators|megaphone|megaphone_range &lt;value&gt;
 * /vcd require off|suggest|warn|kick [min version]
 * /vcd debug [player]                          what a player hears, and why not
 * </pre>
 * Who may do what is split into permissions ({@link #PERM_STATUS} and the others, all under
 * {@link #PERM_ADMIN}); the platform answers {@link Context#allows}.
 */
public final class AdminCommands {

    public static final String NAME = "vcd";

    /** Everything below. */
    public static final String PERM_ADMIN = "vcd.admin";
    /** status, help, zones, zone info. */
    public static final String PERM_STATUS = "vcd.status";
    /** The server's settings: profile, preset, walls, rules, groups, require, reload, undo. */
    public static final String PERM_SETTINGS = "vcd.settings";
    /** Making and changing zones. */
    public static final String PERM_ZONE = "vcd.zone";
    /** debug. */
    public static final String PERM_DEBUG = "vcd.debug";
    /** mute, unmute, mutes. */
    public static final String PERM_MUTE = "vcd.mute";
    /** speaker add and remove. */
    public static final String PERM_SPEAKER = "vcd.speaker";
    public static final String[] PERMISSIONS = {PERM_STATUS, PERM_SETTINGS, PERM_ZONE, PERM_DEBUG, PERM_MUTE, PERM_SPEAKER};

    /** Wall strength suggestions: off, then every 5%. */
    static final String[] WALLS_STEPS = wallsSteps();

    private static String[] wallsSteps() {
        String[] steps = new String[21];
        steps[0] = "off";
        for (int i = 1; i <= 20; i++) {
            steps[i] = String.valueOf(i * 5);
        }
        return steps;
    }

    static final String[] SUBCOMMANDS = {"status", "help", "reload", "undo", "log", "profile", "preset", "walls", "serverwalls", "effects", "extras", "radio", "speaker", "eavesdrop", "lock",
            "monitor", "notices", "zones", "zone", "rule", "group", "require", "block", "mute", "unmute", "mutes", "debug", "report"};
    /** The topics of {@code /vcd help}, in the order they are listed. */
    static final String[] TOPICS = {"status", "zones", "zone", "profile", "preset", "walls", "serverwalls", "effects", "extras", "radio", "speaker", "eavesdrop", "lock", "monitor",
            "notices", "rule", "group", "require", "block", "mute", "unmute", "mutes", "debug", "report", "undo", "log", "reload"};
    /** Times offered for {@code /vcd mute <player>}. */
    static final String[] MUTE_TIMES = {"10m", "30m", "1h", "1d", "7d", "perm"};
    static final String[] MODES = {"off", "suggest", "enforce"};
    static final String[] PRESETS = {"vanilla", "realistic", "clear", "stealth", "custom", "export", "import"};
    static final String[] LOCK_PARTS = {"all", "none", "curve", "walls", "materials", "effects"};
    static final String[] RULES = {"sneak", "dead", "spectators", "megaphone", "megaphone_range"};
    static final String[] GROUP_RULES = {"dead", "spectators", "zones", "open_range"};
    static final String[] REQUIRE = {"off", "suggest", "warn", "kick"};
    static final String[] ON_OFF = {"on", "off"};
    /** {@code /vcd effects <part> ...}: the server's own air and the strengths the profile has. */
    static final String[] EFFECT_PARTS = {"on", "off", "air", "water", "weather", "echo", "status"};
    static final String[] EFFECT_STEPS = {"off", "25", "50", "75", "100", "125", "150"};
    static final String[] ECHO_STEPS = {"off", "25", "50", "75", "100"};
    /** {@code /vcd extras <name> on|off}: the Paper plugin's extras, each off until it has been played. */
    static final String[] EXTRAS = {"radio", "speakers", "eavesdrop", "sculk", "doorway", "integrations"};
    /** Changes {@code /vcd undo} can take back, per settings file. */
    static final int UNDO_STEPS = 10;
    /** Players {@code /vcd debug} lists. */
    static final int DEBUG_PLAYERS = 8;

    /** What the platform provides to the command. */
    public interface Context {

        /** "Fabric", "NeoForge" or "Paper", for the status line. */
        String platform();

        int onlinePlayers();

        int addonPlayers();

        /** Sends every player with the addon their (zone's) profile again. */
        void resendProfiles();

        /** Drops caches that depend on the settings (block acoustics). */
        default void afterSettingsChange() {
        }

        /** The player running the command, or {@code null} for the console. */
        default UUID sender() {
            return null;
        }

        /** Online players as the voice rules see them. */
        default ServerPlayers players() {
            return AudioDistancePlugin.PLAYERS;
        }

        /**
         * Whether the sender has a permission ({@link #PERM_STATUS} and the others); having
         * {@link #PERM_ADMIN} counts as having all of them. The console always may.
         */
        default boolean allows(String permission) {
            return true;
        }

        /** The block the sender looks at, up to 64 blocks away, as {x, y, z}; {@code null} when none. */
        default int[] targetBlock() {
            return null;
        }

        /** Moves the sender to a spot; {@code false} when this platform cannot. */
        default boolean teleport(String world, double x, double y, double z) {
            return false;
        }

        /** The server's worlds, for suggestions. */
        default Collection<String> worlds() {
            return List.of();
        }

        /** Whether claim zones work here (Open Parties and Claims is installed). */
        default boolean claims() {
            return false;
        }

        /** Whether Towny towns can be zones here (Towny is installed). */
        default boolean towns() {
            return false;
        }

        /** Whether Lands lands can be zones here (Lands is installed). */
        default boolean lands() {
            return false;
        }

        /** The server software and game version, for {@code /vcd report}; "" when unknown. */
        default String serverVersion() {
            return "";
        }
    }

    /** A completion for the word being typed, with a short explanation (or {@code null}). */
    public record Suggestion(String text, String tooltip) {
    }

    /** A change {@code /vcd undo} can take back: the settings before it, and the command that made it. */
    private record Change(String before, String command, String permission) {
    }

    private static final Map<Path, Deque<Change>> HISTORY = new ConcurrentHashMap<>();

    private AdminCommands() {
    }

    /**
     * Runs a command.
     *
     * @param input everything typed after {@code /vcd}, possibly empty
     */
    public static CommandReply execute(String input, ServerSettings settings, Context ctx) {
        Run r = new Run(input, settings, ctx);
        String sub = r.args.length == 0 ? "" : r.args[0].toLowerCase(Locale.ROOT);
        if (sub.isEmpty()) {
            sub = ctx.allows(PERM_STATUS) ? "status" : "help";
        }
        String permission = permissionFor(sub, r.args.length > 1 ? r.args[1] : "");
        if (permission != null && !ctx.allows(permission)) {
            r.error("no_permission", "/vcd " + sub, permission);
            return r.reply;
        }
        if (changes(sub)) {
            r.before = settings.snapshot();
        }
        switch (sub) {
            case "status" -> status(r);
            case "help", "?" -> CommandHelp.help(r, r.args.length > 1 ? r.args[1] : "");
            case "reload" -> {
                settings.load();
                HISTORY.remove(key(settings));
                ctx.afterSettingsChange();
                ctx.resendProfiles();
                r.ok(r.m.spans("reloaded", Style.OK, settings.getPath().toString()), false);
            }
            case "undo" -> undo(r);
            case "log" -> log(r, ChangeLog.parseView(r.args));
            case "profile" -> {
                ServerSettings.ProfileMode mode = r.args.length > 1 ? ServerSettings.ProfileMode.fromId(r.args[1], null) : null;
                if (mode == null) {
                    r.badValue("profile", r.arg(1), String.join("|", MODES), "profile");
                    break;
                }
                settings.setProfileMode(mode);
                r.saved("profile_set", mode.getId());
            }
            case "preset" -> preset(r);
            case "walls" -> {
                Double strength = r.args.length > 1 ? parsePercent(r.args[1]) : null;
                if (strength == null) {
                    r.badValue("walls", r.arg(1), "0-100|off", "walls");
                    break;
                }
                settings.setWallsStrength(strength);
                if (strength > 0.0) {
                    r.saved("walls_set", pct(strength));
                } else {
                    r.saved("walls_off");
                }
                wallsNote(r);
            }
            case "serverwalls" -> onOff(r, "serverwalls", on -> settings.setServerWalls(on), "serverwalls_on", "serverwalls_off");
            case "effects" -> effects(r);
            case "extras" -> extras(r);
            case "radio" -> radio(r);
            case "eavesdrop" -> eavesdrop(r);
            case "speaker" -> speaker(r);
            case "lock" -> {
                java.util.Set<DistanceConfig.Part> parts = r.args.length > 1
                        ? DistanceConfig.Part.parseSet(String.join(",", Arrays.copyOfRange(r.args, 1, r.args.length))) : null;
                if (parts == null) {
                    r.badValue("lock", r.rest(1), "all|none|curve,walls,materials,effects", "lock");
                    break;
                }
                settings.setLockedParts(parts);
                r.saved("lock_set", DistanceConfig.Part.format(parts));
            }
            case "monitor" -> onOff(r, "monitor", on -> settings.setMonitorAllowed(on), "monitor_on", "monitor_off");
            case "notices" -> onOff(r, "notices", on -> settings.setZoneNotices(on), "notices_on", "notices_off");
            case "zones" -> ZoneCommands.list(r, r.args.length > 1 ? parseInt(r.args[1]) : null);
            case "zone" -> ZoneCommands.zone(r);
            case "rule" -> rule(r);
            case "group" -> group(r);
            case "require" -> require(r);
            case "block" -> block(r);
            case "mute" -> mute(r);
            case "unmute" -> unmute(r);
            case "mutes" -> mutes(r);
            case "debug" -> debug(r);
            case "report" -> report(r);
            default -> CommandHelp.unknown(r, sub);
        }
        return r.reply;
    }

    /** Runs a command and gives its reply as text, without buttons (the Server tab, tests). */
    public static List<String> run(String input, ServerSettings settings, Context ctx) {
        return execute(input, settings, ctx).text();
    }

    /** Completions for the word being typed, only for what the sender may use. */
    public static List<Suggestion> suggestions(String input, ServerSettings settings, Context ctx) {
        return CommandSuggest.suggest(input, settings, ctx);
    }

    /** Completions for the word being typed, from the fixed words alone. */
    public static List<String> suggest(String input) {
        List<String> out = new ArrayList<>();
        for (Suggestion s : CommandSuggest.suggest(input, null, null)) {
            out.add(s.text());
        }
        return out;
    }

    /** Whether the sender may use {@code /vcd} at all (any of its permissions). */
    public static boolean mayUseAny(Context ctx) {
        for (String p : PERMISSIONS) {
            if (ctx.allows(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The permission a subcommand needs, or {@code null} when it checks by itself (undo) or is not
     * a command (the reply is then the help).
     */
    public static String permissionFor(String sub, String action) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "status", "help", "?", "zones" -> PERM_STATUS;
            case "debug", "report" -> PERM_DEBUG;
            case "mute", "unmute", "mutes" -> PERM_MUTE;
            case "speaker" -> action.isEmpty() || action.equalsIgnoreCase("list") ? PERM_STATUS : PERM_SPEAKER;
            case "zone" -> ZoneCommands.readOnly(action) ? PERM_STATUS : PERM_ZONE;
            case "effects", "extras", "radio", "eavesdrop" -> action.isEmpty() || action.equalsIgnoreCase("info") || action.equalsIgnoreCase("status") ? PERM_STATUS : PERM_SETTINGS;
            case "reload", "log", "profile", "preset", "walls", "serverwalls", "lock", "monitor", "notices", "rule", "group", "require", "block" -> PERM_SETTINGS;
            default -> null;
        };
    }

    /** Subcommands that may change the settings (their state before is kept for undo). */
    private static boolean changes(String sub) {
        return switch (sub) {
            case "profile", "preset", "walls", "serverwalls", "effects", "extras", "radio", "speaker", "eavesdrop", "lock", "monitor", "notices", "zone", "rule", "group", "require", "block", "mute", "unmute" -> true;
            default -> false;
        };
    }

    private static Path key(ServerSettings settings) {
        return settings.getPath().toAbsolutePath().normalize();
    }

    /** How many changes {@code /vcd undo} can still take back. */
    static int undoable(ServerSettings settings) {
        Deque<Change> changes = HISTORY.get(key(settings));
        return changes == null ? 0 : changes.size();
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private static void status(Run r) {
        Messages m = r.m;
        ServerSettings settings = r.settings;
        Context ctx = r.ctx;
        r.line(Style.TITLE, m.get("status.title", BuildInfo.version(), ctx.platform()));
        double voice = AudioDistancePlugin.serverVoiceDistance();
        if (voice > 0.0) {
            r.reply.add(CommandReply.line().addAll(m.spans("status.svc", Style.PLAIN, fmt(voice),
                    fmt(AudioDistancePlugin.serverWhisperDistance()))));
        } else {
            r.line(Style.WARN, m.get("status.svc_off"));
        }
        DistanceConfig p = settings.profile();
        r.reply.add(CommandReply.line().addAll(m.spans("status.walls", Style.PLAIN,
                r.change(p.isOcclusionEnabled() ? pct(p.getOcclusionStrength()) : m.get("off"), "walls"),
                r.change(onOff(m, settings.isServerWalls()), "serverwalls"),
                AudioDistancePlugin.SERVER_WALLS.activeStreams(), settings.getMaxStreams())));
        r.reply.add(CommandReply.line().addAll(m.spans("status.effects", Style.PLAIN,
                r.change(onOff(m, settings.isServerEffects()), "effects"),
                r.change(onOff(m, settings.isServerAir()), "effects air"),
                r.change(effectText(m, p.isUnderwaterEnabled(), p.getUnderwaterStrength()), "effects water"),
                r.change(effectText(m, p.isWeatherEnabled(), p.getWeatherStrength()), "effects weather"),
                r.change(effectText(m, p.isReverbEnabled(), p.getReverbStrength()), "effects echo"))));
        r.reply.add(CommandReply.line().addAll(m.spans("status.players", Style.PLAIN, ctx.addonPlayers(), ctx.onlinePlayers())));
        r.reply.add(CommandReply.line().addAll(m.spans("status.profile", Style.PLAIN,
                r.change(settings.getProfileMode().getId(), "profile"),
                r.change(settings.getProfilePreset(), "preset"),
                p.getModel().getId(), p.isReverbEnabled() ? pct(p.getReverbStrength()) : m.get("off"))));
        r.reply.add(CommandReply.line().addAll(m.spans("status.locks", Style.PLAIN,
                r.change(DistanceConfig.Part.format(settings.getLockedParts()), "lock"),
                r.change(onOff(m, settings.isMonitorAllowed()), "monitor"))));
        r.reply.add(CommandReply.line().addAll(m.spans("status.groups", Style.PLAIN,
                r.change(onOff(m, settings.isGroupDeadSilent()), "group dead"),
                r.change(onOff(m, settings.isGroupSpectatorsApart()), "group spectators"),
                r.change(onOff(m, settings.isGroupIsolatedZones()), "group zones"),
                r.change(onOff(m, settings.isOpenGroupRange()), "group open_range"))));
        r.reply.add(CommandReply.line().addAll(m.spans("status.rules", Style.PLAIN,
                r.change(pct(settings.getSneakMultiplier()), "rule sneak"),
                r.change(onOff(m, settings.isDeadSilent()), "rule dead"),
                r.change(onOff(m, settings.isSpectatorsOnly()), "rule spectators"),
                r.change(settings.getMegaphoneItem().isEmpty() ? m.get("off")
                        : settings.getMegaphoneItem() + " ×" + fmt(settings.getMegaphoneMultiplier()), "rule megaphone"))));
        r.reply.add(CommandReply.line().addAll(m.spans("status.require", Style.PLAIN,
                r.change(settings.getRequireAddon().getId(), "require"),
                settings.getMinAddonVersion().isEmpty() ? "-" : settings.getMinAddonVersion())));
        r.reply.add(CommandReply.line().addAll(m.spans("status.zones", Style.PLAIN,
                new Span(String.valueOf(settings.zones().size()), Style.VALUE, Click.RUN, "/vcd zones", m.get("hover.run", "/vcd zones")),
                r.change(onOff(m, settings.isZoneNotices()), "notices"))));
        int muted = settings.mutes(System.currentTimeMillis()).size();
        if (muted > 0 && ctx.allows(PERM_MUTE)) {
            r.reply.add(CommandReply.line().addAll(m.spans("status.mutes", Style.PLAIN,
                    new Span(String.valueOf(muted), Style.VALUE, Click.RUN, "/vcd mutes", m.get("hover.run", "/vcd mutes")))));
        }
        r.reply.add(CommandReply.line().addAll(m.spans("status.perf", Style.MUTED,
                String.format(Locale.ROOT, "%.2f", AudioDistancePlugin.SERVER_WALLS.perf().averageMs()))));
        LineBuilder buttons = CommandReply.line();
        r.button(buttons, "btn.zones", Click.RUN, "/vcd zones", PERM_STATUS);
        r.button(buttons, "btn.help", Click.RUN, "/vcd help", PERM_STATUS);
        r.button(buttons, "btn.reload", Click.RUN, "/vcd reload", PERM_SETTINGS);
        r.button(buttons, "btn.log", Click.RUN, "/vcd log", PERM_SETTINGS);
        if (undoable(settings) > 0) {
            r.button(buttons, "btn.undo", Click.RUN, "/vcd undo", null);
        }
        if (!buttons.isEmpty()) {
            r.reply.add(buttons);
        }
    }

    /**
     * {@code /vcd effects}: water, weather and echo for players without the addon ({@code on|off}), the
     * distance air ({@code air on|off}) and the strengths the profile has ({@code water|weather|echo}).
     */
    private static void effects(Run r) {
        ServerSettings settings = r.settings;
        DistanceConfig p = settings.profile();
        String part = r.arg(1).toLowerCase(Locale.ROOT);
        switch (part) {
            case "", "status", "info" -> effectsView(r);
            case "on", "off" -> {
                boolean on = part.equals("on");
                settings.setServerEffects(on);
                if (!on) {
                    AudioDistancePlugin.SERVER_ROOMS.clear();
                }
                r.saved(on ? "effects.on" : "effects.off");
                if (on) {
                    r.line(Style.MUTED, r.m.get("effects.cost"));
                }
            }
            case "air" -> {
                Boolean on = r.args.length > 2 ? parseOnOff(r.args[2]) : null;
                if (on == null) {
                    r.badValue("effects air", r.arg(2), "on|off", "effects");
                    return;
                }
                settings.setServerAir(on);
                r.saved(on ? "effects.air_on" : "effects.air_off");
            }
            case "water", "weather", "echo" -> {
                boolean echo = part.equals("echo");
                double max = echo ? DistanceConfig.REVERB_MAX : DistanceConfig.EFFECT_STRENGTH_MAX;
                Double value = r.args.length > 2 ? parseStrength(r.args[2], max) : null;
                if (value == null) {
                    r.badValue("effects " + part, r.arg(2), (echo ? "0-100" : "0-150") + "|off", "effects");
                    return;
                }
                boolean on = value > 0.0;
                switch (part) {
                    case "water" -> {
                        p.setUnderwaterEnabled(on);
                        p.setUnderwaterStrength(on ? value : p.getUnderwaterStrength());
                    }
                    case "weather" -> {
                        p.setWeatherEnabled(on);
                        p.setWeatherStrength(on ? value : p.getWeatherStrength());
                    }
                    default -> {
                        p.setReverbEnabled(on);
                        p.setReverbStrength(on ? value : p.getReverbStrength());
                    }
                }
                if (on) {
                    r.saved("effects." + part + "_set", pct(value));
                } else {
                    r.saved("effects." + part + "_off");
                }
                effectsNote(r);
            }
            default -> r.badValue("effects", part, String.join("|", EFFECT_PARTS), "effects");
        }
    }

    /**
     * {@code /vcd extras}: the Paper plugin's extras (radio, loudspeakers, eavesdrop item, sculk, doorway sound),
     * each {@code on|off}. They are off until they have been played, so turning one on says so.
     */
    private static void extras(Run r) {
        String name = r.arg(1).toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.equals("status") || name.equals("info")) {
            extrasView(r);
            return;
        }
        if (!Arrays.asList(EXTRAS).contains(name)) {
            r.badValue("extras", name, String.join("|", EXTRAS), "extras");
            return;
        }
        Boolean on = r.args.length > 2 ? parseOnOff(r.args[2]) : null;
        if (on == null) {
            r.badValue("extras " + name, r.arg(2), "on|off", "extras");
            return;
        }
        setExtra(r.settings, name, on);
        r.saved(on ? "extras.on" : "extras.off", r.m.get("extras.name." + name));
        if (on) {
            r.line(Style.MUTED, r.m.get("extras.untested"));
        }
    }

    /**
     * {@code /vcd radio}: the radio's state, who is on the air, and {@code item <id>|none} for the item players
     * must hold. Switching it on or off is {@code /vcd extras radio on|off}.
     */
    private static void radio(Run r) {
        Messages m = r.m;
        ServerSettings settings = r.settings;
        String part = r.arg(1).toLowerCase(Locale.ROOT);
        switch (part) {
            case "", "status", "info" -> {
                r.line(Style.TITLE, m.get("radio.title"));
                r.reply.add(CommandReply.line().addAll(m.spans("radio.state", Style.PLAIN,
                        r.change(onOff(m, settings.isServerRadio()), "extras radio"))));
                r.reply.add(CommandReply.line().addAll(m.spans("radio.item", Style.PLAIN,
                        r.change(settings.getRadioItem().isEmpty() ? m.get("radio.no_item") : settings.getRadioItem(), "radio item"))));
                java.util.SortedMap<Integer, Integer> channels = AudioDistancePlugin.PLAYER_PREFS.radioChannels();
                if (channels.isEmpty()) {
                    r.line(Style.MUTED, m.get("radio.nobody"));
                } else {
                    StringBuilder on = new StringBuilder();
                    channels.forEach((frequency, count) -> on.append(on.length() == 0 ? "" : ", ").append(frequency).append(" ×").append(count));
                    r.reply.add(CommandReply.line().addAll(m.spans("radio.channels", Style.PLAIN, on.toString())));
                }
                r.line(Style.MUTED, m.get("extras.untested"));
                LineBuilder buttons = CommandReply.line();
                boolean on = settings.isServerRadio();
                r.button(buttons, on ? "btn.radio_off" : "btn.radio_on", Click.RUN, "/vcd extras radio " + (on ? "off" : "on"), PERM_SETTINGS);
                r.button(buttons, "btn.help", Click.RUN, "/vcd help radio", PERM_STATUS);
                if (undoable(settings) > 0) {
                    r.button(buttons, "btn.undo", Click.RUN, "/vcd undo", null);
                }
                if (!buttons.isEmpty()) {
                    r.reply.add(buttons);
                }
            }
            case "item" -> {
                if (r.args.length < 3) {
                    r.badValue("radio item", "", "<item id>|none", "radio");
                    return;
                }
                settings.setRadioItem(r.args[2].equalsIgnoreCase("none") || r.args[2].equalsIgnoreCase("off") ? "" : r.args[2]);
                if (settings.getRadioItem().isEmpty()) {
                    r.saved("radio.item_off");
                } else {
                    r.saved("radio.item_set", settings.getRadioItem());
                }
            }
            default -> r.badValue("radio", part, "item", "radio");
        }
    }

    /**
     * {@code /vcd eavesdrop [item <id>|none] [factor <0.05-1>]}: the state, the item a player holds to hear
     * through walls, and how much of the walls' muffling is left for them. On/off is {@code /vcd extras eavesdrop}.
     */
    private static void eavesdrop(Run r) {
        Messages m = r.m;
        ServerSettings settings = r.settings;
        String part = r.arg(1).toLowerCase(Locale.ROOT);
        switch (part) {
            case "", "status", "info" -> {
                r.line(Style.TITLE, m.get("eavesdrop.title"));
                r.reply.add(CommandReply.line().addAll(m.spans("eavesdrop.state", Style.PLAIN,
                        r.change(onOff(m, settings.isServerEavesdrop()), "extras eavesdrop"))));
                r.reply.add(CommandReply.line().addAll(m.spans("eavesdrop.item", Style.PLAIN,
                        r.change(settings.getEavesdropItem().isEmpty() ? m.get("eavesdrop.nobody") : settings.getEavesdropItem(), "eavesdrop item"))));
                r.reply.add(CommandReply.line().addAll(m.spans("eavesdrop.factor", Style.PLAIN,
                        r.change(fmt(settings.getEavesdropFactor()), "eavesdrop factor"))));
                r.line(Style.MUTED, m.get("extras.untested"));
                LineBuilder buttons = CommandReply.line();
                boolean on = settings.isServerEavesdrop();
                r.button(buttons, on ? "btn.eavesdrop_off" : "btn.eavesdrop_on", Click.RUN, "/vcd extras eavesdrop " + (on ? "off" : "on"), PERM_SETTINGS);
                r.button(buttons, "btn.help", Click.RUN, "/vcd help eavesdrop", PERM_STATUS);
                if (undoable(settings) > 0) {
                    r.button(buttons, "btn.undo", Click.RUN, "/vcd undo", null);
                }
                if (!buttons.isEmpty()) {
                    r.reply.add(buttons);
                }
            }
            case "item" -> {
                if (r.args.length < 3) {
                    r.badValue("eavesdrop item", "", "<item id>|none", "eavesdrop");
                    return;
                }
                settings.setEavesdropItem(r.args[2].equalsIgnoreCase("none") || r.args[2].equalsIgnoreCase("off") ? "" : r.args[2]);
                if (settings.getEavesdropItem().isEmpty()) {
                    r.saved("eavesdrop.item_off");
                } else {
                    r.saved("eavesdrop.item_set", settings.getEavesdropItem());
                }
            }
            case "factor" -> {
                Double value = r.args.length < 3 ? null : parseNumber(r.args[2]);
                if (value == null || value < ServerSettings.MIN_EAVESDROP_FACTOR || value > 1.0) {
                    r.badValue("eavesdrop factor", r.arg(2), fmt(ServerSettings.MIN_EAVESDROP_FACTOR) + "-1", "eavesdrop");
                    return;
                }
                settings.setEavesdropFactor(value);
                r.saved("eavesdrop.factor_set", fmt(settings.getEavesdropFactor()));
            }
            default -> r.badValue("eavesdrop", part, "item|factor", "eavesdrop");
        }
    }

    /**
     * {@code /vcd speaker add <name> [radius] [pickup]} places a loudspeaker where the sender stands,
     * {@code remove <name>} takes it away, {@code list} shows them. Whoever talks within the pickup distance of
     * it is heard from it by everyone within the radius. Needs {@code /vcd extras speakers on}.
     */
    private static void speaker(Run r) {
        Messages m = r.m;
        ServerSettings settings = r.settings;
        String action = r.arg(1).toLowerCase(Locale.ROOT);
        switch (action) {
            case "", "list", "status", "info" -> {
                r.line(Style.TITLE, m.get("speaker.title"));
                r.reply.add(CommandReply.line().addAll(m.spans("speaker.state", Style.PLAIN,
                        r.change(onOff(m, settings.isServerSpeakers()), "extras speakers"))));
                if (settings.speakers().isEmpty()) {
                    r.line(Style.MUTED, m.get("speaker.none"));
                }
                settings.speakers().values().stream().sorted(java.util.Comparator.comparing(Loudspeaker::key)).forEach(sp -> {
                    LineBuilder line = CommandReply.line().text("  ")
                            .add(new Span(sp.name(), Style.VALUE, Click.SUGGEST, "/vcd speaker add " + sp.name() + " ",
                                    m.get("hover.change", "/vcd speaker add " + sp.name())))
                            .text(" " + m.get("speaker.row", sp.world(), fmt(sp.x()), fmt(sp.y()), fmt(sp.z()),
                                    fmt(sp.pickup()), fmt(sp.radius())), Style.MUTED);
                    r.button(line, "btn.speaker_tp", Click.RUN, "/vcd speaker tp " + sp.name(), PERM_SPEAKER);
                    r.button(line, "btn.speaker_remove", Click.RUN, "/vcd speaker remove " + sp.name(), PERM_SPEAKER);
                    r.reply.add(line);
                });
                r.line(Style.MUTED, m.get("extras.untested"));
                LineBuilder buttons = CommandReply.line();
                boolean on = settings.isServerSpeakers();
                r.button(buttons, on ? "btn.speaker_off" : "btn.speaker_on", Click.RUN, "/vcd extras speakers " + (on ? "off" : "on"), PERM_SETTINGS);
                r.button(buttons, "btn.help", Click.RUN, "/vcd help speaker", PERM_STATUS);
                if (undoable(settings) > 0) {
                    r.button(buttons, "btn.undo", Click.RUN, "/vcd undo", null);
                }
                if (!buttons.isEmpty()) {
                    r.reply.add(buttons);
                }
            }
            case "add" -> {
                String name = r.arg(2);
                if (!Loudspeaker.validName(name)) {
                    r.badValue("speaker add " + name, name, "<name: letters, digits, _ and ->", "speaker");
                    return;
                }
                ServerPlayers.Info me = r.ctx.sender() == null ? null : r.ctx.players().get(r.ctx.sender());
                if (me == null) {
                    r.error("speaker.need_player");
                    return;
                }
                Double radius = r.args.length > 3 ? parseNumber(r.args[3]) : Loudspeaker.DEFAULT_RADIUS;
                Double pickup = r.args.length > 4 ? parseNumber(r.args[4]) : Loudspeaker.DEFAULT_PICKUP;
                if (radius == null || radius < 1 || radius > Loudspeaker.MAX_RADIUS) {
                    r.badValue("speaker add " + name, r.arg(3), "1-" + fmt(Loudspeaker.MAX_RADIUS), "speaker");
                    return;
                }
                if (pickup == null || pickup < 0.5 || pickup > Loudspeaker.MAX_PICKUP) {
                    r.badValue("speaker add " + name + " " + fmt(radius), r.arg(4), "0.5-" + fmt(Loudspeaker.MAX_PICKUP), "speaker");
                    return;
                }
                Loudspeaker sp = new Loudspeaker(name, me.world(), me.x(), me.y(), me.z(), pickup, radius);
                boolean replaced = settings.speaker(name) != null;
                settings.putSpeaker(sp);
                r.saved(replaced ? "speaker.moved" : "speaker.added", sp.name(), sp.world() + " " + fmt(sp.x()) + " " + fmt(sp.y()) + " " + fmt(sp.z()),
                        fmt(sp.pickup()), fmt(sp.radius()));
                if (!settings.isServerSpeakers()) {
                    r.line(Style.WARN, m.get("speaker.off_note"));
                }
            }
            case "remove", "delete" -> {
                Loudspeaker sp = settings.speaker(r.arg(2));
                if (sp == null) {
                    r.error("speaker.unknown", r.arg(2));
                    return;
                }
                settings.removeSpeaker(sp.name());
                r.saved("speaker.removed", sp.name());
            }
            case "tp" -> {
                Loudspeaker sp = settings.speaker(r.arg(2));
                if (sp == null) {
                    r.error("speaker.unknown", r.arg(2));
                } else if (!r.ctx.teleport(sp.world(), sp.x(), sp.y(), sp.z())) {
                    r.error("speaker.no_tp");
                }
            }
            default -> r.badValue("speaker", action, "add|remove|list|tp", "speaker");
        }
    }

    static boolean extra(ServerSettings settings, String name) {
        return switch (name) {
            case "radio" -> settings.isServerRadio();
            case "speakers" -> settings.isServerSpeakers();
            case "eavesdrop" -> settings.isServerEavesdrop();
            case "sculk" -> settings.isServerSculk();
            case "doorway" -> settings.isServerDoorway();
            case "integrations" -> settings.isServerIntegrations();
            default -> false;
        };
    }

    private static void setExtra(ServerSettings settings, String name, boolean on) {
        switch (name) {
            case "radio" -> settings.setServerRadio(on);
            case "speakers" -> settings.setServerSpeakers(on);
            case "eavesdrop" -> settings.setServerEavesdrop(on);
            case "sculk" -> settings.setServerSculk(on);
            case "doorway" -> settings.setServerDoorway(on);
            case "integrations" -> settings.setServerIntegrations(on);
            default -> {
            }
        }
    }

    /** Each extra with its state, a click away from changing it. */
    private static void extrasView(Run r) {
        Messages m = r.m;
        r.line(Style.TITLE, m.get("extras.title"));
        for (String name : EXTRAS) {
            r.reply.add(CommandReply.line().addAll(m.spans("extras.row", Style.PLAIN, m.get("extras.name." + name),
                    r.change(onOff(m, extra(r.settings, name)), "extras " + name))));
        }
        r.line(Style.MUTED, m.get("extras.untested"));
        LineBuilder buttons = CommandReply.line();
        r.button(buttons, "btn.help", Click.RUN, "/vcd help extras", PERM_STATUS);
        if (undoable(r.settings) > 0) {
            r.button(buttons, "btn.undo", Click.RUN, "/vcd undo", null);
        }
        if (!buttons.isEmpty()) {
            r.reply.add(buttons);
        }
    }

    /** The state of the effects, each part a click away. */
    private static void effectsView(Run r) {
        Messages m = r.m;
        ServerSettings settings = r.settings;
        DistanceConfig p = settings.profile();
        r.line(Style.TITLE, m.get("effects.title"));
        r.reply.add(CommandReply.line().addAll(m.spans("effects.state", Style.PLAIN,
                r.change(onOff(m, settings.isServerEffects()), "effects"))));
        r.reply.add(CommandReply.line().addAll(m.spans("effects.strengths", Style.PLAIN,
                r.change(effectText(m, p.isUnderwaterEnabled(), p.getUnderwaterStrength()), "effects water"),
                r.change(effectText(m, p.isWeatherEnabled(), p.getWeatherStrength()), "effects weather"),
                r.change(effectText(m, p.isReverbEnabled(), p.getReverbStrength()), "effects echo"))));
        r.reply.add(CommandReply.line().addAll(m.spans("effects.air", Style.PLAIN,
                r.change(onOff(m, settings.isServerAir()), "effects air"))));
        if (settings.hasServerRealism()) {
            r.reply.add(CommandReply.line().addAll(m.spans("effects.perf", Style.MUTED,
                    String.format(Locale.ROOT, "%.2f", AudioDistancePlugin.SERVER_WALLS.perf().averageMs()))));
        }
        effectsNote(r);
        LineBuilder buttons = CommandReply.line();
        boolean effects = settings.isServerEffects();
        r.button(buttons, effects ? "btn.effects_off" : "btn.effects_on", Click.RUN, "/vcd effects " + (effects ? "off" : "on"), PERM_SETTINGS);
        boolean air = settings.isServerAir();
        r.button(buttons, air ? "btn.air_off" : "btn.air_on", Click.RUN, "/vcd effects air " + (air ? "off" : "on"), PERM_SETTINGS);
        r.button(buttons, "btn.help", Click.RUN, "/vcd help effects", PERM_STATUS);
        if (undoable(settings) > 0) {
            r.button(buttons, "btn.undo", Click.RUN, "/vcd undo", null);
        }
        if (!buttons.isEmpty()) {
            r.reply.add(buttons);
        }
    }

    /**
     * The strengths are the profile's, so players with the addon use them only when the profile is enforced
     * with the effects locked; the server's own effects reach players without it in any case.
     */
    private static void effectsNote(Run r) {
        ServerSettings s = r.settings;
        if (s.getProfileMode() != ServerSettings.ProfileMode.ENFORCE || !s.getLockedParts().contains(DistanceConfig.Part.EFFECTS)) {
            r.line(Style.MUTED, r.m.get("effects.note"));
        }
    }

    /** "off", or the strength as a percentage. */
    private static String effectText(Messages m, boolean enabled, double strength) {
        return enabled ? pct(strength) : m.get("off");
    }

    /**
     * {@code /vcd mute <player> [time] [reason]}: nobody hears the player until the time runs out (no time:
     * until {@code /vcd unmute}). Muting again changes the time and the reason.
     */
    private static void mute(Run r) {
        Messages m = r.m;
        String who = r.arg(1);
        if (who.isEmpty()) {
            r.badValue("mute", "", "<player> [10m|1h|1d|perm] [reason]", "mute");
            return;
        }
        ServerPlayers.Info online = r.ctx.players().byName(who);
        UUID id = online != null ? online.id() : parseUuid(who);
        VoiceMute known = online != null ? null : r.settings.findMute(who, System.currentTimeMillis());
        if (id == null && known != null) {
            id = known.player();
        }
        if (id == null) {
            r.error("mute.unknown", who);
            return;
        }
        String name = online != null ? online.name() : known != null ? known.name() : who;
        int reasonFrom = 2;
        long duration = 0L;
        String time = r.arg(2);
        if (!time.isEmpty()) {
            Long parsed = VoiceMute.parseDuration(time);
            if (parsed != null) {
                duration = parsed;
                reasonFrom = 3;
            } else if (Character.isDigit(time.charAt(0))) {
                r.badValue("mute " + who, time, "30s|10m|2h|1d|1w|perm", "mute");
                return;
            }
        }
        long now = System.currentTimeMillis();
        VoiceMute mute = new VoiceMute(id, name, duration > 0 ? now + duration : 0L, r.who(), r.rest(reasonFrom));
        r.settings.mute(mute);
        LineBuilder line = CommandReply.line().addAll(m.spans("mute.done", Style.OK, name, muteTime(m, mute, now)));
        r.savedLine(line);
        if (!mute.reason().isEmpty()) {
            r.reply.add(CommandReply.line().addAll(m.spans("mute.reason", Style.MUTED, mute.reason())));
        }
        LineBuilder buttons = CommandReply.line();
        r.button(buttons, "btn.unmute", Click.RUN, "/vcd unmute " + name, PERM_MUTE);
        r.button(buttons, "btn.mutes", Click.RUN, "/vcd mutes", PERM_MUTE);
        r.reply.add(buttons);
    }

    /** {@code /vcd unmute <player>}: by name (online or as muted) or UUID. */
    private static void unmute(Run r) {
        String who = r.arg(1);
        if (who.isEmpty()) {
            r.badValue("unmute", "", "<player>", "unmute");
            return;
        }
        long now = System.currentTimeMillis();
        VoiceMute mute = r.settings.findMute(who, now);
        if (mute == null) {
            ServerPlayers.Info online = r.ctx.players().byName(who);
            mute = online == null ? null : r.settings.muteOf(online.id(), now);
        }
        if (mute == null) {
            LineBuilder line = CommandReply.line().addAll(r.m.spans("unmute.none", Style.WARN, who));
            line.button(r.m.get("btn.mutes"), Click.RUN, "/vcd mutes", r.m.get("hover.run", "/vcd mutes"));
            r.reply.add(line);
            return;
        }
        r.settings.unmute(mute.player());
        r.saved("unmute.done", mute.name());
    }

    /** {@code /vcd mutes}: who is muted, for how long more, by whom and why, each with an Unmute button. */
    private static void mutes(Run r) {
        Messages m = r.m;
        long now = System.currentTimeMillis();
        List<VoiceMute> all = r.settings.mutes(now);
        if (all.isEmpty()) {
            LineBuilder line = CommandReply.line().text(m.get("mutes.none"), Style.MUTED);
            line.button(m.get("btn.mute"), Click.SUGGEST, "/vcd mute ", m.get("hover.suggest", "/vcd mute"));
            r.reply.add(line);
            return;
        }
        r.line(Style.TITLE, m.get("mutes.title", all.size()));
        for (VoiceMute mute : all) {
            String by = ChangeLog.CONSOLE.equals(mute.by()) ? m.get("log.console") : mute.by().isEmpty() ? "-" : mute.by();
            LineBuilder line = CommandReply.line().text("• ", Style.MUTED)
                    .addAll(m.spans("mutes.line", Style.PLAIN,
                            new Span(mute.name(), Style.VALUE, Click.SUGGEST, "/vcd mute " + mute.name() + " ", m.get("hover.change", "/vcd mute " + mute.name())),
                            mute.isPermanent() ? m.get("mutes.no_end") : m.get("mutes.left", VoiceMute.formatDuration(mute.leftAt(now))),
                            by));
            if (!mute.reason().isEmpty()) {
                line.text(" - " + mute.reason(), Style.MUTED);
            }
            line.button(m.get("btn.unmute"), Click.RUN, "/vcd unmute " + mute.name(), m.get("hover.run", "/vcd unmute " + mute.name()));
            r.reply.add(line);
        }
    }

    /** "for 10m" or "until unmuted". */
    static String muteTime(Messages m, VoiceMute mute, long now) {
        return mute.isPermanent() ? m.get("mute.forever") : m.get("mute.for", VoiceMute.formatDuration(mute.leftAt(now)));
    }

    private static UUID parseUuid(String s) {
        try {
            return s.length() == 36 ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * {@code /vcd report}: versions, settings, load and the last problems as one text to copy into a bug
     * report. The text itself is in English, so whoever reads the report can read it.
     */
    private static void report(Run r) {
        List<String> lines = ServerReport.lines(r.settings, r.ctx);
        String text = String.join("\n", lines);
        LineBuilder title = CommandReply.line().text(r.m.get("report.title"), Style.TITLE);
        title.button(r.m.get("btn.copy"), Click.COPY, text, r.m.get("hover.copy"));
        r.reply.add(title);
        for (String line : lines) {
            r.line(Style.MUTED, line);
        }
        r.line(Style.PLAIN, r.m.get("report.hint"));
    }

    private static void undo(Run r) {
        Deque<Change> changes = HISTORY.get(key(r.settings));
        Change last = changes == null ? null : changes.peekFirst();
        if (last == null) {
            r.line(Style.WARN, r.m.get("undo.none"));
            return;
        }
        if (!r.ctx.allows(last.permission())) {
            r.error("no_permission", last.command(), last.permission());
            return;
        }
        changes.pollFirst();
        if (!r.settings.restore(last.before())) {
            r.error("undo.failed", r.settings.getPath().toString());
            return;
        }
        r.ctx.afterSettingsChange();
        r.ctx.resendProfiles();
        ChangeLog.append(r.settings, new ChangeLog.Entry(java.time.Instant.now(), r.who(), last.command(), true));
        LineBuilder line = CommandReply.line().addAll(r.m.spans("undo.done", Style.OK,
                new Span(last.command(), Style.VALUE)));
        if (!changes.isEmpty()) {
            line.button(r.m.get("btn.undo_more"), Click.RUN, "/vcd undo", r.m.get("hover.undo", changes.peekFirst().command()));
        }
        r.reply.add(line);
    }

    private static final java.time.format.DateTimeFormatter LOG_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault());

    /** {@code /vcd log [page] [player]}: the changes, ten to a page, everyone's or one player's. */
    private static void log(Run r, ChangeLog.View view) {
        Messages m = r.m;
        List<ChangeLog.Entry> all = ChangeLog.read(r.settings);
        if (all.isEmpty()) {
            r.line(Style.MUTED, m.get("log.none", ChangeLog.FILE));
            return;
        }
        ChangeLog.Page shown = ChangeLog.page(all, view);
        if (shown.total() == 0) {
            r.line(Style.MUTED, m.get("log.none_by", view.who()));
            return;
        }
        int pages = shown.pages();
        int p = shown.page();
        String suffix = view.who() == null ? "" : " " + view.who();
        r.line(Style.TITLE, m.get("log.title", shown.total()));
        for (ChangeLog.Entry e : shown.entries()) {
            String who = ChangeLog.CONSOLE.equals(e.who()) ? m.get("log.console") : e.who();
            LineBuilder line = CommandReply.line()
                    .text("• " + LOG_TIME.format(e.time()) + " ", Style.MUTED)
                    .text(who + " ", Style.VALUE);
            if (e.undo()) {
                line.addAll(m.spans("log.undo", Style.WARN, new Span(e.command(), Style.PLAIN)));
            } else {
                line.add(new Span(e.command(), Style.PLAIN, Click.SUGGEST, e.command(), m.get("hover.suggest", e.command())));
            }
            r.reply.add(line);
        }
        LineBuilder footer = CommandReply.line();
        if (pages > 1) {
            footer.text(m.get("zones.page", p, pages), Style.MUTED);
            if (p > 1) {
                footer.button("‹", Click.RUN, "/vcd log " + (p - 1) + suffix, m.get("hover.run", "/vcd log " + (p - 1) + suffix));
            }
            if (p < pages) {
                footer.button("›", Click.RUN, "/vcd log " + (p + 1) + suffix, m.get("hover.run", "/vcd log " + (p + 1) + suffix));
            }
            r.reply.add(footer);
        }
        r.line(Style.MUTED, m.get("log.file", ChangeLog.fileFor(r.settings).getFileName()));
    }

    /** {@code /vcd block add|remove|list|clear}: blocks and block tags that count as a material of their own. */
    private static void block(Run r) {
        ServerSettings settings = r.settings;
        String action = r.arg(1).toLowerCase(Locale.ROOT);
        BlockRules rules = settings.getBlockRules();
        switch (action) {
            case "", "list" -> {
                if (rules.isEmpty()) {
                    r.line(Style.MUTED, r.m.get("block_none"));
                    return;
                }
                r.line(Style.TITLE, r.m.get("block_list", rules.size()));
                for (BlockRules.Rule rule : rules.rules()) {
                    String material = rule.material().getId();
                    r.reply.add(CommandReply.line()
                            .text("• " + rule.key() + " → ", Style.PLAIN)
                            .text(material + " ", Style.VALUE)
                            .button("×", Click.SUGGEST, "/vcd block remove " + rule.key(),
                                    r.m.get("hover.suggest", "/vcd block remove " + rule.key())));
                }
            }
            case "add", "set" -> {
                String key = BlockRules.normalize(r.arg(2));
                AcousticMaterial material = BlockRules.materialOf(r.arg(3));
                if (key == null) {
                    r.badValue("block add", r.arg(2), "<namespace:block>|#<namespace:tag> <material>", "block");
                    return;
                }
                if (material == null) {
                    r.badValue("block add " + r.arg(2), r.arg(3), materialIds(), "block");
                    return;
                }
                BlockRules next = rules.with(key, material);
                if (next == null) {
                    r.error("block_full", BlockRules.MAX_RULES);
                    return;
                }
                settings.setBlockRules(next);
                r.saved("block_added", key, material.getId());
            }
            case "remove", "delete" -> {
                String key = BlockRules.normalize(r.arg(2));
                if (key == null || rules.get(key) == null) {
                    r.badValue("block remove", r.arg(2), rules.isEmpty() ? "-" : blockKeys(rules), "block");
                    return;
                }
                settings.setBlockRules(rules.without(key));
                r.saved("block_removed", key);
            }
            case "clear" -> {
                if (rules.isEmpty()) {
                    r.line(Style.MUTED, r.m.get("block_none"));
                    return;
                }
                settings.setBlockRules(BlockRules.EMPTY);
                r.saved("block_cleared");
            }
            default -> r.badValue("block", r.arg(1), "list|add|remove|clear", "block");
        }
    }

    static String materialIds() {
        StringBuilder sb = new StringBuilder();
        for (AcousticMaterial m : AcousticMaterial.values()) {
            sb.append(sb.length() == 0 ? "" : "|").append(m.getId());
        }
        return sb.toString();
    }

    private static String blockKeys(BlockRules rules) {
        StringBuilder sb = new StringBuilder();
        for (BlockRules.Rule rule : rules.rules()) {
            sb.append(sb.length() == 0 ? "" : "|").append(rule.key());
        }
        return sb.toString();
    }

    private static void preset(Run r) {
        ServerSettings settings = r.settings;
        String name = r.args.length > 1 ? r.args[1].toLowerCase(Locale.ROOT) : "";
        if (name.equals("export")) {
            double range = AudioDistancePlugin.serverVoiceDistance();
            String code = ProfileCode.encode(settings.profileIn(null, range > 0.0 ? range : AudioDistancePlugin.FALLBACK_DISTANCE));
            r.line(Style.PLAIN, r.m.get("preset_export"));
            r.reply.add(CommandReply.line()
                    .add(new Span(code, Style.VALUE, Click.COPY, code, r.m.get("hover.copy")))
                    .button(r.m.get("btn.copy"), Click.COPY, code, r.m.get("hover.copy")));
            return;
        }
        if (name.equals("import")) {
            // The code may have been split by the chat box; spaces are ignored
            String code = r.args.length > 2 ? String.join("", Arrays.copyOfRange(r.args, 2, r.args.length)) : "";
            if (!settings.importProfile(code)) {
                r.badValue("preset import", code, "VP1:...", "preset");
                return;
            }
            r.saved("preset_imported");
            return;
        }
        if (!name.equals(ServerSettings.CUSTOM_PRESET) && ServerSettings.presetByName(name) == null) {
            r.badValue("preset", r.arg(1), "vanilla|realistic|clear|stealth|custom|export|import <code>", "preset");
            return;
        }
        settings.setProfilePreset(name);
        r.saved("preset_set", settings.getProfilePreset());
    }

    private static void onOff(Run r, String command, java.util.function.Consumer<Boolean> set, String onKey, String offKey) {
        Boolean on = r.args.length > 1 ? parseOnOff(r.args[1]) : null;
        if (on == null) {
            r.badValue(command, r.arg(1), "on|off", command);
            return;
        }
        set.accept(on);
        r.saved(on ? onKey : offKey);
    }

    private static void rule(Run r) {
        ServerSettings settings = r.settings;
        String name = r.args.length > 1 ? r.args[1].toLowerCase(Locale.ROOT) : "";
        String value = r.args.length > 2 ? r.args[2] : "";
        switch (name) {
            case "sneak" -> {
                Double v = parseNumber(value.endsWith("%") ? String.valueOf(parsePercent(value)) : value);
                if (v == null || v < 0.1 || v > 1.0) {
                    r.badValue("rule sneak", value, "0.1-1", "rule");
                    return;
                }
                settings.setSneakMultiplier(v);
                r.saved("rule.sneak", pct(settings.getSneakMultiplier()));
            }
            case "dead", "spectators" -> {
                Boolean on = parseOnOff(value);
                if (on == null) {
                    r.badValue("rule " + name, value, "on|off", "rule");
                    return;
                }
                if (name.equals("dead")) {
                    settings.setDeadSilent(on);
                } else {
                    settings.setSpectatorsOnly(on);
                }
                r.saved("rule." + name + (on ? ".on" : ".off"));
            }
            case "megaphone" -> {
                if (value.isEmpty()) {
                    r.badValue("rule megaphone", value, "<item id>|off", "rule");
                    return;
                }
                settings.setMegaphoneItem(value);
                if (settings.getMegaphoneItem().isEmpty()) {
                    r.saved("rule.megaphone.off");
                } else {
                    r.saved("rule.megaphone.on", settings.getMegaphoneItem(), fmt(settings.getMegaphoneMultiplier()));
                }
            }
            case "megaphone_range" -> {
                Double v = parseNumber(value);
                if (v == null || v < 1.0 || v > 10.0) {
                    r.badValue("rule megaphone_range", value, "1-10", "rule");
                    return;
                }
                settings.setMegaphoneMultiplier(v);
                r.saved("rule.megaphone_range", fmt(settings.getMegaphoneMultiplier()));
            }
            default -> r.badValue("rule", name, String.join("|", RULES), "rule");
        }
    }

    private static void group(Run r) {
        ServerSettings settings = r.settings;
        String name = r.args.length > 1 ? r.args[1].toLowerCase(Locale.ROOT) : "";
        if (!Arrays.asList(GROUP_RULES).contains(name)) {
            r.badValue("group", name, String.join("|", GROUP_RULES), "group");
            return;
        }
        Boolean on = r.args.length > 2 ? parseOnOff(r.args[2]) : null;
        if (on == null) {
            r.badValue("group " + name, r.arg(2), "on|off", "group");
            return;
        }
        switch (name) {
            case "dead" -> settings.setGroupDeadSilent(on);
            case "spectators" -> settings.setGroupSpectatorsApart(on);
            case "zones" -> settings.setGroupIsolatedZones(on);
            default -> settings.setOpenGroupRange(on);
        }
        r.saved("group." + name + (on ? ".on" : ".off"));
    }

    private static void require(Run r) {
        ServerSettings settings = r.settings;
        ServerSettings.RequireAddon mode = r.args.length > 1 ? ServerSettings.RequireAddon.fromId(r.args[1], null) : null;
        if (mode == null) {
            r.badValue("require", r.arg(1), "off|suggest|warn|kick [version]", "require");
            return;
        }
        settings.setRequireAddon(mode);
        if (r.args.length > 2) {
            settings.setMinAddonVersion(r.args[2].equals("-") || r.args[2].equalsIgnoreCase("any") ? "" : r.args[2]);
        }
        r.saved("require.set", mode.getId(), settings.getMinAddonVersion().isEmpty() ? "-" : settings.getMinAddonVersion());
    }

    private static void debug(Run r) {
        Messages m = r.m;
        ServerSettings settings = r.settings;
        ServerPlayers players = r.ctx.players();
        ServerPlayers.Info target = r.args.length > 1 ? players.byName(r.args[1]) : r.me;
        if (target == null) {
            if (r.args.length > 1) {
                r.error("debug.unknown", r.args[1]);
            } else {
                r.badValue("debug", "", "<player>", "debug");
            }
            return;
        }
        double voice = AudioDistancePlugin.serverVoiceDistance();
        double whisper = AudioDistancePlugin.serverWhisperDistance();
        if (voice <= 0.0) {
            voice = AudioDistancePlugin.FALLBACK_DISTANCE;
            whisper = voice / 2.0;
        }
        Zone zone = settings.zoneOf(target);
        String version = AudioDistancePlugin.ADDON_CHECK.version(target.id());
        r.line(Style.TITLE, m.get("debug.title", target.name()));
        r.reply.add(CommandReply.line().addAll(m.spans("debug.where", Style.PLAIN, target.world(),
                fmt(target.x()) + " " + fmt(target.y()) + " " + fmt(target.z()),
                zone == null ? new Span("-", Style.VALUE)
                        : new Span(zone.name(), Style.VALUE, Click.RUN, "/vcd zone info " + zone.name(), ZoneCommands.describe(zone, m)))));
        r.reply.add(CommandReply.line().addAll(m.spans("debug.addon", Style.PLAIN,
                version == null ? new Span(m.get("debug.no_addon"), Style.WARN) : new Span(version, Style.VALUE),
                onOff(m, ServerRange.isMegaphone(settings, target)), onOff(m, target.sneaking()))));
        String[] group = AudioDistancePlugin.groupOf(target.id());
        if (group != null) {
            r.reply.add(CommandReply.line().addAll(m.spans("debug.group", Style.PLAIN, group[0],
                    group[1].isEmpty() ? "-" : m.get("group.type." + group[1]))));
        }
        VoiceMute mute = settings.muteOf(target.id(), System.currentTimeMillis());
        if (mute != null) {
            LineBuilder line = CommandReply.line().addAll(m.spans("debug.muted", Style.WARN,
                    muteTime(m, mute, System.currentTimeMillis()), mute.by().isEmpty() ? "-" : mute.by()));
            if (!mute.reason().isEmpty()) {
                line.text(" - " + mute.reason(), Style.MUTED);
            }
            if (r.ctx.allows(PERM_MUTE)) {
                line.button(m.get("btn.unmute"), Click.RUN, "/vcd unmute " + mute.name(), m.get("hover.run", "/vcd unmute " + mute.name()));
            }
            r.reply.add(line);
        }
        r.reply.add(CommandReply.line().addAll(m.spans("debug.range", Style.PLAIN,
                fmt(ServerRange.rangeOf(settings, target, false, voice, whisper)),
                fmt(ServerRange.rangeOf(settings, target, true, voice, whisper)))));
        List<ServerPlayers.Info> others = new ArrayList<>();
        for (ServerPlayers.Info other : players.all()) {
            if (!other.id().equals(target.id()) && Zone.sameWorld(other.world(), target.world())) {
                others.add(other);
            }
        }
        others.sort((a, b) -> Double.compare(a.distanceTo(target), b.distanceTo(target)));
        for (int i = 0; i < Math.min(DEBUG_PLAYERS, others.size()); i++) {
            ServerPlayers.Info other = others.get(i);
            // How the target hears them, and how they hear the target
            ServerRange.Decision in = ServerRange.decide(settings, other, target, false, voice, whisper);
            ServerRange.Decision outgoing = ServerRange.decide(settings, target, other, false, voice, whisper);
            r.reply.add(CommandReply.line().addAll(m.spans("debug.line", Style.PLAIN,
                    new Span(other.name(), Style.VALUE, Click.RUN, "/vcd debug " + other.name(), m.get("hover.debug", other.name())),
                    fmt(other.distanceTo(target)), reason(m, in), reason(m, outgoing))));
        }
        if (others.isEmpty()) {
            r.line(Style.MUTED, m.get("debug.alone"));
        } else if (others.size() > DEBUG_PLAYERS) {
            r.line(Style.MUTED, m.get("debug.more", others.size() - DEBUG_PLAYERS));
        }
    }

    private static Span reason(Messages m, ServerRange.Decision d) {
        String id = d.reason().name().toLowerCase(Locale.ROOT);
        return new Span(m.get("debug.reason." + id), d.hears() ? Style.OK : Style.ERROR, null, null,
                d.hears() ? null : m.get("debug.why." + id));
    }

    static String onOff(Messages m, boolean on) {
        return on ? m.get("on") : m.get("off");
    }

    // -------------------------------------------------------------------------
    // One run of a command
    // -------------------------------------------------------------------------

    /** The state of one command: who runs it, in which language, the reply so far. */
    static final class Run {

        final String input;
        final String[] args;
        final ServerSettings settings;
        final Context ctx;
        final ServerPlayers.Info me;
        final Messages m;
        final CommandReply reply = new CommandReply();
        /** The settings file before the command, for undo; {@code null} for commands that change nothing. */
        String before;

        Run(String input, ServerSettings settings, Context ctx) {
            this.input = input == null ? "" : input.trim();
            this.args = this.input.isEmpty() ? new String[0] : this.input.split("\\s+");
            this.settings = settings;
            this.ctx = ctx;
            this.me = ctx.sender() == null ? null : ctx.players().get(ctx.sender());
            this.m = new Messages(settings.languageFor(me == null ? "" : me.language()));
        }

        /** Argument {@code i}, or "". */
        String arg(int i) {
            return i < args.length ? args[i] : "";
        }

        /** Arguments from {@code i} on, joined by spaces. */
        String rest(int i) {
            return i < args.length ? String.join(" ", Arrays.copyOfRange(args, i, args.length)) : "";
        }

        void line(Style style, String text) {
            reply.add(text, style);
        }

        void error(String key, Object... args) {
            reply.add(CommandReply.line().addAll(m.spans(key, Style.ERROR, args)));
        }

        /**
         * "walls: "150" does not work. Allowed: 0-100|off" with a button that puts the command back
         * in the chat box, and one that shows the command's help.
         */
        void badValue(String command, String value, String allowed, String topic) {
            LineBuilder line = CommandReply.line();
            if (value == null || value.isBlank()) {
                line.addAll(m.spans("error.missing", Style.ERROR, "/vcd " + command, new Span(allowed, Style.VALUE)));
            } else {
                line.addAll(m.spans("error.value", Style.ERROR, "/vcd " + command, value, new Span(allowed, Style.VALUE)));
            }
            line.button(m.get("btn.fix"), Click.SUGGEST, "/vcd " + command + " ", m.get("hover.suggest", "/vcd " + command));
            line.button("?", Click.RUN, "/vcd help " + topic, m.get("hover.help", "/vcd " + topic));
            reply.add(line);
        }

        /** A value that puts the command changing it in the chat box when clicked. */
        Span change(String value, String command) {
            return new Span(value, Style.VALUE, Click.SUGGEST, "/vcd " + command + " ", m.get("hover.change", "/vcd " + command));
        }

        /** A button, when the sender has {@code permission} (or it is {@code null}). */
        void button(LineBuilder line, String labelKey, Click click, String command, String permission) {
            if (permission == null || ctx.allows(permission)) {
                line.button(m.get(labelKey), click, command, m.get(click == Click.RUN ? "hover.run" : "hover.suggest", command));
            }
        }

        /** A line in the OK colour; with {@code undo}, a button that takes the change back. */
        void ok(List<Span> spans, boolean undo) {
            LineBuilder line = CommandReply.line().addAll(spans);
            if (undo) {
                line.button(m.get("btn.undo"), Click.RUN, "/vcd undo", m.get("hover.undo", "/vcd " + input));
            }
            reply.add(line);
        }

        /** Saves the settings, sends them to the players, remembers the change for undo and says so. */
        void saved(String key, Object... args) {
            savedLine(CommandReply.line().addAll(m.spans(key, Style.OK, args)));
        }

        void savedLine(LineBuilder line) {
            settings.save();
            ctx.afterSettingsChange();
            ctx.resendProfiles();
            boolean undo = remember();
            if (undo) {
                line.button(m.get("btn.undo"), Click.RUN, "/vcd undo", m.get("hover.undo", "/vcd " + input));
            }
            reply.add(line);
        }

        private boolean remember() {
            if (before == null) {
                return false;
            }
            String after = settings.snapshot();
            if (before.equals(after)) {
                return false;
            }
            Deque<Change> changes = HISTORY.computeIfAbsent(key(settings), k -> new ArrayDeque<>());
            synchronized (changes) {
                String permission = permissionFor(arg(0), arg(1));
                changes.addFirst(new Change(before, "/vcd " + input, permission == null ? PERM_SETTINGS : permission));
                while (changes.size() > UNDO_STEPS) {
                    changes.pollLast();
                }
            }
            before = after;
            ChangeLog.append(settings, new ChangeLog.Entry(java.time.Instant.now(), who(), "/vcd " + input, false));
            return true;
        }

        /** Who runs the command, for the change log. */
        String who() {
            if (me != null) {
                return me.name();
            }
            return ctx.sender() == null ? ChangeLog.CONSOLE : ctx.sender().toString();
        }
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /**
     * The server's walls reach players without the addon by themselves; players with it use their own
     * unless the profile is enforced with the walls locked. Says so when that is the case.
     */
    private static void wallsNote(Run r) {
        ServerSettings s = r.settings;
        if (s.getProfileMode() != ServerSettings.ProfileMode.ENFORCE || !s.getLockedParts().contains(DistanceConfig.Part.WALLS)) {
            r.line(Style.MUTED, r.m.get("walls.note"));
        }
    }

    static Double parsePercent(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.equals("off")) {
            return 0.0;
        }
        boolean percent = v.endsWith("%");
        if (percent) {
            v = v.substring(0, v.length() - 1);
        }
        try {
            double d = Double.parseDouble(v.replace(',', '.'));
            if (!Double.isFinite(d) || d < 0.0 || d > 100.0) {
                return null;
            }
            // With a % sign it is a percentage; without, 0.6 and 60 both mean 60%
            return Math.min(1.0, percent || d > 1.0 ? d / 100.0 : d);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A strength from 0 to {@code max} (1 = 100%) as a fraction: "off", "70%", "70", "0.7" and "150%" for a
     * maximum of 1.5. Without a % sign a number up to 2 is the fraction itself and a larger one a percentage.
     */
    static Double parseStrength(String s, double max) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.equals("off")) {
            return 0.0;
        }
        boolean percent = v.endsWith("%");
        if (percent) {
            v = v.substring(0, v.length() - 1);
        }
        try {
            double d = Double.parseDouble(v.replace(',', '.'));
            if (!Double.isFinite(d) || d < 0.0) {
                return null;
            }
            double fraction = percent || d > 2.0 ? d / 100.0 : d;
            return fraction > max + 1e-9 ? null : Math.min(max, fraction);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Boolean parseOnOff(String s) {
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    static Double parseNumber(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Zone names: letters, digits, '_' and '-' (they become settings keys). */
    static String clean(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    static String pct(double v) {
        return Math.round(v * 100.0) + "%";
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Reply texts in the chosen language (the {@code command.vc-audio-distance.*} keys). */
    static final class Messages {

        final String language;

        Messages(String language) {
            this.language = language;
        }

        String get(String key, Object... args) {
            return ServerText.get(language, key, args);
        }

        /** The text of {@code key} as spans in {@code style}, with its values as their own spans. */
        List<Span> spans(String key, Style style, Object... args) {
            return CommandReply.fill(ServerText.get(language, key), style, args);
        }
    }
}
