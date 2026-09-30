package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AdminCommands;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.CommandReply;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ModEnvironment;
import com.kasper.vcdistance.PlayerCommands;
import com.kasper.vcdistance.RoomEstimate;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.ServerPlayers;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.ZoneOutlines;
import com.kasper.vcdistance.ZoneTracker;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the addon for Bukkit, Spigot, Paper, Purpur and Folia. Does the same as the Fabric
 * server side: muffles voices through walls for players without the addon and sends players who have
 * it the server's sound profile and the voice chat state of the players near them. The client half
 * works with this plugin exactly as with a Fabric server, because both speak the same
 * {@link LinkProtocol} over the same channels.
 */
public final class AudioDistanceBukkit extends JavaPlugin implements Listener {

    static final String HELLO_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.HELLO;
    static final String PROFILE_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.PROFILE;
    static final String NEARBY_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.NEARBY;
    static final String ADMIN_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.ADMIN;
    static final String ADMIN_REPLY_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.ADMIN_REPLY;
    static final String ADMIN_PERMISSION = AdminCommands.PERM_ADMIN;
    /** How far {@code /vcd zone pos1 look} reaches, in blocks. */
    static final int LOOK_REACH = 64;

    private static final int RELOAD_CHECK_TICKS = 40;
    /** Each player's surroundings are measured this often for the echo (once a second), spread over the ticks. */
    private static final int ROOM_TICKS = 20;
    /** The plugin's name before 2.2.0, and so its old settings folder. */
    private static final String OLD_NAME = "VoicechatAudioDistance";

    private final ZoneTracker zones = new ZoneTracker();
    /** Folia: each player as their own thread last saw them, for the voice rules. */
    private final Map<UUID, ServerPlayers.Info> infos = new ConcurrentHashMap<>();
    private Scheduling scheduling;
    private BukkitThickness thickness;
    private int ticks;

    @Override
    public void onLoad() {
        moveOldSettings();
        // Settings live in plugins/<name>/ instead of the loader's config directory
        ModEnvironment.setConfigDir(getDataFolder().toPath());
    }

    /** The plugin was called VoicechatAudioDistance before 2.2.0: its settings folder moves along once. */
    private void moveOldSettings() {
        File folder = getDataFolder();
        File old = new File(folder.getParentFile(), OLD_NAME);
        if (!folder.exists() && old.isDirectory()) {
            try {
                Files.move(old.toPath(), folder.toPath());
                getLogger().info("Moved the settings from plugins/" + OLD_NAME + " to plugins/" + folder.getName());
            } catch (Exception e) {
                getLogger().warning("Could not move plugins/" + OLD_NAME + " to plugins/" + folder.getName()
                        + "; move it by hand to keep the settings: " + e);
            }
        }
    }

    @Override
    public void onEnable() {
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat was not found, disabling");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        scheduling = Scheduling.create(this);
        thickness = new BukkitThickness(scheduling.isRegionized());
        AudioDistancePlugin.ensureServerSettings();
        service.registerPlugin(new ServerPlugin());

        getServer().getMessenger().registerOutgoingPluginChannel(this, PROFILE_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NEARBY_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ADMIN_REPLY_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, HELLO_CHANNEL,
                (channel, player, message) -> onHello(player, message));
        getServer().getMessenger().registerIncomingPluginChannel(this, ADMIN_CHANNEL,
                (channel, player, message) -> onAdmin(player, message));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new VoiceMenu.Events(), this);
        scheduling.everyTick(this::tick);
        for (Player player : getServer().getOnlinePlayers()) {
            startPlayerTick(player);
        }
        if (scheduling.isRegionized()) {
            getLogger().info("Running on Folia: each player's walls are measured on their own region's thread");
        }
        PluginCommand command = getCommand(AdminCommands.NAME);
        if (command != null) {
            AdminCommand handler = new AdminCommand();
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        PluginCommand voice = getCommand(PlayerCommands.NAME);
        if (voice != null) {
            VoiceCommand handler = new VoiceCommand();
            voice.setExecutor(handler);
            voice.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (scheduling != null) {
            scheduling.cancelAll();
        }
        ZoneOutlines.clear();
        infos.clear();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        AudioDistancePlugin.SERVER_WALLS.clear();
        BlockAcoustics.clearCache();
        zones.clear();
    }

    /**
     * Every tick (the main thread, or Folia's global region): measures walls, sends nearby voice
     * states, draws zone borders and picks up edited settings. On Folia the parts that touch a player
     * or the blocks around them run in {@link #startPlayerTick} instead.
     */
    private void tick() {
        if (!scheduling.isRegionized()) {
            AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        }
        refreshPlayers();
        ++ticks;
        if (!scheduling.isRegionized() && ticks % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
            for (Player player : getServer().getOnlinePlayers()) {
                sendNearbyAndZone(player);
            }
        }
        if (!scheduling.isRegionized()) {
            for (Player player : getServer().getOnlinePlayers()) {
                if ((ticks + player.getEntityId()) % ROOM_TICKS == 0) {
                    measureRoom(player);
                }
            }
        }
        ZoneOutlines.tick((id, world, points) -> {
            Player p = getServer().getPlayer(id);
            if (p != null) {
                scheduling.onPlayer(p, () -> drawOutline(p, world, points));
            }
        });
        if (ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            BlockAcoustics.clearCache();
            resendProfiles();
        }
    }

    /** Folia: a player's own share of the tick, on the thread that owns them. */
    private void startPlayerTick(Player player) {
        int[] count = {0};
        UUID id = player.getUniqueId();
        scheduling.everyPlayerTick(player, () -> {
            int n = ++count[0];
            if (n % 5 == 0) {
                try {
                    infos.put(id, info(player));
                } catch (Throwable ignored) {
                    // half-way through joining or leaving
                }
            }
            AudioDistancePlugin.SERVER_WALLS.tickListener(id, thickness);
            if ((n + player.getEntityId()) % ROOM_TICKS == 0) {
                measureRoom(player);
            }
            if (n % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
                sendNearbyAndZone(player);
            }
        });
    }

    /**
     * The echo of the place {@code player} stands in, for the server's own echo (players without the addon
     * hear the speaker's room and their own). Only while it is on, and not in a zone that sets its own echo.
     */
    private void measureRoom(Player player) {
        if (!AudioDistancePlugin.SERVER_SETTINGS.isServerEffects() || !AudioDistancePlugin.SERVER_SETTINGS.profile().isReverbEnabled()) {
            return;
        }
        try {
            Zone zone = zoneOf(player);
            RoomEstimate room = com.kasper.vcdistance.ServerRooms.needsMeasuring(zone) ? RoomProbe.measure(player) : null;
            if (room != null || !com.kasper.vcdistance.ServerRooms.needsMeasuring(zone)) {
                AudioDistancePlugin.SERVER_ROOMS.update(player.getUniqueId(), room, zone, System.nanoTime());
            }
        } catch (Throwable ignored) {
            // half-way through joining or leaving
        }
    }

    /** For a player with the addon: the voice chat state of the players nearby, and a new profile on entering a zone. */
    private void sendNearbyAndZone(Player player) {
        if (!AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUniqueId())) {
            return;
        }
        if (player.getListeningPluginChannels().contains(NEARBY_CHANNEL)) {
            sendNearby(player);
        }
        // Walked into another world or region with its own profile
        Zone zone = zoneOf(player);
        if (zones.changed(player.getUniqueId(), zone)) {
            sendProfile(player, zone);
        }
    }

    private static void drawOutline(Player player, String world, List<double[]> points) {
        if (!Zone.sameWorld(player.getWorld().getName(), world)) {
            return;
        }
        for (double[] p : points) {
            player.spawnParticle(Particle.END_ROD, p[0], p[1], p[2], 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Every few ticks: the players for the voice rules, and the addon requirement. */
    private void refreshPlayers() {
        if (!ServerHooks.refreshDue()) {
            return;
        }
        List<ServerPlayers.Info> online = new ArrayList<>();
        if (scheduling.isRegionized()) {
            // Each player's own thread keeps their entry fresh; drop those who left
            infos.keySet().removeIf(id -> getServer().getPlayer(id) == null);
            online.addAll(infos.values());
        } else {
            for (Player p : getServer().getOnlinePlayers()) {
                try {
                    online.add(info(p));
                } catch (Throwable ignored) {
                    // A player half-way through joining or leaving
                }
            }
        }
        ServerHooks.refresh(online, new ServerHooks.Platform() {
            @Override
            public void message(UUID player, String text) {
                Player p = getServer().getPlayer(player);
                if (p != null) {
                    scheduling.onPlayer(p, () -> {
                        try {
                            ChatLink.send(p, text);
                        } catch (LinkageError e) {
                            p.sendMessage(text); // Spigot: no Adventure, the link stays plain text
                        }
                    });
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void kick(UUID player, String text) {
                Player p = getServer().getPlayer(player);
                if (p != null) {
                    scheduling.onPlayer(p, () -> p.kickPlayer(text));
                }
            }

            @Override
            public boolean canSee(UUID viewer, UUID other) {
                Player v = getServer().getPlayer(viewer);
                Player o = getServer().getPlayer(other);
                return v == null || o == null || visible(v, o);
            }

            @Override
            public void actionBar(UUID player, String text) {
                Player p = getServer().getPlayer(player);
                if (p != null) {
                    scheduling.onPlayer(p, () -> {
                        try {
                            ChatLink.actionBar(p, text);
                        } catch (LinkageError e) {
                            try {
                                ChatLink.Spigot.actionBar(p, text);
                            } catch (LinkageError ignored) {
                                // Neither API: no line above the hotbar
                            }
                        }
                    });
                }
            }
        });
    }

    /** A player as the voice rules see them. */
    @SuppressWarnings("deprecation")
    static ServerPlayers.Info info(Player p) {
        Location at = p.getLocation();
        boolean regions = AudioDistancePlugin.SERVER_SETTINGS.zones().keySet().stream().anyMatch(k -> k.startsWith(Zone.REGION + ":"));
        String language = "";
        try {
            language = p.getLocale();
        } catch (Throwable ignored) {
        }
        // Only looked at when the server's effects are on: it is a block or two per player
        boolean effects = AudioDistancePlugin.SERVER_SETTINGS.isServerEffects();
        return new ServerPlayers.Info(p.getUniqueId(), p.getName(), p.getWorld().getName(),
                at.getX(), at.getY(), at.getZ(),
                p.isSneaking(), !p.isDead(), p.getGameMode() == GameMode.SPECTATOR,
                item(p.getInventory().getItemInMainHand()), item(p.getInventory().getItemInOffHand()),
                regions ? WorldGuardRegions.at(at) : List.of(), language == null ? "" : language,
                effects && RoomProbe.underwater(p), effects ? RoomProbe.weather(p) : null);
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        try {
            return stack.getType().getKey().toString();
        } catch (Throwable t) {
            return "minecraft:" + stack.getType().name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Whether the player may use any part of /vcd (and so gets the Server tab). */
    static boolean isAdmin(Player player) {
        for (String permission : AdminCommands.PERMISSIONS) {
            if (allows(player, permission)) {
                return true;
            }
        }
        return false;
    }

    /** A part of /vcd: its own permission, or vcd.admin for all of them (both default to operators, see plugin.yml). */
    static boolean allows(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission(ADMIN_PERMISSION);
    }

    /** A command from the player's Server tab; answered only for admins. */
    private void onAdmin(Player player, byte[] message) {
        AudioDistancePlugin.PLAYERS.update(info(player));
        String reply = ServerHooks.admin(player.getUniqueId(), LinkProtocol.decode(message), isAdmin(player), context(player));
        if (reply != null) {
            player.sendPluginMessage(this, ADMIN_REPLY_CHANNEL, LinkProtocol.encode(reply));
        }
    }

    private void onHello(Player player, byte[] message) {
        if (!ServerHooks.hello(player.getUniqueId(), LinkProtocol.decode(message))) {
            return;
        }
        Zone zone = zoneOf(player);
        zones.set(player.getUniqueId(), zone);
        sendProfile(player, zone);
    }

    private void sendProfile(Player player, Zone zone) {
        // Silently skipped by Bukkit when the client did not register the channel
        player.sendPluginMessage(this, PROFILE_CHANNEL, LinkProtocol.encode(AudioDistancePlugin.serverProfileMessage(zone, isAdmin(player))));
    }

    /** Every player with the addon gets their zone's profile again (after the settings changed). */
    private void resendProfiles() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUniqueId())) {
                scheduling.onPlayer(player, () -> {
                    Zone zone = zoneOf(player);
                    zones.set(player.getUniqueId(), zone);
                    sendProfile(player, zone);
                });
            }
        }
    }

    private static Zone zoneOf(Player player) {
        if (AudioDistancePlugin.SERVER_SETTINGS.zones().isEmpty()) {
            return null;
        }
        return AudioDistancePlugin.SERVER_SETTINGS.zoneOf(info(player));
    }

    private void sendNearby(Player player) {
        String text = AudioDistancePlugin.nearbyMessage(player, AudioDistanceBukkit::visible);
        if (text != null) {
            player.sendPluginMessage(this, NEARBY_CHANNEL, LinkProtocol.encode(text));
        }
    }

    /** Players hidden from the viewer (vanish plugins) are never listed; spectators only to spectators. */
    private static boolean visible(Object viewer, Object other) {
        Player v = (Player) viewer;
        Player o = (Player) other;
        return v.canSee(o) && (o.getGameMode() != GameMode.SPECTATOR || v.getGameMode() == GameMode.SPECTATOR);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ServerHooks.joined(event.getPlayer().getUniqueId());
        startPlayerTick(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ServerHooks.left(event.getPlayer().getUniqueId());
        zones.forget(event.getPlayer().getUniqueId());
        infos.remove(event.getPlayer().getUniqueId());
        ZoneOutlines.hide(event.getPlayer().getUniqueId());
    }

    /** What /vcd needs from the server, for a command run by {@code sender} (a player or the console). */
    private AdminCommands.Context context(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        return new AdminCommands.Context() {
            @Override
            public String platform() {
                return getServer().getName();
            }

            @Override
            public int onlinePlayers() {
                return getServer().getOnlinePlayers().size();
            }

            @Override
            public int addonPlayers() {
                int n = 0;
                for (Player p : getServer().getOnlinePlayers()) {
                    if (AudioDistancePlugin.SERVER_WALLS.hasAddon(p.getUniqueId())) {
                        n++;
                    }
                }
                return n;
            }

            @Override
            public void resendProfiles() {
                AudioDistanceBukkit.this.resendProfiles();
            }

            @Override
            public void afterSettingsChange() {
                BlockAcoustics.clearCache();
            }

            @Override
            public UUID sender() {
                return player == null ? null : player.getUniqueId();
            }

            @Override
            public boolean allows(String permission) {
                return AudioDistanceBukkit.allows(sender, permission);
            }

            @Override
            public int[] targetBlock() {
                try {
                    org.bukkit.block.Block block = player == null ? null : player.getTargetBlockExact(LOOK_REACH);
                    return block == null ? null : new int[]{block.getX(), block.getY(), block.getZ()};
                } catch (Throwable t) {
                    return null;
                }
            }

            @Override
            public boolean teleport(String world, double x, double y, double z) {
                org.bukkit.World w = getServer().getWorld(world);
                if (player == null || w == null) {
                    return false;
                }
                Location to = new Location(w, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
                try {
                    // Paper and Folia: moves the player between regions safely
                    player.teleportAsync(to);
                } catch (NoSuchMethodError e) {
                    player.teleport(to);
                }
                return true;
            }

            @Override
            public java.util.Collection<String> worlds() {
                List<String> names = new ArrayList<>();
                for (org.bukkit.World w : getServer().getWorlds()) {
                    names.add(w.getName());
                }
                return names;
            }
        };
    }

    /**
     * {@code /vcd}: each part needs its own permission (vcd.status, vcd.settings, vcd.zone, vcd.debug)
     * or vcd.admin for all; operators have them all (see plugin.yml).
     */
    private final class AdminCommand implements TabExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (sender instanceof Player p) {
                // Commands like zone pos1 need where the admin stands right now
                AudioDistancePlugin.PLAYERS.update(info(p));
            }
            AdminCommands.Context context = context(sender);
            if (!AdminCommands.mayUseAny(context)) {
                sender.sendMessage(org.bukkit.ChatColor.RED + com.kasper.vcdistance.ServerText.get(
                        AudioDistancePlugin.SERVER_SETTINGS.languageFor(sender instanceof Player p ? info(p).language() : ""),
                        "no_permission", "/vcd", ADMIN_PERMISSION));
                return true;
            }
            for (CommandReply.Line line : AdminCommands.execute(String.join(" ", args), AudioDistancePlugin.SERVER_SETTINGS, context).lines()) {
                ReplyAdventure.send(sender, line);
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> out = new ArrayList<>();
            for (AdminCommands.Suggestion s : AdminCommands.suggestions(String.join(" ", args), AudioDistancePlugin.SERVER_SETTINGS,
                    context(sender))) {
                out.add(s.text());
            }
            return out;
        }
    }

    /** {@code /voice}: what any player sets for themselves (range mode, walls, volumes, the talking line). */
    private final class VoiceCommand implements TabExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("/voice is for players");
                return true;
            }
            PlayerCommands.Context context = new PlayerCommands.Context() {
                @Override
                public boolean allows(String permission) {
                    return p.hasPermission(permission);
                }

                @Override
                public boolean openMenu() {
                    try {
                        VoiceMenu.open(p);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                }
            };
            AudioDistancePlugin.PLAYERS.update(info(p));
            for (CommandReply.Line line : PlayerCommands.execute(p.getUniqueId(), info(p).language(), String.join(" ", args), context).lines()) {
                ReplyAdventure.send(p, line);
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return PlayerCommands.suggest(String.join(" ", args), sender::hasPermission);
        }
    }

    /** Simple Voice Chat plugin with only the server-side events (there is no client on Bukkit). */
    private static final class ServerPlugin implements VoicechatPlugin {

        @Override
        public String getPluginId() {
            return AudioDistancePlugin.MOD_ID;
        }

        @Override
        public void initialize(VoicechatApi api) {
            new AudioDistancePlugin().initialize(api);
        }

        @Override
        public void registerEvents(EventRegistration registration) {
            AudioDistancePlugin.registerServerEvents(registration);
        }
    }
}
