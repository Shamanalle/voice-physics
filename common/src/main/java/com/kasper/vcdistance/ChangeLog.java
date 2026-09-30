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
            Problems.record("Writing " + file.getFileName() + ": " + e.getMessage());
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

    /** Changes per page, in {@code /vcd log} and on the Log screen alike. */
    public static final int PAGE = 10;

    /**
     * What a reader asks for: a page, of everyone's changes or of one player's.
     *
     * @param who a player's name or {@link #CONSOLE}, or {@code null} for everyone
     */
    public record View(int page, String who) {
    }

    /** One page of the changes; {@code total} counts every change that matches, not only this page's. */
    public record Page(List<Entry> entries, int page, int pages, int total, String who) {
    }

    /**
     * Reads {@code log [page] [player]}: a number is the page, any other word the player; both may be
     * given in either order ({@code log Steve 2} and {@code log 2 Steve}). The console is "console".
     */
    public static View parseView(String[] args) {
        int page = 1;
        String who = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].trim();
            if (a.isEmpty()) {
                continue;
            }
            try {
                page = Integer.parseInt(a);
            } catch (NumberFormatException e) {
                who = a.equalsIgnoreCase("console") || a.equalsIgnoreCase("@console") ? CONSOLE : a;
            }
        }
        return new View(page, who);
    }

    /** The changes {@code who} made (names compare without regard to case); everything when {@code who} is null or empty. */
    public static List<Entry> filter(List<Entry> all, String who) {
        if (who == null || who.isEmpty()) {
            return all;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (e.who().equalsIgnoreCase(who)) {
                out.add(e);
            }
        }
        return out;
    }

    /** The page {@code view} asks for, from the newest-first list {@link #read} returns; a page out of range is clamped. */
    public static Page page(List<Entry> all, View view) {
        List<Entry> matching = filter(all, view.who());
        int pages = Math.max(1, (matching.size() + PAGE - 1) / PAGE);
        int p = Math.max(1, Math.min(pages, view.page()));
        List<Entry> slice = matching.subList(Math.min(matching.size(), (p - 1) * PAGE), Math.min(matching.size(), p * PAGE));
        return new Page(List.copyOf(slice), p, pages, matching.size(), view.who() == null ? "" : view.who());
    }

    /** Writes a page as the {@code logpage.*} lines of the admin reply (read back by {@link #readPage}). */
    public static void writePage(Page page, java.util.Map<String, String> out) {
        out.put("logpage.page", String.valueOf(page.page()));
        out.put("logpage.pages", String.valueOf(page.pages()));
        out.put("logpage.total", String.valueOf(page.total()));
        out.put("logpage.who", page.who());
        for (int i = 0; i < page.entries().size(); i++) {
            Entry e = page.entries().get(i);
            out.put("logpage." + i, e.time() + "|" + e.who() + "|" + e.undo() + "|" + e.command());
        }
    }

    /** Reads a page from an admin reply's state, or {@code null} when the reply carries none. */
    public static Page readPage(java.util.Properties state) {
        if (state == null || state.getProperty("logpage.page") == null) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; state.getProperty("logpage." + i) != null; i++) {
            String[] parts = state.getProperty("logpage." + i).split("\\|", 4);
            if (parts.length < 4) {
                continue;
            }
            try {
                entries.add(new Entry(Instant.parse(parts[0]), parts[1], parts[3], Boolean.parseBoolean(parts[2])));
            } catch (RuntimeException ignored) {
                // A line that did not arrive whole
            }
        }
        return new Page(entries, number(state, "logpage.page", 1), number(state, "logpage.pages", 1),
                number(state, "logpage.total", entries.size()), state.getProperty("logpage.who", ""));
    }

    private static int number(java.util.Properties state, String key, int fallback) {
        try {
            return Integer.parseInt(state.getProperty(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Path old(Path file) {
        return file.resolveSibling(file.getFileName() + ".old");
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
