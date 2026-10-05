# Voice Physics

**English** · [Русский](README.ru.md)

An addon for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that makes proximity voice chat sound real: voices fade with distance, get muffled behind walls (occlusion), come round corners through doorways and echo in caves. Servers get sound zones, game rules and wall muffling even for players without the addon.

[![Modrinth](https://img.shields.io/badge/Modrinth-download-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/project/u8mD6TL9)
[![CurseForge](https://img.shields.io/badge/CurseForge-download-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/projects/1712556)
[![Release](https://img.shields.io/github/v/release/Shamanalle/voice-physics?logo=github&color=brightgreen)](https://github.com/Shamanalle/voice-physics/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5%20%E2%80%93%2026.3-blue.svg)](#versions-and-files)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

| Distance | Walls | Effects | HUD |
|---|---|---|---|
| ![Distance tab: curve graph and presets](docs/images/ui-distance.png) | ![Walls tab: strength and materials](docs/images/ui-walls.png) | ![Effects tab: echo, water, rain](docs/images/ui-effects.png) | ![HUD tab: who is talking and who hears you](docs/images/ui-hud.png) |

| Server | Change log | Settings locked by the server |
|---|---|---|
| ![Server tab for admins](docs/images/ui-server.png) | ![The server's change log on its own screen](docs/images/ui-log.png) | ![Settings locked by the server](docs/images/ui-locked.png) |

## Contents

- [Quick start](#quick-start)
- [For players](#for-players)
- [For servers](#for-servers)
- [What works where](#what-works-where)
- [Versions and files](#versions-and-files)
- [Compatibility](#compatibility)
- [Commands](#commands)
- [Settings files](#settings-files)
- [Questions and problems](#questions-and-problems)
- [Building](#building)

## Quick start

**Which file:** Fabric, Quilt, Forge and NeoForge get the full addon, with a file for each range of Minecraft versions. Paper, Purpur, Folia and Spigot servers get the plugin. The exact file for your version is in [Versions and files](#versions-and-files).

**Player**
1. Install [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and, on Fabric, [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put the addon's `.jar` into `.minecraft/mods/`.
3. In game press `V` → **Voice Physics…**, or type `/voicephysics` (also in Mod Menu, or on your own key in *Controls*). Changes are heard at once; *Cancel* undoes them.

**Server**
1. Fabric, Forge or NeoForge: put the same `.jar` into `mods/`. Paper, Purpur, Folia or Spigot: put `voice-physics-bukkit-*.jar` into `plugins/` (on CurseForge the plugin has [its own page](https://www.curseforge.com/projects/1715914)). Simple Voice Chat must be installed.
2. Start the server once. Walls for players without the addon are already on.
3. Set up the rest with `/vcd` in game, on the *Server* tab of the settings screen, or in the [settings file](#server-file). The file is re-read without a restart.

## For players

These need the addon on your client and work on any server with Simple Voice Chat.

### Distance
- Three fade curves: like Simple Voice Chat (straight), realistic, or steep. Every curve fades out at the edge of the range instead of cutting off.
- You set how far voices stay at full volume, how fast they fade, how loud they stay at the edge, and how fast whispers fade.
- A live graph shows the volume at every distance and the players you hear right now. *Listen* plays a voice walking away along the curve.
- Presets, which adapt to each server's voice range:

  | Preset | For | Full volume up to |
  |---|---|---|
  | Vanilla | Exactly like Simple Voice Chat | half the range |
  | Realistic | Everyday play | about 12 blocks |
  | Clear | Events: everyone stays understandable | about 24 blocks |
  | Stealth | Hide-and-seek, horror | about 7 blocks |

### Walls
- A voice behind a wall is quieter and duller; the thicker the wall, the more.
- Materials differ: wool and metal block more than stone, glass and leaves less. Open doors and trapdoors count a tenth of a closed one; slabs, fences and carpets let sound past by their shape. Iron and copper doors count as metal. Every material's weight can be changed on the *Walls* tab, under *Materials*.
- Players side by side in a narrow tunnel hear each other clearly.
- Blocks from other mods: the addon guesses a material from the block's name and sound. When it is wrong, pick the block you look at or hold on the *Walls* tab, or let a resource pack or mod list it in a [data file](#block-materials-for-modders).

### Round corners
- A voice from the next room comes through the doorway or window, from its direction and at the length of the way round.
- The sharper the turn, the duller the voice. The direction glides as you walk past a doorway.
- The HUD shows *round a corner* for such voices.

### Echo, water and rain
- The echo depends on the place: a stone room rings briefly, a cave or hall for 2–3 seconds, a wooden house briefly and softly. Forests, fields and wool rooms have none.
- Near cliffs and in canyons the voice comes back a moment later.
- The speaker's surroundings count too: a friend in a cave echoes even when you are outside. A voice right next to you stays clear.
- The echo changes smoothly, over about a second and a half, as you walk from a field into a cave or out of a hall.
- *By place* (on by default): the dimension, biome and depth tune the sound a little. Deep caves and the deep dark ring longer, the Nether's smoke dulls far voices, the End's open void has no echo, snow and dense jungle swallow sound. It is mild and comes on top of what is measured; a switch on the *Effects* tab turns it off.
- Under water voices are dull and quieter.
- Rain and thunder cover far voices under the open sky.
- The *Effects* tab has a switch for each, sliders for how strong the echo, water and rain are, and shows what kind of place you are in.

### HUD
- A small panel in a screen corner: who is talking, how far away, from which side, and whether they whisper, are behind a wall or round a corner.
- While you talk: how many players hear you and how many cannot (no voice chat, sound off). In a Simple Voice Chat group it counts the group (needs the addon on the server).
- Modes: off, while someone talks, always. Size (50–200%), corner, background, a compact one-line mode, with a preview on the *HUD* tab. A key cycles the modes.
- **Easier to see and follow:** *High contrast* draws the HUD on a solid dark panel with bright text. *Direction marks* add a subtitle-style line for someone talking off to the side, such as `◀ Sam 3m`, so you can tell where a voice comes from without stereo hearing.

### Monitor and radar
- Everyone within voice range, talking or not: distance, direction, how loud they reach you and how much the walls take.
- Who has no voice chat, has it disconnected, turned the sound off or is in a group.
- A radar view shows the same from above.
- Invisible players, spectators and players hidden by vanish plugins are not shown.

### Also
- **Profile codes:** copy all your sound settings as one line and send it to a friend, who pastes it.
- **Colorblind colors** for the HUD, monitor and radar, on the *HUD* tab.
- **Smooth on busy machines:** the addon shares one block lookup between all its measurements and slows its own measuring down, step by step, when the game gets busy. `/voicephysics report` shows the load.
- **A bug report in one click:** `/voicephysics report` gives a short text (versions, settings, load, the last problems; no names) with a *Copy* button.
- **Seven languages:** English, Russian, Ukrainian, German, Spanish, Brazilian Portuguese, Chinese (Simplified).

## For servers

Available in the full Fabric, Forge and NeoForge mods and as a plugin for Paper, Purpur, Folia, Spigot and Bukkit.

- **Walls for everyone.** Players without the addon also hear voices muffled through walls. Load is capped: above 24 voices at once (adjustable) the rest pass unfiltered, and on any error the original audio is sent, so voice chat never goes silent.
- **Sound zones.** Draw a box in game with `/vcd zone`, use a whole world, a WorldGuard region on Paper, or the claims of a player or party with [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) on Fabric, Forge and NeoForge. A zone can:
  - change the voice range: a stage ×2, a library ×0.4, or a range in blocks;
  - be *isolated*: no voice gets in or out;
  - set its own wall strength, a constant echo (a cathedral) or none;
  - show a message on entering.

  Entering and leaving a zone is shown above the hotbar to every player, with or without the addon (`/vcd notices off` turns it off). `/vcd zone show <name>` draws a box's borders with particles.
- **Game rules.** Sneaking players carry less far. Dead players are silent until they respawn. Spectators are heard only by spectators. An item in hand, such as a goat horn, works as a megaphone. You choose which rules also apply inside Simple Voice Chat groups.
- **One sound for everyone.** Offer the server's sound profile with a button, or enforce it while players are on the server (fair PvP and events). Lock all of it or only some parts: curve, walls, materials, effects. Locked walls lock each block's percentage too.
- **No seeing through walls.** Turn off the monitor, the radar and nearby players in the HUD.
- **Require the addon.** Players with Simple Voice Chat but without the addon can get a download link once, on every join, or be kicked. Players without voice chat are never affected.
- **Controls for players without the mod (Paper, Folia).** `/voice` lets anyone choose how loud they talk (quiet, normal, shout), turn wall muffling off for themselves, set how loud each player is to them or ignore one, and show who is talking nearby above the hotbar. The choices are kept per player; shouting needs the `vcd.shout` permission (operators by default).
- **Realism for players without the addon (off by default).** `/vcd effects on` makes the server dull voices under water, cover far ones in rain and thunder, and echo the room a speaker or listener is in, the same physics the addon does at home. `/vcd effects air on` also dulls voices a little as they near the edge of their range. Water, rain and air work on every platform; measuring a room for the echo needs block access and is Paper only, while a zone with its own echo works everywhere. The strengths are the profile's (`/vcd effects water|weather|echo <0-150>`). It costs some load, so it is off until you turn it on.
- **Mute.** `/vcd mute <player> [time] [reason]` silences a player's voice for everyone, for a time (`10m`, `2h`, `1d`, `perm`) or until `/vcd unmute`. `/vcd mutes` lists who is muted and why, with an *Unmute* button. Mutes survive restarts and appear in the change log.
- **Extras (Paper, off by default, not yet tried in a live game).** `/vcd extras` lists them with a switch each, and the *Server* tab of the settings screen shows the same switches (plus the eavesdrop factor) when the server runs the plugin; turn one on on a test server first, `/vcd undo` takes it back. A **radio** (`/voice radio <1-9999>`: players on one frequency hear each other at any distance, optionally only while holding an item), **loudspeakers** (`/vcd speaker add`: whoever talks at one is heard from it around it), an **eavesdrop item** (a spyglass makes walls sound thinner), the **sculk reaction** (a shout is a game event wardens and sculk sensors react to), **doorway sound** (behind a wall, the voice comes through the open door instead of the wall) and **integrations** (Towny towns and Lands lands as zones, a WorldGuard `vcd-zone` flag, LuckPerms contexts `vcd:mode`, `vcd:walls`, `vcd:zone`).
- **PlaceholderAPI (Paper).** With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) installed, scoreboards, tab lists, holograms and chat formats can show [`%vcd_...%` placeholders](#placeholders): who talks, the mode, the range, whether someone is muted.
- **Statistics (plugin).** The plugin sends anonymous usage numbers to [bStats](https://bstats.org) (server size and versions, which options are in use; no names, addresses or chat), so work goes where people use it. Turn it off with `metrics=false` in the server file, or for all plugins in `plugins/bStats/config.yml`.
- **Admin tools.** A *Server* tab in the settings screen (the effects for players without the addon and the plugin's extras switches are there too; speakers, items and mutes stay commands), [`/vcd`](#commands) with clickable replies, examples in its help, undo and separate permissions, a log of who changed what, on a screen of its own (*Server* tab → *Full log*, or `/voicephysics log`) and as `/vcd log`, `/vcd debug <player>` to see whom a player hears and why not, `/vcd report` for a bug report to copy, and messages in each player's own language (all texts can be edited).

## What works where

Server columns: *mod* is the Fabric, Forge or NeoForge addon on the server, *plugin* is the Paper, Purpur, Folia or Spigot plugin. *Both* is the addon on the client and either of them on the server.

| | Client only | Server only: mod | Server only: plugin | Both |
|---|:---:|:---:|:---:|:---:|
| Distance curve, presets | ✅ | — | — | ✅ + server profile |
| Walls | ✅ for you | ✅ for players without the addon | ✅ for players without the addon | ✅ |
| Round corners | ✅ | — | doorway sound (Paper extra) | ✅ |
| Echo of the room | ✅ | — | Paper, if turned on | ✅ |
| Water, rain | ✅ | if turned on | if turned on | ✅ |
| Echo by place (caves, Nether, End) | ✅ | — | — | ✅ |
| HUD, monitor, radar | ✅ | — | — | ✅ + voice chat state of every player |
| Zones: range, walls, isolation, names above the hotbar | — | ✅ + Open Parties and Claims | ✅ + WorldGuard, Towny, Lands | ✅ |
| Zones: echo | — | if effects are on | if effects are on | ✅ |
| Game rules, addon requirement, mute, `/vcd`, change log, `/vcd report` | — | ✅ | ✅ | ✅ |
| `/voice`, PlaceholderAPI | — | — | Paper, Folia | Paper, Folia |
| Extras: radio, loudspeakers, eavesdrop, sculk, LuckPerms | — | — | Paper | Paper |
| Server tab, locked settings, monitor off | — | — | — | ✅ |
| Exact whisper range on the graph | approximate | — | — | ✅ |

With the addon on both sides the client muffles walls itself and the server skips those players, so nothing is muffled twice.

## Versions and files

| Loader | Minecraft | File | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.16.5 | `voice-physics-fabric-2.10.4+mc1.16.5.jar` | 8+ | 2.4.0+ |
| **Fabric / Quilt** | 1.17.1 | `voice-physics-fabric-2.10.4+mc1.17.1.jar` | 16+ | 2.4.0+ |
| **Fabric / Quilt** | 1.18.2 | `voice-physics-fabric-2.10.4+mc1.18.2.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.19.2 | `voice-physics-fabric-2.10.4+mc1.19.2.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.19.4 | `voice-physics-fabric-2.10.4+mc1.19.4.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voice-physics-fabric-2.10.4+mc1.20.1.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.20.2 – 1.20.4 | `voice-physics-fabric-2.10.4+mc1.20.2-1.20.4.jar` | 17+ | 2.4.0+ |
| **Fabric / Quilt** | 1.20.5 – 1.20.6 | `voice-physics-fabric-2.10.4+mc1.20.5-1.20.6.jar` | 21+ | 2.5.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voice-physics-fabric-2.10.4+mc1.21.x.jar` | 21+ | 2.5.0+ |
| **Fabric / Quilt** | 26.1 – 26.3 | `voice-physics-fabric-2.10.4+mc26.x.jar` | 25+ | 2.6.0+ |
| **Forge** | 1.16.5 | `voice-physics-forge-2.10.4+mc1.16.5.jar` | 8 – 16 | 2.4.0+ |
| **Forge** | 1.17.1 | `voice-physics-forge-2.10.4+mc1.17.1.jar` | 16+ | 2.4.0+ |
| **Forge** | 1.18.2 | `voice-physics-forge-2.10.4+mc1.18.2.jar` | 17+ | 2.4.0+ |
| **Forge** | 1.19.2 | `voice-physics-forge-2.10.4+mc1.19.2.jar` | 17+ | 2.4.0+ |
| **Forge** | 1.19.4 | `voice-physics-forge-2.10.4+mc1.19.4.jar` | 17+ | 2.4.0+ |
| **Forge** | 1.20 – 1.20.1 | `voice-physics-forge-2.10.4+mc1.20.1.jar` | 17+ | 2.4.0+ |
| **Forge** | 1.20.2 – 1.20.4 | `voice-physics-forge-2.10.4+mc1.20.2-1.20.4.jar` | 17+ | 2.4.0+ |
| **Forge** | 1.20.6 | `voice-physics-forge-2.10.4+mc1.20.6.jar` | 21+ | 2.5.0+ |
| **Forge** | 1.21 – 1.21.1 | `voice-physics-forge-2.10.4+mc1.21-1.21.1.jar` | 21+ | 2.5.0+ |
| **Forge** | 1.21.3 – 1.21.5 | `voice-physics-forge-2.10.4+mc1.21.3-1.21.5.jar` | 21+ | 2.5.0+ |
| **Forge** | 1.21.6 – 1.21.8 | `voice-physics-forge-2.10.4+mc1.21.6-1.21.8.jar` | 21+ | 2.5.0+ |
| **Forge** | 1.21.9 – 1.21.10 | `voice-physics-forge-2.10.4+mc1.21.9-1.21.10.jar` | 21+ | 2.5.0+ |
| **Forge** | 1.21.11 | `voice-physics-forge-2.10.4+mc1.21.11.jar` | 21+ | 2.5.0+ |
| **Forge** | 26.1 – 26.3 | `voice-physics-forge-2.10.4+mc26.x.jar` | 25+ | 2.6.0+ |
| **NeoForge** | 1.20.1 | `voice-physics-forge-2.10.4+mc1.20.1.jar` | 17+ | 2.4.0+ |
| **NeoForge** | 1.20.2 – 1.20.3 | `voice-physics-neoforge-2.10.4+mc1.20.2-1.20.3.jar` | 17+ | 2.4.0+ |
| **NeoForge** | 1.20.4 | `voice-physics-neoforge-2.10.4+mc1.20.4.jar` | 17+ | 2.4.0+ |
| **NeoForge** | 1.20.5 – 1.20.6 | `voice-physics-neoforge-2.10.4+mc1.20.5-1.20.6.jar` | 21+ | 2.5.0+ |
| **NeoForge** | 1.21 – 1.21.1 | `voice-physics-neoforge-2.10.4+mc1.21-1.21.1.jar` | 21+ | 2.5.0+ |
| **NeoForge** | 1.21.2 – 1.21.5 | `voice-physics-neoforge-2.10.4+mc1.21.2-1.21.5.jar` | 21+ | 2.5.0+ |
| **NeoForge** | 1.21.6 – 1.21.8 | `voice-physics-neoforge-2.10.4+mc1.21.6-1.21.8.jar` | 21+ | 2.5.0+ |
| **NeoForge** | 1.21.9 – 1.21.10 | `voice-physics-neoforge-2.10.4+mc1.21.9-1.21.10.jar` | 21+ | 2.5.0+ |
| **NeoForge** | 1.21.11 | `voice-physics-neoforge-2.10.4+mc1.21.11.jar` | 21+ | 2.5.0+ |
| **NeoForge** | 26.1 – 26.3 | `voice-physics-neoforge-2.10.4+mc26.x.jar` | 25+ | 2.6.0+ |
| **Paper / Purpur / Folia / Spigot / Bukkit** | 1.16.5 – 26.3 | `voice-physics-bukkit-2.10.4.jar` | 8+ (17+ on 1.18+) | Bukkit version |

- **Fabric**, **Forge** and **NeoForge** are the full addon, for the client and the server. Fabric needs [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) is optional. On Forge for 1.21.6 – 1.21.7 the voice HUD is off: that Forge cannot add it. NeoForge 1.20.1 is a fork of Forge 1.20.1 and takes the Forge file. The NeoForge 1.20.4 file needs NeoForge 20.4.80 or newer.
- **Older Minecraft** (1.16.5 – 1.19.4) has the same features as the newer files. Simple Voice Chat itself is still updated for 1.16.5, 1.18.2 and 1.19.2, while 1.17.1 and 1.19.4 stopped at its 2.5.12, which the addon works with. Forge 1.16.5 runs on Java 8 – 16, Fabric 1.16.5 on 8 and newer, Fabric 1.17.1 and Forge 1.17.1 on 16 and newer. Minecraft 1.19.3 is not built.
- **The plugin** is the server side only. Players can join with the addon, without it, or without mods at all.

## Compatibility

- **Sound Physics Remastered:** when it is installed, the addon leaves walls, echo and water to it by default, so nothing is applied twice; rain still works. To hear voices through the addon instead, press *Use ours* in the note on the *Walls* or *Effects* tab (or set `over_sound_physics=true` in the [client file](#client-file)). Then the addon takes Sound Physics Remastered's filters off voices and applies its own walls, echo and water; all other game sounds stay with Sound Physics Remastered. The choice is yours and is never locked by a server.
- **Simple Voice Chat groups:** a group hears its members anywhere, so walls and range do not apply inside it. Game rules apply only where the server turns them on.
- **Vanish plugins** (Paper): hidden players stay hidden in the monitor too.

## Commands

### For players: `/voicephysics`

Works on any server, with or without the addon there.

| Command | What it does |
|---|---|
| `/voicephysics` | Open the settings |
| `/voicephysics distance\|walls\|effects\|hud\|monitor\|server` | Open the settings on that tab |
| `/voicephysics preset vanilla\|realistic\|clear\|stealth` | Your sound preset |
| `/voicephysics hud off\|talking\|always`, `hud compact on\|off` | The voice HUD |
| `/voicephysics code` | Your profile code, with a *Copy* button |
| `/voicephysics code <code>` | Load a profile code |
| `/voicephysics reset curve\|walls\|materials\|effects\|hud\|all` | Back to the defaults |
| `/voicephysics status` | What this server does to your sound: its profile, what it locks, your zone, the voice range |
| `/voicephysics report` | A bug report to copy: versions, your settings, load, the last problems (no names) |
| `/voicephysics log` | The server's change log on its own screen (admins): pages, one player's changes, undo, copy |
| `/voicephysics help` | These commands, clickable |

Parts the server locks cannot be changed from here either.

### For players without the mod: `/voice` (Paper, Folia)

| Command | What it does |
|---|---|
| `/voice` or `/voice status` | Your choices as clickable buttons: each value changes on a click, with an *Undo* |
| `/voice mode quiet\|normal\|shout` | How far your voice carries: half, normal, double (`shout` needs `vcd.shout`) |
| `/voice walls on\|off` | Whether walls muffle the voices you hear (not where the server locks walls or a zone sets its own: there the server decides) |
| `/voice volume <player> <0-100>` | How loud that player is to you (not for players with the addon: their volume is in the voice chat menu) |
| `/voice ignore <player>`, `/voice unignore <player>` | Stop hearing a player, and undo it (works for everyone) |
| `/voice hud on\|off` | The names and distances of players talking near you, above the hotbar (players without the addon) |
| `/voice menu` | The same choices as an inventory menu |
| `/voice radio [1-9999\|off]` | Tune your radio: players on the same frequency hear each other at any distance (when the server has turned the radio on; with `radio_item` set, hold that item) |
| `/voice undo` | Take back your last change |
| `/voice reset` | Back to the defaults |

Everyone may use `/voice` (permission `vcd.player`); each part has its own permission under it (`vcd.player.mode`, `.walls`, `.hud`, `.volume`, `.menu`, `.radio`), so a server can take one away. The choices are saved in `vc-audio-distance-players.properties` next to the server settings.

### Placeholders

With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) on Paper, these work in scoreboards, tab lists, holograms and chat formats. Nothing else is needed: the plugin adds them by itself.

| Placeholder | Shows |
|---|---|
| `%vcd_mode%` | The player's `/voice` mode: `quiet`, `normal` or `shout` |
| `%vcd_mode_name%` | The same, as a word in the player's language |
| `%vcd_talking%` | `true` while the player is speaking (never for a muted player) |
| `%vcd_muted%` | `true` when the player is muted |
| `%vcd_mute_left%` | How long the mute lasts (`1h`, `perm`), or empty |
| `%vcd_mute_reason%` | Why the player is muted, or empty |
| `%vcd_range%` | How far the player's voice carries now, in blocks (mode, zone, sneaking and megaphone counted) |
| `%vcd_whisper_range%` | The same for a whisper |
| `%vcd_walls%` | `true` when walls muffle the voices this player hears |
| `%vcd_zone%` | The name of the sound zone the player is in, or empty |
| `%vcd_addon%` | `true` when the player has the addon |
| `%vcd_talking_near%` | Who is talking near the player and can be heard, nearest first, for example `» Bob 4m, Anna 10m`; empty when the server hides this (`allow_monitor=false`) or the people are hidden by a vanish plugin |
| `%vcd_muted_count%` | How many players are muted (the same for everyone) |

A name PlaceholderAPI does not know stays as typed.

### For servers: `/vcd`

Replies are coloured and clickable: values in `/vcd status` put the command that changes them in the chat box, zones in `/vcd zones` have *Info*, *Show* and *Go there* buttons, and every change has an *Undo* button. Tab completion offers zone, world and player names and the values each setting takes. Changes are saved to the settings file and reach players at once.

| Command | What it does |
|---|---|
| `/vcd` or `/vcd status` | Version, voice range, walls, players with the addon, profile, zones |
| `/vcd help [command]` | Every command, or one with examples to click |
| `/vcd undo` | Take back the last change (up to 10) |
| `/vcd log [page] [player]` | Who changed the settings and when, newest first, ten to a page; a name shows only that player's changes (also kept in `vc-audio-distance-changes.log` next to the settings file) |
| `/vcd block add\|remove\|list\|clear` | Blocks and block tags (`create:andesite_casing`, `#c:glass_blocks`) that count as a material of their own, for everyone on the server; players can also add their own on the *Walls* tab |
| `/vcd reload` | Re-read the settings file |
| `/vcd profile off\|suggest\|enforce` | How the server profile is offered |
| `/vcd preset vanilla\|realistic\|clear\|stealth\|custom` | The server's sound |
| `/vcd preset export` / `import <code>` | The server's profile as a profile code |
| `/vcd walls 0-100\|off` | Wall strength for everyone, in % |
| `/vcd serverwalls on\|off` | Walls for players without the addon |
| `/vcd effects [on\|off]` | The server's water, rain and echo for players without the addon (off by default); with no value: what is on, with buttons |
| `/vcd effects air on\|off` | Voices dull a little as they near the edge of their range (players without the addon) |
| `/vcd effects water\|weather\|echo <0-150>\|off` | How strong each is; these are the server profile's values, so players with the addon get them too |
| `/vcd lock all\|none\|curve,walls,materials,effects` | What players cannot change while the profile is enforced |
| `/vcd monitor on\|off` | Monitor, radar and nearby players in the HUD |
| `/vcd notices on\|off` | A zone's name above the hotbar when players enter or leave it |
| `/vcd zones [page]` | Every zone, with buttons |
| `/vcd zone pos1\|pos2 [x y z \| ~ ~ ~ \| look]` | A corner of a box: where you stand, at coordinates, or the block you look at. The selection is shown with particles |
| `/vcd zone create <name> [radius]` | A box from the two corners, or around you |
| `/vcd zone info [name]` | A zone's settings; click one to change it. Without a name: the zone you are in |
| `/vcd zone set <name\|claim:player> <setting> [value\|default]` | `mode`, `preset`, `voice_range`, `whisper_range`, `range_multiplier`, `walls`, `echo`, `isolated`, `message`, `priority`. Without a value: the current one and the choices |
| `/vcd zone show <name>\|off` | Draw a box's borders with particles for 30 seconds (only you see them) |
| `/vcd zone tp <name>` | Go to the middle of a box |
| `/vcd zone rename <name> <new name>` | Rename a box; its settings stay |
| `/vcd zone delete <name>` | Remove a zone (asks first; `/vcd undo` brings it back) |
| `/vcd rule sneak 0.1-1\|dead on\|off\|spectators on\|off\|megaphone <item>\|megaphone_range 1-10` | Game rules |
| `/vcd group dead\|spectators\|zones\|open_range on\|off` | Rules inside Simple Voice Chat groups |
| `/vcd require off\|suggest\|warn\|kick [version]` | Addon requirement |
| `/vcd mute <player> [time] [reason]` | Nobody hears the player for `30s`, `10m`, `2h`, `1d`, `1w` or `perm` (no time: until unmuted); muting again changes the time and reason |
| `/vcd unmute <player>` | Let the player be heard again |
| `/vcd mutes` | Who is muted, for how long and why, each with an *Unmute* button |
| `/vcd extras [name on\|off]` | The Paper plugin's extras and their switches: `radio`, `speakers`, `eavesdrop`, `sculk`, `doorway`, `integrations` (all off until turned on; not yet tried in a live game) |
| `/vcd radio [item <id>\|none]` | The radio: state, who is on the air on which frequency, the item players must hold |
| `/vcd speaker [list]`, `add <name> [radius] [pickup]`, `tp <name>`, `remove <name>` | Loudspeakers: whoever talks within `pickup` blocks (0.5 - 16) of one is heard from it by everyone within `radius` blocks (1 - 256) |
| `/vcd eavesdrop [item <id>\|none\|factor <0.05-1>]` | The eavesdrop item (default `minecraft:spyglass`) and how much of the walls' muffling is left for its holder (default 0.3) |
| `/vcd debug [player]` | Whom a player hears, who hears them, and why not (without a name: you) |
| `/vcd report` | A bug report to copy: versions, settings that change how voices sound, load, the last problems (no player or zone names) |

**Permissions.** The console may do everything. On Paper each part of `/vcd` has its own permission; operators have all of them:

| Permission | Allows |
|---|---|
| `vcd.status` | `status`, `help`, `zones`, `zone info`, `effects` (looking) |
| `vcd.settings` | `profile`, `preset`, `walls`, `serverwalls`, `effects` (changing), `extras`, `radio`, `eavesdrop`, `lock`, `monitor`, `notices`, `rule`, `group`, `require`, `block`, `reload`, `undo`, `log` |
| `vcd.zone` | Making, changing, showing and deleting zones |
| `vcd.debug` | `debug`, `report` |
| `vcd.mute` | `mute`, `unmute`, `mutes` |
| `vcd.speaker` | `speaker add`, `remove`, `tp` |
| `vcd.admin` | All of the above |

On Fabric, Forge and NeoForge `/vcd` is for operators (level 2+). With LuckPerms or another mod that uses fabric-permissions-api, Fabric checks the same permissions. The *Server* tab shows to anyone who may use any part of `/vcd`.

## Settings files

Everything here can also be set in game. Every key in the files has a comment in English and Russian.

### Client file

`config/vc-audio-distance.properties`

<details>
<summary>All client settings</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `distance_model` | `linear` / `realistic_inverse` / `exponential` | `linear` | Shape of the curve |
| `attenuation_factor` | 0 – 1 | 1.0 | How fast voices fade |
| `openal_reference_ratio` | 0.05 – 1 | 0.5 | Share of the range heard at full volume |
| `min_volume_fraction` | 0 – 0.5 | 0.0 | Volume at the edge of the range |
| `whisper_multiplier` | 0.5 – 2 | 1.0 | How fast whispers fade, relative to voices |
| `occlusion_enabled` | true / false | true | Walls muffle voices |
| `occlusion_strength` | 0 – 1 | 0.6 | How strongly |
| `material.<id>` | 0 – 3 | see *Walls* → *Materials* | How much one block muffles; stone = 1 |
| `reverb_enabled` | true / false | true | Echo |
| `reverb_strength` | 0 – 1 | 0.6 | Echo strength |
| `underwater_enabled` | true / false | true | Dull voices under water |
| `underwater_strength` | 0 – 1.5 | 1 | How dull and quiet voices get under water |
| `weather_enabled` | true / false | true | Rain and thunder cover far voices |
| `weather_strength` | 0 – 1.5 | 1 | How much rain and thunder cover them |
| `diffraction_enabled` | true / false | true | Voices come round corners |
| `place_tuning` | true / false | true | The dimension, biome and depth tune echo and air a little |
| `over_sound_physics` | true / false | false | With Sound Physics Remastered installed: voices get this addon's walls, echo and water instead of its |
| `hud_mode` | `off` / `talking` / `always` | `talking` | When the HUD is shown |
| `hud_corner` | `top_left` / `top_right` / `bottom_left` / `bottom_right` | `top_right` | HUD corner |
| `hud_scale` | 0.5 – 2 | 1.0 | HUD size |
| `hud_background` | 0 – 1 | 0.55 | HUD background opacity |
| `hud_compact` | true / false | false | One HUD line for everyone talking |
| `hud_contrast` | true / false | false | The HUD on a solid dark panel with bright text |
| `hud_markers` | true / false | false | A subtitle-style line with an arrow for voices off to the side |
| `colorblind` | true / false | false | Colors for red-green color blindness |

</details>

### Server file

`config/vc-audio-distance-server.properties` on Fabric, Forge and NeoForge, `plugins/VoicePhysics/vc-audio-distance-server.properties` on Paper (before 2.2.0 `plugins/VoicechatAudioDistance/`, moved automatically). Changes apply within 2 seconds. The voice and whisper range itself is set in Simple Voice Chat (`max_voice_distance`, `whisper_distance`).

<details>
<summary>Walls</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `walls_strength` | 0 – 1 | 0.6 | How strongly walls muffle, for everyone; 0 turns walls off |
| `material.<id>` | 0 – 3 | stone 1.0, metal 1.3, earth 0.9, wood 0.7, wool 1.4, soft 1.2, glass 0.4, ice 0.7, door 0.6, leaves 0.15, thin 0.2, liquid 0.35, other 1.0 | How much one block muffles |
| `server_walls` | true / false | true | The server muffles walls for players without the addon |
| `server_walls_max_streams` | 0 – 512 | 24 | Most voices muffled at once; the rest pass unfiltered |

</details>

<details>
<summary>Server profile (players with the addon)</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `profile_mode` | `off` / `suggest` / `enforce` | `off` | Players keep their settings / get a button to apply the profile / use it while on the server |
| `profile_locked` | `all`, `none`, or any of `curve,walls,materials,effects` | `all` | With `enforce`: what players cannot change; `walls` brings `materials` along |
| `profile_preset` | `vanilla` / `realistic` / `clear` / `stealth` / `custom` | `custom` | The server's sound; `custom` uses the `profile.*` keys |
| `profile.distance_model`, `.attenuation_factor`, `.openal_reference_ratio`, `.min_volume_fraction`, `.whisper_multiplier` | as in the client file | client defaults | The custom curve |
| `profile.reverb_enabled`, `.reverb_strength`, `.underwater_enabled`, `.underwater_strength`, `.weather_enabled`, `.weather_strength`, `.diffraction_enabled`, `.place_tuning` | as in the client file | client defaults | Effects; always part of the profile, whatever the preset |
| `allow_monitor` | true / false | true | `false`: no monitor, radar or nearby players in the HUD |
| `zone_notices` | true / false | true | A zone's name above the hotbar on entering and leaving, for every player |

</details>

<details>
<summary>Zones</summary>

| Key | Values | What it does |
|---|---|---|
| `zone.<kind>.<name>.voice_range`, `.whisper_range` | 1 – 1000 blocks | Voice and whisper range here |
| `zone.<kind>.<name>.range_multiplier` | 0.05 – 10 | Range times this: 2 = stage, 0.4 = library |
| `zone.<kind>.<name>.isolated` | true / false | No voice gets in or out |
| `zone.<kind>.<name>.walls_strength` | 0 – 1 | Wall strength here; it decides for everyone in the zone (0 = no walls), whatever they chose with `/voice walls` |
| `zone.<kind>.<name>.echo` | `auto` / `off` / 0.1 – 1 | Measured as usual / none / this much everywhere here |
| `zone.<kind>.<name>.profile_mode`, `.profile_preset` | as above | The profile here |
| `zone.<kind>.<name>.enter_message` | text | Shown on entering |
| `zone.<kind>.<name>.priority` | whole number | Where zones overlap the highest wins (default 0) |
| `zone.box.<name>.world`, `.from`, `.to` | world, `x,y,z`, `x,y,z` | The box; `/vcd zone create` writes it |

`<kind>` is `world`, `box`, `region` (WorldGuard, Paper), `town` (Towny, Paper), `land` (Lands, Paper) or `claim` (Open Parties and Claims: `zone.claim.<player>` covers that player's claims and, for a party leader, the whole party's; `zone.claim.server` the server's own claims). A world is its folder name on Paper (`world_nether`) and its dimension on Fabric, Forge and NeoForge (`the_nether`). On equal priority a region wins, then the smaller box. Anything a zone does not set comes from the rest of the file.

```properties
# A stage heard twice as far, and a soundproof booth
zone.box.stage.world=world
zone.box.stage.from=0,60,0
zone.box.stage.to=30,80,20
zone.box.stage.range_multiplier=2
zone.box.stage.enter_message=On stage: everyone hears you
zone.box.booth.world=world
zone.box.booth.from=40,60,0
zone.box.booth.to=44,64,4
zone.box.booth.isolated=true
```

</details>

<details>
<summary>Realism for players without the addon, mutes and statistics</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `server_effects` | true / false | false | The server dulls voices under water, covers far ones in rain and thunder and echoes the room (echo: Paper); strengths come from `profile.underwater_*`, `profile.weather_*` and `profile.reverb_*` |
| `server_air` | true / false | false | Voices dull a little as they near the edge of their range |
| `mute.<uuid>` | `end\|name\|muted by\|reason` | none | A muted player; `end` is the time in milliseconds since 1970, 0 until unmuted. `/vcd mute` writes these; expired ones are dropped |
| `metrics` | true / false | true | Plugin only: anonymous usage numbers to [bStats](https://bstats.org) |

</details>

<details>
<summary>Extras of the Paper plugin (off by default)</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `server_radio` | true / false | false | Radio: players on the same frequency (`/voice radio`) hear each other at any distance |
| `radio_item` | item id or empty | empty | Item a player must hold to use the radio, e.g. `minecraft:clock`; empty: none needed |
| `server_speakers` | true / false | false | Loudspeakers placed with `/vcd speaker add` repeat voices around them |
| `speaker.<name>` | `world\|x\|y\|z\|pickup\|radius` | none | A loudspeaker; `/vcd speaker add` writes these |
| `server_eavesdrop` | true / false | false | The eavesdrop item makes walls thinner for its holder |
| `eavesdrop_item` | item id or empty | `minecraft:spyglass` | The item; empty: nobody can |
| `eavesdrop_factor` | 0.05 – 1 | 0.3 | What is left of the walls' muffling for the holder |
| `server_sculk` | true / false | false | A shout or a megaphone voice is a game event for sculk sensors and wardens (at most once per 3 s per player) |
| `server_doorway` | true / false | false | Players without the addon hear a voice behind a wall from the open door it comes through |
| `server_integrations` | true / false | false | `zone.town.*` (Towny), `zone.land.*` (Lands), the WorldGuard flag `vcd-zone` and the LuckPerms contexts `vcd:mode`, `vcd:walls`, `vcd:zone` |

All of these are new and have not been tried in a live game yet: turn one on on a test server first. The WorldGuard flag `vcd-zone` takes the name of a zone in this file (`/region flag <region> vcd-zone quiet-library`).

</details>

<details>
<summary>Game rules and groups</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `sneak_range_multiplier` | 0.1 – 1 | 1 | Voice range while sneaking, times this |
| `dead_players_silent` | true / false | false | Dead players are silent until they respawn |
| `spectators_hear_only_spectators` | true / false | false | Spectators are heard only by spectators |
| `megaphone_item` | item id or empty | empty | Item that works as a megaphone, e.g. `minecraft:goat_horn` |
| `megaphone_multiplier` | 1 – 10 | 2.5 | Voice range with the megaphone, times this |
| `group_dead_silent` | true / false | false | Dead players are silent in their group too |
| `group_spectators_apart` | true / false | false | Spectators in a group are heard only by its spectators |
| `group_isolated_zones` | true / false | false | Isolated zones cut group voices too |
| `open_group_range` | true / false | true | In open groups, zone range, sneaking and the megaphone apply to the voice nearby players hear |

</details>

<details>
<summary>Addon requirement and messages</summary>

| Key | Values | Default | What it does |
|---|---|---|---|
| `require_addon` | `off` / `suggest` / `warn` / `kick` | `off` | Players without the addon: nothing / one message per server start / a message on every join / disconnect |
| `min_addon_version` | version or empty | empty | Oldest addon version that counts |
| `addon_download_url` | link | GitHub releases | Where the message sends players |
| `messages_language` | `auto` / `en_us` / `ru_ru` / `uk_ua` / `de_de` / `es_es` / `pt_br` / `zh_cn` | `auto` | Language of `/vcd` replies and player messages; `auto` = each player's game language |

The texts are in `vc-audio-distance-lang/<language>.json` next to the settings file. Edit any line, or add a file such as `fr_fr.json` for a new language; missing lines fall back to the built-in ones.

</details>

### Block materials for modders

A resource pack, a data pack or a mod's own jar can tell the addon what a block is made of, for blocks the automatic guess gets wrong. Nothing is needed for vanilla blocks.

- `assets/<namespace>/voice_physics/materials.json` is read by the client (resource packs and mod jars);
- `data/<namespace>/voice_physics/materials.json` is read by the server that measures the walls (data packs, mod jars, the world's `datapacks/`).

```json
{
  "replace": false,
  "blocks": {
    "create:andesite_casing": "metal",
    "#minecraft:beds": "wool"
  }
}
```

A key is a block id or a block tag (`#namespace:tag`); a value is one of the materials on the *Walls* tab: `stone`, `metal`, `earth`, `wood`, `wool`, `soft`, `glass`, `ice`, `door`, `leaves`, `thin`, `liquid`, `other`. A pack higher in the list wins for the same block, and `"replace": true` drops what lower packs said. Mistakes (an unknown material, broken JSON) skip that entry, are written to the log (the first five) and show under *Problems* in `/voicephysics report` and `/vcd report`; up to 4096 rules are read in all.

Who wins when several say something about one block: the server's own rules (`/vcd block`) and the player's (*Walls* tab), then data packs, then resource packs and mod jars, then the automatic guess. Walls locked by a server profile lock each block's percentage as well.

## Questions and problems

**I hear no walls muffling.**
- Check that *Walls* is on and above 0% on the *Walls* tab. `/voicephysics status` says when the server locks walls for everyone or turns them off.
- Voices inside one Simple Voice Chat group ignore walls and range on purpose.
- With [Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) installed, the addon leaves walls, echo and water to it, unless you press *Use ours* on the *Walls* tab.
- Without the addon, the server muffles walls only with `server_walls` on, and only up to 24 voices at once (`server_walls_max_streams`); `/voice walls` may be off for that player.

**A setting is greyed out or changes back.** The server has enforced its profile and locked that part. `/voicephysics status` lists what is locked; it is fixed on the server with `/vcd lock`.

**There is no HUD.** `/voicephysics hud talking` turns it on. The server can hide the monitor and nearby players (`allow_monitor=false`). On Forge for 1.21.6 – 1.21.7 the HUD does not exist.

**The addon does nothing at all.** Simple Voice Chat must be on both the server and your game, in the version the [table](#versions-and-files) says, and Fabric also needs Fabric API. The server log and `/vcd status` show whether the voice chat is connected.

**A block from another mod muffles wrongly.** On the *Walls* tab add the block you look at or hold and pick its material; for the whole server use `/vcd block add <id> <material>`. Mod and pack authors can ship a [data file](#block-materials-for-modders).

**Players without the addon hear no water, rain or echo.** These are off by default: `/vcd effects on`. Water, rain and air work on every platform; the echo of a room is measured on Paper only, but a zone with its own echo works everywhere. Changes need no restart.

**The game stutters with many voices.** The addon slows its own measuring down when the game is busy; `/voicephysics report` shows the load. Turn the corners switch off on the *Effects* tab, or the server's `server_walls_max_streams` down, if it is still too much.

**How do I report a bug?** Run `/voicephysics report` (game) or `/vcd report` (server), press *Copy* and paste the text into an [issue](https://github.com/Shamanalle/voice-physics/issues). It holds versions, the settings that change how voices sound, load and the last problems; it has no player or zone names, addresses or chat.

**What does the plugin send to bStats?** Only what bStats itself collects for every plugin (server software and versions, player count, country) and four switches of this plugin: the server profile mode, whether server walls and server realism are on, and whether there are any zones. Names, addresses, chat and settings values are never sent. Turn it off with `metrics=false` in the server file.

## Building

JDK 25 is required.

```bash
git clone https://github.com/Shamanalle/voice-physics.git
cd voice-physics
./gradlew build   # every jar ends up in build/libs/
```

How the project is laid out and how to contribute: [CONTRIBUTING.md](CONTRIBUTING.md). Changes by version: [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE) © Shamanalle
