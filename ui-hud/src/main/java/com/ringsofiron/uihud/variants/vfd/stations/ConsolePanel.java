package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.HudData.ConsoleAction;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * A console's panel (functional-stations 10.2), in place of the game's console window: E at a console
 * opens it, E or Esc closes it. Its pages are those of the stations the console hosts, the same pages
 * as the MFDs', repeated beside the page keys and read-only: the helm's Helm, Navigation and Comms (the
 * Helm console panel), the weapons console's Contacts, Weapons and Defense (the Tactical board), a
 * gun's mount (the Gun panel), the reactor's and life support's Power, Atmos and Damage (Engineering).
 * Under them is the console itself, as the game's window had it: its system and how it is, who works
 * it, its switch on a soft key, and the game's own orders at it ("Take fire control") with their keys.
 *
 * <p>It opens on the page of the most severe, then newest, live alert owned by a station it hosts, and
 * with none on its focus page: Helm at the helm, Contacts at the weapons console, Power at the reactor,
 * Atmos at life support (10.1). Navigation's soft key opens the Plot. The game's keys at a console stay
 * the game's (V sits the player at it); the switch is a click alone, so Enter never switches a system off.
 */
final class ConsolePanel extends TerminalPanel {
    /** Click ids: a soft key, one of the game's orders at the console. */
    static final int KEY = Panels.PAGE_IDS, ORDER = Panels.PAGE_IDS + 20;
    static final int MAX_ORDERS = 4;
    /** The soft keys: the console's switch, and Navigation's Plot. */
    static final int SWITCH = 0, PLOT = 1;
    /** The orders' pitch, the repeaters' gap, and a value's column after its label. */
    static final float ORDER_ROW = 26f, GAP = 12f, VALUE = 110f;

    final ConsolePage helm = new ConsolePage("HELM", false, Mfds.Page.HELM, Mfds.Page.DOCK);
    final ConsolePage nav = new ConsolePage("NAVIGATION", true, Mfds.Page.NAV, Mfds.Page.PLAN);
    final ConsolePage comms = new ConsolePage("COMMS", false, Mfds.Page.COMMS);
    final ConsolePage contacts = new ConsolePage("CONTACTS", false, Mfds.Page.SENSORS);
    final ConsolePage weapons = new ConsolePage("WEAPONS", false, Mfds.Page.WEAPONS);
    final ConsolePage defense = new ConsolePage("DEFENSE", false, Mfds.Page.DEFENSE);
    final ConsolePage mount = new ConsolePage("MOUNT", false, Mfds.Page.WEAPONS);
    final ConsolePage power = new ConsolePage("POWER", false, Mfds.Page.POWER);
    final ConsolePage atmos = new ConsolePage("ATMOS", false, Mfds.Page.ATMOS);
    final ConsolePage damage = new ConsolePage("DAMAGE", false, Mfds.Page.DAMAGE);
    /** A console no station hosts: its system alone. */
    final ConsolePage system = new ConsolePage("SYSTEM", false);
    final Page[] helmPages = {helm, nav, comms}, tacticalPages = {contacts, weapons, defense}, gunPages = {mount},
            engineeringPages = {power, atmos, damage}, otherPages = {system};
    private final Page[] every = {helm, nav, comms, contacts, weapons, defense, mount, power, atmos, damage, system};
    /** The MFDs' painter, for the pages these repeat: read each frame, as the band's. */
    final Pages viewer = new Pages();

    /** The console open: its id and entity; its pages' title ("HELM") and its own name ("HELM CONSOLE"). */
    String consoleId = "", group = "", consoleName = "";
    Entity console;
    ConsolePage focus = system;
    /** Its system: what it is, how it is, its health (NaN with none), and whether it has a switch. */
    String systemName = "", state = "";
    boolean stateBad, switchable, switchedOn;
    float health = Float.NaN;
    /** Who works it: "YOU", a crew member, "NOBODY", or "" when the board has no post for it. */
    String worked = "";
    /** A gun's mount: its weapon, how it is and what it has left ("RAILGUN, READY"); "" elsewhere. */
    String mountLine = "";
    /** The game's orders at it: their words and the caps of their keys. */
    final String[] orderWord = new String[MAX_ORDERS], orderCap = new String[MAX_ORDERS];
    int orders;
    /** On deck at the helm, V sits the player at it: that key's cap, else "". */
    String sitCap = "";
    /** The game's map key, for Navigation's Plot key. */
    String mapCap = "M";
    /** Where the console's own block starts on the page showing, and its orders' first row. */
    float blockY, ordersY;

    @Override
    Page[] pages() {
        return otherPages;
    }

    @Override
    void attach(Panels p) {
        super.attach(p);
        for (Page pg : every) pg.attach(p, this);
    }

    /** The pages of the stations the console {@code id} hosts. */
    Page[] pagesFor(String id) {
        switch (id) {
            case "helm":
                return helmPages;
            case "weapons":
                return tacticalPages;
            case "turret_mount":
                return gunPages;
            case "reactor":
            case "life_support":
                return engineeringPages;
            default:
                return otherPages;
        }
    }

    /** The page the console {@code id} opens on with no alert of its stations live (10.1). */
    ConsolePage focusFor(String id) {
        switch (id) {
            case "helm":
                return helm;
            case "weapons":
                return contacts;
            case "turret_mount":
                return mount;
            case "reactor":
                return power;
            case "life_support":
                return atmos;
            default:
                return system;
        }
    }

    /** The title over its pages: the stations it hosts, as the board names them. */
    static String groupFor(String id) {
        switch (id) {
            case "helm":
                return "HELM";
            case "weapons":
                return "TACTICAL";
            case "turret_mount":
                return "GUN";
            case "reactor":
            case "life_support":
                return "ENGINEERING";
            default:
                return "CONSOLE";
        }
    }

    @Override
    boolean available(Boards b, HudData d) {
        return d.console.isOpen();
    }

    @Override
    boolean opens(ClassicWindow w, Boards b, HudData d) {
        return w == ClassicWindow.CONSOLE && d.console.isOpen();
    }

    /** Each E opens it on the page that matters now, for the console E was pressed at. */
    @Override
    boolean open(ClassicWindow w, Boards b, HudData d) {
        if (!opens(w, b, d)) return false;
        use(d);
        page = null;
        return super.open(w, b, d);
    }

    /** Turns to the pages of the console open in {@code d}, the first time it is this one. */
    void use(HudData d) {
        String id = d.console.data == null ? "" : d.console.data.consoleId;
        Page[] set = pagesFor(id);
        if (set != pages) {
            pages = set;
            on = new boolean[set.length];
            page = null;
        }
        consoleId = id;
        focus = focusFor(id);
        group = groupFor(id);
    }

    /** The page of the most severe, then newest, live alert whose home page one of its pages repeats; else its focus page. */
    @Override
    Page pageFor(ClassicWindow w, Boards b, HudData d) {
        if (w != ClassicWindow.CONSOLE) return null;
        boolean shields = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        Alert best = null;
        Page bestPage = null;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.level == Level.ADVISORY) continue;
            Page p = repeating(Mfds.home(a, shields));
            if (p == null) continue;
            if (best == null || a.level.ordinal() > best.level.ordinal() || (a.level == best.level && a.since > best.since)) {
                best = a;
                bestPage = p;
            }
        }
        return bestPage != null ? bestPage : focus;
    }

    /** The page of this console's that repeats MFD page {@code home}, or null. */
    Page repeating(Mfds.Page home) {
        if (home == null) return null;
        for (Page p : pages) for (Mfds.Page m : ((ConsolePage) p).mfd) if (m == home) return p;
        return null;
    }

    // ------------------------------------------------------------------ reading

    @Override
    void refresh(Boards b, HudData d) {
        read(d);
        super.refresh(b, d);
    }

    /** The console as it is now: its system, who works it, the game's orders at it. */
    void read(HudData d) {
        HudData.ConsoleView v = d.console;
        console = v.console;
        String title = v.data == null || v.data.title == null ? "" : v.data.title.toUpperCase(Locale.ROOT);
        SubsystemComponent s = console == null ? null : console.getComponent(SubsystemComponent.class);
        switchable = s != null;
        if (s != null) {
            switchedOn = s.switchedOn;
            health = s.health;
            state = stateOf(s.isDestroyed(), s.switchedOn, s.powered);
            stateBad = s.isDestroyed() || (s.switchedOn && !s.powered);
            systemName = s.type == null ? title : s.type.name().replace('_', ' ');
        } else {
            switchedOn = false;
            health = Float.NaN;
            state = "";
            stateBad = false;
            systemName = title;
        }
        Seat seat = seat(d);
        boolean gun = "turret_mount".equals(consoleId);
        consoleName = gun && seat != null && !seat.name.isEmpty() ? seat.name.toUpperCase(Locale.ROOT)
                : title.isEmpty() ? "" : gun ? title : title + " CONSOLE";
        worked = seat == null ? "" : who(seat);
        mountLine = gun && v.data != null ? mountLine(d, v.data.tileX, v.data.tileY) : "";
        orders = 0;
        for (int i = 0; i < v.actions.size && orders < MAX_ORDERS; i++) {
            ConsoleAction a = v.actions.get(i);
            orderWord[orders] = a.label.toUpperCase(Locale.ROOT);
            orderCap[orders] = a.action.isEmpty() ? "" : PlotPlan.cap(d.key(a.action));
            orders++;
        }
        boolean onDeck = d.ctx.presence == Presence.DECK;
        sitCap = "helm".equals(consoleId) && onDeck ? PlotPlan.cap(d.key("VIEW")) : "";
        String map = PlotPlan.cap(d.key("MAP"));
        mapCap = map.isEmpty() ? "M" : map;
    }

    /** The mount whose system sits at {@code x, y} on the grid, as the weapons' board has it: "RAILGUN, RELOADING", or "". */
    static String mountLine(HudData d, int x, int y) {
        Array<Mount> mounts = d.weaponFrame.mounts;
        for (int i = 0; i < mounts.size; i++) {
            Mount m = mounts.get(i);
            if (m.tileX != x || m.tileY != y) continue;
            String weapon = m.weapon == null || m.weapon.isEmpty() ? "" : m.weapon.toUpperCase(Locale.ROOT) + ", ";
            String left = m.ammo >= 0 ? ", " + m.ammo + " LEFT" : "";
            return weapon + m.state.name().replace('_', ' ') + left;
        }
        return "";
    }

    /** "ONLINE", "OFF", "NO POWER", "DESTROYED", as the game's window says how a system is. */
    static String stateOf(boolean destroyed, boolean on, boolean powered) {
        return destroyed ? "DESTROYED" : !on ? "OFF" : powered ? "ONLINE" : "NO POWER";
    }

    /** The board's post for this console, or null. */
    private Seat seat(HudData d) {
        if (console == null) return null;
        Array<Seat> seats = d.stations.seats;
        for (int i = 0; i < seats.size; i++) if (seats.get(i).console == console) return seats.get(i);
        return null;
    }

    /** Who works a post: "YOU", the crew member, "ADA, ON THE WAY", or "NOBODY". */
    static String who(Seat s) {
        String name = s.who == null ? "" : s.who.toUpperCase(Locale.ROOT);
        switch (s.hand) {
            case PLAYER:
                return "YOU";
            case CREW:
                return name.isEmpty() ? "CREW" : name;
            case COMING:
                return (name.isEmpty() ? "CREW" : name) + ", ON THE WAY";
            default:
                return "NOBODY";
        }
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = group;
        frame.owner = consoleName;
        frame.figs = 0;
        if (!Float.isNaN(health)) {
            frame.figLabel[0] = "HEALTH";
            frame.figValue[0] = Integer.toString(Math.round(health * 100f));
            frame.figUnit[0] = "%";
            frame.figCells[0] = 3;
            frame.figs = 1;
        }
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        // The pages it repeats are read each frame, as the band reads the MFDs'
        if (page != null) for (Mfds.Page m : ((ConsolePage) page).mfd) viewer.read(m, d);
        super.update(b, d, delta);
    }

    /** E closes it, as Esc does; Tab steps its pages. */
    @Override
    boolean key(int keycode) {
        if (keycode == Keys.E) {
            panels.closeAll();
            return true;
        }
        return super.key(keycode);
    }

    // ------------------------------------------------------------------ orders

    /** Soft key {@code k} on page {@code p}: the switch, or Navigation's Plot. */
    void press(ConsolePage p, int k) {
        if (k == SWITCH) {
            SubsystemComponent s = console == null ? null : console.getComponent(SubsystemComponent.class);
            if (s == null) return;
            s.switchedOn = !s.switchedOn;
            panels.say(systemName + (s.switchedOn ? " SWITCHED ON" : " SWITCHED OFF"));
            panels.refresh();
        } else if (k == PLOT && p.plot) {
            UiHud h = panels.gameHud();
            if (h != null) h.open(ClassicWindow.MAP);
        }
    }

    /** The game's order {@code i} at the console, as its button in the game's window. */
    void order(int i) {
        HudData d = panels.data();
        if (d == null || i < 0 || i >= d.console.actions.size) return;
        d.console.actions.get(i).run.run();
        panels.refresh();
    }

    // ------------------------------------------------------------------ the page

    /**
     * A page of a console's panel: the MFD pages it repeats beside the page keys, then the console's own
     * block under them; its soft keys the switch (a click alone) and, on Navigation, the Plot.
     */
    final class ConsolePage extends Page {
        final Mfds.Page[] mfd;
        /** Navigation's: its second soft key opens the Plot. */
        final boolean plot;

        ConsolePage(String name, boolean plot, Mfds.Page... mfd) {
            super(name, ClassicWindow.CONSOLE, "");
            this.mfd = mfd;
            this.plot = plot;
            keys.define(plot ? 2 : 1, plot ? 2 : 1).cap(SWITCH, "");
        }

        @Override
        void refresh(Boards b, HudData d) {
            keys.set(SWITCH, switchable ? (switchedOn ? "SWITCH OFF" : "SWITCH ON") : "", switchable);
            if (plot) keys.set(PLOT, "OPEN PLOT", true).cap(PLOT, mapCap);
        }

        @Override
        void paint(Filament f, float time) {
            float x0 = at.x0, x1 = at.x1, my = at.top - 8f;
            for (Mfds.Page m : mfd) {
                ShipPage.repeater(f, viewer, m, at.left - 4f, my);
                my += ShipPage.VIEW_H + GAP;
            }
            float y = Math.max(at.split, my) + 4f;
            blockY = y;
            Glass.head(f, x0, y, x1, "THIS CONSOLE");
            y += 20f;
            int cells = Glass.cells(x1 - x0 - VALUE);
            Glass.label(f, x0, y + 9.5f, "SYSTEM");
            float sx = x0 + VALUE + Glass.text(f, x0 + VALUE, y, Glass.cut(systemName, cells), Drive.NORM);
            if (!state.isEmpty()) {
                String st = Glass.cut(", " + state, Math.max(0, cells - systemName.length()));
                if (stateBad) Glass.text(f, sx, y, st, Drive.NORM, Phosphor.AMBER);
                else Glass.text(f, sx, y, st, switchedOn ? Drive.NORM : Drive.DIM);
            }
            y += 16f;
            if (!mountLine.isEmpty()) {
                Glass.label(f, x0, y + 9.5f, "MOUNT");
                Glass.text(f, x0 + VALUE, y, Glass.cut(mountLine, cells), Drive.NORM);
                y += 16f;
            }
            if (!worked.isEmpty()) {
                Glass.label(f, x0, y + 9.5f, "WORKED BY");
                Glass.text(f, x0 + VALUE, y, Glass.cut(worked, cells), worked.equals("NOBODY") ? Drive.DIM : Drive.NORM);
                y += 16f;
            }
            if (orders == 0 && sitCap.isEmpty()) {
                ordersY = y;
                return;
            }
            y += 14f;
            Glass.head(f, x0, y, x1, "ORDERS HERE");
            y += 22f;
            ordersY = y;
            for (int i = 0; i < orders; i++) {
                if (y + Glass.KEY_H > at.bottom) break;
                Glass.capWord(f, x0, y, caps(i), Glass.cut(orderWord[i], Glass.cells(x1 - x0) - 6), Drive.NORM);
                y += ORDER_ROW;
            }
            // The helm has no order of its own here: V sits the player at it, from the deck
            if (!sitCap.isEmpty() && y + Glass.KEY_H <= at.bottom) {
                sit[0] = sitCap;
                Glass.capWord(f, x0, y, sit, "TAKES THE HELM", Drive.DIM);
            }
        }

        private final String[] one = new String[1], sit = new String[1];

        private String[] caps(int i) {
            one[0] = orderCap[i];
            return one;
        }

        @Override
        void boxes(FilamentActor a) {
            keys.boxes(a, KEY);
            for (int i = 0; i < orders; i++) {
                float oy = ordersY + i * ORDER_ROW;
                if (oy + Glass.KEY_H > at.bottom) break;
                a.box(ORDER + i, at.x0 - 2f, oy - 3f, at.x1 - at.x0 + 4f, ORDER_ROW - 2f);
            }
        }

        @Override
        void clicked(int id) {
            if (id >= KEY && id < KEY + keys.count()) {
                if (keys.can(id - KEY)) press(this, id - KEY);
            } else if (id >= ORDER && id < ORDER + MAX_ORDERS) {
                order(id - ORDER);
            }
        }
    }
}
