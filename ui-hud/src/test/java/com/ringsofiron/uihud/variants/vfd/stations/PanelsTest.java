package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleAction;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.variants.ClassicWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FILAMENT × Stations' panels in place of the game's windows (functional-stations 10), from the game's data. */
class PanelsTest {
    private final HudData d = new HudData();
    private final Panels panels = new Panels(null);

    /** E at a console: the game hands the variant its entity, what E found there and its actions. */
    private Entity console(String id, String title, SubsystemType type, MountFootprint.MountKind kind) {
        Entity e = new Entity();
        SubsystemComponent s = new SubsystemComponent(new MountFootprint(kind, MountFootprint.MountSize.S, 1, 4, 0), type);
        s.powered = true;
        e.add(s);
        InteractableConsoleComponent c = new InteractableConsoleComponent(id, title, null, 1, 4);
        e.add(c);
        Array<ConsoleAction> actions = new Array<>();
        if (id.equals("weapons")) actions.add(new ConsoleAction("Take fire control: every gun", "VIEW", () -> { }));
        d.console.set(e, c, actions);
        return e;
    }

    @Test
    void theConsolesWindowsAndTheControlsAreThePanelsHere() {
        for (ClassicWindow w : new ClassicWindow[] {ClassicWindow.CONSOLE, ClassicWindow.HELP, ClassicWindow.MAP, ClassicWindow.LOG}) {
            assertTrue(panels.replaces(w), w + " is a panel's");
        }
        assertSame(panels.console, panels.panelFor(ClassicWindow.CONSOLE));
        assertSame(panels.controls, panels.panelFor(ClassicWindow.HELP));
        assertSame(panels.plot, panels.panelFor(ClassicWindow.MAP));
    }

    @Test
    void aConsoleOpensOnItsStationsPagesAtItsFocusPage() {
        console("helm", "Helm", SubsystemType.HELM, MountFootprint.MountKind.AUXILIARY);
        assertTrue(panels.open(ClassicWindow.CONSOLE, d), "E at the helm opens its panel");
        ConsolePanel c = panels.console;
        assertSame(c, panels.current);
        assertSame(c.helmPages, c.pages, "the helm's pages: Helm, Navigation, Comms");
        assertSame(c.helm, c.page, "on its focus page");
        assertTrue(panels.showing(ClassicWindow.CONSOLE), "it stands for the console window, so the game's checks see it");
        assertEquals("HELM", c.frame.title);
        assertEquals("HELM CONSOLE", c.frame.owner);
        assertEquals("HELM", c.systemName);
        assertEquals("ONLINE", c.state);
        assertEquals(0, c.orders, "the helm has no order of the game's at it");

        panels.closeAll();
        d.console.clear();
        console("reactor", "Reactor", SubsystemType.REACTOR, MountFootprint.MountKind.PRIMARY);
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        assertSame(c.engineeringPages, c.pages);
        assertSame(c.power, c.page, "Power at the reactor");
        d.console.clear();
        console("life_support", "Life support", SubsystemType.LIFE_SUPPORT, MountFootprint.MountKind.AUXILIARY);
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        assertSame(c.atmos, c.page, "Atmos at life support");
    }

    @Test
    void aConsoleOpensOnTheMostSevereAlertItHosts() {
        console("reactor", "Reactor", SubsystemType.REACTOR, MountFootprint.MountKind.PRIMARY);
        d.alerts.begin(10);
        d.alerts.raise("fire:hold", Level.CAUTION, Category.ENGINEERING).set("FIRE", "Fire in the hold");
        d.alerts.end();
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        assertSame(panels.console.damage, panels.console.page, "the fire's home page, Damage");
        // One the console's stations don't show stays out of it
        panels.closeAll();
        d.alerts.reset();
        d.alerts.begin(20);
        d.alerts.raise("hostile:1", Level.WARNING, Category.TACTICAL).set("HOSTILE", "Raider closing");
        d.alerts.end();
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        assertSame(panels.console.power, panels.console.page, "a hostile's page is the weapons console's");
    }

    @Test
    void theWeaponsConsoleHasTheGamesOrderWithItsKeyAndAGunNamesItsPost() {
        Entity weapons = console("weapons", "Weapons", SubsystemType.WEAPONS, MountFootprint.MountKind.AUXILIARY);
        d.keyNames = name -> name.equals("VIEW") ? "V" : null;
        Seat s = d.stations.add();
        s.post = Post.WEAPONS;
        s.name = "WEAPONS";
        s.console = weapons;
        s.hand = Hand.CREW;
        s.who = "Cole";
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        ConsolePanel c = panels.console;
        assertSame(c.tacticalPages, c.pages);
        assertSame(c.contacts, c.page, "Contacts at the weapons console");
        assertEquals("TACTICAL", c.frame.title);
        assertEquals(1, c.orders);
        assertEquals("TAKE FIRE CONTROL: EVERY GUN", c.orderWord[0]);
        assertEquals("V", c.orderCap[0]);
        assertEquals("COLE", c.worked);

        d.console.clear();
        Entity gun = console("turret_mount", "Gun", SubsystemType.TURRET_MOUNT, MountFootprint.MountKind.PRIMARY);
        s.post = Post.GUN;
        s.name = "Port gun";
        s.console = gun;
        s.hand = Hand.NOBODY;
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        assertSame(c.gunPages, c.pages);
        assertEquals("GUN", c.frame.title);
        assertEquals("PORT GUN", c.frame.owner, "a gun is named by its post");
        assertEquals("NOBODY", c.worked);
        assertEquals("", c.mountLine, "no mount on the weapons' board at its tile yet");
        com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount m = d.weaponFrame.add();
        m.weapon = "Railgun";
        m.tileX = 1;
        m.tileY = 4;
        m.state = com.ringsofiron.uihud.providers.WeaponStatusProvider.State.RELOADING;
        c.refresh(panels.boards, d);
        assertEquals("RAILGUN, RELOADING", c.mountLine, "the mount at the gun's own tile");
    }

    @Test
    void theSwitchIsAClickAloneAndSaysWhatItDid() {
        Entity e = console("helm", "Helm", SubsystemType.HELM, MountFootprint.MountKind.AUXILIARY);
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        ConsolePanel c = panels.console;
        assertEquals(-1, c.page.keys.key(com.badlogic.gdx.Input.Keys.ENTER), "Enter never switches a system off");
        c.page.clicked(ConsolePanel.KEY + ConsolePanel.SWITCH);
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        assertFalse(s.switchedOn);
        c.refresh(panels.boards, d);
        assertEquals("OFF", c.state);
        assertEquals("HELM SWITCHED OFF", c.response);
    }

    @Test
    void theConsoleClosesWhenTheGameLetsGoOfIt() {
        console("helm", "Helm", SubsystemType.HELM, MountFootprint.MountKind.AUXILIARY);
        assertTrue(panels.open(ClassicWindow.CONSOLE, d));
        d.console.clear();
        assertFalse(panels.console.available(panels.boards, d));
        assertFalse(panels.open(ClassicWindow.CONSOLE, d), "with no console E found, nothing opens");
    }

    @Test
    void consoleStatesAreTheGamesWords() {
        assertEquals("DESTROYED", ConsolePanel.stateOf(true, true, true));
        assertEquals("OFF", ConsolePanel.stateOf(false, false, true));
        assertEquals("NO POWER", ConsolePanel.stateOf(false, true, false));
        assertEquals("ONLINE", ConsolePanel.stateOf(false, true, true));
    }

    @Test
    void theControlsCardSaysWhatTheGamesKeysOpenHere() {
        com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint k = new com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint();
        k.action = "CREW";
        k.label = "crew";
        assertTrue(ControlsPage.label(k).startsWith("the slate"));
        k.label = "crew (wounded need a medic)";
        assertEquals("crew (wounded need a medic)", ControlsPage.label(k), "one that says why it shows now keeps its words");
        k.action = "MAP";
        k.label = "map";
        assertTrue(ControlsPage.label(k).startsWith("the Plot"));
        k.action = "MISSIONS";
        k.label = "missions held";
        assertEquals("the contracts held, on the slate", ControlsPage.label(k));
    }

    @Test
    void f1OpensTheCardOnTheKeyboardAndItStandsForTheControls() {
        assertTrue(panels.open(ClassicWindow.HELP, d));
        assertSame(panels.controls, panels.current);
        assertSame(panels.controls.keyboard, panels.controls.page);
        assertTrue(panels.showing(ClassicWindow.HELP));
        panels.controls.clicked(ControlsCard.TAB + 1);
        assertSame(panels.controls.gamepad, panels.controls.page);
        assertTrue(panels.showing(ClassicWindow.HELP), "either side stands for it");
        assertFalse(panels.controls.beside(), "the top column stays where it is over a card");
        assertNull(panels.controls.pageFor(ClassicWindow.LOG, panels.boards, d));
    }

    @Test
    void theShipyardIsALinkPageAcrossTheScreenAndAPrizeInTowOpensItAlone() {
        ShipyardProvider.Yard offer = new ShipyardProvider.Yard();
        d.shipyard = new ShipyardProvider() {
            @Override
            public void collect(ShipyardProvider.Yard out) {
                out.stationName = offer.stationName;
                out.prize = offer.prize;
                out.ships.addAll(offer.ships);
            }

            @Override
            public String buy(String id) {
                return "";
            }

            @Override
            public String sellPrize() {
                return "";
            }

            @Override
            public String turnInPrize() {
                return "";
            }
        };
        d.navReadout.docked.present = true;
        d.navReadout.docked.name = "Anvil Yards";
        d.navReadout.docked.shipyard = true;
        offer.stationName = "Anvil Yards";
        ShipyardProvider.Ship gunship = new ShipyardProvider.Ship();
        gunship.id = "gunship";
        gunship.name = "Gunship";
        gunship.seller = "Anvil Yards";
        gunship.terms = "360,000 cr, 207,000 cr after the trade-in";
        offer.ships.add(gunship);
        assertTrue(panels.open(ClassicWindow.SHIPYARD, d));
        assertSame(panels.link, panels.current);
        assertSame(panels.link.shipyard, panels.link.page);
        assertTrue(panels.showing(ClassicWindow.SHIPYARD));
        assertTrue(panels.link.shipyard.wide(), "across the screen");
        assertEquals("ANVIL YARDS", panels.link.frame.title);

        // Away from a station, a prize in tow off one that buys it opens the link on the Shipyard alone
        panels.closeAll();
        d.navReadout.docked.present = false;
        d.navReadout.docked.name = "";
        offer.stationName = "";
        offer.ships.clear();
        offer.prize = "Raider 'Vex' (taken), off Highport";
        assertTrue(panels.open(ClassicWindow.SHIPYARD, d));
        assertSame(panels.link.shipyard, panels.link.page);
        assertEquals("HIGHPORT", panels.link.frame.title);
        assertEquals("PRIZE IN TOW", panels.link.frame.owner);
        assertFalse(panels.open(ClassicWindow.TRADE, d), "no market away from a station");
    }
}
