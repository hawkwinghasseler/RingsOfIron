package com.ringsofiron.uihud.variants.cassette.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import org.junit.jupiter.api.Test;

/** The Stations spec's console panels (10.2): Engineering's tabs, the gun panel, and when a panel opens and shuts. */
class ConsolePanelsTest {

    /** A ship with one circuit and a room: what {@link #system} fits goes on its registry. */
    private static final class Ship {
        final Entity entity = new Entity();
        final ShipGridComponent grid = new ShipGridComponent(12, 12, 1f);
        final ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent();
        final PowerCircuit circuit = new PowerCircuit();

        Ship() {
            grid.circuits.add(circuit);
            Compartment room = new Compartment(0);
            for (int x = 0; x < 12; x++) for (int y = 0; y < 12; y++) room.tiles.add(y * 12 + x);
            room.volume = 144f * Compartment.DECK_HEIGHT;
            grid.compartments.add(room);
            entity.add(grid);
            entity.add(registry);
        }

        /** Fits a system of {@code type} at ({@code x}, {@code y}), on the circuit unless {@code dark}; its entity. */
        Entity system(SubsystemType type, int x, int y, boolean dark) {
            SubsystemComponent s = new SubsystemComponent(new MountFootprint(type.mountKind, MountFootprint.MountSize.S, x, y, 0), type);
            s.circuit = dark ? null : circuit;
            s.powered = !dark;
            s.isOperational = !dark;
            Entity e = new Entity();
            e.add(s);
            registry.subsystems.add(e);
            return e;
        }

        /** A tank of {@code fluid} holding {@code kg} of {@code capacity}. */
        SubsystemComponent tank(Fluid fluid, int x, double kg, double capacity) {
            SubsystemComponent t = sys(system(SubsystemType.TANK, x, 9, true));
            t.fluid = fluid;
            t.contents = kg;
            t.capacity = capacity;
            return t;
        }
    }

    private static SubsystemComponent sys(Entity e) {
        return e.getComponent(SubsystemComponent.class);
    }

    /** The captain on deck aboard {@code ship}, console {@code console} (id {@code id}) open. */
    private static HudData at(Ship ship, Entity console, String id) {
        HudData d = new HudData();
        d.ctx.ship = ship.entity;
        d.ctx.presence = Presence.DECK;
        d.console.set(console, new InteractableConsoleComponent(id, id, ship.entity, 1, 1), null);
        return d;
    }

    @Test
    void aConsolePanelOpensOnTheTabThatMatters() {
        Ship ship = new Ship();
        Entity reactor = ship.system(SubsystemType.REACTOR, 1, 1, false);
        Entity life = ship.system(SubsystemType.LIFE_SUPPORT, 1, 5, false);
        Entity gun = ship.system(SubsystemType.TURRET_MOUNT, 7, 1, false);
        Entity helm = ship.system(SubsystemType.HELM, 5, 5, false);
        ConsolePanels panels = new ConsolePanels(new Boards(), new FireTrend());

        // With no alert, the console's own tab: Power at the reactor, the reactor's row selected
        HudData d = at(ship, reactor, "reactor");
        Page page = panels.open(d);
        assertSame(panels.power, page);
        assertSame(panels.engineering, page.console);
        assertSame(sys(reactor), panels.power.lines.get(panels.power.sel).sys.s, "the console's own system is selected");
        // Atmos at life support, on its unit
        d = at(ship, life, "life_support");
        assertSame(panels.atmos, panels.open(d));
        assertSame(sys(life), panels.atmos.lines.get(panels.atmos.sel).unit.s);
        // The gun panel at a gun; the helm's and the weapons console's panels are their stations' own
        assertSame(panels.gunPage, panels.open(at(ship, gun, "turret_mount")));
        assertSame(panels.gun, panels.gunPage.console);
        assertNull(panels.open(at(ship, helm, "helm")));
        assertNull(panels.open(at(ship, helm, "weapons")));

        // An alert's tab first: the most severe, then the newest, of those Engineering acts on
        d = at(ship, life, "life_support");
        d.alerts.begin(10);
        d.alerts.raise("sys:LIFE SUP:1,5", Level.CAUTION, Category.ENGINEERING);
        d.alerts.end();
        assertSame(panels.power, panels.open(d), "a system down: Power, even at life support");
        d.alerts.begin(20);
        d.alerts.raise("sys:LIFE SUP:1,5", Level.CAUTION, Category.ENGINEERING);
        d.alerts.raise("oxygen", Level.CAUTION, Category.LIFE_SUPPORT);
        d.alerts.end();
        assertSame(panels.atmos, panels.open(d), "the newer of two cautions");
        d.alerts.begin(30);
        d.alerts.raise("sys:LIFE SUP:1,5", Level.CAUTION, Category.ENGINEERING);
        d.alerts.raise("oxygen", Level.CAUTION, Category.LIFE_SUPPORT);
        d.alerts.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT);
        d.alerts.end();
        assertSame(panels.damage, panels.open(d), "a warning over both");
        // What isn't Engineering's leaves the console its own tab: a gun down is Tactical's, an engine the Helm's
        AlertRegistry tactical = new AlertRegistry();
        tactical.begin(0);
        tactical.raise("sys:TURRET:7,1", Level.WARNING, Category.ENGINEERING);
        tactical.raise("sys:SHIELDS:3,3", Level.WARNING, Category.ENGINEERING);
        tactical.raise("sys:ENGINE:5,1", Level.WARNING, Category.ENGINEERING);
        tactical.raise("hostile", Level.WARNING, Category.TACTICAL);
        tactical.end();
        assertNull(panels.tabFor(tactical));
    }

    @Test
    void aPanelShutsAsTheCaptainWalksOffOrSitsDownAtTheGun() {
        Ship ship = new Ship();
        Entity reactor = ship.system(SubsystemType.REACTOR, 1, 1, false);
        Entity gun = ship.system(SubsystemType.TURRET_MOUNT, 7, 1, false);
        ConsolePanels panels = new ConsolePanels(new Boards(), new FireTrend());
        HudData d = at(ship, reactor, "reactor");
        panels.open(d);
        // The game hasn't said how far the captain is yet: half a second's grace on deck
        assertFalse(panels.left(panels.engineering, d, 0.3f));
        assertTrue(panels.left(panels.engineering, d, 0.3f), "no word of the reach in time");
        panels.open(d);
        d.console.reach = 1f;
        assertFalse(panels.left(panels.engineering, d, 0.1f), "at the console");
        d.console.reach = ConsolePanels.WALK_AWAY + 0.1f;
        assertTrue(panels.left(panels.engineering, d, 0.1f), "walked off");
        d.console.reach = Float.NaN;
        assertTrue(panels.left(panels.engineering, d, 0.1f), "off the ship, once the reach was known");
        // A seated captain stays at it while the game can't say
        d.ctx.presence = Presence.HELM;
        panels.open(d);
        assertFalse(panels.left(panels.engineering, d, 2f));
        // The gun panel is the gun's from the deck: sitting down at it shuts it
        HudData g = at(ship, gun, "turret_mount");
        panels.open(g);
        g.console.reach = 0.5f;
        assertFalse(panels.left(panels.gun, g, 0.1f));
        g.ctx.presence = Presence.GUN;
        assertTrue(panels.left(panels.gun, g, 0.1f), "in hand, it is the sight's");
        // And the game shutting the console shuts it
        d.console.clear();
        assertTrue(panels.left(panels.engineering, d, 0.1f));
    }

    @Test
    void thePowerTabListsWhatIsDownInTheOrderItComesBack() {
        Ship ship = new Ship();
        Entity reactor = ship.system(SubsystemType.REACTOR, 1, 1, false);
        SubsystemComponent engine = sys(ship.system(SubsystemType.ENGINE, 3, 1, true));
        SubsystemComponent gun = sys(ship.system(SubsystemType.TURRET_MOUNT, 7, 1, false));
        gun.switchedOn = false;
        SubsystemComponent life = sys(ship.system(SubsystemType.LIFE_SUPPORT, 1, 5, false));
        life.stunned = 5f;
        SubsystemComponent wreck = sys(ship.system(SubsystemType.ENGINE, 5, 1, false));
        wreck.health = 0.1f;
        HudData d = at(ship, reactor, "reactor");
        Boards b = new Boards();
        b.refresh(d);
        PowerPage page = new PowerPage();
        page.refresh(b);
        assertTrue(page.offered());
        Array<SubsystemComponent> down = new Array<>();
        for (PowerPage.Line l : page.lines) if (l.kind == PowerPage.DOWN) down.add(l.sys.s);
        assertEquals(4, down.size, "the reactor works: only the rest are listed");
        assertSame(life, down.get(0), "back by itself in 5 s: the next to come back");
        assertSame(engine, down.get(1), "then its conduit");
        assertSame(gun, down.get(2), "then a switch");
        assertSame(wreck, down.get(3), "a repair last");
        assertEquals(PowerPage.STORE, page.lines.get(1).kind, "the circuit's store under the reactor");
        // Each in the gun chips' words, with what clears it
        ShipSystems.Sys y = new ShipSystems.Sys();
        y.s = wreck;
        y.state = ShipSystems.State.DAMAGED;
        assertEquals("DISABLED", PowerPage.word(y.state));
        assertEquals("10%, REPAIR", PowerPage.clears(y, false));
        y.state = ShipSystems.State.CUT_OFF;
        assertEquals("NO POWER", PowerPage.word(y.state));
        assertEquals("CONDUIT CUT", PowerPage.clears(y, false));
        assertEquals("A REACTOR RUNNING", PowerPage.clears(y, true), "in a blackout, the reactors are what it waits on");
        y.state = ShipSystems.State.OFF;
        assertEquals("SWITCH ON", PowerPage.clears(y, false));
        y.state = ShipSystems.State.STUNNED;
        y.timer = 5f;
        assertEquals("BACK IN 5 S", PowerPage.clears(y, false));
        assertEquals("DESTROYED", PowerPage.word(ShipSystems.State.WRECKED));
        // In a blackout the reactors lead, whatever else comes back sooner
        ShipSystems.Sys r = new ShipSystems.Sys();
        r.s = sys(reactor);
        r.state = ShipSystems.State.OFF;
        assertTrue(PowerPage.reactorFirst(r, y) < 0);
        assertTrue(PowerPage.comeBack(r, y) > 0, "else the stun's timer comes first");
        // The reactor's switch asks twice, and Enter names it
        page.focus(sys(reactor));
        assertEquals("SWITCH OFF", page.enterLabel());
        page.key(com.badlogic.gdx.Input.Keys.ENTER);
        assertTrue(sys(reactor).switchedOn, "the first press lifts the guard");
        page.key(com.badlogic.gdx.Input.Keys.ENTER);
        assertFalse(sys(reactor).switchedOn, "the second switches it off");
        page.refresh(b);
        page.focus(sys(reactor));
        assertEquals("SWITCH ON", page.enterLabel());
    }

    @Test
    void theTanksTabSaysWhatEachGasHoldsAndTheDeltaVItMakes() {
        Ship ship = new Ship();
        Entity reactor = ship.system(SubsystemType.REACTOR, 1, 1, false);
        ship.tank(Fluid.OXYGEN, 1, 60, 100);
        SubsystemComponent fuel = ship.tank(Fluid.PROPELLANT, 3, 400, 1000);
        SubsystemComponent leak = ship.tank(Fluid.PROPELLANT, 5, 100, 1000);
        leak.health = 0.5f;
        SubsystemComponent burst = ship.tank(Fluid.NITROGEN, 7, 0, 200);
        burst.health = 0f;
        HudData d = at(ship, reactor, "reactor");
        d.flightStatus.deltaVLeft = 1850;
        d.flightStatus.deltaVHome = 2100;
        d.flightStatus.deltaVPlanned = 400;
        Boards b = new Boards();
        b.refresh(d);
        TanksPage page = new TanksPage();
        page.refresh(b);
        assertTrue(page.offered());
        // Each gas the ship carries, propellant first; then the delta-v rows; then every tank
        assertEquals(TanksPage.GAS, page.lines.get(0).kind);
        assertSame(Fluid.PROPELLANT, page.lines.get(0).gas);
        assertSame(Fluid.OXYGEN, page.lines.get(1).gas);
        assertSame(Fluid.NITROGEN, page.lines.get(2).gas);
        assertEquals(TanksPage.LEFT, page.lines.get(3).which);
        assertEquals(TanksPage.HOME, page.lines.get(4).which);
        assertEquals(TanksPage.PLANNED, page.lines.get(5).which);
        assertEquals(TanksPage.HEAD, page.lines.get(6).kind);
        assertSame(fuel, page.lines.get(7).tank.s, "the tanks by what they hold, in the same order");
        assertEquals(11, page.lines.size);
        assertEquals("LEAKING", TanksPage.state(leak));
        assertEquals("BURST", TanksPage.state(burst));
        assertEquals("", TanksPage.state(fuel));
        assertEquals("1,850 M/S", TanksPage.mps(1850.4));
        assertEquals("FREE", TanksPage.mps(Double.POSITIVE_INFINITY), "thrust that costs nothing");
        // A prize's tanks show without the player's ship's delta-v
        Ship prize = new Ship();
        Entity console = prize.system(SubsystemType.REACTOR, 1, 1, false);
        prize.tank(Fluid.PROPELLANT, 3, 10, 100);
        HudData across = at(prize, console, "reactor");
        across.ctx.ship = ship.entity;
        across.flightStatus.deltaVLeft = 1850;
        b.refresh(across);
        page.refresh(b);
        for (TanksPage.Line l : page.lines) assertTrue(l.kind != TanksPage.DV, "no delta-v row for a prize");
    }

    @Test
    void theShieldsTabNamesEachEmittersHeatAndWhenATrippedOneStartsAgain() {
        Ship ship = new Ship();
        Entity reactor = ship.system(SubsystemType.REACTOR, 1, 1, false);
        Entity hot = ship.system(SubsystemType.SHIELD_EMITTER, 3, 3, false);
        Entity tripped = ship.system(SubsystemType.SHIELD_EMITTER, 7, 3, false);
        HudData d = at(ship, reactor, "reactor");
        Boards b = new Boards();
        b.refresh(d);
        ShieldsPage page = new ShieldsPage();
        page.refresh(b);
        assertFalse(page.offered(), "no shield cloud: no tab");
        ShieldStatus st = d.shieldStatus;
        st.present = true;
        st.raised = true;
        st.capacity = 500f;
        st.cloud = 310f;
        st.stock = 480f;
        st.stowed = 150f;
        st.scattered = 20f;
        st.emitters = 2;
        st.emittersWorking = 1;
        st.emittersTripped = 1;
        ShieldStatusProvider.Emitter a = st.add(), c = st.add();
        a.system = hot;
        a.working = true;
        a.heat = 0.9f;
        c.system = tripped;
        c.tripped = true;
        c.heat = 0.8f;
        c.restartIn = 12f;
        page.refresh(b);
        assertTrue(page.offered());
        assertEquals("RAISED", ShieldsPage.mode(st));
        assertEquals("150 STOWED, 20 SCATTERED", ShieldsPage.asideText(st));
        int emitters = 0;
        for (ShieldsPage.Line l : page.lines) {
            if (l.kind != ShieldsPage.EMITTER) continue;
            emitters++;
            boolean isHot = l.sys.s == sys(hot);
            assertSame(isHot ? a : c, l.emitter, "each emitter's system has its model's row");
            assertEquals(isHot ? "HOLDING" : "TRIPPED 12 S", ShieldsPage.state(l.sys, l.emitter, st.raised));
            assertEquals(isHot ? "RUNNING HOT: IT TRIPS AT 100%" : "TRIPPED HOT: IT STARTS AGAIN AT HALF ITS TRIP HEAT",
                    ShieldsPage.meaning(l.sys, l.emitter));
        }
        assertEquals(2, emitters);
        // Lowered, a working emitter is ready rather than holding; its system's own state comes first
        ShipSystems.Sys y = new ShipSystems.Sys();
        y.s = sys(hot);
        assertEquals("READY", ShieldsPage.state(y, a, false));
        y.state = ShipSystems.State.SHORTED;
        y.timer = 30f;
        assertEquals("SHORTED 30 S", ShieldsPage.state(y, a, true));
        st.emittersWorking = 0;
        assertEquals("TRIPPED", ShieldsPage.mode(st), "every emitter tripped");
        // A prize's console has no Shields tab: the cloud read is the player's ship's
        Ship prize = new Ship();
        HudData across = at(prize, prize.system(SubsystemType.REACTOR, 1, 1, false), "reactor");
        across.ctx.ship = ship.entity;
        across.shieldStatus.present = true;
        b.refresh(across);
        page.refresh(b);
        assertFalse(page.offered());
    }

    @Test
    void theGunPanelSaysHowItFiresAndWhatThePointDefenseLosesInHand() {
        WeaponFrame f = new WeaponFrame();
        Mount port = pd(f, "FWD PORT PD", 90f, 180f), stbd = pd(f, "FWD STBD PD", -90f, 180f);
        // Taken in hand, the starboard mount leaves its side to nobody
        assertEquals("FWD PORT PD CAN'T SEE TO STARBOARD", GunPage.cover(stbd, f.mounts));
        assertEquals("FWD STBD PD CAN'T SEE TO PORT", GunPage.cover(port, f.mounts));
        assertTrue(GunPage.uncovered(GunPage.cover(stbd, f.mounts)));
        // An all-round mount sees every side; with two others, none is named
        Mount round = pd(f, "AFT PD", 180f, 360f);
        assertEquals("THE OTHER POINT DEFENSE SEES ALL IT DOES", GunPage.cover(stbd, f.mounts));
        assertFalse(GunPage.uncovered(GunPage.cover(stbd, f.mounts)));
        round.state = com.ringsofiron.uihud.providers.WeaponStatusProvider.State.DESTROYED;
        port.state = com.ringsofiron.uihud.providers.WeaponStatusProvider.State.NO_POWER;
        assertEquals("NO OTHER POINT DEFENSE IS WORKING", GunPage.cover(stbd, f.mounts));
        port.state = com.ringsofiron.uihud.providers.WeaponStatusProvider.State.READY;
        // Two narrow mounts, one ahead and one to port: the beam mount's stern and starboard go to nobody
        WeaponFrame g = new WeaponFrame();
        Mount beam = pd(g, "STBD PD", -90f, 180f), narrow = pd(g, "BOW PD", 0f, 60f);
        pd(g, "PORT PD", 90f, 60f);
        String c = GunPage.cover(beam, g.mounts);
        assertEquals("NO OTHER POINT DEFENSE SEES ASTERN OR TO STARBOARD", c);
        assertEquals("NO OTHER POINT DEFENSE SEES", GunPage.wrapped(c, 28, 0), "the panel's line runs on at a space");
        assertEquals("ASTERN OR TO STARBOARD", GunPage.wrapped(c, 28, 1));
        assertEquals("", GunPage.wrapped("SHORT", 28, 1));
        assertTrue(GunPage.sees(narrow, 0f));
        assertFalse(GunPage.sees(narrow, 90f), "60° ahead doesn't reach the beam");
        // How each fires, in the WEAPONS page's words: in hand it is YOURS, point defense or not
        Mount gun = f.add();
        gun.kind = Kind.GUN;
        assertEquals("TRIGGER", GunPage.mode(gun, false));
        gun.weaponsFree = true;
        assertEquals("AT WILL", GunPage.mode(gun, false));
        assertEquals("HOLD", GunPage.mode(gun, true));
        assertEquals("PD AUTO", GunPage.mode(port, false));
        port.controller = Controller.PLAYER;
        assertEquals("YOURS", GunPage.mode(port, false));
        // Who works it
        StationBoardProvider.Seat seat = new StationBoardProvider.Seat();
        seat.hand = StationBoardProvider.Hand.CREW;
        seat.who = "Cole";
        gun.controller = Controller.CREW;
        assertEquals("COLE", GunPage.who(gun, seat));
        seat.hand = StationBoardProvider.Hand.COMING;
        assertEquals("COLE, ON THE WAY", GunPage.who(gun, seat));
        gun.controller = Controller.CONSOLE;
        assertEquals("THE WEAPONS CONSOLE", GunPage.who(gun, null));
        assertEquals("ITSELF", GunPage.who(stbd, null));
        assertEquals("YOU", GunPage.who(port, null));
        // Its arc, which way it faces
        assertEquals("180° ARC TO STARBOARD", GunPage.arc(stbd));
        assertEquals("ALL ROUND", GunPage.arc(round));
        assertEquals("AHEAD TO PORT", GunPage.facing(40f));
        assertEquals("ASTERN", GunPage.facing(-175f));
    }

    private static Mount pd(WeaponFrame f, String name, float facing, float arc) {
        Mount m = f.add();
        m.name = name;
        m.kind = Kind.POINT_DEFENSE;
        m.controller = Controller.AUTO;
        m.facingDeg = facing;
        m.arcDeg = arc;
        return m;
    }

    @Test
    void theGunPanelWorksTheGunsSwitchAndTakesItInHand() {
        Ship ship = new Ship();
        Entity gun = ship.system(SubsystemType.TURRET_MOUNT, 7, 1, false);
        HudData d = at(ship, gun, "turret_mount");
        boolean[] taken = {false};
        Array<HudData.ConsoleAction> actions = new Array<>();
        actions.add(new HudData.ConsoleAction("Take the gun", "VIEW", () -> taken[0] = true));
        d.console.set(gun, d.console.data, actions);
        Mount m = d.weaponFrame.add();
        m.name = "FWD GUN";
        m.weapon = "Autocannon";
        m.tileX = 7;
        m.tileY = 1;
        Boards b = new Boards();
        b.refresh(d);
        GunPage page = new GunPage();
        page.refresh(b);
        assertTrue(page.offered());
        assertEquals("FWD GUN", page.label, "the tab is the mount");
        assertEquals("SWITCH OFF", page.enterLabel(), "Enter works its switch");
        page.key(com.badlogic.gdx.Input.Keys.ENTER);
        assertFalse(sys(gun).switchedOn);
        page.pressKey(1);
        assertTrue(taken[0], "the second key takes it in hand, as V does");
        // A prize's gun: its system's state and its switch, no weapon read
        Ship prize = new Ship();
        Entity other = prize.system(SubsystemType.TURRET_MOUNT, 7, 1, false);
        HudData across = at(prize, other, "turret_mount");
        across.ctx.ship = ship.entity;
        across.weaponFrame.add().tileX = 7;
        b.refresh(across);
        page.refresh(b);
        assertTrue(page.offered());
        assertEquals("GUN", page.label);
        assertFalse(page.lines.contains(GunPage.MODE));
        assertTrue(page.lines.contains(GunPage.HEALTH));
    }
}
