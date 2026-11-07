package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.DockingApproachProvider;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ObjectiveProvider;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.nasapunk.NpScreens;
import com.ringsofiron.uihud.widgets.MapPainter;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import com.ringsofiron.uihud.widgets.BoardingWindow;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nasa-punk art on the context functional spec (documentation/ui-overhaul/functional-context.md;
 * drawn in documentation/ui-overhaul/variations/nasapunk-context). What shows follows the captain's
 * seat and the situations that hold (sections 4.1 and 4.2, the matrix in 4.6): the helm's flight
 * display, navigation block and scope, the docking display through an approach, the lock card and
 * armament in a fight, the weapons rack, shield control and threat list at fire control, the
 * captain's vitals on deck, the station or boarding panel when docked or latched, the emergency
 * card when the ship needs saving.
 * Each widget lives in its zone ({@link ContextLayout}) and stacks from the zone's outer edge
 * inward. P2's rows, H4's lights and the master alert cue come from {@link CautionRows}. While the
 * emergency card shows, everything but it, P2, P3, H4, D1 and the cue dims.
 *
 * <p>Keys: 7 opens and closes the orders menu (C1), which an order's own key also closes;
 * backslash acknowledges the master alert cue, as a click on it does. The orders' keys give the
 * orders as they always have: C1 shows them and why one can't be given.
 */
public class NasaPunkContextHud implements HudVariant {
    /** Where the captain is (section 4.1): any craft's deck, or outside on EVA, is the deck. */
    enum Seat { NONE, HELM, FIRE, GUN, DECK }

    private static final float GAP = 6f;
    /** What the emergency card leaves bright stays at 1; the rest dims to this (section 8, X3). */
    private static final float DIM = 0.4f;
    /** A caution's cue goes out after this long unacknowledged, real s; a warning's stays lit. */
    private static final float CUE_CAUTION_FOR = 5f;
    /** H3 shows the capture this long after the ship docks, s (section 7, H3). */
    private static final float CAPTURE_HOLD = 2f;
    /** H4 stays expanded this long after its last light goes out, s (section 7, H4). */
    private static final float CONDITION_HOLD = 10f;
    /** In cruise the scope folds with nothing inside this, m; F4 lists hostiles inside it. */
    private static final float SCOPE_NEAR = 50_000f;
    /** The captain's suit air under this, away from breathable air, is an emergency, s ({@code AirWatch.WARN_AT}). */
    private static final float SUIT_AIR_SHORT = 240f;
    /** A hostile lock this close is inside weapons range, m: the coilgun's reach. */
    private static final float WEAPONS_RANGE = 5_000f;
    /** A scope step holds a contact with room to spare when it is inside this share of the step. */
    private static final float ROOM = 0.8f;
    /** The scope's 2 km step, for an approach. */
    private static final int APPROACH_STEP = 1;
    private static final String[] SOURCES = {"Helm", "Gunnery", "Shields", "Engineering", "Medical", "Crew", "Boarding", "Station", "Sponsor", "Comms", "Log"};
    private static final Action[] ORDER_ACTIONS = {
        Action.HELM_FLY_BURNS, Action.HELM_MATCH_VELOCITY, Action.HELM_HOLD, Action.HELM_APPROACH, Action.HELM_DOCK, Action.HELM_CANCEL,
        Action.AUTOPILOT, Action.SHIELDS,
    };
    private static final HelmOrder[] HELM_ORDERS = {
        HelmOrder.FLY_BURNS, HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION, HelmOrder.APPROACH, HelmOrder.DOCK, HelmOrder.NONE,
    };

    private HudVariantHost host;
    private NpKit kit;
    /** The menus as pages of CDU-1 and the map's system plot, the same under all three Nasa-punk HUDs. */
    private final NpScreens screens = new NpScreens();
    private Group root;
    private StatusStrip strip;
    private CautionPanel cautions;
    private ThreatAlert threat;
    private MessageSlips messages;
    private ObjectiveSlip objective;
    private FlightDu flight;
    private NavBlock nav;
    private DockingDu docking;
    private ConditionPanel condition;
    private ContactScope scope;
    private LockCard lock;
    private ArmamentStrip armament;
    private WeaponsRack rack;
    private ShieldControl shield;
    private ThreatList threats;
    private Vitals vitals;
    private StationPanel station;
    private BoardingPanel boarding;
    /** The boarding window's board, which the game fills twice a second; null without one. */
    private BoardingBoard board;
    private EmergencyCard emergency;
    private OrdersPanel orders;
    private MasterCue cue;
    /** Every widget the emergency card dims. */
    private final List<Actor> dimmed = new ArrayList<>();

    private final Situations sit = new Situations();
    private final Situations.Input sitIn = new Situations.Input();
    private final CautionRows caution = new CautionRows();
    private final CautionRows.Shown shown = new CautionRows.Shown();
    /** The catalogue's conditions the game doesn't raise, which this HUD works out each frame. */
    private final AlertRegistry derived = new AlertRegistry();
    private Seat seat = Seat.NONE;
    private int lastSignature = -1;
    private boolean ordersOpen, wasDocked;
    private float captureHold, conditionHold, cueLit;
    private String cueId = "";
    private final ConditionPanel.Row hullRow = new ConditionPanel.Row(), shieldRow = new ConditionPanel.Row();
    private final ConditionPanel.Row powerRow = new ConditionPanel.Row(), lifeRow = new ConditionPanel.Row();
    private final List<NpInstruments.Blip> blipPool = new ArrayList<>();
    private final List<MissileTrack> inbound = new ArrayList<>();

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        kit = new NpKit();
        kit.setScale(host.uiScale());
        root = new Group();
        root.setTransform(false);
        root.setTouchable(Touchable.childrenOnly);
        host.layer().addActor(root);
        // back to front: the columns, the primary instrument, the interrupts, then the orders menu
        objective = add(new ObjectiveSlip(kit), true);
        nav = add(new NavBlock(kit), true);
        threats = add(new ThreatList(kit), true);
        condition = add(new ConditionPanel(kit), false);
        vitals = add(new Vitals(kit), false);
        shield = add(new ShieldControl(kit), true);
        messages = add(new MessageSlips(kit), true);
        lock = add(new LockCard(kit), true);
        armament = add(new ArmamentStrip(kit), true);
        station = add(new StationPanel(kit), true);
        boarding = add(new BoardingPanel(kit), true);
        scope = add(new ContactScope(kit), true);
        flight = add(new FlightDu(kit), true);
        docking = add(new DockingDu(kit), true);
        rack = add(new WeaponsRack(kit), true);
        cue = add(new MasterCue(kit), false);
        cautions = add(new CautionPanel(kit), false);
        threat = add(new ThreatAlert(kit), false);
        emergency = add(new EmergencyCard(kit), false);
        orders = add(new OrdersPanel(kit), false);
        strip = add(new StatusStrip(kit), true);
        cue.onAcknowledge = this::acknowledge;
        // a click on P1's chip closes the full-screen mode, back to P2's rows
        strip.onChip = () -> host.hud().setMapVisible(false);
        lights();
    }

    private <T extends Actor> T add(T a, boolean dims) {
        root.addActor(a);
        if (dims) dimmed.add(a);
        return a;
    }

    /** H4's rows, each with its tape's bands. */
    private void lights() {
        hullRow.name = "HULL";
        hullRow.tape.band(0f, 0.25f, NpColors.RED).band(0.25f, 0.5f, NpColors.AMBER);
        shieldRow.name = "SHIELDS";
        shieldRow.tape.band(0f, CautionRows.CLOUD_THIN, NpColors.AMBER);
        powerRow.name = "POWER";
        powerRow.tape.band(0f, CautionRows.STORE_LOW, NpColors.AMBER);
        lifeRow.name = "LIFE SUP";
        lifeRow.tape.band(0f, 0.1f, NpColors.RED).band(0.1f, 0.25f, NpColors.AMBER);
    }

    // ---- the frame ------------------------------------------------------------------------------

    @Override
    public void update(float delta) {
        HudData d = host.data();
        UiContext ctx = d.ctx;
        float dt = Math.max(0f, Math.min(delta, 0.25f));
        seat = seatOf(ctx.presence);
        boolean mapOpen = host.hud().isMapOpen();
        BoardingWindow bw = host.hud().getBoardingWindow();
        board = bw == null ? null : bw.getBoard();
        if (mapOpen) ordersOpen = false;
        situations(d, dt);
        int sig = signature();
        if (sig != lastSignature) {
            // the scope's range set by hand, and its key opened by hand, last until the situation changes
            scope.override = -1;
            scope.opened = false;
            lastSignature = sig;
        }
        boolean helm = seat == Seat.HELM, fire = seat == Seat.FIRE, gun = seat == Seat.GUN, deck = seat == Seat.DECK;
        boolean seated = helm || fire || gun;
        boolean ship = ctx.ship != null && !mapOpen;
        // docked or latched the ship can't fly, so the flight instruments retire
        boolean retired = ctx.docked || ctx.latched;
        boolean emitter = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        boolean armed = d.weaponFrame.mounts.size > 0;

        // which widgets show (section 4.6), decided before P2's rows, which leave out what they show
        if (ctx.docked && !wasDocked && docking.isVisible()) captureHold = CAPTURE_HOLD;
        else captureHold = Math.max(0f, captureHold - dt);
        wasDocked = ctx.docked;
        flight.setVisible(ship && helm && !retired && !sit.approach);
        docking.setVisible(ship && helm && (sit.approach && !retired || captureHold > 0f));
        boolean lockHeld = ship && d.targetStatus.present && !retired;
        lock.setVisible(lockHeld && (fire || gun || helm && !sit.warp && !(sit.approach && !sit.combat)));
        armament.setVisible(ship && helm && sit.combat && !retired && armed);
        rack.setVisible(ship && (fire || gun) && !retired && armed);
        shield.setVisible(ship && fire && !retired && emitter);
        int threatRows = threatRows(d);
        threats.setVisible(ship && (fire || gun) && !retired && threatRows > 0);
        station.setVisible(ship && ctx.docked && !ctx.latched);
        boarding.setVisible(ship && sit.boarding && !sit.warp && !(helm && sit.approach) && !gun);
        emergency.setVisible(ship && sit.emergency);
        boolean scopeHere = ship && seated && !sit.warp && !retired;
        scope.setVisible(scopeHere);
        navBlock(d, ship && helm && !retired && !sit.approach, ship && ctx.docked && d.navReadout.burnsLeft > 0);

        shown.seated = seated;
        shown.dockingDisplay = docking.isVisible();
        shown.planLine = nav.isVisible() && nav.face != NavBlock.Face.COMPACT && nav.hasPlan();
        shown.shieldControl = shield.isVisible();
        derived.begin(ctx.simSeconds);
        caution.derive(d, derived, dt);
        derived.end();
        caution.build(d, derived, shown);

        strip(d, mapOpen);
        cautionPanel(d, mapOpen);
        masterCue(dt);
        threatAlert(d, threats.isVisible());
        messages(d);
        objective(d, mapOpen);
        if (flight.isVisible()) flight(d);
        if (docking.isVisible()) docking(d);
        condition(d, ship, seated, deck, emitter, dt);
        if (scopeHere) scope(d);
        if (lock.isVisible()) lockCard(d, !helm);
        if (armament.isVisible()) armament(d, emitter);
        if (rack.isVisible()) rack(d, gun);
        if (shield.isVisible()) shieldControl(d.shieldStatus);
        vitals(d, ship && deck);
        if (station.isVisible()) station(d);
        if (boarding.isVisible()) boarding(d);
        if (emergency.isVisible()) emergency(d);
        orders.setVisible(ship && ordersOpen);
        if (orders.isVisible()) orders(d);

        float a = emergency.isVisible() ? DIM : 1f;
        for (Actor x : dimmed) x.getColor().a = a;
        layout();
    }

    private static Seat seatOf(Presence p) {
        switch (p) {
            case HELM: return Seat.HELM;
            case WEAPONS: return Seat.FIRE;
            case GUN: return Seat.GUN;
            case DECK:
            case ABOARD_OTHER:
            case EVA: return Seat.DECK;
            default: return Seat.NONE;
        }
    }

    /** The seat and the situations as one number, to tell when either changes. */
    private int signature() {
        int s = seat.ordinal();
        boolean[] b = {sit.warp, sit.approach, sit.docked, sit.latched, sit.combat, sit.boarding, sit.emergency};
        for (int i = 0; i < b.length; i++) if (b[i]) s |= 1 << (4 + i);
        return s;
    }

    // ---- situations (section 4.2) ---------------------------------------------------------------

    private void situations(HudData d, float dt) {
        UiContext ctx = d.ctx;
        DockingApproach k = d.docking;
        Orders o = d.orderState;
        TargetStatus t = d.targetStatus;
        sitIn.warp = ctx.warp > 1 || ctx.railsWarp;
        sitIn.docked = ctx.docked;
        sitIn.latched = ctx.latched;
        sitIn.portRange = k.present ? k.range : Float.NaN;
        boolean navIsPort = d.navReadout.present && !k.other.isEmpty() && d.navReadout.name.equalsIgnoreCase(k.other);
        sitIn.approachTrigger = navIsPort || k.armed || o.helm == HelmOrder.DOCK;
        boolean hostileInRange = t.present && t.relation == Relation.HOSTILE && fighting(t.state)
                && (t.firingOnUs || !Float.isNaN(t.range) && t.range <= WEAPONS_RANGE);
        sitIn.combatTrigger = o.battleStations || ctx.missilesInbound > 0 || d.threatFrame.incoming() > 0 || hostileInRange;
        // in reach on EVA, only a beaten ship, or one our party is on, is a boarding: a fit one is a fight
        sitIn.boarding = ctx.latched || BoardingRows.boarding(board);
        sitIn.emergency = disabledEmergency(d) || o.abandoning || suitAirShort(d.captainStatus);
        sit.update(sitIn, dt);
    }

    /** The lock's state still fights: fit, running or silenced, rather than beaten or wrecked. */
    private static boolean fighting(String state) {
        return "fit".equals(state) || "running".equals(state) || "silenced".equals(state) || state == null || state.isEmpty();
    }

    /**
     * Disabled, unless the only reason is power due back by itself: every reactor fault a stun or a
     * short with its time to recover (section 8, X3), which P2's NO POWER row counts down instead.
     */
    private static boolean disabledEmergency(HudData d) {
        String reason = d.ctx.disabledReason;
        if (reason.isEmpty()) return false;
        if (!"reactor down".equals(reason)) return true;
        int reactors = 0;
        for (PowerStatusProvider.SystemRow r : d.powerFrame.faults) {
            if (!"REACTOR".equals(r.system)) continue;
            reactors++;
            boolean back = (r.fault == PowerStatusProvider.Fault.STUNNED || r.fault == PowerStatusProvider.Fault.SHORTED) && !Float.isNaN(r.timer) && r.timer > 0f;
            if (!back) return true;
        }
        return reactors == 0;
    }

    private static boolean suitAirShort(CaptainStatus c) {
        if (!c.present || !c.suited || Float.isNaN(c.suitAir) || c.suitAir >= SUIT_AIR_SHORT) return false;
        return Float.isNaN(c.roomPressure) || c.roomPressure < 0.7f;
    }

    // ---- P1 ---------------------------------------------------------------------------------------

    private void strip(HudData d, boolean mapOpen) {
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        strip.seat = seatName(d);
        strip.authority = authority(d);
        strip.battle = o.battleStations;
        strip.paused = o.paused;
        double t = ctx.simSeconds;
        strip.clock = "DAY " + (long) (t / 86400.0) + " · " + NpFormat.timeOfDay(t);
        String warp = NpFormat.thousands(ctx.warp) + "×" + (ctx.railsWarp ? " RAILS" : "");
        strip.warp = ctx.warpHold.isEmpty() ? warp : warp + " · HELD: " + upper(ctx.warpHold);
        // while a full-screen mode is open, P1's chip stands for P2
        strip.chip = mapOpen;
        int warnings = 0, cautionsN = 0;
        for (CautionRows.Row r : caution.panel) {
            if (r.level == Level.WARNING) warnings++;
            else if (r.level == Level.CAUTION) cautionsN++;
        }
        strip.warnings = warnings;
        strip.cautions = cautionsN;
        strip.chipLevel = warnings > 0 ? Level.WARNING : Level.CAUTION;
    }

    private String seatName(HudData d) {
        UiContext ctx = d.ctx;
        switch (ctx.presence) {
            case HELM: return "HELM";
            case WEAPONS: return "FIRE CONTROL";
            case GUN:
                for (Mount m : d.weaponFrame.mounts) {
                    if (m.controller == WeaponStatusProvider.Controller.PLAYER) return "GUN · " + WeaponsRack.place(m.name);
                }
                return "GUN";
            case DECK: return "DECK";
            case ABOARD_OTHER: {
                String craft = ctx.docked ? ctx.stationName : ctx.latched ? d.docking.other : "";
                return craft.isEmpty() ? "DECK" : "DECK · " + upper(craft);
            }
            case EVA: return "EVA";
            default: return "";
        }
    }

    /**
     * Who flies, only when it isn't the captain or nobody does (section 6, P1): the autopilot, held or
     * flying; the helm's order; a crew member holding the heading; nobody.
     */
    private String authority(HudData d) {
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        strip.authorityHeld = false;
        if (ctx.ship == null || ctx.docked || ctx.latched) return "";
        if (o.autopilot == Autopilot.HELD) {
            strip.authorityHeld = true;
            // a hold that is warp's is said once, in the warp field
            boolean warps = o.autopilotHeld.isEmpty() || o.autopilotHeld.equalsIgnoreCase(ctx.warpHold);
            return warps ? "AUTOPILOT HELD" : "AUTOPILOT HELD: " + upper(o.autopilotHeld);
        }
        if (o.autopilot == Autopilot.ON) return o.autopilotTo.isEmpty() ? "AUTOPILOT" : "AUTOPILOT → " + upper(o.autopilotTo);
        if (!o.helmLine.isEmpty()) return upper(o.helmLine);
        if (ctx.helmsman.isEmpty()) return "HELM: NOBODY";
        if (ctx.helmsman.equalsIgnoreCase("you") || ctx.helmsman.equalsIgnoreCase("autopilot")) return "";
        return "HELM: " + upper(ctx.helmsman) + " · HOLDING HEADING";
    }

    // ---- P2 and the master alert cue ------------------------------------------------------------

    private void cautionPanel(HudData d, boolean mapOpen) {
        List<CautionRows.Row> p = caution.panel;
        cautions.rows.clear();
        cautions.more.clear();
        if (p.size() <= CautionRows.ROWS) {
            cautions.rows.addAll(p);
        } else {
            cautions.rows.addAll(p.subList(0, CautionRows.ROWS - 1));
            for (int i = CautionRows.ROWS - 1; i < p.size(); i++) cautions.more.add(p.get(i).code);
        }
        cautions.now = d.ctx.simSeconds;
        cautions.setVisible(!mapOpen && !p.isEmpty());
    }

    /** The cue lights for the newest condition not acknowledged: a caution's for 5 s, a warning's until acknowledged. */
    private void masterCue(float dt) {
        CautionRows.Row c = caution.cue;
        String id = c == null ? "" : c.id + c.level;
        if (!id.equals(cueId)) {
            cueId = id;
            cueLit = 0f;
        } else {
            cueLit += dt;
        }
        boolean lit = c != null && (c.level == Level.WARNING || cueLit < CUE_CAUTION_FOR);
        cue.level = lit ? c.level : null;
        cue.code = lit ? c.code : "";
        cue.litFor = cueLit;
    }

    /** Acknowledge: the cue goes out until something new or worse (section 6, P2). */
    private void acknowledge() {
        host.data().alerts.acknowledgeAll();
        derived.acknowledgeAll();
    }

    // ---- P3, F4 ---------------------------------------------------------------------------------

    /**
     * P3: the count, with the soonest impact where no threat list has it; the decoy at a seat (on deck
     * there is none to throw); point defense while it fires, where F4 doesn't show it.
     */
    private void threatAlert(HudData d, boolean listShown) {
        ThreatProvider.ThreatFrame tf = d.threatFrame;
        int n = tf.incoming();
        threat.setVisible(d.ctx.ship != null && n > 0);
        threat.parts.clear();
        threat.keys.clear();
        if (n == 0) return;
        float soonest = tf.soonestImpact();
        String l1 = n + (n == 1 ? " MISSILE INBOUND" : " MISSILES INBOUND");
        threat.line1 = !listShown && !Float.isNaN(soonest) ? l1 + " · " + NpFormat.tMinus(soonest) : l1;
        if (seat != Seat.DECK) {
            float cd = d.weaponFrame.decoyCooldown;
            if (cd <= 0f) {
                threat.part("DECOY READY", false);
                threat.part(key(d, Action.DECOY), true);
            } else {
                threat.part("DECOY IN " + (int) Math.ceil(cd) + " S", false);
            }
        }
        if (d.weaponFrame.pointDefenseActive && !listShown) {
            if (!threat.parts.isEmpty()) threat.part("·", false);
            threat.part("POINT DEFENSE ENGAGING", false);
        }
    }

    /** F4's rows: inbound missiles soonest first, then the hostiles in the fight other than the Lock; returns how many. */
    private int threatRows(HudData d) {
        threats.rows.clear();
        inbound.clear();
        for (MissileTrack m : d.threatFrame.missiles) if (m.side == ThreatProvider.Side.INCOMING) inbound.add(m);
        inbound.sort((a, b) -> Float.isNaN(a.timeToImpact) ? (Float.isNaN(b.timeToImpact) ? 0 : 1)
                : Float.isNaN(b.timeToImpact) ? -1 : Float.compare(a.timeToImpact, b.timeToImpact));
        for (int i = 0; i < inbound.size(); i++) {
            MissileTrack m = inbound.get(i);
            ThreatList.Row r = new ThreatList.Row();
            r.missile = true;
            r.name = upper(m.shooter);
            r.time = m.timeToImpact;
            // point defense takes the soonest first
            r.pd = i == 0 && d.weaponFrame.pointDefenseActive;
            threats.rows.add(r);
        }
        for (Contact c : d.contactFrame.contacts) {
            if (c.kind != ContactProvider.Kind.SHIP || c.relation != Relation.HOSTILE || c.locked) continue;
            if (c.state != ContactProvider.State.FIT && c.state != ContactProvider.State.RUNNING && c.state != ContactProvider.State.SILENCED) continue;
            if (Float.isNaN(c.range) || c.range > SCOPE_NEAR) continue;
            ThreatList.Row r = new ThreatList.Row();
            r.name = upper(c.label);
            r.range = c.range;
            if (c.state == ContactProvider.State.RUNNING) r.state = "FLEEING";
            else if (c.state == ContactProvider.State.SILENCED) r.state = "SILENCED";
            else if (Float.isNaN(c.closing)) r.state = "";
            else r.state = c.closing > 0.5f ? "CLOSING" : c.closing < -0.5f ? "OPENING" : "HOLDING RANGE";
            threats.rows.add(r);
        }
        return threats.rows.size();
    }

    // ---- P4, P5 ---------------------------------------------------------------------------------

    /**
     * P4: up to three fresh messages, newest on top, each under its source's tag; warnings are P2's.
     * In combat only notable news and the replies to the captain's own orders show (section 6, P4).
     */
    private void messages(HudData d) {
        messages.slips.clear();
        for (MessageFeed.Message m : d.messages.all()) {
            if (messages.slips.size() == 3) break;
            if (m.secondsLeft <= 0f || m.level == Level.WARNING) continue;
            String tag = "LOG", msg = m.text;
            int colon = msg.indexOf(':');
            if (colon > 0) {
                String src = msg.substring(0, colon).trim();
                for (String s : SOURCES) {
                    if (!s.equalsIgnoreCase(src)) continue;
                    tag = s.equals("Engineering") ? "ENG" : upper(s);
                    msg = msg.substring(colon + 1).trim();
                    break;
                }
            }
            boolean reply = tag.equals("HELM") || tag.equals("SHIELDS");
            if (sit.combat && !m.important && !reply) continue;
            MessageSlips.Slip s = new MessageSlips.Slip();
            s.tag = tag;
            s.msg = msg.isEmpty() ? msg : Character.toUpperCase(msg.charAt(0)) + msg.substring(1);
            s.count = m.count > 1 ? "×" + m.count : null;
            s.notable = m.important;
            messages.slips.add(s);
        }
        messages.setVisible(d.ctx.ship != null && !messages.slips.isEmpty());
    }

    /**
     * P5: the tracked contract, soonest deadline first. Compact, the job then where and the time
     * left; under warp at the helm its progress and the contracts held besides. Hidden through an
     * approach and on deck in a fight or an emergency; in combat, and at the guns, only a fight's own
     * contract shows (section 6, P5).
     */
    private void objective(HudData d, boolean mapOpen) {
        ObjectiveProvider.ObjectiveFrame of = d.objectiveFrame;
        objective.lines.clear();
        objective.colors.clear();
        Objective o = null;
        int held = 0;
        for (Objective x : of.objectives) {
            if (x.state == ObjectiveProvider.State.DONE) continue;
            held++;
            if (o == null) o = x;
        }
        boolean show;
        boolean full = false;
        if (o == null || mapOpen || d.ctx.ship == null) show = false;
        else if (seat == Seat.DECK && (sit.combat || sit.emergency)) show = false;
        else if (seat == Seat.HELM && sit.approach) show = false;
        else if (sit.combat || seat == Seat.FIRE || seat == Seat.GUN) show = fight(o);
        else {
            show = true;
            full = seat == Seat.HELM && sit.warp;
        }
        objective.setVisible(show);
        if (!show) return;
        objective.line(upper(o.kind.isEmpty() ? o.title : o.kind + ": " + o.title), NpColors.WHITE);
        boolean failed = o.state == ObjectiveProvider.State.FAILED;
        boolean late = o.late || failed || !Double.isNaN(o.timeLeft) && o.timeLeft < 0;
        StringBuilder b = new StringBuilder();
        if (!o.next.isEmpty()) b.append("→ ").append(upper(o.next));
        String left = failed ? "FAILED" : NpFormat.timeLeft(o.timeLeft);
        if (!left.isEmpty()) {
            if (b.length() > 0) b.append(" · ");
            b.append(left.equals("PAST") || failed ? left : left + " LEFT");
        }
        if (b.length() > 0) objective.line(b.toString(), late ? NpColors.AMBER : NpColors.WHITE);
        if (o.of > 0 && (full || sit.combat)) objective.line(NpFormat.thousands(Math.round(o.progress)) + " OF " + NpFormat.thousands(Math.round(o.of)), NpColors.WHITE);
        if (full && !of.operation.isEmpty() && of.operationSteps > 1) {
            objective.line(upper(of.operation) + ": STEP " + of.operationStep + " OF " + of.operationSteps, NpColors.WHITE);
        }
        if (full && held > 1) objective.line("+" + (held - 1) + (held == 2 ? " MORE CONTRACT" : " MORE CONTRACTS"), NpColors.DIM);
    }

    /** A contract that is itself a fight: a defence, a bounty, a convoy or an escort. */
    private static boolean fight(Objective o) {
        String k = (o.kind + " " + o.title).toLowerCase(Locale.ROOT);
        return k.contains("bounty") || k.contains("defen") || k.contains("convoy") || k.contains("escort");
    }

    // ---- H1, H2, H3 -----------------------------------------------------------------------------

    private void flight(HudData d) {
        FlightStatus f = d.flightStatus;
        flight.face(sit.warp);
        switch (f.frame) {
            case TARGET: flight.frame = "LOCK · " + upper(f.frameName); break;
            case NAV: flight.frame = "NAV · " + upper(f.frameName); break;
            case STATION: flight.frame = "STN · " + upper(f.frameName); break;
            default: flight.frame = "ORBIT · AUTO"; break;
        }
        // the frame is always the game's own choice for now: pinning it is a key still to come
        flight.frameAuto = true;
        flight.speed = f.speed;
        flight.assist = !f.present || f.rotationAssist;
        flight.main = f.mainEngine || f.forward > 0.05f;
        flight.retro = f.forward < -0.05f;
        flight.turnLeft = f.turn > 0.05f;
        flight.turnRight = f.turn < -0.05f;
        flight.strafePort = f.strafe > 0.05f;
        flight.strafeStbd = f.strafe < -0.05f;
        flight.thrust = thrust(f);
        flight.firing = !"NONE".equals(flight.thrust);
        flight.dv = f.deltaVLeft;
        flight.prop = d.shipStatus.propellant;
    }

    /** Which thrusters fire, in words. */
    private static String thrust(FlightStatus f) {
        StringBuilder b = new StringBuilder();
        if (f.mainEngine || f.forward > 0.05f) b.append("MAIN");
        else if (f.forward < -0.05f) b.append("RETRO");
        if (Math.abs(f.turn) > 0.05f) b.append(b.length() > 0 ? " · " : "").append("TURN");
        if (Math.abs(f.strafe) > 0.05f) b.append(b.length() > 0 ? " · " : "").append("STRAFE");
        return b.length() == 0 ? "NONE" : b.toString();
    }

    /**
     * H2 at the helm outside an approach, or docked with a departure planned: ORBIT, NAV, NEXT and
     * PLAN; under warp the stops ahead with their keys; in combat one line, the most pressing.
     */
    private void navBlock(HudData d, boolean helmHere, boolean departure) {
        NavReadout n = d.navReadout;
        FlightStatus f = d.flightStatus;
        Orders o = d.orderState;
        nav.face = helmHere && sit.combat ? NavBlock.Face.COMPACT : helmHere && sit.warp ? NavBlock.Face.WARP : NavBlock.Face.FULL;
        OrbitInfo orbit = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        if (orbit != null && !d.ctx.docked) {
            nav.body = upper(orbit.bodyName);
            nav.alt = orbit.altitude;
            boolean bound = !Double.isInfinite(orbit.period) && !Double.isNaN(orbit.period) && orbit.period > 0;
            nav.ap = bound ? orbit.apoapsis : Double.NaN;
            nav.pe = orbit.periapsis;
        } else {
            nav.body = "";
            nav.alt = nav.ap = nav.pe = Double.NaN;
        }
        if (n.present) {
            nav.nav = upper(n.name);
            nav.navRange = n.distance;
            nav.navRate = n.closing;
            nav.cpa = n.cpaDistance;
            nav.cpaIn = n.cpaIn;
        } else {
            nav.nav = "";
            nav.navRange = nav.cpa = Double.NaN;
            nav.navRate = nav.cpaIn = Float.NaN;
        }
        boolean burn = n.burnsLeft > 0;
        if (burn) {
            nav.next = n.burnNumber > 0 && n.burnsPlanned > 1 ? "BURN " + n.burnNumber + "/" + n.burnsPlanned : "BURN";
            nav.nextIn = n.burning ? 0f : n.burnStartIn;
            nav.nextDv = n.burning ? n.burnToGo : n.burnDeltaV;
            boolean helmFlies = o.helm == HelmOrder.FLY_BURNS && !d.ctx.helmsman.isEmpty() && !d.ctx.helmsman.equalsIgnoreCase("you");
            nav.nextWho = n.burning ? "BURNING" : f.autopilot ? "AUTOPILOT FLIES IT" : helmFlies ? "HELM FLIES IT" : "YOU FLY IT";
        } else {
            nav.next = "";
            nav.nextIn = nav.nextDv = Float.NaN;
            nav.nextWho = "";
        }
        if (burn && n.planDeltaV > 0) {
            nav.planDv = n.planDeltaV;
            nav.planLeaves = Double.isInfinite(f.deltaVLeft) || Double.isNaN(f.deltaVLeft) ? Double.NaN : f.deltaVLeft - n.planDeltaV;
        } else {
            nav.planDv = nav.planLeaves = Double.NaN;
        }
        nav.stops.clear();
        nav.stopIn.clear();
        if (nav.face == NavBlock.Face.WARP) {
            if (burn && !n.burning) {
                nav.stops.add(new String[] {nav.next, key(d, Action.WARP_TO_BURN)});
                nav.stopIn.add(n.burnStartIn);
            }
            float stop = d.ctx.warpNextStop;
            boolean sameAsBurn = burn && !Float.isNaN(n.burnStartIn) && Math.abs(stop - n.burnStartIn) < 5f;
            if (!Float.isNaN(stop) && !sameAsBurn) {
                nav.stops.add(new String[] {"WARP STOP", ""});
                nav.stopIn.add(stop);
            }
        }
        nav.setVisible((helmHere || departure) && nav.any());
        if (nav.isVisible()) nav.setSize(NavBlock.W, nav.heightFor());
    }

    /** H3: the ports and clearance, the alignment, the closing gauge, and along its foot H1's thrust, assist and delta-v. */
    private void docking(HudData d) {
        DockingApproach k = d.docking;
        FlightStatus f = d.flightStatus;
        docking.port = k.port > 0 ? "PORT " + k.port + "/" + k.ports + (k.portSize.isEmpty() ? "" : " " + k.portSize) : "PORT";
        docking.other = upper(k.other) + (k.otherPort > 0 ? " PORT " + k.otherPort : "");
        if (k.collarJammed || k.collarTorn) {
            docking.status = k.collarTorn ? "COLLAR TORN" : "JAMMED";
            docking.statusColor = NpColors.AMBER;
        } else if (d.ctx.docked) {
            docking.status = "DOCKED";
            docking.statusColor = NpColors.GREEN;
        } else {
            switch (k.clearance) {
                case GRANTED: docking.status = "CLEARED"; docking.statusColor = NpColors.GREEN; break;
                case DENIED: docking.status = "REFUSED"; docking.statusColor = NpColors.AMBER; break;
                case NO_PORT: docking.status = "NO FREE PORT"; docking.statusColor = NpColors.AMBER; break;
                case YIELDS: docking.status = "STRUCK"; docking.statusColor = NpColors.DIM; break;
                default:
                    docking.status = k.armed ? "ARMED" : "NOT ARMED";
                    docking.statusColor = k.armed ? NpColors.WHITE : NpColors.DIM;
            }
        }
        docking.keyArm = k.armed || d.ctx.docked ? null : key(d, Action.ARM_PORT);
        docking.keyPort = k.ports > 1 && !d.ctx.docked ? key(d, Action.NEXT_PORT) : null;
        if (d.ctx.docked) {
            docking.capture = "DOCKED";
            docking.captureColor = NpColors.GREEN;
        } else if (k.capture == DockingApproachProvider.Capture.CAPTURED) {
            docking.capture = "CAPTURED";
            docking.captureColor = NpColors.GREEN;
        } else if (d.orderState.helm == HelmOrder.DOCK) {
            docking.capture = "HELM DOCKING";
            docking.captureColor = NpColors.WHITE;
        } else {
            docking.capture = "";
        }
        docking.range = k.range;
        docking.lateral = k.lateral;
        docking.angle = k.angleDeg;
        docking.closing = k.closing;
        docking.safe = k.safeSpeed;
        docking.thrust = thrust(f);
        docking.firing = !"NONE".equals(docking.thrust);
        docking.assist = !f.present || f.rotationAssist;
        docking.dv = Double.isInfinite(f.deltaVLeft) ? "FREE" : Double.isNaN(f.deltaVLeft) ? "--" : NpFormat.mps(f.deltaVLeft);
    }

    // ---- H4 -------------------------------------------------------------------------------------

    /**
     * H4: dark while nominal, lit by severity; expanded while a light is lit and for 10 s after,
     * each light heading its row. On deck it shows only then; at fire control F3 has the shields, so
     * H4 keeps three lights and its compact face under F3. A ship with no emitter has no SHIELDS light.
     */
    private void condition(HudData d, boolean ship, boolean seated, boolean deck, boolean emitter, float dt) {
        Level[] lit = caution.lights;
        boolean any = false;
        for (Level l : lit) if (l == Level.WARNING || l == Level.CAUTION) any = true;
        conditionHold = any ? CONDITION_HOLD : Math.max(0f, conditionHold - dt);
        boolean open = any || conditionHold > 0f;
        boolean underF3 = shield.isVisible();
        condition.rows.clear();
        ShipStatus s = d.shipStatus;
        hullRow.level = lit[CautionRows.Light.HULL.ordinal()];
        hullRow.label = "INTEGRITY";
        hullRow.labelColor = NpColors.DIM;
        hullRow.left = null;
        hullRow.right = NpFormat.percent(s.structure);
        hullRow.rightUnit = null;
        hullRow.rightColor = NpColors.WHITE;
        hullRow.frac = s.structure;
        condition.rows.add(hullRow);
        if (emitter && !underF3) {
            ShieldStatus sh = d.shieldStatus;
            shieldRow.level = lit[CautionRows.Light.SHIELDS.ordinal()];
            shieldRow.label = sh.raised ? "CLOUD" : sh.loweredForDock ? "LOWERED TO DOCK" : "LOWERED";
            shieldRow.labelColor = NpColors.DIM;
            shieldRow.left = null;
            shieldRow.right = NpFormat.percent(sh.share());
            shieldRow.rightUnit = null;
            shieldRow.rightColor = NpColors.WHITE;
            shieldRow.frac = sh.share();
            condition.rows.add(shieldRow);
        }
        PowerFrame p = d.powerFrame;
        float charge = p.circuits.size > 0 ? p.charge() : s.power;
        powerRow.level = lit[CautionRows.Light.POWER.ordinal()];
        powerRow.label = "STORE";
        powerRow.labelColor = NpColors.DIM;
        powerRow.left = Float.isNaN(charge) ? "--" : NpFormat.percent(charge);
        powerRow.right = megawatts(p.outputWatts);
        powerRow.rightUnit = p.ratedWatts > 0 ? "OF " + megawatts(p.ratedWatts) + " MW" : "MW";
        powerRow.rightColor = p.ratedWatts > 0 && p.outputWatts <= 0 ? NpColors.RED
                : p.ratedWatts > 0 && p.outputWatts < CautionRows.OUTPUT_LOW * p.ratedWatts ? NpColors.AMBER : NpColors.WHITE;
        powerRow.frac = Float.isNaN(charge) ? Float.NaN : charge;
        condition.rows.add(powerRow);
        lifeRow.level = lit[CautionRows.Light.LIFE_SUP.ordinal()];
        lifeRow.left = null;
        lifeRow.rightColor = NpColors.WHITE;
        if (!Double.isNaN(s.emergencyOxygenKg)) {
            // on the reserve, its kilograms, named as P2's code and the cue name it
            lifeRow.label = "EMERG O2";
            lifeRow.labelColor = NpColors.RED;
            lifeRow.right = Long.toString(Math.round(s.emergencyOxygenKg));
            lifeRow.rightUnit = "KG";
            lifeRow.frac = s.emergencyOxygen;
        } else {
            lifeRow.label = "O2";
            lifeRow.labelColor = NpColors.DIM;
            lifeRow.right = Float.isNaN(s.oxygen) ? "--" : NpFormat.percent(s.oxygen);
            lifeRow.rightUnit = null;
            lifeRow.frac = s.oxygen;
        }
        condition.rows.add(lifeRow);
        condition.expanded = open && !underF3;
        condition.setVisible(ship && (seated || sit.docked || open) && !(deck && !open && !sit.docked));
    }

    private static String megawatts(double watts) {
        return watts <= 0 ? "0" : NpFormat.tenths(watts / 1e6);
    }

    // ---- H5 -------------------------------------------------------------------------------------

    /**
     * H5: north up, the range automatic unless set by hand: 2 km through an approach, else the
     * smallest step that holds every inbound missile and the Lock, the nearest hostile or the nearest
     * contact. In cruise at the helm with nothing inside 50 km it folds to a key with the count beyond.
     */
    private void scope(HudData d) {
        ContactProvider.ContactFrame cf = d.contactFrame;
        FlightStatus f = d.flightStatus;
        float nearest = Float.NaN;
        int beyond = 0;
        for (Contact c : cf.contacts) {
            if (c.kind == ContactProvider.Kind.WAYPOINT || Float.isNaN(c.range)) continue;
            if (c.range > SCOPE_NEAR) beyond++;
            if (Float.isNaN(nearest) || c.range < nearest) nearest = c.range;
        }
        scope.beyond = beyond;
        boolean quiet = seat == Seat.HELM && !sit.approach && !sit.combat;
        scope.fold(quiet && !scope.opened && (Float.isNaN(nearest) || nearest > SCOPE_NEAR));
        if (sit.approach && !sit.combat) {
            scope.auto = APPROACH_STEP;
        } else {
            float need = Float.NaN;
            for (Contact c : cf.contacts) {
                if (c.locked || c.kind == ContactProvider.Kind.MISSILE && c.threat) need = Float.isNaN(need) ? c.range : Math.max(need, c.range);
            }
            if (Float.isNaN(need)) {
                for (Contact c : cf.contacts) {
                    if (c.kind == ContactProvider.Kind.SHIP && c.relation == Relation.HOSTILE) { need = c.range; break; }
                }
            }
            if (Float.isNaN(need)) need = nearest;
            if (!Float.isNaN(need)) {
                int step = ContactScope.STEPS.length - 1;
                for (int i = 0; i < ContactScope.STEPS.length; i++) {
                    if (need <= ContactScope.STEPS[i] * 1000f * ROOM) { step = i; break; }
                }
                scope.auto = step;
            }
        }
        // the bubble's heading is counter-clockwise from +x; the scope's clockwise from north
        float hdg = Float.isNaN(f.heading) ? 90f : f.heading;
        scope.heading = ((90f - hdg) % 360f + 360f) % 360f;
        float range = scope.rangeMetres();
        blipPool.addAll(scope.blips);
        scope.blips.clear();
        for (Contact c : cf.contacts) {
            if (c.kind == ContactProvider.Kind.WAYPOINT && !c.nav) continue;
            if (Float.isNaN(c.range) || Float.isNaN(c.bearingDeg)) continue;
            double a = Math.toRadians(hdg + c.bearingDeg);
            float dx = (float) (c.range * Math.cos(a)), dy = (float) (c.range * Math.sin(a));
            NpInstruments.Blip b = blipPool.isEmpty() ? new NpInstruments.Blip() : blipPool.remove(blipPool.size() - 1);
            b.kind = kindOf(c);
            b.x = dx / range;
            b.y = -dy / range;
            b.locked = c.locked;
            b.nav = c.nav;
            b.label = null;
            // a missile's chevron points along its line to the ship
            b.heading = (float) ((90.0 - Math.toDegrees(Math.atan2(-dy, -dx))) % 360.0 + 360.0) % 360f;
            scope.blips.add(b);
        }
    }

    private static NpSymbols.Kind kindOf(Contact c) {
        switch (c.kind) {
            case STATION: return NpSymbols.Kind.STATION;
            case DERELICT: return NpSymbols.Kind.DERELICT;
            case MISSILE: return c.relation == Relation.FRIENDLY && !c.threat ? NpSymbols.Kind.OWN_MISSILE : NpSymbols.Kind.MISSILE;
            case EVA: return NpSymbols.Kind.FRIENDLY;
            default: break;
        }
        if (c.state == ContactProvider.State.WRECKED) return NpSymbols.Kind.DERELICT;
        return kindOf(c.relation);
    }

    private static NpSymbols.Kind kindOf(Relation r) {
        switch (r) {
            case HOSTILE: return NpSymbols.Kind.HOSTILE;
            case FRIENDLY: return NpSymbols.Kind.FRIENDLY;
            case MISSILE: return NpSymbols.Kind.MISSILE;
            case UNKNOWN: return NpSymbols.Kind.UNKNOWN;
            default: return NpSymbols.Kind.NEUTRAL;
        }
    }

    // ---- H6, F1, H7, F2, F3 ---------------------------------------------------------------------

    /** The lock card: H6's compact face at the helm, F1's full one at fire control and a gun. */
    private void lockCard(HudData d, boolean full) {
        TargetStatus t = d.targetStatus;
        lock.face(full);
        lock.name = upper(t.label);
        lock.kind = kindOf(t.relation);
        lockState(t);
        lock.firing = t.firingOnUs;
        lock.range = t.range;
        lock.closing = t.closing;
        lock.rel = t.relativeSpeed;
        lock.course = t.aspectDeg;
        lock.shot = d.targeting == null ? Float.NaN : d.targeting.getTimeToImpact();
        float best = Float.NaN;
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE || !m.working() || Float.isNaN(m.hitChance)) continue;
            if (Float.isNaN(best) || m.hitChance > best) best = m.hitChance;
        }
        lock.hit = best;
        lock.hitWord = Float.isNaN(best) ? "" : hitWord(best);
        lock.known = Float.isNaN(t.hull) ? "" : "HULL " + NpFormat.percent(t.hull);
    }

    /** The lock's state in one word (section 7, H6), and its box. */
    private void lockState(TargetStatus t) {
        String s = t.state == null ? "" : t.state;
        switch (s) {
            case "running": lock.state = "FLEEING"; lock.stateBox = NpHardware.BoxState.NORMAL; return;
            case "struck": lock.state = "SURRENDERED"; lock.stateBox = NpHardware.BoxState.OFF; return;
            case "silenced": lock.state = "SILENCED"; lock.stateBox = NpHardware.BoxState.NORMAL; return;
            case "NO_POWER":
            case "NO_CREW":
            case "HULL": lock.state = "DISABLED"; lock.stateBox = NpHardware.BoxState.OFF; return;
            case "ABANDONED": lock.state = "ABANDONED"; lock.stateBox = NpHardware.BoxState.OFF; return;
            case "WRECKED": lock.state = "WRECK"; lock.stateBox = NpHardware.BoxState.OFF; return;
            default: break;
        }
        lock.state = t.relation.name();
        lock.stateBox = t.relation == Relation.HOSTILE || t.relation == Relation.MISSILE ? NpHardware.BoxState.WARNING : NpHardware.BoxState.NORMAL;
    }

    static String hitWord(float h) {
        return h >= 0.7f ? "GOOD" : h >= 0.4f ? "FAIR" : h >= 0.15f ? "POOR" : "SLIM";
    }

    private static int pips(float h) {
        return h >= 0.7f ? 3 : h >= 0.4f ? 2 : h >= 0.15f ? 1 : 0;
    }

    /** H7: can the captain shoot, and with what: GUNS and who fires them, MSL and what is left, SHLD with an emitter. */
    private void armament(HudData d, boolean emitter) {
        WeaponStatusProvider.WeaponFrame wf = d.weaponFrame;
        armament.labels.clear();
        armament.words.clear();
        armament.colors.clear();
        boolean locked = d.targetStatus.present;
        boolean guns = false, pd = false, ready = false, inArc = false, noPower = false, yours = false, crew = false, console = false, launchers = false, launcherReady = false;
        float reload = Float.NaN, launcherIn = Float.NaN;
        for (Mount m : wf.mounts) {
            switch (m.kind) {
                case POINT_DEFENSE: pd = true; continue;
                case LAUNCHER:
                    launchers = true;
                    if (m.state == WeaponStatusProvider.State.READY) launcherReady = true;
                    else if (m.working() && (Float.isNaN(launcherIn) || m.cooldown < launcherIn)) launcherIn = m.cooldown;
                    continue;
                default: break;
            }
            guns = true;
            if (m.controller == WeaponStatusProvider.Controller.PLAYER) yours = true;
            if (m.controller == WeaponStatusProvider.Controller.CREW) crew = true;
            if (m.controller == WeaponStatusProvider.Controller.CONSOLE) console = true;
            if (m.state == WeaponStatusProvider.State.READY) {
                ready = true;
                if (m.inArc) inArc = true;
            } else if (m.state == WeaponStatusProvider.State.RELOADING || m.state == WeaponStatusProvider.State.CHARGING) {
                if (Float.isNaN(reload) || m.cooldown < reload) reload = m.cooldown;
            } else if (m.state == WeaponStatusProvider.State.NO_POWER) {
                noPower = true;
            }
        }
        if (guns) {
            String state;
            Color sc = NpColors.WHITE;
            if (ready && locked && !inArc) {
                state = "OUT OF ARC";
                sc = NpColors.DIM;
            } else if (ready) {
                state = "READY";
            } else if (!Float.isNaN(reload)) {
                state = "RELOAD " + NpFormat.tenths(reload) + " S";
            } else if (noPower) {
                state = "NO POWER";
                sc = NpColors.AMBER;
            } else {
                state = "DOWN";
                sc = NpColors.AMBER;
            }
            // you with one in hand, a gunner at a gun, crew at the weapons console, else only your trigger fires them
            String who = yours ? "YOU FIRE" : crew ? "GUNNER FIRES" : console ? "CONSOLE FIRES" : "YOUR TRIGGER";
            armament.row("GUNS", new String[] {state, who}, new Color[] {sc, yours ? NpColors.CYAN : NpColors.WHITE});
        } else if (pd) {
            armament.row("GUNS", new String[] {"POINT DEFENSE ONLY"}, new Color[] {NpColors.DIM});
        }
        if (launchers) {
            String left = wf.missilesLeft < 0 ? "NO LIMIT" : wf.missilesLeft + " LEFT";
            String when = launcherReady ? "READY" : Float.isNaN(launcherIn) ? "DOWN" : NpFormat.tenths(launcherIn) + " S";
            armament.row("MSL", new String[] {left, when}, new Color[] {wf.missilesLeft == 0 ? NpColors.AMBER : NpColors.WHITE, NpColors.WHITE});
        }
        if (emitter) {
            ShieldStatus s = d.shieldStatus;
            armament.row("SHLD", new String[] {s.raised ? "RAISED" : "LOWERED", "TRIM " + trimWord(s.trim)},
                    new Color[] {s.raised ? NpColors.WHITE : NpColors.DIM, NpColors.CYAN});
        }
        armament.setVisible(!armament.labels.isEmpty());
        armament.setSize(ArmamentStrip.W, armament.heightFor());
    }

    private static String trimWord(String trim) {
        return "starboard".equals(trim) ? "STBD" : upper(trim);
    }

    /** F2: a row per mount; at a gun seat only that gun's row and any launcher's. */
    private void rack(HudData d, boolean gun) {
        rack.rows.clear();
        boolean locked = d.targetStatus.present;
        for (Mount m : d.weaponFrame.mounts) {
            boolean inHand = m.controller == WeaponStatusProvider.Controller.PLAYER;
            if (gun && !inHand && m.kind != WeaponStatusProvider.Kind.LAUNCHER) continue;
            WeaponsRack.Row r = new WeaponsRack.Row();
            r.mount = WeaponsRack.place(m.name);
            r.weapon = WeaponsRack.weaponName(m.weapon);
            r.inHand = inHand;
            mountState(m, r, locked);
            if (m.kind == WeaponStatusProvider.Kind.LAUNCHER && m.ammo >= 0) r.ammo = m.ammo + " LEFT";
            if (m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE && locked && m.working() && !Float.isNaN(m.hitChance)) {
                r.pips = pips(m.hitChance);
                r.hitPct = NpFormat.percent(m.hitChance);
                r.hitWord = hitWord(m.hitChance);
            }
            switch (m.controller) {
                case PLAYER: r.who = "YOU"; r.whoColor = NpColors.CYAN; break;
                case CREW: r.who = "CREW"; r.whoColor = NpColors.WHITE; break;
                case CONSOLE: r.who = "CONSOLE"; r.whoColor = NpColors.WHITE; break;
                case AUTO: r.who = m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE ? "PD" : "AUTO"; r.whoColor = NpColors.WHITE; break;
                default: r.who = "--"; r.whoColor = NpColors.DIM; break;
            }
            rack.rows.add(r);
        }
        rack.setSize(WeaponsRack.W, rack.heightFor());
    }

    /** A mount's state in the spec's words (section 7, F2): point defense engaging reads green, as an engaged mode does. */
    private static void mountState(Mount m, WeaponsRack.Row r, boolean locked) {
        r.value = null;
        r.stateColor = NpColors.WHITE;
        switch (m.state) {
            case DESTROYED: r.state = "DESTROYED"; r.stateColor = NpColors.RED; return;
            case REFIT: r.state = "REFIT"; r.stateColor = NpColors.DIM; return;
            case SHORTED: r.state = "SHORTED"; r.stateColor = NpColors.AMBER; return;
            case STUNNED: r.state = "STUNNED"; r.stateColor = NpColors.AMBER; r.value = NpFormat.clock(m.cooldown); return;
            case NO_POWER: r.state = "NO POWER"; r.stateColor = NpColors.AMBER; return;
            case EMPTY: r.state = "EMPTY"; r.stateColor = NpColors.DIM; return;
            case RELOADING: r.state = "RELOAD"; r.value = NpFormat.tenths(m.cooldown) + " S"; return;
            case CHARGING: r.state = "CHARGING"; return;
            default: break;
        }
        if (m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE) {
            if (m.defending || m.engaging) {
                r.state = m.defending ? "ENGAGING MSL" : "ENGAGING";
                r.stateColor = NpColors.GREEN;
            } else {
                r.state = "IDLE";
                r.stateColor = NpColors.DIM;
            }
            return;
        }
        if (locked && !m.inArc) {
            r.state = "OUT OF ARC";
            r.stateColor = NpColors.DIM;
            return;
        }
        r.state = "READY";
    }

    /** F3: the ring of sectors, the cloud against capacity, raised or lowered, the trim, the emitters and their heat. */
    private void shieldControl(ShieldStatus s) {
        System.arraycopy(s.sectors, 0, shield.sectors, 0, shield.sectors.length);
        shield.cloud = s.cloud;
        shield.capacity = s.capacity;
        shield.heat = s.hottest;
        shield.trim = s.trim;
        if (s.raised) {
            shield.raised = "RAISED";
            shield.raisedBox = NpHardware.BoxState.ENGAGED;
        } else {
            shield.raised = s.loweredForDock ? "LOWERED TO DOCK" : "LOWERED";
            shield.raisedBox = NpHardware.BoxState.NORMAL;
        }
        if (s.powerStunned) {
            shield.emitters = "STUNNED";
            shield.emitterColor = NpColors.AMBER;
        } else if (s.emittersTripped > 0) {
            shield.emitters = s.emittersTripped + " OF " + s.emitters + " TRIPPED";
            shield.emitterColor = NpColors.AMBER;
        } else {
            shield.emitters = s.emittersWorking + " OF " + s.emitters + " HOLDING";
            shield.emitterColor = s.emittersWorking < s.emitters ? NpColors.AMBER : NpColors.WHITE;
        }
    }

    // ---- D1 -------------------------------------------------------------------------------------

    /**
     * D1, on deck while any field isn't normal or the suit is on: YOU, SUIT and its air (unless the
     * emergency card has the air), HERE and the air there, CARRY.
     */
    private void vitals(HudData d, boolean deck) {
        CaptainStatus c = d.captainStatus;
        boolean bleeding = !Float.isNaN(c.bleedOut);
        boolean hurt = c.health < 0.999f || !c.wound.isEmpty() || bleeding || !"conscious".equals(c.state);
        String air = airWord(c);
        boolean badAir = !"BREATHABLE".equals(air) && !air.isEmpty();
        vitals.setVisible(deck && c.present && (c.suited || hurt || badAir));
        if (!vitals.isVisible()) return;
        vitals.rows.clear();
        Vitals.Row you = new Vitals.Row();
        you.label = "YOU";
        if ("dead".equals(c.state)) { you.value = "DEAD"; you.color = NpColors.RED; }
        else if ("unconscious".equals(c.state)) { you.value = "UNCONSCIOUS"; you.color = NpColors.RED; }
        else if (bleeding) {
            you.value = "BLEEDING";
            you.color = NpColors.RED;
            you.hazard = true;
            you.right = NpFormat.clock(c.bleedOut);
            you.rightLabel = "OUT IN";
            you.rightColor = NpColors.RED;
        } else if (!c.wound.isEmpty()) { you.value = upper(c.wound); you.color = NpColors.AMBER; }
        else if (c.health < 0.999f) { you.value = "HURT " + NpFormat.percent(c.health); you.color = NpColors.AMBER; }
        else you.value = "OK";
        vitals.rows.add(you);
        Vitals.Row suit = new Vitals.Row();
        suit.label = "SUIT";
        suit.value = c.suited ? (c.suitKind.isEmpty() ? "ON" : "ON · " + upper(c.suitKind)) : "OFF";
        // the suit air's home is X3 while the card shows it (section 8, X3)
        if (c.suited && !Float.isNaN(c.suitAir) && !(emergency.isVisible() && suitAirShort(c))) {
            suit.right = NpFormat.clock(c.suitAir);
            suit.rightLabel = "AIR";
            suit.rightColor = c.suitAir < 60f ? NpColors.RED : c.suitAir < 300f ? NpColors.AMBER : NpColors.WHITE;
        }
        vitals.rows.add(suit);
        Vitals.Row here = new Vitals.Row();
        here.label = "HERE";
        here.value = air.isEmpty() ? "OUTSIDE" : air;
        here.color = "VACUUM".equals(air) ? NpColors.RED : badAir ? NpColors.AMBER : NpColors.WHITE;
        here.hazard = badAir;
        vitals.rows.add(here);
        if (!c.carrying.isEmpty()) {
            Vitals.Row carry = new Vitals.Row();
            carry.label = "CARRY";
            carry.value = upper(c.carrying);
            vitals.rows.add(carry);
        }
        vitals.setSize(Vitals.W, vitals.heightFor());
    }

    /** The air where the captain stands in a word: VACUUM, SMOKE, THIN or BREATHABLE; "" outside a room. */
    private static String airWord(CaptainStatus c) {
        if (Float.isNaN(c.roomPressure)) return "";
        if (c.roomPressure < 0.1f) return "VACUUM";
        if (!Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.05f) return "SMOKE";
        if (c.roomPressure < 0.7f || !Float.isNaN(c.roomOxygen) && c.roomOxygen < 0.7f) return "THIN";
        return "BREATHABLE";
    }

    // ---- X1, X2, X3 -----------------------------------------------------------------------------

    /** X1: the station and the port, who runs it and the standing there, each service it offers with its key, and undock. */
    private void station(HudData d) {
        StationInfo s = d.navReadout.docked;
        String name = upper(s.present && !s.name.isEmpty() ? s.name : d.ctx.stationName);
        int port = d.docking.otherPort > 0 ? d.docking.otherPort : d.docking.port;
        station.name = port > 0 ? name + " · PORT " + port : name;
        station.owner = s.owner.isEmpty() ? "" : s.owner + (s.sponsor ? " · your sponsor" : "");
        if (s.standingName.isEmpty()) station.standing = "";
        else if (Float.isNaN(s.standing)) station.standing = upper(s.standingName);
        else station.standing = upper(s.standingName) + " (" + (s.standing >= 0 ? "+" : NpFormat.MINUS) + Math.round(Math.abs(s.standing)) + ")";
        station.refusal = s.refusal.isEmpty() ? "" : upper(s.refusal);
        station.services.clear();
        if (s.market) station.services.add(new String[] {key(d, Action.TRADE), "MARKET"});
        if (s.yard) station.services.add(new String[] {key(d, Action.YARD), "YARD"});
        if (s.contracts) station.services.add(new String[] {key(d, Action.MISSIONS), "MISSIONS"});
        // the shipyard, hiring and the infirmary are tabs in the terminal, with no keys of their own
        if (s.shipyard) station.services.add(new String[] {"", "SHIPYARD"});
        if (s.hiring) station.services.add(new String[] {"", "HIRE"});
        if (s.infirmary) station.services.add(new String[] {"", "INFIRMARY"});
        station.undockKey = key(d, Action.UNDOCK);
        station.setSize(StationPanel.W, station.heightFor());
    }

    /**
     * X2: the other ship, latched or by EVA, how it stands, the party, salvage, cargo and prisoners
     * from the boarding board, and the ways on: K for the boarding window, U to cast off.
     */
    private void boarding(HudData d) {
        boolean latched = d.ctx.latched;
        if (board != null && board.isOpen()) boarding.rows.read(board, latched);
        else boarding.rows.only(latched ? d.docking.other : "", latched);
        boarding.boardKey = key(d, Action.BOARD);
        boarding.castOffKey = latched ? key(d, Action.UNDOCK) : "";
        boarding.setSize(BoardingPanel.W, boarding.heightFor());
    }

    /**
     * X3: the suit air first when it holds, then abandoning, or disabled with the ways out as guarded
     * switches; with two, the second as one line with its keys (section 8, X3).
     */
    private void emergency(HudData d) {
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        CaptainStatus c = d.captainStatus;
        EmergencyCard e = emergency;
        String pk = key(d, Action.ABANDON), bk = key(d, Action.CUT);
        e.abandonKey = pk;
        e.towKey = bk;
        boolean air = suitAirShort(c), disabled = disabledEmergency(d), abandoning = o.abandoning;
        e.second = "";
        if (air) {
            e.mode = EmergencyCard.Mode.SUIT_AIR;
            e.title = "YOUR SUIT AIR " + NpFormat.clock(c.suitAir);
            String here = airWord(c);
            e.line = (here.isEmpty() ? "OUTSIDE" : "HERE: " + here) + " · GET TO BREATHABLE AIR";
            e.lineKey = "";
            if (abandoning) e.second = "ABANDONING SHIP · " + pk + " LAUNCHES THE PODS";
            else if (disabled) e.second = disabledTitle(ctx.disabledReason) + " · " + pk + " TWICE" + (o.towRefusal.isEmpty() ? " · " + bk + " TWICE" : "");
        } else if (abandoning) {
            e.mode = EmergencyCard.Mode.ABANDONING;
            e.title = "ABANDONING SHIP";
            String pods = "IN THE PODS: " + o.inPods + " OF " + o.crewAboard;
            e.line = Float.isNaN(o.podsLaunchIn) ? pods : pods + " · LAUNCH " + NpFormat.tMinus(o.podsLaunchIn);
            e.lineKey = pk;
        } else {
            e.mode = EmergencyCard.Mode.DISABLED;
            e.title = disabledTitle(ctx.disabledReason);
            e.abandonArmed = o.abandonArmed;
            e.towArmed = o.towArmed;
            e.abandon = o.abandonArmed > 0f ? CtxDraw.Btn.ARMED : !o.abandonRefusal.isEmpty() ? CtxDraw.Btn.NA : CtxDraw.Btn.IDLE;
            // the tow only when the beacon allows it (DistressBeacon.refusal)
            e.towOffered = o.towRefusal.isEmpty() || o.towArmed > 0f;
            e.tow = o.towArmed > 0f ? CtxDraw.Btn.ARMED : CtxDraw.Btn.IDLE;
            e.towCost = o.towFee > 0 ? NpFormat.thousands(Math.round(o.towFee)) + " CR · " + Math.round(o.towStanding) + " STANDING" : "";
            if (o.abandonArmed > 0f) {
                e.foot = "ABANDON ARMED: " + pk + " AGAIN ORDERS IT";
                e.footColor = NpColors.AMBER;
            } else if (o.towArmed > 0f) {
                e.foot = "TOW ARMED: " + bk + " AGAIN CALLS IT";
                e.footColor = NpColors.AMBER;
            } else if (!o.abandonRefusal.isEmpty()) {
                e.foot = "CAN'T ABANDON: " + upper(o.abandonRefusal);
                e.footColor = NpColors.DIM;
            } else if (!o.towRefusal.isEmpty()) {
                e.foot = "NO TOW: " + upper(o.towRefusal);
                e.footColor = NpColors.DIM;
            } else {
                e.foot = "EACH TAKES TWO PRESSES";
                e.footColor = NpColors.DIM;
            }
        }
        e.setSize(EmergencyCard.W, e.heightFor());
    }

    private static String disabledTitle(String reason) {
        switch (reason) {
            case "reactor down": return "SHIP DISABLED: NO POWER";
            case "crew down": return "SHIP DISABLED: CREW DOWN";
            case "hull broken": return "SHIP DISABLED: HULL BROKEN";
            case "": return "SHIP DISABLED";
            default: return "SHIP DISABLED: " + upper(reason);
        }
    }

    // ---- C1 -------------------------------------------------------------------------------------

    /** C1: every order with its key, whether it runs, and each reason one can't be given once with the keys it blocks. */
    private void orders(HudData d) {
        Orders o = d.orderState;
        OrdersPanel c = orders;
        c.reasons.clear();
        c.blocked.clear();
        for (int i = 0; i < HELM_ORDERS.length; i++) {
            String k = key(d, ORDER_ACTIONS[i]);
            HelmOrder h = HELM_ORDERS[i];
            String refusal = o.helmRefusal[h.ordinal()];
            boolean running = h != HelmOrder.NONE && o.helm == h;
            c.helmKeys[i] = k;
            c.helm[i] = running ? CtxDraw.Btn.ON : refusal != null && !refusal.isEmpty() ? CtxDraw.Btn.NA : CtxDraw.Btn.IDLE;
            if (c.helm[i] == CtxDraw.Btn.NA) c.reason(reasonWords(refusal), k);
        }
        c.approach = o.helm == HelmOrder.APPROACH && !Float.isNaN(o.approachDistance)
                ? NpFormat.thousands(Math.round(o.approachDistance)) + " M" : "500 M";
        c.autopilotKey = key(d, Action.AUTOPILOT);
        switch (o.autopilot) {
            case ON: c.autopilot = CtxDraw.Btn.ON; c.autopilotState = "ON"; break;
            case HELD: c.autopilot = CtxDraw.Btn.ARMED; c.autopilotState = "HELD"; break;
            default: c.autopilot = CtxDraw.Btn.IDLE; c.autopilotState = "OFF"; break;
        }
        if (!o.autopilotRefusal.isEmpty()) {
            c.autopilot = CtxDraw.Btn.NA;
            c.reason(reasonWords(o.autopilotRefusal), c.autopilotKey);
        }
        c.suits = o.suitsAtBattleStations ? CtxDraw.Btn.ON : CtxDraw.Btn.IDLE;
        c.eva = o.evaHeld ? CtxDraw.Btn.ON : CtxDraw.Btn.IDLE;
        ShieldStatus s = d.shieldStatus;
        c.shieldKey = key(d, Action.SHIELDS);
        c.trimKey = key(d, Action.SHIELD_TRIM);
        c.trim = s.trim;
        if (!s.present || s.emitters == 0) {
            c.shields = CtxDraw.Btn.NA;
            c.shieldState = "--";
            c.trimNa = true;
            c.reason("NO SHIELD EMITTER ABOARD", c.trimKey + " " + c.shieldKey);
        } else if (!o.seated) {
            // on deck the shield orders take a seat, as the helm's do: one reason for them all
            c.shields = CtxDraw.Btn.NA;
            c.shieldState = s.raised ? "UP" : "DOWN";
            c.trimNa = true;
            String seatReason = o.helmRefusal[HelmOrder.FLY_BURNS.ordinal()];
            c.reason(reasonWords(seatReason == null || seatReason.isEmpty() ? "Take a seat to give orders" : seatReason), c.trimKey + " " + c.shieldKey);
        } else {
            c.shields = s.raised ? CtxDraw.Btn.ON : CtxDraw.Btn.IDLE;
            c.shieldState = s.raised ? "UP" : s.loweredForDock ? "DOCKING" : "DOWN";
            c.trimNa = false;
        }
        c.abandonKey = key(d, Action.ABANDON);
        c.towKey = key(d, Action.CUT);
        c.abandon = o.abandoning ? CtxDraw.Btn.ON : o.abandonArmed > 0f ? CtxDraw.Btn.ARMED : !o.abandonRefusal.isEmpty() ? CtxDraw.Btn.NA : CtxDraw.Btn.IDLE;
        c.abandonOpen = o.abandonArmed > 0f;
        if (c.abandon == CtxDraw.Btn.NA) c.reason(reasonWords(o.abandonRefusal), c.abandonKey);
        c.tow = o.towArmed > 0f ? CtxDraw.Btn.ARMED : !o.towRefusal.isEmpty() ? CtxDraw.Btn.NA : CtxDraw.Btn.IDLE;
        c.towOpen = o.towArmed > 0f;
        c.towCost = o.towFee > 0 ? NpFormat.thousands(Math.round(o.towFee)) + " CR" : "";
        if (c.tow == CtxDraw.Btn.NA) c.reason(reasonWords(o.towRefusal), c.towKey);
        c.setSize(OrdersPanel.W, c.heightFor());
    }

    /** A refusal as the foot words it: capitals, without a closing stop. */
    private static String reasonWords(String s) {
        String t = s == null ? "" : s.trim();
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return upper(t);
    }

    // ---- zones (section 5.1) --------------------------------------------------------------------

    /** Places each widget in its zone, stacked from the zone's outer edge inward, in {@code host.width()} by {@code host.height()} units. */
    private void layout() {
        float w = host.width(), h = host.height();
        root.setBounds(0f, 0f, w, h);
        strip.setSize(w, StatusStrip.H);
        put(strip, 0f, 0f, h);

        float[] z = ContextLayout.zone(ContextLayout.Zone.LTOP, w, h);
        cautions.setSize(CautionPanel.W, cautions.heightFor());
        put(cautions, z[0], z[1], h);

        z = ContextLayout.zone(ContextLayout.Zone.TOPC, w, h);
        float y = z[1];
        if (threat.isVisible()) {
            put(threat, z[0], y, h);
            y += ThreatAlert.H + GAP;
        }
        put(emergency, z[0], y, h);

        z = ContextLayout.zone(ContextLayout.Zone.RTOP, w, h);
        messages.setSize(MessageSlips.W, messages.heightFor());
        put(messages, z[0], z[1], h);

        z = ContextLayout.zone(ContextLayout.Zone.LMID, w, h);
        objective.setSize(ObjectiveSlip.W, objective.heightFor());
        threats.setSize(ThreatList.W, threats.heightFor());
        y = stack(objective, z[0], z[1], h);
        y = stack(nav, z[0], y, h);
        stack(threats, z[0], y, h);

        z = ContextLayout.zone(ContextLayout.Zone.RMID, w, h);
        y = stack(lock, z[0], z[1], h);
        y = stack(armament, z[0], y, h);
        y = stack(station, z[0], y, h);
        stack(boarding, z[0], y, h);

        lowerLeft(ContextLayout.zone(ContextLayout.Zone.LBOT, w, h), h);

        z = ContextLayout.zone(ContextLayout.Zone.RBOT, w, h);
        put(scope, z[0] + z[2] - scope.getWidth(), z[1] + z[3] - scope.getHeight(), h);

        bottomCentre(ContextLayout.zone(ContextLayout.Zone.BOTC, w, h), w, h);
    }

    /** L-BOTTOM: H4 at the foot; at fire control F3 above its compact face; on deck D1 at the foot and H4 above, compact if both won't fit. */
    private void lowerLeft(float[] z, float h) {
        float foot = z[1] + z[3];
        if (vitals.isVisible()) {
            put(vitals, z[0], foot - vitals.getHeight(), h);
            float room = z[3] - vitals.getHeight() - GAP;
            if (condition.expanded && condition.heightFor() > room) condition.expanded = false;
            condition.setSize(ConditionPanel.W, condition.heightFor());
            put(condition, z[0], foot - vitals.getHeight() - GAP - condition.getHeight(), h);
            return;
        }
        condition.setSize(ConditionPanel.W, condition.heightFor());
        put(condition, z[0], foot - condition.getHeight(), h);
        if (shield.isVisible()) put(shield, z[0], z[1], h);
    }

    /**
     * BOTTOM-CENTRE: the seat's primary instrument on the zone's foot, centred, with the master alert
     * cue as its hood; with none (on deck, docked, latched) the cue stands alone, and only while lit.
     * C1 opens with its foot on the hood, or on deck above the cue or on the zone's foot.
     */
    private void bottomCentre(float[] z, float w, float h) {
        float cx = w / 2f, foot = z[1] + z[3];
        Actor primary = flight.isVisible() ? flight : docking.isVisible() ? docking : rack.isVisible() ? rack : null;
        float top;
        if (primary != null) {
            float px = cx - primary.getWidth() / 2f, py = foot - primary.getHeight();
            put(primary, px, py, h);
            cue.shape(true, primary.getWidth());
            cue.setVisible(true);
            put(cue, px + 4f, py - MasterCue.HOOD_H, h);
            top = py - MasterCue.HOOD_H;
        } else {
            cue.shape(false, 0f);
            cue.setVisible(cue.level != null && host.data().ctx.ship != null && !host.hud().isMapOpen());
            float cy = z[1] + 78f;
            put(cue, cx - cue.getWidth() / 2f, cy, h);
            top = cue.isVisible() ? cy - GAP : foot;
        }
        put(orders, cx - OrdersPanel.W / 2f, top - orders.getHeight(), h);
    }

    /** Puts {@code a} at {@code y} down from the top if it shows; returns where the next one goes. */
    private float stack(Actor a, float x, float y, float h) {
        if (!a.isVisible()) return y;
        put(a, x, y, h);
        return y + a.getHeight() + GAP;
    }

    /** Puts {@code a}'s top left corner at ({@code x}, {@code yDown}), y down from the top of a screen {@code h} tall. */
    private static void put(Actor a, float x, float yDown, float h) {
        a.setPosition(Math.round(x), Math.round(h - yDown - a.getHeight()));
    }

    // ---- keys -----------------------------------------------------------------------------------

    /** The key bound to {@code a}, in capitals as a cap shows it. */
    private static String key(HudData d, Action a) {
        String k = d.key(a.name());
        return upper(k.isEmpty() ? a.defaultKeyName() : k);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (host == null) return false;
        if (keycode == Input.Keys.NUM_7) {
            ordersOpen = !ordersOpen;
            return true;
        }
        if (keycode == Input.Keys.BACKSLASH) {
            acknowledge();
            return true;
        }
        // an order's own key closes the menu, and the order still goes to the game
        if (ordersOpen && orderKey(keycode)) ordersOpen = false;
        return false;
    }

    private boolean orderKey(int keycode) {
        String name = Action.keyName(keycode);
        HudData d = host.data();
        for (Action a : ORDER_ACTIONS) if (name.equalsIgnoreCase(key(d, a))) return true;
        return false;
    }

    // ---- the rest -------------------------------------------------------------------------------

    private static String upper(String s) {
        return CautionRows.upper(s);
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        if (kit != null) kit.setScale(uiScale);
        screens.resize(uiScale);
    }

    @Override
    public void detach() {
        if (kit != null) kit.dispose();
        kit = null;
        host = null;
        screens.dispose();
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
    public Set<ClassicPart> keeps() {
        // The world's own symbology (W1 to W7) and the console prompt (P6) stay classic until this
        // HUD draws its own; X1 replaces the docked banner, H1 to H3 the flight readout
        return EnumSet.of(ClassicPart.WORLD_MARKERS, ClassicPart.THREAT_OVERLAY, ClassicPart.PROMPT);
    }
}
