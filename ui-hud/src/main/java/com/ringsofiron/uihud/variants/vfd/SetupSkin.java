package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IdentityMap;

/**
 * The skin the game's menus are built with under the FILAMENT HUDs: the classic skin's style names
 * ({@code ShellLook#skin}) over the character ROM's fonts, with the styles themselves drawing nothing.
 * The menus lay out on the ROM's cells and take their keys and clicks as ever; what they show is
 * painted over them through the kit ({@link SetupPaint}), with its ghosts and glow.
 *
 * <p>Text is at pitch 1.5 (nine units a cell) and the buttons' at pitch 2 (twelve), as the mockups
 * set them; the title and the headings measure as fourteen-segment characters, 22 and 16 tall.
 */
final class SetupSkin implements Disposable {
    /** Dot pitch of the menus' text, and of their buttons. */
    static final float TEXT = 1.5f, KEY = 2f;
    /** Fourteen-segment heights of the game's title and of a menu's heading. */
    static final float DISPLAY = 22f, HEADING = 16f;
    /** The fourteen-segment cell's width and its spacing, as shares of its height (Filament#seg14). */
    static final float SEG14_ADVANCE = 0.6f + 0.24f;

    /** What a label is, by its style, for the painter. */
    enum Kind { DISPLAY, HEADING, TITLE, TEXT, DIM, WARN }

    private final RomFont rom = new RomFont();
    private final Skin skin = new Skin();
    private final IdentityMap<Label.LabelStyle, Kind> kinds = new IdentityMap<>();
    private final Texture white;
    /** Every style's text colour, clear until {@link #reveal}. */
    private final Array<Color> inks = new Array<>();
    private final Array<Kind> inkKinds = new Array<>();
    final BitmapFont text, key;

    /** Needs GL: the fonts are the ROM on a texture. */
    SetupSkin() {
        text = rom.font(6f * TEXT, 15f, 2.25f);
        key = rom.font(6f * KEY, 18f, 2f);
        BitmapFont display = rom.font(DISPLAY * SEG14_ADVANCE, DISPLAY + 6f, 2f);
        BitmapFont heading = rom.font(HEADING * SEG14_ADVANCE, HEADING + 6f, 2f);
        // The section titles are legends in the glass; they measure near enough as small cells
        BitmapFont title = rom.font(7.5f, 13f, 2.5f);
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        white = new Texture(p);
        p.dispose();

        label("display", display, Kind.DISPLAY);
        label("heading", heading, Kind.HEADING);
        label("title", title, Kind.TITLE);
        label("default", text, Kind.TEXT);
        label("dim", text, Kind.DIM);
        label("warn", text, Kind.WARN);
        skin.add("default-font", text, BitmapFont.class);
        skin.add("panel", empty(0f, 0f, 0f, 0f), Drawable.class);

        // A button is a field of cells: one for the cursor, then its word
        for (String name : new String[] {"menu", "toggle", "default"}) {
            TextButton.TextButtonStyle b = new TextButton.TextButtonStyle(empty(14f, 2f, 14f, 2f), empty(14f, 2f, 14f, 2f),
                    empty(14f, 2f, 14f, 2f), key);
            b.over = empty(14f, 2f, 14f, 2f);
            b.disabled = empty(14f, 2f, 14f, 2f);
            b.fontColor = ink(Kind.TEXT);
            b.disabledFontColor = ink(Kind.DIM);
            skin.add(name, b);
        }

        TextField.TextFieldStyle field = new TextField.TextFieldStyle(text, ink(Kind.TEXT), empty(0f, 0f, 0f, 0f),
                empty(0f, 0f, 0f, 0f), empty(0f, 3f, 0f, 3f));
        field.messageFont = text;
        field.messageFontColor = ink(Kind.DIM);
        skin.add("default", field);

        // Room down the right for a list's bar of segments, reserved whether or not it scrolls
        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScroll = empty(0f, 0f, 0f, 0f).min(12f, 0f);
        scroll.vScrollKnob = empty(0f, 0f, 0f, 0f).min(12f, 12f);
        scroll.hScroll = empty(0f, 0f, 0f, 0f).min(0f, 12f);
        scroll.hScrollKnob = empty(0f, 0f, 0f, 0f).min(12f, 12f);
        skin.add("default", scroll);

        List.ListStyle list = new List.ListStyle(text, ink(Kind.TEXT), ink(Kind.TEXT), empty(9f, 2f, 9f, 2f));
        list.background = empty(0f, 4f, 0f, 4f);
        skin.add("default", list);

        // A choice: its value between the arrows, two cells each side
        SelectBox.SelectBoxStyle select = new SelectBox.SelectBoxStyle(text, ink(Kind.TEXT), empty(18f, 3f, 18f, 3f), scroll, list);
        skin.add("default", select);

        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle(empty(0f, 0f, 0f, 0f).min(12f, 12f),
                empty(0f, 0f, 0f, 0f).min(12f, 12f), text, ink(Kind.TEXT));
        skin.add("default", check);

        Slider.SliderStyle slider = new Slider.SliderStyle(empty(0f, 0f, 0f, 0f).min(20f, 12f), empty(0f, 0f, 0f, 0f).min(6f, 12f));
        skin.add("default-horizontal", slider);

        Window.WindowStyle window = new Window.WindowStyle(text, ink(Kind.TEXT), empty(0f, 0f, 0f, 0f));
        // A modal window dims what it floats over, as the pause does
        window.stageBackground = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0f, 0f, 0f, 0.58f));
        skin.add("default", window);
    }

    Skin skin() {
        return skin;
    }

    /** What a label with style {@code s} is: plain text unless it is one of the skin's named styles. */
    Kind kind(Label.LabelStyle s) {
        Kind k = kinds.get(s);
        return k == null ? Kind.TEXT : k;
    }

    /** A white texel, for scrims. */
    Texture white() {
        return white;
    }

    /**
     * Lets the fonts' own glyphs show, in the phosphor's colours: for when the painter cannot paint,
     * so the menus are still there to read and leave.
     */
    void reveal() {
        for (int i = 0; i < inks.size; i++) {
            Kind k = inkKinds.get(i);
            Phosphor c = k == Kind.WARN ? Phosphor.AMBER : Phosphor.ZINC;
            c.lit(k == Kind.DIM ? Drive.DIM : k == Kind.TEXT ? Drive.NORM : Drive.FULL, inks.get(i));
        }
    }

    private void label(String name, BitmapFont font, Kind kind) {
        Label.LabelStyle s = new Label.LabelStyle(font, ink(kind));
        kinds.put(s, kind);
        skin.add(name, s);
    }

    /** A clear colour, made visible by {@link #reveal}. */
    private Color ink(Kind kind) {
        Color c = new Color(0f, 0f, 0f, 0f);
        inks.add(c);
        inkKinds.add(kind);
        return c;
    }

    /** Draws nothing, with the room round what sits on it. */
    static Space empty(float left, float top, float right, float bottom) {
        Space d = new Space();
        d.setLeftWidth(left);
        d.setTopHeight(top);
        d.setRightWidth(right);
        d.setBottomHeight(bottom);
        return d;
    }

    /** A drawable that only takes room: what shows is painted over the menu. */
    static final class Space extends BaseDrawable {
        Space min(float w, float h) {
            setMinWidth(w);
            setMinHeight(h);
            return this;
        }
    }

    @Override
    public void dispose() {
        rom.dispose();
        white.dispose();
    }
}
