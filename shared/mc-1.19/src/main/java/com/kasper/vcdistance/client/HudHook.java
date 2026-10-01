package com.kasper.vcdistance.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/** Draws the voice HUD on Minecraft 1.19 - 1.19.2 (the callback passes a PoseStack). */
public final class HudHook {

    private HudHook() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((pose, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            HudOverlay.paint(new GuiCanvas(pose, mc.font), mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(), mc.level != null && mc.player != null, mc.options.hideGui);
        });
    }
}
