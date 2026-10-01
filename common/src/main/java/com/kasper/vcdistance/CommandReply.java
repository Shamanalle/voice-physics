package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command's answer as chat lines made of spans: text with a role (a value, an error, a button...)
 * and, for buttons and values, what a click does and what hovering shows. Each platform turns the
 * spans into its own chat text (colours, click and hover events); the console, the Server tab and the
 * tests read the plain text.
 */
public final class CommandReply {

    /** What a span is, which decides its colour. */
    public enum Style {
        /** Ordinary text. */
        PLAIN,
        /** The first line of a longer answer. */
        TITLE,
        /** A name in front of a value ("Walls:"). */
        LABEL,
        /** A value ("60%"). */
        VALUE,
        /** Something done ("Saved"). */
        OK,
        /** Something to look at ("not running yet"). */
        WARN,
        /** A command that did not work. */
        ERROR,
        /** Secondary text: hints, syntax. */
        MUTED,
        /** A clickable [button]. */
        BUTTON,
        /** A button that deletes something. */
        DANGER
    }

    /** What clicking a span does. */
    public enum Click {
        /** Runs the command. */
        RUN,
        /** Puts the command in the chat box to finish typing. */
        SUGGEST,
        /** Copies the text. */
        COPY
    }

    /**
     * One piece of a line.
     *
     * @param click  what a click does, or {@code null}
     * @param action the command (with its slash) or the text to copy
     * @param hover  shown when the mouse is over the span, or {@code null}
     */
    @com.github.bsideup.jabel.Desugar
    public record Span(String text, Style style, Click click, String action, String hover) {

        public Span {
            text = text == null ? "" : text;
            style = style == null ? Style.PLAIN : style;
        }

        public Span(String text, Style style) {
            this(text, style, null, null, null);
        }

        public boolean isButton() {
            return style == Style.BUTTON || style == Style.DANGER;
        }

        public Span withHover(String text) {
            return new Span(this.text, style, click, action, text);
        }

        public Span withClick(Click click, String action) {
            return new Span(text, style, click, action, hover);
        }
    }

    /** One chat line. */
    @com.github.bsideup.jabel.Desugar
    public record Line(List<Span> spans) {

        public Line {
            spans = Jv.copyOf(spans);
        }

        /** The text as typed out, buttons included. */
        public String plain() {
            StringBuilder b = new StringBuilder();
            for (Span s : spans) {
                b.append(s.text());
            }
            return b.toString();
        }

        /** The text without its buttons, for places that cannot click (the Server tab's footer). */
        public String text() {
            StringBuilder b = new StringBuilder();
            for (Span s : spans) {
                if (!s.isButton()) {
                    b.append(s.text());
                }
            }
            return Jv.strip(b.toString());
        }
    }

    /** Builds one line. */
    public static final class LineBuilder {

        private final List<Span> spans = new ArrayList<>();

        public LineBuilder add(Span span) {
            if (span != null && !span.text().isEmpty()) {
                spans.add(span);
            }
            return this;
        }

        public LineBuilder addAll(List<Span> more) {
            for (Span s : more) {
                add(s);
            }
            return this;
        }

        public LineBuilder text(String text, Style style) {
            return add(new Span(text, style));
        }

        public LineBuilder text(String text) {
            return text(text, Style.PLAIN);
        }

        /** A space and then a [button] that runs or suggests {@code command}. */
        public LineBuilder button(String label, Click click, String command, String hover) {
            if (!spans.isEmpty()) {
                spans.add(new Span(" ", Style.PLAIN));
            }
            return add(new Span("[" + label + "]", Style.BUTTON, click, command, hover));
        }

        public LineBuilder danger(String label, String command, String hover) {
            if (!spans.isEmpty()) {
                spans.add(new Span(" ", Style.PLAIN));
            }
            return add(new Span("[" + label + "]", Style.DANGER, Click.RUN, command, hover));
        }

        public boolean isEmpty() {
            return spans.isEmpty();
        }

        public Line build() {
            return new Line(spans);
        }
    }

    private final List<Line> lines = new ArrayList<>();

    public static LineBuilder line() {
        return new LineBuilder();
    }

    public CommandReply add(Line line) {
        lines.add(line);
        return this;
    }

    public CommandReply add(LineBuilder line) {
        return add(line.build());
    }

    /** A line of plain text in one style. */
    public CommandReply add(String text, Style style) {
        return add(line().text(text, style));
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Every line as typed out, buttons included. */
    public List<String> plain() {
        List<String> out = new ArrayList<>(lines.size());
        for (Line l : lines) {
            out.add(l.plain());
        }
        return out;
    }

    /** Every line without its buttons (lines that were only buttons are left out). */
    public List<String> text() {
        List<String> out = new ArrayList<>(lines.size());
        for (Line l : lines) {
            String t = l.text();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static final Pattern SLOT = Pattern.compile("%(?:(\\d+)\\$)?([sd%])");

    /**
     * A translated template with its {@code %s} slots filled: the template's own text gets
     * {@code style}, each argument keeps its span (a plain object becomes a {@link Style#VALUE}).
     * Numbered slots ({@code %2$s}) and {@code %%} work as in {@link String#format}.
     */
    public static List<Span> fill(String template, Style style, Object... args) {
        List<Span> out = new ArrayList<>();
        Matcher m = SLOT.matcher(template);
        int last = 0;
        int next = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.add(new Span(template.substring(last, m.start()), style));
            }
            last = m.end();
            if (m.group(2).equals("%")) {
                out.add(new Span("%", style));
                continue;
            }
            int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
            Object arg = index >= 0 && index < args.length ? args[index] : null;
            if (arg instanceof Span s) {
                out.add(s);
            } else if (arg != null) {
                out.add(new Span(String.valueOf(arg), Style.VALUE));
            }
        }
        if (last < template.length()) {
            out.add(new Span(template.substring(last), style));
        }
        return out;
    }
}
