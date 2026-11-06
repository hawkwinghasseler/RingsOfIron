package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.cassette.YardCanvas;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardScreens;
import com.ringsofiron.uihud.widgets.MapPainter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cassette-futurism art (the Yard Standard, documentation/ui-overhaul/style-cassette.md) on the Ship
 * Stations functional spec (documentation/ui-overhaul/functional-stations.md): the ship is the interface.
 * Seated at the helm or the weapons console, a band of hardware stands at the foot of the screen, two
 * MFDs either side of the seat's cluster and the station board on its sill under them; at a gun, only the
 * gunner sight and the gun's own tile; on deck, nothing but the suit readout and the prompt line. Over every
 * presence the top column hangs from its rack: the status strip, the annunciator, the warp strip and the
 * callout ticker, each unit there only while it has something to say.
 *
 * <p>The whole HUD is one actor ({@link YardCanvas}) painting every unit; the units draw in their mockups'
 * coordinates and {@link StationsLayout} places them each frame. The classic HUD keeps only its world
 * overlays: the contact markers and the missile paths.
 *
 * <p>Keys (16.2), held with Alt so they never take a game binding: Alt+1 to 6 puts that station on the
 * right MFD, again its next page, with Shift the left; Alt+0 pins, Alt+minus folds (Shift for the left);
 * Alt+O acknowledges the master lamps.
 *
 * <p>The screens are the Yard Standard's, the same under the three Cassette HUDs ({@link YardScreens}):
 * the menus, the map with its bays, and the game's windows as consoles. A screen hides the band (10.1)
 * and starts under the annunciator, so the status strip and the annunciator with its master lamps stay
 * above it, hung whether or not they have anything to say, so they don't move while it is up. The top
 * column is on a canvas of its own in the screen layer, over the map and the consoles; the telex of the
 * ship lost or the career over has the whole screen.
 */
public class CassetteStationsHud implements HudVariant, Actions {
    /** Where a screen starts: under the annunciator, the last unit hung above it. */
    static final float SCREEN_TOP = StationsLayout.ANN_Y + StationsLayout.ANN_H + 4f;

    private HudVariantHost host;
    private final StationsFrame frame = new StationsFrame();
    private final StationsLayout layout = new StationsLayout();
    private final Pages pages = new Pages();
    private YardPaint paint;
    private YardCanvas canvas;
    /** The top column's canvas, over the screens. */
    private YardCanvas over;
    private TopColumn top;
    private Mfd left, right;
    private HelmCluster helm;
    private TacticalCluster tac;
    private GunnerSight sight;
    private StationBoard board;
    private SuitReadout suit;
    private PromptLine prompt;
    private BerthCard berth;
    private GuideCard guide;
    private Presence lastPresence;
    /** The menus, the map and the windows in the Yard Standard. */
    private final YardScreens screens = new YardScreens();

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        paint = new YardPaint();
        paint.fonts.setScale(host.uiScale());
        canvas = new YardCanvas(paint);
        over = new YardCanvas(paint);
        top = new TopColumn(frame, this);
        left = new Mfd(frame, pages, false);
        right = new Mfd(frame, pages, true);
        helm = new HelmCluster(frame);
        tac = new TacticalCluster(frame);
        sight = new GunnerSight(frame);
        board = new StationBoard(frame, this);
        suit = new SuitReadout(frame);
        prompt = new PromptLine(frame);
        berth = new BerthCard(frame);
        guide = new GuideCard(frame);
        // Painted in this order: the sill under the units standing on it, the out-of-fiction card last; the
        // top column over everything, screens included
        for (YardPart p : new YardPart[]{board, left, right, helm, tac, sight, suit, prompt, berth, guide}) canvas.add(p);
        over.add(top);
        host.layer().addActor(canvas);
        canvas.setBounds(0f, 0f, host.width(), host.height());
        screens.attach(host, SCREEN_TOP);
        host.screenLayer().addActor(over);
        over.setBounds(0f, 0f, host.width(), host.height());
    }

    @Override
    public void update(float delta) {
        screens.update(delta);
        HudData d = host.data();
        UiHud hud = host.hud();
        // A screen is up (the map, a console in a window's place, the deck console's window, the telex): the band hides
        boolean telex = screens.showing(ClassicWindow.GAME_OVER);
        boolean map = hud.isMapOpen() || hud.windowOpen() || telex;
        StationsFrame f = frame;
        f.read(d, map);
        f.time += delta;
        f.alerts.update(d, delta, f.blackout, f.emitter, d.guideText);
        if (d.ctx.presence != lastPresence) {
            // Standing up (or sitting at another seat) lets every MFD choice go
            left.reset();
            right.reset();
            lastPresence = d.ctx.presence;
        }
        defaults();
        autoPage(d);
        helm.watch();
        suit.watch(delta);
        place(d, map);
        canvas.setBounds(0f, 0f, host.width(), host.height());
        over.setBounds(0f, 0f, host.width(), host.height());
        // The ship lost or the career over: the telex has the whole screen
        over.setVisible(!telex);
    }

    /** The MFDs' defaults from the seat and the condition (9.1); CARGO has no data yet, so COMMS stands in. */
    private void defaults() {
        StationsFrame f = frame;
        StationsFrame.Condition c = f.condition;
        if (f.seat == StationsFrame.Seat.HELM) {
            if (c == StationsFrame.Condition.RED) set(Page.SENSORS, false, Page.DAMAGE, false);
            else if (f.docked) set(Page.DOCK, false, Page.COMMS, true);
            else if (c == StationsFrame.Condition.YELLOW) set(Page.SENSORS, false, Page.NAV, true);
            else set(Page.NAV, true, Page.SENSORS, true);
        } else if (f.seat == StationsFrame.Seat.WEAPONS) {
            if (c == StationsFrame.Condition.RED) set(Page.SENSORS, false, f.emitter ? Page.DEFENSE : Page.DAMAGE, false);
            else if (c == StationsFrame.Condition.YELLOW) set(Page.SENSORS, false, Page.WEAPONS, true);
            else set(Page.SENSORS, true, Page.WEAPONS, true);
        }
    }

    private void set(Page l, boolean lFold, Page r, boolean rFold) {
        left.defaults(l, lFold);
        right.defaults(r, rFold);
    }

    /**
     * Auto-paging (9.1): a new caution or warning turns the right MFD to its home page, the left when the
     * right is pinned; a worse alert already on it keeps it. Not when the other MFD already shows that page.
     */
    private void autoPage(HudData d) {
        StationsFrame f = frame;
        Alert a = f.alerts.raised;
        if (a != null && f.commandSeat() && !f.map) {
            Page home = Page.home(a, f.emitter, d.ctx.disabledReason);
            Mfd m = !right.pinned ? right : !left.pinned ? left : null;
            Mfd other = m == right ? left : right;
            boolean shown = other.page == home && !other.showsFolded();
            if (m != null && !shown) m.take(a, home, d);
        }
        float clock = f.alerts.clock();
        left.tick(d, clock);
        right.tick(d, clock);
    }

    /** Spec section 14: what each presence shows, and how docking, warp and a blackout change it. */
    private void place(HudData d, boolean map) {
        StationsFrame f = frame;
        StationsLayout l = layout;
        l.width = host.width();
        l.height = host.height();
        top.screen = map;
        top.choose();
        l.strip = top.strip;
        l.ann = top.ann;
        l.warpStrip = top.warp;
        l.ticker = top.ticker;
        l.seat = f.seat;
        l.map = map;
        l.leftFolded = left.showsFolded();
        l.rightFolded = right.showsFolded();
        l.suit = f.deck() && suit.wanted();
        l.prompt = f.seat != StationsFrame.Seat.NONE && prompt.choose();
        boolean berthUp = berth.update();
        l.berth = berthUp && f.seat != StationsFrame.Seat.NONE;
        l.guideH = guide.measure(paint);
        l.solve();

        board.gun = f.seat == StationsFrame.Seat.GUN;
        board.berthBadge = berth.folded();
        put(top, l.column);
        put(left, l.mfdL);
        put(right, l.mfdR);
        put(helm, f.seat == StationsFrame.Seat.HELM ? l.cluster : null);
        put(tac, f.seat == StationsFrame.Seat.WEAPONS ? l.cluster : null);
        put(sight, l.sight);
        put(board, l.board);
        put(suit, l.suitR);
        put(prompt, l.promptR);
        put(berth, l.berthR);
        put(guide, l.guideR);
    }

    private static void put(YardPart p, StationsLayout.Rect r) {
        if (r == null || !r.on) {
            p.visible = false;
            return;
        }
        p.visible = true;
        p.place(r.x, r.y, r.w, r.h);
    }

    // --- Actions: what a press on one unit asks of the others ----------------------------------------

    @Override
    public void acknowledge() {
        if (host != null) host.data().alerts.acknowledgeAll();
    }

    @Override
    public void show(Page page) {
        if (frame.commandSeat()) right.pick(page);
    }

    @Override
    public void station(Station s) {
        if (frame.commandSeat()) right.cycle(s);
    }

    // --- Keys ----------------------------------------------------------------------------------------

    @Override
    public boolean keyDown(int keycode) {
        if (host == null || paint == null) return false;
        // A console that shows has the keys first
        if (screens.keyDown(keycode)) return true;
        frame.alerts.keyPressed(keycode);
        prompt.keyPressed(keycode);
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        if (!alt) return false;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        if (keycode == Input.Keys.O) {
            acknowledge();
            return true;
        }
        if (!frame.commandSeat()) return false;
        Mfd m = shift ? left : right;
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_6) {
            m.cycle(Station.byNumber(keycode - Input.Keys.NUM_0));
            return true;
        }
        if (keycode == Input.Keys.NUM_0) {
            m.pin();
            return true;
        }
        if (keycode == Input.Keys.MINUS) {
            m.fold();
            return true;
        }
        return false;
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        if (paint != null) paint.fonts.setScale(uiScale);
        if (canvas != null && host != null) canvas.setBounds(0f, 0f, host.width(), host.height());
        if (over != null && host != null) over.setBounds(0f, 0f, host.width(), host.height());
        screens.resize(uiScale);
    }

    @Override
    public void detach() {
        screens.dispose();
        if (over != null) over.remove();
        if (paint != null) paint.dispose();
        paint = null;
        canvas = null;
        over = null;
    }

    // --- The screens: the Yard Standard's menus, map and windows --------------------------------------

    @Override
    public boolean replaces(ClassicWindow window) {
        return screens.replaces(window);
    }

    @Override
    public boolean open(ClassicWindow window) {
        return screens.open(window);
    }

    @Override
    public boolean showing(ClassicWindow window) {
        return screens.showing(window);
    }

    @Override
    public void close(ClassicWindow window) {
        screens.close(window);
    }

    @Override
    public ShellLook shellLook() {
        return screens.shellLook();
    }

    @Override
    public MapPainter mapPainter() {
        return screens.mapPainter();
    }

    /** The world overlays stay: contact markers and missile paths. Everything else is the Yard's. */
    @Override
    public Set<ClassicPart> keeps() {
        return EnumSet.of(ClassicPart.WORLD_MARKERS, ClassicPart.THREAT_OVERLAY);
    }
}
