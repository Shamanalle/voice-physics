package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.CommandReply;
import com.kasper.vcdistance.PlayerCommands;
import com.kasper.vcdistance.PlayerPrefs;
import com.kasper.vcdistance.ServerText;
import com.kasper.vcdistance.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /voice menu}: the same choices as {@code /voice}, as a chest menu for players who would rather
 * click than type. Every click runs the ordinary command (so permissions, the walls lock and Undo work
 * exactly as they do in chat) and redraws the menu; a problem (no permission, walls locked) is told in chat.
 */
final class VoiceMenu implements InventoryHolder {

    private static final int SIZE = 27;
    private static final int QUIET = 10;
    private static final int NORMAL = 11;
    private static final int SHOUT = 12;
    private static final int WALLS = 14;
    private static final int HUD = 15;
    private static final int UNDO = 21;
    private static final int RESET = 23;

    private final UUID player;
    private final String lang;
    private final Inventory inventory;

    private VoiceMenu(Player p) {
        this.player = p.getUniqueId();
        this.lang = AudioDistancePlugin.SERVER_SETTINGS.languageFor(AudioDistanceBukkit.info(p).language());
        this.inventory = create();
    }

    @SuppressWarnings("deprecation")
    private Inventory create() {
        return Bukkit.createInventory(this, SIZE, ServerText.get(lang, "voice.status.title"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Opens the menu for {@code p}. Must be called on the player's own thread (a command, a click). */
    static void open(Player p) {
        VoiceMenu menu = new VoiceMenu(p);
        menu.draw(p);
        p.openInventory(menu.inventory);
    }

    private String t(String key, Object... args) {
        return ServerText.get(lang, key, args);
    }

    private void draw(Player p) {
        PlayerPrefs.Prefs prefs = AudioDistancePlugin.PLAYER_PREFS.get(player);
        boolean canShout = p.hasPermission(PlayerCommands.PERM_SHOUT);
        boolean wallsLocked = AudioDistancePlugin.SERVER_SETTINGS.wallsLocked();
        boolean hasAddon = AudioDistancePlugin.SERVER_WALLS.hasAddon(player);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(QUIET, modeItem(Material.FEATHER, PlayerPrefs.Mode.QUIET, prefs, true, PlayerPrefs.QUIET_FACTOR));
        inventory.setItem(NORMAL, modeItem(Material.PAPER, PlayerPrefs.Mode.NORMAL, prefs, true, 1.0));
        inventory.setItem(SHOUT, modeItem(Material.GOAT_HORN, PlayerPrefs.Mode.SHOUT, prefs, canShout, PlayerPrefs.SHOUT_FACTOR));

        Zone wallsZone = AudioDistancePlugin.SERVER_SETTINGS.wallsZoneOf(AudioDistancePlugin.PLAYERS.get(player));
        if (wallsLocked) {
            inventory.setItem(WALLS, toggle(Material.STONE_BRICKS, t("voice.status.walls_locked", onOff(true)), true, false));
        } else if (wallsZone != null) {
            boolean zoneWalls = AudioDistancePlugin.SERVER_SETTINGS.wallsApply(wallsZone, prefs.walls());
            inventory.setItem(WALLS, toggle(Material.STONE_BRICKS, t("voice.status.walls_zone", onOff(zoneWalls), wallsZone.name()), zoneWalls, false));
        } else {
            inventory.setItem(WALLS, toggle(Material.STONE_BRICKS, t("voice.status.walls", onOff(prefs.walls())), prefs.walls(), true));
        }
        if (hasAddon || !AudioDistancePlugin.SERVER_SETTINGS.isMonitorAllowed()) {
            inventory.setItem(HUD, item(Material.BARRIER, ChatColor.GRAY + t(hasAddon ? "voice.status.hud_addon" : "voice.status.hud_server_off")));
        } else {
            inventory.setItem(HUD, toggle(Material.SPYGLASS, t("voice.status.hud", onOff(prefs.hud())), prefs.hud(), true));
        }
        if (AudioDistancePlugin.PLAYER_PREFS.canUndo(player)) {
            inventory.setItem(UNDO, item(Material.ARROW, ChatColor.YELLOW + t("btn.undo")));
        }
        if (!prefs.isDefault()) {
            inventory.setItem(RESET, item(Material.TNT, ChatColor.RED + t("voice.btn.reset")));
        }
    }

    private String onOff(boolean on) {
        return t(on ? "on" : "off");
    }

    private ItemStack modeItem(Material material, PlayerPrefs.Mode mode, PlayerPrefs.Prefs prefs, boolean allowed, double factor) {
        boolean selected = prefs.mode() == mode;
        String id = mode.name().toLowerCase(Locale.ROOT);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + t("voice.menu.range", PlayerCommands.factorText(factor)));
        if (!allowed) {
            lore.add(ChatColor.RED + t("voice.mode.denied", PlayerCommands.PERM_SHOUT));
        } else if (selected) {
            lore.add(ChatColor.GREEN + t("voice.menu.selected"));
        } else {
            lore.add(ChatColor.DARK_GRAY + t("voice.menu.click"));
        }
        ChatColor color = !allowed ? ChatColor.DARK_GRAY : selected ? ChatColor.GREEN : ChatColor.WHITE;
        return item(material, color + t("voice.btn." + id), lore);
    }

    private ItemStack toggle(Material material, String name, boolean on, boolean changeable) {
        List<String> lore = new ArrayList<>();
        if (changeable) {
            lore.add(ChatColor.DARK_GRAY + t("voice.menu.click"));
        }
        return item(on ? material : Material.LIGHT_GRAY_STAINED_GLASS_PANE, (on ? ChatColor.GREEN : ChatColor.GRAY) + name, lore);
    }

    private static ItemStack item(Material material, String name) {
        return item(material, name, List.of());
    }

    @SuppressWarnings("deprecation")
    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** The command a click on {@code slot} stands for, or {@code null}. */
    private String commandFor(int slot) {
        PlayerPrefs.Prefs prefs = AudioDistancePlugin.PLAYER_PREFS.get(player);
        return switch (slot) {
            case QUIET -> "mode quiet";
            case NORMAL -> "mode normal";
            case SHOUT -> "mode shout";
            case WALLS -> "walls " + (prefs.walls() ? "off" : "on");
            case HUD -> "hud " + (prefs.hud() ? "off" : "on");
            case UNDO -> "undo";
            case RESET -> "reset";
            default -> null;
        };
    }

    private void click(Player p, int slot) {
        String command = commandFor(slot);
        if (command == null) {
            return;
        }
        CommandReply reply = PlayerCommands.execute(player, AudioDistanceBukkit.info(p).language(), command, p::hasPermission);
        boolean problem = false;
        for (CommandReply.Line line : reply.lines()) {
            for (CommandReply.Span span : line.spans()) {
                if (span.style() == CommandReply.Style.ERROR || span.style() == CommandReply.Style.WARN) {
                    problem = true;
                }
            }
        }
        if (problem) {
            for (CommandReply.Line line : reply.lines()) {
                ReplyAdventure.send(p, line);
            }
        }
        draw(p);
    }

    /** Clicks in the menu: nothing can be taken out, and a click on a button runs its command. */
    static final class Events implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (event.getInventory().getHolder() instanceof VoiceMenu menu) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player p && event.getClickedInventory() == event.getInventory()) {
                    menu.click(p, event.getSlot());
                }
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof VoiceMenu) {
                event.setCancelled(true);
            }
        }
    }
}
