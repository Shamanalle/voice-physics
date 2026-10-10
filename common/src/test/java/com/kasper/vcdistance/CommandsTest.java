package com.kasper.vcdistance;

import com.kasper.vcdistance.CommandReply.Click;
import com.kasper.vcdistance.CommandReply.Span;
import com.kasper.vcdistance.CommandReply.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** /vcd as admins use it: buttons, help, errors, undo, zone tools, suggestions and permissions. */
public class CommandsTest {

    @TempDir
    Path dir;

    private final ServerPlayers players = new ServerPlayers();
    private final ServerPlayers.Info admin = new ServerPlayers.Info(UUID.randomUUID(), "Admin", "world", 10.5, 64, 10.5,
            false, true, false, "", "", List.of(), "en_us");

    /** Whether the test server has Open Parties and Claims. */
    private boolean claimsInstalled;

    @AfterEach
    void clear() {
        players.clear();
        ZoneOutlines.clear();
    }

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    /** A context for {@code sender} with only {@code permissions} (all when empty), looking at {@code look}. */
    private AdminCommands.Context ctx(UUID sender, int[] look, String... permissions) {
        Set<String> allowed = Set.of(permissions);
        List<String> teleports = new ArrayList<>();
        return new AdminCommands.Context() {
            public String platform() {
                return "Test";
            }

            public int onlinePlayers() {
                return players.all().size();
            }

            public int addonPlayers() {
                return 0;
            }

            public void resendProfiles() {
            }

            public UUID sender() {
                return sender;
            }

            public ServerPlayers players() {
                return players;
            }

            public boolean allows(String permission) {
                return allowed.isEmpty() || allowed.contains(permission) || allowed.contains(AdminCommands.PERM_ADMIN);
            }

            public int[] targetBlock() {
                return look;
            }

            public boolean teleport(String world, double x, double y, double z) {
                teleports.add(world + " " + x + " " + y + " " + z);
                return true;
            }

            public Collection<String> worlds() {
                return List.of("world", "world_nether");
            }

            public boolean claims() {
                return claimsInstalled;
            }

            @Override
            public String toString() {
                return String.join(";", teleports);
            }
        };
    }

    private AdminCommands.Context ctx() {
        players.update(admin);
        return ctx(admin.id(), null);
    }

    private static List<Span> buttons(CommandReply reply) {
        List<Span> out = new ArrayList<>();
        for (CommandReply.Line l : reply.lines()) {
            for (Span s : l.spans()) {
                if (s.isButton() || s.click() != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static Span button(CommandReply reply, String action) {
        for (Span s : buttons(reply)) {
            if (action.equals(s.action())) {
                return s;
            }
        }
        fail("No button " + action + " in " + reply.plain());
        return null;
    }

    @Test
    @DisplayName("Templates keep their values as their own spans, in any order")
    void fill() {
        List<Span> spans = CommandReply.fill("Zone %s: %s = %s. 100%%", Style.OK, "a", new Span("b", Style.LABEL), 3);
        assertEquals("Zone a: b = 3. 100%", new CommandReply.Line(spans).plain());
        assertEquals(Style.VALUE, spans.get(1).style());
        assertEquals(Style.LABEL, spans.get(3).style());
        assertEquals(Style.OK, spans.get(0).style());
        assertEquals("b then a", new CommandReply.Line(CommandReply.fill("%2$s then %1$s", Style.PLAIN, "a", "b")).plain());
        CommandReply reply = new CommandReply().add(CommandReply.line().text("Saved.", Style.OK)
                .button("Undo", Click.RUN, "/vcd undo", null));
        assertEquals(List.of("Saved. [Undo]"), reply.plain());
        assertEquals(List.of("Saved."), reply.text(), "the Server tab gets the text without buttons");
    }

    @Test
    @DisplayName("Status values are clickable, help lists topics and shows examples, typos get a suggestion")
    void helpAndStatus() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        CommandReply status = AdminCommands.execute("", s, ctx);
        assertTrue(status.plain().get(0).startsWith("Voice Physics"));
        assertEquals(Click.SUGGEST, button(status, "/vcd walls ").click());
        button(status, "/vcd help");

        CommandReply help = AdminCommands.execute("help", s, ctx);
        for (String topic : AdminCommands.TOPICS) {
            button(help, "/vcd help " + topic);
        }
        CommandReply zone = AdminCommands.execute("help zone", s, ctx);
        assertEquals("/vcd zone", zone.plain().get(0));
        assertEquals(Click.SUGGEST, button(zone, "/vcd zone pos2 look").click());

        CommandReply typo = AdminCommands.execute("zoen", s, ctx);
        assertTrue(typo.plain().get(0).contains("zoen"), typo.plain().toString());
        button(typo, "/vcd zone ");
        assertEquals("zone", CommandHelp.nearest("zoen"));
        assertNull(CommandHelp.nearest("banana"));
    }

    @Test
    @DisplayName("Wrong values say what is allowed and offer to retype; walls above 100% are refused")
    void errors() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        CommandReply reply = AdminCommands.execute("walls 150", s, ctx);
        assertTrue(reply.plain().get(0).contains("150") && reply.plain().get(0).contains("0-100|off"), reply.plain().toString());
        assertTrue(reply.lines().get(0).spans().stream().anyMatch(sp -> sp.style() == Style.ERROR));
        button(reply, "/vcd walls ");
        button(reply, "/vcd help walls");
        assertTrue(AdminCommands.run("rule sneak", s, ctx).get(0).contains("0.1-1"));
        assertTrue(AdminCommands.run("zone set nowhere mode loud", s, ctx).get(0).contains("off|suggest|enforce"));
        assertNull(s.findZone("nowhere"), "nothing is made from a wrong value");
    }

    @Test
    @DisplayName("Undo takes back changes one by one, and says when there is nothing left")
    void undo() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        CommandReply walls = AdminCommands.execute("walls 85", s, ctx);
        button(walls, "/vcd undo");
        AdminCommands.run("walls 40", s, ctx);
        AdminCommands.run("zone create stage 5", s, ctx);
        assertEquals(3, AdminCommands.undoable(s));
        assertNotNull(s.findZone("stage"));

        List<String> undone = AdminCommands.run("undo", s, ctx);
        assertTrue(undone.get(0).contains("/vcd zone create stage 5"), undone.toString());
        assertNull(s.findZone("stage"));
        AdminCommands.run("undo", s, ctx);
        assertEquals(0.85, s.profile().getOcclusionStrength(), 1e-9);
        AdminCommands.run("undo", s, ctx);
        assertEquals(0.6, s.profile().getOcclusionStrength(), 1e-9);
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals(0.6, again.profile().getOcclusionStrength(), 1e-9, "undo is saved");
        assertTrue(AdminCommands.run("undo", s, ctx).get(0).startsWith("Nothing to undo"));

        // The same value again is no change to undo
        AdminCommands.run("walls 60", s, ctx);
        assertEquals(0, AdminCommands.undoable(s));

        // Every change and undo is in the log, newest first; nothing was logged for "walls 60" (no change)
        List<ChangeLog.Entry> log = ChangeLog.read(s);
        assertEquals(6, log.size(), log.toString());
        assertTrue(log.get(0).undo() && log.get(0).command().equals("/vcd walls 85"), log.get(0).toString());
        assertEquals("/vcd zone create stage 5", log.get(3).command());
        assertFalse(log.get(5).undo());
        assertTrue(Files.isRegularFile(dir.resolve(ChangeLog.FILE)));
        CommandReply shown = AdminCommands.execute("log", s, ctx);
        String text = String.join("\n", shown.text());
        assertTrue(text.startsWith("Changes to the settings (6)"), text);
        assertTrue(text.contains("undid /vcd walls 85"), text);
        assertTrue(AdminCommands.run("log", s, ctx(admin.id(), null, AdminCommands.PERM_STATUS)).get(0).contains("vcd.settings"),
                "the log needs vcd.settings");
        for (int i = 0; i < 12; i++) {
            AdminCommands.run("walls " + (20 + i), s, ctx);
        }
        String page = String.join("\n", AdminCommands.execute("log", s, ctx).plain());
        assertTrue(page.contains("Page 1 of 2"), page);
        assertEquals(ChangeLog.read(s).size(), 18);
    }

    @Test
    @DisplayName("/vcd block adds, lists and removes block rules; they are saved, sent in the profile and undone")
    void blockRules() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        assertTrue(AdminCommands.run("block", s, ctx).get(0).startsWith("No block rules"));

        List<String> added = AdminCommands.run("block add Create:Andesite_Casing metal", s, ctx);
        assertTrue(added.get(0).contains("create:andesite_casing") && added.get(0).contains("metal"), added.toString());
        AdminCommands.run("block add #c:glass_blocks glass", s, ctx);
        assertEquals(AcousticMaterial.METAL, s.getBlockRules().get("create:andesite_casing").material());
        assertEquals(2, s.getBlockRules().size());
        assertEquals(2, AdminCommands.undoable(s));

        // A wrong block or material says what is allowed and changes nothing
        assertTrue(AdminCommands.run("block add two words metal", s, ctx).get(0).contains("block add"));
        List<String> unknown = AdminCommands.run("block add create:x marble", s, ctx);
        assertTrue(unknown.get(0).contains("marble") && unknown.get(0).contains("stone|metal"), unknown.toString());
        assertEquals(2, s.getBlockRules().size());

        String listing = String.join("\n", AdminCommands.run("block list", s, ctx));
        assertTrue(listing.contains("Block rules (2)") && listing.contains("#c:glass_blocks"), listing);

        // Saved in the file and sent to the players in the profile
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals(s.getBlockRules(), again.getBlockRules());
        DistanceConfig sent = new DistanceConfig();
        java.util.Properties props = new java.util.Properties();
        s.profile().writeTo(props, "profile.");
        sent.readFrom(props, "profile.");
        assertEquals(s.getBlockRules(), sent.getBlockRules());

        // Locked with the materials, a player's own rules give way to the server's
        DistanceConfig player = new DistanceConfig();
        player.setBlockRules(BlockRules.EMPTY.with("mod:a", AcousticMaterial.WOOL));
        player.copyPart(DistanceConfig.Part.CURVE, s.profile());
        assertEquals(AcousticMaterial.WOOL, player.getBlockRules().get("mod:a").material());
        player.copyPart(DistanceConfig.Part.MATERIALS, s.profile());
        assertNull(player.getBlockRules().get("mod:a"));
        assertEquals(s.getBlockRules(), player.getBlockRules());
        player.resetMaterials();
        assertTrue(player.getBlockRules().isEmpty());

        assertTrue(AdminCommands.run("block remove nonsense:x", s, ctx).get(0).contains("block remove"));
        AdminCommands.run("block remove #c:glass_blocks", s, ctx);
        assertEquals(1, s.getBlockRules().size());
        AdminCommands.run("undo", s, ctx);
        assertEquals(2, s.getBlockRules().size(), "undo takes the removal back");
        AdminCommands.run("block clear", s, ctx);
        assertTrue(s.getBlockRules().isEmpty());
        assertTrue(AdminCommands.run("block add stone wool", s, ctx(admin.id(), null, AdminCommands.PERM_STATUS)).get(0)
                .contains("vcd.settings"), "block rules need vcd.settings");
    }

    @Test
    @DisplayName("The log is read as [page] [player]; a page travels to the Log screen and back whole")
    void logPages() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        for (int i = 0; i < 23; i++) {
            AdminCommands.run("walls " + (20 + i * 3), s, ctx);
        }
        String who = ChangeLog.read(s).get(0).who();

        // Words in any order: a number is the page, anything else the player
        ChangeLog.View both = ChangeLog.parseView(new String[]{"log", "Steve", "2"});
        assertEquals(2, both.page());
        assertEquals("Steve", both.who());
        assertEquals(both, ChangeLog.parseView(new String[]{"log", "2", "Steve"}));
        assertEquals(1, ChangeLog.parseView(new String[]{"log"}).page());
        assertNull(ChangeLog.parseView(new String[]{"log"}).who());
        assertEquals(ChangeLog.CONSOLE, ChangeLog.parseView(new String[]{"log", "console"}).who());

        // Ten to a page, the last one shorter, and a page out of range is the nearest one
        List<ChangeLog.Entry> all = ChangeLog.read(s);
        ChangeLog.Page third = ChangeLog.page(all, new ChangeLog.View(3, null));
        assertEquals(3, third.pages());
        assertEquals(3, third.entries().size());
        assertEquals(23, third.total());
        assertEquals(3, ChangeLog.page(all, new ChangeLog.View(99, null)).page());
        assertEquals(1, ChangeLog.page(all, new ChangeLog.View(-4, null)).page());

        // One player's changes, whatever the case of the name; nobody's gives an empty page, not a broken one
        assertEquals(23, ChangeLog.page(all, new ChangeLog.View(1, who.toUpperCase())).total());
        ChangeLog.Page nobody = ChangeLog.page(all, new ChangeLog.View(1, "Nobody"));
        assertEquals(0, nobody.total());
        assertTrue(nobody.entries().isEmpty());
        assertEquals(1, nobody.pages());
        assertTrue(AdminCommands.run("log Nobody", s, ctx).get(0).contains("Nobody"));
        assertTrue(AdminCommands.run("log 2 " + who, s, ctx).stream().anyMatch(l -> l.contains("Page 2 of 3")));

        // The reply of the server: the page written into the state and read back by the client
        java.util.Map<String, String> state = new java.util.LinkedHashMap<>();
        ChangeLog.writePage(ChangeLog.page(all, new ChangeLog.View(2, null)), state);
        java.util.Properties sent = new java.util.Properties();
        sent.putAll(state);
        ChangeLog.Page received = ChangeLog.readPage(sent);
        assertNotNull(received);
        assertEquals(2, received.page());
        assertEquals(3, received.pages());
        assertEquals(23, received.total());
        assertEquals(10, received.entries().size());
        assertEquals(all.get(10), received.entries().get(0));
        assertNull(ChangeLog.readPage(new java.util.Properties()), "a reply of another command carries no page");
    }

    @Test
    @DisplayName("Server tab: its reply carries undo, the player's permissions and the latest changes")
    void serverTabState() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        AdminCommands.run("walls 70", s, ctx);
        AdminCommands.run("notices off", s, ctx);
        java.util.Map<String, String> state = ServerHooks.tabState(s, ctx);
        assertEquals("2", state.get("undo"));
        assertEquals("true", state.get("allows." + AdminCommands.PERM_ZONE));
        assertTrue(state.get("log.0").endsWith("|Admin|false|/vcd notices off"), state.get("log.0"));
        assertTrue(state.get("log.1").endsWith("/vcd walls 70"));
        assertFalse(s.isZoneNotices());

        java.util.Map<String, String> viewer = ServerHooks.tabState(s, ctx(admin.id(), null, AdminCommands.PERM_STATUS));
        assertEquals("false", viewer.get("allows." + AdminCommands.PERM_SETTINGS));
        assertNull(viewer.get("log.0"), "the log needs vcd.settings");
        LinkProtocol.AdminReply reply = LinkProtocol.parseAdminReply(LinkProtocol.adminReply(List.of("ok"), s, state));
        assertEquals("false", reply.state().getProperty("zone_notices"));
        assertEquals("2", reply.state().getProperty("undo"));
    }

    @Test
    @DisplayName("Claim zones (Open Parties and Claims): made with claim:<player>, found for the owner or party leader")
    void claimZones() throws IOException {
        ServerSettings s = settings("zone.claim.server.voice_range=24\nzone.world.steve.walls_strength=0.2\n");
        assertEquals(Zone.CLAIM, s.findZone("claim:server").kind());
        claimsInstalled = true;
        players.update(new ServerPlayers.Info(UUID.randomUUID(), "Steve", "world", 0, 64, 0, false, true, false, "", "", List.of(), ""));
        AdminCommands.Context ctx = ctx();
        List<String> start = AdminCommands.suggestions("zone set cl", s, ctx).stream().map(AdminCommands.Suggestion::text).toList();
        assertTrue(start.contains("claim:"), start.toString());
        List<String> owners = AdminCommands.suggestions("zone set claim:", s, ctx).stream().map(AdminCommands.Suggestion::text).toList();
        assertTrue(owners.contains("claim:steve") && owners.contains("claim:admin"), owners.toString());
        assertEquals(1, owners.stream().filter("claim:server"::equals).count(), "the existing zone once: " + owners);

        AdminCommands.run("zone set claim:Steve walls_strength 90", s, ctx);
        Zone steve = s.findZone("claim:steve");
        assertNotNull(steve);
        assertEquals(Zone.CLAIM, steve.kind());
        assertEquals(0.9, steve.rules().wallsStrength(), 1e-9);
        assertEquals(Zone.WORLD, s.findZone("steve").kind(), "a plain name still finds the world first");
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertNotNull(again.findZone("claim:steve"), "saved as zone.claim.steve");

        // In a party member's claim, the party leader's zone applies; outside claims the world's
        assertSame(steve, s.zoneOf(new ServerPlayers.Info(UUID.randomUUID(), "Alex", "world_nether", 0, 64, 0, false, true, false,
                "", "", List.of("claim:alex", "claim:" + UUID.randomUUID(), "claim:steve"), "")));
        assertEquals("server", s.zoneOf(new ServerPlayers.Info(UUID.randomUUID(), "Alex", "world", 0, 64, 0, false, true, false,
                "", "", List.of("claim:server"), "")).name());
        String card = String.join("\n", AdminCommands.execute("zone info claim:steve", s, ctx).plain());
        assertTrue(card.contains("Claims steve"), card);
        CommandReply list = AdminCommands.execute("zones", s, ctx);
        assertTrue(list.lines().stream().flatMap(l -> l.spans().stream())
                .anyMatch(sp -> "/vcd zone info claim:steve".equals(sp.action())), "buttons name claims with claim:");
        assertTrue(AdminCommands.run("zone delete claim:steve confirm", s, ctx).get(0).contains("steve"));
        assertNull(s.findZone("claim:steve"));
    }

    @Test
    @DisplayName("Zones: corners from coordinates and the look, selection size, info card, rename, tp, delete asks")
    void zoneTools() throws IOException {
        ServerSettings s = settings("");
        players.update(admin);
        AdminCommands.Context ctx = ctx(admin.id(), new int[]{40, 70, 50});

        CommandReply first = AdminCommands.execute("zone pos1 ~ ~-4 ~2", s, ctx);
        assertTrue(first.plain().get(0).contains("10,60,12"), first.plain().toString());
        button(first, "/vcd zone pos2 look");
        CommandReply second = AdminCommands.execute("zone pos2 look", s, ctx);
        assertTrue(second.plain().get(1).contains("31×11×39"), second.plain().toString());
        assertFalse(ZoneOutlines.isEmpty(), "the selection is shown");
        button(second, "/vcd zone create ");
        assertTrue(AdminCommands.run("zone pos1 1 2", s, ctx).get(0).contains("x y z"));
        assertTrue(AdminCommands.run("zone pos1 look", s, ctx(admin.id(), null)).get(0).contains("64"));

        CommandReply created = AdminCommands.execute("zone create Stage", s, ctx);
        assertTrue(created.plain().get(0).contains("10,60,12") && created.plain().get(0).contains("40,70,50"));
        button(created, "/vcd zone info stage");
        assertTrue(AdminCommands.run("zone create stage", s, ctx).get(0).contains("kept"), "a second create moves the box");

        CommandReply card = AdminCommands.execute("zone info stage", s, ctx);
        assertTrue(card.plain().get(1).contains("31×11×39"), card.plain().toString());
        assertEquals(Click.SUGGEST, button(card, "/vcd zone set stage walls ").click());
        button(card, "/vcd zone delete stage");

        CommandReply choices = AdminCommands.execute("zone set stage walls", s, ctx);
        assertTrue(choices.plain().get(0).contains("default"), choices.plain().toString());
        button(choices, "/vcd zone set stage walls 50");
        AdminCommands.run("zone set stage walls 50", s, ctx);
        assertTrue(AdminCommands.run("zone set stage walls", s, ctx).get(0).contains("50%"));
        AdminCommands.run("zone set stage echo 60", s, ctx);
        assertEquals(0.6, s.findZone("stage").rules().echo(), 1e-9);

        AdminCommands.run("zone rename stage arena", s, ctx);
        assertNull(s.findZone("stage"));
        assertEquals(0.5, s.findZone("arena").rules().wallsStrength(), 1e-9, "settings kept");
        AdminCommands.run("zone set world_nether voice_range 16", s, ctx);
        AdminCommands.run("zone create other 2", s, ctx);
        assertTrue(AdminCommands.run("zone rename arena other", s, ctx).get(0).contains("already"));
        AdminCommands.run("zone delete other confirm", s, ctx);
        assertTrue(AdminCommands.run("zone rename world_nether other", s, ctx).get(0).startsWith("Only box zones"));

        assertTrue(AdminCommands.run("zone tp arena", s, ctx).get(0).contains("arena"));
        assertEquals("world 25.5 65.5 31.5", ctx.toString(), "the middle of the box (y 60 - 70), not its floor");

        CommandReply ask = AdminCommands.execute("zone delete arena", s, ctx);
        assertNotNull(s.findZone("arena"), "deleting asks first");
        assertEquals(Style.DANGER, button(ask, "/vcd zone delete arena confirm").style());
        AdminCommands.run("zone delete arena confirm", s, ctx);
        assertNull(s.findZone("arena"));

        CommandReply list = AdminCommands.execute("zones", s, ctx);
        button(list, "/vcd zone info world_nether");
    }

    @Test
    @DisplayName("The zone list has pages")
    void zonePages() throws IOException {
        StringBuilder file = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            file.append("zone.box.z").append(String.format("%02d", i)).append(".world=world\n")
                    .append("zone.box.z").append(String.format("%02d", i)).append(".from=0,0,0\n")
                    .append("zone.box.z").append(String.format("%02d", i)).append(".to=1,1,1\n");
        }
        ServerSettings s = settings(file.toString());
        AdminCommands.Context ctx = ctx();
        CommandReply page1 = AdminCommands.execute("zones", s, ctx);
        assertTrue(page1.plain().stream().anyMatch(l -> l.contains("z00")));
        button(page1, "/vcd zones 2");
        CommandReply page3 = AdminCommands.execute("zones 3", s, ctx);
        assertTrue(page3.plain().stream().anyMatch(l -> l.contains("z19")));
        assertFalse(page3.plain().stream().anyMatch(l -> l.contains("z00")));
        button(page3, "/vcd zones 2");
    }

    @Test
    @DisplayName("/vcd effects: a clickable view, the two switches and the strengths, each with Undo, saved and in the log")
    void effects() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.Context ctx = ctx();
        CommandReply view = AdminCommands.execute("effects", s, ctx);
        assertEquals("Effects for players without the addon", view.plain().get(0));
        assertFalse(s.hasServerRealism(), "off until the admin asks");
        button(view, "/vcd effects on");
        button(view, "/vcd effects air on");
        button(view, "/vcd help effects");
        assertEquals(Click.SUGGEST, button(view, "/vcd effects water ").click());
        assertEquals(Click.SUGGEST, button(view, "/vcd effects echo ").click());
        assertTrue(String.join("\n", view.plain()).contains("Players with the addon") || String.join("\n", view.plain()).contains("Players who"),
                "the note about players with the addon: " + view.plain());
        assertEquals(AdminCommands.execute("effects status", s, ctx).plain(), view.plain());

        CommandReply on = AdminCommands.execute("effects on", s, ctx);
        assertTrue(s.isServerEffects());
        assertFalse(s.isServerAir());
        button(on, "/vcd undo");
        AdminCommands.run("effects air on", s, ctx);
        assertTrue(s.isServerAir());
        CommandReply shown = AdminCommands.execute("effects", s, ctx);
        assertTrue(shown.plain().contains("Air off") || String.join("\n", shown.plain()).contains("[Air off]"), shown.plain().toString());
        button(shown, "/vcd effects off");
        button(shown, "/vcd effects air off");
        AdminCommands.run("effects curve on", s, ctx);
        assertTrue(s.isServerCurve());
        button(AdminCommands.execute("effects", s, ctx), "/vcd effects curve off");
        AdminCommands.run("effects curve off", s, ctx);
        assertFalse(s.isServerCurve());

        AdminCommands.run("effects water 150", s, ctx);
        assertEquals(1.5, s.profile().getUnderwaterStrength(), 1e-9);
        assertTrue(s.profile().isUnderwaterEnabled());
        AdminCommands.run("effects weather 40%", s, ctx);
        assertEquals(0.4, s.profile().getWeatherStrength(), 1e-9);
        AdminCommands.run("effects echo 70", s, ctx);
        assertEquals(0.7, s.profile().getReverbStrength(), 1e-9);
        AdminCommands.run("effects weather off", s, ctx);
        assertFalse(s.profile().isWeatherEnabled());
        assertEquals(0.4, s.profile().getWeatherStrength(), 1e-9, "the strength is kept for when it is turned on again");
        AdminCommands.run("effects weather 50", s, ctx);
        assertTrue(s.profile().isWeatherEnabled());
        assertEquals(0.5, s.profile().getWeatherStrength(), 1e-9);

        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertTrue(again.isServerEffects() && again.isServerAir(), "saved");
        assertEquals(1.5, again.profile().getUnderwaterStrength(), 1e-9);

        // Too much is refused and nothing changes; echo goes to 100% only
        CommandReply tooMuch = AdminCommands.execute("effects water 200", s, ctx);
        assertTrue(tooMuch.plain().get(0).contains("200") && tooMuch.plain().get(0).contains("0-150|off"), tooMuch.plain().toString());
        assertEquals(1.5, s.profile().getUnderwaterStrength(), 1e-9);
        assertTrue(AdminCommands.run("effects echo 150", s, ctx).get(0).contains("0-100|off"));
        assertEquals(0.7, s.profile().getReverbStrength(), 1e-9);
        assertTrue(AdminCommands.run("effects air maybe", s, ctx).get(0).contains("on|off"));
        assertTrue(AdminCommands.run("effects loud", s, ctx).get(0).contains("loud"));
        assertTrue(AdminCommands.run("effects water", s, ctx).get(0).contains("0-150|off"));
        button(AdminCommands.execute("effects water 200", s, ctx), "/vcd effects water ");

        // Every change can be taken back, newest first
        AdminCommands.run("undo", s, ctx);
        assertEquals(0.4, s.profile().getWeatherStrength(), 1e-9);
        assertFalse(s.profile().isWeatherEnabled());
        AdminCommands.run("undo", s, ctx);
        assertTrue(s.profile().isWeatherEnabled());
        AdminCommands.run("undo", s, ctx);
        AdminCommands.run("undo", s, ctx);
        AdminCommands.run("undo", s, ctx);
        assertEquals(1.0, s.profile().getUnderwaterStrength(), 1e-9);
        AdminCommands.run("undo", s, ctx);
        assertTrue(s.isServerCurve(), "curve off undone");
        AdminCommands.run("undo", s, ctx);
        assertFalse(s.isServerCurve());
        AdminCommands.run("undo", s, ctx);
        assertFalse(s.isServerAir());
        AdminCommands.run("undo", s, ctx);
        assertFalse(s.isServerEffects());
        assertEquals(0, AdminCommands.undoable(s));
        assertTrue(ChangeLog.read(s).stream().anyMatch(e -> e.command().equals("/vcd effects water 150") && !e.undo()));

        // The status line shows it
        AdminCommands.run("effects on", s, ctx);
        String status = String.join("\n", AdminCommands.execute("", s, ctx).plain());
        assertTrue(status.contains("Effects for players without the addon: water, weather and echo on"), status);
    }

    @Test
    @DisplayName("/vcd effects: looking needs vcd.status, changing needs vcd.settings; tab completion has the values")
    void effectsPermissionsAndSuggestions() throws IOException {
        ServerSettings s = settings("");
        players.update(admin);
        AdminCommands.Context viewer = ctx(admin.id(), null, AdminCommands.PERM_STATUS);
        assertEquals("Effects for players without the addon", AdminCommands.run("effects", s, viewer).get(0));
        assertTrue(AdminCommands.run("effects on", s, viewer).get(0).contains(AdminCommands.PERM_SETTINGS));
        assertTrue(AdminCommands.run("effects water 50", s, viewer).get(0).contains(AdminCommands.PERM_SETTINGS));
        assertFalse(s.isServerEffects());
        assertEquals(1.0, s.profile().getUnderwaterStrength(), 1e-9);
        List<String> subs = AdminCommands.suggestions("eff", s, viewer).stream().map(AdminCommands.Suggestion::text).toList();
        assertEquals(List.of("effects"), subs);
        assertTrue(AdminCommands.suggestions("eff", s, viewer).get(0).tooltip().contains("water, weather, echo, air and Doppler"));

        assertEquals(List.of("on", "off", "air", "curve", "water", "weather", "echo", "doppler", "status"), AdminCommands.suggest("effects "));
        assertEquals(List.of("on", "off"), AdminCommands.suggest("effects air "));
        assertEquals(List.of("off", "25", "50", "75", "100", "125", "150"), AdminCommands.suggest("effects water "));
        assertEquals(List.of("off", "25", "50", "75", "100"), AdminCommands.suggest("effects echo "));
        assertEquals(List.of("weather"), AdminCommands.suggest("effects we"));
        assertEquals(List.of("help effects"), List.of("help " + AdminCommands.suggest("help eff").get(0)));

        CommandReply help = AdminCommands.execute("help effects", s, ctx());
        assertEquals("/vcd effects", help.plain().get(0));
        assertEquals(Click.SUGGEST, button(help, "/vcd effects air on").click());
    }

    @Test
    @DisplayName("Strengths: off, 70%, 70, 0.7; up to the maximum of the setting")
    void strengthParsing() {
        assertEquals(0.0, AdminCommands.parseStrength("off", 1.5));
        assertEquals(0.7, AdminCommands.parseStrength("70%", 1.0), 1e-9);
        assertEquals(0.7, AdminCommands.parseStrength("70", 1.0), 1e-9);
        assertEquals(0.7, AdminCommands.parseStrength("0.7", 1.0), 1e-9);
        assertEquals(1.0, AdminCommands.parseStrength("1", 1.5), 1e-9, "a bare 1 is 100%");
        assertEquals(1.5, AdminCommands.parseStrength("150", 1.5), 1e-9);
        assertEquals(1.5, AdminCommands.parseStrength("1.5", 1.5), 1e-9);
        assertEquals(0.01, AdminCommands.parseStrength("1%", 1.5), 1e-9);
        assertNull(AdminCommands.parseStrength("151", 1.5));
        assertNull(AdminCommands.parseStrength("101", 1.0));
        assertNull(AdminCommands.parseStrength("-5", 1.5));
        assertNull(AdminCommands.parseStrength("loud", 1.5));
        assertNull(AdminCommands.parseStrength("", 1.5));
    }

    @Test
    @DisplayName("Suggestions offer zones, worlds, players and values, with tooltips")
    void suggestions() throws IOException {
        ServerSettings s = settings("""
                zone.box.stage.world=world
                zone.box.stage.from=0,0,0
                zone.box.stage.to=9,9,9
                zone.world.world_the_end.voice_range=8
                """);
        players.update(new ServerPlayers.Info(UUID.randomUUID(), "Bob", "world", 0, 64, 0, false, true, false, "", "", List.of(), ""));
        AdminCommands.Context ctx = ctx();
        List<AdminCommands.Suggestion> subs = AdminCommands.suggestions("zo", s, ctx);
        assertEquals(List.of("zones", "zone"), subs.stream().map(AdminCommands.Suggestion::text).toList());
        assertNotNull(subs.get(0).tooltip());
        List<AdminCommands.Suggestion> names = AdminCommands.suggestions("zone show ", s, ctx);
        assertEquals(List.of("stage", "off"), names.stream().map(AdminCommands.Suggestion::text).toList(), "only boxes can be shown");
        assertTrue(names.get(0).tooltip().contains("0,0,0"));
        assertEquals(List.of("stage", "world_the_end", "world", "world_nether"),
                AdminCommands.suggestions("zone set ", s, ctx).stream().map(AdminCommands.Suggestion::text).toList());
        assertEquals(List.of("off", "25", "50", "75", "100", "default"),
                AdminCommands.suggestions("zone set stage walls ", s, ctx).stream().map(AdminCommands.Suggestion::text).toList());
        assertEquals(List.of("Admin", "Bob"),
                AdminCommands.suggestions("debug ", s, ctx).stream().map(AdminCommands.Suggestion::text).toList());
        assertEquals(List.of("confirm"), AdminCommands.suggest("zone delete stage c"));
        assertEquals(List.of("curve,walls", "curve,materials", "curve,effects"), AdminCommands.suggest("lock curve,"));
        assertEquals(List.of("look", "~ ~ ~"), AdminCommands.suggest("zone pos1 "));
    }

    @Test
    @DisplayName("Permissions: each part of /vcd needs its own, and what the sender may not use is left out")
    void permissions() throws IOException {
        ServerSettings s = settings("");
        players.update(admin);
        AdminCommands.Context viewer = ctx(admin.id(), null, AdminCommands.PERM_STATUS);
        assertTrue(AdminCommands.run("", s, viewer).get(0).startsWith("Voice Physics"), "the status is allowed");
        assertTrue(AdminCommands.run("walls 20", s, viewer).get(0).contains(AdminCommands.PERM_SETTINGS));
        assertEquals(0.6, s.profile().getOcclusionStrength(), 1e-9);
        assertTrue(AdminCommands.run("zone create x 3", s, viewer).get(0).contains(AdminCommands.PERM_ZONE));
        assertFalse(AdminCommands.run("zone info", s, viewer).get(0).contains(AdminCommands.PERM_ZONE), "looking is allowed");
        assertTrue(AdminCommands.run("debug", s, viewer).get(0).contains(AdminCommands.PERM_DEBUG));

        CommandReply help = AdminCommands.execute("help", s, viewer);
        assertTrue(help.plain().stream().anyMatch(l -> l.startsWith("/vcd zones")));
        assertFalse(help.plain().stream().anyMatch(l -> l.startsWith("/vcd walls")));
        List<String> subs = AdminCommands.suggestions("", s, viewer).stream().map(AdminCommands.Suggestion::text).toList();
        assertTrue(subs.contains("status") && subs.contains("zone") && !subs.contains("walls") && !subs.contains("debug"), subs.toString());
        assertEquals(List.of("info", "list"),
                AdminCommands.suggestions("zone ", s, viewer).stream().map(AdminCommands.Suggestion::text).toList());

        AdminCommands.Context zones = ctx(admin.id(), null, AdminCommands.PERM_ZONE, AdminCommands.PERM_STATUS);
        AdminCommands.run("zone create x 3", s, zones);
        AdminCommands.run("walls 20", s, ctx());
        assertTrue(AdminCommands.run("undo", s, zones).get(0).contains(AdminCommands.PERM_SETTINGS),
                "undo needs the permission of the change it takes back");
        assertTrue(AdminCommands.mayUseAny(viewer));
        assertFalse(AdminCommands.mayUseAny(ctx(admin.id(), null, "other.permission")));
        assertTrue(AdminCommands.mayUseAny(ctx(admin.id(), null, AdminCommands.PERM_ADMIN)));
    }

    @Test
    @DisplayName("Every text key the commands use exists in English, and every language answers without raw keys")
    void texts() throws IOException {
        Path src = Path.of("src/main/java/com/kasper/vcdistance");
        StringBuilder code = new StringBuilder();
        for (String f : new String[]{"AdminCommands", "ZoneCommands", "CommandHelp", "CommandSuggest"}) {
            code.append(Files.readString(src.resolve(f + ".java"), StandardCharsets.UTF_8));
        }
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("(?:\\bm\\.(?:get|spans)|\\br\\.(?:error|saved|button\\(\\w+,)|\\bbutton\\(\\w+,)\\s*\\(?\"([a-z_.]+)\"")
                .matcher(code);
        while (m.find()) {
            keys.add(m.group(1));
        }
        Matcher saved = Pattern.compile("(?:r\\.saved|r\\.error|m\\.get|m\\.spans)\\(\"([a-z_.]+)\"").matcher(code);
        while (saved.find()) {
            keys.add(saved.group(1));
        }
        assertTrue(keys.size() > 60, keys.toString());
        String english = Files.readString(Path.of("src/main/resources/assets/vc-audio-distance/lang/en_us.json"), StandardCharsets.UTF_8);
        Set<String> missing = new HashSet<>();
        for (String k : keys) {
            if (!k.endsWith(".") && !english.contains("\"" + ServerText.PREFIX + k + "\"")) {
                missing.add(k);
            }
        }
        assertTrue(missing.isEmpty(), "Missing: " + missing);

        // The player's /voicephysics builds its keys from KEY + "..."
        String client = Files.readString(Path.of("../shared/mc-all/src/main/java/com/kasper/vcdistance/client/ClientCommands.java"),
                StandardCharsets.UTF_8);
        Matcher c = Pattern.compile("KEY \\+ \"([a-z_.]+)\"").matcher(client);
        Set<String> clientKeys = new TreeSet<>();
        while (c.find()) {
            if (!c.group(1).endsWith(".")) {
                clientKeys.add(c.group(1));
            }
        }
        // ok("preset", ...), error(locked ? "a" : "b", ...): the texts of the first argument
        Matcher calls = Pattern.compile("\\b(?:ok|error)\\(([^;]*?)(?:,|\\)\\s*[);])").matcher(client);
        while (calls.find()) {
            Matcher literal = Pattern.compile("\"([a-z_.]+)\"").matcher(calls.group(1));
            while (literal.find()) {
                clientKeys.add(literal.group(1));
            }
        }
        for (ServerSettings.ProfileMode mode : ServerSettings.ProfileMode.values()) {
            clientKeys.add("status.mode." + mode.getId());
        }
        assertTrue(clientKeys.size() > 25, clientKeys.toString());
        for (String k : clientKeys) {
            assertTrue(english.contains("\"message.vc-audio-distance.cmd." + k + "\""), "Missing message.vc-audio-distance.cmd." + k);
        }

        ServerSettings s = settings("zone.box.stage.world=world\nzone.box.stage.from=0,0,0\nzone.box.stage.to=9,9,9\n");
        for (String language : ServerText.LANGUAGES) {
            ServerPlayers.Info me = new ServerPlayers.Info(admin.id(), "Admin", "world", 1, 1, 1, false, true, false, "", "",
                    List.of(), language);
            players.update(me);
            players.update(new ServerPlayers.Info(UUID.randomUUID(), "Bob", "world", 5, 1, 5, false, true, false, "", "", List.of(), ""));
            AdminCommands.Context ctx = ctx(admin.id(), new int[]{3, 3, 3});
            for (String command : new String[]{"", "help", "help zone", "zones", "zone info", "zone info stage", "zone set stage walls",
                    "zone pos1", "zone pos2 look", "zone delete stage", "walls 150", "zoen", "debug Bob", "undo", "rule dead on", "undo",
                    "preset export"}) {
                for (String line : AdminCommands.execute(command, s, ctx).plain()) {
                    assertFalse(line.matches(".*\\b(btn|hover|zone|zones|error|help|undo|debug)\\.[a-z_.]+.*"),
                            language + " /vcd " + command + ": " + line);
                }
            }
        }
    }
}
