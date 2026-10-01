package com.kasper.vcdistance.client;

import com.kasper.vcdistance.compat.Txt;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.HudMode;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.Preset;
import com.kasper.vcdistance.ProfileCode;
import com.kasper.vcdistance.ServerSettings;
import com.kasper.vcdistance.server.ChatLink;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The player's own {@code /voicephysics} command (client side, works on any server):
 * <pre>
 * /voicephysics [distance|walls|effects|hud|monitor|server]   open the settings, on a tab
 * /voicephysics preset vanilla|realistic|clear|stealth         a sound preset
 * /voicephysics hud off|talking|always | hud compact on|off     the voice HUD
 * /voicephysics code [code]                                     copy your profile code, or load one
 * /voicephysics reset curve|walls|materials|effects|hud|all     back to the defaults
 * /voicephysics status                                          what the server does to your sound
 * /voicephysics log                                             the server's change log (admins)
 * /voicephysics report                                          one text to copy into a bug report
 * /voicephysics help
 * </pre>
 * Built with Brigadier alone, since each loader names its own command helpers differently.
 */
final class ClientCommands {

    static final String[] PRESETS = {"vanilla", "realistic", "clear", "stealth"};
    static final String[] RESET = {"curve", "walls", "materials", "effects", "hud", "all"};
    private static final String KEY = "message.vc-audio-distance.cmd.";

    private ClientCommands() {
    }

    static <S> LiteralArgumentBuilder<S> tree(String name) {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal(name).executes(c -> open(SettingsScreen.Tab.DISTANCE, false));
        for (SettingsScreen.Tab tab : SettingsScreen.Tab.values()) {
            String word = tab.name().toLowerCase(Locale.ROOT);
            LiteralArgumentBuilder<S> literal = LiteralArgumentBuilder.<S>literal(word).executes(c -> open(tab, true));
            if (tab == SettingsScreen.Tab.HUD) {
                for (HudMode mode : HudMode.values()) {
                    literal.then(LiteralArgumentBuilder.<S>literal(mode.getId()).executes(c -> hud(mode)));
                }
                literal.then(LiteralArgumentBuilder.<S>literal("compact")
                        .then(LiteralArgumentBuilder.<S>literal("on").executes(c -> compact(true)))
                        .then(LiteralArgumentBuilder.<S>literal("off").executes(c -> compact(false))));
            }
            root.then(literal);
        }
        root.then(LiteralArgumentBuilder.<S>literal("preset")
                .executes(c -> presetUsage())
                .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                        .suggests(words(PRESETS))
                        .executes(c -> preset(StringArgumentType.getString(c, "name")))));
        root.then(LiteralArgumentBuilder.<S>literal("code")
                .executes(c -> showCode())
                .then(RequiredArgumentBuilder.<S, String>argument("code", StringArgumentType.greedyString())
                        .executes(c -> loadCode(StringArgumentType.getString(c, "code")))));
        root.then(LiteralArgumentBuilder.<S>literal("reset")
                .executes(c -> help())
                .then(RequiredArgumentBuilder.<S, String>argument("part", StringArgumentType.word())
                        .suggests(words(RESET))
                        .executes(c -> reset(StringArgumentType.getString(c, "part")))));
        root.then(LiteralArgumentBuilder.<S>literal("log").executes(c -> openLog()));
        root.then(LiteralArgumentBuilder.<S>literal("status").executes(c -> status()));
        root.then(LiteralArgumentBuilder.<S>literal("report").executes(c -> report()));
        root.then(LiteralArgumentBuilder.<S>literal("help").executes(c -> help()));
        return root;
    }

    private static <S> SuggestionProvider<S> words(String[] options) {
        return (CommandContext<S> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) -> {
            String typed = b.getRemaining().toLowerCase(Locale.ROOT);
            for (String o : options) {
                if (o.startsWith(typed)) {
                    b.suggest(o);
                }
            }
            return b.buildFuture();
        };
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private static int open(SettingsScreen.Tab tab, boolean chosen) {
        if (chosen) {
            SettingsScreen.openOn(tab);
        }
        ClientHints.requestOpen();
        return 1;
    }

    private static int openLog() {
        if (!AudioDistancePlugin.LINK.isAdmin()) {
            say(error("log.admin"));
            return 0;
        }
        SettingsScreen.openLogNext();
        ClientHints.requestOpen();
        return 1;
    }

    private static int hud(HudMode mode) {
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        config.setHudMode(mode);
        config.save();
        say(ok("hud", Txt.translatable(mode.getTranslationKey())));
        return 1;
    }

    private static int compact(boolean on) {
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        config.setHudCompact(on);
        config.save();
        say(ok(on ? "hud.compact.on" : "hud.compact.off"));
        return 1;
    }

    private static int presetUsage() {
        MutableComponent line = Txt.translatable(KEY + "preset.choose").withStyle(ChatFormatting.GRAY);
        for (String p : PRESETS) {
            line.append(" ").append(button(Txt.translatable(ServerSettings.presetByName(p).getTranslationKey()),
                    "/" + ClientHints.COMMAND + " preset " + p, false));
        }
        say(line);
        return 1;
    }

    private static int preset(String name) {
        Preset p = ServerSettings.presetByName(name);
        if (p == null) {
            say(error("preset.unknown", name, String.join(", ", PRESETS)));
            return 0;
        }
        if (AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.CURVE)) {
            say(error("locked"));
            return 0;
        }
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        double range = AudioDistancePlugin.getServerMaxDistance();
        // A preset sets the curve and the walls, but never walls the server locks
        p.apply(config, range, !AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.WALLS));
        config.setChosenPreset(p, range);
        config.save();
        say(ok("preset", Txt.translatable(p.getTranslationKey())));
        return 1;
    }

    private static int showCode() {
        String code = ProfileCode.encode(AudioDistancePlugin.config());
        say(Txt.translatable(KEY + "code.show").withStyle(ChatFormatting.GRAY));
        say(Txt.literal(code).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)
                        .withClickEvent(ChatLink.click("copy_to_clipboard", code))
                        .withHoverEvent(ChatLink.hover(Txt.translatable(KEY + "copy.hover"))))
                .append(" ")
                .append(button(Txt.translatable(KEY + "copy"), code, true)));
        return 1;
    }

    private static int loadCode(String text) {
        for (DistanceConfig.Part part : DistanceConfig.Part.values()) {
            if (AudioDistancePlugin.LINK.isLocked(part)) {
                say(error("locked"));
                return 0;
            }
        }
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        // The chat box may have split a long code; spaces are not part of it
        if (!ProfileCode.decode(text.replace(" ", ""), config)) {
            say(error("code.invalid", ProfileCode.PREFIX));
            return 0;
        }
        config.setChosenPreset(null, 0.0);
        config.save();
        say(ok("code.loaded"));
        return 1;
    }

    private static int reset(String what) {
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        String w = what.toLowerCase(Locale.ROOT);
        List<String> done = new ArrayList<>();
        boolean locked = false;
        for (DistanceConfig.Part part : DistanceConfig.Part.values()) {
            if (w.equals("all") || w.equals(part.getId())) {
                if (AudioDistancePlugin.LINK.isLocked(part)) {
                    locked = true;
                } else {
                    config.resetPart(part);
                    done.add(part.getId());
                }
            }
        }
        if (w.equals("all") || w.equals("hud")) {
            config.resetHud();
            done.add("hud");
        }
        if (done.isEmpty()) {
            say(locked ? error("locked") : error("reset.unknown", what, String.join(", ", RESET)));
            return 0;
        }
        config.setChosenPreset(null, 0.0);
        config.save();
        say(ok("reset", String.join(", ", done)));
        if (locked) {
            say(Txt.translatable(KEY + "locked").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int status() {
        DistanceConfig own = AudioDistancePlugin.CONFIG;
        say(Txt.translatable(KEY + "status.title", BuildInfo.version()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        LinkProtocol.ServerProfile p = AudioDistancePlugin.LINK.profile();
        if (p == null) {
            say(Txt.translatable(KEY + "status.no_server").withStyle(ChatFormatting.GRAY));
        } else {
            say(Txt.translatable(KEY + "status.server",
                    value(Txt.translatable(KEY + "status.mode." + p.mode().getId()))).withStyle(ChatFormatting.GRAY));
            if (p.mode() == ServerSettings.ProfileMode.ENFORCE && !p.locked().isEmpty()) {
                say(Txt.translatable(KEY + "status.locked", value(DistanceConfig.Part.format(p.locked())))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (p.zone() != null && !p.zone().isEmpty()) {
                say(Txt.translatable(KEY + "status.zone", value(p.zone())).withStyle(ChatFormatting.GRAY));
            }
        }
        double range = AudioDistancePlugin.getServerMaxDistance();
        say(Txt.translatable(KEY + "status.range", value(format(range)),
                value(format(range * AudioDistancePlugin.LINK.whisperShare()))).withStyle(ChatFormatting.GRAY));
        Preset preset = own.getChosenPreset();
        say(Txt.translatable(KEY + "status.own",
                value(preset == null ? Txt.translatable(KEY + "status.custom") : Txt.translatable(preset.getTranslationKey())),
                value(Txt.translatable(own.getHudMode().getTranslationKey()))).withStyle(ChatFormatting.GRAY));
        say(Txt.empty()
                .append(button(Txt.translatable(KEY + "button.settings"), "/" + ClientHints.COMMAND, false))
                .append(" ")
                .append(button(Txt.translatable(KEY + "button.help"), "/" + ClientHints.COMMAND + " help", false)));
        return 1;
    }

    /** Versions, what the server sent, the settings in effect and recent problems, with a Copy button. */
    private static int report() {
        List<String> lines = com.kasper.vcdistance.ClientReport.lines(gameVersion(), AudioDistancePlugin.CONFIG,
                AudioDistancePlugin.LINK, AudioDistancePlugin.getServerMaxDistance());
        String text = String.join("\n", lines);
        say(Txt.translatable(KEY + "report.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(" ").append(button(Txt.translatable(KEY + "copy"), text, true)));
        for (String line : lines) {
            say(Txt.literal(line).withStyle(ChatFormatting.GRAY));
        }
        say(Txt.translatable(KEY + "report.hint").withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    /** "1.21.4": the game's own version object, whose accessor is named differently across versions. */
    private static String gameVersion() {
        try {
            Object version = Class.forName("net.minecraft.SharedConstants").getMethod("getCurrentVersion").invoke(null);
            for (String name : new String[]{"name", "getName", "id", "getId"}) {
                try {
                    Object v = version.getClass().getMethod(name).invoke(version);
                    if (v instanceof String text && !text.isEmpty()) {
                        return text;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // the next name
                }
            }
        } catch (Throwable ignored) {
            // unknown
        }
        return "";
    }

    private static int help() {
        say(Txt.translatable(KEY + "help.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        String base = "/" + ClientHints.COMMAND;
        String[][] lines = {
                {"open", base + " "},
                {"preset", base + " preset "},
                {"hud", base + " hud "},
                {"code", base + " code"},
                {"reset", base + " reset "},
                {"status", base + " status"},
                {"log", base + " log"},
                {"report", base + " report"}};
        for (String[] l : lines) {
            String[] parts = Txt.translatable(KEY + "help." + l[0]).getString().split(" - ", 2);
            MutableComponent line = Txt.literal(parts[0]).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)
                    .withClickEvent(ChatLink.click("suggest_command", l[1]))
                    .withHoverEvent(ChatLink.hover(Txt.translatable(KEY + "type.hover"))));
            if (parts.length > 1) {
                line.append(Txt.literal(" - " + parts[1]).withStyle(ChatFormatting.DARK_GRAY));
            }
            say(line);
        }
        return 1;
    }

    // -------------------------------------------------------------------------
    // Text
    // -------------------------------------------------------------------------

    private static void say(Component message) {
        Consumer<Component> chat = ClientHints.chat();
        if (chat != null) {
            chat.accept(message);
        }
    }

    private static MutableComponent ok(String key, Object... args) {
        return Txt.translatable(KEY + key, values(args)).withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent error(String key, Object... args) {
        return Txt.translatable(KEY + key, values(args)).withStyle(ChatFormatting.RED);
    }

    private static Object[] values(Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = value(args[i]);
        }
        return out;
    }

    private static MutableComponent value(Object v) {
        MutableComponent c = v instanceof Component comp ? comp.copy() : Txt.literal(String.valueOf(v));
        return c.withStyle(ChatFormatting.WHITE);
    }

    /** "[label]" that runs {@code command}, or copies it with {@code copy}. */
    private static MutableComponent button(Component label, String action, boolean copy) {
        ClickEvent click = ChatLink.click(copy ? "copy_to_clipboard" : "run_command", action);
        HoverEvent hover = ChatLink.hover(copy ? Txt.translatable(KEY + "copy.hover") : Txt.literal(action));
        Style style = Style.EMPTY.withColor(ChatFormatting.AQUA);
        if (click != null) {
            style = style.withClickEvent(click);
        }
        if (hover != null) {
            style = style.withHoverEvent(hover);
        }
        return Txt.literal("[").append(label).append("]").withStyle(style);
    }

    private static String format(double blocks) {
        return Math.abs(blocks - Math.rint(blocks)) < 0.05 ? String.valueOf(Math.round(blocks))
                : String.format(Locale.ROOT, "%.1f", blocks);
    }
}
