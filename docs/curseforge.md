An addon for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that makes voices behave like sound: they fade with distance, get muffled behind walls, come round corners through doorways and echo in caves.

Install it on your client, on the server, or on both. Each works on its own. On Paper, Purpur, Folia and Spigot servers use the [Voice Physics plugin](https://www.curseforge.com/projects/1715914) instead.

![The Distance tab: the fade curve and presets](https://raw.githubusercontent.com/Shamanalle/voice-physics/main/docs/images/ui-distance.png)

## For players

**🗣️ Distance**
- Choose how voices fade: like Simple Voice Chat, realistic, or steep. No sudden cut-off at the edge.
- A live graph with the players you hear right now, and a *Listen* button to try the fade.
- Presets: **Vanilla**, **Realistic**, **Clear** (events), **Stealth** (hide-and-seek, horror). They adapt to the server's voice range.

**🧱 Walls and corners**
- Voices behind walls are quieter and duller. Wool and metal block more, glass and leaves less, open doors let sound through.
- A voice from the next room comes through the doorway, and it gets duller the sharper the turn.
- Players side by side in a narrow tunnel hear each other clearly.

**🌊 Echo, water, rain**
- The echo fits the place: short in a stone room, long in a cave, soft in a wooden house, none in a forest or field.
- Near cliffs the voice comes back a moment later.
- The echo changes smoothly as you walk between places.
- Caves, the Nether and the End sound different: echo and air follow the place.
- Dull voices under water; rain and thunder cover far voices. You choose how strong each is.

**👀 HUD and monitor**
- See who is talking, how far, from where, and whether they are behind a wall.
- While you talk, see how many players hear you.
- A monitor and radar with everyone in voice range. Colors for color blindness.
- A bigger HUD, a high-contrast mode and subtitle-style arrows for voices off to the side.

**🔗 Also:** share your settings with a friend as one code, send a bug report with one click (`/voicephysics report`). Seven languages.

## For servers

*These need Voice Physics on the server: this mod on Fabric, Forge or NeoForge, or the [plugin](https://www.curseforge.com/projects/1715914) on Paper, Purpur, Folia and Spigot.*

Players **without the addon** also hear voices muffled through walls.

- **Sound zones:** a stage heard twice as far, a quiet library, a soundproof room, a cathedral with echo. Everyone sees the zone's name above the hotbar on entering, even without the addon. WorldGuard regions and Open Parties and Claims claims work too.
- **Game rules:** sneaking is quieter, the dead are silent, spectators talk only to each other, a goat horn works as a megaphone.
- **One sound for everyone:** offer or enforce the server's settings for fair PvP and events.
- **No seeing through walls:** turn off the monitor and radar.
- **Require the addon:** send a download link, remind on every join, or kick.
- **Realism without the addon** (off by default): water, rain and echo for players who only have voice chat.
- **Mute** a player's voice for a time or for good, with `/vcd mute`.
- **Paper:** `/voice` for every player (talk quietly or shout, walls off for yourself, per-player volume) and PlaceholderAPI placeholders for scoreboards and tab lists.
- **In-game Server tab** for admins, clickable `/vcd` commands with undo, and a log of who changed what.

## Which file do I need?

| You play on | File |
|---|---|
| Fabric, Quilt | full, needs [Fabric API](https://modrinth.com/mod/fabric-api) |
| Forge 1.20 – 1.20.4, 1.20.6, 1.21 – 1.21.11, 26.x | full |
| NeoForge 1.20.1, 1.20.2 – 1.21.11, 26.x | full |
| Paper, Purpur, Folia, Spigot | the [plugin](https://www.curseforge.com/projects/1715914), a separate project |

Minecraft 1.20 – 1.20.6, 1.21 – 1.21.11 and 26.1 – 26.3.

**[All settings, commands and what works where →](https://github.com/Shamanalle/voice-physics#readme)** · [Changelog](https://github.com/Shamanalle/voice-physics/blob/main/CHANGELOG.md) · [Questions and problems](https://github.com/Shamanalle/voice-physics#questions-and-problems) · [Report a bug](https://github.com/Shamanalle/voice-physics/issues)
