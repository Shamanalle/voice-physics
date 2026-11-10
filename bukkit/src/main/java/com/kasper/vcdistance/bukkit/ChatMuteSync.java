package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Jv;
import com.kasper.vcdistance.VoiceMute;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors active chat mutes from EssentialsX, LiteBans, LibertyBans and AdvancedBan into
 * {@link com.kasper.vcdistance.ServerSettings#setExternalMutes} while {@code server_integrations} is on.
 * <p>
 * Every plugin is reached by reflection so none of them is linked at compile time, and every check runs on one
 * background daemon thread (LiteBans refuses queries on the server thread) over an immutable snapshot of online
 * players, so neither the main thread nor Folia's region threads ever wait on a database.
 */
final class ChatMuteSync {

    private static final long POLL_SECONDS = 2L;
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    @com.github.bsideup.jabel.Desugar
    private record Entry(UUID id, String name, InetAddress ip) {
    }

    private interface Provider {
        String name();

        VoiceMute check(Entry player, long nowMillis) throws Exception;
    }

    private static final Map<UUID, Entry> online = new ConcurrentHashMap<>();
    private static final AtomicBoolean queued = new AtomicBoolean();
    private static volatile List<Provider> providers = Jv.listOf();
    private static volatile ScheduledExecutorService executor;
    private static volatile boolean lastEmpty = true;

    private ChatMuteSync() {
    }

    /** Starts the background sync only when at least one supported moderation plugin is installed. */
    static synchronized void startIfPresent(JavaPlugin plugin) {
        stop();
        List<Provider> found = new ArrayList<>(4);
        addIfPresent(plugin, "Essentials", ChatMuteSync::essentials, found);
        addIfPresent(plugin, "LiteBans", ChatMuteSync::liteBans, found);
        addIfPresent(plugin, "LibertyBans", ChatMuteSync::libertyBans, found);
        addIfPresent(plugin, "AdvancedBan", ChatMuteSync::advancedBan, found);
        if (found.isEmpty()) {
            return;
        }
        providers = Jv.copyOf(found);
        List<String> names = new ArrayList<>(found.size());
        for (Provider p : found) {
            names.add(p.name());
        }
        plugin.getLogger().info("Chat mute sync found (" + String.join(", ", names)
                + "): muted players are silent in voice chat while server_integrations is on");
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vcd-mute-sync");
            t.setDaemon(true);
            return t;
        });
        executor = exec;
        exec.scheduleWithFixedDelay(ChatMuteSync::refreshAll, 1L, POLL_SECONDS, TimeUnit.SECONDS);
    }

    /** Records an online player from their owning thread and schedules an immediate check when integrations are on. */
    static void noteJoin(Player player) {
        InetAddress ip = null;
        try {
            InetSocketAddress addr = player.getAddress();
            ip = addr == null ? null : addr.getAddress();
        } catch (Throwable ignored) {
        }
        online.put(player.getUniqueId(), new Entry(player.getUniqueId(), player.getName(), ip));
        ScheduledExecutorService exec = executor;
        if (exec != null && AudioDistancePlugin.SERVER_SETTINGS.isServerIntegrations()
                && queued.compareAndSet(false, true)) {
            try {
                exec.execute(() -> {
                    queued.set(false);
                    refreshAll();
                });
            } catch (RuntimeException ignored) {
                queued.set(false);
            }
        }
    }

    static void forget(UUID player) {
        online.remove(player);
    }

    static synchronized void stop() {
        ScheduledExecutorService exec = executor;
        executor = null;
        providers = Jv.listOf();
        online.clear();
        queued.set(false);
        lastEmpty = true;
        AudioDistancePlugin.SERVER_SETTINGS.setExternalMutes(Jv.mapOf());
        if (exec != null) {
            exec.shutdownNow();
        }
    }

    private static void refreshAll() {
        try {
            if (!AudioDistancePlugin.SERVER_SETTINGS.isServerIntegrations() || providers.isEmpty() || online.isEmpty()) {
                if (!lastEmpty) {
                    AudioDistancePlugin.SERVER_SETTINGS.setExternalMutes(Jv.mapOf());
                    lastEmpty = true;
                }
                return;
            }
            long now = System.currentTimeMillis();
            Map<UUID, VoiceMute> active = new LinkedHashMap<>();
            for (Entry player : online.values()) {
                for (Provider provider : providers) {
                    try {
                        VoiceMute mute = provider.check(player, now);
                        if (mute != null) {
                            active.put(player.id(), mute);
                            break;
                        }
                    } catch (Throwable ignored) {
                        // A plugin still starting up or reloading its database: skip until the next poll
                    }
                }
            }
            if (!active.isEmpty() || !lastEmpty) {
                AudioDistancePlugin.SERVER_SETTINGS.setExternalMutes(active);
                lastEmpty = active.isEmpty();
            }
        } catch (Throwable ignored) {
        }
    }

    @FunctionalInterface
    private interface Factory {
        Provider create(Plugin target) throws Exception;
    }

    private static void addIfPresent(JavaPlugin self, String pluginName, Factory factory, List<Provider> out) {
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            return;
        }
        try {
            Provider p = factory.create(target);
            if (p != null) {
                out.add(p);
            }
        } catch (Throwable t) {
            self.getLogger().warning("Could not hook " + pluginName + " for chat-mute sync: " + t);
        }
    }

    private static Provider essentials(Plugin ess) throws Exception {
        Method getUser = ess.getClass().getMethod("getUser", UUID.class);
        Class<?> iUser;
        try {
            iUser = Class.forName("com.earth2me.essentials.IUser");
        } catch (ClassNotFoundException e) {
            iUser = Class.forName("net.ess3.api.IUser");
        }
        Method isMuted = iUser.getMethod("isMuted");
        Method getMuteTimeout = iUser.getMethod("getMuteTimeout");
        Method reasonMethod = null;
        try {
            reasonMethod = iUser.getMethod("getMuteReason");
        } catch (NoSuchMethodException ignored) {
        }
        Method getMuteReason = reasonMethod;
        return new Provider() {
            @Override
            public String name() {
                return "EssentialsX";
            }

            @Override
            public VoiceMute check(Entry player, long nowMillis) throws Exception {
                Object user = getUser.invoke(ess, player.id());
                if (user == null || !Boolean.TRUE.equals(isMuted.invoke(user))) {
                    return null;
                }
                long timeout = ((Number) getMuteTimeout.invoke(user)).longValue();
                if (timeout > 0L && timeout <= nowMillis) {
                    return null;
                }
                Object r = getMuteReason == null ? null : getMuteReason.invoke(user);
                return new VoiceMute(player.id(), player.name(), Math.max(0L, timeout),
                        "EssentialsX", r instanceof String s ? s : "");
            }
        };
    }

    private static Provider liteBans(Plugin ignoredPlugin) throws Exception {
        Class<?> dbClass = Class.forName("litebans.api.Database");
        Method get = dbClass.getMethod("get");
        Method isPlayerMuted = dbClass.getMethod("isPlayerMuted", UUID.class, String.class);
        return new Provider() {
            @Override
            public String name() {
                return "LiteBans";
            }

            @Override
            public VoiceMute check(Entry player, long nowMillis) throws Exception {
                Object db = get.invoke(null);
                String ip = player.ip() == null ? null : player.ip().getHostAddress();
                if (db != null && Boolean.TRUE.equals(isPlayerMuted.invoke(db, player.id(), ip))) {
                    return new VoiceMute(player.id(), player.name(), 0L, "LiteBans", "");
                }
                return null;
            }
        };
    }

    private static Provider libertyBans(Plugin ignoredPlugin) throws Exception {
        Class<?> omnibusProvider = Class.forName("space.arim.omnibus.OmnibusProvider");
        Class<?> omnibusClass = Class.forName("space.arim.omnibus.Omnibus");
        Class<?> registryClass = Class.forName("space.arim.omnibus.registry.Registry");
        Class<?> lbClass = Class.forName("space.arim.libertybans.api.LibertyBans");
        Class<?> selectorClass = Class.forName("space.arim.libertybans.api.select.PunishmentSelector");
        Class<?> netAddrClass = Class.forName("space.arim.libertybans.api.NetworkAddress");
        Class<?> punishmentClass = Class.forName("space.arim.libertybans.api.punish.Punishment");

        Method getOmnibus = omnibusProvider.getMethod("getOmnibus");
        Method getRegistry = omnibusClass.getMethod("getRegistry");
        Method getProvider = registryClass.getMethod("getProvider", Class.class);
        Method getSelector = lbClass.getMethod("getSelector");
        Method addrOf = netAddrClass.getMethod("of", InetAddress.class);
        Method getCachedMute = selectorClass.getMethod("getCachedMute", UUID.class, netAddrClass);
        Method isPermanent = punishmentClass.getMethod("isPermanent");
        Method getEndDate = punishmentClass.getMethod("getEndDate");
        Method getReason = punishmentClass.getMethod("getReason");

        return new Provider() {
            private volatile Object selector;

            private Object selector() throws Exception {
                Object s = selector;
                if (s != null) {
                    return s;
                }
                Object omnibus = getOmnibus.invoke(null);
                if (omnibus == null) {
                    return null;
                }
                Object registry = getRegistry.invoke(omnibus);
                Optional<?> opt = (Optional<?>) getProvider.invoke(registry, lbClass);
                Object lb = opt == null ? null : opt.orElse(null);
                if (lb != null) {
                    s = getSelector.invoke(lb);
                    selector = s;
                }
                return s;
            }

            @Override
            public String name() {
                return "LibertyBans";
            }

            @Override
            public VoiceMute check(Entry player, long nowMillis) throws Exception {
                Object sel = selector();
                if (sel == null) {
                    return null;
                }
                Object addr = addrOf.invoke(null, player.ip() != null ? player.ip() : LOOPBACK);
                Object stage = getCachedMute.invoke(sel, player.id(), addr);
                if (!(stage instanceof CompletionStage<?> cs)) {
                    return null;
                }
                Object res = cs.toCompletableFuture().join();
                Object pun = res instanceof Optional<?> o ? o.orElse(null) : null;
                if (pun == null) {
                    return null;
                }
                boolean perm = Boolean.TRUE.equals(isPermanent.invoke(pun));
                long until = perm ? 0L : ((Instant) getEndDate.invoke(pun)).toEpochMilli();
                if (until > 0L && until <= nowMillis) {
                    return null;
                }
                Object r = getReason.invoke(pun);
                return new VoiceMute(player.id(), player.name(), Math.max(0L, until),
                        "LibertyBans", r instanceof String s ? s : "");
            }
        };
    }

    private static Provider advancedBan(Plugin ignoredPlugin) throws Exception {
        Class<?> pmClass = Class.forName("me.leoko.advancedban.manager.PunishmentManager");
        Method pmGet = pmClass.getMethod("get");
        Method getMute = pmClass.getMethod("getMute", String.class);
        Class<?> umClass = Class.forName("me.leoko.advancedban.manager.UUIDManager");
        Method umGet = umClass.getMethod("get");
        Method getInMemoryUuid = umClass.getMethod("getInMemoryUUID", String.class);
        Class<?> punClass = Class.forName("me.leoko.advancedban.utils.Punishment");
        Method getEnd = punClass.getMethod("getEnd");
        Method getReason = punClass.getMethod("getReason");
        return new Provider() {
            @Override
            public String name() {
                return "AdvancedBan";
            }

            @Override
            public VoiceMute check(Entry player, long nowMillis) throws Exception {
                Object um = umGet.invoke(null);
                Object mem = um == null ? null : getInMemoryUuid.invoke(um, player.name());
                String key = mem instanceof String s && !s.isEmpty() ? s : player.id().toString().replace("-", "");
                Object pm = pmGet.invoke(null);
                Object pun = pm == null ? null : getMute.invoke(pm, key);
                if (pun == null) {
                    return null;
                }
                long end = ((Number) getEnd.invoke(pun)).longValue();
                if (end > 0L && end <= nowMillis) {
                    return null;
                }
                Object r = getReason.invoke(pun);
                String reason = r instanceof String s && !"none".equalsIgnoreCase(s) ? s : "";
                return new VoiceMute(player.id(), player.name(), Math.max(0L, end), "AdvancedBan", reason);
            }
        };
    }
}
