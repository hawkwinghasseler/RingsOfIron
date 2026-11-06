package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.Insets;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Optic;
import com.ringsofiron.uihud.variants.vfd.VfdScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

import java.util.EnumSet;
import java.util.Set;

/**
 * FILAMENT × Context: the 90s VFD art (documentation/ui-overhaul/style-vfd.md) on the context-driven
 * functional spec (documentation/ui-overhaul/functional-context.md), as the mockups in
 * documentation/ui-overhaul/vfd draw it. Two racks frame the view: the status strip along the top,
 * and the seat's instruments along the bottom with the primary instrument in a tower in the middle,
 * the master alert cue on its shelf, H4's lights on the left wing and H5 on the right. Everything that
 * comes and goes is a slab that powers up in a fixed slot of its zone: the caution panel, threat and
 * emergency cards, the feed, the objective, navigation, the lock card and armament, the station and
 * boarding panels, the captain's kit on deck, and the orders menu over the world.
 *
 * <p>Keys: backslash acknowledges (as a press of either master lamp does); 7 opens and closes the
 * orders menu (O stays the lights), and an order's own key closes it. Esc does not: the game reads
 * Esc itself, and with no window open it quits. Clicks: the master lamps
 * and the strip's chip acknowledge, the scope's key unfolds it and its − and + keys step its range,
 * and H4's lights hold its face open or let it fold.
 */
public class VfdContextHud implements HudVariant, Filament.Painter, FilamentActor.Clicks {
    static final int CLICK_CHIP = 4, CLICK_LIGHTS = 5;
    /** While X3 shows, everything but X3, P2, P3, H4, D1, D3 and the cue is on the dimmer bus at this drive. */
    static final float DIMMER = 0.45f;
    static final float GAP = 12f;

    private HudVariantHost host;
    private FilamentActor actor;
    final Situations sit = new Situations();
    final Cautions cautions = new Cautions();
    final Cautions.Shown shown = new Cautions.Shown();
    final TopRack top = new TopRack();
    final Rack rack = new Rack();
    final Flight flight = new Flight();
    final Nav nav = new Nav();
    final Interrupts interrupts = new Interrupts();
    final Feed feed = new Feed();
    final Goal goal = new Goal();
    final Condition condition = new Condition();
    final Scope scope = new Scope();
    final Lock lock = new Lock();
    final Arms arms = new Arms();
    final Shields shields = new Shields();
    final ThreatList threats = new ThreatList();
    final Panels panels = new Panels();
    final Captain captain = new Captain();
    final OrdersMenu orders = new OrdersMenu();
    final World world = new World();
    /** The menus in setup mode and the map's plot, the same under all three FILAMENT HUDs. */
    private final VfdScreens screens = new VfdScreens();
    /** The full-screen modes in place of the classic windows. */
    final Modes modes = new Modes(this);

    /** The groups' origins on the stage, and this frame's slots, on the grid. */
    float width = Geo.W, height = Geo.H, midX, rightX, bottomY;
    float p2Y, goalY, navY, threatsY, lockY, stripY, panelY, x3Y, faceFoot, shieldsFoot, shieldsH, promptY, ordersFoot, rackY;
    double now;
    float time;
    private final float[] centre = new float[4];

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        actor = new FilamentActor(this, this);
        host.layer().addActor(actor);
        modes.attach(host);
    }

    @Override
    public void update(float delta) {
        HudData d = host.data();
        float w = host.width(), h = host.height();
        actor.setBounds(0f, 0f, w, h);
        actor.setUnitsPx(host.uiScale());
        UiHud hud = host.hud();
        boolean fullScreen = modes.up() || hud != null && (hud.openWindow() != null || hud.isMapOpen());
        BoardingBoard board = hud == null || hud.getBoardingWindow() == null ? null : hud.getBoardingWindow().getBoard();
        layout(d, w, h, delta, fullScreen, board);
        boxes(actor);
        modes.update(delta);
    }

    /** Reads this frame's data into every part and places them, on a {@code w} x {@code h} stage. */
    void layout(HudData d, float w, float h, float delta, boolean fullScreen, BoardingBoard board) {
        width = w;
        height = h;
        midX = Geo.midX(w);
        rightX = Geo.rightX(w);
        bottomY = Geo.bottomY(h);
        time += delta;
        now = d.ctx.simSeconds;
        // A ship in reach is a boarding only once it is beaten, or ours (4.2): a fit one is still a fight
        boolean boardable = board != null && board.isOpen() && (board.disabled || board.boarding || board.taken);
        // Being disabled waits while power is due back by itself: last frame's NO POWER row says so
        sit.update(d, boardable, cautions.powerDueBack, fullScreen, delta);
        flight.update(d, sit);
        nav.update(d, sit);
        shields.update(d, sit);
        shown.seated = sit.exterior();
        shown.dockingDisplay = flight.docking;
        shown.planLine = hasPlan(nav);
        shown.shieldControl = shields.on;
        shown.flying = d.ctx.ship != null && !sit.docked && !sit.latched;
        cautions.update(d, shown, delta);
        top.update(d, sit, cautions);
        interrupts.update(d, sit, delta);
        feed.update(d.messages, sit.combat, now, delta);
        goal.update(d, sit);
        condition.update(d, sit, cautions, delta);
        scope.update(d, sit);
        lock.update(d, sit);
        arms.update(d, sit);
        threats.update(d, sit);
        panels.update(d, sit, board);
        captain.update(d, sit);
        orders.update(d, sit);
        rack.update(d, sit, cautions, scope.open || !scope.seated, scope.beyond, arms.rackHeight(), time);
        if (fullScreen) rack.tower = Rack.Tower.NONE;
        slots();
        worldLayout(d);
    }

    private static boolean hasPlan(Nav n) {
        for (int i = 0; i < n.count; i++) if (n.rows[i] == Nav.PLAN) return true;
        return false;
    }

    /** Each zone's widgets stacked from its outer edge inward (5.1, 5.2). */
    private void slots() {
        // Left: P2 at the top; the objective, then navigation or the threat list; H4, F3 or D1 at the bottom
        p2Y = Geo.TOP_Y;
        goalY = Geo.MID_Y;
        float y = goalY + (goal.on ? goal.height() + GAP : 0f);
        navY = y;
        threatsY = y + (nav.count > 0 ? nav.height() + GAP : 0f);
        boolean deck = sit.seat == Situations.Seat.DECK;
        faceFoot = deck ? (captain.vitals ? Geo.D1_Y - GAP : Geo.H - GAP) : Geo.FACE_B - 4f;
        shieldsFoot = Geo.FACE_B - 4f - (condition.on ? condition.height() + 6f : 0f);
        shieldsH = Math.min(Shields.FULL_H, shieldsFoot - Geo.BOT_Y);
        if (shields.on && condition.on) {
            // F3 shrinks to give H4 its face; the face keeps to the rack
            faceFoot = Geo.FACE_B - 4f;
        }
        // Right: the lock card, then the armament or the boarding panel; docked the station panel
        lockY = Geo.MID_Y;
        y = lockY + (lock.on ? lock.height() + GAP : 0f);
        stripY = y;
        panelY = y + (arms.stripHeight() > 0f ? arms.stripHeight() + GAP : 0f);
        // Top centre: P3, then X3 under it
        x3Y = Geo.TOP_Y + (interrupts.threat ? Interrupts.P3_H + 6f : 0f);
        // Bottom centre: the prompt over the primary instrument, the orders menu standing on it
        float instrument = rack.tower == Rack.Tower.NONE ? Rack.cueY(Rack.Tower.NONE, 0f) - 6f
                : Math.min(rack.towerTop(), rack.cueY - 6f);
        promptY = instrument - 8f - 24f;
        ordersFoot = instrument - 6f;
        rackY = rack.tower == Rack.Tower.GUN ? Rack.gunTop(arms.rackHeight()) + 46f : Geo.F2_Y;
    }

    /** The world's overlays, clear of every widget this frame. */
    private void worldLayout(HudData d) {
        world.clearBoxes();
        if (cautions.panel.size > 0 && !sit.fullScreen) world.box(Geo.LX, p2Y, Geo.CW, CautionPanel.height(cautions));
        float left = (goal.on ? goal.height() + GAP : 0f) + nav.height() + (nav.count > 0 ? GAP : 0f) + threats.height();
        world.box(Geo.LX, goalY, Geo.CW, left);
        if (condition.on) world.box(Geo.LX, bottomY + faceFoot - condition.height(), Geo.CW, condition.height());
        if (shields.on) world.box(Geo.LX, bottomY + shieldsFoot - shieldsH, Geo.CW, shieldsH);
        world.box(rightX + Geo.RX, Geo.TOP_Y, Geo.CW, feed.height());
        float right = (lock.on ? lock.height() + GAP : 0f) + arms.stripHeight() + panels.stationHeight() + panels.boardingHeight();
        world.box(rightX + Geo.RX, Geo.MID_Y, Geo.CW, right);
        world.box(midX + Geo.TC_X, Geo.TOP_Y, Geo.TC_W, interrupts.p3Height() + (interrupts.card ? 6f + interrupts.x3Height() : 0f));
        if (scope.open) {
            float r = Geo.SCOPE_R + 10f;
            world.box(rightX + Geo.SCOPE_CX - r, bottomY + Geo.SCOPE_CY - r, 2f * r, 2f * r);
        }
        float floor = bottomY + (rack.tower == Rack.Tower.NONE ? Geo.H : Geo.RACK_Y);
        if (rack.tower != Rack.Tower.NONE) {
            float half = rack.towerHalf(), mid = width / 2f, t = bottomY + Math.min(rack.towerTop(), rack.cueY - 6f);
            world.box(mid - half, t, 2f * half, floor - t);
        }
        if (captain.prompt) world.box(width / 2f - 320f, bottomY + promptY, 640f, 24f);
        centre[0] = Geo.LX + Geo.CW + 10f;
        centre[1] = Geo.TOP_Y + interrupts.p3Height() + (interrupts.card ? 6f + interrupts.x3Height() : 0f) + 6f;
        centre[2] = rightX + Geo.RX - 10f;
        centre[3] = bottomY + (rack.tower == Rack.Tower.NONE ? Geo.CUE_Y_DECK - 6f : Math.min(rack.towerTop(), rack.cueY - 6f)) - 6f;
        boolean navShown = flight.docking || nav.count > 0 && nav.face != Nav.Face.COMPACT || nav.count > 0 && nav.rows[0] == Nav.NAV_LINE;
        world.update(d, sit, host == null ? null : host.viewports(), width, height, floor, centre, lock.on, navShown, threats.on,
                interrupts.showTime || threats.on, flight.frame);
        captain.tags(d, sit, host == null ? null : host.viewports(), width, height, Geo.LX + Geo.CW + 8f, Geo.STRIP_B + 8f,
                rightX + Geo.RX - 8f, bottomY + Geo.CUE_Y_DECK - 10f);
    }

    /** The boxes that take clicks this frame: the master lamps and the chip, the scope's keys and H4's lights. */
    void boxes(FilamentActor a) {
        a.clearBoxes();
        if (sit.fullScreen) {
            if (top.chip) a.box(CLICK_CHIP, top.chipX, Geo.STRIP_Y, 66f, Geo.STRIP_H);
            return;
        }
        rack.boxes(a, midX, bottomY, rightX);
        scope.boxes(a, rightX, bottomY);
        if (rack.tower != Rack.Tower.NONE && rack.lights > 0) a.box(CLICK_LIGHTS, Geo.LIGHTS_X, bottomY + Geo.LIGHTS_Y, Geo.LIGHTS_W, Geo.LIGHTS_H);
    }

    @Override
    public void paint(Filament f) {
        boolean dim = interrupts.card;
        float w = width;
        // The projected world first: every widget covers it
        f.origin(0f, 0f);
        world.paint(f);
        // The racks
        TopRack.paintRack(f, w);
        if (!sit.fullScreen) {
            f.origin(0f, bottomY);
            rack.paintRack(f, w);
        }
        // What the dimmer bus carries while X3 shows
        f.origin(0f, 0f);
        top.paintLeft(f);
        f.origin(rightX, 0f);
        top.paintRight(f);
        if (!sit.fullScreen) {
            feed.paint(f);
            lock.paint(f, lockY);
            arms.paintStrip(f, stripY);
            if (panels.station) panels.paintStation(f, panelY);
            panels.paintBoarding(f, panels.station ? panelY + panels.stationHeight() + GAP : panelY);
            f.origin(0f, 0f);
            goal.paint(f, goalY);
            nav.paint(f, navY);
            threats.paint(f, threatsY);
            f.origin(0f, bottomY);
            shields.paint(f, shieldsFoot, shieldsH);
            f.origin(midX, bottomY);
            flight.paint(f, rack.tower);
            if (rack.tower == Rack.Tower.WEAPONS || rack.tower == Rack.Tower.GUN) arms.paintRack(f, rackY);
            if (!orders.open) captain.paintPrompt(f, promptY);
            f.origin(rightX, bottomY);
            scope.paint(f);
            rack.paintScopeKey(f);
        }
        if (dim) {
            f.origin(0f, 0f);
            f.dimRegion(0f, 0f, w, height, DIMMER);
        }
        // At full drive: P2, P3, X3, H4, D1, D3 and the master alert cue
        f.origin(0f, 0f);
        captain.paintTags(f);
        if (!sit.fullScreen) CautionPanel.paint(f, cautions, p2Y, now, time);
        // Over a mode, the mode puts them over itself (paintInterruptsOver)
        if (!modes.up()) {
            f.origin(midX, 0f);
            interrupts.paintThreat(f);
            interrupts.paintCard(f, x3Y, time);
        }
        if (sit.fullScreen) return;
        f.origin(0f, bottomY);
        rack.paintLights(f);
        condition.paint(f, faceFoot);
        captain.paintVitals(f);
        f.origin(midX, bottomY);
        rack.paintCue(f);
        // The orders menu over the world, on top of everything round it
        orders.paint(f, ordersFoot, time);
    }

    /**
     * P3 and X3 over a mode, in the mode's own glass (functional-context 10: they still interrupt),
     * with the mode on the dimmer bus while X3 shows.
     */
    void paintInterruptsOver(Filament f) {
        if (interrupts.card) {
            f.origin(0f, 0f);
            f.dimRegion(0f, 0f, width, height, DIMMER);
        }
        f.origin(midX, 0f);
        interrupts.paintThreat(f);
        interrupts.paintCard(f, x3Y, time);
        f.origin(0f, 0f);
    }

    @Override
    public void clicked(int id) {
        if (host != null) click(host.data(), id);
    }

    /** A click on box {@code id}. */
    void click(HudData d, int id) {
        switch (id) {
            case Rack.CLICK_WARN:
            case Rack.CLICK_CAUT:
            case CLICK_CHIP:
                acknowledge(d);
                break;
            case Rack.CLICK_SCOPE:
                scope.unfold();
                break;
            case Scope.CLICK_IN:
                scope.zoom(-1);
                break;
            case Scope.CLICK_OUT:
                scope.zoom(1);
                break;
            case CLICK_LIGHTS:
                condition.pinned = !condition.pinned;
                break;
            default:
        }
    }

    void acknowledge(HudData d) {
        cautions.acknowledge();
        d.alerts.acknowledgeAll();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (host == null) return false;
        // A mode's keys first: its tabs, its lists and its soft keys
        if (modes.keyDown(keycode)) return true;
        return key(host.data(), keycode);
    }

    /** A key the classic HUD did not use: true for the HUD's own two, which the game's bindings then leave alone. */
    boolean key(HudData d, int keycode) {
        if (keycode == Keys.BACKSLASH) {
            acknowledge(d);
            return true;
        }
        if (keycode == Keys.NUM_7 && d.ctx.ship != null && !sit.fullScreen) {
            orders.toggle();
            return true;
        }
        // An order's own key closes the menu, and the order still goes to the game
        if (orders.open && OrdersMenu.orderKey(d, keycode)) orders.open = false;
        return false;
    }

    @Override
    public boolean replaces(ClassicWindow window) {
        return modes.replaces(window);
    }

    @Override
    public boolean open(ClassicWindow window) {
        return modes.open(window);
    }

    @Override
    public boolean showing(ClassicWindow window) {
        return modes.showing(window);
    }

    @Override
    public void close(ClassicWindow window) {
        modes.close(window);
    }

    @Override
    public void detach() {
        modes.detach();
        if (actor != null) actor.dispose();
        actor = null;
        host = null;
        screens.dispose();
    }

    @Override
    public void panels(Array<Rectangle> out) {
        // W7: the insets are clamped to the world area, clear of every widget and the racks
        world.panels(out, width, height);
    }

    @Override
    public boolean drawsInsets() {
        return true;
    }

    @Override
    public void insetRoom(Insets.Room out) {
        out.set(Optic.SIDE, Optic.ABOVE, Optic.BELOW);
    }

    @Override
    public boolean wantsInset(Insets.Role role) {
        // The own ship, Lock and Nav while the world's overlays show (W7); at a gun they show the
        // Lock and hostiles only, so no Nav there
        return world.on && (role != Insets.Role.NAV || sit.seat != Situations.Seat.GUN);
    }

    @Override
    public Set<ClassicPart> keeps() {
        // Every classic piece has its place here: the panels in the racks and slabs, the missile banner
        // in P3, markers and threats in the world overlays, the status column in P1, P2, P4 and P5, the
        // key hints and the E prompt in P6, and the docked banner in X1
        return EnumSet.noneOf(ClassicPart.class);
    }

    @Override
    public ShellLook shellLook() {
        return screens.shellLook();
    }

    @Override
    public MapPainter mapPainter() {
        return screens.mapPainter();
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        screens.resize(uiScale);
    }
}
