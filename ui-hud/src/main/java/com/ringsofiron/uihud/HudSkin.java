package com.ringsofiron.uihud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * Builds the HUD's Scene2D {@link Skin} in code: a 1x1 white texture tinted into panels and
 * borders, and the default LibGDX font. Swap for an atlas-based skin from modding-data later;
 * widgets only look styles up by the names defined here.
 *
 * Style names: Label "default", "title", "dim", "alert", "warn", "prompt"; Window "default";
 * TextButton "default"; drawables "white", "panel", "panel-border", "prompt-bg".
 */
public final class HudSkin {
    public static final Color HUD_CYAN = new Color(0.45f, 0.85f, 1f, 1f);
    public static final Color HUD_DIM = new Color(0.55f, 0.65f, 0.75f, 1f);
    public static final Color HUD_ALERT = new Color(1f, 0.3f, 0.25f, 1f);
    public static final Color HUD_WARN = new Color(1f, 0.7f, 0.2f, 1f);
    public static final Color PANEL = new Color(0.02f, 0.05f, 0.09f, 0.85f);

    private HudSkin() {}

    public static Skin create() {
        Skin skin = new Skin();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture white = new Texture(pixmap);
        pixmap.dispose();
        skin.add("white-texture", white);
        skin.add("white", new TextureRegion(white));

        BitmapFont font = new BitmapFont();
        font.getData().markupEnabled = false;
        skin.add("default-font", font);

        skin.add("panel", skin.newDrawable("white", PANEL), Drawable.class);
        skin.add("panel-border", skin.newDrawable("white", new Color(HUD_CYAN.r, HUD_CYAN.g, HUD_CYAN.b, 0.5f)), Drawable.class);
        skin.add("prompt-bg", skin.newDrawable("white", new Color(0f, 0f, 0f, 0.6f)), Drawable.class);
        skin.add("button-up", skin.newDrawable("white", new Color(0.1f, 0.2f, 0.3f, 0.9f)), Drawable.class);
        skin.add("button-over", skin.newDrawable("white", new Color(0.15f, 0.3f, 0.45f, 0.9f)), Drawable.class);
        skin.add("button-down", skin.newDrawable("white", new Color(0.25f, 0.45f, 0.6f, 0.9f)), Drawable.class);

        skin.add("default", new Label.LabelStyle(font, Color.WHITE));
        skin.add("title", new Label.LabelStyle(font, HUD_CYAN));
        skin.add("dim", new Label.LabelStyle(font, HUD_DIM));
        skin.add("alert", new Label.LabelStyle(font, HUD_ALERT));
        skin.add("warn", new Label.LabelStyle(font, HUD_WARN));
        Label.LabelStyle prompt = new Label.LabelStyle(font, Color.WHITE);
        prompt.background = skin.getDrawable("prompt-bg");
        skin.add("prompt", prompt);

        Window.WindowStyle window = new Window.WindowStyle(font, HUD_CYAN, skin.newDrawable("white", new Color(0.03f, 0.07f, 0.12f, 0.92f)));
        skin.add("default", window);

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle(
                skin.getDrawable("button-up"), skin.getDrawable("button-down"), null, font);
        button.over = skin.getDrawable("button-over");
        skin.add("default", button);

        return skin;
    }
}
