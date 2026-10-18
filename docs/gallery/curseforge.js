// Renders the pictures of the CurseForge description (docs/images/curseforge/*.png): section headings
// and the facts strip from banner.html, and 840 px copies of the cover and screenshots. CurseForge
// refuses pictures 850 px wide or wider.
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
};
const facts = [
  "Install on|client, server or both",
  "Loaders|Fabric · Quilt · Forge · NeoForge · Paper",
  "Minecraft|1.16.5 – 26.3",
];
const copies = {
  cover: "docs/images/gallery/cover.png",
  walls: "docs/images/ui-walls.png",
  effects: "docs/images/ui-effects.png",
  monitor: "docs/images/ui-monitor.png",
  server: "docs/images/ui-server.png",
};

(async () => {
  const browser = await chromium.launch();
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
  for (const [name, file] of Object.entries(copies)) {
    await tab.setContent(`<img src="${pathToFileURL(path.join(root, file)).href}" style="display:block;width:840px">`);
    await tab.evaluate(() => document.images[0].decode());
    await tab.locator("img").screenshot({ path: out(name) });
  }
  await browser.close();
})();
