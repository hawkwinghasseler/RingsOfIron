package com.ringsofiron.uihud.variants.cassette;

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
 * The Yard Standard's skin for the game's menus (style guide, section 9): every style the menus look
 * up by name ({@code HudSkin}'s names), made of the style's hardware. The faceplate is enamel, the
 * menu's buttons are push buttons, a field is a card in a label holder, a drop-down is a flap window,
 * a check box is a bat toggle, a slider is a slide pot, and the mod scripts question is a caution tag.
 * A few more styles are for what {@link YardShellDress} puts round the menus once they are built: the
 * builder's plate, typed cards, Dymo tape, keycaps, cassette labels and rubber stamps.
 *
 * <p>The skin's fonts are its own, kept for its life, and drawn again in place when the UI scale
 * changes ({@link #rescale}), so the labels holding them need no rebuilding.
 */
public final class YardSkin implements Disposable {
    // --- Inks on the enamel ------------------------------------------------------------------
    static final Color INK = Yard.INK;
    static final Color INK_DIM = Yard.hex("5E5446");
    static final Color INK_FAINT = new Color(42 / 255f, 36 / 255f, 32 / 255f, 0.36f);
    static final Color CAST = Yard.hex("D8D5CD");
    static final Color CAST_DIM = Yard.hex("B9B6AE");
    static final Color STAMP = Yard.hex("5872A8");
    static final Color PENCIL = Yard.hex("99928A");
    static final Color TAG = Yard.hex("F2C531"), TAG_FOOT = Yard.hex("E2B22A");

    /** A push button's cap: its top, its lip and its legend's ink. */
    enum Cap {
        CREAM("EFE8D4", "C9BB9B", "2A2420"),
        GREEN("A7E483", "3F6A33", "10200C"),
        AMBER("FFC466", "8A5F1C", "2A1606"),
        RED("C9463A", "7A221B", "FBE9DC");

        final Color top, lip, ink;

        Cap(String top, String lip, String ink) {
            this.top = Yard.hex(top);
            this.lip = Yard.hex(lip);
            this.ink = Yard.hex(ink);
        }
    }

    /** One of the skin's fonts: how to make it again at another scale. */
    private static final class Font {
        final BitmapFont font;
        final YardFonts.Face face;
        final float size;
        final boolean caps;

        Font(BitmapFont font, YardFonts.Face face, float size, boolean caps) {
            this.font = font;
            this.face = face;
            this.size = size;
            this.caps = caps;
        }
    }

    private final Skin skin = new Skin();
    private final YardPaint paint;
    private final Array<Font> fonts = new Array<>();
    private float scale;

    /** The skin at UI scale {@code scale}; its hardware is painted with {@code paint}, which the caller keeps. */
    public YardSkin(YardPaint paint, float scale) {
        this.paint = paint;
        this.scale = scale;
        build();
    }

    public Skin skin() {
        return skin;
    }

    public float scale() {
        return scale;
    }

    /** Draws every font again for {@code uiScale}; true when the scale changed. */
    public boolean rescale(float uiScale) {
        if (Math.abs(uiScale - scale) < 0.01f) return false;
        scale = uiScale;
        for (int i = 0; i < fonts.size; i++) {
            Font f = fonts.get(i);
            YardFonts.remake(f.font, f.face, f.size, uiScale, f.caps);
        }
        return true;
    }

    private BitmapFont font(String name, YardFonts.Face face, float size, boolean caps) {
        BitmapFont f = YardFonts.own(face, size, scale, caps);
        fonts.add(new Font(f, face, size, caps));
        skin.add(name, f);
        return f;
    }

    private YardDrawable part(YardDrawable.Painter painter) {
        return new YardDrawable(paint, painter);
    }

    private void build() {
        BitmapFont display = font("display-font", YardFonts.Face.DYMO, 50f, true);
        BitmapFont heading = font("heading-font", YardFonts.Face.LEGEND, 21f, true);
        BitmapFont button = font("button-font", YardFonts.Face.LEGEND, 15f, true);
        BitmapFont buttonBig = font("button-big-font", YardFonts.Face.LEGEND, 19f, true);
        BitmapFont body = font("default-font", YardFonts.Face.LEGEND, 15f, false);
        BitmapFont small = font("small-font", YardFonts.Face.LEGEND, 13f, true);
        BitmapFont legend = font("legend-font", YardFonts.Face.LEGEND, 12f, true);
        BitmapFont typed = font("typed-font", YardFonts.Face.PAPER, 14f, false);
        BitmapFont typedSmall = font("typed-small-font", YardFonts.Face.PAPER, 13f, false);
        BitmapFont tagTitle = font("tag-title-font", YardFonts.Face.PAPER, 21f, true);
        BitmapFont flap = font("flap-font", YardFonts.Face.FLAP, 15f, true);
        BitmapFont stamped = font("stamped-font", YardFonts.Face.FLAP, 14f, false);
        BitmapFont dymo = font("dymo-font", YardFonts.Face.DYMO, 12f, true);
        BitmapFont key = font("key-font", YardFonts.Face.FLAP, 13f, true);

        // --- The faceplate behind each menu, and its parts ---------------------------------------
        skin.add("panel", part(YardShellParts::faceplate).pad(30f, 34f, 30f, 26f), Drawable.class);
        skin.add("plate", part(YardShellParts::builderPlate).pad(46f, 16f, 46f, 14f), Drawable.class);
        skin.add("well", part(YardShellParts::well).pad(8f, 8f, 8f, 8f), Drawable.class);
        skin.add("deck", part(YardShellParts::tapeDeck).pad(34f, 27f, 34f, 64f), Drawable.class);
        skin.add("deck-empty", part(YardShellParts::emptyDeck).pad(20f, 20f, 20f, 40f), Drawable.class);
        skin.add("cassette", part((p, w, h, f) -> YardShellParts.spine(p, w, h, false, false)).pad(18f, 8f, 18f, 11f), Drawable.class);
        skin.add("bay", part(YardShellParts::bay).pad(16f, 12f, 16f, 14f), Drawable.class);
        skin.add("hatch", part(YardShellParts::hatchFrame).pad(14f, 14f, 14f, 14f), Drawable.class);
        skin.add("hatch-panel", part(YardShellParts::hatchPanel).pad(26f, 36f, 26f, 36f), Drawable.class);
        skin.add("grille", part(YardShellParts::grille).min(120f, 80f), Drawable.class);
        skin.add("rule", part((p, w, h, f) -> YardShellParts.rule(p, w, h, false)).min(10f, 9f), Drawable.class);
        skin.add("red-line", part((p, w, h, f) -> YardShellParts.rule(p, w, h, true)).min(10f, 12f), Drawable.class);
        skin.add("hinge", part(YardShellParts::hinge).min(34f, 60f), Drawable.class);
        skin.add("riveted", part(YardShellParts::rivetedPlate).pad(22f, 16f, 22f, 16f), Drawable.class);
        skin.add("scrim", part((p, w, h, f) -> p.rect(0f, 0f, w, h, Color.BLACK, 0.55f)), Drawable.class);

        // --- Labels ----------------------------------------------------------------------------
        skin.add("default", new Label.LabelStyle(body, INK));
        skin.add("display", new Label.LabelStyle(display, INK));
        skin.add("heading", new Label.LabelStyle(heading, INK));
        Label.LabelStyle title = new Label.LabelStyle(small, INK);
        title.background = part(YardShellParts::bayLabel).pad(0f, 6f, 10f, 6f);
        skin.add("title", title);
        skin.add("dim", new Label.LabelStyle(small, INK_DIM));
        skin.add("warn", new Label.LabelStyle(small, Yard.PAPER_RED));
        skin.add("alert", new Label.LabelStyle(small, Yard.PAPER_RED));
        skin.add("legend", new Label.LabelStyle(legend, INK));
        skin.add("legend-dim", new Label.LabelStyle(legend, INK_DIM));
        skin.add("plate-title", new Label.LabelStyle(display, CAST));
        skin.add("plate-sub", new Label.LabelStyle(legend, CAST_DIM));
        skin.add("typed", new Label.LabelStyle(typed, Yard.PAPER_INK));
        skin.add("typed-dim", new Label.LabelStyle(typedSmall, INK_DIM));
        skin.add("typed-red", new Label.LabelStyle(typed, Yard.PAPER_RED));
        skin.add("tag-title", new Label.LabelStyle(tagTitle, Yard.PAPER_INK));
        Label.LabelStyle card = new Label.LabelStyle(typedSmall, Yard.PAPER_INK);
        card.background = part(YardShellParts::card).pad(12f, 10f, 12f, 9f);
        skin.add("card", card);
        Label.LabelStyle serial = new Label.LabelStyle(stamped, INK);
        serial.background = part(YardShellParts::serialPlate).pad(18f, 17f, 18f, 5f);
        skin.add("serial", serial);
        Label.LabelStyle tape = new Label.LabelStyle(dymo, Yard.hex("F4EFE6"));
        tape.background = part(YardShellParts::dymoTape).pad(7f, 2f, 7f, 2f);
        skin.add("dymo", tape);
        Label.LabelStyle stamp = new Label.LabelStyle(key, STAMP);
        stamp.background = part(YardShellParts::stamp).pad(8f, 3f, 8f, 3f);
        skin.add("stamp", stamp);
        Label.LabelStyle lens = new Label.LabelStyle(button, Yard.LEGEND_ON_LIT);
        lens.background = part(YardShellParts::creamLens).pad(26f, 5f, 26f, 5f);
        skin.add("lens", lens);
        Label.LabelStyle flaps = new Label.LabelStyle(flap, Yard.FLAP_TEXT);
        flaps.background = part(YardShellParts::flapWindow).pad(8f, 3f, 8f, 3f);
        skin.add("flaps", flaps);
        Label.LabelStyle drum = new Label.LabelStyle(flap, Yard.FLAP_TEXT);
        drum.background = part(YardShellParts::drumWindow).pad(7f, 3f, 7f, 3f);
        skin.add("drum", drum);
        skin.add("prompt", new Label.LabelStyle(body, INK));
        skin.add("banner", new Label.LabelStyle(heading, Yard.PAPER_RED));

        // --- Buttons ---------------------------------------------------------------------------
        TextButton.TextButtonStyle menu = push(button, Cap.CREAM, 44f);
        skin.add("menu", menu);
        skin.add("default", push(button, Cap.CREAM, 36f));
        skin.add("menu-small", push(button, Cap.CREAM, 32f));
        skin.add("menu-go", push(button, Cap.GREEN, 44f));
        skin.add("menu-go-tall", push(buttonBig, Cap.GREEN, 58f));
        skin.add("menu-go-small", push(button, Cap.GREEN, 32f));
        skin.add("menu-amber", push(button, Cap.AMBER, 40f));
        TextButton.TextButtonStyle guarded = new TextButton.TextButtonStyle();
        guarded.font = button;
        guarded.fontColor = Cap.RED.ink;
        guarded.up = part((p, w, h, f) -> YardShellParts.guarded(p, w, h, false, false, f)).pad(18f, 9f, 18f, 12f).min(90f, 34f);
        guarded.over = part((p, w, h, f) -> YardShellParts.guarded(p, w, h, true, false, f)).pad(18f, 9f, 18f, 12f).min(90f, 34f);
        guarded.checked = part((p, w, h, f) -> YardShellParts.guarded(p, w, h, false, true, f)).pad(18f, 9f, 18f, 12f).min(90f, 34f);
        guarded.checkedOver = guarded.checked;
        guarded.checkedFontColor = Cap.CREAM.ink;
        guarded.down = guarded.checked;
        guarded.pressedOffsetY = -1.5f;
        skin.add("guarded", guarded);
        TextButton.TextButtonStyle toggle = new TextButton.TextButtonStyle();
        toggle.font = button;
        toggle.fontColor = INK;
        toggle.up = part((p, w, h, f) -> YardShellParts.pianoKey(p, w, h, false, false, f)).pad(16f, 6f, 16f, 9f).min(80f, 36f);
        toggle.over = part((p, w, h, f) -> YardShellParts.pianoKey(p, w, h, false, true, f)).pad(16f, 6f, 16f, 9f).min(80f, 36f);
        toggle.checked = part((p, w, h, f) -> YardShellParts.pianoKey(p, w, h, true, false, f)).pad(16f, 8f, 16f, 7f).min(80f, 36f);
        toggle.checkedOver = toggle.checked;
        toggle.down = toggle.checked;
        toggle.disabledFontColor = INK_FAINT;
        skin.add("toggle", toggle);
        TextButton.TextButtonStyle keycap = new TextButton.TextButtonStyle();
        keycap.font = key;
        keycap.fontColor = Yard.INK_LIGHT;
        keycap.up = part((p, w, h, f) -> YardShellParts.keycap(p, w, h, false, f)).pad(8f, 3f, 8f, 5f).min(30f, 22f);
        keycap.over = part((p, w, h, f) -> YardShellParts.keycap(p, w, h, true, f)).pad(8f, 3f, 8f, 5f).min(30f, 22f);
        keycap.down = keycap.over;
        keycap.pressedOffsetY = -1f;
        skin.add("keycap", keycap);
        TextButton.TextButtonStyle spine = new TextButton.TextButtonStyle();
        spine.font = typed;
        spine.fontColor = Yard.PAPER_INK;
        spine.up = part((p, w, h, f) -> YardShellParts.spine(p, w, h, false, f)).pad(16f, 8f, 16f, 10f).min(120f, 34f);
        spine.over = part((p, w, h, f) -> YardShellParts.spine(p, w, h, true, f)).pad(16f, 8f, 16f, 10f).min(120f, 34f);
        spine.down = spine.over;
        skin.add("spine", spine);

        // --- Check boxes: bat toggles -------------------------------------------------------------
        CheckBox.CheckBoxStyle bat = new CheckBox.CheckBoxStyle();
        bat.font = small;
        bat.fontColor = INK;
        bat.checkboxOff = part((p, w, h, f) -> YardShellParts.bat(p, w, h, false)).min(46f, 40f);
        bat.checkboxOn = part((p, w, h, f) -> YardShellParts.bat(p, w, h, true)).min(46f, 40f);
        // The focus shows as the pointer tab: a CheckBox tints only its own background, not the toggle
        bat.up = part((p, w, h, f) -> {
            if (f) YardShellParts.focusTab(p, -2f, h / 2f);
        });
        skin.add("default", bat);

        // --- Text fields: a card in a label holder -------------------------------------------------
        TextField.TextFieldStyle field = new TextField.TextFieldStyle();
        field.font = typed;
        field.fontColor = Yard.PAPER_INK;
        field.messageFont = typed;
        field.messageFontColor = PENCIL;
        field.background = part(YardShellParts::holder).pad(12f, 9f, 12f, 9f).min(60f, 36f);
        YardDrawable cursor = part((p, w, h, f) -> p.rect(0f, 0f, w, h, Yard.PAPER_INK, 0.8f));
        cursor.setMinWidth(2f);
        field.cursor = cursor;
        field.selection = part((p, w, h, f) -> p.rect(0f, 0f, w, h, Yard.hex("E8C77A"), 0.65f));
        skin.add("default", field);

        // --- Scroll panes and lists ----------------------------------------------------------------
        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.background = skin.getDrawable("well");
        scroll.vScroll = part(YardShellParts::scrollSlot).min(10f, 20f);
        scroll.vScrollKnob = part(YardShellParts::scrollKnob).min(10f, 24f);
        skin.add("default", scroll);
        ScrollPane.ScrollPaneStyle plainScroll = new ScrollPane.ScrollPaneStyle();
        plainScroll.vScrollKnob = part(YardShellParts::scrollKnob).min(8f, 24f);
        List.ListStyle list = new List.ListStyle(flap, Yard.hex("1C1712"), Yard.FLAP_TEXT,
                part((p, w, h, f) -> p.rect(0f, 0f, w, h, Yard.FLAP_AMBER)));
        list.background = part(YardShellParts::flapList).pad(6f, 4f, 6f, 4f);
        list.over = part((p, w, h, f) -> p.rect(0f, 0f, w, h, Color.WHITE, 0.08f));
        skin.add("default", list);

        // --- Drop-downs: a flap window between two arrow keys ---------------------------------------
        SelectBox.SelectBoxStyle select = new SelectBox.SelectBoxStyle(flap, Yard.FLAP_TEXT,
                part((p, w, h, f) -> YardShellParts.flapSelector(p, w, h, false, f)).pad(30f, 4f, 30f, 4f).min(120f, 30f),
                plainScroll, list);
        select.backgroundOver = part((p, w, h, f) -> YardShellParts.flapSelector(p, w, h, true, f)).pad(30f, 4f, 30f, 4f).min(120f, 30f);
        select.backgroundOpen = select.backgroundOver;
        select.overFontColor = Yard.FLAP_AMBER;
        skin.add("default", select);

        // --- Sliders: a slide pot ------------------------------------------------------------------
        Slider.SliderStyle pot = new Slider.SliderStyle();
        pot.background = part(YardShellParts::potSlot).pad(8f, 0f, 8f, 0f).min(60f, 26f);
        pot.knob = part(YardShellParts::potKnob).min(14f, 26f);
        skin.add("default-horizontal", pot);

        // --- The mod scripts question: a caution tag on a wire ---------------------------------------
        Window.WindowStyle tag = new Window.WindowStyle(tagTitle, Yard.PAPER_INK,
                part(YardShellParts::cautionTag).pad(30f, 100f, 30f, 26f));
        tag.stageBackground = skin.getDrawable("scrim");
        skin.add("default", tag);
    }

    /** A push button of {@code cap}, {@code height} tall: up, under the pointer, pressed, barred. */
    private TextButton.TextButtonStyle push(BitmapFont font, Cap cap, float height) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = font;
        s.fontColor = cap.ink;
        s.disabledFontColor = INK_FAINT;
        s.up = part((p, w, h, f) -> YardShellParts.push(p, w, h, cap, false, false, false, f)).pad(16f, 7f, 16f, 11f).min(60f, height);
        s.over = part((p, w, h, f) -> YardShellParts.push(p, w, h, cap, false, true, false, f)).pad(16f, 7f, 16f, 11f).min(60f, height);
        s.down = part((p, w, h, f) -> YardShellParts.push(p, w, h, cap, true, false, false, f)).pad(16f, 7f, 16f, 11f).min(60f, height);
        s.disabled = part((p, w, h, f) -> YardShellParts.push(p, w, h, Cap.CREAM, false, false, true, f)).pad(16f, 7f, 16f, 11f).min(60f, height);
        s.pressedOffsetY = -1.5f;
        return s;
    }

    @Override
    public void dispose() {
        // The fonts are the skin's own resources and go with it
        skin.dispose();
        fonts.clear();
    }
}
