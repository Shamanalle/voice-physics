package com.kasper.vcdistance;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last few things that went wrong on this server (a voice that could not be re-encoded, a settings
 * file that could not be read), for {@code /vcd report}. The same problem again only counts up, so one
 * noisy failure does not push the others out.
 */
public final class Problems {

    /** How many different problems are kept. */
    static final int KEEP = 8;

    /**
     * @param last  when it last happened
     * @param what  where and what, "Server walls: IllegalStateException: boom"
     * @param count how often since the start
     */
    @com.github.bsideup.jabel.Desugar
    public record Problem(Instant last, String what, int count) {
    }

    private static final Deque<Problem> RECENT = new ArrayDeque<>();

    private Problems() {
    }

    public static void record(String where, Throwable t) {
        record(where + ": " + (t == null ? "?" : t.toString()));
    }

    public static synchronized void record(String what) {
        String text = what.length() > 300 ? what.substring(0, 300) + "…" : what;
        int count = 1;
        for (Problem p : RECENT) {
            if (p.what().equals(text)) {
                count = p.count() + 1;
                RECENT.remove(p);
                break;
            }
        }
        RECENT.addFirst(new Problem(Instant.now(), text, count));
        while (RECENT.size() > KEEP) {
            RECENT.pollLast();
        }
    }

    /** Newest first. */
    public static synchronized List<Problem> recent() {
        return new ArrayList<>(RECENT);
    }

    public static synchronized void clear() {
        RECENT.clear();
    }
}
