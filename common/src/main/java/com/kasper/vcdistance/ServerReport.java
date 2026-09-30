package com.kasper.vcdistance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What {@code /vcd report} copies: everything a bug report about the server side needs, in one short
 * English text (versions, the settings that change how voices sound, load, and the last problems).
 * Player names and zone names are left out; only counts are given.
 */
public final class ServerReport {

    private ServerReport() {
    }

    public static List<String> lines(ServerSettings s, AdminCommands.Context ctx) {
        List<String> out = new ArrayList<>();
        DistanceConfig p = s.profile();
        out.add("Voice Physics " + BuildInfo.version() + " on " + ctx.platform()
                + (ctx.serverVersion().isEmpty() ? "" : " (" + ctx.serverVersion() + ")"));
        out.add("Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        double voice = AudioDistancePlugin.serverVoiceDistance();
        out.add(voice > 0.0
                ? "Simple Voice Chat: voice " + AdminCommands.fmt(voice) + ", whisper " + AdminCommands.fmt(AudioDistancePlugin.serverWhisperDistance())
                : "Simple Voice Chat: not connected");
        out.add("Players: " + ctx.onlinePlayers() + " online, " + ctx.addonPlayers() + " with the addon");
        out.add("Walls: " + (p.isOcclusionEnabled() ? AdminCommands.pct(p.getOcclusionStrength()) : "off")
                + ", server_walls " + onOff(s.isServerWalls()) + ", streams " + AudioDistancePlugin.SERVER_WALLS.activeStreams()
                + "/" + s.getMaxStreams() + ", block rules " + s.getBlockRules().rules().size());
        out.add("Effects: server_effects " + onOff(s.isServerEffects()) + ", server_air " + onOff(s.isServerAir())
                + ", water " + strength(p.isUnderwaterEnabled(), p.getUnderwaterStrength())
                + ", weather " + strength(p.isWeatherEnabled(), p.getWeatherStrength())
                + ", echo " + strength(p.isReverbEnabled(), p.getReverbStrength()));
        out.add("Profile: " + s.getProfileMode().getId() + ", preset " + s.getProfilePreset() + ", model " + p.getModel().getId()
                + ", locked " + DistanceConfig.Part.format(s.getLockedParts()) + ", allow_monitor " + onOff(s.isMonitorAllowed()));
        out.add("Rules: sneak " + AdminCommands.pct(s.getSneakMultiplier()) + ", dead_silent " + onOff(s.isDeadSilent())
                + ", spectators_apart " + onOff(s.isSpectatorsOnly()) + ", megaphone "
                + (s.getMegaphoneItem().isEmpty() ? "off" : s.getMegaphoneItem() + " x" + AdminCommands.fmt(s.getMegaphoneMultiplier())));
        out.add("Groups: dead " + onOff(s.isGroupDeadSilent()) + ", spectators " + onOff(s.isGroupSpectatorsApart())
                + ", zones " + onOff(s.isGroupIsolatedZones()) + ", open_range " + onOff(s.isOpenGroupRange()));
        Map<String, Integer> kinds = new TreeMap<>();
        for (Zone z : s.zones().values()) {
            kinds.merge(z.kind(), 1, Integer::sum);
        }
        out.add("Zones: " + s.zones().size() + (kinds.isEmpty() ? "" : " " + kinds) + ", notices " + onOff(s.isZoneNotices())
                + ", mutes " + s.mutes(System.currentTimeMillis()).size());
        out.add("Require addon: " + s.getRequireAddon().getId() + (s.getMinAddonVersion().isEmpty() ? "" : " " + s.getMinAddonVersion())
                + ", messages " + s.getMessagesLanguage());
        out.add(String.format(Locale.ROOT, "Load: %.2f ms per tick", AudioDistancePlugin.SERVER_WALLS.perf().averageMs()));
        List<Problems.Problem> problems = Problems.recent();
        if (problems.isEmpty()) {
            out.add("Problems: none since the start");
        } else {
            out.add("Problems (newest first):");
            for (Problems.Problem pr : problems) {
                out.add("  " + pr.last().truncatedTo(ChronoUnit.SECONDS) + (pr.count() > 1 ? " x" + pr.count() : "") + " " + pr.what());
            }
        }
        out.add("Made " + Instant.now().truncatedTo(ChronoUnit.SECONDS));
        return out;
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    private static String strength(boolean enabled, double value) {
        return enabled ? AdminCommands.pct(value) : "off";
    }
}
