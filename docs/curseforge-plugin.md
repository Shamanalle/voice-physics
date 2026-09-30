A server plugin for [Simple Voice Chat](https://www.curseforge.com/minecraft/bukkit-plugins/simple-voice-chat) on Paper, Purpur, Folia and Spigot. Voices get muffled behind walls for every player, sound zones change how far a voice carries, and game rules decide who hears whom.

Players don't need to install anything. Those who also install the [Voice Physics mod](https://www.curseforge.com/projects/1712556) on their client get the rest: distance curves, round corners, echo in caves, water and rain, a HUD and a monitor.

## What it does

- **Walls for everyone.** Players without the mod hear voices muffled through walls. Load is capped: above 24 voices at once (adjustable) the rest pass unfiltered, and on any error the original audio is sent, so voice chat never goes silent.
- **Sound zones:** a stage heard twice as far, a quiet library, a soundproof room, a cathedral with echo. Draw a box in game with `/vcd zone`, use a whole world or a WorldGuard region. Everyone sees the zone's name above the hotbar on entering.
- **Game rules:** sneaking is quieter, the dead are silent, spectators talk only to each other, a goat horn works as a megaphone.
- **One sound for everyone:** offer or enforce the server's settings for players with the mod, for fair PvP and events.
- **No seeing through walls:** turn off the monitor and radar of players with the mod.
- **Require the mod:** send a download link, remind on every join, or kick.
- **Player controls:** `/voice` lets anyone talk quietly or shout (with permission), turn wall muffling off for themselves, set or ignore other players' volume, and show who is talking nearby above the hotbar.
- **Admin tools:** clickable `/vcd` commands with undo, a log of who changed what, `/vcd debug <player>` to see whom a player hears and why not, and messages in each player's own language.

## Install

1. Put `voice-physics-bukkit-*.jar` into `plugins/`. Simple Voice Chat must be installed.
2. Restart the server. Settings are in `plugins/VoicePhysics/vc-audio-distance-server.properties` and can all be changed in game with `/vcd`.

Minecraft 1.20 – 1.21.11 and 26.1 – 26.3, Java 17+. Each part of `/vcd` has its own permission (`vcd.status`, `vcd.settings`, `vcd.zone`, `vcd.debug`, `vcd.admin`); operators have all of them.

Running Fabric, Forge or NeoForge? Install the [mod](https://www.curseforge.com/projects/1712556) on the server instead, it does all of this too.

**[All settings, commands and permissions →](https://github.com/Shamanalle/voice-physics#for-servers)** · [Changelog](https://github.com/Shamanalle/voice-physics/blob/main/CHANGELOG.md) · [Report a bug](https://github.com/Shamanalle/voice-physics/issues)
