package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.systems.InteractPromptSystem;

import java.util.Locale;

/**
 * The Stations spec's console panels (10.2), which E at a deck console hangs at the right, a tab for each
 * page of the station the console hosts, every page with its controls: Engineering at the reactor and at
 * life support (Power, Atmos, Damage, Systems, Tanks, and Shields on a hull with an emitter), and the gun
 * panel at a gun. The title bar names the station and the console, with the console's system's state.
 *
 * <p>A panel opens on the tab that matters (10.1): the one where the most severe, then newest, live alert
 * its station owns is acted on, else the console's own (Power at the reactor, Atmos at life support). It
 * belongs to its console (4): walking out of its reach closes it, and so does sitting down at a gun or
 * standing up from one, since the gun panel is the gun's from the deck and the sight's once in hand.
 */
final class ConsolePanels {
    /**
     * Tiles from its console past which the captain has walked away from a panel: a little past the reach E
     * has there, so it doesn't flicker shut at the edge.
     */
    static final float WALK_AWAY = InteractPromptSystem.INTERACT_RADIUS + 0.5f;
    /** Seconds a panel waits for the game to say how far the captain is before it gives up on them. */
    static final float REACH_WAIT = 0.5f;

    final BigConsole engineering, gun;
    final PowerPage power = new PowerPage();
    final AtmosPage atmos = new AtmosPage();
    final SystemsPage damage, switches;
    final TanksPage tanks = new TanksPage();
    final ShieldsPage shields = new ShieldsPage();
    final GunPage gunPage = new GunPage();
    private final Array<BigConsole> all = new Array<>();
    private final Boards boards;
    /** The console the panel up was opened at, and where the captain was then. */
    private Entity at;
    private Presence presence;
    private float openFor;
    private boolean reachSeen;

    ConsolePanels(Boards boards, FireTrend fires) {
        this.boards = boards;
        damage = new SystemsPage(fires, SystemsPage.Side.PLAN);
        switches = new SystemsPage(fires, SystemsPage.Side.LIST);
        engineering = new BigConsole(BigConsole.Livery.SHIP, "ENGINEERING", boards).consolePanel()
                .add(power).add(atmos).add(damage).add(switches).add(tanks).add(shields);
        gun = new BigConsole(BigConsole.Livery.SHIP, "GUN", boards).consolePanel().add(gunPage);
        all.add(engineering);
        all.add(gun);
    }

    /** The panels, for the windows to put on their canvas. */
    Array<BigConsole> all() {
        return all;
    }

    /** Whether {@code c} is one of these panels. */
    boolean owns(BigConsole c) {
        return c != null && all.contains(c, true);
    }

    /**
     * The tab to raise for the console E has just opened ({@code d.console}), read just now; null where
     * there is none to show. Remembers the console, and where the captain is, to know when they leave it.
     */
    Page open(HudData d) {
        HudData.ConsoleView v = d.console;
        if (!v.isOpen()) return null;
        at = v.console;
        presence = d.ctx.presence;
        openFor = 0f;
        reachSeen = false;
        boards.refresh(d);
        for (BigConsole c : all) for (int i = 0; i < c.pages.size; i++) c.pages.get(i).refresh(boards);
        SubsystemComponent s = v.console.getComponent(SubsystemComponent.class);
        String id = v.data.consoleId;
        if ("turret_mount".equals(id)) return gunPage.offered() ? gunPage : null;
        // The helm's and the weapons console's panels are their stations' own, which aren't drawn yet
        if ("helm".equals(id) || "weapons".equals(id)) return null;
        // Engineering: every console that hosts no other station (the reactor, life support)
        switches.focus(s);
        power.focus(s);
        atmos.focus(s);
        Page alert = tabFor(d.alerts);
        if (alert != null && alert.offered()) return alert;
        Page own = "life_support".equals(id) ? atmos : "reactor".equals(id) ? power : switches;
        if (own.offered()) return own;
        for (int i = 0; i < engineering.pages.size; i++) if (engineering.pages.get(i).offered()) return engineering.pages.get(i);
        return null;
    }

    /**
     * Engineering's tab for its most severe, then newest, live alert (10.1): Damage for a fire, a breach,
     * a room's air or the hull, Power for a system down, Atmos for the oxygen tanks; null with none.
     */
    Page tabFor(AlertRegistry alerts) {
        AlertRegistry.Alert best = null;
        Array<AlertRegistry.Alert> list = alerts.all();
        for (int i = 0; i < list.size; i++) {
            AlertRegistry.Alert a = list.get(i);
            if (tab(a) == null) continue;
            if (best == null || a.level.ordinal() > best.level.ordinal() || (a.level == best.level && a.since > best.since)) best = a;
        }
        return best == null ? null : tab(best);
    }

    /** Engineering's tab where {@code a} is acted on, or null when it isn't Engineering's. */
    private Page tab(AlertRegistry.Alert a) {
        String id = a.id;
        if (id.startsWith("fire:") || id.startsWith("breach:") || id.startsWith("air:") || id.startsWith("smoke:") || id.equals("hull")) return damage;
        if (id.equals("oxygen")) return atmos;
        if (!id.startsWith("sys:")) return null;
        // A system's own station has it: the guns, the weapons console and the shields are Tactical's, the
        // helm and the engines the Helm's, a docking port Comms'
        String what = id.substring(4);
        if (what.startsWith("TURRET") || what.startsWith("WEAPONS") || what.startsWith("SHIELDS") || what.startsWith("HELM")
                || what.startsWith("ENGINE") || what.startsWith("DOCK PORT")) return null;
        return power;
    }

    /**
     * Whether the captain has left the console of panel {@code c}, up now, {@code delta} seconds on: walked
     * out of its reach, sat down at a gun or stood up from it, or the game has shut the console. While the
     * game can't say how far they are (NaN), a seated captain stays at it, and one on deck is given
     * {@link #REACH_WAIT} for the game to say.
     */
    boolean left(BigConsole c, HudData d, float delta) {
        if (!d.console.isOpen() || d.console.console != at) return true;
        if (c == gun && d.ctx.presence != presence) return true;
        openFor += delta;
        float reach = d.console.reach;
        if (Float.isNaN(reach)) {
            if (seated(d.ctx.presence)) return false;
            return reachSeen || openFor > REACH_WAIT;
        }
        reachSeen = true;
        return reach > WALK_AWAY;
    }

    /** Whether the captain is in a seat: the helm, the weapons console or a gun. */
    static boolean seated(Presence p) {
        return p == Presence.HELM || p == Presence.WEAPONS || p == Presence.GUN;
    }

    /** Puts the console's name, where it stands and its system's state on panel {@code c}'s title bar. */
    void title(BigConsole c, HudData d) {
        SubsystemComponent s = at == null ? null : at.getComponent(SubsystemComponent.class);
        if (s == null) {
            c.consoleName = d.console.data == null ? "CONSOLE" : Fmt.upper(d.console.data.title);
            c.consoleWhere = c.consoleState = "";
            c.consoleLamp = null;
            return;
        }
        boolean own = ship(d) == d.ctx.ship;
        Mount m = own && s.type == SubsystemComponent.SubsystemType.TURRET_MOUNT ? ShipSystems.mount(s, d.weaponFrame.mounts) : null;
        c.consoleName = ShipSystems.name(s, m);
        String where = m != null && !m.name.isEmpty() ? m.name.toUpperCase(Locale.ROOT)
                : own && s.footprint != null ? d.roomAt(s.footprint.originX, s.footprint.originY) : "";
        c.consoleWhere = where == null ? "" : where;
        ShipSystems.State st = ShipSystems.state(s);
        float t = ShipSystems.timer(s, st);
        c.consoleState = Float.isNaN(t) ? st.label : st.label + " " + ShipSystems.seconds(t);
        c.consoleLamp = st;
    }

    /** The ship whose console is open: the player's own, or a prize's they have crossed to; the player's with none open. */
    static Entity ship(HudData d) {
        if (d == null) return null;
        HudData.ConsoleView v = d.console;
        return v.data != null && v.data.ship != null ? v.data.ship : d.ctx.ship;
    }

    /** The open console's own system, or null. */
    static SubsystemComponent system(HudData d) {
        return d == null || d.console.console == null ? null : d.console.console.getComponent(SubsystemComponent.class);
    }
}
