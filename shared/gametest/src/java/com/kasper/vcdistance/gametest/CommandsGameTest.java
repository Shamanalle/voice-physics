package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.client.ClientHints;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs {@code /vcd} and {@code /voicephysics} as a player in a real client, in English and in
 * Russian, and takes a screenshot of the chat after each group (uploaded by CI). Makes a zone around
 * the player and checks that entering and leaving it is shown above the hotbar. Fails on a text key
 * that was not translated, on an unknown command, and when the zone notice does not come.
 */
public class CommandsGameTest implements FabricClientGameTest {

    private static final String[] LANGUAGES = {"en_us", "ru_ru"};

    /** Chat lines and lines above the hotbar ("[bar] ..."), as the client received them. */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    private static final String[][] GROUPS = {
            {"status", "vcd"},
            {"help", "vcd help zone"},
            {"zone", "vcd zone create gametest 6", "vcd zone info gametest"},
            {"errors", "vcd walls 150", "vcd zoen", "vcd undo", "vcd log"},
            {"player", "voicephysics status", "voicephysics help"},
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> RECEIVED.add((overlay ? "[bar] " : "") + message.getString()));
        // /voicephysics answers on the client, not through the network
        ClientHints.observe(message -> RECEIVED.add(message.getString()));
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(TestScreens::allowCommands).create()) {
            context.waitTicks(40);
            for (String language : LANGUAGES) {
                context.runOnClient(client -> TestScreens.language(client, language));
                context.waitTicks(5);
                context.waitFor(TestScreens::loaded, 1200);
                // The server answers in the player's language: tell it the new one
                context.runOnClient(TestScreens::sendOptions);
                context.waitTicks(60);
                for (String[] group : GROUPS) {
                    context.runOnClient(TestScreens::clearChat);
                    RECEIVED.clear();
                    for (int i = 1; i < group.length; i++) {
                        String command = group[i];
                        context.runOnClient(client -> TestScreens.command(client, command));
                        context.waitTicks(10);
                    }
                    context.takeScreenshot("commands-" + group[0] + "-" + language);
                    check(group, language);
                }
                // The zone made above is around the player: its name comes above the hotbar
                context.takeScreenshot("commands-zone-bar-" + language);
                RECEIVED.clear();
                context.runOnClient(client -> TestScreens.command(client, "vcd zone delete gametest confirm"));
                context.waitTicks(20);
                context.takeScreenshot("commands-zone-left-" + language);
                if (RECEIVED.stream().noneMatch(l -> l.startsWith("[bar] ") && l.contains("gametest"))) {
                    throw new AssertionError("No line above the hotbar on leaving the zone (" + language + "): " + RECEIVED);
                }
            }
        }
    }

    private static void check(String[] group, String language) {
        if (RECEIVED.isEmpty()) {
            throw new AssertionError("No reply to " + String.join(", ", group) + " (" + language + ")");
        }
        for (String line : RECEIVED) {
            if (line.contains("command.vc-audio-distance.") || line.contains("message.vc-audio-distance.")
                    || line.contains("gui.vc-audio-distance.")) {
                throw new AssertionError("Untranslated text in " + language + ": " + line);
            }
            if (line.contains("Unknown or incomplete command") || line.contains("Неизвестная или неполная команда")) {
                throw new AssertionError("Command not found (" + language + "): " + line);
            }
        }
        if (group[0].equals("zone") && RECEIVED.stream().noneMatch(l -> l.startsWith("[bar] ") && l.contains("gametest"))) {
            throw new AssertionError("No line above the hotbar on entering the zone (" + language + "): " + RECEIVED);
        }
    }
}
