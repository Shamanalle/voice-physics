An addon for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that makes proximity voice chat sound real: voices fade with distance, get muffled behind walls (occlusion), come round corners through doorways and echo in caves.

Install it on your client, on the server, or on both. Each works on its own.

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
- The Doppler effect (off by default): a voice flying or riding towards you sounds higher, one moving away lower.

**👀 HUD and monitor**
- See who is talking, how far, from where, and whether they are behind a wall.
- While you talk, see how many players hear you.
- A monitor and radar with everyone in voice range. Colors for color blindness.
- A bigger HUD, a high-contrast mode and subtitle-style arrows for voices off to the side.

**🔗 Also:** share your settings with a friend as one code, send a bug report with one click (`/voicephysics report`). Seven languages.

## For servers

Players **without the addon** also hear voices muffled through walls.

- **Sound zones:** a stage heard twice as far, a quiet library, a soundproof room, a cathedral with echo. Everyone sees the zone's name above the hotbar on entering, even without the addon. WorldGuard regions and Open Parties and Claims claims work too.
- **Game rules:** sneaking is quieter, the dead are silent, spectators talk only to each other, a goat horn works as a megaphone.
- **One sound for everyone:** offer or enforce the server's settings for fair PvP and events.
- **No seeing through walls:** turn off the monitor and radar.
- **Require the addon:** send a download link, remind on every join, or kick.
- **Realism without the addon** (off by default): water, rain, echo, the Doppler effect and the server profile's distance curve for players who only have voice chat.
- **Mute** a player's voice for a time or for good, with `/vcd mute`.
- **Paper extras** (off by default, not yet tried in a live game): a radio, loudspeakers, an eavesdrop item, a sculk reaction to shouts, doorway sound, and Towny, Lands, WorldGuard and LuckPerms integrations. `/vcd extras` lists the switches.
- **Paper:** `/voice` for every player (talk quietly or shout, walls off for yourself, per-player volume) and PlaceholderAPI placeholders for scoreboards and tab lists.
- **In-game Server tab** for admins: the effects for players without the addon and the Paper extras are switches there, plus clickable `/vcd` commands with undo and a log of who changed what.

## Which file do I need?

| You play on | File |
|---|---|
| Fabric, Quilt (1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4 and 1.20+) | full, needs [Fabric API](https://modrinth.com/mod/fabric-api) |
| Forge 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20 – 1.20.4, 1.20.6, 1.21 – 1.21.11, 26.x | full |
| NeoForge 1.20.1, 1.20.2 – 1.21.11, 26.x | full |
| Paper, Purpur, Folia, Spigot | plugin (server only) |

Minecraft 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20 – 1.20.6, 1.21 – 1.21.11 and 26.1 – 26.3.

**[All settings, commands and what works where →](https://github.com/Shamanalle/voice-physics#readme)** · [Changelog](https://github.com/Shamanalle/voice-physics/blob/main/CHANGELOG.md) · [Questions and problems](https://github.com/Shamanalle/voice-physics#questions-and-problems) · [Report a bug](https://github.com/Shamanalle/voice-physics/issues)

---

<details>
<summary><b>🇷🇺 Русский</b></summary>

Аддон для [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), с которым голоса ведут себя как звук: затихают с расстоянием, глохнут за стенами, доносятся из-за угла через проёмы и отдаются эхом в пещерах.

Ставится на клиент, на сервер или туда и туда. Каждый вариант работает сам по себе.

## Для игроков

**🗣️ Дистанция**
- Выберите, как тихнут голоса: как в Simple Voice Chat, реалистично или круче. Без резкого обрыва на краю.
- Живой график с теми, кого вы слышите сейчас, и кнопка «Прослушать», чтобы услышать спад.
- Пресеты: **Ваниль**, **Реализм**, **Чётко** (ивенты), **Стелс** (прятки, хоррор). Сами подстраиваются под дальность голоса на сервере.

**🧱 Стены и углы**
- Голоса за стенами тише и глуше. Шерсть и металл глушат сильнее, стекло и листва слабее, открытые двери пропускают звук.
- Голос из соседней комнаты идёт через дверной проём и тем глуше, чем круче поворот.
- Двое рядом в узком тоннеле слышат друг друга чисто.

**🌊 Эхо, вода, дождь**
- Эхо по месту: короткое в каменной комнате, долгое в пещере, мягкое в деревянном доме, никакого в лесу и в поле.
- У скал голос возвращается через мгновение.
- Эхо меняется плавно, когда вы переходите из одного места в другое.
- Пещеры, Незер и Энд звучат по-разному: эхо и воздух следуют за местом.
- Глухие голоса под водой; дождь и гроза заглушают дальние голоса. Силу каждого выбираете вы.

**👀 HUD и монитор**
- Видно, кто говорит, как далеко, откуда и за стеной ли.
- Пока говорите вы — сколько игроков вас слышат.
- Монитор и радар со всеми в радиусе голоса. Цвета для дальтоников.
- Крупнее HUD, режим высокого контраста и стрелки в духе субтитров для голосов сбоку.

**🔗 Ещё:** поделитесь настройками с другом одним кодом, отправьте баг-репорт в один клик (`/voicephysics report`). Семь языков.

## Для серверов

Игроки **без аддона** тоже слышат голоса за стенами приглушёнными.

- **Звуковые зоны:** сцена, которую слышно вдвое дальше, тихая библиотека, звукоизолированная комната, собор с эхом. При входе все видят название зоны над хотбаром, даже без аддона. Подходят и регионы WorldGuard, и приваты Open Parties and Claims.
- **Правила игры:** на корточках тише, мёртвые молчат, наблюдатели говорят только между собой, козий рог работает как мегафон.
- **Один звук для всех:** предложите или закрепите настройки сервера для честного PvP и ивентов.
- **Без взгляда сквозь стены:** выключите монитор и радар.
- **Обязательный аддон:** ссылка на скачивание, напоминание при каждом входе или кик.
- **Реализм без аддона** (по умолчанию выключен): вода, дождь и эхо для игроков, у которых есть только голосовой чат.
- **Заглушение** голоса игрока на время или насовсем командой `/vcd mute`.
- **Дополнения Paper** (по умолчанию выключены, ещё не опробованы в живой игре): рация, громкоговорители, предмет для подслушивания, реакция скалка на крик, звук через дверной проём и интеграции с Towny, Lands, WorldGuard и LuckPerms. Переключатели — в `/vcd extras`.
- **Paper:** `/voice` для каждого игрока (говорить тише или кричать, свои стены выключить, громкость каждого) и плейсхолдеры PlaceholderAPI для скорбордов и таба.
- **Вкладка «Сервер» в игре** для админов: эффекты для игроков без аддона и дополнения Paper включаются там переключателями, плюс кликабельные команды `/vcd` с отменой и журнал того, кто что менял.

## Какой файл нужен?

| Вы играете на | Файл |
|---|---|
| Fabric, Quilt (1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4 и 1.20+) | полный, нужен [Fabric API](https://modrinth.com/mod/fabric-api) |
| Forge 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20 – 1.20.4, 1.20.6, 1.21 – 1.21.11, 26.x | полный |
| NeoForge 1.20.1, 1.20.2 – 1.21.11, 26.x | полный |
| Paper, Purpur, Folia, Spigot | плагин (только сервер) |

Minecraft 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20 – 1.20.6, 1.21 – 1.21.11 и 26.1 – 26.3.

**[Все настройки, команды и что где работает →](https://github.com/Shamanalle/voice-physics/blob/main/README.ru.md)** · [Список изменений](https://github.com/Shamanalle/voice-physics/blob/main/CHANGELOG.md) · [Вопросы и проблемы](https://github.com/Shamanalle/voice-physics/blob/main/README.ru.md#вопросы-и-проблемы) · [Сообщить об ошибке](https://github.com/Shamanalle/voice-physics/issues)

</details>
