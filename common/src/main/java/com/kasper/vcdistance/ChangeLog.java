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
    @com.github.bsideup.jabel.Desugar
    public record Entry(Instant time, String who, String command, boolean undo) {
    }

    public static final String CONSOLE = "@console";
    private static final String SET = "set";
    private static final String UNDO = "undo";

    private ChangeLog() {
    }

    public static Path fileFor(ServerSettings settings) {
        Path parent = settings.getPath().toAbsolutePath().getParent();
        return parent == null ? java.nio.file.Paths.get(FILE) : parent.resolve(FILE);
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
            Jv.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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

    /** Changes per page of {@code /vcd log} (the Log screen asks for as many as its height holds, see {@link Window}). */
    public static final int PAGE = 10;

    /**
     * What a reader asks for: a page, of everyone's changes or of one player's.
     *
     * @param who a player's name or {@link #CONSOLE}, or {@code null} for everyone
     */
    @com.github.bsideup.jabel.Desugar
    public record View(int page, String who) {
    }

    /**
     * What the Log screen asks for: {@code rows} changes (as many as its height holds) from the {@code from}-th one
     * on, 0 being the newest. Servers before 2.12.1 do not know it; the ones that do say so with {@code logrows} in
     * the Server tab state.
     *
     * @param who as in {@link View}
     */
    @com.github.bsideup.jabel.Desugar
    public record Window(int from, int rows, String who) {
    }

    /** Most changes one {@link Window} gets; the server sends fewer when they would not fit in one message. */
    public static final int MAX_ROWS = 200;

    /**
     * One page of the changes; {@code total} counts every change that matches, not only this page's, and
     * {@code from} is the place of the first one among them (0 = the newest).
     */
    @com.github.bsideup.jabel.Desugar
    public record Page(List<Entry> entries, int page, int pages, int total, String who, int from) {

        /** The same page with only its first {@code n} changes. */
        public Page first(int n) {
            return new Page(Jv.copyOf(entries.subList(0, Math.max(0, Math.min(n, entries.size())))), page, pages, total, who, from);
        }
    }

    /**
     * Reads {@code log [page] [player]}: a number is the page, any other word the player; both may be
     * given in either order ({@code log Steve 2} and {@code log 2 Steve}). The console is "console".
     * The {@code rows=} and {@code from=} words of a {@link Window} are skipped.
     */
    public static View parseView(String[] args) {
        int page = 1;
        String who = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].trim();
            if (a.isEmpty() || windowWord(a) != null) {
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

    /**
     * Reads {@code log rows=<n> [from=<n>] [player]}, the Log screen's form, or returns {@code null} when there is
     * no {@code rows=} (a page of {@code /vcd log}). A number that is not one counts as 0.
     */
    public static Window parseWindow(String[] args) {
        int rows = 0;
        int from = 0;
        boolean window = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i].trim();
            String key = windowWord(a);
            if (key == null) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(a.substring(key.length() + 1));
            } catch (NumberFormatException e) {
                value = 0;
            }
            if (key.equals("rows")) {
                rows = value;
                window = true;
            } else {
                from = value;
            }
        }
        return window ? new Window(from, rows, parseView(args).who()) : null;
    }

    /** "rows" or "from" when {@code word} is {@code rows=...} or {@code from=...}; player names have no '='. */
    private static String windowWord(String word) {
        String lower = word.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("rows=") ? "rows" : lower.startsWith("from=") ? "from" : null;
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
        return new Page(Jv.copyOf(slice), p, pages, matching.size(), view.who() == null ? "" : view.who(), (p - 1) * PAGE);
    }

    /**
     * The changes {@code window} asks for. A start past the end is the last change; {@code page} and
     * {@code pages} count pages of {@code rows}, for a reader that only knows pages.
     */
    public static Page window(List<Entry> all, Window window) {
        List<Entry> matching = filter(all, window.who());
        int rows = Math.max(1, Math.min(MAX_ROWS, window.rows()));
        int from = Math.max(0, Math.min(matching.size() - 1, window.from()));
        List<Entry> slice = matching.subList(Math.min(matching.size(), from), Math.min(matching.size(), from + rows));
        int pages = Math.max(1, (matching.size() + rows - 1) / rows);
        return new Page(Jv.copyOf(slice), Math.min(pages, from / rows + 1), pages, matching.size(),
                window.who() == null ? "" : window.who(), from);
    }

    /** Writes a page as the {@code logpage.*} lines of the admin reply (read back by {@link #readPage}). */
    public static void writePage(Page page, java.util.Map<String, String> out) {
        out.put("logpage.page", String.valueOf(page.page()));
        out.put("logpage.pages", String.valueOf(page.pages()));
        out.put("logpage.total", String.valueOf(page.total()));
        out.put("logpage.who", page.who());
        out.put("logpage.from", String.valueOf(page.from()));
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
        int page = number(state, "logpage.page", 1);
        // Servers before 2.12.1 send pages of ten and no start
        return new Page(entries, page, number(state, "logpage.pages", 1), number(state, "logpage.total", entries.size()),
                state.getProperty("logpage.who", ""), Math.max(0, number(state, "logpage.from", (page - 1) * PAGE)));
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
