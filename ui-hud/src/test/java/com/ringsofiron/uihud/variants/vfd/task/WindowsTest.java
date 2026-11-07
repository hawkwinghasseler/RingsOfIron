package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleAction;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FILAMENT × Task's screens and panels in place of the game's windows (functional-task 7), from the game's data. */
class WindowsTest {
    private final HudData d = new HudData();
    private final Windows windows = new Windows(null);

    WindowsTest() {
        d.ctx.presence = Presence.DECK;
    }

    /** E at a console: the game hands the variant its system's entity, what E found there and its actions. */
    private Entity console(String id, String title, SubsystemType type, ConsoleAction... actions) {
        Entity e = new Entity();
        // The reactor and a gun stand in primary mounts, the rest in auxiliary ones
        MountFootprint.MountKind kind = type == SubsystemType.REACTOR || type == SubsystemType.TURRET_MOUNT
                ? MountFootprint.MountKind.PRIMARY : MountFootprint.MountKind.AUXILIARY;
        SubsystemComponent s = new SubsystemComponent(new MountFootprint(kind, MountFootprint.MountSize.S, 1, 4, 0), type);
        s.powered = true;
        e.add(s);
        InteractableConsoleComponent c = new InteractableConsoleComponent(id, title, null, 1, 4);
        e.add(c);
        Array<ConsoleAction> list = new Array<>();
        list.addAll(actions);
        d.console.set(e, c, list);
        return e;
    }

    /** The value of the console page's row labelled {@code label}, or null. */
    private static String row(ConsoleTab face, String label) {
        for (ConsoleTab.Row r : face.rows) if (r.kind == ConsoleTab.FIELD && r.label.equals(label)) return r.value;
        return null;
    }

    /** Whether the console page's row labelled {@code label} stands out in amber. */
    private static boolean amber(ConsoleTab face, String label) {
        for (ConsoleTab.Row r : face.rows) if (r.kind == ConsoleTab.FIELD && r.label.equals(label)) return r.amber;
        return false;
    }

    private static boolean noted(ConsoleTab face, String text) {
        for (ConsoleTab.Row r : face.rows) if (r.kind == ConsoleTab.NOTE && r.value.equals(text)) return true;
        return false;
    }

    // ------------------------------------------------------------------ routing

    @Test
    void theGamesWindowsAreTheScreensAndPanelsHere() {
        for (ClassicWindow w : new ClassicWindow[] {ClassicWindow.TRADE, ClassicWindow.YARD, ClassicWindow.SHIPYARD, ClassicWindow.MISSIONS,
                ClassicWindow.CREW, ClassicWindow.BOARDING, ClassicWindow.LOG, ClassicWindow.MAP, ClassicWindow.CONSOLE, ClassicWindow.HELP,
                ClassicWindow.GAME_OVER}) {
            assertTrue(windows.replaces(w), w + " is a screen's or a panel's");
        }
        windows.boards.refresh(d);
        assertSame(windows.terminal, windows.screenFor(ClassicWindow.TRADE, d));
        assertSame(windows.terminal, windows.screenFor(ClassicWindow.YARD, d));
        assertSame(windows.book, windows.screenFor(ClassicWindow.LOG, d));
        assertSame(windows.book, windows.screenFor(ClassicWindow.CREW, d), "I is the book's Crew away from a terminal that hires");
        assertSame(windows.book, windows.screenFor(ClassicWindow.MISSIONS, d), "with no board here, the contracts held");
        assertSame(windows.boarding, windows.screenFor(ClassicWindow.BOARDING, d));
        assertSame(windows.map, windows.screenFor(ClassicWindow.MAP, d));
        assertSame(windows.console, windows.screenFor(ClassicWindow.CONSOLE, d));
        assertSame(windows.controls, windows.screenFor(ClassicWindow.HELP, d));
    }

    @Test
    void theBookOpensOnTheLogAndStandsForIt() {
        assertTrue(windows.open(ClassicWindow.LOG, d));
        assertSame(windows.book, windows.current);
        assertSame(windows.book.log, windows.book.tab);
        assertTrue(windows.showing(ClassicWindow.LOG));
        assertTrue(windows.showing(ClassicWindow.CREW), "every tab of the book stands for the roster, so I closes it");
        assertSame(Screen.Cover.SCREEN, windows.cover());
        windows.close(ClassicWindow.LOG);
        assertNull(windows.current);
        assertSame(Screen.Cover.NONE, windows.cover());
    }

    @Test
    void theControlsCardOpensOverAScreenAndClosesBackToIt() {
        assertTrue(windows.open(ClassicWindow.LOG, d));
        assertTrue(windows.open(ClassicWindow.HELP, d));
        assertSame(windows.controls, windows.current);
        assertSame(windows.controls.keyboard, windows.controls.tab, "F1 opens it on the Keyboard");
        assertSame(windows.book, windows.controls.under);
        assertTrue(windows.showing(ClassicWindow.HELP));
        assertTrue(windows.showing(ClassicWindow.LOG), "the book still shows under the card");
        // The game shuts the log as F1 opens the card: the book stays under it
        windows.close(ClassicWindow.LOG);
        assertSame(windows.book, windows.controls.under);
        windows.controls.clicked(ControlsCard.TAB + 1);
        assertSame(windows.controls.gamepad, windows.controls.tab);
        assertTrue(windows.showing(ClassicWindow.HELP), "either side stands for the controls");
        // Esc closes the card first, back to the book
        windows.close(ClassicWindow.HELP);
        assertSame(windows.book, windows.current);
        assertTrue(windows.showing(ClassicWindow.LOG));

        // L under the card, a frame later, closes the book and leaves the card
        assertTrue(windows.open(ClassicWindow.HELP, d));
        windows.time += 1f;
        windows.close(ClassicWindow.LOG);
        assertSame(windows.controls, windows.current);
        assertNull(windows.controls.under);
        windows.close(ClassicWindow.HELP);
        assertNull(windows.current);
    }

    @Test
    void anotherScreenReplacesTheCardAndWhatItWasOver() {
        assertTrue(windows.open(ClassicWindow.HELP, d));
        assertNull(windows.controls.under);
        assertTrue(windows.open(ClassicWindow.LOG, d));
        assertSame(windows.book, windows.current);
        assertFalse(windows.showing(ClassicWindow.HELP));
    }

    @Test
    void theControlsCardSaysWhatTheGamesKeysOpenHere() {
        KeyHint k = new KeyHint();
        k.action = "CREW";
        k.label = "crew";
        assertTrue(ControlsTab.label(k).startsWith("the ship's book"));
        k.label = "crew, hire";
        assertEquals("the crew, in the ship's book; who is hiring, on the terminal", ControlsTab.label(k));
        k.label = "crew (wounded need a medic)";
        assertEquals("crew (wounded need a medic)", ControlsTab.label(k), "one that says why it shows now keeps its words");
        k.action = "MISSIONS";
        k.label = "missions held";
        assertEquals("the contracts held, in the ship's book", ControlsTab.label(k));
        k.action = "BOARD";
        k.label = "boarding";
        assertEquals("the boarding panel", ControlsTab.label(k));
        k.action = "MAP";
        k.label = "map";
        assertTrue(ControlsTab.label(k).startsWith("the map"));
        // The key strip keeps the game's short words, J's naming the terminal's tab
        assertEquals("map", ControlsTab.word(k));
        k.action = "MISSIONS";
        k.label = "mission board";
        assertEquals("contracts", ControlsTab.word(k));
    }

    @Test
    void theCardListsTheScreensKeysLastOnTheKeyboardOnly() {
        assertTrue(windows.open(ClassicWindow.HELP, d));
        ControlsTab keyboard = windows.controls.keyboard, gamepad = windows.controls.gamepad;
        gamepad.refresh(windows.boards, d);
        assertEquals(ControlsTab.SCREENS, keyboard.order.peek().title);
        assertEquals(0, gamepad.order.size, "the pad's side has the pad's buttons alone");
    }

    // ------------------------------------------------------------------ the console's panel

    @Test
    void eAtAConsoleOpensItsPanelWithItsSystemAndSwitch() {
        console("reactor", "Reactor", SubsystemType.REACTOR);
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        ConsolePanel c = windows.console;
        assertSame(c, windows.current);
        assertTrue(windows.showing(ClassicWindow.CONSOLE), "it stands for the console window, so the game's checks see it");
        assertSame(Screen.Cover.NONE, windows.cover(), "beside the console, nothing else goes");
        assertEquals("REACTOR", c.frame.title);
        assertEquals(1, c.frame.figs);
        assertEquals("HEALTH", c.frame.figLabel[0]);
        assertEquals("100", c.frame.figValue[0]);
        assertEquals("FEEDS NO CIRCUIT", row(c.face, "POWER"));
        assertEquals("ON", row(c.face, "STATE"));
        assertTrue(noted(c.face, "IT FEEDS NOTHING WHILE IT IS DOWN"), "no circuit, so nothing of its circuit to read");
        assertEquals(SoftKeys.NONE, c.face.keys.key(Keys.ENTER), "Enter never switches a system off");
    }

    @Test
    void theReactorsSwitchAsksTwiceAndSaysWhatItDid() {
        Entity e = console("reactor", "Reactor", SubsystemType.REACTOR);
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        ConsoleTab face = windows.console.face;
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        face.clicked(ConsoleTab.KEY + ConsoleTab.SWITCH);
        assertTrue(s.switchedOn, "the first press only asks");
        assertTrue(windows.console.response.startsWith("PRESS AGAIN"));
        face.clicked(ConsoleTab.KEY + ConsoleTab.SWITCH);
        assertFalse(s.switchedOn);
        assertEquals("REACTOR SWITCHED OFF", windows.console.response);
        face.refresh(windows.boards, d);
        assertEquals("OFF", row(face, "STATE"));
        face.clicked(ConsoleTab.KEY + ConsoleTab.SWITCH);
        assertTrue(s.switchedOn, "on again at once");

        // The second press waits only so long
        face.clicked(ConsoleTab.KEY + ConsoleTab.SWITCH);
        face.update(windows.boards, d, ConsoleTab.CONFIRM_FOR + 0.1f);
        face.clicked(ConsoleTab.KEY + ConsoleTab.SWITCH);
        assertTrue(s.switchedOn, "a press after the wait asks again");
    }

    @Test
    void aSystemsStateIsThePowerBoardsWordsWithWhenItComesBack() {
        Entity e = console("life_support", "Life support", SubsystemType.LIFE_SUPPORT);
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        s.shorted = true;
        s.shortedFor = 13f;
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        assertEquals("SHORTED, BACK IN 32 S OR ON A RESET", row(windows.console.face, "STATE"));
        assertTrue(windows.console.face.rows.get(1).amber, "a fault reads amber");
    }

    @Test
    void theHelmAndTheWeaponsConsoleOpenNoPanelEAndVBothSit() {
        boolean[] sat = new boolean[1];
        console("weapons", "Weapons", SubsystemType.WEAPONS, new ConsoleAction("Take fire control: every gun", "VIEW", () -> sat[0] = true));
        assertFalse(windows.open(ClassicWindow.CONSOLE, d), "no panel opens");
        assertTrue(sat[0], "the game's own order there on V sits the captain");
        assertNull(windows.current);
        d.console.clear();
        console("helm", "Helm", SubsystemType.HELM);
        assertFalse(windows.open(ClassicWindow.CONSOLE, d));
        assertNull(windows.current);
        assertTrue(ConsolePanel.sits("helm") && ConsolePanel.sits("weapons") && !ConsolePanel.sits("reactor"));
    }

    @Test
    void eClosesThePanelAndAWalkingKeyClosesItAndWalksOn() {
        console("reactor", "Reactor", SubsystemType.REACTOR);
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        assertTrue(windows.keyDown(Keys.E), "E closes it, as Esc does");
        assertNull(windows.current);
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        assertFalse(windows.keyDown(Keys.S), "the walk goes on to the game");
        assertNull(windows.current, "walking away closes it");
        // Seated, or with the game done with the console, it can't show
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        d.ctx.presence = Presence.HELM;
        assertFalse(windows.console.available(windows.boards, d));
        d.ctx.presence = Presence.DECK;
        d.console.clear();
        assertFalse(windows.console.available(windows.boards, d));
        assertFalse(windows.open(ClassicWindow.CONSOLE, d), "with no console E found, nothing opens");
    }

    @Test
    void aGunNamesWhoWorksItTheGamesOrderOnItsKeyAndWhatPointDefenseCosts() {
        Entity gun = console("turret_mount", "Gun", SubsystemType.TURRET_MOUNT, new ConsoleAction("Take the gun", "VIEW", () -> { }));
        d.keyNames = name -> name.equals("VIEW") ? "V" : null;
        Mount m = d.weaponFrame.add();
        m.name = "Fwd PD";
        m.weapon = "PD laser";
        m.kind = Kind.POINT_DEFENSE;
        m.state = State.READY;
        m.controller = Controller.CREW;
        m.tileX = 1;
        m.tileY = 4;
        Seat s = d.stations.add();
        s.post = Post.GUN;
        s.console = gun;
        s.hand = Hand.CREW;
        s.who = "Cole";
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        ConsoleTab face = windows.console.face;
        assertEquals("PD LASER", face.title, "a gun by its weapon");
        assertEquals("FWD PD", face.room, "and its mount");
        assertEquals("PD LASER, READY", row(face, "WEAPON"));
        assertEquals("COLE, AT THE GUN", row(face, "WORKED BY"));
        assertEquals(1, face.orders);
        assertEquals("TAKE THE GUN", face.orderWord[0]);
        assertEquals("V", face.orderCap[0]);
        assertTrue(noted(face, "POINT DEFENSE: IN HAND, IT HUNTS NO MISSILE"));

        m.kind = Kind.GUN;
        m.controller = Controller.NONE;
        face.refresh(windows.boards, d);
        assertEquals("NOBODY: IT FIRES ON YOUR TRIGGER", row(face, "WORKED BY"));
        assertFalse(noted(face, "POINT DEFENSE: IN HAND, IT HUNTS NO MISSILE"));
    }

    /** Two rooms, 9 by 6 tiles each, split by a light wall at x = 10 with a door at y = 3; life support in the left one. */
    private Entity twoRooms(ShipGridComponent[] grid) {
        String[] layout = {
            "####################",
            "#..................#",
            "#..................#",
            "#..................#",
            "#..................#",
            "#..................#",
            "#..................#",
            "####################"
        };
        ChassisBlueprint bp = new ChassisBlueprint(layout).tileSize(1f).mount("life_support", MountFootprint.MountSize.S, 3, 3, 1);
        for (int y = 1; y <= 6; y++) bp.edgeWall(10, y, Side.LEFT, WeightClass.LIGHT, y == 3);
        bp.covering(FloorCovering.CARPET, 1, 1, 9, 6);
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        Entity unit = null;
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            e.add(new SubsystemComponent(p.footprint, p.type));
            p.footprint.installed = e;
            if (p.type == SubsystemType.LIFE_SUPPORT) unit = e;
        }
        new GridHazardSystem(1).step(r.grid, GridHazardSystem.HAZARD_TICK);
        Entity ship = new Entity();
        ship.add(r.grid);
        d.ctx.ship = ship;
        SubsystemComponent s = unit.getComponent(SubsystemComponent.class);
        InteractableConsoleComponent c = new InteractableConsoleComponent("life_support", "Life support", ship, s.footprint.originX, s.footprint.originY);
        unit.add(c);
        d.console.set(unit, c, null);
        grid[0] = r.grid;
        return unit;
    }

    @Test
    void lifeSupportSaysWhichRoomsItAirsAndWhyNotTheRest() {
        ShipGridComponent[] grid = new ShipGridComponent[1];
        twoRooms(grid);
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        ConsoleTab face = windows.console.face;
        assertEquals("LIFE SUPPORT", face.title);
        String to = row(face, "AIR TO");
        assertTrue(to.contains(", "), "both rooms through the open door: " + to);
        assertNull(row(face, "NONE TO"));
        // A layout from before tanks: no tank of it aboard, so the gas doesn't run out
        assertEquals("NOT LIMITED: NO TANK OF IT ABOARD", row(face, "OXYGEN"));
        assertEquals("60 KG OF OXYGEN, ITS OWN", row(face, "RESERVE"));
        // An oxygen tank aboard that no pipe reaches, as when damage cuts one: life support has its reserve alone
        Entity tank = new Entity();
        SubsystemComponent t = new SubsystemComponent(new MountFootprint(MountFootprint.MountKind.AUXILIARY, MountFootprint.MountSize.S, 14, 3, 0), SubsystemType.TANK);
        t.fluid = Fluid.OXYGEN;
        tank.add(t);
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(d.ctx.ship);
        registry.registerSubsystem(tank);
        d.ctx.ship.add(registry);
        face.refresh(windows.boards, d);
        assertEquals("NONE: NO TANK'S PIPE REACHES IT", row(face, "OXYGEN"));
        assertTrue(amber(face, "OXYGEN"));
        assertEquals("NOT LIMITED: NO TANK OF IT ABOARD", row(face, "NITROGEN"));

        // The door shut: the room behind it gets none
        grid[0].getEdge(10, 3, Side.LEFT).bulkheadSealed = true;
        new GridHazardSystem(1).step(grid[0], GridHazardSystem.HAZARD_TICK);
        face.refresh(windows.boards, d);
        assertFalse(row(face, "AIR TO").contains(", "), "one room now");
        assertTrue(row(face, "NONE TO").endsWith(": BEHIND A SHUT DOOR"), row(face, "NONE TO"));

        // A breach in its own room: venting, so none to it either
        grid[0].getTile(0, 3).setIntegrity(0f);
        new GridHazardSystem(1).step(grid[0], GridHazardSystem.HAZARD_TICK);
        face.refresh(windows.boards, d);
        assertEquals("NO ROOM", row(face, "AIR TO"));
        boolean venting = false;
        for (ConsoleTab.Row r : face.rows) if (r.label.equals("NONE TO") && r.value.endsWith(": VENTING TO SPACE")) venting = true;
        assertTrue(venting, "the breached room vents");
    }

    @Test
    void thePanelStandsBesideTheConsoleAwayFromTheCaptain() {
        console("reactor", "Reactor", SubsystemType.REACTOR);
        assertTrue(windows.open(ClassicWindow.CONSOLE, d));
        ConsolePanel c = windows.console;
        c.anchorX = 640f;
        c.anchorY = 400f;
        c.tile = 20f;
        c.captainX = 610f;
        c.bounds(Geo.W, Geo.H);
        assertEquals(640f + Geo.CON_GAP, c.x, 0.01f, "right of the console, the captain being on its left");
        assertTrue(c.y >= Geo.SCREEN_Y && c.y + c.h <= Geo.bottomY(Geo.H) + Geo.HLM_Y - 10f + 0.01f, "under the band, over the helm's line");
        // Near the right column there's no room on that side: it goes left
        c.side = Float.NaN;
        c.anchorX = 900f;
        c.captainX = 870f;
        c.bounds(Geo.W, Geo.H);
        assertEquals(900f - Geo.CON_GAP - Geo.CON_W, c.x, 0.01f);
        // Once chosen the side holds while it shows, wherever the captain shuffles
        c.captainX = 930f;
        c.bounds(Geo.W, Geo.H);
        assertEquals(-1f, c.side, 0f);
    }

    // ------------------------------------------------------------------ the terminal's contracts and the log

    @Test
    void anOffersTermsReadAsTheTablesColumns() {
        Boards b = new Boards();
        OffersTab.Offer o = new OffersTab.Offer();
        Job j = new Job();
        j.id = 7;
        j.title = "Deliver 120 Water to Highport";
        j.giver = "Selene Mining Combine";
        j.terms = "12.0 cr/unit + 1,500 cr, +4 standing, 2.0 d";
        OffersTab.read(o, j, 0, "Selene Mining Combine", b);
        assertEquals("DELIVERY", o.kind);
        assertEquals("120 WATER TO HIGHPORT", o.job);
        assertEquals("1,500+", o.pay, "the cargo isn't sold here: the fee and more on top");
        assertEquals(1500 + 1440, o.payValue, 1e-6);
        assertEquals("+4", o.standing);
        assertEquals("2.0 D", o.time);
        assertEquals(2 * 86400, o.timeLeft, 1e-6);
        assertTrue(o.sponsor);

        j.title = "Operation: Clear the lanes";
        j.terms = "3 steps, then 8,000 cr, +6 standing";
        OffersTab.read(o, j, 1, "", b);
        assertTrue(o.operation);
        assertEquals("OPERATION", o.kind);
        assertEquals("CLEAR THE LANES", o.job);
        assertEquals(3, o.steps);
        assertEquals("3 STEPS", o.time);
        assertEquals("8,000", o.pay);
        assertEquals("8,000 CR AT THE END", o.payWords);

        j.title = "Bounty: Vex";
        j.terms = "1,500 cr, +4 standing, 30 min";
        OffersTab.read(o, j, 2, "", b);
        assertEquals("BOUNTY", o.kind);
        assertEquals("VEX", o.job);
        assertEquals(1800, o.timeLeft, 1e-6);
        assertFalse(o.sponsor);
    }

    @Test
    void theLogKeepsEachNoticeOnceAndEachAlertOnceWithWhenItCleared() {
        Logbook log = new Logbook();
        d.messages.clear();
        d.messages.add("Docked at Highport", Level.ADVISORY, 100);
        d.messages.posted();
        log.update(d);
        assertEquals(1, log.entries.size);
        // The feed refilled newest first, the old notice with it
        d.messages.clear();
        d.messages.add("Undocked", Level.ADVISORY, 200);
        d.messages.add("Docked at Highport", Level.ADVISORY, 100).count = 2;
        d.messages.posted();
        log.update(d);
        assertEquals(2, log.entries.size, "each notice once");
        assertEquals("Docked at Highport (x2)", log.entries.get(0).text, "posted again: revised with its count");
        assertEquals("Undocked", log.entries.get(1).text);

        d.ctx.simSeconds = 300;
        d.alerts.begin(300);
        d.alerts.raise("fire:hold", Level.CAUTION, Category.ENGINEERING).set("FIRE", "Fire in the hold");
        d.alerts.end();
        log.update(d);
        assertEquals(3, log.entries.size);
        Logbook.Entry fire = log.entries.peek();
        assertTrue(fire.alert);
        assertTrue(Double.isNaN(fire.cleared));
        d.ctx.simSeconds = 310;
        d.alerts.begin(310);
        d.alerts.raise("fire:hold", Level.WARNING, Category.ENGINEERING).set("FIRE", "Fire in the hold, spreading");
        d.alerts.end();
        log.update(d);
        assertEquals(3, log.entries.size, "an alert is logged once");
        assertEquals(Level.WARNING, fire.level, "revised as it worsens");
        assertEquals("Fire in the hold, spreading", fire.text);
        d.ctx.simSeconds = 340;
        d.alerts.begin(340);
        d.alerts.end();
        log.update(d);
        assertEquals(340, fire.cleared, 1e-9, "its clearing stamped on it");
    }
}
