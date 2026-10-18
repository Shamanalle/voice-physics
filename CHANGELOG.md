# Changelog

All notable changes to this project are documented in this file. Each version is described in English first, then in Russian.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

*Все заметные изменения проекта описываются в этом файле. Каждая версия описана сначала на английском, затем на русском.*

## [2.12.3] - 2026-10-18

### English

#### Changed
- The message for players without the addon is shorter and now links to the CurseForge page. Servers that set their own link keep it.

### Русский

#### Изменено
- Сообщение игрокам без аддона стало короче и теперь ведёт на страницу CurseForge. Если сервер указал свою ссылку, она остаётся.

## [2.12.2] - 2026-10-17

### English

#### Fixed
- Small fixes.

### Русский

#### Исправлено
- Мелкие исправления.

## [2.12.1] - 2026-10-17

### English

#### Changed
- **The Log screen fills the screen.** It used to show ten changes at a time even on a big monitor; now it shows as many as fit, and the arrows say which ones you see (*1–24 of 137*). With a server on an older version it still shows ten at a time.

### Русский

#### Изменено
- **Экран журнала занимает весь экран.** Раньше он показывал по десять изменений даже на большом мониторе, теперь столько, сколько помещается, а между стрелками написано, какие видно («1–24 из 137»). Если сервер на старой версии, по-прежнему по десять.

## [2.12.0] - 2026-10-17

### English

#### Added
- **Which way the speaker faces (optional, off by default).** A voice is clearest in front of the speaker; behind their back it is a little quieter and duller. The default strength is gentle, so turning round does not get in the way of the game. A *Facing* switch with a strength slider at the end of the *Effects* tab. For players without the addon: `/vcd effects facing <0-150>|off`.
- **A whisper is lost behind a wall, a shout carries (on by default).** Behind one wall a whisper is barely heard, a normal voice changes only from the third wall, and a shout carries through three. The microphone's volume does not matter: each player's usual level is learnt from their voice. A switch on the *Walls* tab; for players without the addon: `/vcd walls loudness on|off`.
- **Wind on heights and in fast flight (on by default).** High up under the open sky, and in fast elytra flight, far voices are blown away a little, more so in rain and thunder. Under a roof, under water, in the Nether and the End there is no wind. A *Wind* switch with a strength slider on the *Effects* tab; for players without the addon: `/vcd effects wind <0-150>|off`.

#### Changed
- **A megaphone sounds like one, and carries furthest ahead.** A player holding the megaphone item sounds like a horn to everyone, also to players without the addon. It carries furthest the way they look and less behind them, but never goes quiet. `/vcd rule megaphone_sound off` and `/vcd rule megaphone_direction off` turn either part off; the *Server* tab now has a *Megaphone* section with the item, sound and range.
- **The settings screen is laid out anew.** Each effect is one row with its switch and strength slider. The *Effects*, *HUD* and *Walls* tabs are split into clear sections, and on the *Server* tab everything for players without the addon is in one place. Buttons fit in every language, also in a small window.

### Русский

#### Добавлено
- **Куда смотрит говорящий (по желанию, по умолчанию выключено).** Голос чище всего слышен перед говорящим, а за его спиной чуть тише и глуше. По умолчанию сила мягкая, чтобы поворот не мешал игре. Переключатель «Поворот головы» с ползунком силы в конце вкладки «Эффекты». Для игроков без аддона: `/vcd effects facing <0-150>|off`.
- **Шёпот за стеной теряется, крик проходит (по умолчанию включено).** За одной стеной шёпот почти не слышен, обычный голос меняется только с третьей стены, а крик проходит и через три. Громкость микрофона не важна: обычная громкость каждого игрока узнаётся по его голосу. Переключатель на вкладке «Стены»; для игроков без аддона: `/vcd walls loudness on|off`.
- **Ветер на высоте и в быстром полёте (по умолчанию включено).** Высоко под открытым небом и в быстром полёте на элитрах дальние голоса немного сдувает, в дождь и грозу сильнее. Под крышей, под водой, в Незере и Энде ветра нет. Переключатель «Ветер» с ползунком силы на вкладке «Эффекты»; для игроков без аддона: `/vcd effects wind <0-150>|off`.

#### Изменено
- **Мегафон звучит как мегафон, и дальше всего слышно впереди.** Игрок с предметом-мегафоном для всех звучит как из рупора, в том числе для игроков без аддона. Дальше всего его слышно туда, куда он смотрит, за спиной меньше, но не глохнет. `/vcd rule megaphone_sound off` и `/vcd rule megaphone_direction off` выключают одно или другое; на вкладке «Сервер» появился раздел «Мегафон» с предметом, звуком и дальностью.
- **Экран настроек собран заново.** Каждый эффект занимает одну строку: переключатель и ползунок силы. Вкладки «Эффекты», «HUD» и «Стены» разбиты на понятные разделы, а на вкладке «Сервер» всё для игроков без аддона собрано в одном месте. Кнопки помещаются на всех языках и в маленьком окне.

## [2.11.1] - 2026-10-11

### English

#### Changed
- **Fewer files: 3 instead of 34.** One file for Fabric and Quilt, one for Forge and NeoForge, each for every Minecraft version from 1.16.5 to 26.3, and the Bukkit plugin. Nothing changes in game. Forge 1.16.5 still runs on Java 8.

### Русский

#### Изменено
- **Меньше файлов: 3 вместо 34.** Один файл для Fabric и Quilt, один для Forge и NeoForge, каждый для всех версий Minecraft от 1.16.5 до 26.3, и плагин для Bukkit. В игре ничего не меняется. Forge 1.16.5 по-прежнему работает на Java 8.

## [2.11.0] - 2026-10-10

### English

#### Added
- **The Doppler effect (optional, off by default).** A voice coming closer sounds higher, one moving away lower: on elytra, in boats on ice, on horses and in minecarts. Walking side by side or turning round changes nothing. A *Doppler* switch with a strength slider on the *Effects* tab; for players without the addon: `/vcd effects doppler <0-300>|off`.
- With the [Doppler](https://github.com/imAETHER/Doppler) mod installed, a button on the *Effects* tab chooses whose effect voices get: the mod's (as before), ours, or both.

### Русский

#### Добавлено
- **Эффект Доплера (по желанию, по умолчанию выключен).** Голос приближающегося звучит выше, удаляющегося — ниже: на элитрах, в лодках на льду, на лошадях и в вагонетках. Ходьба рядом или повороты ничего не меняют. Переключатель «Эффект Доплера» с ползунком силы на вкладке «Эффекты»; для игроков без аддона: `/vcd effects doppler <0-300>|off`.
- Если установлен мод [Doppler](https://github.com/imAETHER/Doppler), кнопка на вкладке «Эффекты» выбирает, чей эффект получают голоса: мода (как раньше), наш или оба.

## [2.10.5] - 2026-10-06

### English

#### Added
- **The distance curve for players without the addon (optional, off by default).** Players who have only Simple Voice Chat hear voices fade by the server's curve too, like players with the addon. `/vcd effects curve on`, or the *Curve* switch in the *Effects* section of the *Server* tab.

### Русский

#### Добавлено
- **Кривая громкости для игроков без аддона (по желанию, по умолчанию выключено).** Игроки, у которых есть только Simple Voice Chat, тоже слышат, как голоса затихают по кривой сервера, как и игроки с аддоном. `/vcd effects curve on` или переключатель «Кривая» в разделе «Эффекты» вкладки «Сервер».

## [2.10.4] - 2026-10-05

### English

#### Fixed
- **The distance curve now works in play.** Before, voices stayed at full volume and then cut off at the edge of the range; only the graph showed the curve. Now voices fade as the graph shows, on every loader and Minecraft version.

#### Added
- **Voice Physics over Sound Physics Remastered (optional).** With Sound Physics Remastered installed, a *Use ours* switch on the *Walls* and *Effects* tabs gives voices our walls, corners, echo and water. All other game sounds stay with Sound Physics Remastered.

#### Changed
- *Distance* tab: the curve shape, edge volume and full-volume range are always shown.

### Русский

#### Исправлено
- **Кривая громкости теперь работает в игре.** Раньше голоса звучали в полную громкость, а на краю дальности резко пропадали; кривую показывал только график. Теперь голоса затихают так, как показывает график, на всех загрузчиках и версиях Minecraft.

#### Добавлено
- **Voice Physics вместо Sound Physics Remastered (по желанию).** Если установлен Sound Physics Remastered, переключатель «Использовать наш» на вкладках «Стены» и «Эффекты» отдаёт голоса нашим стенам, углам, эху и воде. Все остальные звуки игры остаются за Sound Physics Remastered.

#### Изменено
- Вкладка «Дистанция»: форма кривой, громкость на краю и зона полной громкости показаны всегда.

## [2.10.3] - 2026-10-02

### English

#### Added
- **Forge for 1.17.1 and 1.19.4**, with the same features as the other Forge versions. Forge 1.17.1 needs Java 16 or newer. Minecraft 1.19.3 and 1.12.2 are not supported.

### Русский

#### Добавлено
- **Forge для 1.17.1 и 1.19.4**, с теми же возможностями, что и на других версиях Forge. Forge 1.17.1 нужна Java 16 или новее. Minecraft 1.19.3 и 1.12.2 не поддерживаются.

## [2.10.2] - 2026-10-01

### English

#### Added
- **Older Minecraft, same features.** The addon now runs on **Minecraft 1.16.5, 1.17.1, 1.18.2, 1.19.2 and 1.19.4**: Fabric and Quilt on all five, Forge on 1.16.5, 1.18.2 and 1.19.2, and the Paper/Purpur/Folia/Spigot plugin on 1.16.5 – 1.19.4 (it needed 1.20 before). The 1.16.5 files run on Java 8, as the game does.

### Русский

#### Добавлено
- **Старый Minecraft, те же возможности.** Аддон теперь работает на **Minecraft 1.16.5, 1.17.1, 1.18.2, 1.19.2 и 1.19.4**: Fabric и Quilt на всех пяти, Forge на 1.16.5, 1.18.2 и 1.19.2, а плагин для Paper/Purpur/Folia/Spigot на 1.16.5 – 1.19.4 (раньше нужна была 1.20). Файлы для 1.16.5 работают на Java 8, как и сама игра.

## [2.10.1] - 2026-10-01

### English

#### Added
- **Server tab: effects for players without the addon.** A new section does what `/vcd effects` does: turns the effects on and off and sets how strong water, weather and echo are.
- **Server tab: plugin extras.** On the Paper/Folia plugin, a folded section switches the radio, loudspeakers, eavesdrop item, sculk and wardens, doorway sound and integrations.

### Русский

#### Добавлено
- **Вкладка «Сервер»: эффекты для игроков без аддона.** Новый раздел делает то же, что `/vcd effects`: включает и выключает эффекты и задаёт силу воды, погоды и эха.
- **Вкладка «Сервер»: дополнения плагина.** На плагине Paper/Folia свёрнутый раздел включает рацию, громкоговорители, предмет для подслушивания, скалк и варденов, звук через двери и интеграции.

## [2.10.0] - 2026-09-30

### English

> **Everything new in this release is for the Paper/Folia plugin, off by default and not yet tried in a live game.** Turn each one on with `/vcd extras <name> on` on a test server first; `/vcd undo` takes a switch back.

#### Added
- **`/vcd extras`** lists the new features below, each with a switch.
- **Radio.** `/voice radio <1-9999|off>`: players on the same frequency hear each other at any distance and in any world. A server can make players hold an item for it: `/vcd radio item <id>`.
- **Loudspeakers.** `/vcd speaker add <name>` places one where you stand: whoever talks near it is heard from it by everyone around. `/vcd speaker list|tp|remove` manage them.
- **Eavesdrop item.** A player holding a spyglass hears walls as thinner; `/vcd eavesdrop` sets the item and how much. Works for players without the addon.
- **Sculk and wardens** react to a shout or a megaphone voice. Whispers, sneaking and normal talk do not wake them.
- **Doorway sound** for players without the addon: behind a wall, a voice is heard from a nearby open door or gap instead of through the wall.
- **Integrations:** Towny towns and Lands lands as zones, a WorldGuard flag that puts a region in a zone, and LuckPerms contexts.

### Русский

> **Всё новое в этом выпуске — для плагина Paper/Folia, выключено по умолчанию и ещё не опробовано в живой игре.** Включайте каждое командой `/vcd extras <название> on` сначала на тестовом сервере; `/vcd undo` возвращает переключатель.

#### Добавлено
- **`/vcd extras`** показывает новые возможности ниже, у каждой свой переключатель.
- **Рация.** `/voice radio <1-9999|off>`: игроки на одной частоте слышат друг друга на любом расстоянии и в любом мире. Сервер может требовать предмет в руке: `/vcd radio item <id>`.
- **Громкоговорители.** `/vcd speaker add <имя>` ставит громкоговоритель там, где вы стоите: того, кто говорит рядом с ним, слышат из него все вокруг. `/vcd speaker list|tp|remove` управляют ими.
- **Предмет для подслушивания.** Игрок с подзорной трубой в руке слышит стены тоньше; `/vcd eavesdrop` задаёт предмет и насколько. Работает и для игроков без аддона.
- **Скалк и вардены** реагируют на крик или голос с мегафоном. Шёпот, корточки и обычная речь их не будят.
- **Звук через дверной проём** для игроков без аддона: за стеной голос слышен из ближайшей открытой двери или проёма, а не сквозь стену.
- **Интеграции:** города Towny и земли Lands как зоны, флаг WorldGuard, относящий регион к зоне, и контексты LuckPerms.

## [2.9.0] - 2026-09-30

### English

#### Added
- **`/voice` for every player (Paper/Folia), with or without the mod.** Choose how loud you talk (`/voice mode quiet|normal|shout`), turn wall muffling off for yourself, set how loud a player is to you or ignore them, and show who is talking near you above the hotbar. `/voice menu` opens the same choices as a menu. A server can take any part away by permission.
- **Realism for players without the addon** (off by default, `/vcd effects on`): voices are dull under water, far ones are covered by rain and thunder, and a voice from a cave or hall echoes. The room echo is measured on Paper only.
- **Mute:** `/vcd mute <player> [time] [reason]`, `/vcd unmute <player>`, `/vcd mutes`. Kept across restarts.
- **PlaceholderAPI (Paper):** placeholders for a player's mode, mute, range, zone and who is talking near them; the list is in the README.
- **Bug reports in one click:** `/vcd report` and `/voicephysics report` give one text to copy into a bug report, without player or zone names.
- **Materials for modded blocks:** resource packs, data packs and mods can say which material their blocks count as. The format is in the README.
- **Sound by place** (on by default, a switch on the *Effects* tab): deep caves ring longer, the Nether dulls far voices, the End has no echo, snow and dense jungle swallow sound.
- **Accessibility:** a high-contrast HUD, direction marks for voices off to the side, and a HUD up to twice the size.
- **Statistics (plugin):** anonymous usage numbers to [bStats](https://bstats.org), no names, addresses or chat; the README says how to turn them off.
- A *Questions and problems* section in the README.

#### Changed
- A zone with its own wall strength now applies it to everyone in it, also to players who turned walls off with `/voice walls off`.
- The addon does less work each tick and measures less often while the game is busy.

### Русский

#### Добавлено
- **`/voice` для каждого игрока (Paper/Folia), с модом или без.** Выберите, как громко говорите (`/voice mode quiet|normal|shout`), отключите для себя приглушение стенами, задайте громкость игрока или игнорируйте его и включите над хотбаром показ того, кто говорит рядом. `/voice menu` открывает тот же выбор как меню. Сервер может отнять любую часть правами.
- **Реализм для игроков без аддона** (по умолчанию выключен, `/vcd effects on`): голоса глухие под водой, дальние заглушает дождь и гроза, а голос из пещеры или зала звучит с эхом. Эхо помещения измеряется только на Paper.
- **Заглушение:** `/vcd mute <игрок> [время] [причина]`, `/vcd unmute <игрок>`, `/vcd mutes`. Сохраняется между перезапусками.
- **PlaceholderAPI (Paper):** плейсхолдеры для режима игрока, заглушения, дальности, зоны и того, кто говорит рядом; список в README.
- **Баг-репорт в один клик:** `/vcd report` и `/voicephysics report` выдают один текст для баг-репорта, без имён игроков и зон.
- **Материалы для блоков из модов:** ресурспаки, датапаки и моды могут указать, каким материалом считаются их блоки. Формат описан в README.
- **Звук по месту** (включено по умолчанию, переключатель на вкладке «Эффекты»): в глубоких пещерах эхо дольше, Незер глушит дальние голоса, в Энде эха нет, снег и густые джунгли поглощают звук.
- **Доступность:** HUD с высоким контрастом, метки направления для голосов сбоку и HUD до двойного размера.
- **Статистика (плагин):** анонимные цифры использования в [bStats](https://bstats.org), без имён, адресов и чата; как выключить, написано в README.
- Раздел «Вопросы и проблемы» в README.

#### Изменено
- Зона со своей силой стен теперь применяет её ко всем внутри, в том числе к тем, кто выключил стены командой `/voice walls off`.
- Аддон делает меньше работы за тик и измеряет реже, когда игра занята.

## [2.7.1] - 2026-09-30

### English

#### Fixed
- A voice behind a wall is now heard from the doorway it comes through, and a voice from a cave echoes for you outside it. Neither worked before.
- The echo fades in smoothly when you walk into a cave instead of jumping in.
- With a player's volume above 100%, a voice no longer stays quiet when you move closer.
- Very far voices no longer slow the game down.
- With many players talking on a server, everyone hears walls; before, some players heard none.
- `/vcd zone tp` takes you to the middle of the zone, not to its bottom.
- `/vcd walls 1%` is 1%, not 100%.
- A backslash in a zone message no longer breaks the settings file.
- `/vcd walls` says whom it reaches.

### Русский

#### Исправлено
- Голос за стеной теперь слышен из дверного проёма, через который он проходит, а голос из пещеры даёт эхо и снаружи. Раньше ни то ни другое не работало.
- Эхо плавно нарастает, когда вы заходите в пещеру, а не появляется сразу.
- При громкости игрока выше 100% голос больше не остаётся тихим, когда вы подходите ближе.
- Очень далёкие голоса больше не тормозят игру.
- Когда на сервере говорит много игроков, стены слышат все; раньше некоторые не слышали.
- `/vcd zone tp` переносит в середину зоны, а не на её дно.
- `/vcd walls 1%` — это 1%, а не 100%.
- Обратная косая черта в сообщении зоны больше не ломает файл настроек.
- `/vcd walls` говорит, до кого доходит.

## [2.7.0] - 2026-09-29

### English

#### Added
- Custom blocks: blocks from other mods, or ones that are guessed wrong, can count as a material you choose. On the *Walls* tab, under the materials: add the block you look at, the block in your hand or an id or `#tag`, change its material, remove it. Servers do the same for everyone: *Server* tab or `/vcd block add|remove|list|clear`. Locked together with the materials.
- The server's change log on a screen of its own, for admins: *Server* tab → *Full log*, or `/voicephysics log`. Ten changes a page, one player's changes by name, undo of the last change, and a button that copies the page.
- `/vcd log [page] [player]`: the log of one player's changes (`console` for the console).
- Monitor: point at a talking player to see why the voice is as loud as it is (distance, whisper, wall, the way round a corner).

#### Changed
- *Distance* tab: falloff and whisper first; the curve's shape, edge volume and full-volume range are under *More curve settings*.
- *Server* tab: the sections fold, and a folded one shows what it is set to.

### Русский

#### Добавлено
- Свои блоки: блоки из других модов или те, что определились неверно, можно посчитать выбранным материалом. На вкладке «Стены» под материалами: добавить блок под прицелом, блок в руке или id/`#тег`, сменить материал, удалить. Сервер делает то же для всех: вкладка «Сервер» или `/vcd block add|remove|list|clear`. Закрепляется вместе с материалами.
- Журнал изменений сервера на отдельном экране для админов: вкладка «Сервер» → «Весь журнал» или `/voicephysics log`. По десять изменений на странице, изменения одного игрока по нику, отмена последнего изменения и кнопка, которая копирует страницу.
- `/vcd log [страница] [игрок]`: журнал изменений одного игрока (`console` — консоль).
- Монитор: наведите на говорящего игрока, чтобы увидеть, почему голос такой громкий (расстояние, шёпот, стена, обход угла).

#### Изменено
- Вкладка «Дистанция»: сначала спад и шёпот; форма кривой, громкость на краю и зона полной громкости — в разделе «Ещё настройки кривой».
- Вкладка «Сервер»: разделы сворачиваются, свёрнутый показывает своё значение.

## [2.6.2] - 2026-09-29

### English

#### Added
- The full addon on NeoForge 1.20.2 – 1.20.3: settings screen, walls, echo, HUD, monitor and the server side.

#### Changed
- There is no lite file any more: every Minecraft version from 1.20 has the full addon on every loader.

### Русский

#### Добавлено
- Полный аддон на NeoForge 1.20.2 – 1.20.3: экран настроек, стены, эхо, HUD, монитор и серверная часть.

#### Изменено
- Облегчённого файла больше нет: на каждой версии Minecraft с 1.20 на каждом загрузчике полный аддон.

## [2.6.1] - 2026-09-29

### English

#### Added
- The full addon on Forge 1.20.2 – 1.20.4 and NeoForge 1.20.4 (NeoForge 20.4.80 or newer): settings screen, walls, echo, HUD, monitor and the server side.
- Forge 1.20 and 1.21: the Forge 1.20.1 and 1.21.1 files now run there too. The 1.21 – 1.21.1 file has a new name.
- NeoForge 1.20.1 takes the Forge 1.20.1 file.
- The 26.x file is listed for Quilt.
- The plugin is listed for every release from 1.20 to 26.3, including 1.20, 1.20.5, 1.21.2 and 26.1.

#### Changed
- The lite file is now only for NeoForge 1.20.2 – 1.20.3. The Fabric 1.20.2 – 1.20.4 file is for Fabric and Quilt only.

### Русский

#### Добавлено
- Полный аддон на Forge 1.20.2 – 1.20.4 и NeoForge 1.20.4 (NeoForge 20.4.80 или новее): экран настроек, стены, эхо, HUD, монитор и серверная часть.
- Forge 1.20 и 1.21: файлы для Forge 1.20.1 и 1.21.1 теперь работают и там. У файла для 1.21 – 1.21.1 новое имя.
- NeoForge 1.20.1 берёт файл для Forge 1.20.1.
- Файл для 26.x отмечен и для Quilt.
- Плагин отмечен для всех версий с 1.20 по 26.3, включая 1.20, 1.20.5, 1.21.2 и 26.1.

#### Изменено
- Облегчённый файл теперь только для NeoForge 1.20.2 – 1.20.3. Файл Fabric для 1.20.2 – 1.20.4 — только для Fabric и Quilt.

## [2.6.0] - 2026-09-27

### English

#### Added
- The full addon on Forge 1.20.6, 1.21.1 – 1.21.11 and 26.x, and on NeoForge 1.20.5 – 1.21.11: settings screen, walls, echo, HUD, monitor and the server side. There is a file for each range of versions.

#### Changed
- The Fabric files for 1.20.5 – 1.21.11 are for Fabric and Quilt only; Forge and NeoForge have files of their own.
- On Forge for 1.21.6 – 1.21.7 there is no voice HUD: that Forge cannot add one. Everything else works.
- Forge for 1.21 (not 1.21.1) is no longer supported.

### Русский

#### Добавлено
- Полный аддон на Forge 1.20.6, 1.21.1 – 1.21.11 и 26.x и на NeoForge 1.20.5 – 1.21.11: экран настроек, стены, эхо, HUD, монитор и серверная часть. Для каждого диапазона версий — свой файл.

#### Изменено
- Файлы для Fabric 1.20.5 – 1.21.11 теперь только для Fabric и Quilt; у Forge и NeoForge свои файлы.
- На Forge для 1.21.6 – 1.21.7 нет HUD голоса: этот Forge не умеет его добавлять. Всё остальное работает.
- Forge для 1.21 (не 1.21.1) больше не поддерживается.

## [2.5.0] - 2026-09-27

### English

#### Added
- Entering or leaving a sound zone shows its name above the hotbar, for every player, even without the addon. `/vcd notices off` turns it off.
- Zones from Open Parties and Claims on Fabric, Forge and NeoForge: `/vcd zone set claim:<player> …` covers that player's claims, or the whole party's for its leader.
- `/vcd log`: who changed the server's settings and when. Every change is also written to a file next to the settings.
- Sliders for how strong water and rain are, on the *Effects* tab.
- Server tab: zone names above the hotbar on or off, corners from where you stand and where you look, *Go there*, *Rename*, the latest changes and *Undo*. Buttons you have no permission for are greyed out.

#### Changed
- Open doors and trapdoors muffle a tenth of a closed one; iron and copper doors count as metal.
- The echo changes smoothly as you walk between places, instead of in steps.
- Locking walls on the server also locks each block's percentage.

### Русский

#### Добавлено
- При входе в звуковую зону и выходе из неё её название показывается над хотбаром у всех игроков, даже без аддона. `/vcd notices off` выключает.
- Зоны из Open Parties and Claims на Fabric, Forge и NeoForge: `/vcd zone set claim:<игрок> …` — приваты этого игрока, а для лидера группы — всей группы.
- `/vcd log`: кто и когда менял настройки сервера. Каждое изменение также записывается в файл рядом с настройками.
- Ползунки силы воды и дождя на вкладке «Эффекты».
- Вкладка «Сервер»: названия зон над хотбаром вкл/выкл, углы по месту, где вы стоите, и по блоку, на который смотрите, «Перейти», «Переименовать», последние изменения и «Отменить». Кнопки, на которые нет прав, неактивны.

#### Изменено
- Открытые двери и люки глушат на десятую часть от закрытых; железные и медные двери считаются металлом.
- Эхо меняется плавно, когда вы переходите из одного места в другое, а не ступеньками.
- Закреплённые на сервере стены закрепляют и проценты каждого блока.

## [2.4.0] - 2026-09-27

### English

#### Added
- `/vcd` replies are coloured and clickable: click a value in `/vcd status` to change it, zones in `/vcd zones` have Info, Show and Go there buttons.
- `/vcd undo` takes back the last changes, up to 10; every change has an Undo button.
- `/vcd help <command>` shows the command with examples you can click; typos get "Did you mean…".
- Zones: corners at coordinates or at the block you look at (`/vcd zone pos1 look`), the selection is shown with particles, `/vcd zone info` lists a zone's settings to click, `/vcd zone tp` and `/vcd zone rename`.
- Tab completion offers zone, world and player names and each setting's values, with short explanations.
- Separate permissions on Paper: `vcd.status`, `vcd.settings`, `vcd.zone`, `vcd.debug` (`vcd.admin` gives all). LuckPerms on Fabric uses the same.
- `/voicephysics` for players: open a tab, choose a preset, switch the HUD, copy or load a profile code, reset, and see what the server does to your sound.

#### Changed
- Deleting a zone with `/vcd zone delete` asks first.
- Wrong values say what is allowed, and walls above 100% are refused.

### Русский

#### Добавлено
- Ответы `/vcd` цветные и кликабельные: по значению в `/vcd status` можно кликнуть, чтобы изменить его, у зон в `/vcd zones` есть кнопки «Подробнее», «Показать» и «Туда».
- `/vcd undo` отменяет последние изменения, до 10; у каждого изменения есть кнопка «Отменить».
- `/vcd help <команда>` показывает команду с примерами, по которым можно кликнуть; при опечатке — «Может, …».
- Зоны: углы по координатам или по блоку, на который вы смотрите (`/vcd zone pos1 look`), выделение видно частицами, `/vcd zone info` показывает настройки зоны с кликом по каждой, `/vcd zone tp` и `/vcd zone rename`.
- Tab подсказывает имена зон, миров и игроков и значения каждого параметра, с короткими пояснениями.
- Отдельные права на Paper: `vcd.status`, `vcd.settings`, `vcd.zone`, `vcd.debug` (`vcd.admin` даёт все). LuckPerms на Fabric использует те же.
- `/voicephysics` для игроков: открыть вкладку, выбрать пресет, переключить HUD, скопировать или загрузить код профиля, сбросить и посмотреть, что сервер делает с вашим звуком.

#### Изменено
- `/vcd zone delete` сначала спрашивает, удалять ли зону.
- При неверном значении сказано, что можно, а стены больше 100% не принимаются.

## [2.3.0] - 2026-09-27

### English

#### Added
- Full Forge version for 1.20.1: walls, echo, corners, the HUD, the monitor, the settings screen and the server side.
- Full NeoForge version for 1.21 – 1.21.1.

#### Changed
- *Listen* plays the voice at more distances, and its button is small again.

#### Fixed
- With falloff below 100% the voice still fades out fully by the edge of hearing.
- Server tab: long setting names are no longer cut off, and the zones line does not run under its button.
- Long hints wrap to a second line instead of being cut off; tab names fit in small windows.

### Русский

#### Добавлено
- Полная версия для Forge 1.20.1: стены, эхо, углы, HUD, монитор, экран настроек и серверная часть.
- Полная версия для NeoForge 1.21 – 1.21.1.

#### Изменено
- «Прослушать» проигрывает голос на большем числе расстояний, а кнопка снова маленькая.

#### Исправлено
- При затухании меньше 100% голос всё равно полностью стихает к краю слышимости.
- Вкладка «Сервер»: длинные названия настроек больше не обрезаются, линия «Зоны» не заходит под кнопку.
- Длинные подсказки переносятся на вторую строку, а не обрезаются; названия вкладок помещаются в маленьком окне.

## [2.2.0] - 2026-09-26

### English

#### Added
- The settings screen scrolls, so nothing is cut off in small windows or with a large interface size.
- HUD tab with a live preview of the HUD.
- `/voicephysics` opens the settings. The first-join hint and the server profile message have a clickable link to them.
- `/vcd zone show <name>` and a *Show* button on the Server tab draw a box zone's borders with particles for 30 seconds.
- The server plugin runs on Folia.
- A short HUD notice when the server hides nearby players.

#### Changed
- Materials are on the Walls tab, in a section that opens.
- The profile code is in the footer and works from every tab.
- *Reset* resets only the open tab.
- Settings the server locks show a padlock and say why.
- A preset no longer changes walls the server locks.
- Plain words instead of technical units; shorter tooltips.
- Server tab: settings in sections, the zone you are in is marked, deleting a zone asks for a second click.
- The server plugin is called VoicePhysics. Its settings folder moves from `plugins/VoicechatAudioDistance` by itself.

#### Fixed
- The server plugin showed text keys instead of messages, for example in `/vcd` replies.

### Русский

#### Добавлено
- Экран настроек прокручивается: в маленьком окне и при крупном интерфейсе ничего не обрезается.
- Вкладка «HUD» с живым предпросмотром.
- `/voicephysics` открывает настройки. В подсказке при первом входе и в сообщении о профиле сервера есть ссылка на них.
- `/vcd zone show <имя>` и кнопка «Показать» на вкладке «Сервер» рисуют границы зоны-бокса частицами на 30 секунд.
- Серверный плагин работает на Folia.
- Короткое уведомление в HUD, когда сервер скрывает игроков рядом.

#### Изменено
- Материалы перенесены на вкладку «Стены», в раскрывающийся раздел.
- Код профиля — в нижней панели и работает с любой вкладки.
- «Сбросить» сбрасывает только открытую вкладку.
- Заблокированные сервером настройки отмечены замком и объясняют почему.
- Пресет больше не меняет стены, если сервер их заблокировал.
- Понятные слова вместо технических единиц; подсказки короче.
- Вкладка «Сервер»: настройки по разделам, зона, в которой вы стоите, отмечена, удаление зоны просит второй клик.
- Серверный плагин называется VoicePhysics. Папка настроек сама переносится из `plugins/VoicechatAudioDistance`.

#### Исправлено
- Серверный плагин показывал ключи текстов вместо сообщений, например в ответах `/vcd`.

## [2.1.0] - 2026-09-26

### English

#### Added
- Echo now depends on the blocks around you: stone and ice echo more, wood less, wool and leaves hardly at all.
- Distinct echo off cliffs and canyon walls under the open sky.
- Echo from the speaker's surroundings (a voice from a cave echoes even if you are outside).
- The Effects tab shows the type of place you are in and its echo.
- "Round a corner" label in the HUD.

#### Changed
- Voices close to you have less echo than far ones.
- A voice behind a wall is heard from the nearest doorway, and its direction changes smoothly.
- Sharper corners muffle voices more.
- The edge volume no longer flattens the curve: voices fade smoothly down to it at the edge of the range.
- "Hear you" in the HUD counts your voice chat group (needs the addon on the server).
- Server wall strength can be set in 5% steps.

#### Fixed
- Players standing next to each other in tunnels and narrow corridors heard each other muffled.
- Voices flickered between muffled and clear near block edges and hill crests.
- Forests and open areas had room echo.

### Русский

#### Добавлено
- Эхо зависит от блоков вокруг: камень и лёд отражают сильнее, дерево слабее, шерсть и листва почти не отражают.
- Отчётливое эхо от скал и стен каньона под открытым небом.
- Эхо от окружения говорящего (голос из пещеры звучит с эхом, даже если вы снаружи).
- Вкладка «Эффекты» показывает тип места, где вы находитесь, и его эхо.
- Пометка «из-за угла» в HUD.

#### Изменено
- У близких голосов меньше эха, чем у дальних.
- Голос за стеной слышен со стороны ближайшего проёма, направление меняется плавно.
- Крутые углы глушат голос сильнее.
- Громкость на краю больше не делает кривую плоской: голос плавно затихает до неё к границе дистанции.
- «Вас слышат» в HUD учитывает вашу группу в голосовом чате (нужен аддон на сервере).
- Силу стен на сервере можно задавать с шагом 5%.

#### Исправлено
- Игроки рядом в тоннелях и узких коридорах слышали друг друга приглушённо.
- Голос то глох, то становился чистым у краёв блоков и на гребнях холмов.
- В лесу и на открытых местах было эхо как в помещении.

## [2.0.2] - 2026-09-26

### English

#### Added
- Support for Minecraft 1.20.2 – 1.20.6.
- `/vcd` replies and server messages in each player's game language on 1.20.2+.

### Русский

#### Добавлено
- Поддержка Minecraft 1.20.2 – 1.20.6.
- Ответы `/vcd` и сообщения сервера на языке игры каждого игрока на 1.20.2+.

## [2.0.1] - 2026-09-26

### English

#### Added
- Server options for Simple Voice Chat groups: dead players silent in groups, spectators heard only by spectators, isolated zones cut group voices (all off by default).
- `/vcd debug` shows the player's group; the HUD shows whether your group is open or isolated.
- Server wall strength in 10% steps.

#### Fixed
- The download link in the "addon required" message is now clickable.
- In open groups, nearby players now hear you at zone range, sneaking and megaphone distance.

### Русский

#### Добавлено
- Настройки сервера для групп Simple Voice Chat: мёртвых не слышно и в группе, наблюдателей слышат только наблюдатели, изолированные зоны отрезают голоса групп (по умолчанию всё выключено).
- `/vcd debug` показывает группу игрока; HUD показывает, открытая ваша группа или изолированная.
- Сила стен на сервере с шагом 10%.

#### Исправлено
- Ссылка на скачивание в сообщении «нужен аддон» теперь кликабельная.
- В открытых группах игроки рядом теперь слышат вас с учётом зон, корточек и мегафона.

## [2.0.0] - 2026-09-26

### English

First beta.

#### Added
- Servers can lock only some settings (curve, walls, materials or effects) and leave the rest to players.
- Servers can turn off the monitor, the radar and nearby players in the HUD (for PvP).

### Русский

Первая бета.

#### Добавлено
- Сервер может закрепить только часть настроек (кривую, стены, материалы или эффекты), а остальное оставить игрокам.
- Сервер может выключить монитор, радар и игроков рядом в HUD (для PvP).

## [1.8.0] - 2026-09-26

### English

#### Added
- Box zones drawn in game with `/vcd zone`.
- Zones can change voice range, be soundproof, set wall strength, a fixed echo and a message on entering.
- Rules: quieter when sneaking, dead players silent, spectators heard only by spectators, megaphone item.
- Option to require the addon: a message or a kick for players without it.
- Server tab in the settings screen for operators.
- `/vcd debug <player>`: who a player hears and who hears them.
- Server messages in seven languages, editable.

### Русский

#### Добавлено
- Зоны-боксы, которые рисуются в игре через `/vcd zone`.
- Зоны могут менять дальность голоса, быть звуконепроницаемыми, задавать силу стен, постоянное эхо и сообщение при входе.
- Правила: на корточках тише, мёртвых не слышно, наблюдателей слышат только наблюдатели, предмет-мегафон.
- Можно требовать аддон: сообщение или кик для игроков без него.
- Вкладка «Сервер» в настройках для операторов.
- `/vcd debug <игрок>`: кого слышит игрок и кто слышит его.
- Сообщения сервера на семи языках, их можно менять.

## [1.7.0] - 2026-09-26

### English

#### Added
- HUD size, background opacity and a compact mode.
- Color-blind friendly colors.
- Radar marks differ by shape as well as color.
- Profile codes to share your sound settings as one line of text.
- The monitor shows how much time the addon takes; it does less work on slow machines.

#### Changed
- Quiet voices (between words) have a hollow mark in the HUD.

### Русский

#### Добавлено
- Размер HUD, прозрачность фона и компактный режим.
- Цвета для дальтоников.
- Метки на радаре различаются не только цветом, но и формой.
- Коды профиля: настройки звука одной строкой, чтобы поделиться.
- Монитор показывает, сколько времени занимает аддон; на слабых компьютерах он работает реже.

#### Изменено
- Тихий голос (между словами) отмечен в HUD пустой меткой.

## [1.6.0] - 2026-09-26

### English

#### Added
- New materials: metal, earth and sand, soft blocks, ice, and "other blocks" (including modded blocks).
- More examples in the wall preview.

#### Changed
- Presets adapt to the server's voice range.
- The distance graph is more compact and easier to read.
- Files are renamed to `voice-physics-…`. Delete the old file when updating; settings are kept.

#### Fixed
- The Listen button looked disabled.

### Русский

#### Добавлено
- Новые материалы: металл, земля и песок, мягкие блоки, лёд и «остальные блоки» (в том числе из модов).
- Больше примеров в превью стен.

#### Изменено
- Пресеты подстраиваются под дальность голоса сервера.
- График дальности компактнее и понятнее.
- Файлы переименованы в `voice-physics-…`. При обновлении удалите старый файл; настройки сохраняются.

#### Исправлено
- Кнопка «Прослушать» выглядела выключенной.

## [1.5.0] - 2026-09-26

### English

#### Added
- Voices come through nearby doorways and windows instead of only through walls.
- `/vcd` commands for server admins.
- Sound zones per world or WorldGuard region.
- Full version on NeoForge 26.x.

#### Changed
- The mod is called Voice Physics everywhere.
- The HUD moved to the top right and shows distance units.
- The radar has a legend; Listen turns into Stop while playing.

### Русский

#### Добавлено
- Голос проходит через ближайшие проёмы и окна, а не только сквозь стены.
- Команды `/vcd` для админов сервера.
- Звуковые зоны для мира или региона WorldGuard.
- Полная версия на NeoForge 26.x.

#### Изменено
- Мод везде называется Voice Physics.
- HUD переехал в правый верхний угол и показывает единицы расстояния.
- У радара есть легенда; «Прослушать» во время звучания превращается в «Стоп».

## [1.4.0] - 2026-09-25

### English

#### Added
- Echo in caves and halls.
- Dull voices under water.
- Rain and thunder make far voices harder to hear.
- Effects tab with switches for echo, water and weather.
- Voice HUD: who is talking, how far and where; how many players hear you.
- Direction arrows and a radar in the monitor.
- Voice chat status of nearby players (Simple Voice Chat 2.6.1+).
- Listen button to hear the distance curve.
- Ukrainian, German, Spanish, Portuguese (Brazil) and Chinese.

### Русский

#### Добавлено
- Эхо в пещерах и залах.
- Глухие голоса под водой.
- Дождь и гроза заглушают дальние голоса.
- Вкладка «Эффекты» с переключателями эха, воды и погоды.
- HUD голоса: кто говорит, как далеко и где; сколько игроков вас слышат.
- Стрелки направления и радар в мониторе.
- Статус голосового чата игроков рядом (Simple Voice Chat 2.6.1+).
- Кнопка «Прослушать» для кривой громкости.
- Украинский, немецкий, испанский, португальский (Бразилия) и китайский.

## [1.3.0] - 2026-09-25

### English

#### Added
- The monitor lists everyone in voice range, not only those talking.
- With the addon on the server, the monitor shows who has voice chat off, disconnected or muted.

### Русский

#### Добавлено
- Монитор показывает всех в радиусе голоса, а не только говорящих.
- С аддоном на сервере монитор показывает, у кого голосовой чат выключен, отключён или без звука.

## [1.2.4] - 2026-09-25

### English

#### Fixed
- The exponential curve was not really exponential.
- 1/r and exponential curves cut voices off at the edge of the range.

### Русский

#### Исправлено
- Экспоненциальная кривая была не экспоненциальной.
- Кривые 1/r и экспонента обрывали голос на краю дальности.

## [1.2.3] - 2026-09-25

### English

#### Added
- The Paper / Purpur / Spigot plugin supports Minecraft 1.20.1 – 26.3.

### Русский

#### Добавлено
- Плагин для Paper / Purpur / Spigot поддерживает Minecraft 1.20.1 – 26.3.

## [1.2.2] - 2026-09-25

### English

#### Added
- Support for Minecraft 26.1 – 26.2.

#### Fixed
- The mod icon did not show in Mod Menu.

### Русский

#### Добавлено
- Поддержка Minecraft 26.1 – 26.2.

#### Исправлено
- Иконка мода не показывалась в Mod Menu.

## [1.2.1] - 2026-09-25

### English

#### Changed
- Settings files are ordered and commented in English and Russian.
- Simpler server settings; walls are the same for everyone.

### Русский

#### Изменено
- Файлы настроек упорядочены и прокомментированы на английском и русском.
- Настройки сервера проще; стены одинаковые для всех.

## [1.2.0] - 2026-09-25

### English

#### Added
- Optional server side (Fabric and Paper / Purpur / Spigot): walls for players without the addon, a server sound profile, exact whisper range.
- New settings screen with Distance, Walls, Materials and Monitor tabs.
- Adjustable wall strength per material.

#### Changed
- Wall muffling is much more noticeable, without clicks.

#### Fixed
- The mod did not load on 1.20 and 1.21.
- Crashes and broken walls on some 1.21.x versions.
- Missing texts on 26.x; glass, leaves and doors did not count as walls.
- Voices stayed slightly muffled after the first wall.

### Русский

#### Добавлено
- Необязательная серверная часть (Fabric и Paper / Purpur / Spigot): стены для игроков без аддона, профиль звука сервера, точная дальность шёпота.
- Новый экран настроек с вкладками «Дистанция», «Стены», «Материалы» и «Монитор».
- Сила стен для каждого материала.

#### Изменено
- Приглушение стенами стало намного заметнее и без щелчков.

#### Исправлено
- Мод не загружался на 1.20 и 1.21.
- Вылеты и неработающие стены на некоторых версиях 1.21.x.
- Недостающие тексты на 26.x; стекло, листва и двери не считались стенами.
- После первой стены голос оставался слегка приглушённым.

## [1.1.0] - 2026-09-25

### English

#### Added
- Voices behind walls are muffled.
- Files for Forge, NeoForge and Minecraft 1.20.1, 1.21.x and 26.x.

### Русский

#### Добавлено
- Голоса за стенами приглушаются.
- Файлы для Forge, NeoForge и Minecraft 1.20.1, 1.21.x и 26.x.

## [1.0.0] - 2026-09-25

### English

#### Added
- Distance curves: linear, 1/r and exponential.
- Volume at the edge of the range.
- Whisper falloff setting.
- Live graph of volume by distance.
- Presets.
- Settings from Controls, Mod Menu and Simple Voice Chat's settings.
- English and Russian.

### Русский

#### Добавлено
- Кривые громкости: линейная, 1/r и экспоненциальная.
- Громкость на краю дальности.
- Настройка спада шёпота.
- Живой график громкости по расстоянию.
- Пресеты.
- Настройки из «Управления», Mod Menu и настроек Simple Voice Chat.
- Английский и русский.
