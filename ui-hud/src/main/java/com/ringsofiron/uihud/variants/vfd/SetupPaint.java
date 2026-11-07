package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.ringsofiron.uihud.variants.ShellScreen;

import java.util.Locale;

/**
 * One of the game's menus as the ship's computer in setup mode shows it (style-vfd.md, section 18.5),
 * painted through the kit over the menu's own widgets: they lay it out and take its keys and clicks as
 * they always have, and each frame this reads them and draws what they hold in FILAMENT's parts.
 *
 * <p>The menu's panel is a module of smoked glass on a plate of its own. A button is a field of cells
 * at pitch 2 with a cursor column, focus inverting the field and lighting the cursor; a disabled one is
 * dim. A choice is its value between ◀ and ▶, a check box a lit block or a ghost frame, a text field
 * fixed cells with their ghosts and a block cursor, a level a bar of segments, and a long list shows its
 * place as a column of segments. The legend of keys sits in the bottom rack's window in setup mode, or
 * on a slab of its own under the menu over the paused game. The mod scripts dialog's title is in a red
 * strip: scripts run with the player's full access.
 *
 * <p>The game's focus ({@code FocusNav}) tints the focused control; FILAMENT draws focus as the inverse
 * field and the lit cursor instead, so the tint is read here and never shows.
 */
final class SetupPaint implements Filament.Painter {
    /** The colour {@code com.ringsofiron.uihud.FocusNav} tints the focused control with; a test keeps the two the same. */
    static final Color FOCUS = new Color(1f, 0.82f, 0.35f, 1f);
    /** A cell of text, and of a button. */
    static final float CW = 6f * SetupSkin.TEXT, KW = 6f * SetupSkin.KEY;

    final ShellScreen screen;
    private final Table panel;
    private final SetupSkin skin;
    private final VfdShellLook look;
    /** What the menu shows in the rack's window rather than where it sits: the build's version. */
    private Label rackText;
    /** Seconds this menu has shown, for the cursor's blink and an overflowing row's scroll. */
    float time;

    private final Vector2 v = new Vector2();
    private final StringBuilder sb = new StringBuilder();
    private final StringBuilder cut = new StringBuilder();
    /** The box worked out last ({@link #box}): stage units, y down from the top. */
    private float bx, by, bw, bh, stageH;

    SetupPaint(VfdShellLook look, SetupSkin skin, ShellScreen screen, Table panel) {
        this.look = look;
        this.skin = skin;
        this.screen = screen;
        this.panel = panel;
    }

    /** Shows {@code l}'s text in the rack's window instead of where it sits. */
    void inRack(Label l) {
        rackText = l;
    }

    /** The stage the menu is on, or null once it is gone. */
    Stage stage() {
        return panel.getStage();
    }

    @Override
    public void paint(Filament f) {
        Stage stage = panel.getStage();
        if (stage == null) return;
        stageH = stage.getHeight();
        box(panel);
        float px = bx, py = by, pw = bw, ph = bh;
        if (screen != ShellScreen.MOD_SCRIPTS) look.menuAt(px, py, pw, ph);
        f.plate(px, py, pw, ph, 7f, 6f);
        f.module(px, py, pw, ph, f.o().filaments(Math.max(2, Math.round(ph / 120f))));
        if (panel instanceof Window) titleStrip(f, (Window) panel, px, py, pw);
        walk(f, panel, -Float.MAX_VALUE, Float.MAX_VALUE);
        for (Actor a : stage.getActors()) {
            if (a instanceof SelectBox.SelectBoxScrollPane && a.isVisible()) popup(f, (SelectBox.SelectBoxScrollPane<?>) a);
        }
        if (screen != ShellScreen.MOD_SCRIPTS) legend(f, stage.getWidth(), px, py, pw, ph);
    }

    // ------------------------------------------------------------------ the widgets

    /** Paints {@code a} and what it holds, where it lies between {@code top} and {@code bottom} (y down): a list's rows out of view are not shown. */
    private void walk(Filament f, Actor a, float top, float bottom) {
        if (!a.isVisible() || a == rackText) return;
        if (a instanceof ScrollPane) {
            pane(f, (ScrollPane) a, top, bottom);
            return;
        }
        if (a instanceof Window && a != panel) return;
        boolean leaf = a instanceof Label || a instanceof Button || a instanceof SelectBox || a instanceof TextField || a instanceof Slider;
        if (leaf) {
            box(a);
            if (by < top - 1f || by + bh > bottom + 1f) return;
            if (a instanceof CheckBox) check(f, (CheckBox) a);
            else if (a instanceof TextButton) button(f, (TextButton) a);
            else if (a instanceof SelectBox) choice(f, (SelectBox<?>) a);
            else if (a instanceof TextField) field(f, (TextField) a);
            else if (a instanceof Slider) level(f, (Slider) a);
            else if (a instanceof Label) label(f, (Label) a);
            return;
        }
        if (a instanceof Group) {
            Group g = (Group) a;
            // The dialog's title is its strip's
            if (panel instanceof Window && g == ((Window) panel).getTitleTable()) return;
            for (Actor c : g.getChildren()) walk(f, c, top, bottom);
        }
    }

    private void label(Filament f, Label l) {
        if (l.getText().length() == 0) return;
        SetupSkin.Kind kind = skin.kind(l.getStyle());
        switch (kind) {
            case DISPLAY:
            case HEADING: {
                float h = kind == SetupSkin.Kind.DISPLAY ? SetupSkin.DISPLAY : SetupSkin.HEADING;
                CharSequence s = l.getText();
                float w = f.seg14Width(h, s.length(), f.o());
                f.seg14(bx + bw / 2f - w / 2f, by + (bh - h) / 2f, h, s, f.o().level(kind == SetupSkin.Kind.DISPLAY ? Drive.FULL : Drive.NORM));
                return;
            }
            case TITLE: {
                // A legend in the glass, lit full, on the label's baseline
                float cap = 9.4f;
                f.legend(bx, by + (bh + cap) / 2f, upper(l.getText()), f.o().size(13f).full().noGhost());
                return;
            }
            default:
                lines(f, l, kind == SetupSkin.Kind.DIM ? Drive.DIM : Drive.NORM, kind == SetupSkin.Kind.WARN ? Phosphor.AMBER : Phosphor.ZINC,
                        -1, -1);
        }
    }

    /**
     * A label's lines as the label laid them out, in dot-matrix cells at pitch 1.5, each cell's ghost
     * showing; cells {@code inv0} to {@code inv1} of the first line inverse (none for -1).
     */
    private void lines(Filament f, Label l, float level, Phosphor color, int inv0, int inv1) {
        box(l);
        GlyphLayout layout = l.getGlyphLayout();
        BitmapFont font = l.getStyle().font;
        float lineH = font.getLineHeight();
        boolean multi = layout.runs.size > 1;
        float textH = multi ? layout.height : font.getCapHeight();
        float x0 = bx, y0 = by + (bh - textH) / 2f;
        int align = l.getLabelAlign();
        if (multi && (align & Align.left) == 0) x0 += (align & Align.right) != 0 ? bw - layout.width : (bw - layout.width) / 2f;
        float p = cellPitch(font);
        for (int r = 0; r < layout.runs.size; r++) {
            GlyphLayout.GlyphRun run = layout.runs.get(r);
            sb.setLength(0);
            for (int k = 0; k < run.glyphs.size; k++) sb.append((char) run.glyphs.get(k).id);
            int line = Math.round(-run.y / lineH);
            Filament.Opt o = f.o().pitch(p).level(level).color(color);
            if (r == 0 && inv0 >= 0 && inv1 > inv0) o.inverse(inv0, Math.min(inv1, sb.length()));
            f.dm(x0 + run.x, y0 + line * lineH, sb, o);
        }
    }

    /** The dot pitch a ROM font's cells were made at: six dots a cell. */
    private static float cellPitch(BitmapFont font) {
        BitmapFont.Glyph g = font.getData().getGlyph('M');
        return g == null ? SetupSkin.TEXT : g.xadvance * font.getData().scaleX / 6f;
    }

    private void button(Filament f, TextButton b) {
        int n = Math.max(2, cells(bw, SetupSkin.KEY));
        float fw = n * KW - SetupSkin.KEY, fx = bx + (bw - fw) / 2f, fy = by + (bh - 7f * SetupSkin.KEY) / 2f;
        boolean disabled = b.isDisabled();
        boolean focus = !disabled && (isFocus(b.getColor()) || b.isPressed());
        CharSequence text = fit(b.getText(), n - 1, focus);
        // The field's cells, every one's ghost
        f.dm(fx, fy, "", f.o().pitch(SetupSkin.KEY).cells(n));
        if (focus) {
            f.dm(fx, fy, "▶", f.o().pitch(SetupSkin.KEY).full().noGhost());
            sb.setLength(0);
            sb.append(' ').append(text);
            f.dm(fx, fy, sb, f.o().pitch(SetupSkin.KEY).norm().noGhost().cells(n).inverse(1, n));
        } else {
            if (b.isOver() && !disabled) f.dm(fx, fy, "▶", f.o().pitch(SetupSkin.KEY).dim().noGhost());
            f.dm(fx + KW, fy, text, f.o().pitch(SetupSkin.KEY).level(disabled ? Drive.DIM : Drive.NORM).noGhost());
        }
        // One of a group of choices, picked: a lit frame round its word
        if (b.getButtonGroup() != null && b.isChecked()) {
            f.frameLit(fx + KW - 4f, fy - 5f, (n - 1) * KW + 4f, 9f * SetupSkin.KEY + 6f, 1f, f.o().norm());
        }
    }

    private void check(Filament f, CheckBox c) {
        boolean focus = isFocus(c.getColor()) || c.isPressed();
        box(c.getImage());
        float s = 9f, x = bx + (bw - s) / 2f, y = by + (bh - s) / 2f;
        if (c.isChecked()) f.rectLit(x, y, s, s, f.o().norm().r(0.8f));
        else f.frameLit(x, y, s, s, 1f, f.o().ghostOnly().k(3f));
        if (focus) f.dm(x - CW - 3f, y - 0.75f, "▶", f.o().pitch(SetupSkin.TEXT).full().noGhost());
        Label l = c.getLabel();
        CharSequence t = l.getText();
        int from = 0;
        while (from < t.length() && t.charAt(from) == ' ') from++;
        lines(f, l, Drive.NORM, Phosphor.ZINC, focus ? from : -1, focus ? t.length() : -1);
    }

    private void choice(Filament f, SelectBox<?> s) {
        Drawable bg = s.getStyle().background;
        float left = bg == null ? 2f * CW : bg.getLeftWidth(), right = bg == null ? 2f * CW : bg.getRightWidth();
        int n = Math.max(1, cells(bw - left - right, SetupSkin.TEXT));
        float y = by + (bh - 7f * SetupSkin.TEXT) / 2f;
        boolean focus = isFocus(s.getColor());
        Object picked = s.getSelected();
        CharSequence value = fit(picked == null ? "" : picked.toString(), n, focus);
        f.dm(bx, y, "◀", f.o().pitch(SetupSkin.TEXT).dim().noGhost());
        Filament.Opt o = f.o().pitch(SetupSkin.TEXT).norm().cells(n);
        if (focus) o.inverse(0, n);
        f.dm(bx + left, y, value, o);
        f.dm(bx + left + n * CW + CW, y, "▶", f.o().pitch(SetupSkin.TEXT).dim().noGhost());
    }

    private void field(Filament f, TextField t) {
        Drawable bg = t.getStyle().background;
        float left = bg == null ? 0f : bg.getLeftWidth(), right = bg == null ? 0f : bg.getRightWidth();
        int n = Math.max(1, cells(bw - left - right, SetupSkin.TEXT));
        float x = bx + left, y = by + (bh - 7f * SetupSkin.TEXT) / 2f;
        Stage stage = t.getStage();
        boolean typing = stage != null && stage.getKeyboardFocus() == t;
        String text = t.getText();
        if (text.isEmpty() && !typing) {
            String hint = t.getMessageText();
            f.dm(x, y, hint == null ? "" : hint, f.o().pitch(SetupSkin.TEXT).dim().cells(n));
        } else {
            // The cells in view: the end of what is typed, kept round the cursor
            int cursor = Math.min(t.getCursorPosition(), text.length());
            int start = Math.max(0, Math.min(cursor - n + 1, text.length() - n + 1));
            int end = Math.min(text.length(), start + n);
            Filament.Opt o = f.o().pitch(SetupSkin.TEXT).norm().cells(n);
            String selected = t.getSelection();
            if (typing && selected != null && !selected.isEmpty()) {
                int a = Math.min(t.getSelectionStart(), cursor) - start, b = Math.max(t.getSelectionStart(), cursor) - start;
                o.inverse(Math.max(0, a), Math.min(n, b));
            } else if (typing && ((int) (time * 2f)) % 2 == 0) {
                // The block cursor, in its blink's lit phase: the letter under it shows cut
                o.inverse(cursor - start, cursor - start + 1);
            }
            f.dm(x, y, text.substring(start, end), o);
        }
        f.rectLit(x - 3f, y + 7f * SetupSkin.TEXT + 2.5f, n * CW + 4f, 0.8f, f.o().ghostOnly().k(3f));
    }

    private void level(Filament f, Slider s) {
        boolean focus = isFocus(s.getColor());
        float h = 8f, y = by + (bh - h) / 2f;
        f.bar(bx, y, bw, h, 20, s.getVisualPercent(), f.o().level(focus ? Drive.FULL : Drive.NORM).gap(1.6f));
        if (focus) f.frameLit(bx - 3f, y - 3f, bw + 6f, h + 6f, 0.8f, f.o().dim());
    }

    /** A scroll pane: the rows in view, and its place as a column of segments down its right edge. */
    private void pane(Filament f, ScrollPane p, float top, float bottom) {
        box(p);
        float x = bx, y = by, w = bw, h = bh;
        Actor content = p.getActor();
        if (content != null) walk(f, content, Math.max(top, y), Math.min(bottom, y + h));
        if (content == null || !p.isScrollY()) return;
        float all = content.getHeight(), view = p.getScrollHeight();
        if (all <= 0f) return;
        float from = p.getVisualScrollY() / all, to = (p.getVisualScrollY() + view) / all;
        int n = Math.max(6, Math.round(h / 17f));
        float gap = 2f, sh = (h - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            boolean on = i >= Math.round(from * n) && i < Math.max(Math.round(from * n) + 1, Math.round(to * n));
            Filament.Opt o = on ? f.o().dim() : f.o().ghostOnly().k(1.6f);
            f.rectLit(x + w - 8f, y + i * (sh + gap), 5f, sh, o);
        }
    }

    /** A choice's list, opened over the menu: a slab of its own, the picked item inverse. */
    private void popup(Filament f, SelectBox.SelectBoxScrollPane<?> pane) {
        // Its own drawing is none: the list is clear, like every style in the skin
        box(pane);
        float x = bx, y = by, w = bw, h = bh;
        f.plate(x, y, w, h, 3f, 4f);
        f.module(x, y, w, h, f.o().radius(2.5f).filaments(Math.max(1, Math.round(h / 60f))));
        List<?> list = pane.getList();
        box(list);
        float itemH = list.getItemHeight();
        Drawable bg = list.getStyle().background, sel = list.getStyle().selection;
        float top = by + (bg == null ? 0f : bg.getTopHeight());
        float left = bx + (sel == null ? 0f : sel.getLeftWidth());
        int n = Math.max(1, cells(bw - 2f * (sel == null ? 0f : sel.getLeftWidth()), SetupSkin.TEXT));
        Object overItem = list.getOverItem();
        int picked = list.getSelectedIndex(), over = overItem == null ? -1 : list.getItems().indexOf(cast(overItem), true);
        for (int i = 0; i < list.getItems().size; i++) {
            float iy = top + i * itemH;
            if (iy < y - 1f || iy + itemH > y + h + 1f) continue;
            Object item = list.getItems().get(i);
            boolean lit = over >= 0 ? i == over : i == picked;
            Filament.Opt o = f.o().pitch(SetupSkin.TEXT).norm().cells(n);
            if (lit) o.inverse(0, n);
            f.dm(left, iy + (itemH - 7f * SetupSkin.TEXT) / 2f, fit(item == null ? "" : item.toString(), n, lit), o);
        }
    }

    /** The dialog's name on a red tile, the strip's red frame ghosting beyond it. */
    private void titleStrip(Filament f, Window w, float x, float y, float width) {
        CharSequence title = upper(w.getTitleLabel().getText());
        float tw = f.legendWidth(title, f.o().size(13f)) + 20f;
        f.frameLit(x + 8f, y + 8f, width - 16f, 22f, 1.1f, f.o().ghostOnly().red().k(2.5f));
        f.rectLit(x + 8f, y + 8f, tw, 22f, f.o().red().norm().r(1.2f));
        f.knockout(x + 18f, y + 24f, title, f.o().size(13f));
    }

    // ------------------------------------------------------------------ the legend of keys

    private static final String[] UP_DOWN = {"↑", "↓"}, LEFT_RIGHT = {"◀", "▶"}, ENTER = {"ENTER"}, ESC = {"ESC"};

    private void legend(Filament f, float width, float px, float py, float pw, float ph) {
        String[][] caps;
        String[] words;
        String esc = screen == ShellScreen.PAUSE ? "RESUME" : "BACK";
        switch (screen) {
            case MAIN:
                caps = new String[][] {UP_DOWN, ENTER};
                words = new String[] {"CHOOSE", "SELECT"};
                break;
            case SETTINGS:
                caps = new String[][] {UP_DOWN, LEFT_RIGHT, ENTER, ESC};
                words = new String[] {"CHOOSE", "CHANGE", "SELECT", esc};
                break;
            default:
                caps = new String[][] {UP_DOWN, ENTER, ESC};
                words = new String[] {"CHOOSE", "SELECT", esc};
        }
        if (look.overGame()) {
            // Over the paused game: a slab of its own under the menu
            float w = -14f;
            for (int i = 0; i < caps.length; i++) w += capWordWidth(f, caps[i], words[i]);
            float x = px + pw / 2f - w / 2f, y = Math.min(py + ph + 16f, stageH - 34f);
            f.slab(x - 10f, y, w + 20f, 26f, 3f, 1, false);
            float cx = x;
            for (int i = 0; i < caps.length; i++) cx = capWord(f, cx, y + 4f, caps[i], words[i]);
            return;
        }
        // Setup mode: the bottom rack's window, either side of a menu that comes down over it
        float y = stageH - 39f;
        boolean split = py + ph > stageH - 46f;
        int leftItems = split ? Math.min(2, caps.length) : caps.length;
        if (split) {
            f.module(16f, y, Math.max(0f, px - 30f), 26f, f.o().filaments(1));
            f.module(px + pw + 14f, y, Math.max(0f, width - 16f - (px + pw + 14f)), 26f, f.o().filaments(1));
        } else {
            f.module(16f, y, width - 32f, 26f, f.o().filaments(1));
        }
        float cx = 28f;
        for (int i = 0; i < leftItems; i++) cx = capWord(f, cx, y + 4f, caps[i], words[i]);
        cx = px + pw + 26f;
        for (int i = leftItems; i < caps.length; i++) cx = capWord(f, cx, y + 4f, caps[i], words[i]);
        if (rackText != null && rackText.getText().length() > 0) {
            CharSequence t = rackText.getText();
            f.dm(width - 28f - (t.length() * CW - SetupSkin.TEXT), y + 8.5f, t, f.o().pitch(SetupSkin.TEXT).dim().noGhost());
        }
    }

    /** Key caps, then what they do; returns where the next starts. */
    private float capWord(Filament f, float x, float y, String[] caps, String word) {
        float cx = x;
        for (String c : caps) cx += f.keycap(cx, y, c, 18f, 11f, false) + 3f;
        cx += 3f;
        return cx + f.dm(cx, y + 4f, word, f.o().pitch(SetupSkin.TEXT).norm().noGhost()) + 14f;
    }

    private float capWordWidth(Filament f, String[] caps, String word) {
        float w = 3f;
        for (String c : caps) w += f.keycapWidth(c, 18f, 11f) + 3f;
        return w + word.length() * CW - SetupSkin.TEXT + 14f;
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }

    /** Whether {@code c} is the focus tint, at any alpha (a menu fading in). */
    static boolean isFocus(Color c) {
        return Math.abs(c.r - FOCUS.r) < 0.01f && Math.abs(c.g - FOCUS.g) < 0.01f && Math.abs(c.b - FOCUS.b) < 0.01f;
    }

    /** How many cells of a field at {@code pitch} fit in {@code width}: the last one needs no gap after it. */
    static int cells(float width, float pitch) {
        return (int) Math.floor((width + pitch) / (6f * pitch) + 1e-3f);
    }

    /**
     * {@code s} cut to {@code n} cells, or, on the row with the focus, scrolled through a cell at a time
     * with a pause at each end (style-vfd.md, 9.3): only the focused row may scroll its overflow.
     */
    private CharSequence fit(CharSequence s, int n, boolean scroll) {
        if (s.length() <= n || n <= 0) return s;
        int start = scroll ? scrolled(s.length() - n, time) : 0;
        cut.setLength(0);
        cut.append(s, start, start + n);
        return cut;
    }

    /**
     * How many cells a row {@code over} cells too long for its field has scrolled by after
     * {@code seconds}: four cells a second, held three steps at each end, then round again.
     */
    static int scrolled(int over, float seconds) {
        if (over <= 0) return 0;
        int step = ((int) (seconds * 4f)) % (over + 6);
        return Math.max(0, Math.min(over, step - 3));
    }

    /** For the legends set in type, which, unlike the ROM, has a lower case. */
    private static String upper(CharSequence s) {
        return s.toString().toUpperCase(Locale.ROOT);
    }

    /** Works out {@code a}'s box on the stage, y down: {@link #bx}, {@link #by}, {@link #bw}, {@link #bh}. */
    private void box(Actor a) {
        a.localToStageCoordinates(v.set(0f, 0f));
        bx = v.x;
        bw = a.getWidth();
        bh = a.getHeight();
        by = stageH - (v.y + bh);
    }

}
