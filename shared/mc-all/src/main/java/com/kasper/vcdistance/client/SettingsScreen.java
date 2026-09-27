package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AcousticMaterial;
import com.kasper.vcdistance.AttenuationModel;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioPhysics;
import com.kasper.vcdistance.Bearing;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.EnvironmentEffects;
import com.kasper.vcdistance.HudMode;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ListenerEnvironment;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.OcclusionModel;
import com.kasper.vcdistance.Preset;
import com.kasper.vcdistance.ProfileCode;
import com.kasper.vcdistance.RoomEstimate;
import com.kasper.vcdistance.SoundBlend;
import com.kasper.vcdistance.SpeakerRegistry;
import com.kasper.vcdistance.VoiceState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings screen, shared by every Minecraft version.
 * <p>
 * Tabs: the distance curve with a live graph, walls (with a preview and the materials), effects
 * (echo, water, rain, corners), the HUD with a preview, a live monitor of the players within voice
 * range, and the server's settings for admins. The part below the tabs scrolls, so nothing is left
 * out on small windows. Every change is heard immediately; "Done" (or Esc) saves, "Cancel" restores
 * what was there when the screen opened.
 * <p>
 * Version subclasses only forward rendering through a {@link Canvas} and switch screens.
 */
public abstract class SettingsScreen extends Screen {

    private static final String K = "gui.vc-audio-distance.";
    private static final int MAX_WIDTH = 420;
    private static final int ROW = 24;
    private static final int GAP = 4;
    /** Pixels per step of the mouse wheel. */
    private static final int SCROLL_STEP = 18;

    private static Tab lastTab = Tab.DISTANCE;
    /** Monitor as a list (false) or a radar seen from above (true); kept while the game runs. */
    private static boolean radarView;
    /** The materials section of the Walls tab is open; kept while the game runs. */
    private static boolean materialsOpen;

    /** Walk-away preview: where the voice is at each step, as a share of the range. */
    private static final double[] PREVIEW_STEPS = {0.03, 0.12, 0.21, 0.3, 0.39, 0.48, 0.57, 0.66, 0.75, 0.84, 0.92, 0.97};
    private static final int PREVIEW_STEP_TICKS = 10;

    public enum Tab {
        DISTANCE("tab.distance"),
        WALLS("tab.walls"),
        EFFECTS("tab.effects"),
        HUD("tab.hud"),
        MONITOR("tab.monitor"),
        /** Only for server admins, when the server has the addon. */
        SERVER("tab.server");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private record Example(String key, AcousticMaterial material, int blocks) {
    }

    private static final Example[] EXAMPLES = {
            // Roughly from the weakest to the strongest with the default weights
            new Example("glass", AcousticMaterial.GLASS, 1),
            new Example("leaves", AcousticMaterial.LEAVES, 3),
            new Example("wood", AcousticMaterial.WOOD, 1),
            new Example("earth", AcousticMaterial.EARTH, 1),
            new Example("stone1", AcousticMaterial.STONE, 1),
            new Example("metal", AcousticMaterial.METAL, 1),
            new Example("wool", AcousticMaterial.WOOL, 1),
            new Example("stone2", AcousticMaterial.STONE, 2),
            new Example("stone3", AcousticMaterial.STONE, 3)
    };

    /** A section title on the Server tab, at a content y; its rule runs to {@code lineEnd}. */
    private record Heading(Component text, int y, int lineEnd) {
    }

    protected final Screen parent;
    private final DistanceConfig config = AudioDistancePlugin.CONFIG;
    private final DistanceConfig snapshot;
    private Tab tab;

    private int left;
    private int right;
    private int tabsBottom;
    /** The scrolling band between the tabs and the footer, in screen rows. */
    private int viewTop;
    private int viewBottom;
    private int footerY;
    /** Content coordinates: equal to screen rows when nothing is scrolled. */
    private int contentTop;
    private int contentEnd;
    private int scroll;
    private int maxScroll;

    private int graphHeaderY;
    private int graphTop;
    private int graphBottom;
    /** Where a status banner goes on the Walls and Effects tabs. */
    private int statusY;
    private int panelTop;
    private int panelBottom;
    private int materialsHintY = -1;
    private int noZonesY = -1;
    private final List<Heading> headings = new ArrayList<>();

    /** Widgets below the tabs, with the content y they were laid out at. */
    private final List<AbstractWidget> scrolled = new ArrayList<>();
    private final List<Integer> scrolledY = new ArrayList<>();
    /** Widgets that change a part of the sound settings the server may lock. */
    private final Map<AbstractWidget, DistanceConfig.Part> editParts = new IdentityHashMap<>();
    private final List<AbstractWidget> lockedWidgets = new ArrayList<>();
    private final List<Button> presetButtons = new ArrayList<>();
    private final List<Preset> presetOrder = new ArrayList<>();
    private Button activeTabButton;
    private RangeSlider strengthSlider;
    private RangeSlider reverbSlider;
    private boolean serverChip;
    /** Current walk-away preview step, or -1 when it is not playing. */
    private int previewStep = -1;
    private int previewTicks;
    private Button listenButton;
    /** The footer shows Copy / Paste / Back for the profile code instead of the usual buttons. */
    private boolean codeMode;
    private Button copyButton;
    private Button pasteButton;
    /** Short-lived result shown on the profile code buttons ("copied", "pasted", "not a code"). */
    private String copyFeedback;
    private String pasteFeedback;
    private int codeFeedbackTicks;
    /** Server tab: the zone picked in the list, a zone whose Delete was clicked once, the last reply seen. */
    private static String selectedZone;
    private static String confirmDelete;
    private int seenAdminReplies = -1;

    protected SettingsScreen(Screen parent) {
        super(Component.translatable(K + "title"));
        this.parent = parent;
        this.config.ensureLoaded();
        this.snapshot = config.copy();
        this.tab = lastTab;
    }

    /** The tab the next settings screen opens on (the in-game tests walk through every tab). */
    public static void openOn(Tab tab) {
        lastTab = tab;
    }

    /** Scrolls the open tab to its end (in-game tests). */
    public void scrollToEnd() {
        scroll = maxScroll;
        applyScroll();
    }

    /** Shows another screen (the API for this differs between versions). */
    protected abstract void openScreen(Screen screen);

    /** Plays the preview voice (a villager's "hmm") at {@code volume}, 0 - 1. */
    protected abstract void playPreview(float volume);

    protected abstract boolean inWorld();

    /** Text on the system clipboard, or "" (the API for this differs between versions). */
    protected abstract String readClipboard();

    protected abstract void writeClipboard(String text);

    // =========================================================================
    // Layout
    // =========================================================================

    @Override
    protected void init() {
        scrolled.clear();
        scrolledY.clear();
        editParts.clear();
        lockedWidgets.clear();
        presetButtons.clear();
        presetOrder.clear();
        headings.clear();
        strengthSlider = null;
        reverbSlider = null;
        listenButton = null;
        copyButton = null;
        pasteButton = null;
        activeTabButton = null;
        serverChip = false;
        materialsHintY = -1;
        noZonesY = -1;

        int w = Math.min(this.width - 16, MAX_WIDTH);
        left = (this.width - w) / 2;
        right = left + w;

        // The Server tab only for admins of a server with the addon
        boolean admin = AudioDistancePlugin.LINK.isAdmin();
        if (tab == Tab.SERVER && !admin) {
            tab = Tab.DISTANCE;
        }
        Tab[] tabs = admin ? Tab.values() : java.util.Arrays.copyOf(Tab.values(), Tab.values().length - 1);
        int tabW = (w - GAP * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = i == tabs.length - 1 ? right - tabW : left + i * (tabW + GAP);
            Button b = Button.builder(tr(t.key), btn -> switchTab(t)).bounds(x, 22, tabW, 20).build();
            addRenderableWidget(b);
            if (t == tab) {
                activeTabButton = b;
            }
        }
        tabsBottom = 22 + 20;
        viewTop = tabsBottom + 5;
        footerY = this.height - 26;
        viewBottom = footerY - 5;
        contentTop = viewTop + 3;
        contentEnd = contentTop;

        initFooter(w);
        switch (tab) {
            case DISTANCE -> initDistance();
            case WALLS -> initWalls();
            case EFFECTS -> initEffects();
            case HUD -> initHud();
            case MONITOR -> initMonitor();
            case SERVER -> initServer();
        }
        initServerChip(w);
        applyLocks();

        maxScroll = Math.max(0, contentEnd + 4 - viewBottom);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        applyScroll();
    }

    /** The bottom of a panel that starts at {@code top}: at least {@code natural} tall, or down to the footer. */
    private int stretch(int top, int natural) {
        return Math.max(top + natural, viewBottom - 3);
    }

    /** Adds a widget below the tabs: it scrolls with the content. */
    private <T extends AbstractWidget> T content(T widget) {
        scrolled.add(widget);
        scrolledY.add(widget.getY());
        addRenderableWidget(widget);
        return widget;
    }

    /** Adds a widget that changes {@code part}: it is locked while the server enforces that part. */
    private <T extends AbstractWidget> T edit(T widget, DistanceConfig.Part part) {
        editParts.put(widget, part);
        return content(widget);
    }

    private void applyLocks() {
        for (Map.Entry<AbstractWidget, DistanceConfig.Part> e : editParts.entrySet()) {
            if (AudioDistancePlugin.LINK.isLocked(e.getValue())) {
                AbstractWidget widget = e.getKey();
                widget.active = false;
                widget.setTooltip(tip("locked.tooltip"));
                lockedWidgets.add(widget);
            }
        }
    }

    private void applyScroll() {
        for (int i = 0; i < scrolled.size(); i++) {
            AbstractWidget widget = scrolled.get(i);
            int y = scrolledY.get(i) - scroll;
            widget.setY(y);
            // A widget cut by the edge of the band is hidden until it scrolls fully into view
            widget.visible = y >= viewTop && y + widget.getHeight() <= viewBottom;
        }
    }

    /** Mouse wheel, Minecraft 1.20.2 and newer. Not annotated: the signature differs in 1.20.1. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(mouseY, scrollY);
    }

    /** Mouse wheel, Minecraft 1.20.1. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(mouseY, amount);
    }

    private boolean scrollBy(double mouseY, double amount) {
        if (maxScroll <= 0 || mouseY < viewTop || mouseY >= viewBottom || amount == 0.0) {
            return false;
        }
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(amount * SCROLL_STEP)));
        if (next != scroll) {
            scroll = next;
            applyScroll();
        }
        return true;
    }

    // ---- Footer -------------------------------------------------------------

    /** Reset the tab, profile code, Cancel, Done; on the Server tab only Done (changes apply at once there). */
    private void initFooter(int w) {
        List<Button> buttons = new ArrayList<>();
        if (tab != Tab.SERVER) {
            if (codeMode) {
                copyButton = Button.builder(tr(copyFeedback != null ? copyFeedback : "code.copy"), b -> copyCode())
                        .tooltip(tip("code.copy.tooltip")).build();
                pasteButton = Button.builder(tr(pasteFeedback != null ? pasteFeedback : "code.paste"), b -> pasteCode())
                        .tooltip(tip("code.paste.tooltip")).build();
                if (anyLocked()) {
                    pasteButton.active = false;
                    pasteButton.setTooltip(tip("locked.tooltip"));
                }
                buttons.add(copyButton);
                buttons.add(pasteButton);
                buttons.add(Button.builder(tr("code.back"), b -> {
                    codeMode = false;
                    rebuild();
                }).build());
            } else {
                String resetKey = switch (tab) {
                    case DISTANCE -> "reset.distance.tooltip";
                    case WALLS -> "reset.walls.tooltip";
                    case EFFECTS -> "reset.effects.tooltip";
                    case HUD -> "reset.hud.tooltip";
                    default -> null;
                };
                if (resetKey != null) {
                    Button reset = Button.builder(tr("reset.tab"), b -> resetTab()).tooltip(tip(resetKey)).build();
                    reset.active = canResetTab();
                    buttons.add(reset);
                }
                buttons.add(Button.builder(tr("code"), b -> {
                    codeMode = true;
                    rebuild();
                }).tooltip(tip("code.tooltip")).build());
                buttons.add(Button.builder(tr("cancel"), b -> cancel()).tooltip(tip("cancel.tooltip")).build());
            }
        }
        buttons.add(Button.builder(tr("done"), b -> saveAndClose()).tooltip(tip("done.tooltip")).build());

        // On the Server tab Done keeps the width of a four-button row; the server's reply goes left of it
        int slots = tab == Tab.SERVER ? 4 : buttons.size();
        int bw = (w - GAP * (slots - 1)) / slots;
        int x = right - bw * buttons.size() - GAP * (buttons.size() - 1);
        for (Button b : buttons) {
            b.setX(x);
            b.setY(footerY);
            b.setWidth(bw);
            addRenderableWidget(b);
            x += bw + GAP;
        }
    }

    private static boolean anyLocked() {
        for (DistanceConfig.Part part : DistanceConfig.Part.values()) {
            if (AudioDistancePlugin.LINK.isLocked(part)) {
                return true;
            }
        }
        return false;
    }

    private boolean canResetTab() {
        return switch (tab) {
            case DISTANCE -> !AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.CURVE);
            case WALLS -> !AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.WALLS)
                    || !AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.MATERIALS);
            case EFFECTS -> !AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.EFFECTS);
            default -> true;
        };
    }

    /** Resets what the current tab shows, leaving the parts the server locks alone. */
    private void resetTab() {
        switch (tab) {
            case DISTANCE -> resetUnlocked(DistanceConfig.Part.CURVE);
            case WALLS -> {
                resetUnlocked(DistanceConfig.Part.WALLS);
                resetUnlocked(DistanceConfig.Part.MATERIALS);
            }
            case EFFECTS -> resetUnlocked(DistanceConfig.Part.EFFECTS);
            case HUD -> config.resetHud();
            default -> {
            }
        }
        rebuild();
    }

    private void resetUnlocked(DistanceConfig.Part part) {
        if (!AudioDistancePlugin.LINK.isLocked(part)) {
            config.resetPart(part);
        }
    }

    /** Top-right corner: what the server offers, when it has the addon. */
    private void initServerChip(int w) {
        LinkProtocol.ServerProfile profile = AudioDistancePlugin.LINK.profile();
        if (profile == null || profile.mode() == com.kasper.vcdistance.ServerSettings.ProfileMode.OFF) {
            return;
        }
        serverChip = true;
        if (AudioDistancePlugin.LINK.isSuggested()) {
            Component label = tr("server.apply");
            int bw = Math.min(this.font.width(label) + 16, w / 2);
            addRenderableWidget(Button.builder(label, b -> {
                config.copyFrom(profile.config());
                rebuild();
            }).bounds(right - bw, 2, bw, 16).tooltip(tip("server.apply.tooltip")).build());
        }
    }

    /** What is heard right now: the server's profile while it is enforced, otherwise the player's settings. */
    private static DistanceConfig shown() {
        return AudioDistancePlugin.config();
    }

    // ---- Distance -----------------------------------------------------------

    private void initDistance() {
        int w = right - left;
        int colW = (w - GAP) / 2;
        int col2 = right - colW;
        int y = contentTop;

        Preset[] presets = Preset.values();
        int pw = (w - GAP * (presets.length - 1)) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            Preset p = presets[i];
            int x = i == presets.length - 1 ? right - pw : left + i * (pw + GAP);
            Button b = Button.builder(Component.translatable(p.getTranslationKey()), btn -> {
                double range = AudioDistancePlugin.getServerMaxDistance();
                // A preset sets the curve and the walls, but never walls the server locks
                p.apply(config, range, !AudioDistancePlugin.LINK.isLocked(DistanceConfig.Part.WALLS));
                config.setChosenPreset(p, range);
                rebuild();
            }).bounds(x, y, pw, 20).tooltip(Tooltip.create(Component.translatable(p.getTooltipKey()))).build();
            presetButtons.add(b);
            presetOrder.add(p);
            edit(b, DistanceConfig.Part.CURVE);
        }
        y += ROW;

        // Header strip above the graph: the summary and legend, and Listen (allowed while the server
        // enforces its profile). One width for both labels so it does not jump when it toggles.
        graphHeaderY = y;
        int lw = Math.max(this.font.width(tr("listen")), this.font.width(tr("listen.stop"))) + 12;
        listenButton = content(Button.builder(previewStep >= 0 ? tr("listen.stop") : tr("listen"), b -> togglePreview())
                .bounds(right - lw, y, lw, 14).tooltip(tip("listen.tooltip")).build());
        graphTop = y + 17;

        // The graph keeps a readable shape; on short windows it shrinks down to a minimum and the tab scrolls
        int preferred = Math.max(120, w * 2 / 5);
        int room = viewBottom - graphTop - 6 - (ROW * 3 - GAP) - 3;
        graphBottom = graphTop + Math.max(100, Math.min(room, preferred));
        int rows = graphBottom + 6;

        edit(Button.builder(modelLabel(), b -> {
            config.setModel(config.getModel().next());
            b.setMessage(modelLabel());
            b.setTooltip(Tooltip.create(Component.translatable(config.getModel().getTooltipKey())));
        }).bounds(left, rows, colW, 20).tooltip(Tooltip.create(Component.translatable(shown().getModel().getTooltipKey()))).build(),
                DistanceConfig.Part.CURVE);

        edit(withTip(new RangeSlider(col2, rows, colW, 20,
                DistanceConfig.ROLLOFF_MIN, DistanceConfig.ROLLOFF_MAX, 0.01,
                () -> shown().getAttenuationFactor(), config::setAttenuationFactor,
                v -> tr("falloff", pct(v))), "falloff.tooltip"), DistanceConfig.Part.CURVE);

        edit(withTip(new RangeSlider(left, rows + ROW, colW, 20,
                DistanceConfig.REFERENCE_MIN, DistanceConfig.REFERENCE_MAX, 0.01,
                () -> shown().getOpenalReferenceRatio(), config::setOpenalReferenceRatio,
                v -> tr("reference", blocks(v * AudioDistancePlugin.getServerMaxDistance()))), "reference.tooltip"),
                DistanceConfig.Part.CURVE);

        edit(withTip(new RangeSlider(col2, rows + ROW, colW, 20,
                DistanceConfig.MIN_VOLUME_MIN, DistanceConfig.MIN_VOLUME_MAX, 0.01,
                () -> shown().getMinVolumeFraction(), config::setMinVolumeFraction,
                v -> tr("floor", pct(v))), "floor.tooltip"), DistanceConfig.Part.CURVE);

        edit(withTip(new RangeSlider(left, rows + ROW * 2, w, 20,
                DistanceConfig.WHISPER_MIN, DistanceConfig.WHISPER_MAX, 0.05,
                () -> shown().getWhisperMultiplier(), config::setWhisperMultiplier,
                v -> tr("whisper", pct(v))), "whisper.tooltip"), DistanceConfig.Part.CURVE);
        contentEnd = rows + ROW * 2 + 20;
    }

    // ---- Walls --------------------------------------------------------------

    private void initWalls() {
        int w = right - left;
        int colW = (w - GAP) / 2;
        int y = contentTop;

        edit(Button.builder(wallsLabel(), b -> {
            config.setOcclusionEnabled(!config.isOcclusionEnabled());
            b.setMessage(wallsLabel());
            if (strengthSlider != null) {
                strengthSlider.active = config.isOcclusionEnabled();
            }
        }).bounds(left, y, colW, 20).tooltip(tip("walls.toggle.tooltip")).build(), DistanceConfig.Part.WALLS);

        strengthSlider = new RangeSlider(right - colW, y, colW, 20,
                DistanceConfig.STRENGTH_MIN, DistanceConfig.STRENGTH_MAX, 0.01,
                () -> shown().getOcclusionStrength(), config::setOcclusionStrength,
                v -> tr("strength", pct(v)));
        strengthSlider.active = shown().isOcclusionEnabled();
        edit(withTip(strengthSlider, "strength.tooltip"), DistanceConfig.Part.WALLS);
        y += ROW;

        statusY = y;
        if (hasStatusBanner()) {
            y += 30;
        }
        panelTop = y;
        panelBottom = y + 20 + EXAMPLES.length * 14 + 18;
        y = panelBottom + 6;

        // Materials: a section that opens under the preview
        content(Button.builder(Component.literal(materialsOpen ? "▾ " : "▸ ").append(tr("materials.section")), b -> {
            materialsOpen = !materialsOpen;
            rebuild();
        }).bounds(left, y, w, 20).tooltip(tip("materials.hint")).build());
        y += ROW;
        if (materialsOpen) {
            materialsHintY = y;
            y += 14;
            AcousticMaterial[] materials = AcousticMaterial.values();
            // Materials fill the grid; the reset button takes the cell after the last one
            int cells = materials.length + 1;
            int cols = (w - GAP * 2) / 3 >= 120 ? 3 : 2;
            int cellW = (w - GAP * (cols - 1)) / cols;
            for (int i = 0; i < cells; i++) {
                int col = i % cols;
                int x = col == cols - 1 ? right - cellW : left + col * (cellW + GAP);
                int cy = y + (i / cols) * ROW;
                if (i == materials.length) {
                    edit(Button.builder(tr("materials.reset"), b -> {
                        config.resetMaterials();
                        rebuild();
                    }).bounds(x, cy, cellW, 20).tooltip(tip("materials.reset.tooltip")).build(), DistanceConfig.Part.MATERIALS);
                    break;
                }
                AcousticMaterial m = materials[i];
                RangeSlider slider = new RangeSlider(x, cy, cellW, 20, 0.0, AcousticMaterial.MAX_WEIGHT, 0.05,
                        () -> shown().getMaterialWeight(m), v -> config.setMaterialWeight(m, v),
                        v -> tr("material.value", Component.translatable(m.getTranslationKey()), pct(v)));
                slider.setTooltip(Tooltip.create(Component.translatable(m.getTooltipKey())));
                edit(slider, DistanceConfig.Part.MATERIALS);
            }
            y += ((cells + cols - 1) / cols) * ROW;
        }
        contentEnd = y - GAP;
    }

    private static boolean hasStatusBanner() {
        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        return status == AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS || status == AudioDistancePlugin.OcclusionStatus.UNAVAILABLE;
    }

    // ---- Effects ------------------------------------------------------------

    private void initEffects() {
        int w = right - left;
        int colW = (w - GAP) / 2;
        int y = contentTop;
        edit(Button.builder(onOff("effects.reverb", shown().isReverbEnabled()), b -> {
            config.setReverbEnabled(!config.isReverbEnabled());
            b.setMessage(onOff("effects.reverb", config.isReverbEnabled()));
            if (reverbSlider != null) {
                reverbSlider.active = config.isReverbEnabled();
            }
        }).bounds(left, y, colW, 20).tooltip(tip("effects.reverb.tooltip")).build(), DistanceConfig.Part.EFFECTS);
        reverbSlider = new RangeSlider(right - colW, y, colW, 20,
                DistanceConfig.REVERB_MIN, DistanceConfig.REVERB_MAX, 0.01,
                () -> shown().getReverbStrength(), config::setReverbStrength,
                v -> tr("effects.reverb.strength", pct(v)));
        reverbSlider.active = shown().isReverbEnabled();
        edit(withTip(reverbSlider, "effects.reverb.strength.tooltip"), DistanceConfig.Part.EFFECTS);

        edit(Button.builder(onOff("effects.water", shown().isUnderwaterEnabled()), b -> {
            config.setUnderwaterEnabled(!config.isUnderwaterEnabled());
            b.setMessage(onOff("effects.water", config.isUnderwaterEnabled()));
        }).bounds(left, y + ROW, colW, 20).tooltip(tip("effects.water.tooltip")).build(), DistanceConfig.Part.EFFECTS);
        edit(Button.builder(onOff("effects.weather", shown().isWeatherEnabled()), b -> {
            config.setWeatherEnabled(!config.isWeatherEnabled());
            b.setMessage(onOff("effects.weather", config.isWeatherEnabled()));
        }).bounds(right - colW, y + ROW, colW, 20).tooltip(tip("effects.weather.tooltip")).build(), DistanceConfig.Part.EFFECTS);
        edit(Button.builder(onOff("effects.corners", shown().isDiffractionEnabled()), b -> {
            config.setDiffractionEnabled(!config.isDiffractionEnabled());
            b.setMessage(onOff("effects.corners", config.isDiffractionEnabled()));
        }).bounds(left, y + ROW * 2, colW, 20).tooltip(tip("effects.corners.tooltip")).build(), DistanceConfig.Part.EFFECTS);

        statusY = y + ROW * 3 + 2;
        panelTop = statusY + (hasStatusBanner() ? 30 : 0);
        panelBottom = stretch(panelTop, 140);
        contentEnd = panelBottom;
    }

    private static Component onOff(String key, boolean on) {
        return tr(key, tr(on ? "on" : "off"));
    }

    // ---- HUD ----------------------------------------------------------------

    /** The HUD's look, with made-up voices in a preview; never locked by the server. */
    private void initHud() {
        int w = right - left;
        int colW = (w - GAP) / 2;
        int col2 = right - colW;
        int y = contentTop;
        DistanceConfig prefs = config;
        content(Button.builder(HudOverlay.modeLabel(prefs.getHudMode()), b -> {
            prefs.setHudMode(prefs.getHudMode().next());
            b.setMessage(HudOverlay.modeLabel(prefs.getHudMode()));
        }).bounds(left, y, colW, 20).tooltip(tip("hud.mode.tooltip")).build());
        content(Button.builder(HudOverlay.cornerLabel(prefs.getHudCorner()), b -> {
            prefs.setHudCorner(prefs.getHudCorner().next());
            b.setMessage(HudOverlay.cornerLabel(prefs.getHudCorner()));
        }).bounds(col2, y, colW, 20).tooltip(tip("hud.corner.tooltip")).build());

        content(withTip(new RangeSlider(left, y + ROW, colW, 20,
                DistanceConfig.HUD_SCALE_MIN, DistanceConfig.HUD_SCALE_MAX, 0.05,
                prefs::getHudScale, prefs::setHudScale, v -> tr("hud.scale", pct(v))), "hud.scale.tooltip"));
        content(withTip(new RangeSlider(col2, y + ROW, colW, 20, 0.0, 1.0, 0.05,
                prefs::getHudBackground, prefs::setHudBackground, v -> tr("hud.background", pct(v))), "hud.background.tooltip"));

        content(Button.builder(onOff("hud.compact", prefs.isHudCompact()), b -> {
            prefs.setHudCompact(!prefs.isHudCompact());
            b.setMessage(onOff("hud.compact", prefs.isHudCompact()));
        }).bounds(left, y + ROW * 2, colW, 20).tooltip(tip("hud.compact.tooltip")).build());
        content(Button.builder(colorsLabel(), b -> {
            prefs.setColorblind(!prefs.isColorblind());
            b.setMessage(colorsLabel());
        }).bounds(col2, y + ROW * 2, colW, 20).tooltip(tip("colors.tooltip")).build());

        panelTop = y + ROW * 3 + 2;
        panelBottom = stretch(panelTop, 110);
        contentEnd = panelBottom;
    }

    private Component colorsLabel() {
        return tr("colors", tr(config.isColorblind() ? "colors.colorblind" : "colors.normal"));
    }

    // ---- Monitor ------------------------------------------------------------

    private void initMonitor() {
        int colW = (right - left - GAP) / 2;
        content(Button.builder(viewLabel(), b -> {
            radarView = !radarView;
            b.setMessage(viewLabel());
        }).bounds(left, contentTop, colW, 20).tooltip(tip("monitor.view.tooltip")).build());
        panelTop = contentTop + ROW + 2;
        panelBottom = stretch(panelTop, 170);
        contentEnd = panelBottom;
    }

    private static Component viewLabel() {
        return tr("monitor.view", tr(radarView ? "monitor.view.radar" : "monitor.view.list"));
    }

    // =========================================================================
    // Server tab (admins)
    // =========================================================================

    private static final String[] SERVER_MODES = {"off", "suggest", "enforce"};
    private static final String[] SERVER_PRESETS = {"custom", "vanilla", "realistic", "clear", "stealth"};
    private static final String[] SERVER_REQUIRE = {"off", "suggest", "warn", "kick"};
    private static final String[] SERVER_SNEAK = {"1", "0.7", "0.5", "0.3"};
    private static final String[] ZONE_RANGE = {"-", "0.4", "2", "3"};
    private static final String[] ZONE_WALLS = {"-", "0", "0.5", "1"};
    private static final String[] ZONE_ECHO = {"-", "off", "0.5", "0.9"};
    private static final String MEGAPHONE = "minecraft:goat_horn";
    /** What {@code /vcd lock} cycles through on the Server tab. */
    private static final String[] SERVER_LOCKS = {"all", "curve,walls", "curve", "none"};

    private static java.util.Properties serverState() {
        LinkProtocol.AdminReply reply = AudioDistancePlugin.LINK.adminReply();
        return reply == null ? null : reply.state();
    }

    /** The value after {@code current} in {@code values}, comparing numbers as numbers. */
    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (same(values[i], current)) {
                return values[(i + 1) % values.length];
            }
        }
        // A value set elsewhere (0.85 in the file): go on to the next step above it
        try {
            double v = Double.parseDouble(current);
            for (String value : values) {
                if (Double.parseDouble(value) > v) {
                    return value;
                }
            }
        } catch (NumberFormatException | NullPointerException ignored) {
            // not a number list
        }
        return values[0];
    }

    private static boolean same(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        try {
            return Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) < 0.005;
        } catch (NumberFormatException e) {
            return a.equalsIgnoreCase(b);
        }
    }

    private Button serverButton(Component label, String tooltip, int x, int y, int w, String command) {
        Button b = Button.builder(label, btn -> AudioDistancePlugin.LINK.sendAdmin(command)).bounds(x, y, w, 20).build();
        if (tooltip != null) {
            b.setTooltip(tip(tooltip));
        }
        return content(b);
    }

    /** A toggle: "on" or "off" sent after {@code command}. */
    private Button serverToggle(String key, java.util.Properties st, String property, String fallback, int x, int y, int w,
                                String command) {
        boolean on = "true".equalsIgnoreCase(st.getProperty(property, fallback));
        return serverButton(tr(key, tr(on ? "on" : "off")), key + ".tooltip", x, y, w, command + (on ? " off" : " on"));
    }

    private static Component presetName(String name) {
        return switch (name == null ? "custom" : name) {
            case "vanilla" -> Component.translatable(Preset.VANILLA.getTranslationKey());
            case "realistic" -> Component.translatable(Preset.REALISTIC.getTranslationKey());
            case "clear" -> Component.translatable(Preset.CLEAR.getTranslationKey());
            case "stealth" -> Component.translatable(Preset.ATMOSPHERIC.getTranslationKey());
            default -> tr("server.preset.custom");
        };
    }

    private static Component lockedName(String locked) {
        return switch (locked) {
            case "all" -> tr("server.locked.all");
            case "none" -> tr("server.locked.none");
            case "curve" -> tr("server.locked.curve");
            case "curve,walls" -> tr("server.locked.curve_walls");
            default -> Component.literal(locked);
        };
    }

    /** A section title, then the section's first row. */
    private int heading(String key, int y) {
        headings.add(new Heading(tr(key), y, right));
        return y + 13;
    }

    private void initServer() {
        java.util.Properties st = serverState();
        int w = right - left;
        // Two columns: the labels ("Spectators apart: On") are too long for three
        int half = (w - GAP) / 2;
        int x2 = right - half;
        int y = contentTop;
        if (st == null) {
            contentEnd = contentTop + 40;
            return;
        }

        // Profile: how it reaches players, which sound, what they cannot change
        y = heading("server.section.profile", y);
        String mode = st.getProperty("profile_mode", "off");
        String preset = st.getProperty("profile_preset", "custom");
        String locked = st.getProperty("profile_locked", "all");
        serverButton(tr("server.profile", tr("server.mode." + mode)), "server.profile.tooltip", left, y, half,
                "profile " + next(SERVER_MODES, mode));
        serverButton(tr("server.preset", presetName(preset)), "server.preset.tooltip", x2, y, half,
                "preset " + next(SERVER_PRESETS, preset));
        y += ROW;
        serverButton(tr("server.locked", lockedName(locked)), "server.locked.tooltip", left, y, half,
                "lock " + next(SERVER_LOCKS, locked));
        y += ROW + 4;

        // Walls: − and + in 5% steps either side of the value
        y = heading("server.section.walls", y);
        int wallsPct = (int) Math.round(parse(st.getProperty("walls_strength", "0")) * 100.0);
        int down = Math.max(0, (wallsPct + 4) / 5 * 5 - 5);
        int up = Math.min(100, wallsPct / 5 * 5 + 5);
        serverButton(Component.literal("−"), "server.walls.tooltip", left, y, 20, down == 0 ? "walls off" : "walls " + down)
                .active = wallsPct > 0;
        serverButton(tr("server.walls", wallsPct == 0 ? tr("off") : Component.literal(wallsPct + "%")), "server.walls.tooltip",
                left + 22, y, half - 44, "walls " + up).active = wallsPct < 100;
        serverButton(Component.literal("+"), "server.walls.tooltip", left + half - 20, y, 20, "walls " + up)
                .active = wallsPct < 100;
        serverToggle("server.server_walls", st, "server_walls", "false", x2, y, half, "serverwalls");
        y += ROW;
        serverToggle("server.monitor", st, "allow_monitor", "true", left, y, half, "monitor");
        y += ROW + 4;

        // Game rules
        y = heading("server.section.rules", y);
        String sneak = st.getProperty("sneak_range_multiplier", "1");
        serverButton(tr("server.sneak", pct(parse(sneak))), "server.sneak.tooltip", left, y, half,
                "rule sneak " + next(SERVER_SNEAK, sneak));
        String megaphone = st.getProperty("megaphone_item", "");
        serverButton(tr("server.megaphone", megaphone.isEmpty() ? tr("off") : Component.translatable("item.minecraft.goat_horn")),
                "server.megaphone.tooltip", x2, y, half, "rule megaphone " + (megaphone.isEmpty() ? MEGAPHONE : "off"));
        y += ROW;
        serverToggle("server.dead", st, "dead_players_silent", "false", left, y, half, "rule dead");
        serverToggle("server.spectators", st, "spectators_hear_only_spectators", "false", x2, y, half, "rule spectators");
        y += ROW + 4;

        // Rules inside Simple Voice Chat groups
        y = heading("server.section.groups", y);
        serverToggle("server.group.dead", st, "group_dead_silent", "false", left, y, half, "group dead");
        serverToggle("server.group.spectators", st, "group_spectators_apart", "false", x2, y, half, "group spectators");
        y += ROW;
        serverToggle("server.group.zones", st, "group_isolated_zones", "false", left, y, half, "group zones");
        serverToggle("server.group.open_range", st, "open_group_range", "true", x2, y, half, "group open_range");
        y += ROW + 4;

        // Players who have Simple Voice Chat but not this addon
        y = heading("server.section.addon", y);
        String require = st.getProperty("require_addon", "off");
        serverButton(tr("server.require", tr("server.require." + require)), "server.require.tooltip", left, y, half,
                "require " + next(SERVER_REQUIRE, require));
        y += ROW + 4;

        y = initZones(st, y, w, (w - GAP * 2) / 3);
        contentEnd = y;
    }

    /** Zones: a list to pick from, and the picked zone's settings right under it. */
    private int initZones(java.util.Properties st, int y, int w, int third) {
        List<String[]> zones = new ArrayList<>();
        for (int i = 0; st.getProperty("zone." + i) != null; i++) {
            String[] z = st.getProperty("zone." + i).split("\\|", -1);
            if (z.length >= 11) {
                zones.add(z);
            }
        }
        int used = zones.size() + 1;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String[] z : zones) {
            names.add(z[1]);
        }
        while (names.contains("zone-" + used)) {
            used++;
        }
        int createW = Math.min(third * 2, this.font.width(tr("server.zone.create")) + 16);
        headings.add(new Heading(tr("server.zones"), y + 6, right - createW - 8));
        serverButton(tr("server.zone.create"), "server.zone.create.tooltip", right - createW, y, createW,
                "zone create zone-" + used + " 8");
        y += ROW;
        if (zones.isEmpty()) {
            noZonesY = y + 2;
            return y + 14;
        }

        LinkProtocol.ServerProfile profile = AudioDistancePlugin.LINK.profile();
        String here = profile == null ? null : profile.zone();
        int x2 = left + third + GAP;
        int x3 = right - third;
        for (String[] z : zones) {
            String name = z[1];
            boolean picked = name.equals(selectedZone);
            Component label = zoneLabel(z);
            if (name.equals(here)) {
                label = Component.empty().append(label).append(Component.literal(" · ")).append(tr("server.zone.here"));
            }
            content(Button.builder(Component.literal(picked ? "▾ " : "▸ ").append(label), btn -> {
                selectedZone = name.equals(selectedZone) ? null : name;
                confirmDelete = null;
                rebuild();
            }).bounds(left, y, w, 20).build());
            y += 22;
            if (!picked) {
                continue;
            }
            serverButton(tr("server.zone.range", "-".equals(z[6]) ? tr("server.default") : Component.literal("×" + z[6])),
                    "server.zone.range.tooltip", left, y, third,
                    "zone set " + name + " range_multiplier " + zoneValue(next(ZONE_RANGE, z[6])));
            serverButton(tr("server.zone.walls", "-".equals(z[7]) ? tr("server.default") : Component.literal(pct(parse(z[7])))),
                    "server.zone.walls.tooltip", x2, y, third,
                    "zone set " + name + " walls " + zoneValue(next(ZONE_WALLS, z[7])));
            serverButton(tr("server.zone.echo", "-".equals(z[8]) ? tr("server.default")
                            : "off".equals(z[8]) ? tr("off") : Component.literal(pct(parse(z[8])))),
                    "server.zone.echo.tooltip", x3, y, third,
                    "zone set " + name + " echo " + zoneValue(next(ZONE_ECHO, z[8])));
            y += ROW;
            boolean isolated = "true".equalsIgnoreCase(z[9]);
            serverButton(tr("server.zone.isolated", tr(isolated ? "on" : "off")), "server.zone.isolated.tooltip", left, y, third,
                    "zone set " + name + " isolated " + (isolated ? "off" : "on"));
            // Only a box has borders to draw
            serverButton(tr("server.zone.show"), "server.zone.show.tooltip", x2, y, third, "zone show " + name)
                    .active = "box".equals(z[0]);
            boolean confirm = name.equals(confirmDelete);
            content(Button.builder(tr(confirm ? "server.zone.delete.confirm" : "server.zone.delete"), btn -> {
                if (name.equals(confirmDelete)) {
                    confirmDelete = null;
                    selectedZone = null;
                    AudioDistancePlugin.LINK.sendAdmin("zone delete " + name);
                } else {
                    confirmDelete = name;
                    rebuild();
                }
            }).bounds(x3, y, third, 20).tooltip(tip("server.zone.delete.tooltip")).build());
            y += ROW + 4;
        }
        return y;
    }

    private static String zoneValue(String v) {
        return "-".equals(v) ? "default" : v;
    }

    private static double parse(String v) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException | NullPointerException e) {
            return 0.0;
        }
    }

    /** "Box stage · range ×2 · isolated" for the zone list. */
    private static Component zoneLabel(String[] z) {
        List<Component> parts = new ArrayList<>();
        if (!"-".equals(z[6])) {
            parts.add(tr("server.zone.range", Component.literal("×" + z[6])));
        }
        if (!"-".equals(z[4])) {
            parts.add(tr("server.zone.voice", z[4]));
        }
        if (!"-".equals(z[7])) {
            parts.add(tr("server.zone.walls", Component.literal(pct(parse(z[7])))));
        }
        if (!"-".equals(z[8])) {
            parts.add(tr("server.zone.echo", "off".equals(z[8]) ? tr("off") : Component.literal(pct(parse(z[8])))));
        }
        if ("true".equalsIgnoreCase(z[9])) {
            parts.add(tr("server.zone.isolated_short"));
        }
        if (!"-".equals(z[3])) {
            parts.add(presetName(z[3]));
        }
        Component label = Component.empty().append(tr("server.kind." + z[0])).append(Component.literal(" " + z[1]));
        for (Component p : parts) {
            label = Component.empty().append(label).append(Component.literal(" · ")).append(p);
        }
        return label;
    }

    private void paintServer(Canvas c) {
        LinkProtocol.AdminReply reply = AudioDistancePlugin.LINK.adminReply();
        if (reply == null) {
            c.centered(tr("server.loading"), (left + right) / 2, contentTop + 16, Palette.TEXT_DIM);
            return;
        }
        for (Heading h : headings) {
            c.text(h.text(), left + 1, h.y(), Palette.ACCENT_LINE);
            int lineX = left + c.width(h.text()) + 8;
            if (lineX < h.lineEnd()) {
                c.hLine(lineX, h.lineEnd(), h.y() + 4, Palette.PANEL_BORDER);
            }
        }
        if (noZonesY >= 0) {
            c.text(fit(c, tr("server.zones.none"), right - left), left + 1, noZonesY, Palette.TEXT_MUTED);
        }
    }

    /** The server's answer to the last change ("Saved ..."), or a hint, left of Done in the footer. */
    private void paintServerStatus(Canvas c) {
        LinkProtocol.AdminReply reply = AudioDistancePlugin.LINK.adminReply();
        java.util.List<String> lines = reply == null ? List.of() : reply.lines();
        Component status = lines.isEmpty() || lines.get(0).startsWith("Voice Physics")
                ? tr("server.hint") : Component.literal(lines.get(lines.size() - 1));
        int w = right - left;
        int doneW = (w - GAP * 3) / 4;
        c.text(fit(c, status, w - doneW - GAP - 2), left + 1, footerY + 6, Palette.TEXT_MUTED);
    }

    // =========================================================================
    // Profile code
    // =========================================================================

    private void copyCode() {
        try {
            writeClipboard(ProfileCode.encode(shown()));
            showCodeFeedback("code.copied", null);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Could not copy the profile code: {}", t.toString());
            showCodeFeedback("code.failed", null);
        }
    }

    private void pasteCode() {
        String text;
        try {
            text = readClipboard();
        } catch (Throwable t) {
            text = "";
        }
        if (ProfileCode.decode(text, config)) {
            config.setChosenPreset(null, 0.0);
            showCodeFeedback(null, "code.pasted");
            rebuild();
        } else {
            showCodeFeedback(null, "code.invalid");
        }
    }

    private void showCodeFeedback(String copy, String paste) {
        copyFeedback = copy;
        pasteFeedback = paste;
        codeFeedbackTicks = 40;
        if (copyButton != null) {
            copyButton.setMessage(tr(copy != null ? copy : "code.copy"));
        }
        if (pasteButton != null) {
            pasteButton.setMessage(tr(paste != null ? paste : "code.paste"));
        }
    }

    // =========================================================================
    // Walk-away preview
    // =========================================================================

    private void togglePreview() {
        if (previewStep >= 0) {
            stopPreview();
            return;
        }
        previewStep = 0;
        previewTicks = 0;
        playPreviewStep();
        if (listenButton != null) {
            listenButton.setMessage(tr("listen.stop"));
        }
    }

    private void stopPreview() {
        previewStep = -1;
        if (listenButton != null) {
            listenButton.setMessage(tr("listen"));
        }
    }

    private void playPreviewStep() {
        DistanceConfig shown = shown();
        double gain = AudioPhysics.calculateGain(PREVIEW_STEPS[previewStep], shown.getModel(),
                shown.getAttenuationFactor(), shown.getMinVolumeFraction(), shown.getOpenalReferenceRatio());
        if (gain > 0.005) {
            try {
                playPreview((float) gain);
            } catch (Throwable t) {
                DistanceConfig.LOGGER.debug("Preview sound failed: {}", t.toString());
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == Tab.SERVER && AudioDistancePlugin.LINK.adminReplyCount() != seenAdminReplies) {
            seenAdminReplies = AudioDistancePlugin.LINK.adminReplyCount();
            rebuild();
        }
        if (codeFeedbackTicks > 0 && --codeFeedbackTicks == 0) {
            showCodeFeedback(null, null);
            codeFeedbackTicks = 0;
        }
        if (previewStep < 0) {
            return;
        }
        if (++previewTicks >= PREVIEW_STEP_TICKS) {
            previewTicks = 0;
            previewStep++;
            if (previewStep >= PREVIEW_STEPS.length) {
                stopPreview();
            } else {
                playPreviewStep();
            }
        }
    }

    private void switchTab(Tab t) {
        previewStep = -1;
        if (t != tab) {
            scroll = 0;
        }
        tab = t;
        lastTab = t;
        confirmDelete = null;
        if (t == Tab.SERVER) {
            // Fresh settings from the server; the tab fills in when the reply arrives
            AudioDistancePlugin.LINK.sendAdmin("status");
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    // =========================================================================
    // Closing
    // =========================================================================

    private void cancel() {
        config.copyFrom(snapshot);
        config.copyInterfaceFrom(snapshot);
        openScreen(parent);
    }

    private void saveAndClose() {
        config.save();
        openScreen(parent);
    }

    @Override
    public void onClose() {
        // Esc keeps the changes, like vanilla option screens
        saveAndClose();
    }

    // =========================================================================
    // Painting
    // =========================================================================

    /** Draws everything that is not a widget. Called by the version subclass after the widgets. */
    protected void paint(Canvas c, int mouseX, int mouseY) {
        Palette.useColorblind(config.isColorblind());
        if (serverChip) {
            c.text(this.title, left, 8, Palette.TEXT);
            if (AudioDistancePlugin.LINK.isEnforced()) {
                boolean lockedHere = !lockedWidgets.isEmpty() || tab == Tab.MONITOR || tab == Tab.SERVER;
                c.right(tr(lockedHere ? "server.enforced" : "server.enforced.free"), right, 8,
                        lockedHere ? Palette.WARN : Palette.TEXT_MUTED);
            }
        } else {
            c.centered(this.title, this.width / 2, 8, Palette.TEXT);
        }
        // The open tab and the preset that matches the sound: framed and underlined
        if (activeTabButton != null) {
            mark(c, activeTabButton);
        }
        for (int i = 0; i < presetButtons.size(); i++) {
            Button b = presetButtons.get(i);
            if (b.visible && presetOrder.get(i).matches(shown(), AudioDistancePlugin.getServerMaxDistance())) {
                mark(c, b);
            }
        }

        // The scrolling band: content coordinates, cut at the band's edges
        Canvas band = new ScrollCanvas(c, scroll, viewTop, viewBottom);
        boolean inBand = mouseY >= viewTop && mouseY < viewBottom;
        int my = inBand ? mouseY + scroll : Integer.MIN_VALUE / 2;
        switch (tab) {
            case DISTANCE -> paintDistance(band, mouseX, my);
            case WALLS -> paintWalls(band);
            case EFFECTS -> paintEffects(band);
            case HUD -> paintHud(band);
            case MONITOR -> paintMonitor(band, mouseX, my);
            case SERVER -> paintServer(band);
        }
        for (AbstractWidget widget : lockedWidgets) {
            if (widget.visible) {
                lockIcon(c, widget.getX() + widget.getWidth() - 11, widget.getY() + (widget.getHeight() - 8) / 2);
            }
        }
        if (maxScroll > 0) {
            paintScrollbar(c);
        }
        if (tab == Tab.SERVER) {
            paintServerStatus(c);
        }
    }

    private static void mark(Canvas c, AbstractWidget b) {
        int x1 = b.getX();
        int y1 = b.getY();
        int x2 = x1 + b.getWidth();
        int y2 = y1 + b.getHeight();
        c.frame(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x00000000, Palette.withAlpha(Palette.ACCENT, 0xC0));
        c.fill(x1 + 2, y2 + 1, x2 - 2, y2 + 3, Palette.ACCENT);
    }

    /** A small padlock on a control the server locks. */
    private static void lockIcon(Canvas c, int x, int y) {
        int color = Palette.WARN;
        c.fill(x + 2, y, x + 5, y + 1, color);
        c.fill(x + 1, y + 1, x + 2, y + 3, color);
        c.fill(x + 5, y + 1, x + 6, y + 3, color);
        c.fill(x, y + 3, x + 7, y + 8, color);
        c.fill(x + 3, y + 5, x + 4, y + 7, 0xFF000000);
    }

    /** A thin bar right of the content: where the visible band is in the whole tab. */
    private void paintScrollbar(Canvas c) {
        int x = Math.min(this.width - 3, right + 3);
        int trackH = viewBottom - viewTop;
        int total = trackH + maxScroll;
        int thumbH = Math.max(16, trackH * trackH / total);
        int thumbY = viewTop + (int) Math.round((double) (trackH - thumbH) * scroll / maxScroll);
        c.fill(x, viewTop, x + 2, viewBottom, 0x30FFFFFF);
        c.fill(x, thumbY, x + 2, thumbY + thumbH, Palette.withAlpha(Palette.TEXT, 0xB0));
    }

    // ---- Distance -----------------------------------------------------------

    private void paintDistance(Canvas c, int mouseX, int mouseY) {
        int x1 = left;
        int x2 = right;
        int headerY = graphHeaderY + 3;
        int y1 = graphTop;
        int y2 = graphBottom;

        double maxDist = AudioDistancePlugin.getServerMaxDistance();
        DistanceConfig shown = shown();
        AttenuationModel model = shown.getModel();
        double rolloff = shown.getAttenuationFactor();
        double floor = shown.getMinVolumeFraction();
        double ref = shown.getOpenalReferenceRatio();

        // Header: plain-language summary + legend. Every curve ends at the edge volume, so the
        // summary names the loudness halfway through the fade, where the curves differ.
        double middle = ref + (1.0 - ref) / 2.0;
        Component summary = rolloff <= 0.001
                ? tr("summary.flat", blocks(maxDist))
                : tr("summary.curve", blocks(ref * maxDist), pct(AudioPhysics.calculateGain(middle, model, rolloff, floor, ref)), blocks(middle * maxDist));
        Component voice = tr("legend.voice");
        Component whisper = tr("legend.whisper");
        int legendW = c.width(voice) + c.width(whisper) + 36;
        int headRight = listenButton != null ? listenButton.getX() - 8 : x2;
        boolean legend = c.width(summary) + legendW + 14 <= headRight - x1;
        c.text(fit(c, summary, (legend ? headRight - legendW : headRight) - x1 - 2), x1 + 1, headerY, Palette.TEXT_DIM);
        if (legend) {
            int lx = headRight - c.width(whisper);
            c.text(whisper, lx, headerY, Palette.TEXT_DIM);
            c.dashedHLine(lx - 12, lx - 3, headerY + 4, 2, Palette.WHISPER);
            lx -= 16 + c.width(voice);
            c.text(voice, lx, headerY, Palette.TEXT_DIM);
            c.fill(lx - 12, headerY + 3, lx - 3, headerY + 5, Palette.ACCENT_LINE);
        }

        c.frame(x1, y1, x2, y2, Palette.PANEL, Palette.PANEL_BORDER);
        // Volume scale on the left, distance scale along the bottom
        int scaleW = c.width(Component.literal("100%")) + 4;
        int px1 = x1 + 4 + scaleW;
        int px2 = x2 - 8;
        int py1 = y1 + 8;
        int py2 = y2 - 13;
        int pw = px2 - px1;
        int ph = py2 - py1;
        if (pw < 40 || ph < 14) {
            return;
        }

        for (int i = 1; i <= 4; i++) {
            int gy = py2 - ph * i / 4;
            if (i < 4) {
                c.hLine(px1, px2, gy, Palette.GRID);
                c.vLine(px1 + pw * i / 4, py1, py2, Palette.GRID);
            }
            if (i % 2 == 0 || ph >= 60) {
                c.right(Component.literal((25 * i) + "%"), px1 - 3, gy - 3, Palette.TEXT_MUTED);
            }
        }
        // Full-volume zone: shaded, with its edge marked
        int refX = px1 + (int) Math.round(ref * pw);
        c.fill(px1, py1, refX, py2, Palette.ACCENT_ZONE);
        if (ref > 0.0 && refX < px2) {
            c.dashedVLine(refX, py1, py2, 2, Palette.withAlpha(Palette.ACCENT, 0x70));
        }
        c.hLine(px1, px2 + 1, py2, Palette.PANEL_BORDER);

        // Voice curve: filled area + line
        int prevY = -1;
        for (int i = 0; i <= pw; i++) {
            double g = AudioPhysics.calculateGain((double) i / pw, model, rolloff, floor, ref);
            int y = py2 - (int) Math.round(g * ph);
            int x = px1 + i;
            if (i < pw) {
                c.fill(x, y, x + 1, py2, Palette.ACCENT_AREA);
            }
            int top = prevY < 0 ? y : Math.min(prevY, y);
            int bottom = prevY < 0 ? y : Math.max(prevY, y);
            c.fill(x, top, x + 1, bottom + 1, Palette.ACCENT_LINE);
            prevY = y;
        }

        // Whisper curve over the whisper range, dashed along its length so steep parts stay dashed too
        double whisperRolloff = AudioDistancePlugin.effectiveRolloff(true);
        int whisperEnd = Math.max(1, (int) Math.round(AudioDistancePlugin.LINK.whisperShare() * pw));
        prevY = -1;
        int run = 0;
        for (int i = 0; i <= whisperEnd; i++) {
            double g = AudioPhysics.calculateGain((double) i / whisperEnd, model, whisperRolloff, floor, ref);
            int y = py2 - (int) Math.round(g * ph);
            int x = px1 + i;
            int from = prevY < 0 ? y : prevY;
            int dir = y >= from ? 1 : -1;
            for (int yy = from; ; yy += dir) {
                if ((run++ / 4) % 2 == 0) {
                    c.fill(x, yy, x + 1, yy + 1, Palette.WHISPER);
                }
                if (yy == y) {
                    break;
                }
            }
            prevY = y;
        }
        // Where the whisper range ends, on the distance scale
        c.fill(px1 + whisperEnd, py2 + 1, px1 + whisperEnd + 1, py2 + 4, Palette.WHISPER);

        // Walk-away preview: where the voice is now
        if (previewStep >= 0) {
            double f = PREVIEW_STEPS[previewStep];
            double g = AudioPhysics.calculateGain(f, model, rolloff, floor, ref);
            int sx = px1 + (int) Math.round(f * pw);
            int sy = py2 - (int) Math.round(g * ph);
            c.vLine(sx, py1, py2, Palette.withAlpha(Palette.ACCENT, 0xA0));
            c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
            c.fill(sx - 1, sy - 1, sx + 2, sy + 2, Palette.ACCENT_LINE);
            badge(c, tr("listen.at", blocks(f * maxDist), pct(g)), sx, sy - 17 >= y1 + 2 ? sy - 17 : sy + 5);
        }

        if (floor > 0.0) {
            int fy = py2 - (int) Math.round(floor * ph);
            c.dashedHLine(px1, px2, fy, 3, Palette.withAlpha(Palette.FLOOR, 0xB0));
            Component label = tr("floor_label", pct(floor));
            c.text(label, px2 - c.width(label) - 2, Math.max(py1, fy - 10), Palette.FLOOR);
        }

        // Axis in blocks
        for (int i = 0; i <= 4; i++) {
            int x = px1 + pw * i / 4;
            int ly = py2 + 3;
            if (i == 0) {
                c.text(Component.literal("0"), x, ly, Palette.TEXT_MUTED);
            } else if (i == 4) {
                c.right(tr("blocks", blocks(maxDist)), x + 1, ly, Palette.TEXT_MUTED);
            } else {
                c.centered(Component.literal(blocks(maxDist * i / 4.0)), x, ly, Palette.TEXT_MUTED);
            }
        }

        // People you hear right now, as dots on the curve
        SpeakerRegistry.Speaker hovered = null;
        int hoverX = 0;
        int hoverY = 0;
        List<SpeakerRegistry.Speaker> dots = AudioDistancePlugin.LINK.isMonitorAllowed()
                ? AudioDistancePlugin.SPEAKERS.active(System.nanoTime()) : List.of();
        for (SpeakerRegistry.Speaker s : dots) {
            if (s.getDistance() < 0.0) {
                continue;
            }
            double f = Math.min(1.0, s.getDistance() / maxDist);
            double g = AudioDistancePlugin.curveGain(s.getDistance(), s.getMaxDistance(), s.isWhispering());
            int sx = px1 + (int) Math.round(f * pw);
            int sy = py2 - (int) Math.round(g * ph);
            int color = s.isWhispering() ? Palette.WHISPER
                    : (s.getFilter().getDisplayLossDb() > 1.0F ? Palette.MUFFLED : Palette.TEXT);
            c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
            c.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                hovered = s;
                hoverX = sx;
                hoverY = sy;
            }
        }

        if (hovered != null) {
            double g = AudioDistancePlugin.curveGain(hovered.getDistance(), hovered.getMaxDistance(), hovered.isWhispering());
            badge(c, tr("inspect.speaker", speakerName(hovered), blocks(hovered.getDistance()), pct(g)),
                    hoverX, hoverY - 17 >= y1 + 2 ? hoverY - 17 : hoverY + 5);
        } else if (mouseX >= px1 && mouseX <= px2 && mouseY >= py1 && mouseY <= py2) {
            double f = (double) (mouseX - px1) / pw;
            double g = AudioPhysics.calculateGain(f, model, rolloff, floor, ref);
            int gy = py2 - (int) Math.round(g * ph);
            c.vLine(mouseX, py1, py2, 0x66FFFFFF);
            c.fill(mouseX - 1, gy - 1, mouseX + 2, gy + 2, 0xFFFFFFFF);
            int badgeY = gy - 17 >= y1 + 2 ? gy - 17 : Math.min(py2 - 12, gy + 5);
            badge(c, tr("inspect", blocks(f * maxDist), pct(g)), mouseX, badgeY);
        }
    }

    // ---- Walls --------------------------------------------------------------

    /** A warning above a panel when Sound Physics Remastered does the job, or this build cannot. */
    private void paintStatusBanner(Canvas c, String soundPhysicsKey) {
        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        if (status != AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS && status != AudioDistancePlugin.OcclusionStatus.UNAVAILABLE) {
            return;
        }
        String key = status == AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS ? soundPhysicsKey : "status.unavailable";
        int y = statusY;
        c.frame(left, y, right, y + 26, 0x30F6C453, Palette.withAlpha(Palette.WARN, 0x90));
        c.text(fit(c, tr(key), right - left - 12), left + 6, y + 4, Palette.WARN);
        c.text(fit(c, tr(key + ".detail"), right - left - 12), left + 6, y + 14, Palette.TEXT_DIM);
    }

    private void paintWalls(Canvas c) {
        paintStatusBanner(c, "status.sound_physics");
        DistanceConfig shown = shown();
        boolean on = shown.isOcclusionEnabled();
        int y = panelTop;
        c.frame(left, y, right, panelBottom, Palette.PANEL, Palette.PANEL_BORDER);
        Component header = on ? tr("walls.preview") : tr("walls.preview_off");
        c.text(fit(c, header, right - left - 12), left + 6, y + 5, on ? Palette.TEXT_DIM : Palette.TEXT_MUTED);

        int labelW = 0;
        int levelW = 0;
        for (Example e : EXAMPLES) {
            labelW = Math.max(labelW, c.width(tr("walls.example." + e.key)));
        }
        for (String level : WALL_LEVELS) {
            levelW = Math.max(levelW, c.width(tr(level)));
        }
        labelW = Math.min(labelW, (right - left) * 2 / 5);
        levelW = Math.min(levelW + 4, (right - left) / 4);
        int barX1 = left + 8 + labelW + 8;
        int barX2 = right - 8 - levelW;
        if (barX2 - barX1 < 30) {
            barX2 = right - 8;
            levelW = 0;
        }

        double strength = shown.getOcclusionStrength();
        int rowY = y + 20;
        for (Example e : EXAMPLES) {
            double thickness = e.blocks * shown.getMaterialWeight(e.material);
            double muffle = OcclusionModel.muffle(thickness, strength);
            double loss = OcclusionModel.lossDb(thickness, strength);
            double gain = OcclusionModel.dbToGain(-loss);
            int alpha = on ? 0xFF : 0x70;

            c.text(fit(c, tr("walls.example." + e.key), labelW), left + 8, rowY, Palette.withAlpha(Palette.TEXT, alpha));
            c.fill(barX1, rowY + 1, barX2, rowY + 8, 0x22FFFFFF);
            int filled = barX1 + (int) Math.round(gain * (barX2 - barX1));
            c.fill(barX1, rowY + 1, filled, rowY + 8, Palette.withAlpha(Palette.mix(Palette.ACCENT, Palette.MUFFLED, muffle), alpha));
            if (levelW > 0) {
                c.right(fit(c, tr(wallLevel(loss)), levelW - 4), right - 8, rowY, Palette.withAlpha(Palette.TEXT_DIM, alpha));
            }
            rowY += 14;
        }
        c.text(fit(c, tr("walls.hint"), right - left - 16), left + 8, panelBottom - 14, Palette.TEXT_MUTED);

        if (materialsHintY >= 0) {
            c.text(fit(c, tr("materials.hint_other"), right - left), left + 1, materialsHintY, Palette.TEXT_MUTED);
        }
    }

    /** How a voice sounds behind a wall that takes {@code lossDb} away, in words. */
    private static final String[] WALL_LEVELS = {"walls.level.clear", "walls.level.slight", "walls.level.muffled",
            "walls.level.strong", "walls.level.faint"};

    private static String wallLevel(double lossDb) {
        if (lossDb < 1.5) {
            return WALL_LEVELS[0];
        }
        if (lossDb < 5.0) {
            return WALL_LEVELS[1];
        }
        if (lossDb < 10.0) {
            return WALL_LEVELS[2];
        }
        return lossDb < 18.0 ? WALL_LEVELS[3] : WALL_LEVELS[4];
    }

    // ---- Effects ------------------------------------------------------------

    /** What the surroundings do to voices right now. */
    private void paintEffects(Canvas c) {
        paintStatusBanner(c, "effects.sound_physics");
        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        int y = panelTop;
        int bottom = panelBottom;
        c.frame(left, y, right, bottom, Palette.PANEL, Palette.PANEL_BORDER);
        int x = left + 8;
        int w = right - left - 16;
        if (!inWorld()) {
            c.centered(tr("effects.no_world"), (left + right) / 2, (y + bottom) / 2 - 4, Palette.TEXT_DIM);
            return;
        }
        DistanceConfig shown = shown();
        ListenerEnvironment env = AudioDistancePlugin.ENVIRONMENT;
        c.text(tr("effects.now"), x, y + 5, Palette.TEXT_DIM);
        int rowY = y + 20;

        // Echo: what kind of place you are in and what it does to voices
        RoomEstimate room = env.room();
        boolean reverbOn = shown.isReverbEnabled() && status != AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS;
        double level = (room.wet() > 0.0 ? room.wet() : room.echoes().loudest()) * shown.getReverbStrength();
        c.text(fit(c, Component.translatable(room.kind().getTranslationKey()), w), x, rowY,
                reverbOn && room.isAudible() ? Palette.TEXT : Palette.TEXT_MUTED);
        rowY += 12;
        Component detail = null;
        if (!room.echoes().isEmpty()) {
            detail = tr("effects.room.repeat", String.format(Locale.ROOT, "%.2f", room.echoes().delays()[0]));
        } else if (room.wet() > 0.0) {
            detail = tr("effects.room.detail", String.format(Locale.ROOT, "%.1f", room.decaySeconds()), blocks(room.meanFree()));
        }
        if (detail != null) {
            c.text(fit(c, detail, w), x, rowY, Palette.TEXT_DIM);
            rowY += 12;
        }
        int barRight = right - 8 - c.width(Component.literal("100%")) - 4;
        c.fill(x, rowY + 1, barRight, rowY + 7, 0x22FFFFFF);
        c.fill(x, rowY + 1, x + (int) Math.round(Math.min(1.0, reverbOn ? level : 0.0) * (barRight - x)), rowY + 7,
                Palette.withAlpha(Palette.ACCENT, reverbOn ? 0xFF : 0x70));
        c.right(Component.literal(pct(reverbOn ? level : 0.0)), right - 8, rowY, Palette.TEXT_DIM);
        rowY += 16;

        // Water
        boolean waterOn = shown.isUnderwaterEnabled() && status != AudioDistancePlugin.OcclusionStatus.SOUND_PHYSICS;
        c.text(fit(c, tr(env.isUnderWater() ? "effects.water.under" : "effects.water.dry"), w), x, rowY,
                env.isUnderWater() && waterOn ? Palette.ACCENT_LINE : Palette.TEXT_MUTED);
        rowY += 14;

        // Weather
        EnvironmentEffects.Weather weather = env.weather();
        String weatherKey = "effects.weather." + weather.name().toLowerCase(Locale.ROOT);
        c.text(fit(c, tr(weatherKey), w), x, rowY,
                weather != EnvironmentEffects.Weather.CLEAR && shown.isWeatherEnabled() ? Palette.WARN : Palette.TEXT_MUTED);
        rowY += 14;

        // Corners: voices that come round a wall right now
        int round = 0;
        for (SpeakerRegistry.Speaker sp : AudioDistancePlugin.SPEAKERS.active(System.nanoTime())) {
            if (sp.isHeardRound()) {
                round++;
            }
        }
        boolean cornersOn = shown.isDiffractionEnabled() && status == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        c.text(fit(c, round > 0 ? tr("effects.corners.now", round) : tr("effects.corners.none"), w), x, rowY,
                round > 0 && cornersOn ? Palette.ACCENT_LINE : Palette.TEXT_MUTED);
        rowY += 14;

        if (rowY + 22 <= bottom - 4) {
            c.text(fit(c, tr("effects.hint"), w), x, bottom - 14, Palette.TEXT_MUTED);
        }
    }

    // ---- HUD ----------------------------------------------------------------

    private void paintHud(Canvas c) {
        c.frame(left, panelTop, right, panelBottom, Palette.PANEL, Palette.PANEL_BORDER);
        if (config.getHudMode() == HudMode.OFF) {
            c.centered(fit(c, tr("hud.preview.off"), right - left - 12), (left + right) / 2,
                    (panelTop + panelBottom) / 2 - 4, Palette.TEXT_MUTED);
            return;
        }
        c.centered(fit(c, tr("hud.preview"), right - left - 12), (left + right) / 2,
                (panelTop + panelBottom) / 2 - 4, Palette.withAlpha(Palette.TEXT_MUTED, 0x80));
        HudOverlay.paintPreview(c, left + 1, panelTop + 1, right - 1, panelBottom - 1);
    }

    // ---- Monitor ------------------------------------------------------------

    private void paintMonitor(Canvas c, int mouseX, int mouseY) {
        int top = panelTop;
        int bottom = panelBottom;
        c.frame(left, top, right, bottom, Palette.PANEL, Palette.PANEL_BORDER);
        int midX = (left + right) / 2;
        if (!inWorld()) {
            c.centered(tr("monitor.no_world"), midX, (top + bottom) / 2 - 4, Palette.TEXT_DIM);
            return;
        }

        AudioDistancePlugin.OcclusionStatus status = AudioDistancePlugin.occlusionStatus();
        boolean wallsActive = status == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        double maxDist = AudioDistancePlugin.getServerMaxDistance();
        int y = top + 6;
        c.text(tr("monitor.walls", tr("monitor.status." + status.name().toLowerCase(Locale.ROOT))), left + 6, y,
                wallsActive ? Palette.GOOD : Palette.TEXT_MUTED);
        c.right(tr("monitor.range", blocks(maxDist)), right - 6, y, Palette.TEXT_MUTED);
        LinkProtocol.ServerProfile serverProfile = AudioDistancePlugin.LINK.profile();
        Component serverLine = serverProfile == null
                ? tr("monitor.server.none")
                : tr("monitor.server.addon", tr("monitor.server.mode." + serverProfile.mode().getId()));
        y += 11;
        // The addon spaces its work out when it gets busy; say so only then
        int busyW = 0;
        if (AudioDistancePlugin.CLIENT_PERF.isBusy()) {
            Component busy = tr("monitor.busy");
            busyW = c.width(busy) + 12;
            c.right(busy, right - 6, y, Palette.WARN);
        }
        c.text(fit(c, serverLine, right - left - 12 - busyW), left + 6, y, Palette.TEXT_MUTED);

        if (!AudioDistancePlugin.LINK.isMonitorAllowed()) {
            c.centered(tr("monitor.off_by_server"), midX, (y + bottom) / 2, Palette.TEXT_DIM);
            return;
        }
        long now = System.nanoTime();
        List<NearbyPlayers.Row> rows = NearbyPlayers.rows(AudioDistancePlugin.SPEAKERS.active(now),
                AudioDistancePlugin.NEARBY.players(), id -> AudioDistancePlugin.voiceState(id, now));
        // Without any source of voice chat states only who is talking is known
        int footer = AudioDistancePlugin.hasVoiceStates(now) ? 0 : 12;
        if (footer > 0) {
            c.text(fit(c, tr("monitor.states_unknown"), right - left - 12), left + 6, bottom - 13, Palette.TEXT_MUTED);
        }
        if (radarView) {
            paintRadar(c, rows, y + 14, bottom - 4 - footer, maxDist, wallsActive, mouseX, mouseY);
            return;
        }
        if (rows.isEmpty()) {
            int cy = (y + bottom) / 2 - 4;
            c.centered(tr("monitor.empty"), midX, cy, Palette.TEXT_DIM);
            c.centered(fit(c, tr("monitor.empty_hint"), right - left - 12), midX, cy + 12, Palette.TEXT_MUTED);
            return;
        }

        int wallsRight = right - 8;
        int wallsW = c.width(tr("monitor.col.walls"));
        for (String level : WALL_LEVELS) {
            wallsW = Math.max(wallsW, c.width(tr(level)));
        }
        wallsW = Math.min(wallsW, (right - left) / 5);
        int loudRight = wallsRight - wallsW - 10;
        int loudW = Math.max(60, Math.min(120, (right - left) / 4));
        int loudLeft = loudRight - loudW;
        int distRight = loudLeft - 10;
        int arrowW = c.width(Component.literal("↗")) + 3;
        int distW = Math.max(c.width(tr("monitor.col.distance")), c.width(tr("blocks", "000")) + arrowW);
        int nameLeft = left + 16;
        int nameW = distRight - distW - 10 - nameLeft;

        int hy = y + 16;
        c.text(tr("monitor.col.name"), nameLeft, hy, Palette.TEXT_MUTED);
        c.right(tr("monitor.col.distance"), distRight, hy, Palette.TEXT_MUTED);
        c.text(tr("monitor.col.loudness"), loudLeft, hy, Palette.TEXT_MUTED);
        c.right(tr("monitor.col.walls"), wallsRight, hy, Palette.TEXT_MUTED);
        c.hLine(left + 6, right - 6, hy + 11, Palette.PANEL_BORDER);

        int rowY = hy + 16;
        int shownRows = 0;
        for (NearbyPlayers.Row row : rows) {
            if (rowY + 10 > bottom - 4 - footer) {
                c.text(tr("monitor.more", rows.size() - shownRows), nameLeft, rowY - 2, Palette.TEXT_MUTED);
                break;
            }
            // Distance and, after it, an arrow towards the player
            String arrow = Bearing.arrow(row.bearing());
            if (!arrow.isEmpty()) {
                c.right(Component.literal(arrow), distRight, rowY, Palette.TEXT_DIM);
            }
            int numRight = distRight - arrowW;
            if (row.isTalking()) {
                paintTalkingRow(c, row, rowY, wallsActive, nameLeft, nameW, numRight, loudLeft, loudRight, wallsRight, wallsW);
            } else {
                paintSilentRow(c, row, rowY, nameLeft, nameW, numRight, loudLeft, wallsRight);
            }
            rowY += 13;
            shownRows++;
        }
    }

    /** Top-down view: you in the middle looking up, the voice and whisper ranges as rings. */
    private void paintRadar(Canvas c, List<NearbyPlayers.Row> rows, int y1, int y2, double maxDist,
                            boolean wallsActive, int mouseX, int mouseY) {
        int size = Math.min(right - left - 16, y2 - y1 - 4);
        if (size < 40) {
            return;
        }
        int radius = size / 2 - 2;
        int cx = (left + right) / 2;
        int cy = y1 + size / 2 + 2;

        ring(c, cx, cy, radius, Palette.withAlpha(Palette.ACCENT, 0x90));
        ring(c, cx, cy, (int) Math.round(radius * AudioDistancePlugin.LINK.whisperShare()), Palette.withAlpha(Palette.WHISPER, 0x70));
        c.hLine(cx - radius, cx + radius + 1, cy, Palette.GRID);
        c.vLine(cx, cy - radius, cy + radius + 1, Palette.GRID);
        c.right(tr("blocks", blocks(maxDist)), cx + radius, cy - radius, Palette.TEXT_MUTED);
        // You, facing up
        c.fill(cx - 2, cy - 2, cx + 3, cy + 3, Palette.TEXT);
        c.centered(Component.literal("↑"), cx, cy - 12, Palette.TEXT_DIM);

        NearbyPlayers.Row hovered = null;
        int hoverX = 0;
        int hoverY = 0;
        for (NearbyPlayers.Row row : rows) {
            if (row.distance() < 0.0 || Double.isNaN(row.bearing())) {
                continue;
            }
            double r = Math.min(1.0, row.distance() / maxDist) * radius;
            double a = Math.toRadians(row.bearing());
            int px = cx + (int) Math.round(Math.sin(a) * r);
            int py = cy - (int) Math.round(Math.cos(a) * r);
            int color;
            if (row.isTalking()) {
                // Shape as well as color: square talking, cross whispering, diamond behind a wall
                SpeakerRegistry.Speaker s = row.speaker();
                boolean muffled = wallsActive && s.getFilter().getDisplayLossDb() > 1.0F;
                color = s.isWhispering() ? Palette.WHISPER : (muffled ? Palette.MUFFLED : Palette.GOOD);
                if (s.isWhispering()) {
                    c.fill(px - 4, py - 1, px + 5, py + 2, 0xFF000000);
                    c.fill(px - 1, py - 4, px + 2, py + 5, 0xFF000000);
                    c.fill(px - 3, py, px + 4, py + 1, color);
                    c.fill(px, py - 3, px + 1, py + 4, color);
                } else if (muffled) {
                    for (int d = -3; d <= 3; d++) {
                        int half = 3 - Math.abs(d);
                        c.fill(px - half - 1, py + d, px + half + 2, py + d + 1, 0xFF000000);
                    }
                    for (int d = -2; d <= 2; d++) {
                        int half = 2 - Math.abs(d);
                        c.fill(px - half, py + d, px + half + 1, py + d + 1, color);
                    }
                } else {
                    c.fill(px - 3, py - 3, px + 4, py + 4, 0xFF000000);
                    c.fill(px - 2, py - 2, px + 3, py + 3, color);
                }
            } else {
                color = stateColor(row.state());
                c.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
                c.frame(px - 2, py - 2, px + 3, py + 3, 0x00000000, color);
            }
            Component name = row.isTalking() ? speakerName(row.speaker())
                    : Component.literal(row.name() != null ? row.name() : "?");
            c.text(fit(c, name, 70), px + 5, py - 4, row.isTalking() ? Palette.TEXT : Palette.TEXT_MUTED);
            if (Math.abs(mouseX - px) <= 4 && Math.abs(mouseY - py) <= 4) {
                hovered = row;
                hoverX = px;
                hoverY = py;
            }
        }
        if (hovered != null) {
            Component state = hovered.isTalking()
                    ? tr(hovered.speaker().isWhispering() ? "monitor.whisper" : "monitor.talking")
                    : tr("monitor.state." + (hovered.state() == null ? "silent" : hovered.state().getTranslationKey()));
            Component name = hovered.isTalking() ? speakerName(hovered.speaker())
                    : Component.literal(hovered.name() != null ? hovered.name() : "?");
            badge(c, tr("monitor.radar.badge", name, blocks(hovered.distance()), state), hoverX, hoverY - 17);
        }
        if (rows.isEmpty()) {
            c.centered(tr("monitor.empty"), cx, cy + radius / 2, Palette.TEXT_DIM);
        }
        paintRadarLegend(c, left + 8, cx - radius - 8, y2);
    }

    /** What the marks mean, in the free space left of the radar (skipped when there is none). */
    private void paintRadarLegend(Canvas c, int x, int maxX, int bottom) {
        int w = maxX - x;
        if (w < 50) {
            return;
        }
        Component[] labels = {tr("monitor.talking"), tr("monitor.whisper"), tr("hud.walls"),
                tr("monitor.state.silent"), tr("monitor.legend.voice_ring"), tr("monitor.legend.whisper_ring")};
        int[] colors = {Palette.GOOD, Palette.WHISPER, Palette.MUFFLED, Palette.TEXT_MUTED,
                Palette.ACCENT, Palette.WHISPER};
        int y = bottom - labels.length * 11;
        for (int i = 0; i < labels.length; i++) {
            // The same shapes as on the radar
            int mx = x + 2;
            int my = y + 4;
            if (i == 1) {
                c.fill(mx - 2, my, mx + 3, my + 1, colors[i]);
                c.fill(mx, my - 2, mx + 1, my + 3, colors[i]);
            } else if (i == 2) {
                for (int d = -2; d <= 2; d++) {
                    int half = 2 - Math.abs(d);
                    c.fill(mx - half, my + d, mx + half + 1, my + d + 1, colors[i]);
                }
            } else if (i == 3) {
                c.frame(x, y + 2, x + 5, y + 7, 0x00000000, colors[i]);
            } else if (i >= 4) {
                c.hLine(x, x + 6, y + 4, Palette.withAlpha(colors[i], 0xC0));
            } else {
                c.fill(x, y + 2, x + 5, y + 7, colors[i]);
            }
            c.text(fit(c, labels[i], w - 9), x + 9, y, Palette.TEXT_DIM);
            y += 11;
        }
    }

    private static void ring(Canvas c, int cx, int cy, int radius, int argb) {
        if (radius < 2) {
            return;
        }
        int steps = Math.max(24, (int) (radius * 6.3));
        for (int i = 0; i < steps; i++) {
            double a = 2.0 * Math.PI * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            c.fill(x, y, x + 1, y + 1, argb);
        }
    }

    private void paintTalkingRow(Canvas c, NearbyPlayers.Row row, int rowY, boolean wallsActive, int nameLeft, int nameW,
                                 int distRight, int loudLeft, int loudRight, int wallsRight, int wallsW) {
        SpeakerRegistry.Speaker s = row.speaker();
        boolean talking = s.getLevelDb() > -50.0F;
        c.fill(left + 7, rowY + 2, left + 11, rowY + 6, talking ? Palette.GOOD : Palette.withAlpha(Palette.TEXT_MUTED, 0x80));

        // A tag after the name: whispering, or a state that means they will not hear you back
        Component tag = null;
        int tagColor = Palette.WHISPER;
        if (s.isWhispering()) {
            tag = tr("monitor.whisper");
        } else if (row.state() != null && row.state().isProblem()) {
            tag = tr("monitor.state." + row.state().getTranslationKey());
            tagColor = Palette.WARN;
        }
        Component name = speakerName(s);
        if (tag != null) {
            int tagW = c.width(tag) + 4;
            Component fitted = fit(c, name, nameW - tagW);
            c.text(fitted, nameLeft, rowY, Palette.TEXT);
            c.text(tag, nameLeft + c.width(fitted) + 4, rowY, tagColor);
        } else {
            c.text(fit(c, name, nameW), nameLeft, rowY, Palette.TEXT);
        }

        c.right(s.getDistance() >= 0.0 ? tr("blocks", blocks(s.getDistance())) : Component.literal("—"),
                distRight, rowY, Palette.TEXT_DIM);

        float lossDb = wallsActive ? s.getFilter().getDisplayLossDb() : 0.0F;
        // Round a wall the voice is as loud as the longer way round
        SoundBlend blend = wallsActive ? s.getBlend() : null;
        double heardAt = blend != null ? s.getDistance() + blend.extraDistance() : s.getDistance();
        double gain = s.getDistance() >= 0.0
                ? AudioDistancePlugin.curveGain(heardAt, s.getMaxDistance(), s.isWhispering()) * OcclusionModel.dbToGain(-lossDb)
                : 0.0;
        Component pctText = Component.literal(pct(gain));
        int barRight = loudRight - c.width(Component.literal("100%")) - 4;
        c.fill(loudLeft, rowY + 1, barRight, rowY + 7, 0x22FFFFFF);
        int fill = loudLeft + (int) Math.round(Math.min(1.0, gain) * (barRight - loudLeft));
        double muffle = wallsActive ? s.getFilter().getDisplayMuffle() : 0.0;
        c.fill(loudLeft, rowY + 1, fill, rowY + 7, Palette.mix(Palette.ACCENT, Palette.MUFFLED, muffle));
        c.right(pctText, loudRight, rowY, Palette.TEXT_DIM);

        if (lossDb >= 1.5F) {
            c.right(fit(c, tr(wallLevel(lossDb)), wallsW), wallsRight, rowY, Palette.MUFFLED);
        } else {
            c.right(Component.literal("—"), wallsRight, rowY, Palette.TEXT_MUTED);
        }
    }

    /** A nearby player who is not talking: their voice chat state takes the loudness and walls columns. */
    private void paintSilentRow(Canvas c, NearbyPlayers.Row row, int rowY, int nameLeft, int nameW,
                                int distRight, int loudLeft, int wallsRight) {
        VoiceState state = row.state();
        int color = stateColor(state);
        // Hollow marker: in range, not talking
        c.frame(left + 7, rowY + 2, left + 11, rowY + 6, 0x00000000, Palette.withAlpha(color, 0xC0));
        String name = row.name();
        c.text(fit(c, Component.literal(name != null ? name : row.playerId().toString().substring(0, 8)), nameW),
                nameLeft, rowY, Palette.TEXT_DIM);
        c.right(tr("blocks", blocks(row.distance())), distRight, rowY, Palette.TEXT_DIM);
        Component text = tr("monitor.state." + (state == null ? "silent" : state.getTranslationKey()));
        c.text(fit(c, text, wallsRight - loudLeft), loudLeft, rowY, color);
    }

    private static int stateColor(VoiceState state) {
        if (state == null || state == VoiceState.CONNECTED) {
            return Palette.TEXT_MUTED;
        }
        return switch (state) {
            case GROUP -> Palette.WHISPER;
            case NO_VOICE_CHAT -> Palette.BAD;
            default -> Palette.WARN;
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void badge(Canvas c, Component text, int centerX, int y) {
        int w = c.width(text) + 8;
        int x = Math.max(left + 2, Math.min(right - 2 - w, centerX - w / 2));
        c.frame(x, y, x + w, y + 12, Palette.BADGE, Palette.BADGE_BORDER);
        c.text(text, x + 4, y + 2, Palette.TEXT);
    }

    private Component modelLabel() {
        return tr("model.label", Component.translatable(shown().getModel().getTranslationKey()));
    }

    private Component wallsLabel() {
        return tr("walls.toggle", tr(shown().isOcclusionEnabled() ? "on" : "off"));
    }

    private static Component speakerName(SpeakerRegistry.Speaker s) {
        String name = s.getDisplayName();
        if (name != null && !name.isEmpty()) {
            return Component.literal(name);
        }
        if (s.getKind() == SpeakerRegistry.Kind.LOCATIONAL) {
            return tr("monitor.source");
        }
        String id = s.getEntityId() != null ? s.getEntityId().toString() : s.getChannelId().toString();
        return Component.literal(id.substring(0, 8));
    }

    /** Shortens a text with an ellipsis so it fits into {@code maxWidth} pixels. */
    private static Component fit(Canvas c, Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (c.width(text) <= maxWidth) {
            return text;
        }
        String s = text.getString();
        for (int len = s.length() - 1; len > 0; len--) {
            Component candidate = Component.literal(s.substring(0, len).trim() + "…");
            if (c.width(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return Component.literal("…");
    }

    private static <T extends AbstractWidget> T withTip(T widget, String key) {
        widget.setTooltip(tip(key));
        return widget;
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(tr(key));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    private static String pct(double v) {
        return Math.round(v * 100.0) + "%";
    }

    private static String blocks(double v) {
        return v < 10.0 && Math.abs(v - Math.rint(v)) > 0.05
                ? String.format(Locale.ROOT, "%.1f", v)
                : String.valueOf(Math.round(v));
    }
}
