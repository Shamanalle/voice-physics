package com.kasper.vcdistance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Who changed the server's settings with {@code /vcd} (or the Server tab), when, and how: one line
 * per saved change in {@link #FILE} next to the settings file. At {@link #MAX_BYTES} the file is
 * moved to {@code .old} and a new one begins, so the log never grows without end.
 */
public final class ChangeLog {

    public static final String FILE = "vc-audio-distance-changes.log";
    static final long MAX_BYTES = 1_000_000L;

    /**
     * One change.
     *
     * @param who     the player's name, or {@link #CONSOLE}
     * @param command what was typed, "/vcd walls 60"
     * @param undo    the change took back {@code command}
     */
    public record Entry(Instant time, String who, String command, boolean undo) {
    }

    public static final String CONSOLE = "@console";
    private static final String SET = "set";
    private static final String UNDO = "undo";

    private ChangeLog() {
    }

    public static Path fileFor(ServerSettings settings) {
        Path parent = settings.getPath().toAbsolutePath().getParent();
        return parent == null ? Path.of(FILE) : parent.resolve(FILE);
    }

    /** Adds a change; a log that cannot be written never stops the command. */
    public static synchronized void append(ServerSettings settings, Entry entry) {
        Path file = fileFor(settings);
        String line = entry.time() + "\t" + clean(entry.who()) + "\t" + (entry.undo() ? UNDO : SET) + "\t"
                + clean(entry.command()) + "\n";
        try {
            if (Files.isRegularFile(file) && Files.size(file) >= MAX_BYTES) {
                Files.move(file, old(file), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            DistanceConfig.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
        }
    }

    /** The changes, newest first (the current file, then the one before). */
    public static synchronized List<Entry> read(ServerSettings settings) {
        Path file = fileFor(settings);
        List<Entry> out = new ArrayList<>();
        readInto(old(file), out);
        readInto(file, out);
        Collections.reverse(out);
        return out;
    }

    private static void readInto(Path file, List<Entry> out) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 4);
                if (parts.length < 4) {
                    continue;
                }
                try {
                    out.add(new Entry(Instant.parse(parts[0]), parts[1], parts[3], UNDO.equals(parts[2])));
                } catch (RuntimeException ignored) {
                    // A line someone edited by hand
                }
            }
        } catch (IOException e) {
            DistanceConfig.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
        }
    }

    private static Path old(Path file) {
        return file.resolveSibling(file.getFileName() + ".old");
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
