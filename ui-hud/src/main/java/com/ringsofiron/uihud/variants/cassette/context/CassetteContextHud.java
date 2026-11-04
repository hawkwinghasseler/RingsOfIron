package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.cassette.YardCanvas;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.widgets.BoardingWindow;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cassette-futurism art (the Yard Standard, documentation/ui-overhaul/style-cassette.md) on the context
 * functional spec (documentation/ui-overhaul/functional-context.md): the HUD shows what the moment needs
 * and nothing else. Where the captain is (the helm, the weapons console, a gun, or on deck) decides the
 * instrument at the bottom centre and the units beside it, and what is happening (warp, an approach, a
 * berth, a fight, a boarding, an emergency) decides which of the rest come up. Every unit has its own zone
 * and stacks from the zone's edge, so one coming or going never moves another.
 *
 * <p>The whole HUD is one actor ({@link YardCanvas}) painting every unit; {@link ContextLayout} places
 * them each frame. The classic HUD keeps only its world overlays: the contact markers and the missile
 * paths.
 *
 * <p>Keys: backslash acknowledges the master cue; Alt+O swings the orders panel up over the seat's
 * instrument and down again. The panel shows the orders and the game's own keys give them, so a key that
 * gives a lasting order (a helm order, the autopilot, the shields) closes it on its way to the game.
 */
public class CassetteContextHud implements HudVariant {
    /** The actions whose keys close the orders panel as they reach the game; trim, approach and the guarded two keep it. */
    private static final String[] CLOSES = {"HELM_FLY_BURNS", "HELM_MATCH_VELOCITY", "HELM_HOLD", "HELM_DOCK",
            "HELM_CANCEL", "AUTOPILOT", "SHIELDS"};
    private static final String[] CLOSES_DEFAULT = {"1", "2", "3", "5", "0", "6", "9"};

    private HudVariantHost host;
    private final ContextFrame frame = new ContextFrame();
    private final ContextLayout layout = new ContextLayout();
    private final Conditions conditions = new Conditions();
    private final Conditions.Shown shown = new Conditions.Shown();
    private YardPaint paint;
    private YardCanvas canvas;

    private HeaderRail rail;
    private CautionBoard caution;
    private ThreatUnit threat;
    private EmergencyPlacard placard;
    private Intercom intercom;
    private Clipboard clipboard;
    private NavTube nav;
    private ThreatList threatList;
    private LockCard lock;
    private ArmamentPlate armament;
    private StationPanel station;
    private BoardingPanel boarding;
    private ConditionUnit condition;
    private ShieldControl shield;
    private SuitUnit suit;
    private Scope scope;
    private FlightPod flight;
    private DockingPod docking;
    private WeaponsRack rack;
    private MasterCue cue;
    private PromptPlate prompt;
    private OrdersPanel orders;
    private Coaming coaming;
    private Veil veil;
    private YardPart[] all = new YardPart[0];

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        paint = new YardPaint();
        paint.fonts.setScale(host.uiScale());
        canvas = new YardCanvas(paint);
        ContextFrame f = frame;
        Runnable ack = this::acknowledge;
        rail = new HeaderRail(f);
        caution = new CautionBoard(f, conditions);
        threat = new ThreatUnit(f);
        placard = new EmergencyPlacard(f);
        intercom = new Intercom(f);
        clipboard = new Clipboard(f);
        nav = new NavTube(f);
        threatList = new ThreatList(f);
        lock = new LockCard(f);
        armament = new ArmamentPlate(f);
        station = new StationPanel(f);
        boarding = new BoardingPanel(f);
        condition = new ConditionUnit(f, conditions);
        shield = new ShieldControl(f);
        suit = new SuitUnit(f);
        scope = new Scope(f);
        flight = new FlightPod(f, conditions, ack);
        docking = new DockingPod(f, conditions, ack);
        rack = new WeaponsRack(f);
        cue = new MasterCue(f, conditions, ack);
        prompt = new PromptPlate(f);
        orders = new OrdersPanel(f);
        coaming = new Coaming();
        veil = new Veil();
        // Painted in this order: the console's edge; the units the emergency's veil dims; the veil; the units
        // it leaves lit (the way out and its evidence); the orders panel over everything when it is swung up
        YardPart[] dimmed = {rail, intercom, clipboard, nav, threatList, lock, armament, station, boarding, shield,
                scope, flight, docking, rack, prompt};
        YardPart[] lit = {condition, suit, caution, threat, placard, cue};
        canvas.add(coaming);
        veil.under.add(coaming);
        for (YardPart p : dimmed) {
            canvas.add(p);
            veil.under.add(p);
        }
        canvas.add(veil);
        for (YardPart p : lit) canvas.add(p);
        canvas.add(orders);
        all = new YardPart[canvas.parts().size];
        for (int i = 0; i < all.length; i++) all[i] = canvas.parts().get(i);
        host.layer().addActor(canvas);
        canvas.setBounds(0f, 0f, host.width(), host.height());
    }

    @Override
    public void update(float delta) {
        if (host == null || paint == null) return;
        HudData d = host.data();
        UiHud hud = host.hud();
        BoardingWindow bw = hud.getBoardingWindow();
        BoardingBoard board = bw == null ? null : bw.getBoard();
        ContextFrame f = frame;
        f.read(d, hud.isMapOpen(), board, delta);
        f.screenW = host.width();
        f.screenH = host.height();
        canvas.setBounds(0f, 0f, f.screenW, f.screenH);
        intercom.update(d);
        if (f.seat == ContextFrame.Seat.NONE || f.map) {
            // No ship to show, or the map over everything: the units wait
            orders.open = false;
            for (YardPart p : all) p.visible = false;
            return;
        }
        scope.update(d);
        place(d, delta);
    }

    /** What is up this frame and where (spec 4 and 5): each unit's height, then the layout's rectangles. */
    private void place(HudData d, float delta) {
        ContextFrame f = frame;
        ContextLayout l = layout;
        ContextFrame.Seat seat = f.seat;
        boolean helm = seat == ContextFrame.Seat.HELM, fire = seat == ContextFrame.Seat.FIRE;
        boolean gun = seat == ContextFrame.Seat.GUN, deck = f.deck();
        l.width = f.screenW;
        l.height = f.screenH;
        l.seat = seat;

        // The seat's instrument (H3, H8, F2): the flight pod, the docking pod on an approach, the weapons rack
        boolean flightUp = helm && !f.approach && !f.berthed();
        boolean dockingUp = helm && f.approach;
        float rackH = fire || gun ? rack.measure(d, gun) : 0f;
        boolean rackUp = rackH > 0f;
        flight.warpFace = f.warp;
        l.instrumentW = flightUp || dockingUp ? FlightPod.W : rackUp ? WeaponsRack.W : 0f;
        l.instrumentH = flightUp ? (f.warp ? FlightPod.WARP_H : FlightPod.H) : dockingUp ? DockingPod.H : rackH;

        // The left middle: the objective, navigation (the helm's), the threat list (fire control's)
        Clipboard.Mode clip = clipboardMode(d, f, helm, fire, gun, deck);
        l.clipboardH = clip == null ? 0f : clipboard.measure(paint, d, clip);
        NavTube.Mode navMode = helm ? navMode(d, f) : null;
        int budget = NavTube.budget(paint);
        l.navH = navMode == null ? 0f : nav.measure(d, navMode, budget);
        l.navCompactH = l.navH > 0f && navMode != NavTube.Mode.COMPACT ? NavTube.PAD * 2f + NavTube.LINE : 0f;
        l.threatListH = fire || gun ? threatList.measure(d) : 0f;
        l.solve();
        if (l.navCompacted) {
            // The zone is too short for the whole tube: it takes its compact face, or goes with nothing to say
            float ch = nav.measure(d, NavTube.Mode.COMPACT, budget);
            if (ch <= 0f) l.navH = l.navCompactH = 0f;
            else l.navCompactH = ch;
        }
        boolean navUp = l.navH > 0f;
        boolean navWhole = navUp && navMode != NavTube.Mode.COMPACT && !l.navCompacted;

        // The conditions, now that it is known which instruments on screen show their own
        boolean shieldControl = fire && f.emitter;
        shown.dockingPod = dockingUp;
        shown.planLine = navWhole && nav.planShort;
        shown.shieldControl = shieldControl;
        shown.suitUnit = deck && SuitUnit.wanted(d.captainStatus);
        conditions.update(d, f, shown, f.time, delta);

        // The left foot: on deck the suit unit and the condition unit over it; at a seat the condition unit,
        // and at fire control shield control over it
        condition.update(f.emitter && !shieldControl);
        l.suitH = shown.suitUnit ? SuitUnit.H : 0f;
        float room = deck ? ContextLayout.BOTTOM_H - (l.suitH > 0f ? l.suitH + ContextLayout.GAP : 0f)
                : shieldControl ? ContextLayout.BOTTOM_H - (ShieldControl.H_SHORT + ContextLayout.GAP)
                : ContextLayout.BOTTOM_H;
        l.conditionH = condition.measure(deck, shieldControl, room);
        l.shieldH = shieldControl ? ShieldControl.measure(ContextLayout.BOTTOM_H - l.conditionH - ContextLayout.GAP) : 0f;

        // The right middle: the lock card (H6 at the helm, F1 at fire control), the armament plate in a fight
        // at the helm, the station panel while docked, the boarding panel in a boarding
        TargetStatus t = d.targetStatus;
        boolean lockedOn = t.present && !latchedTo(d, f, t);
        boolean helmLock = helm && lockedOn && !f.warp && !(f.approach && !f.combat) && !f.docked;
        boolean fireLock = (fire || gun) && lockedOn;
        l.lockH = helmLock ? lock.measure(false) : fireLock ? lock.measure(true) : 0f;
        l.armamentH = helm && f.combat && !f.docked ? ArmamentPlate.H : 0f;
        l.stationH = f.docked ? station.measure(d) : 0f;
        l.boardingH = f.boarding ? boarding.measure(paint) : 0f;

        // The right foot: the scope, folded to its key while there is nothing near
        boolean scopeUp = f.seated() && !f.warp && !f.berthed();
        l.scopeW = !scopeUp ? 0f : scope.folded ? 64f : Scope.W;
        l.scopeH = !scopeUp ? 0f : scope.folded ? Scope.BUTTON : Scope.H;

        // The top: the caution board, the missile warning and the emergency placard under it, the intercom
        l.cautionH = CautionBoard.height(conditions.listed.size);
        l.threatH = ThreatUnit.inbound(d) > 0 ? ThreatUnit.H : 0f;
        l.placardH = f.emergency ? placard.measure(ContextLayout.TC_H - (l.threatH > 0f ? l.threatH + ContextLayout.GAP : 0f)) : 0f;
        l.intercomH = intercom.measure(paint);

        // The master cue: on its pod's face at the helm, on a bracket over the rack, else alone while lit
        l.cueBracket = rackUp;
        l.cueAlone = !flightUp && !dockingUp && !rackUp && conditions.cue.lit;
        l.cueW = l.cueBracket || l.cueAlone ? MasterCue.W : 0f;
        l.cueH = l.cueBracket || l.cueAlone ? MasterCue.H : 0f;
        cue.bracket = l.cueBracket;

        // The bottom centre's top: the prompt, or the orders panel swung up over the instrument
        boolean promptWanted = (helm && !f.combat && !f.warp || deck || f.docked) && !orders.open;
        boolean promptUp = promptWanted && prompt.choose();
        l.promptW = promptUp ? PromptPlate.W : 0f;
        l.promptH = promptUp ? PromptPlate.H : 0f;
        float oh = orders.measure(d);
        l.ordersW = oh > 0f ? OrdersPanel.W : 0f;
        l.ordersH = oh;

        l.solve();

        put(rail, l.rail);
        put(caution, l.caution);
        put(threat, l.threat);
        put(placard, l.placard);
        put(intercom, l.intercom);
        put(clipboard, l.clipboard);
        put(nav, l.nav);
        put(threatList, l.threatList);
        put(lock, l.lock);
        put(armament, l.armament);
        put(station, l.station);
        put(boarding, l.boarding);
        put(condition, l.condition);
        put(shield, l.shield);
        put(suit, l.suit);
        put(scope, l.scope);
        put(flight, flightUp ? l.instrument : null);
        put(docking, dockingUp ? l.instrument : null);
        put(rack, rackUp ? l.instrument : null);
        put(cue, l.cue);
        put(prompt, l.prompt);
        put(orders, l.orders);

        // The console's top edge, at a seat only
        coaming.visible = f.seated();
        if (coaming.visible) coaming.place(0f, f.screenH - Coaming.H, f.screenW, Coaming.H);

        // The emergency's veil, while the placard is up; the master cue on a dimmed pod's face stays clear
        veil.visible = placard.visible;
        if (veil.visible) {
            veil.place(0f, 0f, f.screenW, f.screenH);
            veil.holeW = 0f;
            if (flight.visible) hole(flight, FlightPod.CUE_X, FlightPod.CUE_Y, FlightPod.CUE_W);
            else if (docking.visible) hole(docking, DockingPod.CUE_X, DockingPod.CUE_Y, DockingPod.CUE_W);
        }
    }

    /** The objective's face (H1): the guide's step while it runs, else the tracked contract where it matters. */
    private static Clipboard.Mode clipboardMode(HudData d, ContextFrame f, boolean helm, boolean fire, boolean gun, boolean deck) {
        if (f.guided) return Clipboard.Mode.GUIDE;
        Objective o = Clipboard.tracked(d);
        if (o == null) return null;
        if (f.warp && !f.combat) return Clipboard.Mode.FULL;
        if (f.docked) return Clipboard.Mode.COMPACT;
        if (helm && !f.approach && !f.combat) return Clipboard.Mode.COMPACT;
        if (deck && !f.combat && !f.emergency) return Clipboard.Mode.COMPACT;
        if ((f.combat || fire || gun) && Clipboard.fight(o)) return Clipboard.Mode.COMPACT;
        return null;
    }

    /** Navigation's face (H2), at the helm: gone on an approach, and berthed unless a burn is planned. */
    private static NavTube.Mode navMode(HudData d, ContextFrame f) {
        if (f.approach) return null;
        if (f.berthed()) return d.navReadout.burnsLeft > 0 ? NavTube.Mode.FULL : null;
        if (f.combat) return NavTube.Mode.COMPACT;
        if (f.warp) return NavTube.Mode.WARP;
        return NavTube.Mode.FULL;
    }

    /** The lock is the ship the captain is latched to: the boarding panel speaks for it. */
    private static boolean latchedTo(HudData d, ContextFrame f, TargetStatus t) {
        if (!f.latched || t.label.isEmpty()) return false;
        return t.label.equalsIgnoreCase(d.docking.other) || t.label.equalsIgnoreCase(f.board.craftName);
    }

    private void hole(YardPart pod, float cx, float cy, float cw) {
        veil.holeX = pod.x + cx - 2f;
        veil.holeY = pod.y + cy - 2f;
        veil.holeW = cw + 4f;
        veil.holeH = 28f;
    }

    private static void put(YardPart p, ContextLayout.Rect r) {
        if (r == null || !r.on) {
            p.visible = false;
            return;
        }
        p.visible = true;
        p.place(r.x, r.y, r.w, r.h);
    }

    /** The captain acknowledges: the master cue goes out and the new warnings stop flashing. */
    private void acknowledge() {
        if (host != null) conditions.acknowledge(host.data());
    }

    /** Something the acknowledge key would put out: the cue lit, or a warning not yet acknowledged. */
    private boolean toAcknowledge() {
        if (conditions.cue.lit) return true;
        for (int i = 0; i < conditions.all.size; i++) {
            Conditions.Row r = conditions.all.get(i);
            if (r.warning && !r.acknowledged) return true;
        }
        return false;
    }

    // --- Keys ----------------------------------------------------------------------------------------

    @Override
    public boolean keyDown(int keycode) {
        if (host == null || paint == null) return false;
        prompt.keyPressed(keycode);
        intercom.keyPressed();
        // Escape is never the HUD's: with no window open it is the game's way out
        if (keycode == Input.Keys.ESCAPE) return false;
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        if (alt && keycode == Input.Keys.O) {
            orders.open = !orders.open && frame.seat != ContextFrame.Seat.NONE && !frame.map;
            return true;
        }
        if (!alt && keycode == Input.Keys.BACKSLASH && toAcknowledge()) {
            acknowledge();
            return true;
        }
        if (orders.open && closesOrders(keycode)) orders.open = false;
        return false;
    }

    /** Whether {@code keycode} gives one of the orders that closes the panel as it goes to the game. */
    private boolean closesOrders(int keycode) {
        String name = Input.Keys.toString(keycode);
        if (name == null) return false;
        HudData d = host.data();
        for (int i = 0; i < CLOSES.length; i++) {
            if (name.equalsIgnoreCase(Kit.key(d, CLOSES[i], CLOSES_DEFAULT[i]))) return true;
        }
        return false;
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        if (paint != null) paint.fonts.setScale(uiScale);
        if (canvas != null && host != null) canvas.setBounds(0f, 0f, host.width(), host.height());
    }

    @Override
    public void detach() {
        if (paint != null) paint.dispose();
        paint = null;
        canvas = null;
        all = new YardPart[0];
    }

    /** The world overlays stay: contact markers and missile paths. Everything else is the Yard's. */
    @Override
    public Set<ClassicPart> keeps() {
        return EnumSet.of(ClassicPart.WORLD_MARKERS, ClassicPart.THREAT_OVERLAY);
    }
}
