package com.ringsofiron.uihud.text;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.ViewportManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The HUD's text drawn straight to the screen beside the stage, in the skin's fonts: the status
 * column top right, and the panels over the game (the notice log, the controls, the crash screen).
 * The game says what the text is; this lays it out and draws it, in UI units.
 */
public final class HudText {
    /** The status column's panel, top right: its width, and the text's inside it once padded. */
    public static final float STATUS_WIDTH = 420f, STATUS_PAD = 8f, STATUS_TEXT = STATUS_WIDTH - 2f * STATUS_PAD;

    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final ViewportManager viewports;
    /** The HUD skin's body, banner and display fonts, rasterised for the UI scale. */
    private final BitmapFont font, largeFont, displayFont;
    private final GlyphLayout layout = new GlyphLayout();
    /** Where the status column's text ended last frame, UI y: off-screen marker names keep above or below it. */
    private float statusBottom = Float.MAX_VALUE;
    /** This frame's status column: its text's left edge and top. */
    private float statusLeft, statusTop;

    public HudText(ShapeRenderer shapes, SpriteBatch batch, ViewportManager viewports, Skin skin) {
        this.shapes = shapes;
        this.batch = batch;
        this.viewports = viewports;
        this.font = skin.getFont("default-font");
        this.largeFont = skin.getFont("large-font");
        this.displayFont = skin.getFont("display-font");
    }

    public SpriteBatch batch() {
        return batch;
    }

    public BitmapFont font() {
        return font;
    }

    public BitmapFont largeFont() {
        return largeFont;
    }

    /** The layout the text was last set into; set it again before drawing it. */
    public GlyphLayout layout() {
        return layout;
    }

    // --- The status column ---------------------------------------------------------------

    /** Where the status column's text ended last frame, UI y; {@link Float#MAX_VALUE} before the first. */
    public float statusBottom() {
        return statusBottom;
    }

    /** The status column panel's left edge, UI x. */
    public float statusPanelX() {
        return viewports.getUiViewport().getWorldWidth() - STATUS_WIDTH - 10f;
    }

    public float statusLeft() {
        return statusLeft;
    }

    public float statusTop() {
        return statusTop;
    }

    /**
     * Starts the status column: top right, as ui-hud's flight panel holds the top left. The column
     * sits on a panel like the HUD's, 10 units in from the corner, as tall as its text reached last
     * frame. Leaves the batch begun in UI units; {@link #endStatus} ends it.
     */
    public void beginStatus() {
        viewports.applyUi();
        float panelX = statusPanelX();
        float panelTop = viewports.getUiViewport().getWorldHeight() - 10f;
        if (statusBottom < panelTop) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapes.setProjectionMatrix(viewports.getUiCamera().combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(HudSkin.PANEL);
            shapes.rect(panelX, statusBottom + 4f, STATUS_WIDTH, panelTop - statusBottom - 4f);
            shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        statusLeft = panelX + STATUS_PAD;
        statusTop = panelTop - STATUS_PAD;
    }

    /** Ends the status column, its text having reached down to {@code below}. */
    public void endStatus(float below) {
        statusBottom = below;
        batch.end();
    }

    /** Draws one line of the status column in the font's color, wrapped to the column; returns the y below it. */
    public float statusLine(String text, float x, float y) {
        layout.setText(font, text, font.getColor(), STATUS_TEXT, Align.left, true);
        font.draw(batch, layout, x, y);
        return y - Math.max(20f, layout.height + 6f);
    }

    /** Draws {@code text} in {@code color}, wrapped to the column; returns how tall it was. */
    public float wrapped(String text, Color color, float x, float y) {
        layout.setText(font, text, color, STATUS_TEXT, Align.left, true);
        font.draw(batch, layout, x, y);
        return layout.height;
    }

    // --- The panels over the game --------------------------------------------------------

    /** The crash screen: {@code title} large and red in the middle, {@code detail} under it, then what the keys do. */
    public void drawCrash(String title, String detail, String keys) {
        viewports.applyUi();
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        float w = viewports.getUiViewport().getWorldWidth();
        float h = viewports.getUiViewport().getWorldHeight();
        displayFont.setColor(Color.RED);
        displayFont.draw(batch, title, 0f, h / 2f + 40f, w, Align.center, false);
        largeFont.setColor(Color.WHITE);
        largeFont.draw(batch, detail, 0f, h / 2f - 10f, w, Align.center, false);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, keys, 0f, h / 2f - 50f, w, Align.center, false);
        batch.end();
    }

    /**
     * The notice log: {@code log}, newest first, from {@code scroll} entries back, each with when it
     * came and in its own color. Returns the scroll, kept within the log.
     */
    public <T> int drawLog(String keys, List<T> log, int scroll, Function<T, String> when, Function<T, Color> color,
                           Function<T, String> text) {
        viewports.applyUi();
        float[] p = panel(720f, 560f);
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Notice log", p[0], p[1]);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, keys, p[0], p[1], p[2], Align.right, false);
        scroll = Math.min(scroll, Math.max(0, log.size() - 1));
        float y = p[1] - 30f;
        if (log.isEmpty()) {
            font.draw(batch, "Nothing yet.", p[0], y);
        }
        for (int i = scroll; i < log.size() && y > p[3] + 20f; i++) {
            T n = log.get(i);
            font.setColor(Color.GRAY);
            font.draw(batch, when.apply(n), p[0], y);
            font.setColor(color.apply(n));
            layout.setText(font, text.apply(n), font.getColor(), p[2] - 110f, Align.left, true);
            font.draw(batch, layout, p[0] + 110f, y);
            y -= layout.height + 8f;
        }
        batch.end();
        return scroll;
    }

    /**
     * The controls panel: each section's title over its lines of key and what it does. As many
     * columns as fit the window, filled top to bottom; a section carries on in the next column
     * when it runs out of room, and the key column is as wide as the longest key name.
     *
     * @param sections each section's title, and its lines as {key, what it does}, in order
     */
    public void drawHelp(String keys, Map<String, List<String[]>> sections) {
        viewports.applyUi();
        float[] p = panel(1240f, 720f);
        batch.setProjectionMatrix(viewports.getUiCamera().combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "Controls", p[0], p[1]);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, keys, p[0], p[1], p[2], Align.right, false);
        // Columns enough for every line at the window's height, as many as the widest key leaves room for
        float keyWidth = 0f;
        int lines = 0;
        for (Map.Entry<String, List<String[]>> e : sections.entrySet()) {
            lines += e.getValue().size() + 2;
            for (String[] h : e.getValue()) {
                layout.setText(font, h[0]);
                keyWidth = Math.max(keyWidth, layout.width);
            }
        }
        keyWidth += 12f;
        float top = p[1] - 30f, rows = Math.max(1f, (top - p[3]) / 16f);
        int columns = Math.max(1, Math.min((int) Math.ceil(lines / rows), (int) (p[2] / (keyWidth + 120f))));
        float colWidth = (p[2] - 20f * (columns - 1)) / columns;
        float x = p[0], y = top;
        int column = 0;
        outer:
        for (Map.Entry<String, List<String[]>> e : sections.entrySet()) {
            // A heading with no room for a line under it starts the next column
            if (y - 36f < p[3]) {
                if (++column >= columns) break;
                x += colWidth + 20f;
                y = top;
            }
            font.setColor(Color.SKY);
            font.draw(batch, e.getKey(), x, y);
            y -= 20f;
            for (String[] h : e.getValue()) {
                if (y - 16f < p[3]) {
                    if (++column >= columns) break outer;
                    x += colWidth + 20f;
                    y = top;
                }
                font.setColor(Color.WHITE);
                font.draw(batch, h[0], x, y, keyWidth, Align.left, false);
                // A long description wraps under itself rather than running into the next column
                layout.setText(font, h[1], Color.LIGHT_GRAY, colWidth - keyWidth, Align.left, true);
                font.draw(batch, layout, x + keyWidth, y);
                y -= Math.max(16f, layout.height + 4f);
            }
            y -= 6f;
        }
        batch.end();
    }

    /** A panel in the middle of the screen, dimming what is behind it; returns its {x, top, width, bottom} for text. */
    private float[] panel(float width, float height) {
        float w = viewports.getUiViewport().getWorldWidth();
        float h = viewports.getUiViewport().getWorldHeight();
        width = Math.min(width, w - 40f);
        height = Math.min(height, h - 40f);
        float x = (w - width) / 2f, y = (h - height) / 2f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(viewports.getUiCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // The HUD round it dimmed, so its titles don't read as part of the panel
        shapes.setColor(0f, 0f, 0f, 0.5f);
        shapes.rect(0f, 0f, w, h);
        // Dark enough that the HUD under it doesn't read through the text
        shapes.setColor(0f, 0f, 0f, 0.92f);
        shapes.rect(x, y, width, height);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.4f, 0.6f, 0.8f, 1f);
        shapes.rect(x, y, width, height);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        return new float[]{x + 16f, y + height - 16f, width - 32f, y + 16f};
    }
}
