# Store pages / Страницы на площадках

The Modrinth project page is `docs/modrinth.md`: the whole page, in English, with the Russian translation collapsed in a `<details>` block at the end. It is short and tells players what they get; settings, commands and details belong in the README. The *Modrinth page* workflow uploads the page and the summary below to Modrinth on every change pushed to `main` (it needs the `MODRINTH_TOKEN` secret with the *Write projects* scope). CurseForge cannot be updated this way: open the description editor's source code view (<>) and paste `docs/curseforge.html` by hand. That file is the same page in English only, since CurseForge limits the description's length, and it points Paper servers to the plugin; keep the two files in step. The gallery pictures are listed in `docs/gallery.json` (file, title, description, featured; the list's order is their order) and the same workflow puts them into the Modrinth gallery, replacing the picture with the same title, so keep a picture's title when you redo it; pictures added on the site by hand stay. The screenshots come from the in-game client test: it shoots the `store-*` set (3840x2160, English, no HUD or chat) and CI keeps it in the `client-fabric-1.21` artifact; scale the files to 1920x1080 and put them in `docs/images/ui-*.png` (the README uses them too). The gallery pictures in `docs/images/gallery/` are those screenshots with a heading and a line of text, plus a cover; `docs/gallery/page.html` lays them out from the `cover`, `heading`, `text` and `shot` fields of `docs/gallery.json`, and `NODE_PATH=$(npm root -g) node docs/gallery/make.js` (Playwright with Chromium) renders them again. Each picture must stay under 5 MiB. CurseForge has no API for pictures: add the files from `docs/images/gallery/` in the project's *Images* tab. The pictures in the description are copies 840 px wide in `docs/images/curseforge/`, since CurseForge refuses wider ones. The plugin has its own CurseForge project in the Bukkit Plugins section; its page is `docs/curseforge-plugin.md`, pasted the same way.

Страница проекта на Modrinth — это `docs/modrinth.md`: вся страница на английском, а русский перевод свёрнут в блок `<details>` в конце. Она короткая и рассказывает игрокам, что они получат; настройки, команды и подробности — в README. Workflow *Modrinth page* выкладывает страницу и краткое описание ниже на Modrinth при каждом изменении в `main` (нужен секрет `MODRINTH_TOKEN` с правом *Write projects*). CurseForge так обновить нельзя: откройте в редакторе описания исходный код (<>) и вставьте `docs/curseforge.html` вручную. Это та же страница только на английском, потому что CurseForge ограничивает длину описания, и в ней серверы на Paper отправляются к плагину; держите оба файла одинаковыми. Картинки галереи перечислены в `docs/gallery.json` (файл, заголовок, описание, featured; порядок списка — порядок картинок), тот же workflow кладёт их в галерею Modrinth, заменяя картинку с тем же заголовком, поэтому при переделке не меняйте заголовок; добавленные на сайте вручную остаются. Скриншоты снимает клиентский игровой тест: набор `store-*` (3840x2160, английский, без HUD и чата), CI хранит его в артефакте `client-fabric-1.21`; уменьшите файлы до 1920x1080 и положите в `docs/images/ui-*.png` (их использует и README). Картинки галереи в `docs/images/gallery/` — это те же скриншоты с заголовком и строкой текста, плюс обложка; `docs/gallery/page.html` собирает их из полей `cover`, `heading`, `text` и `shot` в `docs/gallery.json`, а `NODE_PATH=$(npm root -g) node docs/gallery/make.js` (Playwright с Chromium) рендерит заново. Каждая картинка должна быть меньше 5 МиБ. У CurseForge для картинок нет API: добавьте файлы из `docs/images/gallery/` во вкладке *Images* проекта. У плагина свой проект на CurseForge в разделе Bukkit Plugins; его страница — `docs/curseforge-plugin.md`, вставляется так же.

---

## English

**Name:** Simple Voice Chat: Voice Physics

**Summary:** Realistic proximity voice chat for Simple Voice Chat: voices fade with distance, are muffled by walls (occlusion), come through doors and echo in caves. HUD, presets and sound zones; server-side walls work even for players without the mod.

**Categories:** Utility, Social, Game Mechanics

**Icon:** `icon.png` in the repository root (512×512).

## Русский

**Название:** Simple Voice Chat: Voice Physics

**Краткое описание** (на площадках только на английском, как выше). Перевод: Реалистичный проксимити-чат для Simple Voice Chat: голоса затихают с расстоянием, глохнут за стенами (окклюзия), доносятся через двери и отдаются эхом в пещерах. HUD, пресеты и звуковые зоны; стены на сервере работают даже для игроков без мода.

**Категории:** Utility, Social, Game Mechanics

**Иконка:** `icon.png` в корне репозитория (512×512).
