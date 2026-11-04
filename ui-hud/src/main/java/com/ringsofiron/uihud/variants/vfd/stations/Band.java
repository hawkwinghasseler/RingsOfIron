package com.ringsofiron.uihud.variants.vfd.stations;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.ContactProvider.State;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.FlightStatusProvider.Frame;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.UiContextProvider.Condition;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Mfd;
import com.ringsofiron.uihud.variants.vfd.stations.Mfds.Page;

import java.util.Locale;

import static com.ringsofiron.uihud.variants.vfd.stations.Geo.BAND_W;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.BAND_X;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.BAND_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.BOARD_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.CL;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.CLUSTER_H;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.CLUSTER_W;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.GLASS_B;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.KEY_H;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.KEY_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.LMFD;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.MFD_W;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.PAGE_H;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.PAGE_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.RAIL_Y;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.RMFD;
import static com.ringsofiron.uihud.variants.vfd.stations.Geo.SLOT;

/**
 * The seated band (functional-stations 8, 9): one console rack holding the two MFDs either side of the
 * seat's cluster (the helm's five slots, or the weapons console's tactical slots), and the station
 * board under them with the chronometer at its end. A folded MFD sinks into the rack and leaves its
 * soft-key rail. In a blackout every tube is dark and each instrument shows its NO POWER face. At a gun
 * there is no band, only the gun's own plate.
 */
final class Band {
    static final int CLICK_SOFT = 200, CLICK_BOARD = 300;
    /** Soft keys per MFD: three pages, then pin and fold. */
    static final int PIN = 3, FOLD = 4;
    static final String[] PIN_GLYPH = {".###.", ".###.", ".###.", "#####", "..#..", "..#..", "..#.."};
    static final String[] FOLD_GLYPH = {".....", "#...#", ".#.#.", "..#..", ".....", "#####", "....."};

    private final Pages pages;
    final Mfds mfds;

    /** What shows: the band at a command seat, the gun plate at a gun, nothing on deck. */
    Presence seat = Presence.NONE;
    boolean blackout, green;

    Band(Mfds mfds, Pages pages) {
        this.mfds = mfds;
        this.pages = pages;
    }

    // ------------------------------------------------------------------ the helm cluster's slots (8.1)

    final boolean[] frames = new boolean[4];
    static final String[] FRAMES = {"ORBIT", "TARGET", "PORT", "DOCKED"};
    String speed = "", speedUnit = "", control = "", rot = "";
    boolean controlHeld, assist, thr, rcs;
    // Reference
    boolean ref;
    String refName = "", refRange = "", refCls = "", refCpa = "", refCpaIn = "";
    boolean refKm, cpaKm;
    // Next event
    boolean next;
    String nextWhat = "", nextWhen = "", nextDv = "", nextDir = "";
    // Trigger
    boolean trig;
    String trigGuns = "", trigMode = "", trigMsl = "", trigDcy = "";
    boolean shields, shieldsDown;
    // Delta-v
    String dvLeft = "", dvHome = "", dvPlan = "";
    float dvFrac, dvHomeFrac = Float.NaN, dvPlanFrac;

    // ------------------------------------------------------------------ the tactical cluster's slots (8.3)

    /** The target's name: "FREIGHTER" whole. */
    static final int TGT_CELLS = 9;
    String tgtName = "", tgtClass = "", tgtRange = "", tgtCls = "", tgtState = "";
    boolean tgtKm, tgtPresent;
    static final int GUN_CHIPS = 2;
    final GunChip[] guns = {new GunChip(), new GunChip(), new GunChip()};
    int gunCount, gunsMore;
    String pdCount = "", pdState = "";
    boolean pdPresent;
    String lPos = "", lLeft = "", lSize = "", lSecs = "", lSalvo = "";
    boolean lPresent, lReady, lSalvoAll;
    String decoy = "";
    boolean decoyReady;
    String shMass = "";
    boolean shRaised;

    static final class GunChip {
        String pos = "", who = "", state = "", hit = "";
        boolean ready;
    }

    // ------------------------------------------------------------------ the board (8.5)

    static final String[] BOARD = {"1 HELM", "2 NAV", "3 TAC", "4 ENG", "5 COMMS", "6 CMD"};
    final String[] who = new String[6];
    final boolean[] you = new boolean[6];
    final Level[] boardAlert = new Level[6];
    boolean latched;
    String day = "", time = "";

    // ------------------------------------------------------------------ reading

    void update(HudData d) {
        UiContext ctx = d.ctx;
        seat = ctx.presence;
        green = ctx.condition == Condition.GREEN;
        shields = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        board(d);
        if (seat == Presence.HELM) helm(d);
        if (seat == Presence.WEAPONS || seat == Presence.GUN) tactical(d);
    }

    private void board(HudData d) {
        UiContext ctx = d.ctx;
        Seat helm = d.stations.at(Post.HELM), weapons = d.stations.at(Post.WEAPONS);
        Seat reactor = d.stations.at(Post.REACTOR), life = d.stations.at(Post.LIFE_SUPPORT);
        who[0] = holder(helm);
        who[1] = who[0];
        who[2] = holder(weapons);
        String eng = holder(reactor);
        who[3] = eng.isEmpty() ? holder(life) : eng;
        who[4] = seat == Presence.HELM ? "YOU" : "";
        who[5] = "";
        for (int i = 0; i < 6; i++) you[i] = false;
        if (seat == Presence.HELM) you[0] = you[1] = you[4] = true;
        if (seat == Presence.WEAPONS) you[2] = true;
        for (int i = 0; i < 6; i++) boardAlert[i] = null;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.level == Level.ADVISORY) continue;
            int s = station(a.category);
            if (boardAlert[s] == null || a.level.ordinal() > boardAlert[s].ordinal()) boardAlert[s] = a.level;
        }
        latched = ctx.latched;
        day = Integer.toString(Fmt.day(ctx.simSeconds));
        time = Fmt.clock(ctx.simSeconds);
    }

    /** Who holds a post, as the board prints it: YOU, a name, or nobody. */
    private static String holder(Seat s) {
        if (s == null) return "";
        if (s.hand == Hand.PLAYER) return "YOU";
        if (s.hand == Hand.CREW) return Fmt.cut(s.who, 8);
        return "";
    }

    /** The station an alert belongs to, by the board's numbering. */
    static int station(Category c) {
        switch (c) {
            case FLIGHT: return 0;
            case NAV: return 1;
            case TACTICAL: return 2;
            case ENGINEERING:
            case LIFE_SUPPORT: return 3;
            case COMMS: return 4;
            default: return 5;
        }
    }

    /**
     * The docking approach has the cluster (8.1): the selected port is armed, or a free port is in
     * guidance range on the station that is the nav target, or the collars have caught. The game
     * reports any port in range, a hostile's too, which is no reason to hand it Reference.
     */
    static boolean approach(DockingApproach dk, NavReadout nav) {
        if (!dk.present) return false;
        if (dk.capture == Capture.CAPTURED) return true;
        if (dk.capture != Capture.APPROACH) return false;
        return dk.armed || nav.present && nav.kind == NavigationProvider.Kind.STATION && nav.name.equalsIgnoreCase(dk.other);
    }

    private void helm(HudData d) {
        FlightStatus fl = d.flightStatus;
        NavReadout nav = d.navReadout;
        TargetStatus tg = d.targetStatus;
        DockingApproach dk = d.docking;
        UiContext ctx = d.ctx;
        // Velocity: the frame, the speed in it, the control chip, rotation, assist, the thrusters
        for (int i = 0; i < 4; i++) frames[i] = false;
        boolean approach = approach(dk, nav);
        if (ctx.docked || dk.capture == Capture.DOCKED) frames[3] = true;
        else if (approach) frames[2] = true;
        else if (fl.frame == Frame.TARGET || fl.frame == Frame.NAV || fl.frame == Frame.STATION) frames[1] = true;
        else if (fl.present) frames[0] = true;
        float v = fl.speed;
        if (frames[3]) {
            speed = "";
            speedUnit = "";
        } else if (!Float.isNaN(v) && v >= 1000f) {
            speed = Fmt.fit(v / 1000f, 4, 2);
            speedUnit = "KM/S";
        } else {
            speed = Fmt.fit(v, 4, 1);
            speedUnit = Float.isNaN(v) ? "" : "M/S";
        }
        controlHeld = false;
        if (nav.burning || ctx.burnFlown) control = "BURN " + Math.max(1, nav.burnNumber);
        else if (fl.autopilot) control = "AUTO";
        else if (d.orderState.helm != com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder.NONE) {
            control = "HELD";
            controlHeld = true;
        } else control = "MANUAL";
        float spin = Float.isNaN(fl.spin) ? 0f : Math.abs(fl.spin);
        rot = spin > 0.5f ? Fmt.fit(Math.min(spin, 99f), 2, 0) : "";
        assist = fl.rotationAssist;
        thr = fl.mainEngine;
        rcs = fl.rcs;

        // Reference: the lock, else the nav target
        ref = tg.present || nav.present;
        if (tg.present) {
            refName = Fmt.name(tg.label, 12);
            reference(tg.range, tg.closing, tg.cpaDistance, tg.cpaTime);
        } else if (nav.present) {
            refName = Fmt.name(nav.name, 12);
            reference((float) nav.distance, nav.closing, (float) nav.cpaDistance, nav.cpaIn);
        } else {
            refName = refRange = refCls = refCpa = refCpaIn = "";
        }
        if (approach && !Float.isNaN(dk.range)) {
            // The approach takes over Reference: the port, its range and closing
            ref = true;
            refName = Fmt.cut(dk.other + (dk.otherPort > 0 ? " P" + dk.otherPort : ""), 12);
            reference(dk.range, dk.closing, Float.NaN, Float.NaN);
        }

        // Next event: the next burn, or the autopilot's leg
        next = false;
        nextWhat = nextWhen = nextDv = nextDir = "";
        if (nav.burning && !Float.isNaN(nav.burnToGo)) {
            next = true;
            nextWhat = "BURN " + Math.max(1, nav.burnNumber);
            nextWhen = "";
            nextDv = Fmt.fit(nav.burnToGo, 3, 0);
            nextDir = direction(fl, nav.burnHeadingDeg);
        } else if (!Float.isNaN(nav.burnIn)) {
            next = true;
            nextWhat = "BURN " + Math.max(1, nav.burnNumber);
            float in = !Float.isNaN(nav.burnStartIn) ? nav.burnStartIn : nav.burnIn;
            nextWhen = when(in);
            nextDv = Fmt.fit(nav.burnDeltaV, 3, 0);
            nextDir = direction(fl, nav.burnHeadingDeg);
        } else if (fl.autopilot) {
            next = true;
            nextWhat = "AUTO";
            nextWhen = "";
        }

        // Trigger: with a lock, or in Red
        WeaponFrame w = d.weaponFrame;
        trig = tg.present || ctx.condition == Condition.RED;
        boolean anyGun = false, anyReady = false, crewOnGun = false;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            if (m.kind == com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.LAUNCHER
                    || m.kind == com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.POINT_DEFENSE) continue;
            anyGun = true;
            if (m.state == com.ringsofiron.uihud.providers.WeaponStatusProvider.State.READY) anyReady = true;
            if (m.controller == Controller.CREW || m.controller == Controller.CONSOLE) crewOnGun = true;
        }
        trigGuns = !anyGun ? "" : anyReady ? "RDY" : "RLD";
        trigMode = !anyGun ? "" : w.holdFire ? "HOLD" : crewOnGun ? "AT WILL" : "TRIGGER";
        trigMsl = missiles(w);
        trigDcy = w.decoyCooldown <= 0f ? "RDY" : Fmt.secs(w.decoyCooldown) + "S";
        shieldsDown = shields && !d.shieldStatus.raised;

        // Delta-v: left, the trip home, the plan
        double left = fl.deltaVLeft, home = fl.deltaVHome, plan = fl.deltaVPlanned;
        float frac = d.hasShipStatus ? d.shipStatus.propellant : Float.NaN;
        dvLeft = Double.isNaN(left) ? "" : Fmt.fit(Math.min(left, 9999), 4, 0);
        dvHome = Double.isNaN(home) ? "" : Fmt.fit(Math.min(home, 9999), 4, 0);
        dvPlan = plan > 0 ? Fmt.fit(Math.min(plan, 9999), 4, 0) : "";
        dvFrac = Float.isNaN(frac) ? 0f : MathUtils.clamp(frac, 0f, 1f);
        // The bar is the tanks; the notch and the bracket go where their delta-v would leave them
        double full = !Double.isNaN(left) && frac > 0.01f ? left / frac : Double.NaN;
        dvHomeFrac = Double.isNaN(full) || Double.isNaN(home) ? Float.NaN : (float) MathUtils.clamp(home / full, 0, 1);
        dvPlanFrac = Double.isNaN(full) || plan <= 0 ? 0f : (float) MathUtils.clamp(plan / full, 0, dvFrac);
    }

    private void reference(float range, float closing, float cpa, float cpaIn) {
        refRange = Fmt.range(range, 4);
        refKm = Fmt.km(range);
        refCls = Float.isNaN(closing) ? "" : Fmt.fit(closing, 4, Math.abs(closing) < 10f ? 1 : 0);
        cpaKm = !Float.isNaN(cpa) && cpa >= 1000f;
        refCpa = Float.isNaN(cpa) ? "" : cpaKm ? Fmt.fit(cpa / 1000f, 3, 1) : Fmt.fit(Math.round(cpa), 3, 0);
        refCpaIn = Float.isNaN(cpaIn) || cpaIn < 0 ? "" : cpaIn < 600f ? Fmt.mmss(cpaIn) : Fmt.fit(cpaIn / 60f, 3, 0);
    }

    /** A time on a four-cell clock: m:ss under ten minutes, mm:ss under an hour, h:mm past it. */
    static String when(float seconds) {
        if (Float.isNaN(seconds) || seconds < 0) return "";
        if (seconds < 3600f) return Fmt.mmss(seconds);
        long m = (long) Math.ceil(seconds / 60f);
        if (m / 60 < 100) return String.format(Locale.ROOT, "%d:%02d", m / 60, m % 60);
        return "99:59";
    }

    /** A burn's direction against the velocity in the frame: PRO, RETRO, or RAD across it. */
    static String direction(FlightStatus fl, float headingDeg) {
        if (Float.isNaN(headingDeg) || (fl.vx == 0f && fl.vy == 0f)) return "";
        float vel = MathUtils.atan2(fl.vy, fl.vx) * MathUtils.radiansToDegrees;
        float off = Math.abs(((headingDeg - vel) % 360f + 540f) % 360f - 180f);
        return off < 45f ? "PRO" : off > 135f ? "RETRO" : "RAD";
    }

    /** The launchers' missiles for the trigger slot: left, and ready or seconds. */
    private static String missiles(WeaponFrame w) {
        int left = 0;
        boolean any = false, ready = false;
        float soonest = Float.MAX_VALUE;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            if (m.kind != com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind.LAUNCHER) continue;
            any = true;
            if (m.ammo > 0) left += m.ammo;
            if (m.state == com.ringsofiron.uihud.providers.WeaponStatusProvider.State.READY && m.ammo != 0) ready = true;
            else if (m.cooldown > 0f) soonest = Math.min(soonest, m.cooldown);
        }
        if (!any) return "";
        if (w.missilesLeft < 0) return ready ? "RDY" : soonest < Float.MAX_VALUE ? Fmt.secs(soonest) + "S" : "";
        if (left == 0) return "NONE";
        return left + (ready ? " RDY" : soonest < Float.MAX_VALUE ? " " + Fmt.secs(soonest) + "S" : "");
    }

    private void tactical(HudData d) {
        TargetStatus tg = d.targetStatus;
        tgtPresent = tg.present;
        tgtName = Fmt.name(tg.label, TGT_CELLS);
        tgtClass = "";
        tgtState = "";
        Contact locked = d.contactFrame.locked();
        if (locked != null) {
            tgtClass = Fmt.cut(locked.kind == Kind.STATION ? "STATION" : relationWord(locked), 15);
            if (locked.state == State.STRUCK) tgtState = "STRUCK";
            else if (locked.state == State.DISABLED || locked.state == State.WRECKED) tgtState = "DISABLED";
        }
        if (tgtState.isEmpty() && tg.state != null && tg.state.toLowerCase(Locale.ROOT).contains("vent")) tgtState = "VENTING";
        tgtRange = Fmt.range(tg.range, 3);
        tgtKm = Fmt.km(tg.range);
        tgtCls = Float.isNaN(tg.closing) ? "" : Fmt.fit(tg.closing, 3, 0);

        // The guns in hand, point defense, the launcher, the decoy, the shields
        WeaponFrame w = d.weaponFrame;
        gunCount = 0;
        gunsMore = 0;
        int pdAll = 0, pdWorking = 0;
        boolean pdEngaging = false;
        lPresent = false;
        Mount launcher = null;
        Mount seatedGun = seat == Presence.GUN ? gunAt(d) : null;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            switch (m.kind) {
                case POINT_DEFENSE:
                    pdAll++;
                    if (m.working()) pdWorking++;
                    if (m.engaging || m.defending) pdEngaging = true;
                    break;
                case LAUNCHER:
                    if (launcher == null && (m.controller == Controller.PLAYER || seat != Presence.WEAPONS)) launcher = m;
                    break;
                default:
                    boolean inHand = seat == Presence.GUN ? m == seatedGun : m.controller == Controller.PLAYER;
                    if (!inHand) break;
                    if (gunCount < guns.length) chip(guns[gunCount++], m, d);
                    else gunsMore++;
                    break;
            }
        }
        pdPresent = pdAll > 0;
        pdCount = pdWorking >= pdAll ? "" : pdAll < 10 ? pdWorking + " OF " + pdAll : pdWorking + "/" + pdAll;
        pdState = pdEngaging ? "ENGAGING" : pdWorking == 0 && pdAll > 0 ? "DOWN" : "IDLE";
        if (launcher != null) {
            lPresent = true;
            lPos = Fmt.cut(launcher.name, 9);
            lLeft = launcher.ammo < 0 ? "" : Integer.toString(Math.min(9, launcher.ammo));
            lSize = launcher.magazine < 0 ? "" : Integer.toString(launcher.magazine);
            lReady = launcher.state == com.ringsofiron.uihud.providers.WeaponStatusProvider.State.READY && launcher.ammo != 0;
            lSecs = lReady ? "" : launcher.cooldown > 0f ? Fmt.fit(Math.min(99, Math.ceil(launcher.cooldown)), 2, 0) : "";
            int salvo = launcher.ammo < 0 ? 4 : Math.min(4, launcher.ammo);
            lSalvo = Integer.toString(salvo);
            lSalvoAll = launcher.ammo >= 0 && salvo >= launcher.ammo;
        }
        decoyReady = w.decoyCooldown <= 0f;
        decoy = decoyReady ? "" : Fmt.secs(w.decoyCooldown);
        ShieldStatus sh = d.shieldStatus;
        shRaised = sh.raised;
        shMass = sh.capacity > 0f ? Fmt.fit(Math.min(99f, 100f * sh.cloud / sh.capacity), 2, 0) : "";
    }

    /** The gun the player sits at, by its console's tile. */
    private static Mount gunAt(HudData d) {
        Seat at = null;
        for (int i = 0; i < d.stations.seats.size; i++) {
            Seat s = d.stations.seats.get(i);
            if (s.console != null && s.console == d.ctx.seatedGun) at = s;
        }
        WeaponFrame w = d.weaponFrame;
        for (int i = 0; i < w.mounts.size; i++) {
            Mount m = w.mounts.get(i);
            if (at != null && m.tileX == at.tileX && m.tileY == at.tileY) return m;
        }
        return null;
    }

    private static String relationWord(Contact c) {
        switch (c.relation) {
            case HOSTILE: return "HOSTILE " + kindWord(c);
            case FRIENDLY: return "FRIENDLY " + kindWord(c);
            default: return kindWord(c);
        }
    }

    private static String kindWord(Contact c) {
        switch (c.kind) {
            case STATION: return "STATION";
            case MISSILE: return "MISSILE";
            case DERELICT: return "DERELICT";
            default: return "SHIP";
        }
    }

    private static void chip(GunChip g, Mount m, HudData d) {
        g.pos = Fmt.cut(m.name, 10);
        g.who = "";
        for (int i = 0; i < d.stations.seats.size; i++) {
            Seat s = d.stations.seats.get(i);
            if (s.post == Post.GUN && s.tileX == m.tileX && s.tileY == m.tileY && s.hand == Hand.CREW) g.who = Fmt.cut(s.who, 5);
        }
        g.ready = m.state == com.ringsofiron.uihud.providers.WeaponStatusProvider.State.READY && (m.inArc || Float.isNaN(m.hitChance));
        g.state = gunState(m, d.targetStatus.present);
        g.hit = Float.isNaN(m.hitChance) ? "" : Fmt.fit(Math.min(99, Math.round(m.hitChance * 100f)), 2, 0);
    }

    /** A gun's state in eight cells, the words the POWER page uses (8.3). */
    static String gunState(Mount m, boolean locked) {
        switch (m.state) {
            case DESTROYED: return "WRECKED";
            case REFIT: return "REFIT";
            case SHORTED: return "SHORTED";
            case STUNNED: return Fmt.cut("STUN " + Fmt.secs(m.cooldown), 8);
            case NO_POWER: return "NO POWER";
            case EMPTY: return "EMPTY";
            case RELOADING: return Fmt.cut("RELOAD " + Fmt.secs(m.cooldown), 8);
            case CHARGING: return "CHARGING";
            default:
                return locked && !m.inArc ? "NO ARC" : "READY";
        }
    }

    // ------------------------------------------------------------------ clicks

    void boxes(FilamentActor actor, float ox, float oy) {
        if (seat != Presence.HELM && seat != Presence.WEAPONS) return;
        softKeys(actor, ox + LMFD, oy, 0);
        softKeys(actor, ox + RMFD, oy, 1);
        for (int i = 0; i < 6; i++) actor.box(CLICK_BOARD + i, ox + BAND_X + i * 150f, oy + BOARD_Y, 150f, 20f);
    }

    private static void softKeys(FilamentActor actor, float x, float oy, int side) {
        for (int i = 0; i < 3; i++) actor.box(CLICK_SOFT + side * 10 + i, x + SLOT * i, oy + RAIL_Y, SLOT, KEY_Y + KEY_H - RAIL_Y);
        float sx = x + 3f * SLOT, sw = MFD_W - 3f * SLOT;
        actor.box(CLICK_SOFT + side * 10 + PIN, sx, oy + RAIL_Y, sw / 2f, KEY_Y + KEY_H - RAIL_Y);
        actor.box(CLICK_SOFT + side * 10 + FOLD, sx + sw / 2f, oy + RAIL_Y, sw / 2f, KEY_Y + KEY_H - RAIL_Y);
    }

    // ------------------------------------------------------------------ painting

    void paint(Filament f) {
        if (seat == Presence.GUN) {
            gunPlate(f);
            return;
        }
        if (seat != Presence.HELM && seat != Presence.WEAPONS) return;
        Mfd l = mfds.left, r = mfds.right;
        rack(f, !l.folded, !r.folded);
        mfd(f, LMFD, l);
        mfd(f, RMFD, r);
        if (blackout) {
            f.dark = true;
            cluster(f);
            f.dark = false;
            noPowerFace(f, CL, BAND_Y, CLUSTER_W, BAND_Y + CLUSTER_H / 2f);
            f.dark = true;
            board(f);
            f.dark = false;
        } else {
            cluster(f);
            board(f);
        }
    }

    private void cluster(Filament f) {
        if (seat == Presence.HELM) helmCluster(f);
        else tacCluster(f);
    }

    /** The console the band is built into: a folded MFD sinks into the rack and leaves its rail. */
    private static void rack(Filament f, boolean leftOpen, boolean rightOpen) {
        float top = BAND_Y - 10f, wing = RAIL_Y - 8f, x0 = BAND_X - 10f, x1 = BAND_X + BAND_W + 10f;
        float lx = LMFD + MFD_W + 8f, rx = RMFD - 8f;
        f.rackColumns(new float[] {x0, lx, rx, x1}, new float[] {leftOpen ? top : wing, top, rightOpen ? top : wing}, 812f, 7f);
        // The board's shelf: a step in the moulding under the instruments
        f.silkRule(BAND_X - 4f, BOARD_Y - 3.5f, BAND_X + BAND_W + 4f, f.o().ink(SHELF_DARK).stroke(1.2f));
        f.silkRule(BAND_X - 4f, BOARD_Y - 2.3f, BAND_X + BAND_W + 4f, f.o().ink(SHELF_LIGHT).stroke(0.6f));
    }

    private static final com.badlogic.gdx.graphics.Color SHELF_DARK = com.badlogic.gdx.graphics.Color.valueOf("08090a");
    private static final com.badlogic.gdx.graphics.Color SHELF_LIGHT = com.badlogic.gdx.graphics.Color.valueOf("26282b");

    /** An MFD: open with its title row, page and rail, or folded to its rail; dark on NO POWER in a blackout. */
    private void mfd(Filament f, float x, Mfd m) {
        if (blackout) f.dark = true;
        if (m.folded) {
            f.module(x, RAIL_Y, MFD_W, GLASS_B - RAIL_Y, f.o().filaments(0).noGlare().radius(2f));
            keyRow(f, x, m, true);
            if (blackout) {
                f.dark = false;
                noPowerFace(f, x, RAIL_Y, MFD_W, RAIL_Y + (GLASS_B - RAIL_Y) / 2f);
            }
            return;
        }
        float y = BAND_Y, gh = GLASS_B - y;
        f.module(x, y, MFD_W, gh, f.o().filaments(4));
        String title = m.page.name();
        float tw = f.dm(x + 8f, y + 5f, title, f.o().pitch(1.5f).norm().noGhost());
        String setting = pages.setting(m.page);
        if (!setting.isEmpty()) f.legend(x + 8f + tw + 10f, y + 15f, setting, f.o().dim());
        String owner = Mfds.TAGS[m.station()];
        f.dm(x + MFD_W - 8f - f.dmWidth(owner.length(), f.o().pitch(1.5f)), y + 5f, owner, f.o().pitch(1.5f).dim().noGhost());
        f.rectLit(x + 6f, y + 20f, MFD_W - 12f, 0.7f, f.o().ghostOnly().k(2f));
        pages.paint(f, m.page, x, PAGE_Y, MFD_W, PAGE_H);
        f.rectLit(x + 6f, RAIL_Y - 1.5f, MFD_W - 12f, 0.7f, f.o().ghostOnly().k(2f));
        keyRow(f, x, m, false);
        if (blackout) {
            f.dark = false;
            noPowerFace(f, x, y, MFD_W, y + 63f);
        }
    }

    /** The soft-key row (9.1): each page's full name over a blank rubber key, then the split pin | fold key. */
    private static void keyRow(Filament f, float x, Mfd m, boolean folded) {
        Page[] items = Mfds.STATIONS[m.station()];
        for (int i = 0; i < items.length; i++) {
            String t = items[i].name();
            float cx = x + SLOT * (i + 0.5f), w = t.length() * 9f - 1.5f;
            boolean active = items[i] == m.page && !folded;
            Filament.Opt o = f.o().pitch(1.5f).level(active ? Drive.NORM : Drive.DIM).noGhost();
            if (active) o.inverse(0, t.length());
            f.dm(cx - w / 2f, RAIL_Y + 3.75f, t, o);
        }
        for (int i = 0; i < 3; i++) f.key(x + SLOT * i + 3f, KEY_Y, SLOT - 6f, KEY_H, "", f.o());
        float sx = x + 3f * SLOT, sw = MFD_W - 3f * SLOT;
        f.rectLit(sx - 0.4f, RAIL_Y + 3f, 0.8f, 12f, f.o().ghostOnly().k(1.6f));
        f.sprite(sx + sw / 4f, RAIL_Y + 9f, PIN_GLYPH, f.o().centred().pitch(1.5f).level(m.pinned ? Drive.NORM : Drive.DIM));
        f.sprite(sx + sw * 3f / 4f, RAIL_Y + 9f, FOLD_GLYPH, f.o().centred().pitch(1.5f).dim());
        f.key(sx, KEY_Y, sw, KEY_H, "", f.o());
        // The groove down the middle of the split key
        f.scrim(sx + sw / 2f - 0.7f, KEY_Y + 1.5f, 1.4f, KEY_H - 3f, 0f, 0.97f);
    }

    /** A blacked-out instrument's face (5.1): the backup LCD behind the smoked glass, lit from the emergency bus. */
    private static void noPowerFace(Filament f, float x, float y, float w, float fy) {
        float lw = 86f, lh = 17f;
        f.lcdPanel(x + w / 2f - lw / 2f, fy - lh / 2f, lw, lh, f.o().backlight());
        f.lcdText(x + w / 2f, fy + 4.5f, "NO POWER", f.o().size(12f).centre());
    }

    // ------------------------------------------------------------------ the board

    private void board(Filament f) {
        float y = BOARD_Y, h = 18f;
        for (int i = 0; i < 6; i++) {
            float x = BAND_X + i * 150f;
            f.lampKey(x + 1f, y, 60f, h, BOARD[i], null, Filament.Lamp.ZINC, you[i] && !green);
            f.module(x + 66f, y, 74f, h, f.o().filaments(0).noGlare().radius(1.5f));
            if (i == 5) {
                f.legend(x + 66f + 37f, y + 13.5f, "LATCHED", f.o().size(12f).centre().lit(latched).norm());
            } else {
                f.dm(x + 67.75f, y + 3.75f, who[i], f.o().pitch(1.5f).cells(8).level(boardAlert[i] != null ? Drive.NORM : Drive.DIM));
            }
            Kit.pip(f, x + 145f, y + 5f, Phosphor.RED, boardAlert[i] == Level.WARNING);
            Kit.pip(f, x + 145f, y + 13f, Phosphor.AMBER, boardAlert[i] == Level.CAUTION);
        }
        // The right end: the chronometer
        float rx = BAND_X + 904f;
        f.module(rx, y, 184f, h, f.o().filaments(0).noGlare().radius(1.5f));
        float cx = rx + 8f;
        cx += f.legend(cx, y + 13.5f, "DAY", f.o().size(12f).dim()) + 5f;
        cx += f.seg7(cx, y + 3f, 12f, day, f.o().cells(2).right().dim()) + 12f;
        cx += f.legend(cx, y + 13.5f, "TIME", f.o().size(12f).dim()) + 5f;
        f.seg7(cx, y + 3f, 12f, time, f.o().cells(6).dim());
    }

    // ------------------------------------------------------------------ the helm cluster (8.1)

    private void helmCluster(Filament f) {
        float gx = CL, gy = BAND_Y, L = 192f, rx = gx + L + 10f, lim = gx + 472f;
        f.module(gx, gy, CLUSTER_W, CLUSTER_H, f.o().filaments(4));
        rule(f, gx + L, gy + 8f, 0.8f, 104f);
        rule(f, rx - 2f, gy + 47f, lim - rx + 2f, 0.8f);
        rule(f, rx - 2f, gy + 80f, lim - rx + 2f, 0.8f);
        rule(f, gx + 8f, gy + 118f, 464f, 0.8f);

        // Velocity: frame, speed, control chip, rotation, assist and the thrusters
        float cx = gx + 10f;
        for (int i = 0; i < 4; i++) cx += f.legend(cx, gy + 19f, FRAMES[i], f.o().size(13f).lit(frames[i]).norm()) + 6f;
        float sw = f.seg7(gx + 12f, gy + 27f, 38f, speed, f.o().cells(4).right().norm());
        f.legend(gx + 20f + sw, gy + 45f, "KM/S", f.o().lit("KM/S".equals(speedUnit)).norm());
        f.legend(gx + 20f + sw, gy + 63f, "M/S", f.o().lit("M/S".equals(speedUnit)).norm());
        f.seg14(gx + 12f, gy + 74f, 14f, control, f.o().cells(7).norm().color(controlHeld ? Phosphor.AMBER : Phosphor.ZINC));
        float x = gx + 112f;
        x += f.legend(x, gy + 87.5f, "ROT", f.o().lit(!rot.isEmpty()).dim()) + 5f;
        f.seg7(x, gy + 76f, 12f, rot, f.o().cells(2).right().norm());
        float ax = gx + 12f;
        float aw = f.legend(ax, gy + 108f, "ASSIST", f.o().size(13f).lit(assist).norm());
        if (assist) f.frameLit(gx + 8.5f, gy + 96f, aw + 7f, 16f, 0.8f, f.o().dim().gap(0.8f));
        ax += aw + 10f;
        ax += f.legend(ax, gy + 108f, "THR", f.o().size(13f).lit(thr).norm()) + 10f;
        f.legend(ax, gy + 108f, "RCS", f.o().size(13f).lit(rcs).norm());

        // Reference: name, range, closing, closest approach
        f.legend(rx, gy + 21f, "REF", f.o().lit(ref).dim());
        f.dm(rx + 34f, gy + 10.5f, refName, f.o().pitch(1.5f).cells(12).norm());
        x = rx + 34f + 12f * 9f + 6f;
        x += f.seg7(x, gy + 7f, 16f, refRange, f.o().cells(4).right().norm()) + 5f;
        x += f.legend(x, gy + 21f, "M", f.o().lit(ref && !refRange.isEmpty() && !refKm).dim()) + 6f;
        f.legend(x, gy + 21f, "KM", f.o().lit(ref && refKm).dim());
        x = rx + 34f;
        x += f.legend(x, gy + 41f, "CLS", f.o().lit(!refCls.isEmpty()).dim()) + 4f;
        x += f.seg7(x, gy + 29f, 12f, refCls, f.o().cells(4).right().dim()) + 6f;
        x += f.legend(x, gy + 41f, "M/S", f.o().lit(!refCls.isEmpty()).dim()) + 12f;
        x += f.legend(x, gy + 41f, "CPA", f.o().lit(!refCpa.isEmpty()).dim()) + 4f;
        x += f.seg7(x, gy + 29f, 12f, refCpa, f.o().cells(3).right().dim()) + 5f;
        x += f.legend(x, gy + 41f, cpaKm ? "KM" : "M", f.o().lit(!refCpa.isEmpty()).dim()) + 6f;
        x += f.legend(x, gy + 41f, "IN", f.o().lit(!refCpaIn.isEmpty()).dim()) + 4f;
        f.seg7(x, gy + 29f, 12f, refCpaIn, f.o().cells(3).right().dim());

        // Next event: what, when, how much
        f.legend(rx, gy + 68f, "NEXT", f.o().lit(next).dim());
        x = rx + 42f;
        x += f.seg14(x, gy + 55f, 14f, nextWhat, f.o().cells(6).norm()) + 8f;
        x += f.seg7(x, gy + 54f, 16f, nextWhen, f.o().cells(4).right().norm()) + 9f;
        x += f.seg7(x, gy + 58f, 12f, nextDv, f.o().cells(3).right().dim()) + 6f;
        x += f.legend(x, gy + 70f, "M/S", f.o().lit(!nextDv.isEmpty()).dim()) + 6f;
        f.legend(x, gy + 70f, nextDir.isEmpty() ? "PRO" : nextDir, f.o().lit(!nextDir.isEmpty()).dim());

        // Trigger: the guns and how they fire, missiles, the decoy, shields lowered
        x = rx;
        x += f.legend(x, gy + 96.5f, "GUNS", f.o().lit(trig).dim()) + 4f;
        x += f.dm(x, gy + 86f, trig ? trigGuns : "", f.o().pitch(1.5f).cells(3).norm()) + 10f;
        f.seg14(x, gy + 85f, 12f, trig ? trigMode : "", f.o().cells(7).dim());
        x = rx;
        x += f.legend(x, gy + 111.5f, "MSL", f.o().lit(trig).dim()) + 4f;
        x += f.dm(x, gy + 101f, trig ? trigMsl : "", f.o().pitch(1.5f).cells(5).norm()) + 10f;
        x += f.legend(x, gy + 111.5f, "DCY", f.o().lit(trig).dim()) + 4f;
        x += f.dm(x, gy + 101f, trig ? trigDcy : "", f.o().pitch(1.5f).cells(3).norm()) + 10f;
        if (shields) f.legend(x, gy + 111.5f, "SHLD DN", f.o().lit(trig && shieldsDown).amber().norm());

        // Delta-v: left, the trip home's notch, the plan's bracket
        f.legend(gx + 10f, gy + 144f, "ΔV", f.o().dim());
        x = gx + 34f;
        x += f.seg7(x, gy + 126f, 18f, dvLeft, f.o().cells(4).right().norm()) + 7f;
        x += f.legend(x, gy + 144f, "M/S", f.o().dim()) + 10f;
        float bx = x, bw = gx + 384f - bx, by = gy + 131f;
        f.bar(bx, by, bw, 9f, 50, dvFrac, f.o().dim().gap(1.1f));
        if (!Float.isNaN(dvHomeFrac)) {
            float hx = bx + bw * dvHomeFrac;
            f.rectLit(hx - 0.7f, by + 11f, 1.4f, 6f, f.o().norm());
            f.dot(hx - 2.2f, by + 13f, f.o().size(1.1f).norm());
            f.dot(hx + 2.2f, by + 13f, f.o().size(1.1f).norm());
        }
        if (dvPlanFrac > 0f) {
            float p0 = bx + bw * (dvFrac - dvPlanFrac), p1 = bx + bw * dvFrac;
            f.rectLit(p0, by - 4f, p1 - p0, 0.9f, f.o().dim());
            f.rectLit(p0, by - 4f, 0.9f, 3f, f.o().dim());
            f.rectLit(p1 - 0.9f, by - 4f, 0.9f, 3f, f.o().dim());
        }
        x = gx + 394f;
        float hw = f.legend(x, gy + 133f, "HOME", f.o().lit(!dvHome.isEmpty()).dim()) + 5f;
        f.seg7(x + hw, gy + 122f, 12f, dvHome, f.o().cells(4).right().dim());
        f.legend(x, gy + 151f, "PLAN", f.o().lit(!dvPlan.isEmpty()).dim());
        f.seg7(x + hw, gy + 140f, 12f, dvPlan, f.o().cells(4).right().dim());
    }

    private static void rule(Filament f, float x, float y, float w, float h) {
        f.rectLit(x, y, w, h, f.o().ghostOnly().k(2.2f));
    }

    // ------------------------------------------------------------------ the tactical cluster (8.3)

    private static void chipFrame(Filament f, float x, float y, float w, float h, boolean lit) {
        f.frameLit(x, y, w, h, 0.7f, f.o().ghostOnly().k(2f));
        if (lit) f.frameLit(x, y, w, h, 0.8f, f.o().dim());
    }

    private void tacCluster(Filament f) {
        float gx = CL, gy = BAND_Y, lim = gx + 472f;
        f.module(gx, gy, CLUSTER_W, CLUSTER_H, f.o().filaments(4));
        rule(f, gx + 8f, gy + 49f, 464f, 0.8f);
        rule(f, gx + 8f, gy + 99f, 464f, 0.8f);
        // Target: name, class, range, closing, its state where known
        f.legend(gx + 10f, gy + 21f, "TGT", f.o().lit(tgtPresent).dim());
        f.dm(gx + 44f, gy + 8f, tgtName, f.o().pitch(2f).cells(TGT_CELLS).norm());
        f.dm(gx + 44f, gy + 29f, tgtClass, f.o().pitch(1.5f).cells(15).dim());
        float x = gx + 192f;
        x += f.seg7(x, gy + 8f, 26f, tgtRange, f.o().cells(3).right().norm()) + 4f;
        f.legend(x, gy + 20f, "KM", f.o().lit(tgtPresent && tgtKm).dim());
        f.legend(x, gy + 34f, "M", f.o().lit(tgtPresent && !tgtKm && !tgtRange.isEmpty()).dim());
        x += f.legendWidth("KM", f.o()) + 16f;
        x += f.legend(x, gy + 20f, "CLS", f.o().lit(!tgtCls.isEmpty()).dim()) + 5f;
        x += f.seg7(x, gy + 8f, 12f, tgtCls, f.o().cells(3).right().norm()) + 6f;
        f.legend(x, gy + 20f, "M/S", f.o().lit(!tgtCls.isEmpty()).dim());
        float sx = gx + 286f;
        sx += f.legend(sx, gy + 41f, "STRUCK", f.o().size(13f).lit("STRUCK".equals(tgtState)).norm()) + 8f;
        sx += f.legend(sx, gy + 41f, "DISABLED", f.o().size(13f).lit("DISABLED".equals(tgtState)).norm()) + 8f;
        f.legend(sx, gy + 41f, "VENTING", f.o().size(13f).lit("VENTING".equals(tgtState)).norm());

        // Guns in hand and point defense
        float cy = gy + 54f, ch = 40f;
        int chips = gunCount + (gunsMore > 0 ? 1 : 0) + 1;
        float cw = chips <= 3 ? 150f : (464f - 7f * (chips - 1)) / chips;
        int shown = chips <= 3 ? gunCount : Math.min(gunCount, chips - 1 - (gunsMore > 0 ? 1 : 0));
        for (int i = 0; i < shown; i++) {
            GunChip g = guns[i];
            float c0 = gx + 8f + i * (cw + 7f);
            chipFrame(f, c0, cy, cw, ch, true);
            f.legend(c0 + 6f, cy + 15f, g.pos, f.o().norm());
            if (cw >= 150f) f.dm(c0 + cw - 6f - 43.5f, cy + 5f, g.who, f.o().pitch(1.5f).cells(5).right().dim());
            f.dm(c0 + 6f, cy + 24f, g.state, f.o().pitch(1.5f).cells(8).level(g.ready ? Drive.DIM : Drive.NORM));
            float hx = c0 + cw - 6f - f.seg7Width(14f, 2, f.o()) - 2f - f.legendWidth("%", f.o());
            f.legend(hx - 4f, cy + 35f, "HIT", f.o().dim().right().lit(!g.hit.isEmpty()));
            hx += f.seg7(hx, cy + 22f, 14f, g.hit, f.o().cells(2).right().norm()) + 2f;
            f.legend(hx, cy + 35f, "%", f.o().dim().lit(!g.hit.isEmpty()));
        }
        int slot = shown;
        if (gunsMore > 0 || shown < gunCount) {
            float c0 = gx + 8f + slot * (cw + 7f);
            chipFrame(f, c0, cy, cw, ch, false);
            f.dm(c0 + 6f, cy + 15f, "+" + (gunCount - shown + gunsMore), f.o().pitch(1.5f).norm());
            f.dm(c0 + 6f, cy + 26f, "WEAPONS", f.o().pitch(1.5f).dim());
            slot++;
        }
        float px = chips <= 3 ? gx + 8f + 2f * (150f + 7f) : gx + 8f + slot * (cw + 7f), pw = chips <= 3 ? 150f : cw;
        chipFrame(f, px, cy, pw, ch, false);
        if (pdPresent) {
            f.legend(px + 6f, cy + 15f, "PD", f.o().norm());
            f.legend(px + 6f + f.legendWidth("PD", f.o()) + 6f, cy + 15f, "AUTO", f.o().dim());
            f.dm(px + pw - 6f - 54f, cy + 5f, pdCount, f.o().pitch(1.5f).cells(6).right().dim());
            f.dm(px + 6f, cy + 24f, pdState, f.o().pitch(1.5f).cells(8).level("IDLE".equals(pdState) ? Drive.DIM : Drive.NORM));
        }

        // Launcher, decoy, shields
        float ly = gy + 104f, lw = 228f;
        chipFrame(f, gx + 8f, ly, lw, 48f, false);
        if (lPresent) {
            x = gx + 14f;
            x += f.legend(x, ly + 15f, lPos, f.o().norm()) + 10f;
            x += f.legend(x, ly + 15f, "MSL", f.o().dim()) + 5f;
            x += f.seg7(x, ly + 3f, 14f, lLeft, f.o().cells(1).norm()) + 5f;
            if (!lSize.isEmpty()) f.legend(x, ly + 15f, "OF " + lSize, f.o().dim());
            x = gx + 14f;
            x += f.legend(x, ly + 39f, "READY", f.o().lit(lReady).norm()) + 4f;
            x += f.seg7(x, ly + 27f, 12f, lSecs, f.o().cells(2).right().norm()) + 14f;
            // A salvo that empties the magazine is behind an amber filter: one press throws all of them
            x += f.legend(x, ly + 39f, "SALVO", f.o().amber().norm().lit(lSalvoAll)) + 5f;
            f.seg7(x, ly + 27f, 12f, lSalvo, f.o().cells(1).norm().color(lSalvoAll ? Phosphor.AMBER : Phosphor.ZINC));
        }
        float dx = gx + 8f + lw + 7f, dw = 96f;
        chipFrame(f, dx, ly, dw, 48f, false);
        f.legend(dx + 6f, ly + 15f, "DECOY", f.o().dim());
        f.legend(dx + 6f, ly + 39f, "READY", f.o().lit(decoyReady).norm());
        f.seg7(dx + 10f + f.legendWidth("READY", f.o()), ly + 27f, 12f, decoy, f.o().cells(1).norm());
        float shx = dx + dw + 7f, shw = lim - shx;
        chipFrame(f, shx, ly, shw, 48f, false);
        // A hull with no shield emitter has blank glass here, not a ghosted SHLD (guide 7.8)
        if (!shields) return;
        f.legend(shx + 6f, ly + 15f, "SHLD", f.o().dim());
        f.legend(shx + 6f, ly + 39f, "RAISED", f.o().lit(shRaised).norm());
        f.seg7(shx + shw - 6f - f.seg7Width(20f, 2, f.o()) - 12f, ly + 14f, 20f, shMass, f.o().cells(2).right().norm());
        f.legend(shx + shw - 16f, ly + 34f, "%", f.o().dim());
    }

    // ------------------------------------------------------------------ the gunner's sight (8.4)

    /** At a gun: that gun's own plate, its state and its chance on the lock; no MFDs, no board. */
    private void gunPlate(Filament f) {
        float w = 360f, h = 56f, x = (Geo.W - w) / 2f, y = Geo.H - 16f - h;
        f.plate(x, y, w, h, 6f, 7f);
        if (blackout) f.dark = true;
        f.module(x, y, w, h, f.o().filaments(2));
        GunChip g = gunCount > 0 ? guns[0] : null;
        f.legend(x + 10f, y + 17f, g == null ? "GUN" : g.pos, f.o().norm());
        f.dm(x + 120f, y + 7f, g == null ? "" : g.state, f.o().pitch(1.5f).cells(8).level(g != null && g.ready ? Drive.DIM : Drive.NORM));
        float hx = x + w - 10f - f.seg7Width(16f, 2, f.o()) - 2f - f.legendWidth("%", f.o());
        f.legend(hx - 4f, y + 21f, "HIT", f.o().dim().right().lit(g != null && !g.hit.isEmpty()));
        hx += f.seg7(hx, y + 6f, 16f, g == null ? "" : g.hit, f.o().cells(2).right().norm()) + 2f;
        f.legend(hx, y + 21f, "%", f.o().dim().lit(g != null && !g.hit.isEmpty()));
        f.rectLit(x + 8f, y + 28f, w - 16f, 0.7f, f.o().ghostOnly().k(2f));
        f.legend(x + 10f, y + 47f, "TGT", f.o().lit(tgtPresent).dim());
        f.dm(x + 44f, y + 36f, tgtName, f.o().pitch(1.5f).cells(TGT_CELLS).norm());
        float rx = x + 140f;
        rx += f.seg7(rx, y + 34f, 14f, tgtRange, f.o().cells(3).right().norm()) + 4f;
        rx += f.legend(rx, y + 47f, tgtKm ? "KM" : "M", f.o().lit(tgtPresent && !tgtRange.isEmpty()).dim()) + 14f;
        rx += f.legend(rx, y + 47f, "CLS", f.o().lit(!tgtCls.isEmpty()).dim()) + 5f;
        rx += f.seg7(rx, y + 36f, 12f, tgtCls, f.o().cells(3).right().norm()) + 5f;
        f.legend(rx, y + 47f, "M/S", f.o().lit(!tgtCls.isEmpty()).dim());
        if (blackout) {
            f.dark = false;
            noPowerFace(f, x, y, w, y + h / 2f);
        }
    }
}
