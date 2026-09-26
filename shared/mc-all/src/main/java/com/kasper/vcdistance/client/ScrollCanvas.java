package com.kasper.vcdistance.client;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws the scrolling part of the settings screen: moves everything up by the scroll offset and
 * leaves out what falls outside the visible band. Fills are cut at the band's edges; a line of text
 * is drawn only when it fits whole. Works without scissor support, so every version can use it.
 */
final class ScrollCanvas implements Canvas {

    private static final int LINE_HEIGHT = 9;

    private final Canvas canvas;
    private final int offset;
    private final int top;
    private final int bottom;
    private final Deque<Float> scales = new ArrayDeque<>();
    private float scale = 1.0F;

    /**
     * @param offset how far the content is scrolled, in screen pixels
     * @param top    first visible screen row
     * @param bottom first screen row below the visible band
     */
    ScrollCanvas(Canvas canvas, int offset, int top, int bottom) {
        this.canvas = canvas;
        this.offset = offset;
        this.top = top;
        this.bottom = bottom;
    }

    /** Content y (in the current scale) to the y the underlying canvas gets. */
    private int shift(int y) {
        return Math.round(y - offset / scale);
    }

    private int clipTop() {
        return (int) Math.ceil(top / scale);
    }

    private int clipBottom() {
        return (int) Math.floor(bottom / scale);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        int a = Math.max(shift(y1), clipTop());
        int b = Math.min(shift(y2), clipBottom());
        if (b > a) {
            canvas.fill(x1, a, x2, b, argb);
        }
    }

    private boolean lineVisible(int y) {
        int s = shift(y);
        return s >= clipTop() && s + LINE_HEIGHT <= clipBottom();
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        if (lineVisible(y)) {
            canvas.text(text, x, shift(y), argb);
        }
    }

    @Override
    public void centered(Component text, int centerX, int y, int argb) {
        if (lineVisible(y)) {
            canvas.centered(text, centerX, shift(y), argb);
        }
    }

    @Override
    public int width(Component text) {
        return canvas.width(text);
    }

    @Override
    public boolean pushScale(float factor) {
        if (!canvas.pushScale(factor)) {
            return false;
        }
        scales.push(scale);
        scale *= factor;
        return true;
    }

    @Override
    public void popScale() {
        canvas.popScale();
        scale = scales.isEmpty() ? 1.0F : scales.pop();
    }
}
