package com.ringsofiron.uihud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;

/**
 * The HUD's framed background, drawn in shapes with the skin's 1x1 white region so it stays sharp at
 * any {@link UiScale}: a fill shaded from top to bottom, a hairline border, brighter brackets on the
 * corners, and optionally a short accent tab on the top edge, a header band (a window's title strip),
 * a bar down the left edge and a filled inset (a ticked check box). Every colour is multiplied by the
 * batch's, so a widget's tint (the menus' focus colour) and its fade still apply.
 *
 * Its padding is zero, as the flat backgrounds it replaced had, so no layout moves.
 */
public final class HudFrame extends BaseDrawable {
    private final TextureRegion white;
    private final Color fillTop = new Color(), fillBottom = new Color();
    private final Color border = new Color(Color.CLEAR);
    private final Color corner = new Color(Color.CLEAR);
    private float cornerLength;
    private final Color accent = new Color(Color.CLEAR);
    private float accentLength;
    private final Color headerFill = new Color(Color.CLEAR), headerLine = new Color(Color.CLEAR);
    private float headerHeight;
    private final Color leftBar = new Color(Color.CLEAR);
    private float leftBarWidth;
    private final Color inset = new Color(Color.CLEAR);
    private float insetMargin;
    private final Color batchColor = new Color();
    private final float[] quad = new float[20];

    public HudFrame(TextureRegion white, Color fill) {
        this(white, fill, fill);
    }

    /** Shaded from {@code top} at the top edge to {@code bottom} at the bottom. */
    public HudFrame(TextureRegion white, Color top, Color bottom) {
        this.white = white;
        fillTop.set(top);
        fillBottom.set(bottom);
    }

    /** A copy to restyle, as for a button's other states. */
    public HudFrame(HudFrame from) {
        super(from);
        white = from.white;
        fillTop.set(from.fillTop);
        fillBottom.set(from.fillBottom);
        border.set(from.border);
        corner.set(from.corner);
        cornerLength = from.cornerLength;
        accent.set(from.accent);
        accentLength = from.accentLength;
        headerFill.set(from.headerFill);
        headerLine.set(from.headerLine);
        headerHeight = from.headerHeight;
        leftBar.set(from.leftBar);
        leftBarWidth = from.leftBarWidth;
        inset.set(from.inset);
        insetMargin = from.insetMargin;
    }

    public HudFrame fill(Color top, Color bottom) {
        fillTop.set(top);
        fillBottom.set(bottom);
        return this;
    }

    /** A one-unit line round the edge. */
    public HudFrame border(Color c) {
        border.set(c);
        return this;
    }

    /** L-shaped brackets {@code length} units along each edge from every corner, over the border. */
    public HudFrame corners(Color c, float length) {
        corner.set(c);
        cornerLength = length;
        return this;
    }

    /** A two-unit tab {@code length} long on the top edge, from the left corner. */
    public HudFrame accent(Color c, float length) {
        accent.set(c);
        accentLength = length;
        return this;
    }

    /** A band {@code height} units deep across the top in {@code fill}, ruled off below by {@code line}. */
    public HudFrame header(Color fill, Color line, float height) {
        headerFill.set(fill);
        headerLine.set(line);
        headerHeight = height;
        return this;
    }

    /** A bar {@code width} units wide down the left edge. */
    public HudFrame leftBar(Color c, float width) {
        leftBar.set(c);
        leftBarWidth = width;
        return this;
    }

    /** A filled square {@code margin} units in from the edges. */
    public HudFrame inset(Color c, float margin) {
        inset.set(c);
        insetMargin = margin;
        return this;
    }

    /** The same frame with a minimum size, as a check box or a slider's knob needs. */
    public HudFrame size(float width, float height) {
        setMinWidth(width);
        setMinHeight(height);
        return this;
    }

    @Override
    public void draw(Batch batch, float x, float y, float width, float height) {
        if (width <= 0f || height <= 0f) return;
        batchColor.set(batch.getColor());
        gradient(batch, x, y, width, height);
        if (headerHeight > 0f && height > headerHeight) {
            rect(batch, headerFill, x, y + height - headerHeight, width, headerHeight);
            rect(batch, headerLine, x, y + height - headerHeight, width, 1f);
        }
        if (leftBarWidth > 0f) rect(batch, leftBar, x, y, Math.min(leftBarWidth, width), height);
        if (insetMargin > 0f && width > insetMargin * 2f && height > insetMargin * 2f) {
            rect(batch, inset, x + insetMargin, y + insetMargin, width - insetMargin * 2f, height - insetMargin * 2f);
        }
        if (border.a > 0f) {
            rect(batch, border, x, y, width, 1f);
            rect(batch, border, x, y + height - 1f, width, 1f);
            rect(batch, border, x, y + 1f, 1f, height - 2f);
            rect(batch, border, x + width - 1f, y + 1f, 1f, height - 2f);
        }
        if (cornerLength > 0f && corner.a > 0f) {
            float lx = Math.min(cornerLength, width / 2f), ly = Math.min(cornerLength, height / 2f);
            float r = x + width, t = y + height;
            rect(batch, corner, x, y, lx, 1f);
            rect(batch, corner, x, y, 1f, ly);
            rect(batch, corner, r - lx, y, lx, 1f);
            rect(batch, corner, r - 1f, y, 1f, ly);
            rect(batch, corner, x, t - 1f, lx, 1f);
            rect(batch, corner, x, t - ly, 1f, ly);
            rect(batch, corner, r - lx, t - 1f, lx, 1f);
            rect(batch, corner, r - 1f, t - ly, 1f, ly);
        }
        if (accentLength > 0f) rect(batch, accent, x, y + height - 2f, Math.min(accentLength, width), 2f);
        batch.setColor(batchColor);
    }

    private void rect(Batch batch, Color c, float x, float y, float w, float h) {
        if (c.a <= 0f || w <= 0f || h <= 0f) return;
        batch.setColor(c.r * batchColor.r, c.g * batchColor.g, c.b * batchColor.b, c.a * batchColor.a);
        batch.draw(white, x, y, w, h);
    }

    /** The fill: one quad, its top and bottom edges in their own colours. */
    private void gradient(Batch batch, float x, float y, float w, float h) {
        if (fillTop.a <= 0f && fillBottom.a <= 0f) return;
        float top = tinted(fillTop), bottom = tinted(fillBottom);
        float u = (white.getU() + white.getU2()) / 2f, v = (white.getV() + white.getV2()) / 2f;
        vertex(0, x, y, bottom, u, v);
        vertex(1, x, y + h, top, u, v);
        vertex(2, x + w, y + h, top, u, v);
        vertex(3, x + w, y, bottom, u, v);
        batch.draw(white.getTexture(), quad, 0, quad.length);
    }

    private float tinted(Color c) {
        return Color.toFloatBits(c.r * batchColor.r, c.g * batchColor.g, c.b * batchColor.b, c.a * batchColor.a);
    }

    private void vertex(int i, float x, float y, float color, float u, float v) {
        int o = i * 5;
        quad[o] = x;
        quad[o + 1] = y;
        quad[o + 2] = color;
        quad[o + 3] = u;
        quad[o + 4] = v;
    }
}
