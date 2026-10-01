# Changelog

All notable changes to this project are documented in this file. Each version is described in English first, then in Russian.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

*Все заметные изменения проекта описываются в этом файле. Каждая версия описана сначала на английском, затем на русском.*

## [2.11.0] - 2026-10-01

### English

#### Added
- **The Paper/Purpur/Folia/Spigot plugin now also runs on Minecraft 1.18.2 – 1.19.4** (it needed 1.20 before). The same jar; Java 17 is enough. `api-version` in `plugin.yml` is lowered to 1.18 so older servers load it.
- The plugin is checked against every Paper API from 1.18.2 on (`compat-bukkit.yml`) and started on real Paper 1.18.2 and 1.19.4 servers in the in-game tests.
- Fabric, Forge and NeoForge files are unchanged: they stay 1.20 and newer.

### Русский

#### Добавлено
- **Плагин Paper/Purpur/Folia/Spigot теперь работает и на Minecraft 1.18.2 – 1.19.4** (раньше нужна была 1.20). Тот же jar, достаточно Java 17. `api-version` в `plugin.yml` снижен до 1.18, чтобы старые серверы его загружали.
- Плагин проверяется на каждом Paper API начиная с 1.18.2 (`compat-bukkit.yml`) и запускается на настоящих серверах Paper 1.18.2 и 1.19.4 в игровых тестах.
- Файлы Fabric, Forge и NeoForge не изменились: по-прежнему 1.20 и новее.

## [2.10.1] - 2026-10-01

### English

#### Added
- **Server tab: effects for players without the addon.** A new "Effects without the addon" section sets what `/vcd effects` sets: effects on/off, air on/off, and water, weather and echo strength in 10% steps (water and weather up to 150%, echo up to 100%, 0 = off). Every button sends the same `/vcd` command, so nothing changes for the console.
- **Server tab: plugin extras.** On the Paper/Folia plugin only, a new "Plugin extras" section (closed by default) has a switch for each of radio, loudspeakers, eavesdrop item, sculk and wardens, doorway sound and integrations, and a step button for the eavesdrop factor. The section is not shown on Fabric, Forge or NeoForge servers, which have none of these. The extras are still not play-tested; the section says so.
- The buttons grey out for players without the `vcd.settings` permission, as the others do.

#### Not in the panel, on purpose
Placing and removing loudspeakers, the radio and eavesdrop items, mutes, `undo`, `report`, `debug`, `reload` and the Towny/Lands zone names stay commands only: they need text, a place or a player, or are rare.

### Русский

#### Добавлено
- **Вкладка «Сервер»: эффекты для игроков без аддона.** Новый раздел «Эффекты без аддона» задаёт то же, что `/vcd effects`: эффекты вкл/выкл, воздух вкл/выкл, силу воды, погоды и эха шагами по 10% (вода и погода до 150%, эхо до 100%, 0 = выключено). Каждая кнопка отправляет ту же команду `/vcd`, так что для консоли ничего не меняется.
- **Вкладка «Сервер»: дополнения плагина.** Только на плагине Paper/Folia появился раздел «Дополнения плагина» (по умолчанию свёрнут): переключатель для рации, громкоговорителей, предмета подслушивания, скалка и вардена, звука через двери и интеграций, и кнопка шага для коэффициента подслушивания. На серверах Fabric, Forge и NeoForge раздела нет: там этих возможностей нет. Дополнения всё ещё не проверены в игре, об этом сказано в разделе.
- Кнопки неактивны у игроков без права `vcd.settings`, как и остальные.

#### Намеренно не в панели
Расстановка и удаление громкоговорителей, предметы рации и подслушивания, мьюты, `undo`, `report`, `debug`, `reload` и названия зон Towny/Lands остаются только командами: им нужен текст, место или игрок, либо они нужны редко.

## [2.10.0] - 2026-09-30

### English

> **Everything new in this release is for the Paper/Folia plugin, off by default and not yet tried in a live game.** Turn each one on with `/vcd extras <name> on` on a test server first; `/vcd undo` takes a switch back. The mods are unchanged apart from the version number.

#### Added
- **`/vcd extras`** lists the new features with a switch each, in the server file's section 12 (`server_radio`, `server_speakers`, `server_eavesdrop`, `server_sculk`, `server_doorway`, `server_integrations`; all `false`). Changes can be undone and appear in the change log.
- **Radio.** `/voice radio <1-9999|off>` tunes a player's radio (permission `vcd.player.radio`, on for everyone): players on the same frequency hear each other at any distance and in any world, like a voice in the ear, unless they already hear the speaker by distance or ignore them. `radio_item` (for example `minecraft:clock`) makes holding that item necessary. `/vcd radio` shows who is on the air and sets the item (`/vcd radio item <id>|none`). Keys `server_radio`, `radio_item`.
- **Loudspeakers.** `/vcd speaker add <name> [radius] [pickup]` places a loudspeaker where you stand; whoever talks within its pickup distance is heard from it by every player within its radius (up to 256 blocks), as a located sound. `list`, `tp <name>` and `remove <name>` manage them (permission `vcd.speaker`; `list` needs only `vcd.status`). Saved as `speaker.<name>=world|x|y|z|pickup|radius`.
- **Eavesdrop item.** A player holding `eavesdrop_item` (default `minecraft:spyglass`) hears walls as thinner: the muffling is multiplied by `eavesdrop_factor` (default 0.3, 0.05 - 1). `/vcd eavesdrop [item <id>|none|factor <0.05-1>]`. Works for players without the addon.
- **Sculk and wardens.** A shout (`/voice mode shout`) or a megaphone voice raises a game event at the speaker that sculk sensors and wardens react to, at most once every 3 seconds; whispers, sneaking, ordinary talking and the dead raise nothing. Switch `/vcd extras sculk on`.
- **Doorway sound** for players without the addon: behind a wall, a voice with a clearly better way round through an open door or gap is heard from that opening, muffled only by the bends, instead of through the wall (at most 6 searches per tick, 48 blocks). Any failure leaves the muffled straight path. Switch `/vcd extras doorway on`.
- **Integrations** (`/vcd extras integrations on`, each needs its plugin, all by reflection): Towny towns and Lands lands as zones (`zone.town.<name>`, `zone.land.<name>`, `/vcd zone set town:<name> ...`); a WorldGuard region flag `vcd-zone` that puts a region in one of the settings file's zones by name; LuckPerms contexts `vcd:mode`, `vcd:walls` and `vcd:zone`.

#### Fixed
- The Modrinth page workflow no longer fails when a gallery picture is already there.

### Русский

> **Всё новое в этом выпуске — для плагина Paper/Folia, выключено по умолчанию и ещё не опробовано в живой игре.** Включайте каждое командой `/vcd extras <название> on` сначала на тестовом сервере; `/vcd undo` возвращает переключатель. Моды не изменились, кроме номера версии.

#### Добавлено
- **`/vcd extras`** показывает новые возможности с переключателем у каждой, в разделе 12 файла сервера (`server_radio`, `server_speakers`, `server_eavesdrop`, `server_sculk`, `server_doorway`, `server_integrations`; все `false`). Изменения можно отменить, они попадают в журнал.
- **Рация.** `/voice radio <1-9999|off>` настраивает рацию игрока (право `vcd.player.radio`, у всех включено): игроки на одной частоте слышат друг друга на любом расстоянии и в любом мире, как голос в ухе, если только не слышат говорящего и так по расстоянию или не игнорируют его. `radio_item` (например `minecraft:clock`) делает обязательным предмет в руке. `/vcd radio` показывает, кто в эфире, и задаёт предмет (`/vcd radio item <id>|none`). Ключи `server_radio`, `radio_item`.
- **Громкоговорители.** `/vcd speaker add <имя> [радиус] [захват]` ставит громкоговоритель там, где вы стоите; того, кто говорит в пределах захвата, слышат из него все игроки в его радиусе (до 256 блоков) как позиционный звук. `list`, `tp <имя>` и `remove <имя>` управляют ими (право `vcd.speaker`; для `list` хватает `vcd.status`). Хранится как `speaker.<имя>=мир|x|y|z|захват|радиус`.
- **Предмет для подслушивания.** Игрок с `eavesdrop_item` в руке (по умолчанию `minecraft:spyglass`) слышит стены тоньше: приглушение умножается на `eavesdrop_factor` (по умолчанию 0.3, от 0.05 до 1). `/vcd eavesdrop [item <id>|none|factor <0.05-1>]`. Работает и для игроков без аддона.
- **Скалк и вардены.** Крик (`/voice mode shout`) или голос с мегафоном создаёт у говорящего игровое событие, на которое реагируют скалковые датчики и вардены, не чаще раза в 3 секунды; шёпот, корточки, обычная речь и мёртвые ничего не создают. Переключатель `/vcd extras sculk on`.
- **Звук через дверной проём** для игроков без аддона: за стеной голос, у которого есть заметно лучший путь через открытую дверь или проём, слышен из этого проёма, приглушённый только изгибами, а не через стену (не больше 6 поисков за тик, 48 блоков). При любом сбое остаётся приглушённый прямой путь. Переключатель `/vcd extras doorway on`.
- **Интеграции** (`/vcd extras integrations on`, каждой нужен свой плагин, всё через reflection): города Towny и земли Lands как зоны (`zone.town.<имя>`, `zone.land.<имя>`, `/vcd zone set town:<имя> ...`); флаг региона WorldGuard `vcd-zone`, относящий регион к зоне из файла настроек по имени; контексты LuckPerms `vcd:mode`, `vcd:walls` и `vcd:zone`.

#### Исправлено
- Workflow страницы Modrinth больше не падает, если картинка галереи уже есть.

## [2.9.0] - 2026-09-30

### English

#### Added
- **`/voice` for every player (Paper/Folia), with or without the mod.** Choose how loud you talk (`mode quiet|normal|shout`: half, normal or double range; shouting needs the `vcd.shout` permission), turn wall muffling off for yourself (`walls off`), set how loud a player is to you (`volume <player> <0-100>`) or ignore one (`ignore`), and show who is talking near you above the hotbar (`hud on`). Choices are saved per player in `vc-audio-distance-players.properties`. Replies are clickable: *Your choices* shows buttons for every value, every change has an *Undo*, `/voice help` has examples, `/voice menu` opens the same choices as an inventory menu, and each part has its own permission (`vcd.player.mode`, `.walls`, `.hud`, `.volume`, `.menu`).
- A shout does not stretch a range a zone sets and does not add to a megaphone; the talking line follows `allow_monitor` and hides players hidden by vanish plugins; `vcd.admin` includes `vcd.shout`. A volume below 100% and wall muffling apply together.
- `/voice walls off` is refused while the server has enforced its profile with walls locked, and inside a zone that sets its own wall strength; the walls then apply to everyone there. `/vcd debug` says "ignored" when a listener turned a player off with `/voice ignore`.
- **Server realism for players without the addon** (off by default; `/vcd effects on`). The server dulls voices under water, covers far ones in rain and thunder, and gives a speaker in a cave or hall an echo for a listener outside it, the same physics the addon does at home. `/vcd effects air on` also dulls voices a little as they near the edge of their range. Strengths are the server profile's (`/vcd effects water|weather|echo <0-150>`). Water, rain and air work on Paper, Fabric, Forge and NeoForge; measuring a room for the echo is Paper only, and a zone with its own echo works everywhere. Keys `server_effects` and `server_air`.
- **Mute:** `/vcd mute <player> [time] [reason]`, `/vcd unmute <player>`, `/vcd mutes`. Nobody hears a muted player for the time given (`30s`, `10m`, `2h`, `1d`, `1w`, `perm`) or until unmuted. Kept across restarts, in the change log, with an *Undo* and an *Unmute* button, in `/vcd debug`; permission `vcd.mute`.
- **PlaceholderAPI (Paper):** `%vcd_mode%`, `%vcd_mode_name%`, `%vcd_talking%`, `%vcd_muted%`, `%vcd_mute_left%`, `%vcd_mute_reason%`, `%vcd_range%`, `%vcd_whisper_range%`, `%vcd_walls%`, `%vcd_zone%`, `%vcd_addon%`, `%vcd_talking_near%`, `%vcd_muted_count%`.
- **Bug reports in one click:** `/vcd report` (permission `vcd.debug`) and `/voicephysics report` give one short text to copy, with versions, the settings that change how voices sound, load and the last problems. Player and zone names are left out.
- **Block materials from data files.** A resource pack, a data pack or a mod's own jar can list blocks and block tags with a material in `voice_physics/materials.json` (`assets/<namespace>/` for the client, `data/<namespace>/` for the server), for blocks of other mods the automatic guess gets wrong. The server's and the player's own rules still come first. Mistakes are logged and shown in the reports.
- **Sound by place** (on by default, a switch on the *Effects* tab): deep caves and the deep dark ring longer, the Nether's smoke dulls far voices, the End's open void has no echo, snow and dense jungle swallow sound. Mild, on top of what is measured. Part of the server profile (`place_tuning`).
- **Accessibility:** *High contrast* (the HUD on a solid dark panel with bright text), *Direction marks* (a subtitle-style line such as `◀ Sam 3m` for voices off to the side) and a HUD up to twice the size (`hud_contrast`, `hud_markers`, `hud_scale` up to 2).
- **Statistics (plugin):** anonymous usage numbers to [bStats](https://bstats.org), on by default; `metrics=false` in the server file, or `plugins/bStats/config.yml`, turns them off. No names, addresses or chat.
- **Questions and problems:** a new README section (no walls, no HUD, locked settings, modded blocks, water and rain without the addon, how to report a bug), a documented data-file format, PlaceholderAPI table, and the new settings in both READMEs and on the store pages.
- **Audio regression test:** known signals through the filter and the echo, held to the filter's design and the echo's decay time, so a change that alters how voices sound fails a test instead of being found by ear.

#### Changed
- A zone that sets `walls_strength` now decides the walls for everyone in it, also for players without the addon: their own `/voice walls off` does not apply there (the command, the menu and the status say which zone decides), and a zone's strength works even where walls are off elsewhere on the server.
- Performance: the block lookups of one tick are shared between the wall, echo and round-the-corner measurements without creating garbage, and the addon slows its own measuring down step by step when the game gets busy, then speeds up again.

### Русский

#### Добавлено
- **`/voice` для каждого игрока (Paper/Folia), с модом или без.** Выберите, как громко говорите (`mode quiet|normal|shout`: вдвое ближе, обычно или вдвое дальше; для крика нужно право `vcd.shout`), отключите для себя приглушение стенами (`walls off`), задайте громкость игрока (`volume <игрок> <0-100>`) или игнорируйте его (`ignore`) и включите над хотбаром показ того, кто говорит рядом (`hud on`). Выбор сохраняется для каждого игрока в `vc-audio-distance-players.properties`. Ответы кликабельны: «Ваш выбор» показывает кнопки для каждого значения, у каждого изменения есть «Отменить», у `/voice help` есть примеры, `/voice menu` открывает тот же выбор как меню-инвентарь, а у каждой части своё право (`vcd.player.mode`, `.walls`, `.hud`, `.volume`, `.menu`).
- Крик не растягивает дальность, заданную зоной, и не прибавляется к мегафону; строка «кто говорит» подчиняется `allow_monitor` и не показывает скрытых vanish-плагинами игроков; `vcd.admin` включает `vcd.shout`. Громкость ниже 100% и приглушение стенами применяются вместе.
- `/voice walls off` не работает, пока сервер принудительно задал профиль и закрепил стены, и внутри зоны со своей силой стен: там стены действуют для всех. `/vcd debug` пишет «игнор», если слушающий отключил игрока командой `/voice ignore`.
- **Серверный реализм для игроков без аддона** (по умолчанию выключен; `/vcd effects on`). Сервер глушит голоса под водой, заглушает дальние в дождь и грозу и даёт эхо говорящему в пещере или зале для слушателя снаружи: та же физика, что аддон делает у себя. `/vcd effects air on` ещё и слегка глушит голоса у края дальности. Силы берутся из профиля сервера (`/vcd effects water|weather|echo <0-150>`). Вода, дождь и воздух работают на Paper, Fabric, Forge и NeoForge; измерение помещения для эха есть только на Paper, а зона с собственным эхом работает везде. Ключи `server_effects` и `server_air`.
- **Заглушение:** `/vcd mute <игрок> [время] [причина]`, `/vcd unmute <игрок>`, `/vcd mutes`. Заглушенного игрока никто не слышит заданное время (`30s`, `10m`, `2h`, `1d`, `1w`, `perm`) или пока не снимут заглушение. Хранится между перезапусками, попадает в журнал изменений, есть кнопки «Отменить» и «Снять», виден в `/vcd debug`; право `vcd.mute`.
- **PlaceholderAPI (Paper):** `%vcd_mode%`, `%vcd_mode_name%`, `%vcd_talking%`, `%vcd_muted%`, `%vcd_mute_left%`, `%vcd_mute_reason%`, `%vcd_range%`, `%vcd_whisper_range%`, `%vcd_walls%`, `%vcd_zone%`, `%vcd_addon%`, `%vcd_talking_near%`, `%vcd_muted_count%`.
- **Баг-репорт в один клик:** `/vcd report` (право `vcd.debug`) и `/voicephysics report` выдают один короткий текст для копирования: версии, настройки, меняющие звук голосов, нагрузка и последние проблемы. Имена игроков и зон не включаются.
- **Материалы блоков из файлов данных.** Ресурспак, датапак или собственный jar мода могут перечислить блоки и теги блоков с материалом в `voice_physics/materials.json` (`assets/<пространство имён>/` для клиента, `data/<пространство имён>/` для сервера) — для блоков других модов, которые автоматическое определение угадывает неверно. Собственные правила сервера и игрока по-прежнему главнее. Ошибки пишутся в лог и видны в отчётах.
- **Звук по месту** (включено по умолчанию, переключатель на вкладке «Эффекты»): в глубоких пещерах и глубинной тьме эхо дольше, дым Незера глушит дальние голоса, в открытой пустоте Энда эха нет, снег и густые джунгли поглощают звук. Мягко, поверх измеренного. Входит в профиль сервера (`place_tuning`).
- **Доступность:** «Высокий контраст» (HUD на сплошной тёмной панели с яркими буквами), «Метки направления» (строка в духе субтитров вроде `◀ Sam 3m` для голосов сбоку) и HUD до двойного размера (`hud_contrast`, `hud_markers`, `hud_scale` до 2).
- **Статистика (плагин):** анонимные цифры использования в [bStats](https://bstats.org), включены по умолчанию; `metrics=false` в файле сервера или `plugins/bStats/config.yml` выключают их. Без имён, адресов и чата.
- **Вопросы и проблемы:** новый раздел README (нет стен, нет HUD, закреплённые настройки, блоки из модов, вода и дождь без аддона, как сообщить об ошибке), описанный формат файлов данных, таблица PlaceholderAPI и новые настройки в обоих README и на страницах площадок.
- **Тест «звук не изменился»:** известные сигналы через фильтр и эхо, которые сверяются с расчётом фильтра и временем затухания эха, так что изменение, которое меняет звук голосов, роняет тест, а не обнаруживается на слух.

#### Изменено
- Зона с `walls_strength` теперь решает стены за всех в ней, в том числе за игроков без аддона: их `/voice walls off` там не действует (команда, меню и статус говорят, какая зона решает), а сила зоны работает и там, где на остальном сервере стены выключены.
- Производительность: обращения к блокам за один тик делятся между измерениями стен, эха и пути из-за угла без создания мусора, а аддон шаг за шагом замедляет собственные измерения, когда игра занята, и потом снова ускоряется.

## [2.7.1] - 2026-09-30

### English

#### Fixed
- A voice behind a wall is now heard from the doorway it comes through, and a voice from a cave echoes for you outside it. Neither had ever started: the check that starts them could never pass.
- The echo fades in over about a second and a half when you walk into a cave; after a long time in one place it used to jump there at once.
- With a player's volume above 100%, a voice no longer keeps the old, quieter volume when you move closer.
- Voices from more than 256 blocks away are not traced through walls, so a far source cannot slow the game down.
- Server walls: with many players talking, every listener's walls are measured in turn. Before, pairs beyond the first hundred or so were never measured and heard no walls.
- Paper/Folia: the block material cache is safe on several threads.
- `/vcd zone tp` takes you to the middle of the zone, not to its bottom.
- `/vcd walls 1%` is 1%, not 100% (likewise `0.5%`).
- A backslash in a zone message (`C:\underworld`) no longer breaks the settings file.
- `/vcd walls` now says whom it reaches by itself: players without the addon, and players with it only when the profile is enforced with walls locked.

### Русский

#### Исправлено
- Голос за стеной теперь слышен из дверного проёма, через который он проходит, а голос из пещеры даёт эхо и снаружи. Раньше ни то ни другое не запускалось: проверка, которая их включает, никогда не срабатывала.
- Эхо нарастает около полутора секунд, когда вы заходите в пещеру; после долгого времени на одном месте оно раньше появлялось сразу.
- При громкости игрока выше 100% голос больше не остаётся на прежней, более тихой громкости, когда вы подходите ближе.
- Голоса дальше 256 блоков не просвечиваются на стены, поэтому далёкий источник не может затормозить игру.
- Стены на сервере: когда говорит много игроков, стены каждого слушателя измеряются по очереди. Раньше пары после первой сотни примерно не измерялись и стен не слышали.
- Paper/Folia: кэш материалов блоков безопасен при работе в нескольких потоках.
- `/vcd zone tp` переносит в середину зоны, а не на её дно.
- `/vcd walls 1%` — это 1%, а не 100% (так же `0.5%`).
- Обратная косая черта в сообщении зоны (`C:\underworld`) больше не ломает файл настроек.
- `/vcd walls` теперь сам говорит, до кого доходит: до игроков без аддона, а до игроков с ним — только когда профиль принудительный и стены закреплены.

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
- Plain words instead of dB, kHz and ms per tick; shorter tooltips.
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
- Понятные слова вместо дБ, кГц и мс за тик; подсказки короче.
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
