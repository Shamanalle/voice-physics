package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.DistanceConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Small client behaviours shared by every version: the first-join hint, the HUD toggle key, short
 * HUD notices and the {@code /voicephysics} command.
 */
public final class ClientHints {

    /** The client command that opens the settings screen. */
    public static final String COMMAND = "voicephysics";

    /** Wait this long in the world before the hint, so it is not lost among the join messages. */
    private static final int WELCOME_DELAY_TICKS = 100;

    private static int ticksInWorld;
    /** Set by the command; the screen opens on the next tick, after the chat screen has closed. */
    private static volatile boolean openRequested;
    /** The "server hides nearby players" notice was shown since the monitor was last allowed. */
    private static boolean toldMonitorOff;
    /** Shows a message in the player's chat; set by the loader every tick (see {@link #tickWelcome}). */
    private static volatile Consumer<Component> chat;

    private ClientHints() {
    }

    /**
     * Once ever, a few seconds after joining the first world: how to open the settings.
     *
     * @param openKey the "open settings" key, or {@code null} when it could not be registered
     * @param chat    shows a message in chat
     */
    public static void tickWelcome(boolean inWorld, KeyMapping openKey, Consumer<Component> chat) {
        ClientHints.chat = inWorld ? chat : null;
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        if (!inWorld) {
            ticksInWorld = 0;
            return;
        }
        if (config.isWelcomeShown() || ++ticksInWorld < WELCOME_DELAY_TICKS) {
            return;
        }
        config.setWelcomeShown(true);
        config.save();
        Component message = openKey != null && !openKey.isUnbound()
                ? Component.translatable("message.vc-audio-distance.welcome.key", openKey.getTranslatedKeyMessage())
                : Component.translatable("message.vc-audio-distance.welcome.button");
        chat.accept(withOpenLink(message));
    }

    /** {@code message}, then a clickable "[Open settings]" that runs {@code /voicephysics}. */
    public static Component withOpenLink(Component message) {
        Component link = com.kasper.vcdistance.server.ChatLink.command(
                Component.translatable("message.vc-audio-distance.open"), "/" + COMMAND);
        return Component.empty().append(message).append(Component.literal(" ")).append(link);
    }

    /** The chat message about a server's own sound profile; a suggested one comes with the link to apply it. */
    public static Component serverProfileMessage(boolean enforced) {
        Component message = Component.translatable("message.vc-audio-distance.server_profile." + (enforced ? "enforce" : "suggest"));
        return enforced ? message : withOpenLink(message);
    }

    /**
     * The {@code /voicephysics} command for any loader's client command dispatcher: opens the
     * settings, and has the player's own quick commands (see {@link ClientCommands}).
     */
    public static <S> LiteralArgumentBuilder<S> openCommand() {
        return ClientCommands.tree(COMMAND);
    }

    /** Where command replies go: the player's chat, or {@code null} outside a world. */
    static Consumer<Component> chat() {
        return chat;
    }

    /** Called by the {@code /voicephysics} command: open the settings on the next tick. */
    public static void requestOpen() {
        openRequested = true;
    }

    /** @return {@code true} once after {@link #requestOpen}, when no other screen is open */
    public static boolean consumeOpenRequest(boolean screenOpen) {
        if (!openRequested || screenOpen) {
            return false;
        }
        openRequested = false;
        return true;
    }

    /** Short HUD notices: sound zones, and the server hiding nearby players. Called every client tick. */
    public static void tickNotices() {
        tickZoneNotice();
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        if (AudioDistancePlugin.LINK.isMonitorAllowed()) {
            toldMonitorOff = false;
        } else if (!toldMonitorOff && config.getHudMode() != com.kasper.vcdistance.HudMode.OFF) {
            toldMonitorOff = true;
            HudOverlay.flash(Component.translatable("gui.vc-audio-distance.hud.hidden"));
        }
    }

    /** Shows the sound zone the server just put the player in (or that they left it). */
    private static void tickZoneNotice() {
        com.kasper.vcdistance.ServerLink.ZoneNotice zone = AudioDistancePlugin.LINK.consumeZoneNotice();
        if (zone != null) {
            HudOverlay.flash(zone.name().isEmpty()
                    ? Component.translatable("gui.vc-audio-distance.hud.zone_left")
                    : zone.message() != null
                    ? Component.literal(zone.message())
                    : Component.translatable("gui.vc-audio-distance.hud.zone", zone.name()));
        }
    }

    /** The "voice HUD" key: off, while talking, always. */
    public static void cycleHud() {
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        config.setHudMode(config.getHudMode().next());
        config.save();
        HudOverlay.flash(HudOverlay.modeLabel(config.getHudMode()));
    }
}
