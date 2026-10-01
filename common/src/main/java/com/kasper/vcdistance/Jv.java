package com.kasper.vcdistance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The few library calls newer than Java 8 that the shared code uses (immutable collection factories, String
 * and Files helpers). The Minecraft 1.16.5 builds run on Java 8, so the code goes through these instead.
 */
public final class Jv {
    private Jv() {
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(items)));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(items)));
    }

    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (Map.Entry<? extends K, ? extends V> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    public static <T> List<T> copyOf(Collection<? extends T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public static <T> Set<T> copyOfSet(Collection<? extends T> items) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(items));
    }

    public static <K, V> Map<K, V> copyOf(Map<? extends K, ? extends V> items) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public static <T> List<T> toList(Stream<T> stream) {
        return Collections.unmodifiableList(stream.collect(Collectors.toList()));
    }

    public static boolean isBlank(String s) {
        return s.trim().isEmpty();
    }

    public static String strip(String s) {
        return s.trim();
    }

    public static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) <= ' ') {
            i++;
        }
        return s.substring(i);
    }

    public static String repeat(String s, int times) {
        StringBuilder b = new StringBuilder(s.length() * Math.max(times, 0));
        for (int i = 0; i < times; i++) {
            b.append(s);
        }
        return b.toString();
    }

    public static String readString(Path file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file), charset);
    }

    public static Path writeString(Path file, CharSequence text, Charset charset, OpenOption... options) throws IOException {
        return Files.write(file, text.toString().getBytes(charset), options);
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public static String utf8(ByteArrayOutputStream out) {
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static byte[] readNBytes(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while (out.size() < limit && (n = in.read(buffer, 0, Math.min(buffer.length, limit - out.size()))) >= 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** The parameter types of a record class's canonical constructor, or {@code null} when it is not a record (or before Java 16). */
    public static Class<?>[] recordTypes(Class<?> type) {
        try {
            if (!(Boolean) Class.class.getMethod("isRecord").invoke(type)) {
                return null;
            }
            Object[] parts = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            Class<?>[] types = new Class<?>[parts.length];
            for (int i = 0; i < parts.length; i++) {
                types[i] = (Class<?>) parts[i].getClass().getMethod("getType").invoke(parts[i]);
            }
            return types;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
