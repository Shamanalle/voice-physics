package com.kasper.vcdistance;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player chose for themselves with {@code /voice}: how loud they talk (quiet, normal,
 * shout), whether walls muffle what they hear, how loud each other player is to them, and whether
 * the "who is talking" line shows above their hotbar. Read from Simple Voice Chat's thread for every
 * voice packet, so a player's entry is an immutable record swapped in whole.
 * <p>
 * Kept in {@code vc-audio-distance-players.properties} next to the server settings: one line per
 * player, {@code <uuid>=mode:shout;walls:off;hud:on;vol:<uuid>@50,<uuid>@0}. A volume of 0 means the
 * player is ignored.
 */
public final class PlayerPrefs {

    public static final String FILE = "vc-audio-distance-players.properties";

    /** How loud a player talks, as a share of the normal range. */
    public enum Mode {
        QUIET, NORMAL, SHOUT;

        public static Mode of(String id) {
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(id)) {
                    return m;
                }
            }
            return NORMAL;
        }
    }

    /** Range with {@link Mode#QUIET}, times the normal range. */
    public static final double QUIET_FACTOR = 0.5;
    /** Range with {@link Mode#SHOUT}, times the normal range. */
    public static final double SHOUT_FACTOR = 2.0;

    /** One player's choices; volumes are in percent, 0-99 (100 is the default and not stored). */
    public record Prefs(Mode mode, boolean walls, boolean hud, Map<UUID, Integer> volumes) {

        static final Prefs DEFAULT = new Prefs(Mode.NORMAL, true, false, Map.of());

        boolean isDefault() {
            return mode == Mode.NORMAL && walls && !hud && volumes.isEmpty();
        }

        Prefs withMode(Mode m) {
            return new Prefs(m, walls, hud, volumes);
        }

        Prefs withWalls(boolean on) {
            return new Prefs(mode, on, hud, volumes);
        }

        Prefs withHud(boolean on) {
            return new Prefs(mode, walls, on, volumes);
        }

        Prefs withVolume(UUID other, int percent) {
            Map<UUID, Integer> next = new HashMap<>(volumes);
            if (percent >= 100) {
                next.remove(other);
            } else {
                next.put(other, Math.max(0, percent));
            }
            return new Prefs(mode, walls, hud, Map.copyOf(next));
        }
    }

    private final Map<UUID, Prefs> players = new ConcurrentHashMap<>();
    /** Whether any player has a choice that changes what the voice rules do (range mode or a volume). */
    private volatile boolean active;
    private volatile Path file;

    public Prefs get(UUID player) {
        Prefs p = player == null ? null : players.get(player);
        return p == null ? Prefs.DEFAULT : p;
    }

    /** Whether any choice needs the voice packets looked at (a range mode, a volume, an ignored player). */
    public boolean anyRules() {
        return active;
    }

    /** Factor on {@code speaker}'s voice range from their mode; 1 when they did not choose one. */
    public double rangeFactor(UUID speaker) {
        return switch (get(speaker).mode()) {
            case QUIET -> QUIET_FACTOR;
            case SHOUT -> SHOUT_FACTOR;
            default -> 1.0;
        };
    }

    /** Whether {@code listener} muffles voices behind walls (true unless they turned it off). */
    public boolean wallsFor(UUID listener) {
        return get(listener).walls();
    }

    public boolean hudFor(UUID player) {
        return get(player).hud();
    }

    /** Percent {@code listener} hears {@code speaker} at: 100 unless they set it, 0 = ignored. */
    public int volume(UUID listener, UUID speaker) {
        Integer v = speaker == null ? null : get(listener).volumes().get(speaker);
        return v == null ? 100 : v;
    }

    /** Quiet-down in dB for {@link VoiceFilter}'s loss: 0 at 100 %, 6 dB at 50 %. */
    public double lossDb(UUID listener, UUID speaker) {
        int v = volume(listener, speaker);
        if (v >= 100) {
            return 0.0;
        }
        return v <= 0 ? 60.0 : -20.0 * Math.log10(v / 100.0);
    }

    public void setMode(UUID player, Mode mode) {
        update(player, p -> p.withMode(mode));
    }

    public void setWalls(UUID player, boolean on) {
        update(player, p -> p.withWalls(on));
    }

    public void setHud(UUID player, boolean on) {
        update(player, p -> p.withHud(on));
    }

    public void setVolume(UUID listener, UUID speaker, int percent) {
        update(listener, p -> p.withVolume(speaker, percent));
    }

    public void reset(UUID player) {
        update(player, p -> Prefs.DEFAULT);
    }

    /** A player left for good: nothing to forget, choices are kept. Others' volumes for them stay too. */
    private void update(UUID player, java.util.function.UnaryOperator<Prefs> change) {
        players.compute(player, (id, old) -> {
            Prefs next = change.apply(old == null ? Prefs.DEFAULT : old);
            return next.isDefault() ? null : next;
        });
        recount();
        save();
    }

    private void recount() {
        boolean any = false;
        for (Prefs p : players.values()) {
            if (p.mode() != Mode.NORMAL || !p.volumes().isEmpty()) {
                any = true;
                break;
            }
        }
        active = any;
    }

    // -------------------------------------------------------------------------
    // File
    // -------------------------------------------------------------------------

    /** Reads the file (if it exists) and keeps writing to it from now on. */
    public void load(Path path) {
        file = path;
        players.clear();
        if (java.nio.file.Files.isRegularFile(path)) {
            try {
                Properties props = ConfigWriter.load(path);
                for (String key : props.stringPropertyNames()) {
                    try {
                        players.put(UUID.fromString(key.trim()), decode(props.getProperty(key)));
                    } catch (IllegalArgumentException e) {
                        DistanceConfig.LOGGER.warn("Ignoring a line in {}: {}", path.getFileName(), key);
                    }
                }
            } catch (java.io.IOException e) {
                DistanceConfig.LOGGER.warn("Could not read {}: {}", path.getFileName(), e.getMessage());
            }
        }
        recount();
    }

    private synchronized void save() {
        Path path = file;
        if (path == null) {
            return;
        }
        ConfigWriter w = new ConfigWriter().title(
                "Voice Physics: what each player chose with /voice. Edited by the server; safe to delete.",
                "Voice Physics: что каждый игрок выбрал командой /voice. Меняется сервером; файл можно удалить.");
        players.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> w.value(e.getKey().toString(), encode(e.getValue())));
        w.save(path);
    }

    static String encode(Prefs p) {
        StringBuilder out = new StringBuilder("mode:").append(p.mode().name().toLowerCase(java.util.Locale.ROOT))
                .append(";walls:").append(p.walls() ? "on" : "off")
                .append(";hud:").append(p.hud() ? "on" : "off");
        if (!p.volumes().isEmpty()) {
            out.append(";vol:");
            boolean first = true;
            for (Map.Entry<UUID, Integer> e : new java.util.TreeMap<>(p.volumes()).entrySet()) {
                out.append(first ? "" : ",").append(e.getKey()).append('@').append(e.getValue());
                first = false;
            }
        }
        return out.toString();
    }

    static Prefs decode(String text) {
        Prefs p = Prefs.DEFAULT;
        for (String part : text.split(";")) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = part.substring(0, colon).trim();
            String value = part.substring(colon + 1).trim();
            switch (key) {
                case "mode" -> p = p.withMode(Mode.of(value));
                case "walls" -> p = p.withWalls(!value.equals("off"));
                case "hud" -> p = p.withHud(value.equals("on"));
                case "vol" -> {
                    for (String item : value.split(",")) {
                        int at = item.indexOf('@');
                        if (at < 0) {
                            continue;
                        }
                        try {
                            p = p.withVolume(UUID.fromString(item.substring(0, at).trim()),
                                    Integer.parseInt(item.substring(at + 1).trim()));
                        } catch (IllegalArgumentException ignored) {
                            // a damaged entry: the rest still loads
                        }
                    }
                }
                default -> {
                }
            }
        }
        return p;
    }
}
