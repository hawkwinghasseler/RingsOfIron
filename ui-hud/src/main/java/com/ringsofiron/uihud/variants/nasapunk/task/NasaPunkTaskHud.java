package com.ringsofiron.uihud.variants.nasapunk.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.DockingApproachProvider;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHintFrame;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ObjectiveProvider;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nasa-punk art on the task functional spec (documentation/ui-overhaul/functional-task.md; drawn
 * in documentation/ui-overhaul/variations/nasapunk-task). Widgets live in the spec's zones, each
 * anchored to its screen edge or corner, and show by the spec's visibility rules (section 8): the
 * clock and the lamps always, the alert band while an alert stands, the flight instrument at the
 * helm, the weapons at a gunnery post, the target card while a target is locked, the suit display
 * on deck while it has something to say.
 */
public class NasaPunkTaskHud implements HudVariant {
    private HudVariantHost host;
    private NpKit kit;
    private Group root;
    private Chronometer clock;
    private Annunciator lamps;
    private SuitDisplay suit;
    private AlertBand band;
    private Objectives objectives;
    private TargetCard target;
    private FlightDisplay flight;
    private HelmStrip helm;
    private WeaponsDisplay weapons;
    private ScopeDisplay scope;
    private HintStrip hints;
    private Presence lastPresence = Presence.NONE;
    /** FLT shows BURN from this long before the engine lights (the helm's 120 s turn lead and a minute), s. */
    private static final float BURN_LEAD = 180f;
    /** A scope step holds a contact with room to spare when it is inside this share of the step. */
    private static final float ROOM = 0.8f;
    /** Docked, the scope shows only while a hostile is this close, m. */
    private static final float DOCKED_SCOPE_RANGE = 50_000f;
    /** The hint line dims after this long without a change of context, s (section 6, HNT). */
    private static final float HINT_FADE = 10f;
    /** Keys the hint line shows before F1 (section 6, HNT). */
    private static final int HINT_KEYS = 4;
    /** The hints as last shown, to tell a change of context; and how long they have held. */
    private final StringBuilder hintSet = new StringBuilder(), lastHintSet = new StringBuilder();
    private float hintAge;
    /** Actions whose keys another widget already shows this frame, which the hint line leaves out. */
    private final Set<String> keysOnScreen = new HashSet<>();
    private final List<NpInstruments.Blip> blipPool = new ArrayList<>();

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        kit = new NpKit();
        kit.setScale(host.uiScale());
        root = new Group();
        root.setTransform(false);
        host.layer().addActor(root);
        clock = add(new Chronometer(kit));
        lamps = add(new Annunciator(kit));
        suit = add(new SuitDisplay(kit));
        band = add(new AlertBand(kit));
        objectives = add(new Objectives(kit));
        target = add(new TargetCard(kit));
        flight = add(new FlightDisplay(kit));
        helm = add(new HelmStrip(kit));
        weapons = add(new WeaponsDisplay(kit));
        scope = add(new ScopeDisplay(kit));
        hints = add(new HintStrip(kit));
    }

    private <T extends Actor> T add(T a) {
        root.addActor(a);
        return a;
    }

    @Override
    public void update(float delta) {
        HudData d = host.data();
        UiContext ctx = d.ctx;
        Presence p = ctx.presence;
        if (p != lastPresence) {
            // the scope's range override lasts until the post changes (section 6, SNS)
            scope.override = -1;
            lastPresence = p;
        }
        boolean gunnery = p == Presence.WEAPONS || p == Presence.GUN;
        boolean atHelm = p == Presence.HELM;
        boolean deck = !gunnery && !atHelm;
        boolean ship = ctx.ship != null;
        keysOnScreen.clear();
        // the console prompt's key is on screen while it shows, so the hint line leaves it out
        if (!d.promptAction.isEmpty()) keysOnScreen.add(Action.USE.name());

        TaskAlerts.apply(d, p, lamps, band, ctx.simSeconds);
        band.setVisible(band.warning != null || !band.cautions.isEmpty());
        if (band.warning != null) keysOnScreen.addAll(band.warning.named);
        lamps.setVisible(ship);
        objectives(d);
        news(d);

        suit.setVisible(deck && suitVisible(d.captainStatus));
        if (suit.isVisible()) suit(d.captainStatus);

        // docked, the card shows only a target other than the station (section 8)
        boolean targeted = (atHelm || gunnery) && d.targetStatus.present
                && !(ctx.docked && d.targetStatus.label.equalsIgnoreCase(ctx.stationName));
        target.setVisible(targeted);
        if (targeted) target(d.targetStatus);

        // FLT at the helm, outside rails warp; at gunnery HLM takes its place (section 6)
        flight.setVisible(atHelm && !ctx.railsWarp && ship);
        if (flight.isVisible()) flight(d);
        chronometer(d);
        boolean order = !ctx.helmOrder.isEmpty() || ctx.burnFlown || d.flightStatus.autopilot;
        helm.setVisible(ship && !ctx.docked && (atHelm ? order : true));
        helm(d);

        boolean armed = d.weaponFrame.mounts.size > 0;
        boolean helmWeapons = atHelm && armed && !ctx.docked && !ctx.railsWarp
                && (ctx.combat || d.targetStatus.present && d.targetStatus.relation == Relation.HOSTILE);
        weapons.setVisible(gunnery && armed || helmWeapons);
        weapons.setCompact(!gunnery);
        if (weapons.isVisible()) weapons(d, p);

        boolean scopeHere = (atHelm || gunnery) && !ctx.railsWarp && ship
                && (!ctx.docked || hostileWithin(d.contactFrame, DOCKED_SCOPE_RANGE));
        scope.setVisible(scopeHere);
        if (scopeHere) scope(d);

        hints(d.keyHints, delta);
        layout();
    }

    // ---- zones ----------------------------------------------------------------------------------

    private final TaskLayout.Sizes sizes = new TaskLayout.Sizes();

    /** Places each widget in its zone (see {@link TaskLayout}) in a window {@code host.width()} by {@code host.height()} units. */
    private void layout() {
        float w = host.width(), h = host.height();
        root.setBounds(0f, 0f, w, h);
        sizes.objectives = objectivesHeight();
        sizes.suit = suit.getHeight();
        sizes.flight = flight.isVisible();
        sizes.compactWeapons = weapons.compact;
        place(clock, TaskLayout.Part.CLOCK, w, h);
        place(lamps, TaskLayout.Part.LAMPS, w, h);
        place(suit, TaskLayout.Part.SUIT, w, h);
        place(band, TaskLayout.Part.BAND, w, h);
        place(objectives, TaskLayout.Part.OBJECTIVES, w, h);
        place(target, TaskLayout.Part.TARGET, w, h);
        place(scope, TaskLayout.Part.SCOPE, w, h);
        place(weapons, TaskLayout.Part.WEAPONS, w, h);
        place(flight, TaskLayout.Part.FLIGHT, w, h);
        place(helm, TaskLayout.Part.HELM, w, h);
        place(hints, TaskLayout.Part.HINTS, w, h);
    }

    private float objectivesHeight() {
        float oh = 0f;
        for (Objectives.Row r : objectives.rows) oh += (r.sub != null ? 42f : 26f) + 6f;
        return oh + objectives.news.size() * 24f;
    }

    /**
     * Puts {@code a} in its zone at the zone's size, so it takes no clicks beyond what it draws. The
     * suit display sizes itself to its rows as it draws, so only its top is placed.
     */
    private void place(Actor a, TaskLayout.Part p, float w, float h) {
        float[] b = TaskLayout.box(p, w, h, sizes);
        if (p != TaskLayout.Part.SUIT) a.setSize(b[2], b[3]);
        a.setPosition(Math.round(b[0]), Math.round(h - b[1] - a.getHeight()));
    }

    // ---- data -----------------------------------------------------------------------------------

    private void chronometer(HudData d) {
        UiContext ctx = d.ctx;
        NavReadout n = d.navReadout;
        double t = ctx.simSeconds;
        clock.day = "DAY " + (long) (t / 86400.0);
        clock.time = NpFormat.timeOfDay(t);
        clock.warp = NpFormat.thousands(ctx.warp) + "×" + (ctx.railsWarp ? " RAILS" : "");
        // one home for each time: FLT's BURN mode owns the burn's countdown, else the chronometer has it
        boolean fltBurn = flight.isVisible() && flight.mode == FlightDisplay.Mode.BURN;
        boolean burnHere = !fltBurn && (n.burning || n.burnsLeft > 0 && !Float.isNaN(n.burnStartIn));
        if (!ctx.warpHold.isEmpty()) {
            clock.note = "HELD: " + ctx.warpHold.toUpperCase(Locale.ROOT);
            clock.noteColor = NpColors.WHITE;
        } else if (burnHere) {
            clock.note = n.burning ? "BURNING" : "BURN " + NpFormat.tMinus(Math.max(0f, n.burnStartIn));
            clock.noteColor = NpColors.MAGENTA;
        } else if ((ctx.warp > 1 || ctx.railsWarp) && !Float.isNaN(ctx.warpNextStop)) {
            clock.note = "STOP " + NpFormat.tMinus(ctx.warpNextStop);
            clock.noteColor = NpColors.WHITE;
        } else {
            clock.note = null;
        }
    }

    /** OBJ: up to four contract rows, soonest deadline first (section 6). */
    private void objectives(HudData d) {
        ObjectiveProvider.ObjectiveFrame of = d.objectiveFrame;
        objectives.rows.clear();
        for (Objective o : of.objectives) {
            if (objectives.rows.size() == 4) break;
            if (o.state == ObjectiveProvider.State.DONE) continue;
            Objectives.Row r = new Objectives.Row();
            r.title = upper(o.kind.isEmpty() ? o.title : o.kind + ": " + o.title);
            r.nav = o.nextIsNav;
            boolean failed = o.state == ObjectiveProvider.State.FAILED;
            boolean returning = o.state == ObjectiveProvider.State.RETURN;
            r.late = o.late || failed || !Double.isNaN(o.timeLeft) && o.timeLeft < 0;
            r.left = failed ? "FAILED" : Double.isNaN(o.timeLeft) && returning ? "COLLECT" : NpFormat.timeLeft(o.timeLeft);
            // a second line only for the navigation target's row, and only with a next step
            if (r.nav && returning && !o.next.isEmpty()) r.sub = "RETURN LEG: " + upper(o.next);
            else if (r.nav && !of.operation.isEmpty() && of.operationSteps > 1) r.sub = "STEP " + of.operationStep + " OF " + of.operationSteps;
            objectives.rows.add(r);
        }
    }

    /** NTF: the three newest notices still fresh (section 6), on comm-loop slips. */
    private void news(HudData d) {
        objectives.news.clear();
        for (MessageFeed.Message m : d.messages.all()) {
            if (objectives.news.size() == 3) break;
            if (m.secondsLeft <= 0f || m.level == AlertRegistry.Level.WARNING) continue;
            // in combat, plain news waits until the fight ends; important news shows at once
            if (d.ctx.combat && !m.important) continue;
            Objectives.News n = new Objectives.News();
            n.tag = m.important ? "NEWS" : "INFO";
            n.msg = m.text;
            n.count = m.count > 1 ? "×" + m.count : null;
            objectives.news.add(n);
        }
        objectives.setVisible(!objectives.rows.isEmpty() || !objectives.news.isEmpty());
    }

    private static boolean suitVisible(CaptainStatus c) {
        if (!c.present) return false;
        boolean hurt = c.health < 0.999f || !c.wound.isEmpty() || !Float.isNaN(c.bleedOut);
        boolean badAir = !Float.isNaN(c.roomPressure) && c.roomPressure < 0.7f || !Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.05f;
        return c.suited || hurt || badAir;
    }

    private void suit(CaptainStatus c) {
        String[] r = suit.rows;
        Color[] col = suit.colors;
        boolean hurt = !c.wound.isEmpty() || !Float.isNaN(c.bleedOut);
        r[0] = "CONDITION";
        r[1] = !Float.isNaN(c.bleedOut) ? "BLEEDING" : hurt ? c.wound.toUpperCase(Locale.ROOT) : "UNHURT";
        col[0] = !Float.isNaN(c.bleedOut) ? NpColors.RED : hurt ? NpColors.AMBER : NpColors.WHITE;
        r[2] = c.suited ? (c.suitKind.isEmpty() ? "SUIT" : c.suitKind.toUpperCase(Locale.ROOT) + " SUIT") : "SUIT";
        if (c.suited && !Float.isNaN(c.suitAir)) {
            r[3] = "AIR " + NpFormat.clock(c.suitAir);
            col[1] = c.suitAir < 60f ? NpColors.RED : c.suitAir < 300f ? NpColors.AMBER : NpColors.WHITE;
        } else {
            r[3] = c.suited ? "ON" : "NONE";
            col[1] = NpColors.WHITE;
        }
        r[4] = "HERE";
        String air;
        Color ac;
        if (!Float.isNaN(c.roomPressure) && c.roomPressure < 0.1f) {
            air = "VACUUM";
            ac = NpColors.RED;
        } else if (!Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.05f) {
            air = "SMOKE";
            ac = NpColors.AMBER;
        } else if (!Float.isNaN(c.roomPressure) && c.roomPressure < 0.7f) {
            air = "THIN";
            ac = NpColors.AMBER;
        } else {
            air = "BREATHABLE";
            ac = NpColors.WHITE;
        }
        r[5] = (c.room.isEmpty() ? "" : c.room + ": ") + air;
        col[2] = ac;
    }

    private void target(TargetStatus t) {
        target.name = t.label.toUpperCase(Locale.ROOT);
        switch (t.relation) {
            case HOSTILE: target.kind = NpSymbols.Kind.HOSTILE; break;
            case FRIENDLY: target.kind = NpSymbols.Kind.FRIENDLY; break;
            case MISSILE: target.kind = NpSymbols.Kind.MISSILE; break;
            case UNKNOWN: target.kind = NpSymbols.Kind.UNKNOWN; break;
            default: target.kind = NpSymbols.Kind.NEUTRAL; break;
        }
        target.kindLine = t.relation.name();
        target.state = t.state.toUpperCase(Locale.ROOT);
        target.firingOnUs = t.firingOnUs;
        target.hull = t.hull;
        target.range = t.range;
        target.closing = t.closing;
        target.cpa = t.cpaDistance;
        target.cpaTime = t.cpaTime;
        target.footer = null;
    }

    private void helm(HudData d) {
        UiContext ctx = d.ctx;
        NavReadout n = d.navReadout;
        helm.who = ctx.helmsman.isEmpty() ? "NOBODY" : ctx.helmsman.toUpperCase(Locale.ROOT);
        if (ctx.burnFlown || n.burning) {
            StringBuilder o = new StringBuilder("FLYING BURN");
            if (n.burnNumber > 0) o.append(' ').append(n.burnNumber);
            float toGo = burnToGo(n);
            if (!Float.isNaN(toGo)) o.append(", ").append(NpFormat.mps(toGo)).append(" M/S TO GO");
            helm.order = o.toString();
            helm.state = NpHardware.BoxState.ENGAGED;
            helm.note = null;
        } else if (!ctx.helmOrder.isEmpty()) {
            helm.order = ctx.helmOrder.toUpperCase(Locale.ROOT);
            helm.state = ctx.helmsman.isEmpty() ? NpHardware.BoxState.NORMAL : NpHardware.BoxState.ENGAGED;
            helm.note = ctx.helmsman.isEmpty() ? "WAITING" : null;
        } else if (d.flightStatus.autopilot) {
            helm.order = "AUTOPILOT";
            helm.state = NpHardware.BoxState.ENGAGED;
            helm.note = null;
        } else {
            helm.order = "HOLDING HEADING";
            helm.state = NpHardware.BoxState.NORMAL;
            helm.note = "NO ORDER";
        }
    }

    /** The delta-v still to go on the burn being flown, else the next burn's, m/s; NaN with none. */
    private static float burnToGo(NavReadout n) {
        return n.burning ? n.burnToGo : n.burnDeltaV;
    }

    // ---- FLT ------------------------------------------------------------------------------------

    /** FLT's mode, the first that applies, a burn being flown beating DOCK (section 6, FLT), and its figures. */
    private void flight(HudData d) {
        UiContext ctx = d.ctx;
        FlightStatus f = d.flightStatus;
        NavReadout n = d.navReadout;
        DockingApproach k = d.docking;
        FlightDisplay fl = flight;
        fl.keys.clear();
        fl.checks.clear();
        fl.mainLit = f.mainEngine;
        fl.rcsLit = f.rcs;
        fl.assistOff = f.present && !f.rotationAssist;
        fl.freeThrust = Double.isInfinite(f.deltaVLeft);
        fl.dv = fl.freeThrust ? Double.NaN : f.deltaVLeft;
        fl.home = f.deltaVHome;
        fl.plan = f.deltaVPlanned > 0 ? f.deltaVPlanned : Double.NaN;
        boolean flying = n.burning || ctx.burnFlown;
        boolean burnSoon = n.burnsLeft > 0 && !Float.isNaN(n.burnStartIn) && n.burnStartIn <= BURN_LEAD;
        if (ctx.docked) fl.mode = FlightDisplay.Mode.DEPARTURE;
        else if (flying) fl.mode = FlightDisplay.Mode.BURN;
        else if (dockApplies(ctx, k)) fl.mode = FlightDisplay.Mode.DOCK;
        else if (burnSoon) fl.mode = FlightDisplay.Mode.BURN;
        else fl.mode = FlightDisplay.Mode.CRUISE;
        switch (fl.mode) {
            case DEPARTURE: departure(d); break;
            case DOCK: dock(d); break;
            case BURN: burn(d, flying); break;
            default: cruise(f); break;
        }
    }

    /**
     * DOCK applies with a port in reach of guidance (within 5 km) and a port armed, clearance asked,
     * a collar holding, or the helm ordered to dock; merely lining up with a port in range is not enough.
     */
    private static boolean dockApplies(UiContext ctx, DockingApproach k) {
        if (!k.present || Float.isNaN(k.range)) return false;
        return k.armed || k.clearance != DockingApproachProvider.Clearance.NONE || k.capture == DockingApproachProvider.Capture.CAPTURED
                || ctx.helmOrder.toLowerCase(Locale.ROOT).startsWith("dock");
    }

    private void cruise(FlightStatus f) {
        switch (f.frame) {
            case TARGET:
            case NAV:
            case STATION:
                flight.frame = upper(f.frameName);
                if (Float.isNaN(f.frameDistance)) flight.frameRange = "";
                else {
                    String[] r = NpFormat.range(f.frameDistance);
                    flight.frameRange = r[0] + " " + r[1];
                }
                break;
            case ORBIT:
                flight.frame = f.frameName.isEmpty() ? "ORBIT" : "ORBIT " + upper(f.frameName);
                flight.frameRange = "";
                break;
            default:
                flight.frame = "";
                flight.frameRange = "";
        }
        flight.speed = f.speed;
    }

    private void burn(HudData d, boolean flying) {
        NavReadout n = d.navReadout;
        FlightStatus f = d.flightStatus;
        flight.burn = n.burnNumber > 0 && n.burnsPlanned > 1 ? "BURN " + n.burnNumber + "/" + n.burnsPlanned : "BURN";
        flight.burning = flying;
        flight.burnIn = n.burnStartIn;
        flight.turnIn = n.turnIn;
        flight.burnToGo = burnToGo(n);
        flight.burnTime = n.burnTime;
        flight.pointing = n.pointingDeg;
        // who flies it, when that is you or the flight computer; a helmsman flying it is HLM's line
        flight.burnWho = flying || f.autopilot ? "COMPUTER FLIES" : "YOU FLY";
    }

    private void dock(HudData d) {
        DockingApproach k = d.docking;
        flight.port = "PORT " + k.port + "/" + k.ports + (k.portSize.isEmpty() ? "" : " " + k.portSize);
        if (k.collarJammed || k.collarTorn) {
            flight.clearance = k.collarTorn ? "COLLAR TORN" : "JAMMED";
            flight.clearanceColor = NpColors.AMBER;
        } else if (k.capture == DockingApproachProvider.Capture.CAPTURED) {
            flight.clearance = "CAPTURED";
            flight.clearanceColor = NpColors.GREEN;
        } else {
            switch (k.clearance) {
                case GRANTED:
                    flight.clearance = "CLEARED" + (k.otherPort > 0 ? " TO PORT " + k.otherPort : "");
                    flight.clearanceColor = NpColors.GREEN;
                    break;
                case DENIED:
                    flight.clearance = "REFUSED";
                    flight.clearanceColor = NpColors.AMBER;
                    break;
                case NO_PORT:
                    flight.clearance = "NO FREE PORT";
                    flight.clearanceColor = NpColors.AMBER;
                    break;
                case YIELDS:
                    flight.clearance = "STRUCK: NO CLEARANCE";
                    flight.clearanceColor = NpColors.DIM;
                    break;
                default:
                    flight.clearance = k.armed ? "ARMED" : "NOT ARMED";
                    flight.clearanceColor = k.armed ? NpColors.WHITE : NpColors.DIM;
            }
        }
        flight.dockRange = k.range;
        flight.dockClosing = k.closing;
        flight.safeSpeed = k.safeSpeed;
        flight.lateral = k.lateral;
        flight.angle = k.angleDeg;
        if (!k.armed) modeKey(Action.ARM_PORT, "ARM");
        if (k.ports > 1) modeKey(Action.NEXT_PORT, "PORT");
    }

    private void departure(HudData d) {
        UiContext ctx = d.ctx;
        flight.berth = upper(ctx.stationName);
        flight.berthPort = d.docking.capture == DockingApproachProvider.Capture.DOCKED && d.docking.port > 0 ? "PORT " + d.docking.port : "";
        // the readiness checks this HUD can read: delta-v, the hull and systems, the missiles
        if (flight.freeThrust) flight.checks.add(new FlightDisplay.Check("ΔV", "FREE", true));
        else if (!Double.isNaN(flight.dv)) {
            float prop = d.shipStatus.propellant;
            flight.checks.add(new FlightDisplay.Check("ΔV", NpFormat.mps(flight.dv), Float.isNaN(prop) || prop >= 0.25f));
        }
        float hull = d.shipStatus.structure;
        flight.checks.add(new FlightDisplay.Check("HULL", hull >= 0.999f ? "OK" : NpFormat.percent(hull), hull >= 0.999f));
        int faults = d.powerFrame.faults.size;
        flight.checks.add(new FlightDisplay.Check("SYSTEMS", faults == 0 ? "OK" : faults + (faults == 1 ? " FAULT" : " FAULTS"), faults == 0));
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind != WeaponStatusProvider.Kind.LAUNCHER || m.magazine <= 0) continue;
            int left = Math.max(0, m.ammo);
            flight.checks.add(new FlightDisplay.Check("MISSILES", left >= m.magazine ? "FULL" : left + "/" + m.magazine, left >= m.magazine));
            break;
        }
        modeKey(Action.UNDOCK, "UNDOCK");
        modeKey(Action.TRADE, "TERMINAL");
    }

    /** One of FLT's mode keys, which the hint line then leaves out. */
    private void modeKey(Action a, String verb) {
        String key = host.data().key(a.name());
        if (key.isEmpty()) return;
        flight.keys.add(new String[] {key.toUpperCase(Locale.ROOT), verb});
        keysOnScreen.add(a.name());
    }

    // ---- SNS ------------------------------------------------------------------------------------

    private static boolean hostileWithin(ContactProvider.ContactFrame cf, float metres) {
        for (Contact c : cf.contacts) {
            if (c.kind == ContactProvider.Kind.SHIP && c.relation == Relation.HOSTILE && c.range <= metres) return true;
        }
        return false;
    }

    /**
     * SNS: the range automatic unless set by hand, the smallest step that holds with room to spare
     * every inbound missile and the locked target; with neither the nearest hostile, else the
     * nearest contact. Contacts sit at their offsets north up, from their range and their bearing off
     * the nose.
     */
    private void scope(HudData d) {
        ContactProvider.ContactFrame cf = d.contactFrame;
        FlightStatus f = d.flightStatus;
        float need = Float.NaN;
        for (Contact c : cf.contacts) {
            if (c.locked || c.kind == ContactProvider.Kind.MISSILE && c.threat) need = Float.isNaN(need) ? c.range : Math.max(need, c.range);
        }
        if (Float.isNaN(need)) {
            for (Contact c : cf.contacts) {
                if (c.kind == ContactProvider.Kind.SHIP && c.relation == Relation.HOSTILE) { need = c.range; break; }
            }
        }
        if (Float.isNaN(need)) {
            for (Contact c : cf.contacts) {
                if (c.kind != ContactProvider.Kind.WAYPOINT) { need = c.range; break; }
            }
        }
        if (!Float.isNaN(need)) {
            int step = ScopeDisplay.STEPS.length - 1;
            for (int i = 0; i < ScopeDisplay.STEPS.length; i++) {
                if (need <= ScopeDisplay.STEPS[i] * 1000f * ROOM) { step = i; break; }
            }
            scope.auto = step;
        }
        // the bubble's heading is counter-clockwise from +x; the scope's is clockwise from north
        float hdg = Float.isNaN(f.heading) ? 90f : f.heading;
        scope.heading = ((90f - hdg) % 360f + 360f) % 360f;
        float range = scope.rangeMetres();
        blipPool.addAll(scope.blips);
        scope.blips.clear();
        int hostiles = 0;
        for (Contact c : cf.contacts) {
            if (c.kind == ContactProvider.Kind.SHIP && c.relation == Relation.HOSTILE
                    && c.state != ContactProvider.State.WRECKED && c.state != ContactProvider.State.STRUCK) hostiles++;
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
        scope.hostiles = hostiles == 0 ? null : hostiles + (hostiles == 1 ? " HOSTILE" : " HOSTILES");
    }

    private static NpSymbols.Kind kindOf(Contact c) {
        switch (c.kind) {
            case STATION: return NpSymbols.Kind.STATION;
            case DERELICT: return NpSymbols.Kind.DERELICT;
            case MISSILE: return c.relation == Relation.FRIENDLY && !c.threat ? NpSymbols.Kind.OWN_MISSILE : NpSymbols.Kind.MISSILE;
            // crew adrift outside: one of ours
            case EVA: return NpSymbols.Kind.FRIENDLY;
            default: break;
        }
        if (c.state == ContactProvider.State.WRECKED) return NpSymbols.Kind.DERELICT;
        switch (c.relation) {
            case HOSTILE: return NpSymbols.Kind.HOSTILE;
            case FRIENDLY: return NpSymbols.Kind.FRIENDLY;
            case MISSILE: return NpSymbols.Kind.MISSILE;
            case UNKNOWN: return NpSymbols.Kind.UNKNOWN;
            default: return NpSymbols.Kind.NEUTRAL;
        }
    }

    // ---- HNT ------------------------------------------------------------------------------------

    /**
     * HNT: up to four keys for what can be done here and now, most useful first, then help; a key
     * another widget shows is left out (section 6). It dims once the same keys have held for 10 s.
     * While furniture is placed, its own line takes the strip.
     */
    private void hints(KeyHintFrame kh, float delta) {
        hints.keys.clear();
        hintSet.setLength(0);
        if (kh.placing) {
            hints.keys.add(new String[] {"", upper(kh.placingText)});
            hintSet.append(kh.placingText);
        } else {
            KeyHint help = null;
            int shown = 0;
            for (int i = 0; i < kh.hints.size; i++) {
                KeyHint h = kh.hints.get(i);
                if (h.action.equals(Action.HELP.name())) { help = h; continue; }
                if (shown == HINT_KEYS || h.key.isEmpty() || keysOnScreen.contains(h.action)) continue;
                hints.keys.add(new String[] {upper(h.key), verb(h.label)});
                hintSet.append(h.key).append(h.label);
                shown++;
            }
            if (help != null) hints.keys.add(new String[] {upper(help.key), "CONTROLS"});
        }
        if (!hintSet.toString().contentEquals(lastHintSet)) {
            lastHintSet.setLength(0);
            lastHintSet.append(hintSet);
            hintAge = 0f;
        }
        hintAge += delta;
        hints.faded = hintAge > HINT_FADE;
        hints.setVisible(!hints.keys.isEmpty());
    }

    /** A hint's words as a key's verb: up to any comma, in capitals. */
    private static String verb(String label) {
        int comma = label.indexOf(',');
        return upper(comma > 0 ? label.substring(0, comma) : label);
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private void weapons(HudData d, Presence p) {
        WeaponStatusProvider.WeaponFrame f = d.weaponFrame;
        weapons.rows.clear();
        weapons.header = p == Presence.GUN ? "THIS GUN" : "GUNS IN HAND  ·  POINT DEFENSE AUTOMATIC";
        float tof = d.targeting == null ? Float.NaN : d.targeting.getTimeToImpact();
        boolean tofShown = false;
        String rounds = null;
        for (Mount m : f.mounts) {
            WeaponsDisplay.Row r = new WeaponsDisplay.Row();
            r.place = place(m.name);
            r.weapon = weaponName(m.weapon);
            switch (m.controller) {
                case PLAYER: r.who = "HAND"; r.whoColor = NpColors.CYAN; break;
                case CREW: r.who = "GUNNER"; r.whoColor = NpColors.WHITE; break;
                case CONSOLE: r.who = "CONSOLE"; r.whoColor = NpColors.WHITE; break;
                case AUTO: r.who = "AUTO"; r.whoColor = NpColors.DIM; break;
                default: r.who = "TRIGGER"; r.whoColor = NpColors.DIM; break;
            }
            state(m, r, d.targetStatus.present);
            if (m.kind == WeaponStatusProvider.Kind.LAUNCHER && m.magazine > 0) {
                rounds = Math.max(0, m.ammo) + "/" + m.magazine;
                r.extra = rounds;
            } else if (!tofShown && m.kind == WeaponStatusProvider.Kind.GUN && m.controller == WeaponStatusProvider.Controller.PLAYER
                    && !Float.isNaN(tof) && tof > 0f && m.working()) {
                // one time of flight: the gun in hand the lead reticle follows
                r.extra = String.format(Locale.ROOT, "%.1f", tof);
                r.extraUnit = "S";
                r.extraIsTof = true;
                tofShown = true;
            }
            r.pip = m.state == WeaponStatusProvider.State.READY ? 0 : m.working() ? 1 : 2;
            weapons.rows.add(r);
        }
        WeaponsDisplay.Row decoy = new WeaponsDisplay.Row();
        decoy.place = "DECOY";
        decoy.who = "HAND";
        decoy.whoColor = NpColors.CYAN;
        boolean decoyReady = f.decoyCooldown <= 0f;
        decoy.state = decoyReady ? "READY" : String.format(Locale.ROOT, "%.0f S", Math.ceil(f.decoyCooldown));
        weapons.rows.add(decoy);
        weapons.rounds = rounds;
        weapons.decoy = decoyReady ? "READY" : decoy.state;
        // the shields, on a ship with an emitter: up with the cloud's share of capacity, or down
        boolean emitter = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        weapons.shields = emitter ? (d.shieldStatus.raised ? "UP" : "DOWN") : null;
        if (emitter) {
            WeaponsDisplay.Row sh = new WeaponsDisplay.Row();
            sh.place = "SHIELDS";
            sh.who = "";
            if (d.shieldStatus.emittersTripped > 0 && d.shieldStatus.emittersWorking == 0) {
                sh.state = "TRIPPED";
                sh.box = NpHardware.BoxState.CAUTION;
            } else if (d.shieldStatus.raised) {
                sh.state = "UP " + NpFormat.percent(d.shieldStatus.share());
            } else {
                sh.state = "DOWN";
                sh.stateColor = NpColors.DIM;
            }
            weapons.rows.add(sh);
        }
    }

    /**
     * A mount's place without the kind word the game's name ends on ("FWD PORT PD" is FWD PORT),
     * since the weapon column says what it is; a name that is only the kind keeps it.
     */
    static String place(String name) {
        String[] w = name.split(" ");
        StringBuilder b = new StringBuilder();
        for (String s : w) {
            if (s.equals("GUN") || s.equals("PD") || s.equals("MSL") || s.equals("BEAM")) continue;
            if (b.length() > 0) b.append(' ');
            b.append(s);
        }
        // a lone number ("2") says nothing without its kind
        String out = b.toString();
        return out.isEmpty() || out.matches("[0-9]+") ? name : out;
    }

    private static final java.util.regex.Pattern MARK = java.util.regex.Pattern.compile("\\s+MK\\s*[0-9IVX]+$");

    /** A weapon's name in capitals without its mark ("Coilgun Mk1" is COILGUN). */
    static String weaponName(String weapon) {
        return MARK.matcher(upper(weapon)).replaceAll("").trim();
    }

    /** A mount's state in the spec's words, and how it is boxed. */
    private static void state(Mount m, WeaponsDisplay.Row r, boolean locked) {
        r.box = null;
        r.stateColor = NpColors.WHITE;
        switch (m.state) {
            case DESTROYED: r.state = "DESTROYED"; r.box = NpHardware.BoxState.WARNING; return;
            case REFIT: r.state = "REFIT"; r.box = NpHardware.BoxState.OFF; return;
            case SHORTED: r.state = "SHORTED"; r.box = NpHardware.BoxState.CAUTION; return;
            case STUNNED: r.state = "STUNNED " + NpFormat.clock(m.cooldown); r.box = NpHardware.BoxState.CAUTION; return;
            case NO_POWER: r.state = "NO POWER"; r.box = NpHardware.BoxState.CAUTION; return;
            case EMPTY: r.state = "EMPTY"; r.box = NpHardware.BoxState.OFF; return;
            case CHARGING: r.state = "CHARGING"; return;
            case RELOADING: r.state = String.format(Locale.ROOT, "RELOAD %.1f S", m.cooldown); return;
            default: break;
        }
        if (m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE) {
            if (m.defending || m.engaging) {
                r.state = "ENGAGING";
                r.box = NpHardware.BoxState.ENGAGED;
            } else {
                r.state = "IDLE";
                r.stateColor = NpColors.DIM;
            }
            return;
        }
        if (locked && !m.inArc) {
            r.state = "OUT OF ARC";
            r.box = NpHardware.BoxState.OFF;
            return;
        }
        r.state = "READY";
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        if (kit != null) kit.setScale(uiScale);
    }

    @Override
    public void detach() {
        if (kit != null) kit.dispose();
        kit = null;
    }

    @Override
    public Set<ClassicPart> keeps() {
        // The world's own symbology and the console prompt stay classic until this HUD draws its own
        // (INT, the prompt beside the object); FLT's DEPARTURE mode replaces the docked banner
        return EnumSet.of(ClassicPart.WORLD_MARKERS, ClassicPart.THREAT_OVERLAY, ClassicPart.PROMPT);
    }
}
