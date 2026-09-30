package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The little JSON the data files need, without a library: objects (in their order), arrays, strings,
 * numbers, {@code true}, {@code false} and {@code null}. Throws {@link IllegalArgumentException} with the
 * place of the mistake.
 */
final class MiniJson {

    private final String text;
    private int at;

    private MiniJson(String text) {
        this.text = text;
    }

    /** @return a {@code Map<String, Object>}, {@code List<Object>}, {@code String}, {@code Double}, {@code Boolean} or {@code null} */
    static Object parse(String text) {
        MiniJson json = new MiniJson(text == null ? "" : text);
        json.space();
        Object value = json.value(0);
        json.space();
        if (json.at < json.text.length()) {
            throw json.error("text after the end");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > 32) {
            throw error("nested too deep");
        }
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", null);
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield number();
                }
                throw error("unexpected '" + c + "'");
            }
        };
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        at++;
        space();
        if (peek('}')) {
            at++;
            return out;
        }
        while (true) {
            space();
            if (!peek('"')) {
                throw error("expected a name in quotes");
            }
            String key = string();
            space();
            expect(':');
            space();
            out.put(key, value(depth + 1));
            space();
            if (peek(',')) {
                at++;
                continue;
            }
            expect('}');
            return out;
        }
    }

    private List<Object> array(int depth) {
        List<Object> out = new ArrayList<>();
        at++;
        space();
        if (peek(']')) {
            at++;
            return out;
        }
        while (true) {
            space();
            out.add(value(depth + 1));
            space();
            if (peek(',')) {
                at++;
                continue;
            }
            expect(']');
            return out;
        }
    }

    private String string() {
        at++;
        StringBuilder sb = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (at >= text.length()) {
                break;
            }
            char e = text.charAt(at++);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("broken \\u escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("broken \\u escape");
                    }
                    at += 4;
                }
                default -> sb.append(e);
            }
        }
        throw error("a string is not closed");
    }

    private Double number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        try {
            return Double.valueOf(text.substring(start, at));
        } catch (NumberFormatException e) {
            at = start;
            throw error("broken number");
        }
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("unexpected '" + text.charAt(at) + "'");
        }
        at += word.length();
        return value;
    }

    private void space() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
                at++;
            } else {
                return;
            }
        }
    }

    private boolean peek(char c) {
        return at < text.length() && text.charAt(at) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error(at >= text.length() ? "unexpected end, expected '" + c + "'" : "expected '" + c + "'");
        }
        at++;
    }

    private IllegalArgumentException error(String what) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(at, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new IllegalArgumentException(what + " at line " + line + ", column " + column);
    }
}
