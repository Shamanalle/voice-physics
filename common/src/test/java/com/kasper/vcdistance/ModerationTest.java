package com.kasper.vcdistance;

import com.kasper.vcdistance.CommandReply.Click;
import com.kasper.vcdistance.CommandReply.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /vcd mute, unmute and mutes (the command, the file, undo, the voice rules and what the muted player is told),
 * and /vcd report for bug reports.
 */
public class ModerationTest {

    @TempDir
    Path dir;

    private final ServerPlayers players = new ServerPlayers();
    private final ServerPlayers.Info admin = info("Admin", 0);
    private final ServerPlayers.Info steve = info("Steve", 5);

    @AfterEach
    void clear() {
        for (VoiceMute m : AudioDistancePlugin.SERVER_SETTINGS.mutes(System.currentTimeMillis())) {
            AudioDistancePlugin.SERVER_SETTINGS.unmute(m.player());
        }
        AudioDistancePlugin.PLAYERS.clear();
        AudioDistancePlugin.MUTED_TALK.clear();
        ServerHooks.left(steve.id());
    }

    private static ServerPlayers.Info info(String name, double x) {
        return new ServerPlayers.Info(UUID.nameUUIDFromBytes(name.getBytes()), name, "world", x, 64, 0,
                false, true, false, "", "", List.of(), "en_us");
    }

    private ServerSettings settings() throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, "");
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    private AdminCommands.Context ctx(String... permissions) {
        players.update(admin);
        players.update(steve);
        List<String> allowed = List.of(permissions);
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
                return admin.id();
            }

            public ServerPlayers players() {
                return players;
            }

            public boolean allows(String permission) {
                return allowed.isEmpty() || allowed.contains(permission);
            }
        };
    }

    private static Span button(CommandReply reply, String action) {
        for (CommandReply.Line l : reply.lines()) {
            for (Span s : l.spans()) {
                if (action.equals(s.action())) {
                    return s;
                }
            }
        }
        fail("No button " + action + " in " + reply.plain());
        return null;
    }

    @Test
    @DisplayName("Times: 30s, 10m, 2h, 1d, 1w, 1h30m, a bare number of minutes, perm; nonsense is refused")
    void durations() {
        assertEquals(30_000L, VoiceMute.parseDuration("30s"));
        assertEquals(600_000L, VoiceMute.parseDuration("10m"));
        assertEquals(600_000L, VoiceMute.parseDuration("10"));
        assertEquals(7_200_000L, VoiceMute.parseDuration("2H"));
        assertEquals(86_400_000L, VoiceMute.parseDuration("1d"));
        assertEquals(604_800_000L, VoiceMute.parseDuration("1w"));
        assertEquals(5_400_000L, VoiceMute.parseDuration("1h30m"));
        assertEquals(0L, VoiceMute.parseDuration("perm"));
        assertEquals(0L, VoiceMute.parseDuration("forever"));
        for (String bad : new String[]{"", "spam", "10x", "0m", "m", "h1", "999999d", "-5m"}) {
            assertNull(VoiceMute.parseDuration(bad), bad);
        }
        assertEquals("10m", VoiceMute.formatDuration(600_000L));
        assertEquals("1h 30m", VoiceMute.formatDuration(5_400_000L));
        assertEquals("1d 2h", VoiceMute.formatDuration(93_600_000L + 59_000L));
        assertEquals("1s", VoiceMute.formatDuration(10L), "never 0s while it holds");
        assertEquals("1d", VoiceMute.formatDuration(86_400_000L + 5 * 60_000L));
    }

    @Test
    @DisplayName("A mute is kept in the settings file with its reason, and ends by itself")
    void file() throws IOException {
        ServerSettings s = settings();
        long now = System.currentTimeMillis();
        s.mute(new VoiceMute(steve.id(), "Steve", now + 60_000L, "Admin", "spam | caps"));
        UUID ghost = UUID.randomUUID();
        s.mute(new VoiceMute(ghost, "Ghost", now - 1_000L, "Admin", ""));
        s.save();
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        VoiceMute m = again.muteOf(steve.id(), now);
        assertNotNull(m);
        assertEquals("spam | caps", m.reason(), "the reason may hold anything");
        assertEquals("Admin", m.by());
        assertNull(again.muteOf(ghost, now), "an ended mute is gone from the file");
        assertNull(again.muteOf(steve.id(), now + 61_000L), "and ends by itself");
        assertTrue(Files.readString(s.getPath()).contains("# ---------- 10. Muted players"));
    }

    @Test
    @DisplayName("/vcd mute: time, reason, Undo and Unmute buttons, saved, logged; /vcd mutes lists it; unmute and undo")
    void command() throws IOException {
        ServerSettings s = settings();
        AdminCommands.Context ctx = ctx();
        CommandReply reply = AdminCommands.execute("mute steve 10m spam in voice", s, ctx);
        String text = String.join("\n", reply.plain());
        assertTrue(text.startsWith("Steve is muted for 10m. Saved."), text);
        assertTrue(text.contains("Reason: spam in voice"), text);
        button(reply, "/vcd undo");
        button(reply, "/vcd unmute Steve");
        VoiceMute m = s.muteOf(steve.id(), System.currentTimeMillis());
        assertNotNull(m);
        assertEquals("spam in voice", m.reason());
        assertEquals("Admin", m.by());

        CommandReply list = AdminCommands.execute("mutes", s, ctx);
        assertEquals("Muted players (1):", list.plain().get(0));
        assertTrue(list.plain().get(1).contains("Steve · 10m left · by Admin - spam in voice"), list.plain().toString());
        button(list, "/vcd unmute Steve");
        assertEquals(Click.SUGGEST, button(list, "/vcd mute Steve ").click());
        assertTrue(String.join("\n", AdminCommands.execute("", s, ctx).plain()).contains("Muted players: 1"));

        // No time: until unmuted; muting again replaces the old mute
        assertTrue(AdminCommands.run("mute Steve", s, ctx).get(0).startsWith("Steve is muted until unmuted."));
        assertTrue(s.muteOf(steve.id(), System.currentTimeMillis()).isPermanent());
        assertEquals(1, s.mutes(System.currentTimeMillis()).size());

        assertEquals("Steve can talk again. Saved. [Undo]", AdminCommands.execute("unmute steve", s, ctx).plain().get(0));
        assertNull(s.muteOf(steve.id(), System.currentTimeMillis()));
        assertTrue(AdminCommands.run("unmute Steve", s, ctx).get(0).contains("Steve is not muted"));
        assertTrue(AdminCommands.run("mutes", s, ctx).get(0).startsWith("Nobody is muted."));

        AdminCommands.run("undo", s, ctx);
        assertNotNull(s.muteOf(steve.id(), System.currentTimeMillis()), "undo brings the mute back");
        AdminCommands.run("undo", s, ctx);
        AdminCommands.run("undo", s, ctx);
        assertNull(s.muteOf(steve.id(), System.currentTimeMillis()));
        assertTrue(ChangeLog.read(s).stream().anyMatch(e -> e.command().equals("/vcd mute steve 10m spam in voice")));
    }

    @Test
    @DisplayName("/vcd mute: wrong times, unknown players and offline players by UUID")
    void errors() throws IOException {
        ServerSettings s = settings();
        AdminCommands.Context ctx = ctx();
        CommandReply bad = AdminCommands.execute("mute Steve 10x", s, ctx);
        assertTrue(bad.plain().get(0).contains("10x") && bad.plain().get(0).contains("perm"), bad.plain().toString());
        button(bad, "/vcd help mute");
        assertNull(s.muteOf(steve.id(), System.currentTimeMillis()));
        assertTrue(AdminCommands.run("mute Nobody 1h", s, ctx).get(0).contains("No player Nobody online"));
        assertTrue(AdminCommands.run("mute", s, ctx).get(0).contains("<player>"));

        UUID offline = UUID.randomUUID();
        AdminCommands.run("mute " + offline + " 1d", s, ctx);
        assertNotNull(s.muteOf(offline, System.currentTimeMillis()));
        // A reason without a time: the mute has no end
        AdminCommands.run("mute Steve being loud", s, ctx);
        VoiceMute m = s.muteOf(steve.id(), System.currentTimeMillis());
        assertTrue(m.isPermanent());
        assertEquals("being loud", m.reason());
    }

    @Test
    @DisplayName("Permission vcd.mute for all three; tab completion offers players, times and the muted")
    void permissionsAndSuggestions() throws IOException {
        ServerSettings s = settings();
        AdminCommands.Context viewer = ctx(AdminCommands.PERM_STATUS);
        assertTrue(AdminCommands.run("mute Steve", s, viewer).get(0).contains(AdminCommands.PERM_MUTE));
        assertTrue(AdminCommands.run("mutes", s, viewer).get(0).contains(AdminCommands.PERM_MUTE));
        assertNull(s.muteOf(steve.id(), System.currentTimeMillis()));
        AdminCommands.Context moderator = ctx(AdminCommands.PERM_MUTE);
        assertTrue(AdminCommands.mayUseAny(moderator), "a moderator with only vcd.mute gets /vcd");
        AdminCommands.run("mute Steve 5m", s, moderator);
        assertNotNull(s.muteOf(steve.id(), System.currentTimeMillis()));
        assertFalse(AdminCommands.run("undo", s, moderator).get(0).contains(AdminCommands.PERM_SETTINGS),
                "undoing a mute needs vcd.mute, not vcd.settings");
        assertNull(s.muteOf(steve.id(), System.currentTimeMillis()));

        AdminCommands.Context all = ctx();
        assertEquals(List.of("Admin", "Steve"), AdminCommands.suggestions("mute ", s, all).stream().map(AdminCommands.Suggestion::text).toList());
        assertEquals(List.of("10m", "30m", "1h", "1d", "7d", "perm"), AdminCommands.suggest("mute Steve "));
        AdminCommands.run("mute Steve 1h", s, all);
        List<AdminCommands.Suggestion> muted = AdminCommands.suggestions("unmute ", s, all);
        assertEquals(List.of("Steve"), muted.stream().map(AdminCommands.Suggestion::text).toList());
        assertTrue(muted.get(0).tooltip().startsWith("for "));
        List<String> subs = AdminCommands.suggestions("mu", s, viewer).stream().map(AdminCommands.Suggestion::text).toList();
        assertTrue(subs.isEmpty(), "left out without the permission: " + subs);
    }

    @Test
    @DisplayName("A muted speaker is heard by nobody, and /vcd debug says why")
    void voiceRules() throws IOException {
        ServerSettings s = settings();
        long now = System.currentTimeMillis();
        s.mute(new VoiceMute(steve.id(), "Steve", now + 60_000L, "Admin", "spam"));
        ServerRange.Decision d = ServerRange.decide(s, steve, admin, false, 48, 24);
        assertEquals(ServerRange.Reason.MUTED, d.reason());
        assertEquals(ServerRange.Reason.MUTED, ServerRange.decideGroup(s, steve, admin).reason());
        assertTrue(ServerRange.decide(s, admin, steve, false, 48, 24).hears(), "a muted player still hears others");

        AdminCommands.Context ctx = ctx();
        CommandReply debug = AdminCommands.execute("debug Steve", s, ctx);
        String text = String.join("\n", debug.plain());
        assertTrue(text.contains("Muted for 1m, by Admin - spam"), text);
        button(debug, "/vcd unmute Steve");
        String mine = String.join("\n", AdminCommands.execute("debug", s, ctx).plain());
        assertTrue(mine.contains("muted"), "the admin hears Steve: muted: " + mine);
    }

    @Test
    @DisplayName("The muted player is told in chat, above the hotbar when they try to talk, and when it ends; /voice shows it")
    void playerIsTold() {
        ServerSettings s = AudioDistancePlugin.SERVER_SETTINGS;
        List<String> chat = new ArrayList<>();
        List<String> bar = new ArrayList<>();
        ServerHooks.Platform platform = new ServerHooks.Platform() {
            public void message(UUID player, String text) {
                chat.add(text);
            }

            public void kick(UUID player, String text) {
            }

            public void actionBar(UUID player, String text) {
                bar.add(text);
            }
        };
        ServerHooks.refresh(List.of(steve), platform);
        assertTrue(chat.isEmpty());
        s.mute(new VoiceMute(steve.id(), "Steve", System.currentTimeMillis() + 600_000L, "Admin", "spam"));
        ServerHooks.refresh(List.of(steve), platform);
        assertEquals(1, chat.size());
        assertTrue(chat.get(0).startsWith("You are muted in voice chat for 10m.") && chat.get(0).endsWith("Reason: spam"), chat.get(0));
        ServerHooks.refresh(List.of(steve), platform);
        assertEquals(1, chat.size(), "told once");

        AudioDistancePlugin.MUTED_TALK.spoke(steve.id(), System.nanoTime());
        ServerHooks.refresh(List.of(steve), platform);
        assertTrue(bar.stream().anyMatch(t -> t.startsWith("Muted: nobody hears you (for 10m)")), bar.toString());

        CommandReply status = PlayerCommands.execute(steve.id(), "en_us", "", p -> true);
        assertTrue(status.plain().get(1).startsWith("You are muted by an admin for 10m: nobody hears you. - spam"), status.plain().toString());

        s.unmute(steve.id());
        ServerHooks.refresh(List.of(steve), platform);
        assertEquals("You can talk in voice chat again.", chat.get(chat.size() - 1));
    }

    @Test
    @DisplayName("/vcd report: one text with a Copy button, the settings and recent problems, no player or zone names")
    void report() throws IOException {
        ServerSettings s = settings();
        s.putZone(new Zone(Zone.BOX, "secret_base", null, null, Zone.Rules.NONE, new Zone.Box("world", 0, 0, 0, 5, 5, 5), 0));
        s.mute(new VoiceMute(steve.id(), "Steve", 0L, "Admin", "spam"));
        Problems.clear();
        Problems.record("Server voice processing", new IllegalStateException("boom"));
        Problems.record("Server voice processing", new IllegalStateException("boom"));
        AdminCommands.Context ctx = ctx();
        CommandReply reply = AdminCommands.execute("report", s, ctx);
        assertEquals("Report for a bug report: [Copy]", reply.plain().get(0));
        Span copy = reply.lines().get(0).spans().stream().filter(sp -> sp.click() == Click.COPY).findFirst().orElseThrow();
        String text = copy.action();
        assertTrue(text.startsWith("Voice Physics " + BuildInfo.version() + " on Test"), text);
        assertTrue(text.contains("Walls: 60%, server_walls on"), text);
        assertTrue(text.contains("Effects: server_effects off, server_air off"), text);
        assertTrue(text.contains("mutes 1"), text);
        assertTrue(text.contains("x2 Server voice processing: java.lang.IllegalStateException: boom"), text);
        assertFalse(text.contains("Steve") || text.contains("secret_base") || text.contains("spam"), "no names: " + text);
        assertTrue(AdminCommands.run("report", s, ctx(AdminCommands.PERM_STATUS)).get(0).contains(AdminCommands.PERM_DEBUG));
        Problems.clear();
        assertTrue(String.join("\n", AdminCommands.run("report", s, ctx)).contains("Problems: none since the start"));
    }
}
