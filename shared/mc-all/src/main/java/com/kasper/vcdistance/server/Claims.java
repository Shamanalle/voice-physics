package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Mc;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.Zone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sound zones from Open Parties and Claims: a zone {@code claim.<name>} covers every chunk claimed by
 * the player {@code <name>}, or by anyone in the party {@code <name>} leads ({@code claim.server} for
 * the server's own claims). OPAC is reached through reflection, only when it is installed and a
 * claim zone exists, so the addon does not depend on it.
 */
public final class Claims {

    private static final String API = "xaero.pac.common.server.api.OpenPACServerAPI";
    /** OPAC's owner of the server's own claims. */
    private static final UUID SERVER_CLAIMS = new UUID(0L, 0L);
    /** A player's claim is looked up again after this, even without moving to another chunk. */
    private static final long RECHECK_NANOS = 2_000_000_000L;
    private static final long NAMES_NANOS = 60_000_000_000L;

    @com.github.bsideup.jabel.Desugar

    private record Looked(String world, int chunkX, int chunkZ, long nanos, List<String> ids) {
    }

    private static final Map<UUID, Looked> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
    /** "Long ago", far enough from MIN_VALUE that {@code now - namesNanos} does not overflow. */
    private static volatile long namesNanos = Long.MIN_VALUE / 2;
    private static volatile boolean failed;
    private static volatile Boolean installed;

    private Claims() {
    }

    /**
     * The claim zones' ids at the player's chunk ("claim:steve", then the party leader), for
     * {@link Zone#resolve}; empty without OPAC or claim zones.
     */
    public static List<String> at(ServerPlayer player, String world) {
        if (failed || !hasClaimZones() || !installed()) {
            return com.kasper.vcdistance.Jv.listOf();
        }
        int chunkX = (int) Math.floor(player.getX()) >> 4;
        int chunkZ = (int) Math.floor(player.getZ()) >> 4;
        long now = System.nanoTime();
        Looked last = CACHE.get(player.getUUID());
        if (last != null && last.chunkX == chunkX && last.chunkZ == chunkZ && last.world.equals(world)
                && now - last.nanos < RECHECK_NANOS) {
            return last.ids;
        }
        List<String> ids;
        try {
            ids = lookup(((ServerLevel) Mc.level(player)).getServer(), world, chunkX, chunkZ);
        } catch (Throwable t) {
            failed = true;
            DistanceConfig.LOGGER.warn("Could not read Open Parties and Claims, claim zones are off: {}", t.toString());
            return com.kasper.vcdistance.Jv.listOf();
        }
        CACHE.put(player.getUUID(), new Looked(world, chunkX, chunkZ, now, ids));
        return ids;
    }

    public static void forget(UUID player) {
        CACHE.remove(player);
    }

    /** Remembers an online player's name, for claims of players who are offline later. */
    public static void seen(UUID id, String name) {
        NAMES.put(id, name);
    }

    private static boolean hasClaimZones() {
        for (String key : AudioDistancePlugin.SERVER_SETTINGS.zones().keySet()) {
            if (key.startsWith(Zone.CLAIM + ":")) {
                return true;
            }
        }
        return false;
    }

    /** Whether Open Parties and Claims is installed. */
    public static boolean installed() {
        Boolean i = installed;
        if (i == null) {
            try {
                Class.forName(API);
                i = true;
            } catch (Throwable t) {
                i = false;
            }
            installed = i;
        }
        return i;
    }

    private static List<String> lookup(MinecraftServer server, String world, int chunkX, int chunkZ) throws Exception {
        Object api = invoke(Class.forName(API), null, "get", server);
        Object manager = invoke(api.getClass(), api, "getServerClaimsManager");
        Method get = null;
        for (Class<?> t : publicTypes(manager.getClass())) {
            for (Method m : t.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (get == null && m.getName().equals("get") && p.length == 3 && p[1] == int.class && p[2] == int.class) {
                    get = m;
                }
            }
        }
        if (get == null) {
            throw new NoSuchMethodException("claims get(dimension, x, z)");
        }
        Object claim = get.invoke(manager, dimension(get.getParameterTypes()[0], world), chunkX, chunkZ);
        if (claim == null) {
            return com.kasper.vcdistance.Jv.listOf();
        }
        UUID owner = (UUID) invoke(claim.getClass(), claim, "getPlayerId");
        List<String> ids = new ArrayList<>();
        if (owner == null) {
            return ids;
        }
        if (owner.equals(SERVER_CLAIMS)) {
            ids.add(Zone.CLAIM + ":server");
            return ids;
        }
        Object party = null;
        try {
            Object parties = invoke(api.getClass(), api, "getPartyManager");
            party = invoke(parties.getClass(), parties, "getPartyByMember", owner);
        } catch (ReflectiveOperationException ignored) {
            // No parties in this OPAC version
        }
        add(ids, nameOf(owner, party));
        ids.add(Zone.CLAIM + ":" + owner);
        if (party != null) {
            Object leader = invoke(party.getClass(), party, "getOwner");
            if (leader != null) {
                add(ids, (String) invoke(leader.getClass(), leader, "getUsername"));
            }
        }
        return ids;
    }

    private static void add(List<String> ids, String name) {
        if (name != null && !com.kasper.vcdistance.Jv.isBlank(name)) {
            String id = Zone.CLAIM + ":" + name.trim().toLowerCase(Locale.ROOT);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
    }

    /** The owner's name: from their party, the players seen online, or the server's user cache. */
    private static String nameOf(UUID owner, Object party) {
        if (party != null) {
            try {
                Object member = invoke(party.getClass(), party, "getMemberInfo", owner);
                if (member != null) {
                    return (String) invoke(member.getClass(), member, "getUsername");
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to the names below
            }
        }
        String name = NAMES.get(owner);
        if (name == null && System.nanoTime() - namesNanos > NAMES_NANOS) {
            namesNanos = System.nanoTime();
            readUserCache();
            name = NAMES.get(owner);
        }
        return name;
    }

    private static final Pattern USER = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

    /** usercache.json in the server's folder: every player who has joined. */
    private static void readUserCache() {
        Path file = java.nio.file.Paths.get("usercache.json");
        try {
            if (Files.isRegularFile(file)) {
                Matcher m = USER.matcher(com.kasper.vcdistance.Jv.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    NAMES.putIfAbsent(UUID.fromString(m.group(2)), m.group(1));
                }
            }
        } catch (Exception ignored) {
            // Names stay unknown; the zone can use the owner's UUID instead
        }
    }

    /** The dimension as OPAC's key type (ResourceLocation, or Identifier on newer versions). */
    private static Object dimension(Class<?> type, String world) throws ReflectiveOperationException {
        String id = world.indexOf(':') >= 0 ? world : "minecraft:" + world;
        for (String factory : new String[]{"tryParse", "parse"}) {
            try {
                Object key = type.getMethod(factory, String.class).invoke(null, id);
                if (key != null) {
                    return key;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next way
            }
        }
        Constructor<?> c = type.getConstructor(String.class);
        return c.newInstance(id);
    }

    /**
     * Calls a method through a public class or interface that declares it: OPAC's implementations
     * may be hidden, its API interfaces are not.
     */
    private static Object invoke(Class<?> type, Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Class<?> t : publicTypes(type)) {
            for (Method m : t.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    return m.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    /** The type, its superclasses and all their interfaces that are public. */
    private static List<Class<?>> publicTypes(Class<?> type) {
        List<Class<?>> out = new ArrayList<>();
        java.util.ArrayDeque<Class<?>> todo = new java.util.ArrayDeque<>();
        todo.add(type);
        while (!todo.isEmpty()) {
            Class<?> t = todo.poll();
            if (out.contains(t)) {
                continue;
            }
            if (java.lang.reflect.Modifier.isPublic(t.getModifiers())) {
                out.add(t);
            }
            if (t.getSuperclass() != null) {
                todo.add(t.getSuperclass());
            }
            todo.addAll(com.kasper.vcdistance.Jv.listOf(t.getInterfaces()));
        }
        return out;
    }
}
