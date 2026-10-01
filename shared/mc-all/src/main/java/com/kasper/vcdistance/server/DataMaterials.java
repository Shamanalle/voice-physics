package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Mc;
import com.kasper.vcdistance.BlockDataRules;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.Problems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads {@code voice_physics/materials.json} from resource packs and mod jars (client, {@code assets/}) or
 * from data packs (server, {@code data/}); see {@link BlockDataRules}.
 */
public final class DataMaterials {

    /** Bigger files are not read. */
    private static final int MAX_BYTES = 1 << 20;

    /** The server's resources the data-pack rules were read from; a {@code /reload} gives new ones. */
    private static Object serverResources;

    private DataMaterials() {
    }

    /** Server tick: reads the data packs when the server starts and after {@code /reload}. */
    public static void tick(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        if (resources == serverResources) {
            return;
        }
        serverResources = resources;
        BlockDataRules.setData(read(resources, "data pack"));
    }

    /**
     * Every {@code voice_physics/materials.json} with its packs, lowest first. The listing method and its filter type
     * differ between versions (Predicate up to 26.2, Selector in 26.3), so it is called by name.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<Resource>> list(ResourceManager resources) throws Exception {
        java.lang.reflect.Method method = null;
        for (String name : new String[]{"listResourceStacks", "listResources"}) {
            for (java.lang.reflect.Method m : ResourceManager.class.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 2 && m.getParameterTypes()[0] == String.class
                        && m.getParameterTypes()[1].isInterface() && Map.class.isAssignableFrom(m.getReturnType())) {
                    method = m;
                    break;
                }
            }
            if (method != null) {
                break;
            }
        }
        if (method == null) {
            throw new NoSuchMethodException("ResourceManager listing");
        }
        Class<?> filterType = method.getParameterTypes()[1];
        Object filter = java.lang.reflect.Proxy.newProxyInstance(filterType.getClassLoader(), new Class<?>[]{filterType}, (proxy, m, args) -> {
            if (m.getDeclaringClass() == Object.class) {
                return m.getName().equals("hashCode") ? 0 : m.getName().equals("equals") ? proxy == args[0] : "filter";
            }
            boolean match = args != null && args.length > 0 && String.valueOf(args[0]).endsWith(":" + BlockDataRules.PATH);
            return m.getReturnType() == boolean.class ? (Object) match : m.getReturnType() == Boolean.class ? (Object) match : null;
        });
        Map<String, List<Resource>> found = new TreeMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) method.invoke(resources, "voice_physics", filter)).entrySet()) {
            Object value = e.getValue();
            found.put(String.valueOf(e.getKey()), value instanceof List<?> ? (List<Resource>) value : List.of((Resource) value));
        }
        return found;
    }

    /** Every {@code <namespace>:voice_physics/materials.json}, namespaces in alphabetical order, each from the lowest pack up. */
    public static BlockDataRules.Loaded read(ResourceManager resources, String kind) {
        List<BlockDataRules.Source> sources = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        try {
            Map<String, List<Resource>> found = new TreeMap<>();
            found.putAll(list(resources));
            for (Map.Entry<String, List<Resource>> e : found.entrySet()) {
                int pack = 0;
                for (Resource resource : e.getValue()) {
                    pack++;
                    String name = e.getKey() + (e.getValue().size() > 1 ? " (" + kind + " " + pack + ")" : "");
                    try (InputStream in = Mc.open(resource)) {
                        byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                        if (bytes.length > MAX_BYTES) {
                            unreadable.add(name + ": larger than 1 MB");
                            continue;
                        }
                        sources.add(new BlockDataRules.Source(name, new String(bytes, StandardCharsets.UTF_8)));
                    } catch (Exception ex) {
                        unreadable.add(name + ": " + ex);
                    }
                }
            }
        } catch (Throwable t) {
            unreadable.add("listing " + BlockDataRules.PATH + ": " + t);
        }
        BlockDataRules.Loaded loaded = BlockDataRules.read(sources);
        if (!unreadable.isEmpty()) {
            List<String> problems = new ArrayList<>(unreadable);
            problems.addAll(loaded.problems());
            loaded = new BlockDataRules.Loaded(loaded.rules(), loaded.files(), List.copyOf(problems));
        }
        if (loaded.files() > 0 || !loaded.problems().isEmpty()) {
            DistanceConfig.LOGGER.info("Block materials from {}s: {}", kind, loaded.summary());
        }
        int shown = 0;
        for (String problem : loaded.problems()) {
            if (shown++ < 5) {
                DistanceConfig.LOGGER.warn("Block materials, {}", problem);
                Problems.record("Block materials, " + problem);
            }
        }
        return loaded;
    }
}
