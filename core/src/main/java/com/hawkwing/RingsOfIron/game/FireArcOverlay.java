package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.fire.HitChance;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;

/**
 * The firing arcs of the guns the player fires, drawn over the outside view in world meters: those
 * in hand at the weapons console or a gun, or every gun while the player flies with a target
 * locked. Each is a fan from its muzzle across its arc, banded out by range and tinted by
 * {@link HitChance} (the bands widen outward, {@link #edge}): green where a shot (a second's worth, for a fast gun) all but surely hits the
 * target even dodging, red out where it would hardly hit even a still one, yellow between. Against
 * the locked target, or with none a corvette-sized ship ({@link #REFERENCE_RADIUS}). It runs out to
 * the red end, or as far as the round flies.
 */
public class FireArcOverlay {
    /** Range bands across each fan. */
    static final int BANDS = 16;
    /** Size of the ship the arcs are for with nothing locked, meters. */
    static final float REFERENCE_RADIUS = 12f;
    /** How strong the tint is: a subtle wash over the view. */
    static final float FILL_ALPHA = 0.07f, EDGE_ALPHA = 0.25f;
    private static final Color RED = new Color(1f, 0.25f, 0.2f, 1f);
    private static final Color YELLOW = new Color(1f, 0.85f, 0.25f, 1f);
    private static final Color GREEN = new Color(0.35f, 1f, 0.45f, 1f);

    private final GameWorld world;
    private final Array<WeaponMountComponent> shown = new Array<>();
    private final float[] tints = new float[BANDS];
    private final Color color = new Color();

    public FireArcOverlay(GameWorld world) {
        this.world = world;
    }

    /** The player's locked target, or null. */
    private Entity locked() {
        Entity ship = world.getPlayer();
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        return sensor == null ? null : sensor.lockedTarget;
    }

    /** The guns whose arcs are drawn now, into {@code out}. */
    void shown(Array<WeaponMountComponent> out) {
        out.clear();
        Entity ship = world.getPlayer();
        WeaponsComponent weapons = ship == null ? null : ship.getComponent(WeaponsComponent.class);
        if (weapons == null) return;
        boolean onGuns = world.bridge.playerOnGuns();
        boolean flyingArmed = !onGuns && world.bridge.playerAtHelm() && locked() != null;
        if (!onGuns && !flyingArmed) return;
        for (WeaponMountComponent m : weapons.mounts) {
            if (!PlayerGunnery.isMainGun(m)) continue;
            if (!onGuns || PlayerGunnery.inHand(world, m)) out.add(m);
        }
    }

    /**
     * Draws with {@code shapes}, whose projection is the world camera; {@code px} is meters per
     * pixel. Begins and ends its own batches.
     */
    public void draw(ShapeRenderer shapes, float px) {
        shown(shown);
        if (shown.size == 0) return;
        Body body = world.docking.bodyOf(world.getPlayer());
        if (body == null) return;
        Entity target = locked();
        WeaponControlSystem control = world.weaponControl;
        float radius = target == null ? REFERENCE_RADIUS : control.radiusOf(target);
        float evasion = target == null ? HitChance.DEFAULT_EVASION : control.evasionOf(target);
        float angle = body.getAngle();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        for (int pass = 0; pass < 2; pass++) {
            shapes.begin(pass == 0 ? ShapeRenderer.ShapeType.Filled : ShapeRenderer.ShapeType.Line);
            for (WeaponMountComponent m : shown) {
                float far = reach(control, m, radius);
                if (far <= 0f) continue;
                tints(control, m, radius, evasion, far, tints);
                fan(shapes, m, angle, far, pass == 0);
            }
            shapes.end();
        }
    }

    /** How far out a fan runs: where a shot has hardly a chance on a still target, or as far as the round flies. */
    static float reach(WeaponControlSystem control, WeaponMountComponent m, float radius) {
        WeaponDef def = m.def;
        ProjectileDef round = control.roundOf(def);
        float beam = WeaponControlSystem.beamReach(def);
        return HitChance.effectiveRange(def, round, beam, m.gunnerySkill, radius, 0f, HitChance.RED);
    }

    /** Each band's tint, 0 (red) .. 1 (green), at its middle, out to {@code far}. */
    static void tints(WeaponControlSystem control, WeaponMountComponent m, float radius, float evasion, float far,
                      float[] out) {
        WeaponDef def = m.def;
        ProjectileDef round = control.roundOf(def);
        float beam = WeaponControlSystem.beamReach(def);
        for (int i = 0; i < out.length; i++) {
            float range = 0.5f * (edge(far, i) + edge(far, i + 1));
            float dodging = HitChance.rated(def, HitChance.perShot(def, round, beam, m.gunnerySkill, range, radius, evasion));
            float still = HitChance.rated(def, HitChance.perShot(def, round, beam, m.gunnerySkill, range, radius, 0f));
            out[i] = HitChance.tint(dodging, still);
        }
    }

    private void fan(ShapeRenderer shapes, WeaponMountComponent m, float bodyAngle, float far, boolean fill) {
        float arc = Math.min(360f, m.def.firingArcDeg) * MathUtils.degreesToRadians;
        float from = bodyAngle + m.facing - 0.5f * arc;
        int steps = Math.max(8, MathUtils.ceil(arc / MathUtils.PI2 * 72f));
        float x = m.muzzle.x, y = m.muzzle.y;
        for (int b = 0; b < BANDS; b++) {
            float r0 = edge(far, b), r1 = edge(far, b + 1);
            tint(tints[b], color);
            if (fill) {
                shapes.setColor(color.r, color.g, color.b, FILL_ALPHA);
                for (int s = 0; s < steps; s++) {
                    float a0 = from + arc * s / steps, a1 = from + arc * (s + 1) / steps;
                    float c0 = MathUtils.cos(a0), s0 = MathUtils.sin(a0), c1 = MathUtils.cos(a1), s1 = MathUtils.sin(a1);
                    shapes.triangle(x + c0 * r0, y + s0 * r0, x + c0 * r1, y + s0 * r1, x + c1 * r1, y + s1 * r1);
                    shapes.triangle(x + c0 * r0, y + s0 * r0, x + c1 * r1, y + s1 * r1, x + c1 * r0, y + s1 * r0);
                }
            } else {
                // The arc's edges, and its outer rim
                shapes.setColor(color.r, color.g, color.b, EDGE_ALPHA);
                if (arc < MathUtils.PI2 - 1e-3f) {
                    float to = from + arc;
                    shapes.line(x + MathUtils.cos(from) * r0, y + MathUtils.sin(from) * r0,
                            x + MathUtils.cos(from) * r1, y + MathUtils.sin(from) * r1);
                    shapes.line(x + MathUtils.cos(to) * r0, y + MathUtils.sin(to) * r0,
                            x + MathUtils.cos(to) * r1, y + MathUtils.sin(to) * r1);
                }
                if (b == BANDS - 1) {
                    for (int s = 0; s < steps; s++) {
                        float a0 = from + arc * s / steps, a1 = from + arc * (s + 1) / steps;
                        shapes.line(x + MathUtils.cos(a0) * r1, y + MathUtils.sin(a0) * r1,
                                x + MathUtils.cos(a1) * r1, y + MathUtils.sin(a1) * r1);
                    }
                }
            }
        }
    }

    /**
     * Where band {@code b} starts, out of {@code far}: the bands widen outward, on the square of the
     * band count, so a gun whose round flies 100 km still shows the few km it fights at.
     */
    static float edge(float far, int b) {
        float f = (float) b / BANDS;
        return far * f * f;
    }

    /** Red at 0, yellow at 0.5, green at 1. */
    static Color tint(float t, Color out) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t < 0.5f ? out.set(RED).lerp(YELLOW, 2f * t) : out.set(YELLOW).lerp(GREEN, 2f * t - 1f);
    }
}
