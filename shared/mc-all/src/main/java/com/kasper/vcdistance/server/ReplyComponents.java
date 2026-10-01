package com.kasper.vcdistance.server;

import com.kasper.vcdistance.compat.Txt;

import com.kasper.vcdistance.CommandReply;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** {@code /vcd} reply lines as Minecraft chat text: colours, and click and hover events where this version can make them. */
public final class ReplyComponents {

    private ReplyComponents() {
    }

    public static Component of(CommandReply.Line line) {
        MutableComponent out = Txt.literal("");
        for (CommandReply.Span span : line.spans()) {
            Style style = Style.EMPTY.withColor(color(span.style()));
            if (span.style() == CommandReply.Style.TITLE) {
                style = style.withBold(true);
            }
            if (span.click() != null && span.action() != null) {
                ClickEvent click = ChatLink.click(action(span.click()), span.action());
                if (click != null) {
                    style = style.withClickEvent(click);
                }
            }
            if (span.hover() != null) {
                HoverEvent hover = ChatLink.hover(Txt.literal(span.hover()));
                if (hover != null) {
                    style = style.withHoverEvent(hover);
                }
            }
            out.append(Txt.literal(span.text()).withStyle(style));
        }
        return out;
    }

    static ChatFormatting color(CommandReply.Style style) {
        return switch (style) {
            case TITLE -> ChatFormatting.GOLD;
            case LABEL, PLAIN -> ChatFormatting.GRAY;
            case VALUE -> ChatFormatting.WHITE;
            case OK -> ChatFormatting.GREEN;
            case WARN -> ChatFormatting.YELLOW;
            case ERROR, DANGER -> ChatFormatting.RED;
            case MUTED -> ChatFormatting.DARK_GRAY;
            case BUTTON -> ChatFormatting.AQUA;
        };
    }

    static String action(CommandReply.Click click) {
        return switch (click) {
            case RUN -> "run_command";
            case SUGGEST -> "suggest_command";
            case COPY -> "copy_to_clipboard";
        };
    }
}
