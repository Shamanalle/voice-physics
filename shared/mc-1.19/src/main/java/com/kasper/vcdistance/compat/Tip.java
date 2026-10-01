package com.kasper.vcdistance.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A widget tooltip on Minecraft 1.18 - 1.19.2, which have no {@code Tooltip} class: a button asks for
 * its text when the mouse rests on it and the screen draws it ({@link Btn}). Widgets that are not
 * buttons (text boxes, sliders) have no tooltip on these versions.
 */
public final class Tip {

    private static final Map<AbstractWidget, Tip> OF_WIDGET = new WeakHashMap<>();

    private final Component text;

    private Tip(Component text) {
        this.text = text;
    }

    public static Tip create(Component text) {
        return new Tip(text);
    }

    public static void set(AbstractWidget widget, Tip tip) {
        synchronized (OF_WIDGET) {
            if (tip == null) {
                OF_WIDGET.remove(widget);
            } else {
                OF_WIDGET.put(widget, tip);
            }
        }
    }

    static Tip of(AbstractWidget widget) {
        synchronized (OF_WIDGET) {
            return OF_WIDGET.get(widget);
        }
    }

    Component text() {
        return text;
    }
}
