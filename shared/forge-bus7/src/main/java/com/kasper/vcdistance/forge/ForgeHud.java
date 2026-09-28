package com.kasper.vcdistance.forge;

import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.HudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * The voice HUD as a Forge overlay layer. A class of its own, so a Forge without the layers event
 * (the 1.21.6 - 1.21.7 releases) fails only here and the rest of the addon still loads.
 */
final class ForgeHud {

    private ForgeHud() {
    }

    static void register(BusGroup modBus) {
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(e -> e.getLayeredDraw().add(
                ForgeCompat.id("vc-audio-distance", "voice_hud"), (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    HudOverlay.paint(new GuiCanvas(graphics, mc.font), graphics.guiWidth(), graphics.guiHeight(),
                            mc.level != null && mc.player != null, mc.options.hideGui);
                }));
    }
}
