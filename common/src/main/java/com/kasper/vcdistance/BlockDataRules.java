package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block materials from data files, for blocks of other mods the automatic guess gets wrong. Any resource
 * pack or mod jar can hold {@code assets/<namespace>/voice_physics/materials.json} (read by the client),
 * and any data pack {@code data/<namespace>/voice_physics/materials.json} (read by the server that traces
 * the walls):
 * <pre>
 * {
 *   "replace": false,
 *   "blocks": {
 *     "create:andesite_casing": "metal",
 *     "#minecraft:beds": "wool"
 *   }
 * }
 * </pre>
 * A pack higher in the list wins for the same block; {@code "replace": true} drops what lower packs said.
 * These rules come after the player's and the server's own ({@code /vcd block}) and before the automatic
 * guess.
 */
public final class BlockDataRules {

    /** Where the file is, inside {@code assets/<namespace>/} or {@code data/<namespace>/}. */
    public static final String PATH = "voice_physics/materials.json";
    /** Most rules taken from all files together. */
    public static final int MAX = 4096;

    /** One file as found, in order from the lowest pack to the highest. */
    public record Source(String name, String json) {
    }

    /**
     * What a set of files gives.
     *
     * @param rules    the rules, higher packs first for the same block
     * @param files    how many files were read
     * @param problems mistakes found, one line each ("pack/file: what")
     */
    public record Loaded(BlockRules rules, int files, List<String> problems) {
        public static final Loaded NONE = new Loaded(BlockRules.EMPTY, 0, List.of());

        /** "12 rules from 2 files" for reports. */
        public String summary() {
            return rules.size() + (rules.size() == 1 ? " rule" : " rules") + " from " + files + (files == 1 ? " file" : " files")
                    + (problems.isEmpty() ? "" : ", " + problems.size() + (problems.size() == 1 ? " problem" : " problems"));
        }
    }

    private static volatile Loaded assets = Loaded.NONE;
    private static volatile Loaded data = Loaded.NONE;
    private static volatile BlockRules current = BlockRules.EMPTY;

    private BlockDataRules() {
    }

    /** The rules in force: the data packs' over the resource packs'. */
    public static BlockRules current() {
        return current;
    }

    /** What the resource packs and mod jars gave (client). */
    public static Loaded assets() {
        return assets;
    }

    /** What the data packs gave (the server, also the one inside single player). */
    public static Loaded data() {
        return data;
    }

    public static void setAssets(Loaded loaded) {
        assets = loaded == null ? Loaded.NONE : loaded;
        combine();
    }

    public static void setData(Loaded loaded) {
        data = loaded == null ? Loaded.NONE : loaded;
        combine();
    }

    private static synchronized void combine() {
        Map<String, AcousticMaterial> merged = new LinkedHashMap<>();
        put(merged, data.rules());
        put(merged, assets.rules());
        BlockRules next = toRules(merged);
        if (!next.equals(current)) {
            current = next;
        }
    }

    /** Adds rules that are not there yet (the caller goes from the strongest source down). */
    private static void put(Map<String, AcousticMaterial> into, BlockRules rules) {
        for (BlockRules.Rule r : rules.rules()) {
            if (into.size() >= MAX) {
                return;
            }
            into.putIfAbsent(r.key(), r.material());
        }
    }

    private static BlockRules toRules(Map<String, AcousticMaterial> map) {
        List<BlockRules.Rule> out = new ArrayList<>(map.size());
        map.forEach((k, v) -> out.add(new BlockRules.Rule(k, v)));
        return BlockRules.of(out);
    }

    /** Reads the files, lowest pack first; a broken file or entry is skipped and named in the problems. */
    public static Loaded read(List<Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return Loaded.NONE;
        }
        Map<String, AcousticMaterial> merged = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        int files = 0;
        for (Source source : sources) {
            Map<String, AcousticMaterial> file = new LinkedHashMap<>();
            boolean replace;
            try {
                replace = readFile(source.json(), file, problems, source.name());
            } catch (IllegalArgumentException e) {
                problems.add(source.name() + ": " + e.getMessage());
                continue;
            }
            files++;
            if (replace) {
                merged.clear();
            }
            // A higher pack's rules go to the front, so its tags are tried before a lower pack's
            Map<String, AcousticMaterial> next = new LinkedHashMap<>(file);
            merged.forEach(next::putIfAbsent);
            merged = next;
        }
        List<BlockRules.Rule> out = new ArrayList<>();
        for (Map.Entry<String, AcousticMaterial> e : merged.entrySet()) {
            if (out.size() >= MAX) {
                problems.add("more than " + MAX + " rules, the rest left out");
                break;
            }
            out.add(new BlockRules.Rule(e.getKey(), e.getValue()));
        }
        return new Loaded(BlockRules.of(out), files, List.copyOf(problems));
    }

    /** @return whether the file says {@code "replace": true} */
    private static boolean readFile(String json, Map<String, AcousticMaterial> into, List<String> problems, String name) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("the file is not a JSON object");
        }
        Object blocks = map.get("blocks");
        if (blocks != null && !(blocks instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("\"blocks\" is not an object");
        }
        if (blocks instanceof Map<?, ?> entries) {
            for (Map.Entry<?, ?> e : entries.entrySet()) {
                String key = BlockRules.normalize(String.valueOf(e.getKey()));
                AcousticMaterial material = e.getValue() instanceof String s ? BlockRules.materialOf(s) : null;
                if (key == null) {
                    problems.add(name + ": \"" + e.getKey() + "\" is not a block id or #tag");
                } else if (material == null) {
                    problems.add(name + ": \"" + e.getValue() + "\" for " + key + " is not a material ("
                            + materialIds() + ")");
                } else {
                    into.put(key, material);
                }
            }
        }
        return Boolean.TRUE.equals(map.get("replace"));
    }

    private static String materialIds() {
        StringBuilder sb = new StringBuilder();
        for (AcousticMaterial m : AcousticMaterial.values()) {
            if (m != AcousticMaterial.LIQUID) {
                sb.append(sb.length() == 0 ? "" : ", ").append(m.getId());
            }
        }
        return sb.toString();
    }
}
