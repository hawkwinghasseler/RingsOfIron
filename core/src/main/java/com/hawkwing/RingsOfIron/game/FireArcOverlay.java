package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.ProjectileDef;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.fire.HitChance;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.systems.WeaponControlSystem;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * The guns the player fires, drawn over the outside view in world meters: those in hand at the
 * weapons console or a gun, or every gun while the player flies with a target locked.
 *
 * <p>Each gun's firing arc is two faint edges a short way out from its muzzle, so it keeps clear of
 * the play area. With a target locked, two lines run from the muzzle past either side of the
 * predicted intercept point: where the target would be if it burned that way to dodge while the
 * round flew ({@code a t^2 / 2} out, beyond its half-width). They are drawn in the color of the gun's
 * chance to hit ({@link HitChance}): green where a shot (a second's worth, for a fast gun) all but
 * surely hits even a dodging target, red where it would hardly hit even a still one, yellow between.
 */
public class FireArcOverlay {
    /** How far the arc's edges reach from the muzzle, pixels. */
    static final float EDGE_PIXELS = 90f;
    private static final Color EDGE = new Color(0.75f, 0.85f, 0.95f, 0.3f);
    /** How strong the dodge lines are. */
    static final float LINE_ALPHA = 0.85f;
    private static final Color RED = new Color(1f, 0.25f, 0.2f, 1f);
    private static final Color YELLOW = new Color(1f, 0.85f, 0.25f, 1f);
    private static final Color GREEN = new Color(0.35f, 1f, 0.45f, 1f);

    private final GameWorld world;
    private final Array<WeaponMountComponent> shown = new Array<>();
    private final Color color = new Color();
    private final Dodge dodge = new Dodge();
    private final Vector2 tmp = new Vector2();
    private final Vector2 shipVel = new Vector2();

    public FireArcOverlay(GameWorld world) {
        this.world = world;
    }

    /** One gun's dodge lines on a target: where they end, and the chance to hit that colors them. */
    static final class Dodge {
        /** The predicted intercept point, and the two ends either side of it, world meters. */
        final Vector2 intercept = new Vector2(), left = new Vector2(), right = new Vector2();
        /** Range to the intercept point, meters, and the round's flight time to it, seconds. */
        float range, flight;
        /** 0 (red) .. 1 (green), from {@link HitChance#tint}. */
        float tint;
    }

    /** The player's locked target, or null. */
    private Entity locked() {
        Entity ship = world.getPlayer();
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        return sensor == null ? null : sensor.lockedTarget;
    }

    /** The guns drawn now, into {@code out}. */
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
     * pixel. Begins and ends its own batch of lines.
     */
    public void draw(ShapeRenderer shapes, float px) {
        shown(shown);
        if (shown.size == 0) return;
        Body body = world.docking.bodyOf(world.getPlayer());
        if (body == null) return;
        Entity target = locked();
        PhysicsManager.getVelocity(body, shipVel);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (WeaponMountComponent m : shown) {
            edges(shapes, m, body.getAngle(), EDGE_PIXELS * px);
            if (target == null || !dodge(m, target, shipVel, dodge)) continue;
            tint(dodge.tint, color);
            shapes.setColor(color.r, color.g, color.b, LINE_ALPHA);
            shapes.line(m.muzzle.x, m.muzzle.y, dodge.left.x, dodge.left.y);
            shapes.line(m.muzzle.x, m.muzzle.y, dodge.right.x, dodge.right.y);
        }
        shapes.end();
    }

    /**
     * Works out {@code m}'s dodge lines on {@code target} into {@code out}, from the ship's track of it
     * and the ship's own velocity {@code shipVel}: false with no track. The intercept is where a round
     * fired now meets the target holding its course; the ends sit either side of it, square to the
     * line of fire, by the target's half-width plus how far its best burn takes it in the flight time.
     */
    boolean dodge(WeaponMountComponent m, Entity target, Vector2 shipVel, Dodge out) {
        Entity ship = world.getPlayer();
        SensorComponent sensor = ship == null ? null : ship.getComponent(SensorComponent.class);
        Track track = sensor == null ? null : sensor.trackFor(target);
        if (track == null) return false;
        WeaponControlSystem control = world.weaponControl;
        WeaponDef def = m.def;
        ProjectileDef round = control.roundOf(def);
        float age = world.sensors.ageOf(track);
        Vector2 at = track.positionAt(age, out.left).sub(m.muzzle);
        Vector2 closing = track.velocityAt(age, out.right).sub(shipVel);
        float speed = round == null ? 0f : round.muzzleSpeed;
        float t = 0f;
        // A few passes of where it will be when the round gets there
        for (int i = 0; i < 4 && speed > 0f; i++) t = tmp.set(at).mulAdd(closing, t).len() / speed;
        out.flight = t;
        out.intercept.set(at).mulAdd(closing, t);
        out.range = out.intercept.len();
        float radius = control.radiusOf(target), evasion = control.evasionOf(target);
        float aside = HitChance.BROADSIDE_SHARE * radius + 0.5f * evasion * t * t;
        tmp.set(out.intercept).nor().rotate90(1).scl(aside);
        out.intercept.add(m.muzzle);
        out.left.set(out.intercept).add(tmp);
        out.right.set(out.intercept).sub(tmp);
        float beam = WeaponControlSystem.beamReach(def);
        float dodging = HitChance.rated(def, HitChance.perShot(def, round, beam, m.gunnerySkill, out.range, radius, evasion));
        float still = HitChance.rated(def, HitChance.perShot(def, round, beam, m.gunnerySkill, out.range, radius, 0f));
        out.tint = HitChance.tint(dodging, still);
        return true;
    }

    /** The arc's two edges, {@code length} out from the muzzle; nothing for a gun that turns all the way round. */
    private static void edges(ShapeRenderer shapes, WeaponMountComponent m, float bodyAngle, float length) {
        if (m.def.firingArcDeg >= 360f) return;
        float half = 0.5f * m.def.firingArcDeg * MathUtils.degreesToRadians;
        float x = m.muzzle.x, y = m.muzzle.y;
        shapes.setColor(EDGE);
        for (int side = -1; side <= 1; side += 2) {
            float a = bodyAngle + m.facing + side * half;
            shapes.line(x, y, x + MathUtils.cos(a) * length, y + MathUtils.sin(a) * length);
        }
    }

    /** Red at 0, yellow at 0.5, green at 1. */
    static Color tint(float t, Color out) {
        t = MathUtils.clamp(t, 0f, 1f);
        return t < 0.5f ? out.set(RED).lerp(YELLOW, 2f * t) : out.set(YELLOW).lerp(GREEN, 2f * t - 1f);
    }
}
