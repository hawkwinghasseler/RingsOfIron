package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.shipops.Tactics;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.hawkwing.RingsOfIron.game.HostileTraffic.CROSSFIRE_ANGLE;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.CROSSFIRE_SECONDS;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.STRAY_FIRE_REVIVES;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.SURRENDER_GRACE;

/**
 * {@link HostileTraffic}'s targeting: who each hostile fights in the bubble (whoever shot at it last,
 * its leader's target, the player it hunts, or for a patrol the nearest pirate), its wing's odds, and
 * whether it is caught in a crossfire.
 */
final class HostileTargeting {
    private final GameContext ctx;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final ShipDisablement disablement;
    /** Who last shot at whom, victim to shooter: a hostile fights whoever attacked it. */
    final Map<Entity, Entity> provoked = new IdentityHashMap<>();
    /** Each hostile's attackers in the bubble, and the sim second each last fired at it. */
    final Map<Entity, Map<Entity, Double>> attackers = new IdentityHashMap<>();
    private final Vector2 here = new Vector2(), there = new Vector2();

    HostileTargeting(GameContext ctx, MacroSimulation macroSim, FactionStanding standing, ShipDisablement disablement) {
        this.ctx = ctx;
        this.macroSim = macroSim;
        this.standing = standing;
        this.disablement = disablement;
    }

    /** Meters between two ships in the bubble; infinite if either has no body. */
    double range(Entity a, Entity b) {
        PhysicsBodyComponent pa = a.getComponent(PhysicsBodyComponent.class), pb = b.getComponent(PhysicsBodyComponent.class);
        if (pa == null || pa.body == null || pb == null || pb.body == null) return Double.POSITIVE_INFINITY;
        return PhysicsManager.getPosition(pa.body, here).dst(PhysicsManager.getPosition(pb.body, there));
    }

    /** Who the hostile fights in the bubble now, or null. */
    Entity enemyOf(Entity e, HostileComponent h) {
        Entity player = ctx.player();
        Entity attacker = provoked.get(e);
        if (attacker != null && attacker.getComponent(PhysicsBodyComponent.class) != null && attacker != e) return attacker;
        Entity leaders = leadersEnemy(h);
        if (leaders != null) return leaders;
        if (h.isPirate()) {
            return h.huntingPlayer() && player != null && player.getComponent(PhysicsBodyComponent.class) != null
                    ? player : null;
        }
        // A patrol: the nearest pirate of another side in the bubble
        Body body = e.getComponent(PhysicsBodyComponent.class).body;
        Entity best = null;
        float bestDistance = Float.MAX_VALUE;
        for (String id : macroSim.bubble().inBubble()) {
            Entity other = ctx.engine.getEntity(id);
            HostileComponent o = other == null ? null : other.getComponent(HostileComponent.class);
            if (o == null || !o.isPirate() || (o.faction != null && o.faction.equals(h.faction))) continue;
            PhysicsBodyComponent pb = other.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null) continue;
            float d = pb.body.getPosition().dst(body.getPosition());
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best;
    }

    /**
     * True when {@code h} flies on a leader that is in the bubble and done fighting: breaking off,
     * or beaten. A wing breaks off with its leader.
     */
    boolean leaderDone(HostileComponent h) {
        Entity leader = h.wingOf == null ? null : ctx.engine.getEntity(h.wingOf);
        HostileComponent lh = leader == null ? null : leader.getComponent(HostileComponent.class);
        if (lh == null || leader.getComponent(PhysicsBodyComponent.class) == null) return false;
        return lh.brokeOff || ShipDisablement.isDisabled(leader);
    }

    /**
     * True while a wingman whose leader is done is still on its run in: not yet fired a round, still
     * beyond its guns' reach of its enemy, and not hurt past {@link Tactics#RUN_HULL}. It came to
     * fight, and fights on alone (round 6: a frigate beat a leader at 4 km and its wingman turned for
     * home with its chaingun never fired, in 5 of 10 wing fights).
     */
    boolean stillComing(Entity e, HostileComponent h) {
        if (h.hull < Tactics.RUN_HULL) return false;
        WeaponsComponent weapons = e.getComponent(WeaponsComponent.class);
        if (weapons != null) for (WeaponMountComponent m : weapons.mounts) if (m.shotsFired > 0) return false;
        Entity enemy = enemyOf(e, h);
        return enemy != null && range(e, enemy) > Tactics.arms(e).reach;
    }

    /** True when {@code h} is a wingman whose leader, in the bubble, has its guns on {@code enemy} too. */
    boolean besideLeader(HostileComponent h, Entity enemy) {
        Entity leader = h.wingOf == null ? null : ctx.engine.getEntity(h.wingOf);
        SensorComponent sensor = leader == null || leader.getComponent(PhysicsBodyComponent.class) == null ? null
                : leader.getComponent(SensorComponent.class);
        return sensor != null && sensor.lockedTarget == enemy;
    }

    /** A wingman fights whatever its leader has its guns on in the bubble; null if nothing. */
    private Entity leadersEnemy(HostileComponent h) {
        Entity leader = h.wingOf == null ? null : ctx.engine.getEntity(h.wingOf);
        SensorComponent sensor = leader == null ? null : leader.getComponent(SensorComponent.class);
        Entity target = sensor == null ? null : sensor.lockedTarget;
        return target != null && target.getComponent(PhysicsBodyComponent.class) != null ? target : null;
    }

    /**
     * The odds a hostile fights at: its firepower times what is left of its hull, with its wing's
     * when they fight the same enemy, over the enemy's.
     */
    double odds(String id, Entity e, HostileComponent h, Entity enemy) {
        double ours = HostileTraffic.firepower(e) * h.hull;
        for (String other : macroSim.bubble().inBubble()) {
            if (other.equals(id)) continue;
            Entity o = ctx.engine.getEntity(other);
            HostileComponent oh = o == null ? null : o.getComponent(HostileComponent.class);
            if (oh == null || oh.brokeOff || !sameWing(id, h, other, oh)) continue;
            SensorComponent sensor = o.getComponent(SensorComponent.class);
            if (sensor != null && sensor.lockedTarget == enemy) ours += HostileTraffic.firepower(o) * oh.hull;
        }
        double theirs = HostileTraffic.firepower(enemy) * HostileTraffic.hull(enemy);
        return theirs <= 0 ? Double.POSITIVE_INFINITY : ours / theirs;
    }

    private static boolean sameWing(String a, HostileComponent ha, String b, HostileComponent hb) {
        return a.equals(hb.wingOf) || b.equals(ha.wingOf) || (ha.wingOf != null && ha.wingOf.equals(hb.wingOf));
    }

    /**
     * Drops a ship gone from the engine as victim and as attacker. The engine hands its entity out
     * again as a new one: left in, a hostile would go on fighting whatever came out of the pool.
     */
    void forget(Entity e) {
        provoked.remove(e);
        provoked.values().removeIf(attacker -> attacker == e);
        attackers.remove(e);
        for (Map<Entity, Double> by : attackers.values()) by.remove(e);
    }

    /** True when attackers that fired at {@code e} lately are {@link HostileTraffic#CROSSFIRE_ANGLE} apart in bearing from it. */
    boolean crossfire(Entity e, double now) {
        Map<Entity, Double> by = attackers.get(e);
        if (by == null || by.size() < 2) return false;
        Body body = e.getComponent(PhysicsBodyComponent.class).body;
        List<Float> bearings = new ArrayList<>();
        by.entrySet().removeIf(en -> now - en.getValue() > CROSSFIRE_SECONDS);
        for (Entity a : by.keySet()) {
            PhysicsBodyComponent pb = a.getComponent(PhysicsBodyComponent.class);
            if (pb == null || pb.body == null) continue;
            float dx = pb.body.getPosition().x - body.getPosition().x, dy = pb.body.getPosition().y - body.getPosition().y;
            bearings.add((float) Math.toDegrees(Math.atan2(dy, dx)));
        }
        for (int i = 0; i < bearings.size(); i++) {
            for (int j = i + 1; j < bearings.size(); j++) {
                float d = Math.abs(bearings.get(i) - bearings.get(j)) % 360f;
                if (Math.min(d, 360f - d) >= CROSSFIRE_ANGLE) return true;
            }
        }
        return false;
    }

    /**
     * As {@link HostileTraffic#onShotAt(Entity, Entity, double)}; {@code aimed} is false for stray fire (a
     * point-defense round, or one aimed at another ship), which doesn't bring a prize back.
     */
    void onShotAt(Entity victim, Entity attacker, double firedAt, boolean aimed) {
        if (victim == null || attacker == null || victim == attacker) return;
        HostileComponent h = victim.getComponent(HostileComponent.class);
        if (h == null) return;
        // Friendly fire doesn't turn a ship on its own side: round 7 found the base's two batteries,
        // moored in a line, shooting each other for the rest of the raid after one's stray rounds
        HostileComponent by = attacker.getComponent(HostileComponent.class);
        if (by != null && h.faction != null && h.faction.equals(by.faction)) return;
        // Nor does stray fire from a ship crewed by its own side: First Play's raider, its point
        // defense up, turned a gunship on itself with the rounds it missed
        if (!aimed && sameCrew(victim, attacker)) return;
        // A patrol hit by the player's stray rounds in someone else's fight doesn't turn on them,
        // unless the player stands hostile with its side (it used to follow them to the berth)
        if (!h.isPirate() && attacker == ctx.player()) {
            int side = standing.factions().index(h.faction);
            if (side < 0 || !standing.hostile(FactionStanding.PLAYER, side)) return;
        }
        if (h.surrendered) {
            // A beaten ace whose bounty paid stays struck, fired on or not (round 8: in the window Black
            // Sal surrendered, the bounty paid, and the gunship's guns, still firing, brought it back
            // into the fight and set it running)
            if (h.partDropped) return;
            // A prize stays one unless the player fires on it again. A round or missile fired before
            // it gave up doesn't count, however long it flew: a heavy missile's fragments landing
            // 15 s after a surrender lifted it on seed 12, and the prize then tore the boarding latch
            DisabledComponent d = victim.getComponent(DisabledComponent.class);
            if (attacker != ctx.player() || d != null && firedAt - d.since < SURRENDER_GRACE) return;
            if (!aimed && !STRAY_FIRE_REVIVES) return;
            h.surrendered = false;
            if (d != null && d.reason == DisabledComponent.Reason.SURRENDERED) disablement.set(victim, null);
        }
        provoked.put(victim, attacker);
        attackers.computeIfAbsent(victim, k -> new IdentityHashMap<>()).put(attacker, macroSim.now());
        if (attacker == ctx.player()) h.foughtPlayer = true;
    }

    /** True if both ships' crews fly for the same side. */
    private static boolean sameCrew(Entity a, Entity b) {
        ShipTaskBoardComponent ba = a.getComponent(ShipTaskBoardComponent.class), bb = b.getComponent(ShipTaskBoardComponent.class);
        return ba != null && bb != null && ba.faction == bb.faction;
    }
}
