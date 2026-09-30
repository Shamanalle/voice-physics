package com.kasper.vcdistance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What {@code /voicephysics report} copies: the player's side of a bug report in one short English text
 * (versions, what the server sent, the settings in effect, load, the last problems). No names.
 */
public final class ClientReport {

    private ClientReport() {
    }

    /**
     * @param game  the game version, or "" when unknown
     * @param range the voice range in effect, in blocks
     */
    public static List<String> lines(String game, DistanceConfig own, ServerLink link, double range) {
        List<String> out = new ArrayList<>();
        out.add("Voice Physics " + BuildInfo.version() + (game == null || game.isEmpty() ? "" : " on Minecraft " + game));
        out.add("Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + (ModEnvironment.isSoundPhysicsPresent() ? ", Sound Physics Remastered installed" : ""));
        LinkProtocol.ServerProfile p = link.profile();
        if (p == null) {
            out.add("Server: no Voice Physics on the server (or not connected)");
        } else {
            out.add("Server: profile " + p.mode().getId() + (p.mode() == ServerSettings.ProfileMode.ENFORCE
                    ? ", locked " + DistanceConfig.Part.format(p.locked()) : "")
                    + ", server_walls " + onOff(p.serverWalls()) + ", monitor " + onOff(p.monitor())
                    + ", zone " + (p.zone() == null || p.zone().isEmpty() ? "none" : "yes")
                    + (p.echo() == null ? "" : ", zone echo " + ConfigWriter.number(p.echo()))
                    + ", admin " + onOff(p.admin()));
        }
        out.add(String.format(Locale.ROOT, "Range: voice %s, whisper %s", AdminCommands.fmt(range),
                AdminCommands.fmt(range * link.whisperShare())));
        Preset preset = own.getChosenPreset();
        out.add("Own: preset " + (preset == null ? "custom" : preset.getId()) + ", " + describe(own)
                + ", HUD " + own.getHudMode().getId() + (own.isHudCompact() ? " compact" : ""));
        DistanceConfig used = link.effective(own);
        if (used != own) {
            out.add("In effect (server): " + describe(used));
        }
        if (BlockDataRules.assets().files() > 0 || !BlockDataRules.assets().problems().isEmpty()) {
            out.add("Block materials from resource packs: " + BlockDataRules.assets().summary());
        }
        if (BlockDataRules.data().files() > 0) {
            out.add("Block materials from data packs: " + BlockDataRules.data().summary());
        }
        PlaceTuning.Tuning place = AudioDistancePlugin.ENVIRONMENT.place();
        if (!place.isNone()) {
            out.add(String.format(Locale.ROOT, "Place: %s (echo x%.2f, air %.2f)", place.where(), place.echo(), place.air()));
        }
        int slowdown = AudioDistancePlugin.CLIENT_PERF.slowdown();
        out.add(String.format(Locale.ROOT, "Load: %.2f ms per tick", AudioDistancePlugin.CLIENT_PERF.averageMs())
                + (slowdown > 1 ? ", work spread over " + slowdown + "x the time" : ""));
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

    /** The settings that change how voices sound, in one line. */
    static String describe(DistanceConfig c) {
        return "model " + c.getModel().getId()
                + ", walls " + (c.isOcclusionEnabled() ? AdminCommands.pct(c.getOcclusionStrength()) : "off")
                + ", corners " + onOff(c.isDiffractionEnabled())
                + ", echo " + (c.isReverbEnabled() ? AdminCommands.pct(c.getReverbStrength()) : "off")
                + ", water " + (c.isUnderwaterEnabled() ? AdminCommands.pct(c.getUnderwaterStrength()) : "off")
                + ", weather " + (c.isWeatherEnabled() ? AdminCommands.pct(c.getWeatherStrength()) : "off")
                + ", place " + onOff(c.isPlaceTuning())
                + ", block rules " + c.getBlockRules().rules().size();
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }
}
