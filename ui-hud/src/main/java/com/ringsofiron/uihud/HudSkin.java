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
 * TextField, SelectBox, List and ScrollPane "default"; drawables "white", "panel", "panel-border", "prompt-bg".
 */
public final class HudSkin {
    public static final Color HUD_CYAN = new Color(0.45f, 0.85f, 1f, 1f);
    public static final Color HUD_DIM = new Color(0.55f, 0.65f, 0.75f, 1f);
    public static final Color HUD_ALERT = new Color(1f, 0.3f, 0.25f, 1f);
    public static final Color HUD_WARN = new Color(1f, 0.7f, 0.2f, 1f);
    public static final Color PANEL = new Color(0.02f, 0.05f, 0.09f, 0.85f);

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

        skin.add("panel", skin.newDrawable("white", PANEL), Drawable.class);
        skin.add("panel-border", skin.newDrawable("white", new Color(HUD_CYAN.r, HUD_CYAN.g, HUD_CYAN.b, 0.5f)), Drawable.class);
        skin.add("prompt-bg", skin.newDrawable("white", new Color(0f, 0f, 0f, 0.6f)), Drawable.class);
        skin.add("button-up", skin.newDrawable("white", new Color(0.1f, 0.2f, 0.3f, 0.9f)), Drawable.class);
        skin.add("button-over", skin.newDrawable("white", new Color(0.15f, 0.3f, 0.45f, 0.9f)), Drawable.class);
        skin.add("button-down", skin.newDrawable("white", new Color(0.25f, 0.45f, 0.6f, 0.9f)), Drawable.class);

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

        Window.WindowStyle window = new Window.WindowStyle(titleFont, HUD_CYAN, skin.newDrawable("white", new Color(0.03f, 0.07f, 0.12f, 0.92f)));
        skin.add("default", window);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle(
                skin.getDrawable("button-up"), skin.getDrawable("button-down"), null, font);
        button.over = skin.getDrawable("button-over");
        skin.add("default", button);

        Drawable boxOff = skin.newDrawable("white", new Color(0.1f, 0.2f, 0.3f, 1f));
        Drawable boxOn = skin.newDrawable("white", HUD_CYAN);
        boxOff.setMinWidth(14f);
        boxOff.setMinHeight(14f);
        boxOn.setMinWidth(14f);
        boxOn.setMinHeight(14f);
        skin.add("default", new CheckBox.CheckBoxStyle(boxOff, boxOn, font, Color.WHITE));

        // The game's menus and settings
        TextButton.TextButtonStyle menu = new TextButton.TextButtonStyle(button);
        menu.disabledFontColor = HUD_DIM;
        menu.disabled = skin.newDrawable("white", new Color(0.06f, 0.1f, 0.14f, 0.9f));
        skin.add("menu", menu);
        TextButton.TextButtonStyle toggle = new TextButton.TextButtonStyle(menu);
        toggle.checked = skin.getDrawable("button-down");
        toggle.checkedFontColor = HUD_CYAN;
        skin.add("toggle", toggle);

        Drawable track = skin.newDrawable("white", new Color(0.1f, 0.2f, 0.3f, 1f));
        track.setMinHeight(6f);
        Drawable knob = skin.newDrawable("white", HUD_CYAN);
        knob.setMinWidth(10f);
        knob.setMinHeight(16f);
        Slider.SliderStyle slider = new Slider.SliderStyle(track, knob);
        Drawable filled = skin.newDrawable("white", new Color(HUD_CYAN.r, HUD_CYAN.g, HUD_CYAN.b, 0.5f));
        filled.setMinHeight(6f);
        slider.knobBefore = filled;
        skin.add("default-horizontal", slider);

        Drawable field = skin.newDrawable("white", new Color(0.05f, 0.1f, 0.16f, 1f));
        field.setLeftWidth(6f);
        field.setRightWidth(6f);
        field.setMinHeight(24f);
        Drawable cursor = skin.newDrawable("white", HUD_CYAN);
        cursor.setMinWidth(2f);
        Drawable selection = skin.newDrawable("white", new Color(HUD_CYAN.r, HUD_CYAN.g, HUD_CYAN.b, 0.4f));
        skin.add("default", new TextField.TextFieldStyle(font, Color.WHITE, cursor, selection, field));

        Drawable listBg = skin.newDrawable("white", new Color(0.03f, 0.07f, 0.12f, 0.97f));
        List.ListStyle list = new List.ListStyle(font, Color.WHITE, HUD_DIM, selection);
        list.background = listBg;
        skin.add("default", list);
        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScrollKnob = skin.newDrawable("white", HUD_DIM);
        scroll.vScrollKnob.setMinWidth(6f);
        skin.add("default", scroll);
        Drawable box = skin.newDrawable("white", new Color(0.1f, 0.2f, 0.3f, 0.9f));
        box.setLeftWidth(6f);
        box.setRightWidth(6f);
        box.setMinHeight(24f);
        skin.add("default", new SelectBox.SelectBoxStyle(font, Color.WHITE, box, scroll, list));

        return skin;
    }

    private static final String SCALE = "ui-scale";

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
