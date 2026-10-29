package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SuitOutfit;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.Suit;
import org.junit.jupiter.api.Test;

/**
 * Work outside that needs a part ({@link OutsideTask.Job#part}): its crate lifted out of the hold,
 * carried out on EVA (its mass with the carrier's), set down at the work, and what the work took
 * off brought back in to the hold; a part carried back in when called in goes back in the hold; and
 * work put in stands for the next trip. The ship of {@link EvaRepairTest#ship()} with nothing
 * wrecked, two cargo bays at its left end.
 */
class EvaCrateTest {
    static final String WIDGET = "part:widget", OLD = "part:old";

    TestShips s;
    CargoHoldComponent hold;
    Mounting job;
    OutsideTask task;

    void ship(float seconds) {
        s = new TestShips(
                "############",
                "#..........#",
                "#..........#",
                "#..........#",
                "############",
                "############");
        s.grid.placeMount(new MountFootprint(MountFootprint.MountKind.PORT, MountFootprint.MountSize.M, 3, 4, 0));
        assertNotNull(SuitOutfit.placeLocker(s.grid, Suit.Kind.EVA, 9, 4, 2), "an EVA locker by the port");
        hold = new CargoHoldComponent(s.grid.tileSize, new int[]{1, 2, 1, 3});
        s.ship.add(hold);
        assertEquals(1.0, hold.load(WIDGET, 1, 1.0, 250.0), 1e-9);
        // On the hull over the ship's right end
        job = new Mounting(10, 6, seconds);
        task = new OutsideTask(CrewTask.Kind.MOUNT_PART, 10, 4, job);
    }

    /** Runs {@code seconds}, the job posted while it is open, as the game posts its work outside each frame. */
    void run(float seconds) {
        for (float t = 0; t < seconds; t += 0.1f) {
            if (job.isOpen() && !task.isActive()) task = new OutsideTask(CrewTask.Kind.MOUNT_PART, 10, 4, job);
            if (job.isOpen()) s.board.post(task);
            s.run(0.1f);
        }
    }

    /** Mounting a widget from the hold at ring tile (x, y): the old one comes off and back in. */
    final class Mounting implements OutsideTask.Job {
        final int x, y;
        final float seconds;
        String stage = "hold";
        Entity carrier;
        float worked;
        int done, stowed;
        boolean inHold;

        Mounting(int x, int y, float seconds) {
            this.x = x;
            this.y = y;
            this.seconds = seconds;
        }

        @Override
        public boolean isOpen() {
            if (stage.equals("hold") || stage.equals("out")) return true;
            if (!stage.equals("carried")) return false;
            CrewMemberComponent c = s.c(carrier);
            return c.task instanceof OutsideTask && ((OutsideTask) c.task).job == this && c.task.isActive();
        }

        @Override
        public boolean workTile(HullSurface hull, int[] out) {
            out[0] = x;
            out[1] = y;
            return hull.isRing(x, y);
        }

        @Override
        public float seconds(CrewDef def) {
            return seconds;
        }

        @Override
        public void done(Entity worker) {
            done++;
            stage = "old";
            carrier = worker;
            s.c(worker).crate = new CrewMemberComponent.Crate(OLD, "the old widget", 1.0, 250f, this);
        }

        @Override
        public String doing() {
            return "mounting the widget";
        }

        @Override
        public String where() {
            return "the widget mount";
        }

        @Override
        public String part() {
            return stage.equals("hold") || stage.equals("carried") ? WIDGET : null;
        }

        @Override
        public CrewMemberComponent.Crate take(Entity worker) {
            if (!stage.equals("hold") || hold.amount(WIDGET) < 1) return null;
            hold.unload(WIDGET, 1);
            stage = "carried";
            carrier = worker;
            return new CrewMemberComponent.Crate(WIDGET, "the widget", 1.0, 250f, this);
        }

        @Override
        public void setDown(Entity worker, CrewMemberComponent.Crate crate) {
            stage = "out";
            carrier = null;
        }

        @Override
        public void stowed(Entity worker, CrewMemberComponent.Crate crate, boolean inHold) {
            stowed++;
            this.inHold = inHold;
            stage = stage.equals("old") ? "finished" : "hold";
            carrier = null;
        }

        @Override
        public float worked() {
            return worked;
        }

        @Override
        public void work(Entity worker, float seconds) {
            worked += seconds;
        }
    }

    @Test
    void thePartIsFetchedFromTheHoldCarriedOutAndSetDownAndTheOldOneComesBackIn() {
        ship(20f);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        boolean lifted = false, carriedOut = false, broughtIn = false;
        for (int i = 0; i < 6000 && !job.stage.equals("finished"); i++) {
            run(0.1f);
            CrewMemberComponent c = s.c(e);
            lifted |= "lifting a crate out of the hold".equals(c.activity);
            EvaComponent eva = EvaSystem.of(e);
            if (eva != null && eva.outside() && c.crate != null) {
                if (c.crate.commodity.equals(WIDGET)) {
                    carriedOut = true;
                    assertEquals(250f, eva.cargo, 1e-3f, "its mass with theirs");
                    assertEquals(EvaSystem.MASS + 250f, EvaSystem.mass(eva), 1e-3f);
                } else {
                    broughtIn = true;
                }
            }
        }
        assertTrue(lifted, "lifted out of the hold");
        assertTrue(carriedOut, "carried out on EVA");
        assertTrue(broughtIn, "the old one brought back in");
        assertEquals(1, job.done, "the work done once");
        assertEquals("finished", job.stage);
        assertTrue(job.inHold, "the old one stowed in the hold");
        assertEquals(0.0, hold.amount(WIDGET), 1e-9);
        assertEquals(1.0, hold.amount(OLD), 1e-9);
        assertNull(s.c(e).crate);
        assertNull(EvaSystem.of(e), "back in");
    }

    @Test
    void calledInWithThePartTheyBringItBackToTheHoldAndGoAgainOnceLetOut() {
        ship(20f);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && !(EvaSystem.outside(e) && EvaSystem.of(e).phase == EvaComponent.Phase.ON_HULL); i++) run(0.1f);
        assertNotNull(s.c(e).crate, "out with it");
        assertNull(EvaOrders.callIn(s.crew, e));
        for (int i = 0; i < 6000 && !(EvaSystem.of(e) == null && s.c(e).crate == null); i++) run(0.1f);
        assertNull(EvaSystem.of(e), "back in");
        assertNull(s.c(e).crate, "set down");
        assertEquals("hold", job.stage);
        assertEquals(1, job.stowed);
        assertEquals(1.0, hold.amount(WIDGET), 1e-9, "back in the hold");
        assertEquals(0, job.done);
        run(60f);
        assertEquals("hold", job.stage, "kept in");
        EvaOrders.letOut(s.crew, e);
        for (int i = 0; i < 6000 && !job.stage.equals("finished"); i++) run(0.1f);
        assertEquals(1, job.done, "out again for it once let out");
        assertEquals(1.0, hold.amount(OLD), 1e-9);
    }

    @Test
    void calledAwayAtTheWorkTheyLeaveThePartThereAndWhoeverGoesNextGoesStraightOut() {
        ship(200f);
        Entity e = s.spawn(CrewRole.ENGINEER, 0, 9, 2);
        for (int i = 0; i < 6000 && job.worked < 20f; i++) run(0.1f);
        assertEquals("out", job.stage, "set down at the work");
        assertNull(s.c(e).crate);
        assertNull(EvaOrders.callIn(s.crew, e));
        for (int i = 0; i < 6000 && EvaSystem.of(e) != null; i++) run(0.1f);
        float first = job.worked;
        assertTrue(first >= 20f, "the work put in: " + first);
        assertNull(job.part(), "nothing to fetch: the part waits at the work");
        assertEquals(0.0, hold.amount(WIDGET), 1e-9);
        EvaOrders.letOut(s.crew, e);
        boolean fetched = false;
        float before = job.worked;
        for (int i = 0; i < 12000 && job.done == 0; i++) {
            run(0.1f);
            fetched |= "fetching a crate from the hold".equals(s.c(e).activity);
        }
        assertFalse(fetched, "straight out to it");
        assertEquals(1, job.done, "finished on the second trip");
        assertTrue(job.worked - before < 200f - first + 1f, "only the rest of the work: " + (job.worked - before));
    }
}
