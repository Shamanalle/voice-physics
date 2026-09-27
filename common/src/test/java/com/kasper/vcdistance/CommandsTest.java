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
        assertEquals("world 25.5 60.0 31.5", ctx.toString());

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
