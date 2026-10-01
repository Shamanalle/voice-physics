package com.kasper.vcdistance.client;

import com.kasper.vcdistance.compat.Txt;
import com.kasper.vcdistance.compat.Btn;
import com.kasper.vcdistance.compat.Tip;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ChangeLog;
import com.kasper.vcdistance.LinkProtocol;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The server's change log on a screen of its own, for admins: who changed the settings with
 * {@code /vcd} or the Server tab, when, and what. Ten changes a page (the same pages as
 * {@code /vcd log}), everyone's or one player's, with a button that takes the last change back and one
 * that copies the page. The server does the paging; each page comes with the admin reply.
 * <p>
 * Version subclasses only forward rendering through a {@link Canvas} and switch screens.
 */
public abstract class LogScreen extends Screen {

    private static final String K = "gui.vc-audio-distance.";
    private static final int MAX_WIDTH = 420;
    private static final int GAP = 4;
    private static final int LINE = 11;
    private static final int COPIED_TICKS = 40;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    protected final Screen parent;

    private int left;
    private int right;
    private int headerY;
    private int listTop;
    private int listBottom;
    private int pagerY;
    private int footerY;

    private EditBox filterBox;
    private Button copyButton;
    private int copiedTicks;

    /** The player whose changes are shown ("" = everyone), and the page last asked for. */
    private String who = "";
    private int wantPage = 1;
    private boolean asked;
    private int seenReplies = -1;
    /** The latest page from the server, or {@code null} until the first reply. */
    private ChangeLog.Page shown;
    private int undoable;

    protected LogScreen(Screen parent) {
        super(Txt.translatable(K + "log.title"));
        this.parent = parent;
    }

    /** Shows another screen (the API for this differs between versions). */
    protected abstract void openScreen(Screen screen);

    protected abstract void writeClipboard(String text);

    // =========================================================================
    // Layout
    // =========================================================================

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        left = (this.width - w) / 2;
        right = left + w;
        headerY = 48;
        listTop = 60;
        footerY = this.height - 26;
        pagerY = footerY - 24;
        listBottom = pagerY - 4;

        // Filter row: a name, Filter, Everyone
        int applyW = Math.min(w / 4, this.font.width(tr("log.apply")) + 16);
        int allW = Math.min(w / 4, this.font.width(tr("log.all")) + 16);
        int boxW = w - applyW - allW - GAP * 2;
        filterBox = new EditBox(this.font, left, 22, boxW, 20, tr("log.filter"));
        filterBox.setMaxLength(32);
        filterBox.setValue(who);
        Tip.set(filterBox, tip("log.filter.tooltip"));
        addRenderableWidget(filterBox);
        addRenderableWidget(Btn.builder(tr("log.apply"), b -> ask(1, typedName()))
                .bounds(left + boxW + GAP, 22, applyW, 20).tooltip(tip("log.filter.tooltip")).build());
        addRenderableWidget(Btn.builder(tr("log.all"), b -> {
            filterBox.setValue("");
            ask(1, "");
        }).bounds(right - allW, 22, allW, 20).build());

        // Pager: previous and next page either side of the page number
        int page = shown == null ? 1 : shown.page();
        int pages = shown == null ? 1 : shown.pages();
        Button prev = Btn.builder(Txt.literal("‹"), b -> ask(page - 1, who)).bounds(left, pagerY, 30, 20).build();
        Button next = Btn.builder(Txt.literal("›"), b -> ask(page + 1, who)).bounds(right - 30, pagerY, 30, 20).build();
        prev.active = shown != null && page > 1;
        next.active = shown != null && page < pages;
        addRenderableWidget(prev);
        addRenderableWidget(next);

        // Footer: undo, copy, back
        int bw = (w - GAP * 2) / 3;
        Button undo = Btn.builder(tr("server.log.undo"), b -> undoLast())
                .bounds(left, footerY, bw, 20).tooltip(tip("server.log.undo.tooltip")).build();
        undo.active = undoable > 0;
        addRenderableWidget(undo);
        copyButton = Btn.builder(tr(copiedTicks > 0 ? "log.copied" : "log.copy"), b -> copyPage())
                .bounds(left + bw + GAP, footerY, bw, 20).tooltip(tip("log.copy.tooltip")).build();
        copyButton.active = shown != null && !shown.entries().isEmpty();
        addRenderableWidget(copyButton);
        addRenderableWidget(Btn.builder(tr("log.back"), b -> onClose()).bounds(right - bw, footerY, bw, 20).build());

        if (!asked) {
            asked = true;
            ask(1, "");
        }
    }

    // =========================================================================
    // Talking to the server
    // =========================================================================

    private String typedName() {
        return filterBox == null ? who : filterBox.getValue().replaceAll("\\s+", "");
    }

    /** Asks the server for a page; it arrives with the next admin reply. */
    private void ask(int page, String name) {
        who = name;
        wantPage = Math.max(1, page);
        AudioDistancePlugin.LINK.sendAdmin("log " + wantPage + (name.isEmpty() ? "" : " " + name));
    }

    private void undoLast() {
        // The undo reply carries no log page, so ask for the page again right after it
        AudioDistancePlugin.LINK.sendAdmin("undo");
        ask(wantPage, who);
    }

    @Override
    public void tick() {
        super.tick();
        if (copiedTicks > 0 && --copiedTicks == 0 && copyButton != null) {
            copyButton.setMessage(tr("log.copy"));
        }
        if (AudioDistancePlugin.LINK.adminReplyCount() == seenReplies) {
            return;
        }
        seenReplies = AudioDistancePlugin.LINK.adminReplyCount();
        LinkProtocol.AdminReply reply = AudioDistancePlugin.LINK.adminReply();
        if (reply == null) {
            return;
        }
        ChangeLog.Page page = ChangeLog.readPage(reply.state());
        if (page != null) {
            shown = page;
            wantPage = page.page();
        }
        try {
            undoable = Integer.parseInt(reply.state().getProperty("undo", "0"));
        } catch (NumberFormatException e) {
            undoable = 0;
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void copyPage() {
        if (shown == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (ChangeLog.Entry e : shown.entries()) {
            text.append(TIME_FULL.format(e.time())).append('\t').append(who(e)).append('\t')
                    .append(e.undo() ? "undo " : "").append(e.command()).append('\n');
        }
        try {
            writeClipboard(text.toString());
            copiedTicks = COPIED_TICKS;
            copyButton.setMessage(tr("log.copied"));
        } catch (Throwable t) {
            com.kasper.vcdistance.DistanceConfig.LOGGER.debug("Could not copy the log page: {}", t.toString());
        }
    }

    @Override
    public void onClose() {
        openScreen(parent);
    }

    // =========================================================================
    // Painting
    // =========================================================================

    /** Draws everything that is not a widget. Called by the version subclass after the widgets. */
    protected void paint(Canvas c, int mouseX, int mouseY) {
        Palette.useColorblind(AudioDistancePlugin.CONFIG.isColorblind());
        c.centered(this.title, this.width / 2, 8, Palette.TEXT);
        if (shown == null) {
            c.centered(tr("log.loading"), this.width / 2, listTop + 20, Palette.TEXT_MUTED);
            return;
        }

        // What is shown, and where the server keeps it
        Component total = shown.who().isEmpty() ? tr("log.total", shown.total()) : tr("log.total.by", shown.total(), whoName(shown.who()));
        c.text(total, left, headerY, Palette.TEXT_DIM);
        Component file = tr("log.file", com.kasper.vcdistance.ChangeLog.FILE);
        if (c.width(total) + c.width(file) + 12 <= right - left) {
            c.right(file, right, headerY, Palette.TEXT_MUTED);
        }
        c.hLine(left, right, listTop - 3, Palette.PANEL_BORDER);

        if (shown.entries().isEmpty()) {
            Component none = shown.who().isEmpty() ? tr("log.empty") : tr("log.empty.by", whoName(shown.who()));
            c.centered(none, this.width / 2, listTop + 20, Palette.TEXT_MUTED);
        }
        List<ChangeLog.Entry> entries = shown.entries();
        int timeW = c.width(Txt.literal("00.00 00:00")) + 8;
        int whoW = 0;
        for (ChangeLog.Entry e : entries) {
            whoW = Math.max(whoW, c.width(Txt.literal(who(e))));
        }
        whoW = Math.min(whoW + 8, (right - left) / 3);
        int rows = Math.min(entries.size(), Math.max(0, (listBottom - listTop) / LINE));
        for (int i = 0; i < rows; i++) {
            ChangeLog.Entry e = entries.get(i);
            int y = listTop + i * LINE;
            if (i % 2 == 0) {
                c.fill(left, y - 1, right, y + LINE - 1, 0x14FFFFFF);
            }
            c.text(Txt.literal(TIME.format(e.time())), left + 2, y, Palette.TEXT_MUTED);
            c.text(fit(c, who(e), whoW - 8), left + 2 + timeW, y, Palette.TEXT);
            String command = e.undo() ? tr("server.log.undone", e.command()).getString() : e.command();
            int x = left + 2 + timeW + whoW;
            c.text(fit(c, command, right - x - 2), x, y, e.undo() ? Palette.WARN : Palette.TEXT_DIM);
        }

        // Page number between the arrows
        c.centered(tr("log.page", shown.page(), shown.pages()), this.width / 2, pagerY + 6, Palette.TEXT_DIM);
    }

    private static String who(ChangeLog.Entry e) {
        return whoName(e.who());
    }

    private static String whoName(String name) {
        return ChangeLog.CONSOLE.equals(name) ? tr("server.log.console").getString() : name;
    }

    /** {@code text} cut with an ellipsis so that it fits in {@code max} pixels. */
    private static Component fit(Canvas c, String text, int max) {
        String s = text;
        if (c.width(Txt.literal(s)) <= max) {
            return Txt.literal(s);
        }
        while (s.length() > 1 && c.width(Txt.literal(s + "…")) > max) {
            s = s.substring(0, s.length() - 1);
        }
        return Txt.literal(s + "…");
    }

    private static Tip tip(String key) {
        return Tip.create(tr(key));
    }

    private static Component tr(String key, Object... args) {
        return Txt.translatable(K + key, args);
    }
}
