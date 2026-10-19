package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.nav.GridNode;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.WeightClass;
import org.junit.jupiter.api.Test;

/** Crew are Box2D bodies inside the deck: walls and shut doors stop them, and they push each other apart softly. */
class InteriorPhysicsTest {

    private static final float DT = 1f / 60f;

    private static TestShips corridor() {
        return new TestShips(new ChassisBlueprint(
                "##############",
                "#............#",
                "##############"), false);
    }

    /** An AI crew member with no brain, so only the path moves them. */
    private static Entity walker(TestShips s, int x, int y) {
        Entity e = s.spawn(CrewRole.CREWMAN, 0, x, y);
        e.remove(BehaviorTreeComponent.class);
        return e;
    }

    private static void walk(TestShips s, Entity e, int tx, int ty) {
        Array<GridNode> path = new Array<>();
        GridPositionComponent p = s.p(e);
        assertTrue(s.crew.pathfinder(s.grid).findPath(p.tileX(), p.tileY(), tx, ty, path));
        p.setPath(path);
    }

    private static float distance(TestShips s, Entity a, Entity b) {
        GridPositionComponent p = s.p(a), q = s.p(b);
        return (float) Math.hypot(p.x - q.x, p.y - q.y);
    }

    @Test
    void twoCrewMeetingHeadOnInACorridorSqueezePast() {
        TestShips s = corridor();
        Entity a = walker(s, 1, 1), b = walker(s, 12, 1);
        walk(s, a, 12, 1);
        walk(s, b, 1, 1);
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < 60 * 15 && (s.p(a).isMoving() || s.p(b).isMoving()); i++) {
            s.engine.update(DT);
            closest = Math.min(closest, distance(s, a, b));
        }
        assertEquals(12, s.p(a).tileX(), "made it to the far end");
        assertEquals(1, s.p(b).tileX());
        assertFalse(s.p(a).pathBlocked || s.p(b).pathBlocked, "neither gave up");
        assertTrue(closest >= 2 * CrewBody.CORE_RADIUS - 0.02f, "the solid cores never overlap (" + closest + ")");
        assertTrue(closest < 2 * CrewBody.SOFT_RADIUS, "they came close enough to push (" + closest + ")");
    }

    @Test
    void aWalkerPushesPastSomeoneStandingInTheWayWhoStepsBack() {
        TestShips s = corridor();
        Entity stander = walker(s, 6, 1), w = walker(s, 1, 1);
        walk(s, w, 12, 1);
        float moved = 0f;
        for (int i = 0; i < 60 * 15 && s.p(w).isMoving(); i++) {
            s.engine.update(DT);
            moved = Math.max(moved, (float) Math.hypot(s.p(stander).x - 6.5f, s.p(stander).y - 1.5f));
        }
        assertEquals(12, s.p(w).tileX(), "got past");
        assertTrue(moved > 0.05f, "the one standing was pushed aside (" + moved + ")");
        s.run(3f);
        assertEquals(6.5f, s.p(stander).x, 0.05f, "and stepped back to their spot");
        assertEquals(1.5f, s.p(stander).y, 0.05f);
    }

    @Test
    void crewStandingOnTopOfEachOtherArePushedApart() {
        TestShips s = corridor();
        Entity a = walker(s, 5, 1), b = walker(s, 5, 1);
        s.p(b).x += 0.05f;
        s.run(2f);
        assertTrue(distance(s, a, b) >= 2 * CrewBody.CORE_RADIUS - 0.02f, "out of each other's cores (" + distance(s, a, b) + ")");
    }

    @Test
    void theAvatarSlidesAlongWallsAndStopsShortOfThem() {
        TestShips s = corridor();
        Entity me = CrewFactory.spawnPlayer(s.crew, s.ship, "Me", 3, 1);
        PlayerAvatarComponent input = me.getComponent(PlayerAvatarComponent.class);
        input.moveX = 1f;
        input.moveY = 1f;
        s.run(1f);
        GridPositionComponent p = s.p(me);
        assertEquals(2f - CrewBody.RADIUS, p.y, 0.02f, "held off the wall by its radius");
        assertTrue(p.x > 4.5f, "slid along it (" + p.x + ")");
        input.moveY = 0f;
        s.run(10f);
        assertEquals(13f - CrewBody.RADIUS, p.x, 0.02f, "stopped short of the end wall");
    }

    @Test
    void aShutDoorStopsCrewUntilItOpens() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#########");
        bp.edgeWall(4, 1, Side.LEFT, WeightClass.LIGHT, true);
        TestShips s = new TestShips(bp, false);
        s.grid.getEdge(4, 1, Side.LEFT).bulkheadSealed = true;
        Entity me = CrewFactory.spawnPlayer(s.crew, s.ship, "Me", 1, 1);
        me.getComponent(PlayerAvatarComponent.class).moveX = 1f;
        s.run(3f);
        assertEquals(4f - CrewBody.RADIUS, s.p(me).x, 0.02f, "against the shut door");
        s.grid.getEdge(4, 1, Side.LEFT).bulkheadSealed = false;
        s.run(3f);
        assertEquals(7, s.p(me).tileX(), "through once it opened");
    }

    @Test
    void downedCrewDoNotBlockTheWay() {
        TestShips s = corridor();
        Entity down = walker(s, 6, 1), w = walker(s, 1, 1);
        s.crew.applyDamage(down, s.c(down).def.maxHealth, com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.OTHER);
        assertFalse(s.c(down).isConscious());
        walk(s, w, 12, 1);
        s.run(8f);
        assertEquals(12, s.p(w).tileX());
        assertEquals(6.5f, s.p(down).x, 1e-4f, "stepped over, not shoved");
        assertNull(s.crew.physics.body(down), "lying down, they have no body");
    }
}
