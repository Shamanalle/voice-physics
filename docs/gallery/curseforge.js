// Renders the pictures of the CurseForge descriptions of the mod and the plugin
// (docs/images/curseforge/*.png): section headings and facts strips from banner.html, and 840 px
// copies of the cover and screenshots. CurseForge refuses pictures 850 px wide or wider.
// Run from the repo root: NODE_PATH=$(npm root -g) node docs/gallery/curseforge.js
const { chromium } = require("playwright");
const path = require("path");
const { pathToFileURL } = require("url");

const root = path.resolve(__dirname, "../..");
const out = name => path.join(root, "docs/images/curseforge", name + ".png");
const banner = pathToFileURL(path.join(__dirname, "banner.html")).href;

const headings = {
  players: { title: "For players", text: "Install it on your client. Works on any server, even without the addon there.", accent: "#3fd0e6" },
  servers: { title: "For servers", text: "Install it on the server, or the plugin on Paper. Players need nothing.", accent: "#f2c46d" },
  files: { title: "Which file do I need?", text: "One file for Fabric, one for Forge and NeoForge, the plugin for Paper.", accent: "#a98bff" },
  // The plugin's page (docs/curseforge-plugin.html)
  "plugin-hear": { title: "What players hear", text: "For everyone on the server, with only Simple Voice Chat installed.", accent: "#3fd0e6" },
  "plugin-admin": { title: "Tools for admins", text: "Zones, rules and commands, all changed in game with /vcd.", accent: "#f2c46d" },
  "plugin-install": { title: "Install", text: "One file for Paper, Purpur, Folia and Spigot 1.16.5 – 26.3.", accent: "#a98bff" },
};
const facts = [
  "Install on|client, server or both",
  "Loaders|Fabric · Quilt · Forge · NeoForge · Paper",
  "Minecraft|1.16.5 – 26.3",
];
const pluginFacts = [
  "Players need|nothing but voice chat",
  "Servers|Paper · Purpur · Folia · Spigot",
  "Minecraft|1.16.5 – 26.3",
];
// The plugin's cover, in the gallery's size for its CurseForge gallery and as an 840 px copy
const pluginCover = {
  for: "A plugin for Simple Voice Chat",
  title: "Voice Physics",
  text: "Voices are muffled behind walls for every player, sound zones change how far a voice carries, and game rules decide who hears whom.",
  chips: ["Walls for everyone", "Sound zones", "Game rules", "Megaphone", "Water and rain", "Echo", "/voice", "Mute", "PlaceholderAPI", "WorldGuard"],
  shot: "docs/images/ui-server.png",
  loaders: "<b>Paper · Purpur · Folia · Spigot</b> &nbsp; Minecraft 1.16.5 – 26.3 &nbsp; Players need nothing",
};
const copies = {
  cover: "docs/images/gallery/cover.png",
  "plugin-cover": "docs/images/gallery/cover-plugin.png",
  walls: "docs/images/ui-walls.png",
  effects: "docs/images/ui-effects.png",
  monitor: "docs/images/ui-monitor.png",
  server: "docs/images/ui-server.png",
};

(async () => {
  const browser = await chromium.launch();
  const big = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  const cq = new URLSearchParams({ cover: "", for: pluginCover.for, title: pluginCover.title, text: pluginCover.text,
    shot: "../../" + pluginCover.shot, loaders: pluginCover.loaders });
  pluginCover.chips.forEach(chip => cq.append("chip", chip));
  await big.goto(pathToFileURL(path.join(__dirname, "page.html")).href + "?" + cq);
  await big.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(i => i.decode())); });
  await big.screenshot({ path: path.join(root, "docs/images/gallery/cover-plugin.png") });
  const tab = await browser.newPage({ viewport: { width: 840, height: 600 } });
  const shoot = async (query, name) => {
    await tab.goto(banner + "?" + query);
    await tab.evaluate(() => document.fonts.ready);
    await tab.locator(".box").screenshot({ path: out(name), omitBackground: true });
  };
  for (const [name, h] of Object.entries(headings)) await shoot(new URLSearchParams(h).toString(), "heading-" + name);
  const q = new URLSearchParams({ facts: "", accent: "#3fd0e6" });
  facts.forEach(f => q.append("fact", f));
  await shoot(q.toString(), "facts");
  const pq = new URLSearchParams({ facts: "", accent: "#3fd0e6" });
  pluginFacts.forEach(f => pq.append("fact", f));
  await shoot(pq.toString(), "plugin-facts");
  for (const [name, file] of Object.entries(copies)) {
    await tab.setContent(`<img src="${pathToFileURL(path.join(root, file)).href}" style="display:block;width:840px">`);
    await tab.evaluate(() => document.images[0].decode());
    await tab.locator("img").screenshot({ path: out(name) });
  }
  await browser.close();
})();
