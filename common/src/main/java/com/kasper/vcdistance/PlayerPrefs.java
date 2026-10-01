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
 * player, {@code <uuid>=mode:shout;walls:off;hud:on;radio:1200;vol:<uuid>@50,<uuid>@0}. A volume of 0
 * means the player is ignored; a radio frequency of 0 (not stored) means the radio is off.
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

    /** Lowest and highest radio frequency; 0 is "off". */
    public static final int RADIO_MIN = 1;
    public static final int RADIO_MAX = 9999;

    /**
     * One player's choices; volumes are in percent, 0-99 (100 is the default and not stored); the radio
     * is a frequency {@link #RADIO_MIN}-{@link #RADIO_MAX}, or 0 when it is off.
     */
    @com.github.bsideup.jabel.Desugar
    public record Prefs(Mode mode, boolean walls, boolean hud, Map<UUID, Integer> volumes, int radio) {

        public static final Prefs DEFAULT = new Prefs(Mode.NORMAL, true, false, Jv.mapOf(), 0);

        public boolean isDefault() {
            return mode == Mode.NORMAL && walls && !hud && volumes.isEmpty() && radio == 0;
        }

        Prefs withMode(Mode m) {
            return new Prefs(m, walls, hud, volumes, radio);
        }

        Prefs withWalls(boolean on) {
            return new Prefs(mode, on, hud, volumes, radio);
        }

        Prefs withHud(boolean on) {
            return new Prefs(mode, walls, on, volumes, radio);
        }

        Prefs withRadio(int frequency) {
            return new Prefs(mode, walls, hud, volumes, frequency < RADIO_MIN || frequency > RADIO_MAX ? 0 : frequency);
        }

        Prefs withVolume(UUID other, int percent) {
            Map<UUID, Integer> next = new HashMap<>(volumes);
            if (percent >= 100) {
                next.remove(other);
            } else {
                next.put(other, Math.max(0, percent));
            }
            return new Prefs(mode, walls, hud, Jv.copyOf(next), radio);
        }
    }

    private final Map<UUID, Prefs> players = new ConcurrentHashMap<>();
    /** Each player's choices before their last change, for {@link #undo} (kept while the server runs). */
    private final Map<UUID, Prefs> before = new ConcurrentHashMap<>();
    /** Whether any player has a choice that changes what the voice rules do (range mode or a volume). */
    private volatile boolean active;
    /** How many players have a radio frequency (the radio looks at voice packets only while someone has). */
    private volatile int radioUsers;
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

    /** Whether any player tuned a radio, so voice packets are worth a look. */
    public boolean anyRadio() {
        return radioUsers > 0;
    }

    /** The player's radio frequency, or 0 when it is off. */
    public int radioOf(UUID player) {
        return get(player).radio();
    }

    /** Players per radio frequency, lowest first. */
    public java.util.SortedMap<Integer, Integer> radioChannels() {
        java.util.SortedMap<Integer, Integer> out = new java.util.TreeMap<>();
        for (Prefs p : players.values()) {
            if (p.radio() > 0) {
                out.merge(p.radio(), 1, Integer::sum);
            }
        }
        return out;
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

    /** Tunes the radio to {@code frequency} ({@link #RADIO_MIN}-{@link #RADIO_MAX}); anything else turns it off. */
    public void setRadio(UUID player, int frequency) {
        update(player, p -> p.withRadio(frequency));
    }

    public void setVolume(UUID listener, UUID speaker, int percent) {
        update(listener, p -> p.withVolume(speaker, percent));
    }

    public void reset(UUID player) {
        update(player, p -> Prefs.DEFAULT);
    }

    /** Whether the player has a change to take back (or, right after taking one back, to put back). */
    public boolean canUndo(UUID player) {
        return player != null && before.containsKey(player);
    }

    /**
     * Takes back the player's last change; asking again puts it back.
     *
     * @return {@code false} when there is nothing to take back
     */
    public boolean undo(UUID player) {
        Prefs previous = player == null ? null : before.get(player);
        if (previous == null) {
            return false;
        }
        update(player, p -> previous);
        return true;
    }

    /** The player left the game: their undo is forgotten. Their choices and others' volumes for them are kept. */
    public void forget(UUID player) {
        before.remove(player);
    }

    private void update(UUID player, java.util.function.UnaryOperator<Prefs> change) {
        Prefs[] old = new Prefs[1];
        players.compute(player, (id, current) -> {
            old[0] = current == null ? Prefs.DEFAULT : current;
            Prefs next = change.apply(old[0]);
            return next.isDefault() ? null : next;
        });
        if (!old[0].equals(get(player))) {
            before.put(player, old[0]);
        }
        recount();
        save();
    }

    private void recount() {
        boolean any = false;
        int radios = 0;
        for (Prefs p : players.values()) {
            if (p.mode() != Mode.NORMAL || !p.volumes().isEmpty()) {
                any = true;
            }
            if (p.radio() > 0) {
                radios++;
            }
        }
        active = any;
        radioUsers = radios;
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
        if (p.radio() > 0) {
            out.append(";radio:").append(p.radio());
        }
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
                case "radio" -> {
                    try {
                        p = p.withRadio(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        // a damaged entry: the radio stays off
                    }
                }
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
