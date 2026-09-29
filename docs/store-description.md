# Store pages / Страницы на площадках

The Modrinth project page is `docs/modrinth.md`: the whole page, in English, with the Russian translation collapsed in a `<details>` block at the end. It is short and tells players what they get; settings, commands and details belong in the README. The *Modrinth page* workflow uploads the page and the summary below to Modrinth on every change pushed to `main` (it needs the `MODRINTH_TOKEN` secret with the *Write projects* scope). CurseForge cannot be updated this way: switch its description editor to Markdown and paste `docs/curseforge.md` by hand. That file is the same page in English only, since CurseForge limits the description's length, and it points Paper servers to the plugin; keep the two files in step. The gallery pictures are listed in `docs/gallery.json` (file, title, description, featured; the list's order is their order) and the same workflow puts them into the Modrinth gallery, replacing the picture with the same title; pictures added on the site by hand stay. The pictures come from the in-game client test: it shoots the `store-*` set (1920x1080, English) and CI keeps it in the `client-fabric-1.21` artifact; copy the files from there to `docs/images/`. CurseForge has no API for pictures: add the same files from `docs/images/` in the project's *Images* tab. The plugin has its own CurseForge project in the Bukkit Plugins section; its page is `docs/curseforge-plugin.md`, pasted the same way.

Страница проекта на Modrinth — это `docs/modrinth.md`: вся страница на английском, а русский перевод свёрнут в блок `<details>` в конце. Она короткая и рассказывает игрокам, что они получат; настройки, команды и подробности — в README. Workflow *Modrinth page* выкладывает страницу и краткое описание ниже на Modrinth при каждом изменении в `main` (нужен секрет `MODRINTH_TOKEN` с правом *Write projects*). CurseForge так обновить нельзя: переключите редактор описания на Markdown и вставьте `docs/curseforge.md` вручную. Это та же страница только на английском, потому что CurseForge ограничивает длину описания, и в ней серверы на Paper отправляются к плагину; держите оба файла одинаковыми. Картинки галереи перечислены в `docs/gallery.json` (файл, заголовок, описание, featured; порядок списка — порядок картинок), тот же workflow кладёт их в галерею Modrinth, заменяя картинку с тем же заголовком; добавленные на сайте вручную остаются. Картинки снимает клиентский игровой тест: набор `store-*` (1920x1080, английский), CI хранит его в артефакте `client-fabric-1.21`; скопируйте файлы оттуда в `docs/images/`. У CurseForge для картинок нет API: добавьте те же файлы из `docs/images/` во вкладке *Images* проекта. У плагина свой проект на CurseForge в разделе Bukkit Plugins; его страница — `docs/curseforge-plugin.md`, вставляется так же.

---

## English

**Name:** Simple Voice Chat: Voice Physics

**Summary:** Voices that behave like sound: they fade with distance, get muffled by walls, come round corners and echo in caves. A HUD shows who is talking and who hears you. Servers get sound zones, game rules and walls even for players without the addon.

**Categories:** Utility, Social, Game Mechanics

**Icon:** `icon.png` in the repository root (512×512).

## Русский

**Название:** Simple Voice Chat: Voice Physics

**Краткое описание** (на площадках только на английском, как выше). Перевод: Голоса ведут себя как звук: затихают с расстоянием, глохнут за стенами, доносятся из-за угла и отдаются эхом в пещерах. HUD показывает, кто говорит и кто слышит вас. Серверу — звуковые зоны, правила игры и стены даже для игроков без аддона.

**Категории:** Utility, Social, Game Mechanics

**Иконка:** `icon.png` в корне репозитория (512×512).
