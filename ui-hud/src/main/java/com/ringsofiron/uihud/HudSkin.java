package com.ringsofiron.uihud;

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
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * Builds the HUD's Scene2D {@link Skin} in code: a 1x1 white texture tinted into panels and
 * borders, and the {@link HudFonts} rasterised for the {@link UiScale}. Swap for an atlas-based
 * skin from modding-data later; widgets only look styles up by the names defined here.
 *
 * Fonts: "default-font" (body), "title-font" (panel titles), "large-font" (banners and headings),
 * "display-font" (the main menu's title). After the UI scale changes, {@link #rescale} draws them again.
 *
 * Style names: Label "default", "title", "dim", "alert", "warn", "prompt", "banner" (a large alert),
 * "heading" (a large title), "display"; Window "default";
 * TextButton "default", "menu" (wide, for the game's menus), "toggle" (a menu button that stays down when picked); CheckBox "default"; Slider "default-horizontal";
 * TextField, SelectBox, List and ScrollPane "default"; drawables "white", "panel", "panel-border", "prompt-bg",
 * "window-bg". Panels, windows and controls are {@link HudFrame}s: framed, with corner brackets.
 */
public final class HudSkin {
    public static final Color HUD_CYAN = new Color(0.45f, 0.85f, 1f, 1f);
    public static final Color HUD_DIM = new Color(0.55f, 0.65f, 0.75f, 1f);
    public static final Color HUD_ALERT = new Color(1f, 0.3f, 0.25f, 1f);
    public static final Color HUD_WARN = new Color(1f, 0.7f, 0.2f, 1f);
    public static final Color PANEL = new Color(0.02f, 0.05f, 0.09f, 0.85f);
    /** The top of a panel's fill, which shades down to {@link #PANEL}. */
    public static final Color PANEL_TOP = new Color(0.045f, 0.095f, 0.15f, 0.85f);
    /** A button's fill, top and bottom. */
    private static final Color BUTTON_TOP = new Color(0.12f, 0.23f, 0.33f, 0.92f), BUTTON = new Color(0.07f, 0.15f, 0.23f, 0.92f);

    private HudSkin() {}

    public static Skin create() {
        return create(com.badlogic.gdx.Gdx.graphics == null ? 1f
                : UiScale.effective(com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight()));
    }

    /** The skin with its fonts rasterised for {@code scale}. */
    public static Skin create(float scale) {
        Skin skin = new Skin();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture white = new Texture(pixmap);
        pixmap.dispose();
        skin.add("white-texture", white);
        skin.add("white", new TextureRegion(white));

        BitmapFont font = HudFonts.create(HudFonts.REGULAR, HudFonts.BODY, scale);
        BitmapFont titleFont = HudFonts.create(HudFonts.SEMIBOLD, HudFonts.TITLE, scale);
        BitmapFont largeFont = HudFonts.create(HudFonts.SEMIBOLD, HudFonts.LARGE, scale);
        BitmapFont displayFont = HudFonts.create(HudFonts.SEMIBOLD, HudFonts.DISPLAY, scale);
        skin.add("default-font", font);
        skin.add("title-font", titleFont);
        skin.add("large-font", largeFont);
        skin.add("display-font", displayFont);
        skin.add(SCALE, new float[]{scale});

        TextureRegion region = skin.getRegion("white");
        // Panels: shaded a touch lighter at the top, a hairline edge, bright corner brackets and a
        // short tab on the top edge over the panel's title
        skin.add("panel", new HudFrame(region, PANEL_TOP, PANEL)
                .border(alpha(HUD_CYAN, 0.16f)).corners(alpha(HUD_CYAN, 0.7f), 7f)
                .accent(HUD_CYAN, 22f), Drawable.class);
        skin.add("panel-border", skin.newDrawable("white", new Color(HUD_CYAN.r, HUD_CYAN.g, HUD_CYAN.b, 0.5f)), Drawable.class);
        HudFrame promptBg = new HudFrame(region, new Color(0.03f, 0.07f, 0.11f, 0.85f), new Color(0f, 0.02f, 0.04f, 0.85f))
                .border(alpha(HUD_CYAN, 0.25f)).leftBar(HUD_CYAN, 3f);
        // Room for the bar, and the text off the edges
        promptBg.setLeftWidth(10f);
        promptBg.setRightWidth(7f);
        promptBg.setTopHeight(2f);
        promptBg.setBottomHeight(2f);
        skin.add("prompt-bg", promptBg, Drawable.class);
        // Buttons: a framed plate that lights its edge under the pointer and fills when pressed
        HudFrame buttonUp = new HudFrame(region, BUTTON_TOP, BUTTON).border(alpha(HUD_CYAN, 0.28f));
        HudFrame buttonOver = new HudFrame(buttonUp).fill(lighter(BUTTON_TOP, 0.06f), lighter(BUTTON, 0.05f))
                .border(alpha(HUD_CYAN, 0.75f)).leftBar(HUD_CYAN, 3f);
        HudFrame buttonDown = new HudFrame(buttonUp).fill(new Color(0.22f, 0.44f, 0.6f, 0.95f), new Color(0.14f, 0.3f, 0.44f, 0.95f))
                .border(HUD_CYAN).leftBar(HUD_CYAN, 3f);
        skin.add("button-up", buttonUp, Drawable.class);
        skin.add("button-over", buttonOver, Drawable.class);
        skin.add("button-down", buttonDown, Drawable.class);

        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        skin.add("title", new Label.LabelStyle(titleFont, HUD_CYAN));
        skin.add("dim", new Label.LabelStyle(font, HUD_DIM));
        skin.add("alert", new Label.LabelStyle(font, HUD_ALERT));
        skin.add("warn", new Label.LabelStyle(font, HUD_WARN));
        Label.LabelStyle prompt = new Label.LabelStyle(font, Color.WHITE);
        prompt.background = skin.getDrawable("prompt-bg");
        skin.add("prompt", prompt);
        skin.add("banner", new Label.LabelStyle(largeFont, HUD_ALERT));
        skin.add("heading", new Label.LabelStyle(largeFont, HUD_CYAN));
        skin.add("display", new Label.LabelStyle(displayFont, HUD_CYAN));

        // Windows: the panel's frame, with the title on a band of its own across the top. The
        // windows leave 28 units over their content for the title; the band fills them.
        HudFrame windowBg = new HudFrame(region, new Color(0.05f, 0.1f, 0.16f, 0.95f), new Color(0.02f, 0.045f, 0.08f, 0.95f))
                .border(alpha(HUD_CYAN, 0.22f)).corners(alpha(HUD_CYAN, 0.8f), 10f)
                .header(alpha(HUD_CYAN, 0.08f), alpha(HUD_CYAN, 0.35f), 28f).accent(HUD_CYAN, 36f);
        skin.add("window-bg", windowBg, Drawable.class);
        Window.WindowStyle window = new Window.WindowStyle(titleFont, HUD_CYAN, windowBg);
        skin.add("default", window);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle(
                skin.getDrawable("button-up"), skin.getDrawable("button-down"), null, font);
        button.over = skin.getDrawable("button-over");
        skin.add("default", button);

        HudFrame boxOff = new HudFrame(region, new Color(0.05f, 0.1f, 0.16f, 1f)).border(alpha(HUD_CYAN, 0.6f)).size(14f, 14f);
        HudFrame boxOn = new HudFrame(boxOff).border(HUD_CYAN).inset(HUD_CYAN, 3f);
        skin.add("default", new CheckBox.CheckBoxStyle(boxOff, boxOn, font, Color.WHITE));

        // The game's menus and settings
        TextButton.TextButtonStyle menu = new TextButton.TextButtonStyle(button);
        menu.disabledFontColor = HUD_DIM;
        menu.disabled = new HudFrame(region, new Color(0.05f, 0.08f, 0.11f, 0.85f)).border(alpha(HUD_DIM, 0.12f));
        skin.add("menu", menu);
        TextButton.TextButtonStyle toggle = new TextButton.TextButtonStyle(menu);
        toggle.checked = new HudFrame(buttonDown).fill(new Color(0.12f, 0.28f, 0.4f, 0.95f), new Color(0.08f, 0.19f, 0.28f, 0.95f));
        toggle.checkedFontColor = HUD_CYAN;
        skin.add("toggle", toggle);

        HudFrame track = new HudFrame(region, new Color(0.05f, 0.1f, 0.16f, 1f)).border(alpha(HUD_CYAN, 0.3f));
        track.setMinHeight(6f);
        HudFrame knob = new HudFrame(region, new Color(0.75f, 0.94f, 1f, 1f), HUD_CYAN).border(new Color(0.02f, 0.05f, 0.09f, 1f)).size(10f, 16f);
        Slider.SliderStyle slider = new Slider.SliderStyle(track, knob);
        HudFrame filled = new HudFrame(region, alpha(HUD_CYAN, 0.7f), alpha(HUD_CYAN, 0.45f));
        filled.setMinHeight(6f);
        slider.knobBefore = filled;
        skin.add("default-horizontal", slider);

        HudFrame field = new HudFrame(region, new Color(0.03f, 0.07f, 0.11f, 1f), new Color(0.05f, 0.1f, 0.16f, 1f))
                .border(alpha(HUD_CYAN, 0.35f));
        field.setLeftWidth(6f);
        field.setRightWidth(6f);
        field.setMinHeight(24f);
        Drawable cursor = skin.newDrawable("white", HUD_CYAN);
        cursor.setMinWidth(2f);
        Drawable selection = skin.newDrawable("white", new Color(HUD_CYAN.r, HUD_CYAN.g, HUD_CYAN.b, 0.4f));
        skin.add("default", new TextField.TextFieldStyle(font, Color.WHITE, cursor, selection, field));

        Drawable listBg = new HudFrame(region, new Color(0.03f, 0.07f, 0.12f, 0.97f)).border(alpha(HUD_CYAN, 0.35f));
        List.ListStyle list = new List.ListStyle(font, Color.WHITE, HUD_DIM, selection);
        list.background = listBg;
        skin.add("default", list);
        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScrollKnob = skin.newDrawable("white", HUD_DIM);
        scroll.vScrollKnob.setMinWidth(6f);
        skin.add("default", scroll);
        HudFrame box = new HudFrame(buttonUp);
        box.setLeftWidth(6f);
        box.setRightWidth(6f);
        box.setMinHeight(24f);
        skin.add("default", new SelectBox.SelectBoxStyle(font, Color.WHITE, box, scroll, list));

        return skin;
    }

    private static final String SCALE = "ui-scale";

    private static Color alpha(Color c, float a) {
        return new Color(c.r, c.g, c.b, a);
    }

    private static Color lighter(Color c, float by) {
        return new Color(Math.min(1f, c.r + by), Math.min(1f, c.g + by), Math.min(1f, c.b + by), c.a);
    }

    /** The UI scale {@code skin}'s fonts were last rasterised for. */
    public static float scaleOf(Skin skin) {
        return skin.has(SCALE, float[].class) ? skin.get(SCALE, float[].class)[0] : 1f;
    }

    /**
     * Rasterises the skin's fonts again for {@code scale}, in place, when it differs from theirs;
     * returns whether it did. Lay the stage out again after ({@code root.invalidateHierarchy()}).
     */
    public static boolean rescale(Skin skin, float scale) {
        float[] at = skin.get(SCALE, float[].class);
        if (Math.abs(at[0] - scale) < 0.01f) return false;
        at[0] = scale;
        HudFonts.rescale(skin.getFont("default-font"), HudFonts.REGULAR, HudFonts.BODY, scale);
        HudFonts.rescale(skin.getFont("title-font"), HudFonts.SEMIBOLD, HudFonts.TITLE, scale);
        HudFonts.rescale(skin.getFont("large-font"), HudFonts.SEMIBOLD, HudFonts.LARGE, scale);
        HudFonts.rescale(skin.getFont("display-font"), HudFonts.SEMIBOLD, HudFonts.DISPLAY, scale);
        return true;
    }
}
