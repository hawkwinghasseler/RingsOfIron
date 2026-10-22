package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;

import java.util.Collection;
import java.util.List;

/** What a {@link DebugOverlay} draws with and over, filled in by the game screen each frame. */
public final class OverlayView {
    /** Turns a world point into UI pixels. */
    public interface Projection {
        Vector2 worldToUi(float x, float y, Vector2 out);
    }

    public GameWorld world;
    /** The career, or null for a screen with only a world. */
    public GameScenario game;
    public ShapeRenderer shapes;
    public SpriteBatch batch;
    public BitmapFont font;
    /** True in the deck view, false outside. */
    public boolean deckView;
    /** World meters one screen pixel covers. */
    public float metersPerPixel;
    /** The world meters on screen. */
    public final Rectangle worldBounds = new Rectangle();
    /** The UI's size in pixels. */
    public float uiWidth, uiHeight;
    public Projection projection;
    /** The world camera's matrix, for a renderer that takes one (Box2D's debug renderer). */
    public final Matrix4 worldMatrix = new Matrix4();
    /** The craft whose decks are drawn, in the deck view. */
    public Collection<Entity> decks = List.of();
    /** Seconds since the last frame. */
    public float delta;

    public Vector2 worldToUi(float x, float y, Vector2 out) {
        return projection.worldToUi(x, y, out);
    }
}
