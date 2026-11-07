package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleView;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.screens.ShipSystems.State;
import com.ringsofiron.uihud.variants.cassette.task.ConsolePanel.Air;
import com.ringsofiron.uihud.variants.cassette.task.ConsolePanel.TankRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The console panel (spec CON): what each console says, its switch, and when it closes. */
class ConsolePanelTest {
    private static SubsystemComponent system(SubsystemType type, PowerCircuit circuit) {
        SubsystemComponent s = new SubsystemComponent(new MountFootprint(type.mountKind, MountFootprint.MountSize.S, 2, 1, 0), type);
        s.circuit = circuit;
        s.powered = circuit != null;
        s.isOperational = circuit != null;
        return s;
    }

    @Test
    void theHelmAndTheWeaponsConsoleSeatTheCaptainAndEveryOtherOpensAPanel() {
        assertTrue(ConsolePanel.seat("helm"));
        assertTrue(ConsolePanel.seat("weapons"));
        for (String id : new String[]{"reactor", "life_support", "turret_mount", ""}) assertFalse(ConsolePanel.seat(id), id);
        // Their prompt says so: E as well as V
        HudData d = new HudData();
        d.promptAction = "for the Helm panel   [V] sit and fly";
        d.promptConsole = "helm";
        assertArrayEquals(new String[]{YardPaint.KEY + "E", "OR ", YardPaint.KEY + "V", "SIT AND FLY"}, PromptStrip.segments(d));
        // A gun's E still opens its panel
        d.promptAction = "for the Gun panel   [V] take the gun";
        d.promptConsole = "turret_mount";
        assertArrayEquals(new String[]{YardPaint.KEY + "E", "FOR THE GUN PANEL   ", YardPaint.KEY + "V", "TAKE THE GUN"}, PromptStrip.segments(d));
    }

    @Test
    void theStateAndThePowerSayWhatTheBoardSays() {
        assertEquals("ON", ConsolePanel.stateWords(State.ON, Float.NaN));
        assertEquals("SHORTED 32 S", ConsolePanel.stateWords(State.SHORTED, 31.6f));
        assertEquals("STUNNED 8 S", ConsolePanel.stateWords(State.STUNNED, 8f));
        ShipGridComponent grid = new ShipGridComponent(6, 4, 1f);
        PowerCircuit one = new PowerCircuit(), two = new PowerCircuit();
        grid.circuits.add(one);
        grid.circuits.add(two);
        assertEquals("CIRCUIT 2", ConsolePanel.power(system(SubsystemType.LIFE_SUPPORT, two), grid));
        // Switched on and whole, but no reactor reaches it; switched off it just has none
        SubsystemComponent cut = system(SubsystemType.LIFE_SUPPORT, null);
        assertEquals("NONE: NO REACTOR REACHES IT", ConsolePanel.power(cut, grid));
        cut.switchedOn = false;
        assertEquals("NONE", ConsolePanel.power(cut, grid));
    }

    @Test
    void theReactorSaysItsOutputItsCircuitsDrawAndTheStore() {
        PowerCircuit c = new PowerCircuit();
        c.output = 2.4e6;
        c.capacity = 4.8e6;
        c.stored = 3.6e6;
        SubsystemComponent r = system(SubsystemType.REACTOR, c);
        r.ratedOutput = 3e6;
        r.health = 0.8f;
        assertArrayEquals(new String[]{"OUTPUT 2.4 MW OF 3.0 MW", "DRAW   350 KW", "STORE  3.6 MJ, 75%"}, ConsolePanel.reactorLines(r, 350e3));
        // Switched off it makes nothing, and with no circuit there is nothing to draw on or store
        r.switchedOn = false;
        r.circuit = null;
        assertArrayEquals(new String[]{"OUTPUT 0 W OF 3.0 MW", "DRAW   NONE, NO CIRCUIT", "STORE  NONE, NO CIRCUIT"}, ConsolePanel.reactorLines(r, 0));
    }

    @Test
    void lifeSupportSaysWhyARoomGetsNoAir() {
        assertEquals(Air.AIR, ConsolePanel.air(true, false, true));
        assertEquals(Air.VENTING, ConsolePanel.air(false, true, false));
        assertEquals(Air.SHUT, ConsolePanel.air(false, false, false));
        assertEquals(Air.OTHER, ConsolePanel.air(false, false, true));
        assertEquals("BEHIND A SHUT DOOR", Air.SHUT.label);
        assertEquals("", ConsolePanel.noAir(State.ON, true));
        assertEquals("NO AIR FROM IT WHILE IT IS SWITCHED OFF", ConsolePanel.noAir(State.OFF, false));
        assertEquals("NO AIR FROM IT WHILE NO REACTOR REACHES IT", ConsolePanel.noAir(State.CUT_OFF, false));
        assertEquals("NO AIR FROM IT UNTIL IT IS REPAIRED", ConsolePanel.noAir(State.WRECKED, false));
        assertEquals("IT STANDS IN NO ROOM, SO IT PUTS AIR INTO NONE", ConsolePanel.noAir(State.ON, false));
    }

    @Test
    void theTanksPipedToItAndTheEmergencyOxygen() {
        TankRow t = new TankRow();
        ConsolePanel.tank(t, "O2", 108, 150, 60, 60, true);
        assertTrue(t.on);
        assertEquals("72%", t.words);
        assertFalse(t.bad);
        ConsolePanel.tank(t, "O2", 0, 150, 42.4, 60, true);
        assertEquals("DRY, RESERVE 42 KG", t.words);
        assertTrue(t.bad);
        assertEquals(42.4f / 60f, t.share, 1e-4f);
        ConsolePanel.tank(t, "N2", 0, 150, 0, 0, true);
        assertEquals("DRY", t.words);
        // The ship has the tanks, but none is piped to this unit
        ConsolePanel.tank(t, "N2", 0, 0, 0, 0, true);
        assertEquals("NONE PIPED TO IT", t.words);
        // A layout from before tanks: life support never runs short, and no row says otherwise
        ConsolePanel.tank(t, "N2", 0, 0, 0, 0, false);
        assertFalse(t.on);
        SubsystemComponent tank = system(SubsystemType.TANK, null);
        tank.fluid = Fluid.NITROGEN;
        tank.capacity = 150;
        SubsystemComponent o2 = system(SubsystemType.TANK, null);
        o2.fluid = Fluid.OXYGEN;
        o2.capacity = 90;
        Array<SubsystemComponent> piped = new Array<>();
        piped.add(tank);
        piped.add(o2);
        assertEquals(150, ConsolePanel.capacity(piped, Fluid.NITROGEN), 1e-9);
    }

    private static Mount pd(WeaponFrame f, float facing, float arc) {
        Mount m = f.add();
        m.kind = Kind.POINT_DEFENSE;
        m.facingDeg = facing;
        m.arcDeg = arc;
        return m;
    }

    @Test
    void aPointDefenseGunInHandHuntsNoMissileAndMayLeaveItsSideBare() {
        // The starter corvette: an autocannon either side, each 300 degrees blind across the hull
        WeaponFrame f = new WeaponFrame();
        Mount port = pd(f, 90f, 300f), stbd = pd(f, -90f, 300f);
        Array<String> cost = new Array<>();
        ConsolePanel.gunCost(port, f.mounts, cost);
        assertEquals(2, cost.size);
        assertEquals("Point defense: in hand, it hunts no missile", cost.get(0));
        assertEquals("No other point defense covers its side", cost.get(1));
        assertFalse(ConsolePanel.covered(stbd, f.mounts));
        // One more on the nose takes in both sides
        Mount nose = pd(f, 0f, 300f);
        assertTrue(ConsolePanel.covered(port, f.mounts));
        ConsolePanel.gunCost(port, f.mounts, cost);
        assertEquals(1, cost.size);
        // ... unless it is down
        nose.state = com.ringsofiron.uihud.providers.WeaponStatusProvider.State.DESTROYED;
        assertFalse(ConsolePanel.covered(port, f.mounts));
        // A gun that is no point defense costs nothing to take
        Mount gun = f.add();
        gun.kind = Kind.GUN;
        ConsolePanel.gunCost(gun, f.mounts, cost);
        assertEquals(0, cost.size);
    }

    private static ConsolePanel open(SubsystemComponent s) {
        Entity e = new Entity();
        e.add(s);
        Entity ship = new Entity();
        InteractableConsoleComponent c = new InteractableConsoleComponent("reactor", "Reactor", ship, 2, 1);
        e.add(c);
        ConsoleView v = new ConsoleView();
        v.set(e, c, null);
        ConsolePanel p = new ConsolePanel(new TaskFrame());
        p.open(v);
        return p;
    }

    @Test
    void theReactorsSwitchAsksTwiceToSwitchOffAndOnceToSwitchOn() {
        SubsystemComponent r = system(SubsystemType.REACTOR, new PowerCircuit());
        ConsolePanel p = open(r);
        assertEquals("TWICE TO SWITCH OFF", p.switchWords());
        assertTrue(p.flip());
        assertTrue(r.switchedOn, "the first press only lifts the guard");
        assertEquals("AGAIN TO SWITCH OFF", p.switchWords());
        p.flip();
        assertFalse(r.switchedOn);
        assertEquals("SWITCH ON", p.switchWords());
        p.flip();
        assertTrue(r.switchedOn);
        // The guard drops again if the second press doesn't come
        p.flip();
        p.update(new HudData(), ConsolePanel.GUARD_SECONDS + 0.1f);
        p.flip();
        assertTrue(r.switchedOn);
        // Anything else switches at once
        SubsystemComponent life = system(SubsystemType.LIFE_SUPPORT, new PowerCircuit());
        ConsolePanel q = open(life);
        assertEquals("SWITCH OFF", q.switchWords());
        q.flip();
        assertFalse(life.switchedOn);
    }

    @Test
    void itClosesAsTheCaptainWalksAway() {
        ConsolePanel p = open(system(SubsystemType.REACTOR, new PowerCircuit()));
        // The game says where the captain is from the next frame on
        assertFalse(p.walkedAway(Float.NaN, 0.016f));
        assertFalse(p.walkedAway(1.4f, 0.016f));
        assertFalse(p.walkedAway(ConsolePanel.WALK_AWAY - 0.01f, 0.016f));
        assertTrue(p.walkedAway(ConsolePanel.WALK_AWAY + 0.01f, 0.016f));
        // Off its ship, once it has been aboard
        assertTrue(p.walkedAway(Float.NaN, 0.016f));
        // Never aboard at all: it gives up after a moment
        ConsolePanel q = open(system(SubsystemType.REACTOR, new PowerCircuit()));
        assertFalse(q.walkedAway(Float.NaN, 0.3f));
        assertTrue(q.walkedAway(Float.NaN, 0.3f));
    }

    @Test
    void wattsAndJoulesReadAsTheBoardsDo() {
        assertEquals("3.0 MW", ConsolePanel.watts(3e6));
        assertEquals("850 KW", ConsolePanel.watts(850e3));
        assertEquals("40 W", ConsolePanel.watts(40));
        assertEquals("0 W", ConsolePanel.watts(-5));
        assertEquals("6.0 MJ", ConsolePanel.joules(6e6));
        assertEquals("12 KJ", ConsolePanel.joules(12e3));
    }
}
