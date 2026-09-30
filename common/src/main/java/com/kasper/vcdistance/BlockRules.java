package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Which acoustic material a block counts as, when the automatic guess is wrong or the block comes from
 * another mod: a block id ({@code create:andesite_casing}) or a block tag ({@code #c:glass_blocks})
 * mapped to one of the {@link AcousticMaterial}s. The list is written in one line,
 * {@code id=material,#tag=material}, so it fits a settings file and the profile a server sends. A block's
 * own id wins over a tag; among tags, the one added first.
 * <p>
 * Immutable: a change makes a new list, so anything that caches the answers can tell it changed.
 */
public final class BlockRules {

    /** How many rules one list holds. */
    public static final int MAX_RULES = 64;

    public static final BlockRules EMPTY = new BlockRules(List.of());

    private static final Pattern KEY = Pattern.compile("#?[a-z0-9_.\\-]+(:[a-z0-9_.\\-/]+)?");

    /** One rule: {@code key} is {@code namespace:path}, with a leading {@code #} for a tag. */
    public record Rule(String key, AcousticMaterial material) {

        public boolean isTag() {
            return key.startsWith("#");
        }

        /** The id without the {@code #}. */
        public String id() {
            return isTag() ? key.substring(1) : key;
        }
    }

    private final List<Rule> rules;

    private BlockRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** A list without the {@link #MAX_RULES} limit, for the rules that come from data files (mods can list many blocks). */
    static BlockRules of(List<Rule> rules) {
        return rules.isEmpty() ? EMPTY : new BlockRules(List.copyOf(rules));
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int size() {
        return rules.size();
    }

    public List<Rule> rules() {
        return rules;
    }

    /**
     * A block id or tag as it is stored: lower case, {@code minecraft:} when no namespace is given, {@code #}
     * kept for a tag.
     *
     * @return the key, or {@code null} when the text is not an id or tag
     */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > 100 || !KEY.matcher(s).matches()) {
            return null;
        }
        boolean tag = s.startsWith("#");
        String id = tag ? s.substring(1) : s;
        if (id.isEmpty()) {
            return null;
        }
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        return tag ? "#" + id : id;
    }

    /** The material with this id ({@code stone}, {@code wool}...), or {@code null}. */
    public static AcousticMaterial materialOf(String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        for (AcousticMaterial m : AcousticMaterial.values()) {
            if (m.getId().equals(s)) {
                return m;
            }
        }
        return null;
    }

    /** Reads {@code id=material,#tag=material}; parts that are not a rule are left out, so a hand-edited line never breaks. */
    public static BlockRules parse(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        List<Rule> out = new ArrayList<>();
        for (String part : text.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = normalize(part.substring(0, eq));
            AcousticMaterial material = materialOf(part.substring(eq + 1));
            if (key == null || material == null || indexOf(out, key) >= 0) {
                continue;
            }
            out.add(new Rule(key, material));
            if (out.size() >= MAX_RULES) {
                break;
            }
        }
        return out.isEmpty() ? EMPTY : new BlockRules(Collections.unmodifiableList(out));
    }

    /** The line {@link #parse} reads back. */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Rule r : rules) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(r.key()).append('=').append(r.material().getId());
        }
        return sb.toString();
    }

    /**
     * This list with a rule added, or its material changed when the block or tag already has one.
     *
     * @return the new list, or {@code null} when {@code key} is not an id or tag or the list is full
     */
    public BlockRules with(String key, AcousticMaterial material) {
        String k = normalize(key);
        if (k == null || material == null) {
            return null;
        }
        List<Rule> out = new ArrayList<>(rules);
        int at = indexOf(out, k);
        if (at >= 0) {
            out.set(at, new Rule(k, material));
        } else if (out.size() >= MAX_RULES) {
            return null;
        } else {
            out.add(new Rule(k, material));
        }
        return new BlockRules(Collections.unmodifiableList(out));
    }

    /** This list without the rule for {@code key}; the same list when there is none. */
    public BlockRules without(String key) {
        String k = normalize(key);
        int at = k == null ? -1 : indexOf(rules, k);
        if (at < 0) {
            return this;
        }
        List<Rule> out = new ArrayList<>(rules);
        out.remove(at);
        return out.isEmpty() ? EMPTY : new BlockRules(Collections.unmodifiableList(out));
    }

    /** The rule for exactly this key, or {@code null}. */
    public Rule get(String key) {
        String k = normalize(key);
        int at = k == null ? -1 : indexOf(rules, k);
        return at < 0 ? null : rules.get(at);
    }

    /**
     * The material a block counts as by these rules.
     *
     * @param blockId {@code namespace:path} of the block
     * @param hasTag  whether the block is in the tag {@code namespace:path} (given without the {@code #})
     * @return the material, or {@code null} when no rule applies and the automatic guess stands
     */
    public AcousticMaterial find(String blockId, Predicate<String> hasTag) {
        if (rules.isEmpty()) {
            return null;
        }
        for (Rule r : rules) {
            if (!r.isTag() && r.key().equals(blockId)) {
                return r.material();
            }
        }
        for (Rule r : rules) {
            if (r.isTag() && hasTag.test(r.id())) {
                return r.material();
            }
        }
        return null;
    }

    private static int indexOf(List<Rule> list, String key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockRules other && rules.equals(other.rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }
}
