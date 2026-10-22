package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.events.WorldShiftEvent;

import java.util.function.Supplier;

/**
 * A developer overlay: the physics bubble's own frame as a grid, a faint line every 100 m and a
 * brighter one every kilometer (only those, zoomed out). The bubble falls with its anchor, so the
 * grid shows motion relative to the bubble rather than to anything the player would steer by;
 * the game reads speed from {@link SpaceSnow} instead. The grid stays put through a rebase of the
 * floating origin, since it moves with the bodies.
 */
public final class BubbleGrid {
    private static final float STEP = 100f;
    private static final float MAJOR = 1000f;
    /** Wider than this, meters, only the kilometer lines are drawn. */
    private static final float MAJOR_ONLY = 3000f;
    private static final Color MINOR_COLOR = new Color(0.08f, 0.1f, 0.16f, 1f);
    private static final Color MAJOR_COLOR = new Color(0.16f, 0.2f, 0.32f, 1f);

    private final Supplier<GameWorld> world;
    private final Listener<WorldShiftEvent> onShift = (signal, e) -> shift(e.deltaX, e.deltaY);
    private PhysicsManager listeningTo;
    private boolean shown;
    /** Where the grid's lines fall: local meters plus this are multiples of {@link #STEP}, within one {@link #MAJOR}. */
    private double frameX, frameY;

    public BubbleGrid(Supplier<GameWorld> world) {
        this.world = world;
    }

    public boolean isShown() {
        return shown;
    }

    public BubbleGrid setShown(boolean shown) {
        this.shown = shown;
        follow();
        return this;
    }

    /** The grid in the view of {@code camera} (world meters, not turned), if it is shown. */
    public void draw(ShapeRenderer shapes, OrthographicCamera camera) {
        follow();
        if (!shown) return;
        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        double left = frameX + camera.position.x - halfW;
        double right = frameX + camera.position.x + halfW;
        double bottom = frameY + camera.position.y - halfH;
        double top = frameY + camera.position.y + halfH;
        float step = 2f * halfW > MAJOR_ONLY ? MAJOR : STEP;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (double gx = Math.floor(left / step) * step; gx <= right; gx += step) {
            shapes.setColor(isMajor(gx) ? MAJOR_COLOR : MINOR_COLOR);
            float x = (float) (gx - frameX);
            shapes.line(x, (float) (bottom - frameY), x, (float) (top - frameY));
        }
        for (double gy = Math.floor(bottom / step) * step; gy <= top; gy += step) {
            shapes.setColor(isMajor(gy) ? MAJOR_COLOR : MINOR_COLOR);
            float y = (float) (gy - frameY);
            shapes.line((float) (left - frameX), y, (float) (right - frameX), y);
        }
        shapes.end();
    }

    /** Listens to the current world's rebases while shown, and to none otherwise. */
    private void follow() {
        GameWorld w = world.get();
        PhysicsManager want = shown && w != null ? w.physics : null;
        if (want == listeningTo) return;
        if (listeningTo != null) listeningTo.worldShifts.remove(onShift);
        if (want != null) want.worldShifts.add(onShift);
        listeningTo = want;
    }

    /** The origin moved by ({@code dx}, {@code dy}) and every body by the opposite: the grid goes with them. */
    void shift(double dx, double dy) {
        frameX = Math.IEEEremainder(frameX + dx, MAJOR);
        frameY = Math.IEEEremainder(frameY + dy, MAJOR);
    }

    double frameX() {
        return frameX;
    }

    double frameY() {
        return frameY;
    }

    private static boolean isMajor(double coordinate) {
        return Math.abs(Math.IEEEremainder(coordinate, MAJOR)) < 0.5;
    }
}
