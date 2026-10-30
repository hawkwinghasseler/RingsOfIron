package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.hawkwing.RingsOfIron.game.Navigation;
import com.hawkwing.RingsOfIron.game.PlayerDocking;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * Flight aids drawn over the outside view, in world meters: an arrow from the ship along every
 * planned burn (the next one brightest and nearest the ship, the later ones fainter and further out,
 * each labelled with its number and countdown), and, closing on a free docking port within {@link PlayerDocking#READOUT_RANGE}, the
 * approach corridor out from that port, green while the closing speed is safe for the distance,
 * amber over it and red well over it.
 */
public class NavOverlay {
    private static final Color BURN = new Color(1f, 0.7f, 0.2f, 1f);
    private static final Color LATER_BURN = new Color(1f, 0.7f, 0.2f, 0.5f);
    /** Pixels from the ship to the next burn's arrowhead, and how much further out each later one is. */
    static final float BURN_LENGTH = 60f, LATER_STEP = 22f;
    private static final Color SAFE = new Color(0.35f, 1f, 0.45f, 0.8f);
    private static final Color FAST = new Color(1f, 0.7f, 0.2f, 0.9f);
    private static final Color DANGER = new Color(1f, 0.25f, 0.2f, 1f);

    private final GameWorld world;
    private final PlayerDocking docking;
    private final com.badlogic.gdx.utils.FloatArray burns = new com.badlogic.gdx.utils.FloatArray();
    private final Vector2 at = new Vector2();
    /** Where each burn's label goes, world meters, and its text: filled by {@link #draw}. */
    final com.badlogic.gdx.utils.FloatArray labelAt = new com.badlogic.gdx.utils.FloatArray();
    final com.badlogic.gdx.utils.Array<String> labels = new com.badlogic.gdx.utils.Array<>();

    public NavOverlay(GameWorld world, PlayerDocking docking) {
        this.world = world;
        this.docking = docking;
    }

    /**
     * Draws with {@code shapes}, whose projection is the world camera; {@code px} is meters per
     * pixel, so marks keep their size on screen. Begins and ends its own batch of lines.
     */
    public void draw(ShapeRenderer shapes, float px) {
        Entity ship = world.getPlayer();
        Body body = ship == null ? null : world.docking.bodyOf(ship);
        if (body == null) return;
        PhysicsManager.getPosition(body, at);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        world.nav.plannedBurnMarkers(burns);
        labelAt.clear();
        labels.clear();
        for (int i = burns.size / 4 - 1; i >= 0; i--) {
            float dx = burns.get(4 * i), dy = burns.get(4 * i + 1);
            float len = (BURN_LENGTH + LATER_STEP * i) * px;
            float x1 = at.x + dx * len, y1 = at.y + dy * len;
            shapes.setColor(i == 0 ? BURN : LATER_BURN);
            shapes.line(at.x + dx * 20f * px, at.y + dy * 20f * px, x1, y1);
            // The head, and on the next burn a ring to line the nose up on
            float hx = -dx * 8f * px, hy = -dy * 8f * px;
            shapes.line(x1, y1, x1 + hx - hy * 0.5f, y1 + hy + hx * 0.5f);
            shapes.line(x1, y1, x1 + hx + hy * 0.5f, y1 + hy - hx * 0.5f);
            if (i == 0) shapes.circle(x1, y1, 5f * px, 16);
            labelAt.addAll(x1 + dx * 10f * px, y1 + dy * 10f * px);
            labels.add(label(i + 1, burns.get(4 * i + 2), burns.get(4 * i + 3)));
        }
        DockingSystem.Approach a = docking.approach();
        if (a.target != null && a.distance > 1f) corridor(shapes, a, px);
        shapes.end();
    }

    /** "2: 3,084 m/s in 4m 10s" */
    public static String label(int number, float deltaV, float in) {
        return String.format(java.util.Locale.ROOT, "%d: %,.0f m/s in %s", number, deltaV, Navigation.formatTime(in));
    }

    /**
     * Writes each burn's label by its arrowhead, as {@link #draw} placed them this frame; {@code toUi}
     * turns world meters into the UI coordinates {@code batch} draws in. Call inside the batch.
     */
    public void drawLabels(com.badlogic.gdx.graphics.g2d.SpriteBatch batch, com.badlogic.gdx.graphics.g2d.BitmapFont font,
                           java.util.function.BiFunction<Float, Float, Vector2> toUi) {
        for (int i = 0; i < labels.size; i++) {
            Vector2 ui = toUi.apply(labelAt.get(2 * i), labelAt.get(2 * i + 1));
            // labels were added last burn first: the next burn's is the last one
            font.setColor(i == labels.size - 1 ? BURN : LATER_BURN);
            font.draw(batch, labels.get(i), ui.x + 4f, ui.y + 6f);
        }
        font.setColor(Color.WHITE);
        // Drawn once: a frame without the outside view leaves none behind
        labels.clear();
        labelAt.clear();
    }

    /** Out along the target port's normal past the ship, widening with distance. */
    private void corridor(ShapeRenderer shapes, DockingSystem.Approach a, float px) {
        float safe = PlayerDocking.safeSpeed(a.distance);
        shapes.setColor(a.closing <= safe ? SAFE : a.closing <= 1.5f * safe ? FAST : DANGER);
        float length = Math.max(a.distance * 1.2f, 40f * px);
        float nx = a.normalX, ny = a.normalY;
        float sx = -ny, sy = nx;
        float near = Math.max(1.5f, 2f * px), far = Math.max(near, length * 0.08f);
        float ex = a.faceX + nx * length, ey = a.faceY + ny * length;
        shapes.line(a.faceX + sx * near, a.faceY + sy * near, ex + sx * far, ey + sy * far);
        shapes.line(a.faceX - sx * near, a.faceY - sy * near, ex - sx * far, ey - sy * far);
        // Range marks every tenth of the way out
        for (int i = 1; i <= 10; i++) {
            float d = length * i / 10f, w = near + (far - near) * i / 10f;
            float cx = a.faceX + nx * d, cy = a.faceY + ny * d;
            shapes.line(cx + sx * w, cy + sy * w, cx + sx * w * 0.6f, cy + sy * w * 0.6f);
            shapes.line(cx - sx * w, cy - sy * w, cx - sx * w * 0.6f, cy - sy * w * 0.6f);
        }
    }
}
