package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.CommandReply;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /vcd} reply lines as chat text: with Paper's Adventure API, colours, buttons and hover
 * texts; on Spigot, which has no Adventure, colours only. The Adventure part is in its own class so
 * that on Spigot only it fails to load.
 */
final class ReplyAdventure {

    private ReplyAdventure() {
    }

    static void send(CommandSender to, CommandReply.Line line) {
        try {
            Rich.send(to, line);
        } catch (LinkageError e) {
            to.sendMessage(legacy(line));
        }
    }

    /** The line with colour codes, for servers without Adventure. */
    @SuppressWarnings("deprecation")
    static String legacy(CommandReply.Line line) {
        StringBuilder b = new StringBuilder();
        for (CommandReply.Span span : line.spans()) {
            ChatColor color = switch (span.style()) {
                case TITLE -> ChatColor.GOLD;
                case LABEL, PLAIN -> ChatColor.GRAY;
                case VALUE -> ChatColor.WHITE;
                case OK -> ChatColor.GREEN;
                case WARN -> ChatColor.YELLOW;
                case ERROR, DANGER -> ChatColor.RED;
                case MUTED -> ChatColor.DARK_GRAY;
                case BUTTON -> ChatColor.AQUA;
            };
            b.append(color).append(span.text());
        }
        return b.toString();
    }

    private static final class Rich {

        static void send(CommandSender to, CommandReply.Line line) {
            TextComponent.Builder out = Component.text();
            for (CommandReply.Span span : line.spans()) {
                Component c = Component.text(span.text(), color(span.style()));
                if (span.style() == CommandReply.Style.TITLE) {
                    c = c.decorate(TextDecoration.BOLD);
                }
                if (span.click() != null && span.action() != null) {
                    c = c.clickEvent(switch (span.click()) {
                        case RUN -> ClickEvent.runCommand(span.action());
                        case SUGGEST -> ClickEvent.suggestCommand(span.action());
                        case COPY -> ClickEvent.copyToClipboard(span.action());
                    });
                }
                if (span.hover() != null) {
                    c = c.hoverEvent(HoverEvent.showText(Component.text(span.hover())));
                }
                out.append(c);
            }
            to.sendMessage(out.build());
        }

        private static NamedTextColor color(CommandReply.Style style) {
            return switch (style) {
                case TITLE -> NamedTextColor.GOLD;
                case LABEL, PLAIN -> NamedTextColor.GRAY;
                case VALUE -> NamedTextColor.WHITE;
                case OK -> NamedTextColor.GREEN;
                case WARN -> NamedTextColor.YELLOW;
                case ERROR, DANGER -> NamedTextColor.RED;
                case MUTED -> NamedTextColor.DARK_GRAY;
                case BUTTON -> NamedTextColor.AQUA;
            };
        }
    }
}
