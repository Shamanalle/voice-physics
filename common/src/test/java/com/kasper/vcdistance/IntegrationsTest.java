package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Town and land zones, zones named by the WorldGuard flag, and the switch that turns the integrations on. */
public class IntegrationsTest {

    @TempDir
    Path dir;

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    private static final AdminCommands.Context CTX = new AdminCommands.Context() {
        public String platform() {
            return "Test";
        }

        public int onlinePlayers() {
            return 0;
        }

        public int addonPlayers() {
            return 0;
        }

        public void resendProfiles() {
        }

        public boolean allows(String permission) {
            return true;
        }

        public boolean towns() {
            return true;
        }

        public boolean lands() {
            return true;
        }
    };

    @Test
    @DisplayName("Town, land and claim zones are read from the file and found by their prefix")
    void zonesFromFile() throws IOException {
        ServerSettings s = settings("zone.town.Springfield.voice_range=12\nzone.land.atlantis.isolated=true\nzone.claim.steve.voice_range=5\n"
                + "zone.region.library.voice_range=8\nzone.box.stage.world=w\nzone.box.stage.from=0,0,0\nzone.box.stage.to=9,9,9\n");
        assertNotNull(s.findZone("town:springfield"));
        assertNotNull(s.findZone("land:Atlantis"));
        assertNotNull(s.findZone("claim:steve"));
        assertNotNull(s.findZone("library"));
        assertEquals(12.0, s.findZone("town:springfield").rules().voiceRange());
        assertTrue(s.findZone("land:atlantis").rules().isolated());
        assertEquals("town", Zone.prefixedKind("Town:Springfield"));
        assertNull(Zone.prefixedKind("springfield"));
        assertNull(Zone.prefixedKind("region:x"));
        assertTrue(Zone.isKind("town") && Zone.isKind("land"));

        s.save();
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals(5, again.zones().size());
        assertNotNull(again.findZone("town:springfield"));
    }

    @Test
    @DisplayName("Zone.resolve: a town, a land and a zone named by a region's flag; the highest priority wins, the flag beats a tie")
    void resolve() throws IOException {
        ServerSettings s = settings("zone.town.springfield.voice_range=12\nzone.land.atlantis.voice_range=30\n"
                + "zone.box.quiet.world=w\nzone.box.quiet.from=0,0,0\nzone.box.quiet.to=9,9,9\nzone.box.quiet.voice_range=4\nzone.region.library.voice_range=8\nzone.world.w.voice_range=50\n");
        assertEquals("town:springfield", Zone.resolve(s.zones(), "w", List.of("town:Springfield")).key());
        assertEquals("land:atlantis", Zone.resolve(s.zones(), "w", List.of("land:atlantis")).key());
        assertEquals("box:quiet", Zone.resolve(s.zones(), "w", List.of("flag:quiet")).key(), "a flag names a box zone");
        assertEquals("region:library", Zone.resolve(s.zones(), "w", List.of("flag:library")).key(), "or a region zone");
        assertEquals("world:w", Zone.resolve(s.zones(), "w", List.of("flag:nothing-like-that")).key(), "an unknown name falls back to the world");
        assertEquals("town:springfield", Zone.resolve(s.zones(), "w", List.of("town:springfield", "land:atlantis")).key(), "first on a tie");
        assertEquals("world:w", Zone.resolve(s.zones(), "w", List.of("town:unknown")).key());
    }

    @Test
    @DisplayName("/vcd zone set town:<name> makes a town zone; extras integrations switch with undo; Tab offers the prefixes")
    void commands() throws IOException {
        ServerSettings s = settings("");
        AdminCommands.run("zone set town:Springfield voice_range 12", s, CTX);
        Zone town = s.findZone("town:springfield");
        assertNotNull(town, "a town zone appears by setting something on it");
        assertEquals(Zone.TOWN, town.kind());
        assertEquals("town:Springfield", ZoneCommands.ref(town).replace("springfield", "Springfield"));
        AdminCommands.run("zone set land:atlantis isolated on", s, CTX);
        assertEquals(Zone.LAND, s.findZone("land:atlantis").kind());
        assertTrue(Files.readString(s.getPath()).contains("zone.town.springfield.voice_range="));

        assertFalse(s.isServerIntegrations());
        AdminCommands.run("extras integrations on", s, CTX);
        assertTrue(s.isServerIntegrations());
        assertTrue(Files.readString(s.getPath()).contains("server_integrations=true"));
        AdminCommands.run("undo", s, CTX);
        assertFalse(s.isServerIntegrations());

        List<String> tab = CommandSuggest.suggest("zone set ", s, CTX).stream().map(AdminCommands.Suggestion::text).toList();
        assertTrue(tab.contains("town:") && tab.contains("land:"), tab.toString());
        assertTrue(tab.contains("town:springfield"), "existing zones are offered by their reference: " + tab);
    }

    @Test
    @DisplayName("External chat mutes silence voice only while server_integrations is on, never touch the settings file, and yield to /vcd mute")
    void externalChatMutes() throws IOException {
        ServerSettings s = settings("");
        ServerPlayers players = new ServerPlayers();
        ServerPlayers.Info steve = new ServerPlayers.Info(java.util.UUID.nameUUIDFromBytes("Steve".getBytes()), "Steve", "w", 5, 64, 0,
                false, true, false, "", "", List.of(), "en_us");
        ServerPlayers.Info alex = new ServerPlayers.Info(java.util.UUID.nameUUIDFromBytes("Alex".getBytes()), "Alex", "w", 8, 64, 0,
                false, true, false, "", "", List.of(), "en_us");
        players.update(steve);
        players.update(alex);

        long now = System.currentTimeMillis();
        VoiceMute chatMute = new VoiceMute(steve.id(), "Steve", now + 600_000L, "LiteBans", "chat spam");
        s.setExternalMutes(java.util.Map.of(steve.id(), chatMute));

        // Off while server_integrations is false
        assertNull(s.effectiveMuteOf(steve.id(), now));
        assertFalse(s.hasVoiceRules());
        assertTrue(ServerRange.decide(s, steve, alex, false, 48, 24).hears());
        assertEquals("false", ServerPlaceholders.value("muted", steve.id(), s, System.nanoTime(), now));

        // On once server_integrations is enabled
        s.setServerIntegrations(true);
        assertEquals(chatMute, s.effectiveMuteOf(steve.id(), now));
        assertTrue(s.hasVoiceRules() && s.hasGroupRules());
        assertEquals(ServerRange.Reason.MUTED, ServerRange.decide(s, steve, alex, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.MUTED, ServerRange.decideGroup(s, steve, alex).reason());
        assertEquals("true", ServerPlaceholders.value("muted", steve.id(), s, System.nanoTime(), now));
        assertEquals("chat spam", ServerPlaceholders.value("mute_reason", steve.id(), s, System.nanoTime(), now));

        // Never written to disk, and /vcd debug shows the plugin name without a misleading /vcd unmute button
        s.save();
        assertFalse(Files.readString(s.getPath()).contains("chat spam"));
        assertTrue(s.mutes(now).isEmpty());
        AdminCommands.Context debugCtx = new AdminCommands.Context() {
            public String platform() { return "Paper"; }
            public int onlinePlayers() { return 2; }
            public int addonPlayers() { return 0; }
            public void resendProfiles() {}
            public ServerPlayers players() { return players; }
            public boolean allows(String permission) { return true; }
        };
        CommandReply debug = AdminCommands.execute("debug Steve", s, debugCtx);
        String debugText = String.join("\n", debug.plain());
        assertTrue(debugText.contains("by LiteBans - chat spam"), debugText);
        assertFalse(debugText.contains("[Unmute]"), "external chat mute cannot be removed with /vcd unmute: " + debugText);

        // Own /vcd mute takes precedence and CAN be unmuted with /vcd unmute
        s.mute(new VoiceMute(steve.id(), "Steve", 0L, "Admin", "voice rule"));
        assertEquals("Admin", s.effectiveMuteOf(steve.id(), now).by());
        assertTrue(String.join("\n", AdminCommands.execute("debug Steve", s, debugCtx).plain()).contains("[Unmute]"));
    }
}
