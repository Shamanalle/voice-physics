package com.kasper.vcdistance.gametest;

import com.kasper.vcdistance.client.ScreenSwitch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens and reads the current screen on 26.x (the API moved in 26.3). */
final class TestScreens {

    private static volatile java.util.concurrent.CompletableFuture<?> reload;

    private TestScreens() {
    }

    static void open(Minecraft client, Screen screen) {
        ScreenSwitch.open(client, screen);
    }

    static Screen current(Minecraft client) {
        return ScreenSwitch.current(client);
    }

    /** Switches the game's language; the resources reload after it. */
    static void language(Minecraft client, String code) {
        client.options.languageCode = code;
        client.getLanguageManager().setSelected(code);
        reload = client.reloadResourcePacks();
    }

    /** A world where the player may use commands (cheats on), so /vcd works. */
    static void allowCommands(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState settings) {
        settings.setAllowCommands(true);
    }

    /** Sends a command as if typed in chat, without the slash (client commands run on the client). */
    static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    /** Sends the client's options (its language) to the server. */
    static void sendOptions(Minecraft client) {
        client.options.broadcastOptions();
    }

    /** Sets the GUI scale (0 is automatic) and lays the open screen out again. */
    static void guiScale(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeGui();
    }

    /**
     * Empties the chat, so each screenshot shows one group of replies. The chat moved out of Gui in
     * 26.x, so it is looked up by its clearMessages(boolean); without it the older lines stay.
     */
    static void clearChat(Minecraft client) {
        for (Object owner : new Object[]{client.gui, client}) {
            for (java.lang.reflect.Method getter : owner.getClass().getMethods()) {
                if (getter.getParameterCount() != 0 || !getter.getReturnType().getSimpleName().contains("Chat")) {
                    continue;
                }
                try {
                    Object chat = getter.invoke(owner);
                    chat.getClass().getMethod("clearMessages", boolean.class).invoke(chat, false);
                    return;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not the chat; try the next
                }
            }
        }
    }

    /** Whether the game has finished loading resources. */
    static boolean loaded(Minecraft client) {
        return reload == null || reload.isDone();
    }
}
