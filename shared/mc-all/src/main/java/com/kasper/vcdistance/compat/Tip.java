package com.kasper.vcdistance.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A widget tooltip. Here (1.20 and newer) it is Minecraft's {@link Tooltip}; the older bands replace
 * this file with one that draws the tooltip itself.
 */
public final class Tip {

    private final Tooltip tooltip;

    private Tip(Tooltip tooltip) {
        this.tooltip = tooltip;
    }

    public static Tip create(Component text) {
        return new Tip(Tooltip.create(text));
    }

    /** The tooltip for a widget that is not a button (a button takes it through {@link Btn#tooltip}). */
    public static void set(AbstractWidget widget, Tip tip) {
        widget.setTooltip(tip == null ? null : tip.tooltip);
    }

    Tooltip raw() {
        return tooltip;
    }
}
