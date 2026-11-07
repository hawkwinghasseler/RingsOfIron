package com.ringsofiron.uihud.variants.nasapunk;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * The skin the game's menus are built with under the Nasa-punk HUDs: the classic skin's style names
 * ({@code ShellLook#skin}) drawn as CDU-1's pages (documentation/ui-overhaul/variations/
 * nasapunk-shell). The menus keep their rows, buttons and keys; here each takes the computer's look.
 *
 * <ul>
 *   <li>The {@code "panel"} behind each menu is the unit itself.</li>
 *   <li>Text is B612 in capitals, each style at the mockups' size and spacing, baked into the font so
 *       Scene2D measures what it draws: the page title 15, a prompt's data line 15, body text 13, a
 *       label line 12, the game's name 30 as engraved on the console.</li>
 *   <li>A button is a prompt: a {@code <} caret, its word in white, the cyan cursor while the keyboard
 *       or the pad is on it, the computer's reverse video while it is pressed. One that can't be
 *       pressed is dim with no caret: not offered. A button in a list is a field: no caret.</li>
 *   <li>A choice of two (a sponsor) is an option select: the live option cyan, the other dim.</li>
 *   <li>Typing shows in cyan, as typed; a drop-down's live value is cyan with its caret; a check box
 *       is a box, filled green when on; a slider is a bar on its track with a fader's cap.</li>
 * </ul>
 */
final class NpShellSkin implements Disposable {
    /** The style the look gives a button in a list (a key binding, a save to write over): a field, no caret. */
    static final String FIELD = "np-field";
    /** The style the look gives the main menu's subtitle: engraved on the console under the game's name. */
    static final String SUBTITLE = "np-subtitle";

    /** A font of the skin's, with what it is made from, so it can be made again at another scale. */
    private static final class Face {
        final String file;
        final float size, track;
        final boolean caps;
        final BitmapFont font;

        Face(String file, float size, float track, boolean caps, float scale) {
            this.file = file;
            this.size = size;
            this.track = track;
            this.caps = caps;
            this.font = NpFonts.create(file, size, scale, track, caps);
        }
    }

    private final NpKit kit;
    private final Skin skin = new Skin();
    private final Array<Face> faces = new Array<>();
    private float scale;
    /** What the unit's plate reads: CDU-1, then the build once the main menu has given it. */
    private String plate = "CDU-1";

    /** Needs GL: the fonts are rasterised, at {@code uiScale}. */
    NpShellSkin(NpKit kit, float uiScale) {
        this.kit = kit;
        this.scale = uiScale > 0f ? uiScale : 1f;
        NpCanvas cv = kit.canvas();
        BitmapFont name = face(NpFonts.BOLD, 30f, 7f, true);
        BitmapFont subtitle = face(NpFonts.BOLD, 10.5f, 3.2f, true);
        BitmapFont title = face(NpFonts.BOLD, NpCdu.TITLE, NpCdu.TITLE_TRACK, true);
        BitmapFont data = face(NpFonts.BOLD, NpCdu.DATA, NpCdu.DATA_TRACK, true);
        BitmapFont cell = face(NpFonts.BOLD, NpCdu.CELL, NpCdu.CELL_TRACK, true);
        BitmapFont label = face(NpFonts.BOLD, NpCdu.LABEL, NpCdu.LABEL_TRACK, true);
        // Typing shows as typed: a save's name keeps its case
        BitmapFont typed = face(NpFonts.REGULAR, NpCdu.DATA, 0f, false);

        // --- Labels -----------------------------------------------------------------------------
        skin.add("default-font", cell, BitmapFont.class);
        label("display", name, NpColors.STENCIL);
        label(SUBTITLE, subtitle, NpCdu.SUBTITLE);
        label("heading", title, NpColors.WHITE);
        // A part of a page: the look draws its divider's rules either side of the word
        label("title", label, NpColors.DIM);
        label("default", cell, NpColors.WHITE);
        label("dim", label, NpColors.DIM);
        // The scratchpad's message: "a message in its colour (a clash of keys is amber)"
        label("warn", label, NpColors.AMBER);
        label("alert", label, NpColors.RED);
        label("prompt", cell, NpColors.WHITE);
        label("banner", title, NpColors.AMBER);

        // --- The unit -----------------------------------------------------------------------------
        skin.add("panel", unit(), Drawable.class);
        skin.add("np-console", new NpDrawable(cv, (c, w, h, f) -> NpCdu.console(c, w, h)), Drawable.class);

        // --- Prompts and fields -----------------------------------------------------------------------
        float caret = cv.width("<", NpCdu.DATA, true, NpCdu.DATA_TRACK);
        skin.add("menu", prompt(data, true, caret));
        skin.add("default", prompt(data, true, caret));
        skin.add(FIELD, prompt(cell, false, 0f));
        skin.add("toggle", option(data));

        // --- Typing --------------------------------------------------------------------------------
        TextField.TextFieldStyle field = new TextField.TextFieldStyle();
        field.font = typed;
        field.fontColor = NpColors.CYAN;
        field.messageFont = label;
        field.messageFontColor = NpColors.DIM;
        field.background = entry(false).pad(6f, 4f, 6f, 4f).min(40f, 24f);
        field.focusedBackground = entry(true).pad(6f, 4f, 6f, 4f).min(40f, 24f);
        field.cursor = new NpDrawable(cv, (c, w, h, f) -> c.fill(0f, 0f, w, h, NpColors.CYAN)).min(2f, 0f);
        field.selection = new NpDrawable(cv, (c, w, h, f) -> c.fill(0f, 0f, w, h, NpColors.CYAN, 0.35f));
        skin.add("default", field);

        // --- Lists and scrolling ----------------------------------------------------------------------
        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScroll = bar(true, NpColors.TRACK).min(6f, 20f);
        scroll.vScrollKnob = bar(true, NpColors.DIM).min(6f, 20f);
        scroll.hScroll = bar(false, NpColors.TRACK).min(20f, 6f);
        scroll.hScrollKnob = bar(false, NpColors.DIM).min(20f, 6f);
        skin.add("default", scroll);

        List.ListStyle list = new List.ListStyle(cell, NpColors.CYAN, NpColors.WHITE,
                new NpDrawable(cv, (c, w, h, f) -> NpCdu.cursor(c, 0f, 0f, w, h)).pad(8f, 3f, 8f, 3f));
        list.over = new NpDrawable(cv, (c, w, h, f) -> c.fill(0f, 0f, w, h, NpColors.WHITE, 0.05f));
        list.background = new NpDrawable(cv, (c, w, h, f) -> {
            c.fill(0f, 0f, w, h, NpColors.GLASS);
            c.stroke(0.5f, 0.5f, w - 1f, h - 1f, 1f, NpColors.KEY_EDGE);
        }).pad(2f, 3f, 2f, 3f);
        skin.add("default", list);

        // A drop-down's live value, the player's own setting, in cyan with the caret that opens it
        SelectBox.SelectBoxStyle select = new SelectBox.SelectBoxStyle(cell, NpColors.CYAN,
                dropdown(false, false).pad(6f, 5f, 20f, 5f).min(60f, 24f), scroll, list);
        select.backgroundOver = dropdown(true, false).pad(6f, 5f, 20f, 5f).min(60f, 24f);
        select.backgroundOpen = dropdown(false, true).pad(6f, 5f, 20f, 5f).min(60f, 24f);
        select.disabledFontColor = NpColors.DIM;
        skin.add("default", select);

        // --- Switches and bars -------------------------------------------------------------------------
        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle(box(false), box(true), label, NpColors.DIM);
        // The cursor on a check box: it tints only the box's own background, not its image
        check.up = new NpDrawable(cv, (c, w, h, f) -> {
            if (f) NpCdu.cursor(c, -4f, -2f, w + 8f, h + 4f);
        });
        skin.add("default", check);

        Slider.SliderStyle slider = new Slider.SliderStyle();
        slider.background = new NpDrawable(cv, (c, w, h, f) -> c.fill(0f, h / 2f - 2f, w, 4f, NpColors.TRACK)).min(40f, 18f);
        slider.knobBefore = new NpDrawable(cv, (c, w, h, f) -> c.fill(0f, 0f, w, h, NpColors.WHITE)).min(0f, 4f);
        slider.knob = new NpDrawable(cv, (c, w, h, f) -> {
            NpHardware.softKey(c, 0f, 0f, w, h, false);
            c.fill(w / 2f - 0.75f, 3f, 1.5f, h - 6f, f ? NpColors.CYAN : NpColors.WHITE);
            if (f) NpCdu.cursor(c, -3f, -3f, w + 6f, h + 6f);
        }).min(8f, 18f);
        skin.add("default-horizontal", slider);

        // --- The mod scripts question: a page of its own, over the game dimmed ------------------------
        Window.WindowStyle window = new Window.WindowStyle(title, NpColors.WHITE, unit());
        window.stageBackground = new NpDrawable(cv, (c, w, h, f) -> c.fill(0f, 0f, w, h, NpColors.INK, 0.6f));
        skin.add("default", window);
    }

    Skin skin() {
        return skin;
    }

    /**
     * Makes every font again at {@code uiScale}, into the same font objects the styles and labels
     * hold; true when anything changed, so the menus lay out again.
     */
    boolean rescale(float uiScale) {
        if (uiScale <= 0f || Math.abs(uiScale - scale) < 1e-3f) return false;
        scale = uiScale;
        for (Face f : faces) NpFonts.refit(f.font, NpFonts.create(f.file, f.size, scale, f.track, f.caps));
        return true;
    }

    private BitmapFont face(String file, float size, float track, boolean caps) {
        Face f = new Face(file, size, track, caps, scale);
        faces.add(f);
        return f.font;
    }

    private void label(String name, BitmapFont font, Color c) {
        skin.add(name, new Label.LabelStyle(font, c));
    }

    /** The unit as a drawable: its plate carries the build once the main menu has shown it. */
    private NpDrawable unit() {
        return new NpDrawable(kit.canvas(), (c, w, h, f) -> NpCdu.unit(c, 0f, 0f, w, h, plate))
                .pad(NpCdu.PAD_SIDE, NpCdu.PAD_TOP, NpCdu.PAD_SIDE, NpCdu.PAD_BOTTOM);
    }

    void setBuild(String build) {
        plate = build == null || build.isEmpty() ? "CDU-1" : "CDU-1   " + build;
    }

    String plate() {
        return plate;
    }

    /**
     * A prompt (with its caret) or a field (without): up, under the pointer, pressed, not offered.
     * Its word starts after the caret; {@code caret} is the caret's width.
     */
    private TextButton.TextButtonStyle prompt(BitmapFont font, boolean withCaret, float caret) {
        NpCanvas cv = kit.canvas();
        float left = 5f + (withCaret ? caret : 0f);
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = font;
        s.fontColor = NpColors.WHITE;
        s.disabledFontColor = NpColors.DIM;
        s.up = button(cv, withCaret, false, false, false).pad(left, 3f, 6f, 3f);
        s.over = button(cv, withCaret, true, false, false).pad(left, 3f, 6f, 3f);
        s.down = button(cv, withCaret, false, true, false).pad(left, 3f, 6f, 3f);
        s.disabled = button(cv, withCaret, false, false, true).pad(left, 3f, 6f, 3f);
        return s;
    }

    private static NpDrawable button(NpCanvas cv, boolean withCaret, boolean over, boolean down, boolean barred) {
        return new NpDrawable(cv, (c, w, h, f) -> {
            if (down) c.fill(0f, 0f, w, h, NpCdu.SHADE);
            else if (over && !barred) c.fill(0f, 0f, w, h, NpColors.WHITE, 0.05f);
            // A dim field is not offered: no caret
            if (withCaret && !barred) {
                c.text(5f, NpCdu.baseline(c, h, NpCdu.DATA), "<", NpCdu.DATA, true, NpColors.WHITE, NpCdu.DATA_TRACK, NpCanvas.START);
            }
            if (f) NpCdu.cursor(c, 0f, 0f, w, h);
        });
    }

    /** An option select's option: dim, or cyan while it is the live one. */
    private TextButton.TextButtonStyle option(BitmapFont font) {
        NpCanvas cv = kit.canvas();
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = font;
        s.fontColor = NpColors.DIM;
        s.overFontColor = NpColors.WHITE;
        s.checkedFontColor = NpColors.CYAN;
        s.checkedOverFontColor = NpColors.CYAN;
        s.disabledFontColor = NpColors.DIM;
        s.up = new NpDrawable(cv, (c, w, h, f) -> {
            if (f) NpCdu.cursor(c, 0f, 0f, w, h);
        }).pad(5f, 3f, 5f, 3f);
        s.over = new NpDrawable(cv, (c, w, h, f) -> {
            c.fill(0f, 0f, w, h, NpColors.WHITE, 0.05f);
            if (f) NpCdu.cursor(c, 0f, 0f, w, h);
        }).pad(5f, 3f, 5f, 3f);
        return s;
    }

    /** An entry field's ground: a shade over the glass with a line along its foot; the cursor round it while it takes the typing. */
    private NpDrawable entry(boolean typing) {
        return new NpDrawable(kit.canvas(), (c, w, h, f) -> {
            c.fill(0f, 0f, w, h, NpCdu.FIELD);
            c.fill(0f, h - 1f, w, 1f, NpColors.DIM);
            if (typing || f) NpCdu.cursor(c, 0f, 0f, w, h);
        });
    }

    /** A drop-down: an entry field with its caret at the right, cyan while it is open or the cursor is on it. */
    private NpDrawable dropdown(boolean over, boolean open) {
        return new NpDrawable(kit.canvas(), (c, w, h, f) -> {
            c.fill(0f, 0f, w, h, NpCdu.FIELD);
            if (over) c.fill(0f, 0f, w, h, NpColors.WHITE, 0.04f);
            c.fill(0f, h - 1f, w, 1f, NpColors.DIM);
            float cx = w - 10f, cy = h / 2f;
            c.fillPoly(new float[] {cx - 4f, cy - 2f, cx + 4f, cy - 2f, cx, cy + 3f}, open || f ? NpColors.CYAN : NpColors.DIM);
            if (open || f) NpCdu.cursor(c, 0f, 0f, w, h);
        });
    }

    /** A check box's box: hollow and dim when off, green and filled when on ("fill means engaged"). */
    private NpDrawable box(boolean on) {
        return new NpDrawable(kit.canvas(), (c, w, h, f) -> {
            float s = 12f, x = (w - s) / 2f, y = (h - s) / 2f;
            c.stroke(x + 0.6f, y + 0.6f, s - 1.2f, s - 1.2f, 1.2f, on ? NpColors.GREEN : NpColors.DIM);
            if (on) c.fill(x + 3f, y + 3f, s - 6f, s - 6f, NpColors.GREEN);
        }).min(16f, 16f);
    }

    /** A scroll bar's track or its knob: a thin line down (or across) the middle of its room. */
    private NpDrawable bar(boolean vertical, Color c0) {
        return new NpDrawable(kit.canvas(), (c, w, h, f) -> {
            if (vertical) c.fill(w / 2f - 1f, 0f, 2f, h, c0);
            else c.fill(0f, h / 2f - 1f, w, 2f, c0);
        });
    }

    @Override
    public void dispose() {
        // The fonts are the faces'; the skin itself holds only styles and drawables, so it is let go
        for (Face f : faces) f.font.dispose();
        faces.clear();
    }
}
