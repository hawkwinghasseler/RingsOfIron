package com.hawkwing.RingsOfIron.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What goes with crew out on EVA, drawn round their craft in its own frame: each one's tether, from
 * the reel by the airlock door along the rope's sag and wrap round the hull to their hip (or, once it
 * has parted, loose, as the reel winds it in), and the puffs of their jetpack while it fires, blown
 * the other way from its push and gone in a moment. A crate one carries out trails behind them on
 * its strap, and one clipped on at a hardpoint for mounting waits there ({@code FieldRefits}).
 * Their figures are drawn with the rest of the crew ({@link DeckRenderer}).
 */
final class Spacewalkers {
    /** The line, high-visibility yellow, and its dark edge so it shows against a pale hull. */
    static final Color LINE = new Color(0.93f, 0.74f, 0.2f, 1f), LINE_EDGE = new Color(0.06f, 0.08f, 0.1f, 0.85f);
    /** The line's width and its edge's, meters. */
    static final float LINE_WIDTH = 0.045f, EDGE_WIDTH = 0.09f;
    /** The reel's housing, meters across. */
    static final float REEL = 0.14f;
    /** Pieces the line is drawn in between each two of the rope's points, along a curve through them. */
    static final int CURVE_STEPS = 4;
    /** Puffs a second while a jetpack fires, how long each lasts, seconds, and how fast it leaves, m/s. */
    static final float PUFFS_PER_SECOND = 40f, PUFF_LIFE = 0.45f, PUFF_SPEED = 2f;
    /** A part's crate, meters across (a cubic meter seen from above), and how far behind its carrier it trails. */
    static final float CRATE = 0.8f, CRATE_TRAIL = 0.6f;
    static final Color CRATE_EDGE = new Color(0.08f, 0.09f, 0.1f, 0.95f);

    /** Each craft's puffs, in its local meters: x, y, vx, vy, age per puff. */
    private final Map<Entity, FloatArray> puffs = new IdentityHashMap<>();
    private final FloatArray rope = new FloatArray(), curve = new FloatArray(), crates = new FloatArray();
    private final Array<String> crateParts = new Array<>();
    private final Vector2 local = new Vector2();
    private final Color tint = new Color();
    private long frame = -1;

    /** Moves every puff on by {@code delta} and lets the spent ones go; once a frame, however often it is called. */
    void update(float delta) {
        long id = Gdx.graphics == null ? -1 : Gdx.graphics.getFrameId();
        if (id >= 0 && id == frame) return;
        frame = id;
        for (Iterator<FloatArray> it = puffs.values().iterator(); it.hasNext(); ) {
            FloatArray p = it.next();
            for (int i = p.size - 5; i >= 0; i -= 5) {
                p.items[i] += p.items[i + 2] * delta;
                p.items[i + 1] += p.items[i + 3] * delta;
                p.items[i + 4] += delta;
                if (p.items[i + 4] >= PUFF_LIFE) p.removeRange(i, i + 4);
            }
            if (p.size == 0) it.remove();
        }
    }

    /**
     * Inside a filled batch moved to {@code ship}'s pose (its local meters): the crates clipped on at
     * its hardpoints, the tethers of its crew outside and the crates they carry, and their jetpacks'
     * puffs, new ones blown off those firing over {@code delta}.
     */
    void draw(ShapeRenderer shapes, GameWorld world, Entity ship, ShipGridComponent g, float delta) {
        float s = g.tileSize, ox = g.gridOriginLocal.x, oy = g.gridOriginLocal.y;
        CrewService crew = world.crew;
        crates.clear();
        crateParts.clear();
        world.fieldRefits.cratesOutside(ship, crates, crateParts);
        for (int i = 0; i < crateParts.size; i++) {
            crate(shapes, ox + crates.get(2 * i) * s, oy + crates.get(2 * i + 1) * s, 0f, crateParts.get(i), s);
        }
        ImmutableArray<Entity> aboard = crew.aboard(ship);
        for (int i = 0; i < aboard.size(); i++) {
            Entity e = aboard.get(i);
            EvaComponent eva = EvaSystem.of(e);
            if (eva == null || !eva.outside()) continue;
            GridPositionComponent pos = crew.position(e);
            crew.eva.ropeTiles(e, rope);
            if (rope.size >= 4) {
                curve(rope, curve);
                for (int pass = 0; pass < 2; pass++) {
                    shapes.setColor(pass == 0 ? LINE_EDGE : LINE);
                    float w = (pass == 0 ? EDGE_WIDTH : LINE_WIDTH) * s;
                    for (int k = 0; k + 3 < curve.size; k += 2) {
                        shapes.rectLine(ox + curve.get(k) * s, oy + curve.get(k + 1) * s, ox + curve.get(k + 2) * s,
                                oy + curve.get(k + 3) * s, w);
                    }
                }
                float rx = ox + rope.get(0) * s, ry = oy + rope.get(1) * s;
                shapes.setColor(LINE_EDGE);
                shapes.circle(rx, ry, REEL * s, 12);
                shapes.setColor(LINE);
                shapes.circle(rx, ry, REEL * 0.45f * s, 10);
            }
            CrewMemberComponent c = crew.crew(e);
            if (c != null && c.crate != null) {
                // Trailing behind them on its strap
                Vector2 at = pos.toShipLocal(g, local);
                float bx = -MathUtils.cos(eva.facing), by = -MathUtils.sin(eva.facing);
                float cx = at.x + bx * CRATE_TRAIL * s, cy = at.y + by * CRATE_TRAIL * s;
                shapes.setColor(LINE_EDGE);
                shapes.rectLine(at.x, at.y, cx, cy, EDGE_WIDTH * s);
                crate(shapes, cx, cy, eva.facing * MathUtils.radiansToDegrees, c.crate.commodity, s);
            }
            if (eva.jetting && delta > 0f) blow(ship, pos.toShipLocal(g, local), eva, delta, s);
        }
        FloatArray p = puffs.get(ship);
        if (p == null) return;
        for (int i = 0; i < p.size; i += 5) {
            float t = p.items[i + 4] / PUFF_LIFE;
            shapes.setColor(0.92f, 0.95f, 1f, 0.6f * (1f - t));
            shapes.circle(p.items[i], p.items[i + 1], (0.05f + 0.16f * t) * s, 10);
        }
    }

    /**
     * The line as it is drawn: a Catmull-Rom curve through the rope's points ({@code points}, x, y
     * pairs), {@link #CURVE_STEPS} pieces to each stretch between two of them, into {@code out}. A
     * line bunched up as it is wound in waves rather than zigzags, and one wrapped round a corner of
     * the hull bends round it on the outside.
     */
    static void curve(FloatArray points, FloatArray out) {
        out.clear();
        int n = points.size / 2;
        float[] p = points.items;
        for (int i = 0; i < n - 1; i++) {
            int a = 2 * Math.max(i - 1, 0), b = 2 * i, c = 2 * i + 2, d = 2 * Math.min(i + 2, n - 1);
            for (int k = 0; k < CURVE_STEPS; k++) {
                float t = k / (float) CURVE_STEPS;
                out.add(catmullRom(p[a], p[b], p[c], p[d], t), catmullRom(p[a + 1], p[b + 1], p[c + 1], p[d + 1], t));
            }
        }
        out.add(p[2 * n - 2], p[2 * n - 1]);
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        return 0.5f * (2f * p1 + (p2 - p0) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t
                + (3f * p1 - p0 - 3f * p2 + p3) * t * t * t);
    }

    /** A crate of {@code commodity} carried aboard, at ({@code x}, {@code y}) in the deck's local meters, under its carrier. */
    void crate(ShapeRenderer shapes, float x, float y, String commodity, float s) {
        crate(shapes, x, y, 0f, commodity, s);
    }

    /** A crate of {@code commodity} at ({@code x}, {@code y}), local meters, turned {@code degrees}: in the hold's colors, edged dark. */
    private void crate(ShapeRenderer shapes, float x, float y, float degrees, String commodity, float s) {
        float h = CRATE * 0.5f * s, e = EDGE_WIDTH * 0.5f * s;
        shapes.setColor(CRATE_EDGE);
        shapes.rect(x - h - e, y - h - e, h + e, h + e, 2f * (h + e), 2f * (h + e), 1f, 1f, degrees);
        shapes.setColor(DeckView.commodityColor(commodity, tint).lerp(Color.WHITE, 0.15f));
        shapes.rect(x - h, y - h, h, h, 2f * h, 2f * h, 1f, 1f, degrees);
        // Its lid's straps
        shapes.setColor(CRATE_EDGE);
        float c = MathUtils.cosDeg(degrees), sn = MathUtils.sinDeg(degrees), w = 0.04f * s;
        shapes.rectLine(x - c * h, y - sn * h, x + c * h, y + sn * h, w);
        shapes.rectLine(x + sn * h, y - c * h, x - sn * h, y + c * h, w);
    }

    /** New puffs from {@code eva}'s jetpack at {@code at}, blown against its push, for {@code delta} seconds of firing. */
    private void blow(Entity ship, Vector2 at, EvaComponent eva, float delta, float s) {
        FloatArray p = puffs.get(ship);
        if (p == null) puffs.put(ship, p = new FloatArray());
        int n = MathUtils.floor(delta * PUFFS_PER_SECOND + MathUtils.random());
        for (int k = 0; k < n; k++) {
            float spread = MathUtils.random(-0.35f, 0.35f), speed = PUFF_SPEED * MathUtils.random(0.7f, 1.2f) * s;
            float bx = -eva.jetX, by = -eva.jetY;
            float vx = (bx * MathUtils.cos(spread) - by * MathUtils.sin(spread)) * speed;
            float vy = (bx * MathUtils.sin(spread) + by * MathUtils.cos(spread)) * speed;
            p.add(at.x + bx * 0.22f * s, at.y + by * 0.22f * s);
            p.add(vx, vy, 0f);
        }
    }
}
