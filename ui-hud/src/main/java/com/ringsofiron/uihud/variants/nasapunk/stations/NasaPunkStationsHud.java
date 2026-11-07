package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.ShellLook;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware.BoxState;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import com.ringsofiron.uihud.variants.nasapunk.NpScreens;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import com.ringsofiron.uihud.variants.nasapunk.stations.StationsLayout.Zone;
import com.ringsofiron.uihud.widgets.MapPainter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Nasa-punk art on the ship stations functional spec (documentation/ui-overhaul/functional-stations.md;
 * drawn in documentation/ui-overhaul/variations/nasapunk-stations). The ship is the interface: on
 * deck the captain sees the always-on overlay (the status strip, the annunciator with its master
 * lamps, the warp strip, the callout ticker) and the suit readout on their chest. Seated at a command
 * console the band comes up on the bottom edge: the seat's cluster between two MFDs, and the station
 * board under them. At a gun, the gunner sight and the gun's own tile.
 *
 * <p>The ship's condition ({@link ShipCondition}) sets the MFDs' pages and the board's lights; the
 * annunciator's tiles ({@link Annunciation}) light the board's stations and turn an MFD to their home
 * pages ({@link MfdState}).
 *
 * <p>Keys: Alt with a station's number puts it on the right MFD (again cycles its pages), with
 * Shift on the left; Alt+0 pins and Alt+minus folds the right, with Shift the left. Backslash, or
 * a click on a master lamp, acknowledges. A click on a tile shows its home page, on a board tile
 * its station, on a soft key its page.
 */
public class NasaPunkStationsHud implements HudVariant {
    /** Where the captain is: at a command seat (with MFDs), at a gun, or on deck (aboard another craft and EVA too). */
    enum Seat { NONE, HELM, WEAPONS, GUN, DECK }

    /** The master lamps flash at this period, s: 1 Hz, within the spec's 2 Hz or slower. */
    private static final float FLASH = 1f;
    /** The berth card shows this long after the ports latch, s. */
    private static final float BERTH_FOR = 8f;
    /** The helm's Reference shows Ap and Pe this long after the engine or RCS last fired, s. */
    private static final float ORBIT_HOLD = 5f;
    /** The prompt line's hints show this long after they change, s. */
    private static final float HINTS_FOR = 10f;
    /** A hostile this close is inside weapons range (the coilgun's reach), and this close is near, m. */
    private static final float WEAPONS_RANGE = 5_000f, NEAR = 50_000f;
    /** A free port this close to the nav target's station starts a docking approach (PlayerDocking.READOUT_RANGE), m. */
    private static final float APPROACH_RANGE = 5_000f;
    /** Missiles one press throws at most (PlayerGunnery.SALVO). */
    private static final int SALVO = 4;

    /** The menus as pages of CDU-1 and the map's system plot, the same under all three Nasa-punk HUDs. */
    private final NpScreens screens = new NpScreens();
    private HudVariantHost host;
    private NpKit kit;
    private Group root;
    private StatusStrip strip;
    private Annunciator annunciator;
    private WarpStrip warpStrip;
    private Ticker ticker;
    private BerthCard berth;
    private SuitReadout suit;
    private PromptLine prompt;
    private Mfd mfdLeft, mfdRight;
    private HelmCluster helm;
    private TacticalCluster tac;
    private GunnerSight gunner;
    private StationBoard board;
    private final Pages pages = new Pages();

    private final ShipCondition cond = new ShipCondition();
    private final ShipCondition.Input condIn = new ShipCondition.Input();
    private final Annunciation ann = new Annunciation();
    private final MfdState mfds = new MfdState();
    private Seat seat = Seat.NONE;
    private Object lastShip;
    private MfdState.Seat lastMfdSeat = MfdState.Seat.NONE;
    private float clock, berthLeft, sinceThrust = Float.MAX_VALUE, hintsAge;
    private boolean wasDocked;
    private String hintsSig = "";

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        kit = new NpKit();
        kit.setScale(host.uiScale());
        root = new Group();
        root.setTransform(false);
        root.setTouchable(Touchable.childrenOnly);
        host.layer().addActor(root);
        // back to front: the band, then the overlay over it
        board = add(new StationBoard(kit));
        mfdLeft = add(new Mfd(kit, true));
        mfdRight = add(new Mfd(kit, false));
        helm = add(new HelmCluster(kit));
        tac = add(new TacticalCluster(kit));
        gunner = add(new GunnerSight(kit));
        suit = add(new SuitReadout(kit));
        prompt = add(new PromptLine(kit));
        berth = add(new BerthCard(kit));
        ticker = add(new Ticker(kit));
        warpStrip = add(new WarpStrip(kit));
        annunciator = add(new Annunciator(kit));
        strip = add(new StatusStrip(kit));
        pages.key = this::key;
        Mfd.Keys keys = new Mfd.Keys() {
            @Override
            public void page(Mfd m, Page p) {
                mfds.choose(m.left, p);
            }

            @Override
            public void pin(Mfd m) {
                mfds.pin(m.left);
            }

            @Override
            public void fold(Mfd m) {
                mfds.fold(m.left);
            }

            @Override
            public void range(Mfd m, int step) {
                // − steps out to a longer range, + in
                m.rangeSet = true;
                m.rangeStep = Math.max(0, Math.min(Pages.STEPS.length - 1, m.rangeStep - step));
            }
        };
        for (Mfd m : new Mfd[] {mfdLeft, mfdRight}) {
            m.pages = pages;
            m.onKeys = keys;
        }
        annunciator.onAcknowledge = this::acknowledge;
        annunciator.onTile = t -> {
            if (mfds.seat != MfdState.Seat.NONE) mfds.choose(mfds.right.pinned && !mfds.left.pinned, t.home);
        };
        board.onStation = s -> {
            if (mfds.seat != MfdState.Seat.NONE) mfds.station(false, s);
        };
        for (Actor a : root.getChildren()) a.setVisible(false);
    }

    private <T extends Actor> T add(T a) {
        root.addActor(a);
        return a;
    }

    // ---- the frame ------------------------------------------------------------------------------

    @Override
    public void update(float delta) {
        HudData d = host.data();
        UiContext ctx = d.ctx;
        float dt = Math.max(0f, Math.min(delta, 0.25f));
        clock = (clock + dt) % 3600f;
        seat = seatOf(ctx);
        boolean ship = ctx.ship != null && !host.hud().isMapOpen();
        if (ctx.ship != lastShip) {
            lastShip = ctx.ship;
            cond.reset();
        }
        condition(d, dt);
        ann.build(d.alerts, cond.blackout, ctx.simSeconds);
        boolean warp = ctx.warp > 1 || ctx.railsWarp;
        boolean emitter = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        MfdState.Seat ms = seat == Seat.HELM ? MfdState.Seat.HELM : seat == Seat.WEAPONS ? MfdState.Seat.WEAPONS : MfdState.Seat.NONE;
        if (ms != lastMfdSeat) {
            // a new seat: SENSORS opens on its own range again
            mfdLeft.rangeSet = mfdRight.rangeSet = false;
            lastMfdSeat = ms;
        }
        // docked, the MFDs keep DOCK whatever the condition: Red while docked brings back only the
        // threat symbology (section 12)
        MfdState.Mode mode = cond.docked ? MfdState.Mode.DOCKED
                : cond.level == ShipCondition.Level.RED ? MfdState.Mode.RED
                : cond.level == ShipCondition.Level.YELLOW ? MfdState.Mode.YELLOW : MfdState.Mode.GREEN;
        mfds.update(ms, mode, emitter, warp, ann.all, dt);
        pages.d = d;
        pages.ann = ann;

        strip(d, ship);
        annunciator(ship);
        warpStrip(d, ship && warp);
        ticker(d, ship);
        berth(d, ship, dt);
        suit(d, ship && seat == Seat.DECK);

        boolean band = ship && ms != MfdState.Seat.NONE;
        mfd(mfdLeft, mfds.left, d, band);
        mfd(mfdRight, mfds.right, d, band);
        // in a blackout the seat goes dark: its cluster and MFDs on their NO POWER face, its board
        // unlit; the overlay stays up (section 5.1, rule 4). A console without power darkens its own.
        helm.setVisible(ship && seat == Seat.HELM);
        helm.noPower = cond.blackout || !consoleWorks(d, Station.HELM);
        if (helm.isVisible()) helm(d, warp, emitter, dt);
        else sinceThrust = Float.MAX_VALUE;
        tac.setVisible(ship && seat == Seat.WEAPONS);
        tac.noPower = cond.blackout || !consoleWorks(d, Station.TAC);
        if (tac.isVisible()) tactical(d, emitter);
        gunner.setVisible(ship && seat == Seat.GUN);
        gunner.noPower = cond.blackout;
        if (gunner.isVisible()) gunner(d);
        board.setVisible(ship && (band || seat == Seat.GUN));
        board.dark = cond.blackout;
        if (board.isVisible()) board(d);
        prompt(d, ship, dt);
        layout();
    }

    private static Seat seatOf(UiContext ctx) {
        switch (ctx.presence) {
            case HELM: return Seat.HELM;
            case WEAPONS: return Seat.WEAPONS;
            case GUN: return Seat.GUN;
            case DECK:
            case ABOARD_OTHER:
            case EVA: return Seat.DECK;
            default: return Seat.NONE;
        }
    }

    // ---- the condition (section 12) -------------------------------------------------------------

    private void condition(HudData d, float dt) {
        UiContext ctx = d.ctx;
        TargetStatus t = d.targetStatus;
        boolean fired = t.present && t.firingOnUs, inRange = false, near = false;
        ContactProvider.ContactFrame cf = d.contactFrame;
        for (int i = 0; i < cf.contacts.size; i++) {
            Contact c = cf.contacts.get(i);
            if (c.kind != ContactProvider.Kind.SHIP || c.relation != Relation.HOSTILE || Float.isNaN(c.range)) continue;
            boolean fights = c.state == ContactProvider.State.FIT;
            if (fights && c.threat) fired = true;
            if (fights && c.range <= WEAPONS_RANGE) inRange = true;
            if ((fights || c.state == ContactProvider.State.RUNNING) && c.range <= NEAR) near = true;
        }
        condIn.firedUpon = fired;
        condIn.missilesInbound = ctx.missilesInbound > 0 || d.threatFrame.incoming() > 0;
        condIn.hostileInRange = inRange;
        condIn.hostileNear = near;
        condIn.battleStations = d.orderState.battleStations;
        condIn.docked = ctx.docked;
        condIn.latched = ctx.latched;
        condIn.disabled = !ctx.disabledReason.isEmpty();
        condIn.abandoning = d.orderState.abandoning;
        condIn.ratedWatts = d.powerFrame.ratedWatts;
        condIn.outputWatts = d.powerFrame.outputWatts;
        cond.update(condIn, dt);
    }

    // ---- the overlay (section 7) ----------------------------------------------------------------

    private void strip(HudData d, boolean ship) {
        UiContext ctx = d.ctx;
        strip.condition = cond.level == ShipCondition.Level.RED ? "RED" : cond.level == ShipCondition.Level.YELLOW ? "YELLOW" : null;
        strip.battle = cond.level == ShipCondition.Level.RED && d.orderState.battleStations;
        strip.state = ctx.docked ? "DOCKED" : ctx.latched ? "LATCHED" : "";
        strip.emergency = emergency(d);
        strip.setVisible(ship && !strip.dark());
    }

    /**
     * The emergency's words with its keys (section 7.7): "DISABLED [P][P] ABANDON [B][B] TOW", the
     * tow only while the beacon would answer; once abandon is ordered, "ABANDON SHIP [P] LAUNCH NOW".
     * A first press arms its order, and the strip says what the second does.
     */
    private String emergency(HudData d) {
        Orders o = d.orderState;
        String p = "[" + key(Action.ABANDON) + "]", b = "[" + key(Action.CUT) + "]";
        if (o.abandoning) {
            String pods = Float.isNaN(o.podsLaunchIn) ? "" : "  PODS " + NpFormat.tMinus(o.podsLaunchIn);
            return "ABANDON SHIP " + p + " LAUNCH NOW" + pods;
        }
        if (d.ctx.disabledReason.isEmpty()) return "";
        if (o.abandonArmed > 0f) return "DISABLED " + p + " AGAIN: ABANDON SHIP";
        if (o.towArmed > 0f) return "DISABLED " + b + " AGAIN: CALL A TOW";
        StringBuilder s = new StringBuilder("DISABLED");
        if (o.abandonRefusal.isEmpty()) s.append(' ').append(p).append(p).append(" ABANDON");
        if (o.towRefusal.isEmpty()) s.append(' ').append(b).append(b).append(" TOW");
        return s.toString();
    }

    private void annunciator(boolean ship) {
        annunciator.tiles.clear();
        annunciator.tiles.addAll(ann.row);
        boolean phase = clock % FLASH < 0.6f * FLASH;
        annunciator.warnLit = ann.warnUnacked && phase;
        annunciator.cautLit = ann.cautUnacked && phase;
        annunciator.setVisible(ship && (!ann.row.isEmpty() || ann.warnUnacked || ann.cautUnacked));
    }

    /** Acknowledge: the master lamps stop flashing until something new or worse (sections 7.1 and 13.3). */
    private void acknowledge() {
        ann.acknowledge(host.data().alerts);
    }

    private void warpStrip(HudData d, boolean show) {
        warpStrip.setVisible(show);
        if (!show) return;
        UiContext ctx = d.ctx;
        warpStrip.level = "WARP " + NpFormat.thousands(ctx.warp) + "×" + (ctx.railsWarp ? "  RAILS" : "");
        warpStrip.held = !ctx.warpHold.isEmpty();
        if (warpStrip.held) {
            warpStrip.rest = "HELD: " + up(ctx.warpHold);
        } else if (!Float.isNaN(ctx.warpNextStop)) {
            NavReadout n = d.navReadout;
            boolean forBurn = n.burnsLeft > 0 && !Float.isNaN(n.burnStartIn) && Math.abs(n.burnStartIn - ctx.warpNextStop) < 5f;
            warpStrip.rest = "DROPS OUT IN " + NpFormat.clock(ctx.warpNextStop) + (forBurn ? " FOR BURN " + n.burnNumber : "");
        } else {
            warpStrip.rest = "";
        }
    }

    /** The ticker: the game's notices still showing, newest at the bottom, each tagged by its station. */
    private void ticker(HudData d, boolean ship) {
        ticker.lines.clear();
        Array<MessageFeed.Message> all = d.messages.all();
        for (int i = 0; i < all.size && ticker.lines.size() < 3; i++) {
            MessageFeed.Message m = all.get(i);
            if (m.secondsLeft <= 0f) continue;
            Ticker.Line l = new Ticker.Line();
            String text = m.text;
            l.tag = "SHIP";
            int colon = text.indexOf(':');
            if (colon > 0 && colon < 14) {
                String tag = tagOf(text.substring(0, colon).trim());
                if (tag != null) {
                    l.tag = tag;
                    text = text.substring(colon + 1).trim();
                }
            }
            l.text = text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
            l.color = m.level == Level.WARNING ? NpColors.RED : m.level == Level.CAUTION ? NpColors.AMBER : NpColors.WHITE;
            l.advisory = m.level == Level.ADVISORY && !m.important;
            l.count = m.count > 1 ? "×" + m.count : null;
            ticker.lines.add(0, l);
        }
        ticker.setVisible(ship && !ticker.lines.isEmpty());
    }

    /** The station a notice's "Source:" belongs to (section 7.2), or null when it names none. */
    static String tagOf(String source) {
        switch (source.toLowerCase(java.util.Locale.ROOT)) {
            case "helm": return "HELM";
            case "navigation":
            case "nav": return "NAV";
            case "gunnery":
            case "shields":
            case "tactical": return "TAC";
            case "engineering": return "ENG";
            case "medical":
            case "crew":
            case "boarding":
            case "sponsor": return "CMD";
            case "station":
            case "comms": return "COMMS";
            case "log": return "SHIP";
            default: return null;
        }
    }

    /** The berth card, for a few seconds after the ports latch at a station. */
    private void berth(HudData d, boolean ship, float dt) {
        UiContext ctx = d.ctx;
        if (ctx.docked && !wasDocked) berthLeft = BERTH_FOR;
        else berthLeft = Math.max(0f, berthLeft - dt);
        wasDocked = ctx.docked;
        berth.setVisible(ship && ctx.docked && berthLeft > 0f);
        if (!berth.isVisible()) return;
        StationInfo s = d.navReadout.docked;
        String name = up(s.present && !s.name.isEmpty() ? s.name : ctx.stationName);
        berth.title = name.isEmpty() ? "DOCKED" : "DOCKED AT " + name;
        berth.keys.clear();
        if (s.market) berth.keys.add(new String[] {key(Action.TRADE), "MARKET"});
        if (s.yard) berth.keys.add(new String[] {key(Action.YARD), "YARD"});
        if (s.contracts) berth.keys.add(new String[] {key(Action.MISSIONS), "MISSIONS"});
    }

    /** The suit readout on deck (section 7.4): only what matters now, else hidden. */
    private void suit(HudData d, boolean deck) {
        suit.reset();
        CaptainStatus c = d.captainStatus;
        if (deck && c.present) {
            String air = airWord(c);
            boolean outside = air.isEmpty(), bad = !outside && !air.equals("BREATHABLE");
            if (c.suited && (bad || outside) && !Float.isNaN(c.suitAir)) {
                suit.row("SUIT AIR", NpFormat.clock(c.suitAir), c.suitAir < 60f ? NpColors.RED : c.suitAir < 240f ? NpColors.AMBER : NpColors.WHITE);
            } else if (!c.suited && bad) {
                suit.row("SUIT", "NO SUIT", NpColors.RED);
            }
            if (bad) {
                String o2 = Float.isNaN(c.roomOxygen) ? "--" : Math.round(c.roomOxygen * 21f) + "%";
                suit.row("AIR", Math.round(c.roomPressure * 101.3f) + " KPA  O2 " + o2, air.equals("VACUUM") ? NpColors.RED : NpColors.AMBER);
            }
            if (!c.room.isEmpty() && d.alerts.get("fire:" + c.room) != null) suit.row("ROOM", "FIRE", NpColors.AMBER);
            if (!Float.isNaN(c.bleedOut)) suit.row("YOU", "BLEEDING " + NpFormat.clock(c.bleedOut), NpColors.RED);
            else if (!c.wound.isEmpty()) suit.row("YOU", StDraw.fitHead(kit.canvas(), up(c.wound), 110f, 13f, true, 0f), NpColors.AMBER);
        }
        suit.setVisible(!suit.colors.isEmpty());
        suit.setSize(SuitReadout.W, suit.heightFor());
    }

    /** The air where the captain stands in a word: VACUUM, SMOKE, THIN or BREATHABLE; "" outside a room. */
    static String airWord(CaptainStatus c) {
        if (Float.isNaN(c.roomPressure)) return "";
        if (c.roomPressure < 0.1f) return "VACUUM";
        if (!Float.isNaN(c.roomSmoke) && c.roomSmoke > 0.05f) return "SMOKE";
        if (c.roomPressure < 0.7f || !Float.isNaN(c.roomOxygen) && c.roomOxygen < 0.7f) return "THIN";
        return "BREATHABLE";
    }

    /** The prompt line's hints: the three keys worth knowing here, for a while after they change. */
    private void prompt(HudData d, boolean ship, float dt) {
        prompt.hints.clear();
        boolean disabled = !d.ctx.disabledReason.isEmpty();
        Array<KeyHint> hs = d.keyHints.hints;
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < hs.size && prompt.hints.size() < PromptLine.MOST; i++) {
            KeyHint h = hs.get(i);
            if (h.key.isEmpty() || h.label.isEmpty() || h.action.equals(Action.HELP.name())) continue;
            // disabled, the status strip has the abandon and tow keys
            if (disabled && (h.action.equals(Action.ABANDON.name()) || h.action.equals(Action.CUT.name()))) continue;
            prompt.hints.add(new String[] {up(h.key), up(h.label)});
            sig.append(h.key).append(h.label).append('|');
        }
        String s = sig.toString() + seat;
        if (!s.equals(hintsSig)) {
            hintsSig = s;
            hintsAge = 0f;
        } else {
            hintsAge += dt;
        }
        prompt.setVisible(ship && seat != Seat.NONE && !prompt.hints.isEmpty() && hintsAge < HINTS_FOR && !d.keyHints.placing);
    }

    // ---- the band (section 8) -------------------------------------------------------------------

    private void mfd(Mfd m, MfdState.Side s, HudData d, boolean band) {
        m.setVisible(band);
        if (!band) return;
        m.show(s.page, mfds.folded(s), s.pinned);
        m.noPower = cond.blackout || !consoleWorks(d, s.page.station);
    }

    /** Whether the console hosting {@code st} works; a hull without one counts as working. */
    private static boolean consoleWorks(HudData d, Station st) {
        StationBoardProvider.StationBoard b = d.stations;
        switch (st) {
            case HELM:
            case NAV:
            case COMMS: {
                StationBoardProvider.Seat s = b.at(Post.HELM);
                return s == null || s.working;
            }
            case TAC: {
                StationBoardProvider.Seat s = b.at(Post.WEAPONS);
                return s == null || s.working;
            }
            case ENG: {
                StationBoardProvider.Seat r = b.at(Post.REACTOR), l = b.at(Post.LIFE_SUPPORT);
                return r == null && l == null || r != null && r.working || l != null && l.working;
            }
            default: return true;
        }
    }

    /** The docking approach (section 8.2): a port armed, or a free port near a station that is the nav target. */
    private static boolean approach(HudData d) {
        DockingApproach k = d.docking;
        if (!k.present || d.ctx.docked || d.ctx.latched) return false;
        if (k.armed) return true;
        NavReadout n = d.navReadout;
        boolean navIsPort = n.present && !k.other.isEmpty() && n.name.equalsIgnoreCase(k.other);
        return navIsPort && !Float.isNaN(k.range) && k.range <= APPROACH_RANGE;
    }

    private void helm(HudData d, boolean warp, boolean emitter, float dt) {
        FlightStatus f = d.flightStatus;
        NavReadout n = d.navReadout;
        Orders o = d.orderState;
        DockingApproach k = d.docking;
        TargetStatus t = d.targetStatus;
        UiContext ctx = d.ctx;
        HelmCluster h = helm;
        boolean appr = approach(d);
        h.warp = warp;

        // Velocity: the frame and the speed in it, who has the stick, assist, the thrusters
        boolean firing = f.mainEngine || f.rcs || Math.abs(f.forward) > 0.05f || Math.abs(f.strafe) > 0.05f;
        sinceThrust = firing ? 0f : Math.min(Float.MAX_VALUE, sinceThrust + dt);
        if (ctx.docked || ctx.latched) {
            h.frame = "DOCKED";
            h.speed = "";
            h.speedUnit = "";
        } else {
            float v = f.speed;
            if (appr && !Float.isNaN(k.closing)) {
                h.frame = "PORT";
                v = k.closing;
            } else {
                switch (f.frame) {
                    case TARGET:
                        // the target frame reads its closing speed, as the port frame does (section 8.1)
                        h.frame = "TARGET";
                        if (t.present && !Float.isNaN(t.closing)) v = t.closing;
                        break;
                    case NAV: h.frame = "NAV"; break;
                    case STATION: h.frame = "STN"; break;
                    case ORBIT: h.frame = "ORBIT"; break;
                    default: h.frame = "--"; break;
                }
            }
            if (Float.isNaN(v)) {
                h.speed = "--";
                h.speedUnit = "";
            } else {
                String[] s = NpFormat.speed(v);
                h.speed = (v < -0.05f ? NpFormat.MINUS : "") + s[0];
                h.speedUnit = s[1] + (h.frame.equals("PORT") || h.frame.equals("TARGET") ? (v < -0.05f ? " OPENING" : " CLOSING") : "");
            }
        }
        h.spin = !Float.isNaN(f.spin) && Math.abs(f.spin) > 0.5f ? NpFormat.tenths(Math.abs(f.spin)) + "°/S" : "";
        if (f.autopilot) {
            h.control = "AUTOPILOT";
            h.controlBox = BoxState.ENGAGED;
        } else if (n.burning && o.helm == HelmOrder.FLY_BURNS) {
            h.control = n.burnNumber > 0 ? "BURN " + n.burnNumber : "BURN";
            h.controlBox = BoxState.GUIDANCE;
        } else if (o.helm != HelmOrder.NONE) {
            h.control = orderShort(o.helm);
            h.controlBox = BoxState.ENGAGED;
        } else {
            h.control = "MANUAL";
            h.controlBox = BoxState.NORMAL;
        }
        h.assist = !f.present || f.rotationAssist;
        h.main = f.mainEngine || f.forward > 0.05f;
        h.rcs = f.rcs;

        // Reference: the ports through an approach, else the lock, else the nav target, else the orbit while thrusting
        OrbitInfo orbit = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        h.rng = h.closing = h.cpa = "";
        if (appr) {
            h.ref = HelmCluster.Ref.PORTS;
            h.ports = "PORT " + k.port + "/" + k.ports + (k.portSize.isEmpty() ? "" : " " + k.portSize)
                    + (k.other.isEmpty() ? "" : "  →  " + up(k.other) + (k.otherPort > 0 ? " PORT " + k.otherPort : ""));
            switch (k.clearance) {
                case GRANTED: h.clearance = "CLEARED"; h.clearanceBox = BoxState.ENGAGED; break;
                case DENIED: h.clearance = "REFUSED"; h.clearanceBox = BoxState.CAUTION; break;
                case NO_PORT: h.clearance = "NO FREE PORT"; h.clearanceBox = BoxState.CAUTION; break;
                case YIELDS: h.clearance = "STRUCK"; h.clearanceBox = BoxState.NORMAL; break;
                default: h.clearance = k.armed ? "ARMED" : "NOT ARMED"; h.clearanceBox = k.armed ? BoxState.NORMAL : BoxState.OFF; break;
            }
        } else if (t.present) {
            h.ref = HelmCluster.Ref.LOCK;
            h.refKind = kindOf(t.relation);
            h.refName = up(StDraw.bare(t.label));
            h.rng = Float.isNaN(t.range) ? "" : StDraw.range(t.range);
            h.closing = Float.isNaN(t.closing) ? "" : StDraw.speed(t.closing);
            h.cpa = Float.isNaN(t.cpaDistance) || Float.isNaN(t.cpaTime) ? "" : StDraw.range(t.cpaDistance) + "  " + NpFormat.tMinus(t.cpaTime);
        } else if (n.present) {
            h.ref = HelmCluster.Ref.NAV;
            h.refName = up(n.name);
            h.rng = Double.isNaN(n.distance) ? "" : StDraw.range(n.distance);
            h.closing = Float.isNaN(n.closing) ? "" : StDraw.speed(n.closing);
            h.cpa = Double.isNaN(n.cpaDistance) ? "" : StDraw.range(n.cpaDistance) + (Float.isNaN(n.cpaIn) ? "" : "  " + NpFormat.tMinus(n.cpaIn));
        } else if (orbit != null && sinceThrust <= ORBIT_HOLD) {
            h.ref = HelmCluster.Ref.ORBIT;
            boolean bound = !Double.isInfinite(orbit.period) && !Double.isNaN(orbit.period) && orbit.period > 0;
            h.ap = bound ? StDraw.range(orbit.apoapsis) : "ESCAPE";
            h.pe = StDraw.range(orbit.periapsis);
        } else {
            h.ref = HelmCluster.Ref.NONE;
        }

        // Next: the approach, the next burn, or the autopilot
        if (appr) {
            h.next = HelmCluster.Next.APPROACH;
            h.approachRange = Float.isNaN(k.range) ? "--" : StDraw.range(k.range);
            h.approachClosing = k.closing;
            h.safe = k.safeSpeed;
            h.offset = Float.isNaN(k.lateral) ? "" : Math.round(Math.abs(k.lateral)) + " M";
            h.angle = Float.isNaN(k.angleDeg) ? "" : Math.round(Math.abs(k.angleDeg)) + "°";
        } else if (n.burnsLeft > 0) {
            h.next = HelmCluster.Next.BURN;
            h.burning = n.burning;
            h.burn = n.burnNumber > 0 ? "BURN " + n.burnNumber : "BURN";
            h.burnWhen = n.burning ? "BURNING" : Float.isNaN(n.burnStartIn) ? "" : NpFormat.tMinus(n.burnStartIn);
            float dv = n.burning ? n.burnToGo : n.burnDeltaV;
            h.burnDv = Float.isNaN(dv) ? "" : NpFormat.mps(dv) + " M/S";
            boolean soon = !n.burning && !Float.isNaN(n.burnStartIn) && n.burnStartIn < 60f;
            h.burnAlign = soon && !Float.isNaN(n.pointingDeg) ? "ALIGN " + Math.round(Math.abs(n.pointingDeg)) + "°" : "";
        } else if (o.autopilot != Autopilot.OFF) {
            h.next = HelmCluster.Next.AUTOPILOT;
            h.autopilotHeld = o.autopilot == Autopilot.HELD;
            h.autopilot = h.autopilotHeld ? (o.autopilotHeld.isEmpty() ? "HELD" : "HELD: " + up(o.autopilotHeld))
                    : o.autopilotTo.isEmpty() ? "FLYING" : "→ " + up(o.autopilotTo);
        } else {
            h.next = HelmCluster.Next.NONE;
        }

        // Trigger: lit with a lock or in Red
        WeaponStatusProvider.WeaponFrame wf = d.weaponFrame;
        h.triggerLit = t.present || cond.level == ShipCondition.Level.RED;
        boolean guns = false, ready = false, noPower = false, crewed = false, launchers = false, launcherReady = false;
        float reload = Float.NaN, launcherIn = Float.NaN;
        for (int i = 0; i < wf.mounts.size; i++) {
            Mount m = wf.mounts.get(i);
            if (m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE) continue;
            if (m.kind == WeaponStatusProvider.Kind.LAUNCHER) {
                launchers = true;
                if (m.state == WeaponStatusProvider.State.READY) launcherReady = true;
                else if (m.working() && (Float.isNaN(launcherIn) || m.cooldown < launcherIn)) launcherIn = m.cooldown;
                continue;
            }
            guns = true;
            if (m.controller == WeaponStatusProvider.Controller.CREW || m.controller == WeaponStatusProvider.Controller.CONSOLE) crewed = true;
            if (m.state == WeaponStatusProvider.State.READY) ready = true;
            else if (m.state == WeaponStatusProvider.State.RELOADING || m.state == WeaponStatusProvider.State.CHARGING) {
                if (Float.isNaN(reload) || m.cooldown < reload) reload = m.cooldown;
            } else if (m.state == WeaponStatusProvider.State.NO_POWER) noPower = true;
        }
        boolean hold = wf.holdFire || ctx.holdFire;
        if (guns) {
            h.guns = ready ? "READY" : !Float.isNaN(reload) ? NpFormat.tenths(reload) + " S" : noPower ? "NO POWER" : "DOWN";
            h.gunsColor = ready || !Float.isNaN(reload) ? NpColors.WHITE : NpColors.AMBER;
            h.gunsMode = hold ? "HOLD" : crewed ? "AT WILL" : "TRIGGER";
            h.gunsModeBox = hold ? BoxState.CAUTION : crewed ? BoxState.ENGAGED : BoxState.NORMAL;
        } else {
            h.guns = "";
            h.gunsMode = "";
        }
        h.launchers = launchers;
        h.msl = wf.missilesLeft < 0 ? "NO LIMIT" : wf.missilesLeft + " LEFT";
        h.mslWhen = launcherReady ? "READY" : Float.isNaN(launcherIn) ? "DOWN" : NpFormat.tenths(launcherIn) + " S";
        h.decoy = wf.decoyCooldown <= 0f ? "READY" : (int) Math.ceil(wf.decoyCooldown) + " S";
        h.emitter = emitter;
        h.shieldsLowered = emitter && !d.shieldStatus.raised;

        // Δv: the tape is the tanks; HOME a notch, the plan a bracket
        double left = f.deltaVLeft;
        if (Double.isNaN(left)) {
            h.dv = "--";
            h.dvFrac = Float.NaN;
            h.home = "";
        } else if (Double.isInfinite(left)) {
            h.dv = "FREE";
            h.dvFrac = Float.NaN;
            h.home = "";
        } else {
            h.dv = NpFormat.mps(left);
            float share = d.shipStatus.propellant;
            double full = !Float.isNaN(share) && share > 0.01f ? left / share : Math.max(left, Double.isNaN(f.deltaVHome) ? 0 : f.deltaVHome) * 1.25;
            h.dvFrac = full > 0 ? (float) (left / full) : 0f;
            // at home, or near it, HOME would read 0: no notch then
            boolean home = !Double.isNaN(f.deltaVHome) && f.deltaVHome >= 1 && full > 0;
            h.tape.notch = home ? (float) Math.min(1.0, f.deltaVHome / full) : Float.NaN;
            h.home = home ? NpFormat.mps(f.deltaVHome) : "";
            boolean plan = f.deltaVPlanned > 0 && full > 0;
            h.tape.bracketA = plan ? (float) Math.max(0.0, (left - f.deltaVPlanned) / full) : Float.NaN;
            h.tape.bracketB = plan ? h.dvFrac : Float.NaN;
            h.tape.pointer = home && left < f.deltaVHome ? NpColors.AMBER : NpColors.WHITE;
        }
    }

    private static String orderShort(HelmOrder o) {
        switch (o) {
            case FLY_BURNS: return "BURNS";
            case MATCH_VELOCITY: return "MATCH";
            case HOLD_POSITION: return "HOLD";
            case APPROACH: return "APPROACH";
            case DOCK: return "DOCK";
            default: return "MANUAL";
        }
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

    /** The lock's state in a word, where it isn't simply fit: FLEEING, SURRENDERED, DISABLED. */
    private static String lockState(TargetStatus t) {
        String s = t.state == null ? "" : t.state;
        switch (s) {
            case "running": return "FLEEING";
            case "struck": return "SURRENDERED";
            case "silenced": return "SILENCED";
            case "NO_POWER":
            case "NO_CREW":
            case "HULL": return "DISABLED";
            case "ABANDONED": return "ABANDONED";
            case "WRECKED": return "WRECK";
            default: return "";
        }
    }

    private void tactical(HudData d, boolean emitter) {
        TacticalCluster c = tac;
        TargetStatus t = d.targetStatus;
        WeaponStatusProvider.WeaponFrame wf = d.weaponFrame;
        c.locked = t.present;
        c.target = up(StDraw.bare(t.label));
        c.targetKind = kindOf(t.relation);
        c.targetClass = t.relation == Relation.UNKNOWN ? "" : t.relation.name();
        c.targetHull = Float.isNaN(t.hull) ? "" : "HULL " + NpFormat.percent(t.hull);
        c.targetState = lockState(t);
        c.rng = Float.isNaN(t.range) ? "--" : StDraw.range(t.range);
        c.closing = Float.isNaN(t.closing) ? "--" : StDraw.speed(t.closing);
        c.guns.clear();
        c.launchers.clear();
        int pd = 0, pdWorking = 0;
        for (int i = 0; i < wf.mounts.size; i++) {
            Mount m = wf.mounts.get(i);
            switch (m.kind) {
                case POINT_DEFENSE:
                    pd++;
                    if (m.working()) pdWorking++;
                    break;
                case LAUNCHER: {
                    TacticalCluster.Launcher l = new TacticalCluster.Launcher();
                    l.place = StDraw.place(m.name);
                    l.left = m.ammo < 0 ? "--" : m.ammo + "/" + Math.max(m.ammo, m.magazine);
                    l.ready = Tactics.word(m, t.present);
                    l.salvo = m.ammo > 0 ? "SALVO " + Math.min(SALVO, m.ammo) : "";
                    c.launchers.add(l);
                    break;
                }
                default: {
                    if (m.controller != WeaponStatusProvider.Controller.PLAYER) break;
                    TacticalCluster.Gun g = new TacticalCluster.Gun();
                    g.place = StDraw.place(m.name);
                    g.crew = gunner(d, m);
                    g.state = Tactics.word(m, t.present);
                    g.box = Tactics.box(m, t.present);
                    g.hit = t.present && m.working() && !Float.isNaN(m.hitChance) ? NpFormat.percent(m.hitChance) : "";
                    c.guns.add(g);
                    break;
                }
            }
        }
        c.pd = pd > 0;
        if (wf.pointDefenseActive) {
            c.pdState = "ENGAGING";
            c.pdBox = BoxState.ENGAGED;
        } else if (pdWorking < pd) {
            c.pdState = pdWorking + " OF " + pd;
            c.pdBox = BoxState.CAUTION;
        } else {
            c.pdState = "READY";
            c.pdBox = BoxState.NORMAL;
        }
        c.decoy = wf.decoyCooldown <= 0f ? "READY" : (int) Math.ceil(wf.decoyCooldown) + " S";
        c.emitter = emitter;
        if (emitter) {
            ShieldStatus s = d.shieldStatus;
            c.shieldMode = s.raised ? "RAISED" : "LOWERED";
            c.shieldBox = s.raised ? BoxState.ENGAGED : BoxState.CAUTION;
            c.cloud = NpFormat.percent(s.share());
            c.trim = Pages.trimWord(s.trim);
        }
    }

    /** The crew member seated at the gun {@code m}, by the post's name, or "". */
    private static String gunner(HudData d, Mount m) {
        Array<StationBoardProvider.Seat> seats = d.stations.seats;
        for (int i = 0; i < seats.size; i++) {
            StationBoardProvider.Seat s = seats.get(i);
            if (s.post != Post.GUN || s.hand != StationBoardProvider.Hand.CREW) continue;
            if (s.name.equalsIgnoreCase(m.name) || s.tileX == m.tileX && s.tileY == m.tileY && m.tileX >= 0) return up(s.who);
        }
        return "";
    }

    private void gunner(HudData d) {
        GunnerSight g = gunner;
        TargetStatus t = d.targetStatus;
        Mount gun = null;
        WeaponStatusProvider.WeaponFrame wf = d.weaponFrame;
        for (int i = 0; i < wf.mounts.size; i++) {
            Mount m = wf.mounts.get(i);
            if (m.controller == WeaponStatusProvider.Controller.PLAYER && m.kind != WeaponStatusProvider.Kind.LAUNCHER) {
                gun = m;
                break;
            }
        }
        g.title(gun == null ? "GUN" : "GUN · " + StDraw.place(gun.name));
        if (gun != null) {
            g.weapon = StDraw.weaponName(gun.weapon);
            String[] st = Tactics.state(gun, t.present);
            g.state = st[0];
            g.stateBox = Tactics.box(gun, t.present);
            g.ammo = gun.ammo >= 0 ? gun.ammo + "/" + Math.max(gun.ammo, gun.magazine) : "";
            g.ready = gun.cooldown > 0.05f && gun.working() ? "READY IN " + NpFormat.tenths(gun.cooldown) + " S" : "";
        } else {
            g.weapon = "";
            g.state = "";
            g.ammo = "";
            g.ready = "";
        }
        g.locked = t.present;
        g.target = up(StDraw.bare(t.label));
        g.targetKind = kindOf(t.relation);
        g.rng = Float.isNaN(t.range) ? "--" : StDraw.range(t.range);
        float tof = d.targeting == null ? Float.NaN : d.targeting.getTimeToImpact();
        g.tof = Float.isNaN(tof) || tof <= 0f ? "" : NpFormat.tenths(tof) + " S";
        g.hit = gun != null && t.present && !Float.isNaN(gun.hitChance) ? NpFormat.percent(gun.hitChance) : "";
    }

    /** The station board: who works each station, its alerts, whether its console works; dim in Green with nothing lit. */
    private void board(HudData d) {
        UiContext ctx = d.ctx;
        StationBoardProvider.StationBoard b = d.stations;
        StationBoardProvider.Seat helmSeat = b.at(Post.HELM), weapons = b.at(Post.WEAPONS);
        StationBoardProvider.Seat reactor = b.at(Post.REACTOR), life = b.at(Post.LIFE_SUPPORT);
        StationBoard.Tile[] t = board.tiles;
        post(t[Station.HELM.ordinal()], helmSeat);
        StationBoard.Tile nav = t[Station.NAV.ordinal()];
        if (seat == Seat.HELM) {
            nav.hand = StationBoard.Hand.YOU;
        } else if (!ctx.helmsman.isEmpty() && !ctx.helmsman.equalsIgnoreCase("you")) {
            nav.hand = StationBoard.Hand.CREW;
            nav.who = up(ctx.helmsman);
        } else {
            nav.hand = StationBoard.Hand.EMPTY;
        }
        nav.struck = helmSeat != null && !helmSeat.working;
        post(t[Station.TAC.ordinal()], weapons);
        StationBoard.Tile eng = t[Station.ENG.ordinal()];
        eng.who = "";
        if (reactor != null && reactor.hand == StationBoardProvider.Hand.PLAYER || life != null && life.hand == StationBoardProvider.Hand.PLAYER) {
            eng.hand = StationBoard.Hand.YOU;
        } else if (reactor != null && reactor.hand != StationBoardProvider.Hand.NOBODY) {
            eng.hand = StationBoard.Hand.CREW;
            eng.who = up(reactor.who);
        } else if (life != null && life.hand != StationBoardProvider.Hand.NOBODY) {
            eng.hand = StationBoard.Hand.CREW;
            eng.who = up(life.who);
        } else {
            eng.hand = StationBoard.Hand.EMPTY;
        }
        eng.struck = !consoleWorks(d, Station.ENG);
        StationBoard.Tile comms = t[Station.COMMS.ordinal()];
        comms.hand = seat == Seat.HELM ? StationBoard.Hand.YOU : StationBoard.Hand.EMPTY;
        comms.struck = helmSeat != null && !helmSeat.working;
        StationBoard.Tile cmd = t[Station.CMD.ordinal()];
        cmd.hand = StationBoard.Hand.NONE;
        cmd.badge = ctx.latched ? "LATCHED" : "";
        cmd.struck = false;
        boolean lit = false;
        for (int i = 0; i < t.length; i++) {
            t[i].alert = ann.stations[i];
            if (t[i].alert != null) lit = true;
        }
        board.dim = cond.level == ShipCondition.Level.GREEN && !lit;
        double s = ctx.simSeconds;
        board.day = "DAY " + (long) (s / 86400.0);
        board.time = NpFormat.timeOfDay(s);
        if (seat == Seat.GUN) {
            board.only = Station.TAC;
            Mount gun = null;
            for (int i = 0; i < d.weaponFrame.mounts.size; i++) {
                Mount m = d.weaponFrame.mounts.get(i);
                if (m.controller == WeaponStatusProvider.Controller.PLAYER) {
                    gun = m;
                    break;
                }
            }
            board.onlyName = gun == null ? "GUN" : "GUN · " + StDraw.place(gun.name);
            t[Station.TAC.ordinal()].hand = StationBoard.Hand.YOU;
        } else {
            board.only = null;
        }
    }

    private static void post(StationBoard.Tile tile, StationBoardProvider.Seat s) {
        tile.who = "";
        tile.struck = s != null && !s.working;
        if (s == null || s.hand == StationBoardProvider.Hand.NOBODY) {
            tile.hand = StationBoard.Hand.EMPTY;
        } else if (s.hand == StationBoardProvider.Hand.PLAYER) {
            tile.hand = StationBoard.Hand.YOU;
        } else {
            tile.hand = StationBoard.Hand.CREW;
            tile.who = up(s.who);
        }
    }

    // ---- zones (section 6.2) --------------------------------------------------------------------

    private void layout() {
        float w = host.width(), h = host.height();
        root.setBounds(0f, 0f, w, h);
        place(strip, Zone.STRIP, w, h);
        place(annunciator, Zone.ANNUNCIATOR, w, h);
        place(warpStrip, Zone.WARP, w, h);
        place(ticker, Zone.TICKER, w, h);
        place(berth, Zone.BERTH, w, h);
        float[] z = StationsLayout.zone(Zone.SUIT, w, h);
        put(suit, z[0], z[1] + z[3] - suit.getHeight(), h);
        place(prompt, seat == Seat.HELM || seat == Seat.WEAPONS || seat == Seat.GUN ? Zone.PROMPT_SEATED : Zone.PROMPT_DECK, w, h);
        foot(mfdLeft, StationsLayout.zone(Zone.MFD_LEFT, w, h), h);
        foot(mfdRight, StationsLayout.zone(Zone.MFD_RIGHT, w, h), h);
        z = StationsLayout.zone(Zone.CLUSTER, w, h);
        put(helm, z[0], z[1], h);
        put(tac, z[0], z[1], h);
        foot(gunner, z, h);
        place(board, Zone.BOARD, w, h);
    }

    private static void place(Actor a, Zone zone, float w, float h) {
        float[] z = StationsLayout.zone(zone, w, h);
        put(a, z[0], z[1], h);
    }

    /** {@code a} on the foot of its zone: a folded MFD's rail, the gunner sight. */
    private static void foot(Actor a, float[] z, float h) {
        put(a, z[0], z[1] + z[3] - a.getHeight(), h);
    }

    /** Puts {@code a}'s top left corner at ({@code x}, {@code yDown}), y down from the top of a screen {@code h} tall. */
    private static void put(Actor a, float x, float yDown, float h) {
        a.setPosition(Math.round(x), Math.round(h - yDown - a.getHeight()));
    }

    // ---- keys -----------------------------------------------------------------------------------

    /** The key bound to {@code a}, in capitals as a cap shows it. */
    private String key(Action a) {
        String k = host == null ? "" : host.data().key(a.name());
        return up(k.isEmpty() ? a.defaultKeyName() : k);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (host == null) return false;
        if (keycode == Input.Keys.BACKSLASH) {
            acknowledge();
            return true;
        }
        if (Gdx.input == null || mfds.seat == MfdState.Seat.NONE) return false;
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        if (!alt) return false;
        boolean left = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        int n = digit(keycode);
        Station s = Station.numbered(n);
        if (s != null) {
            mfds.station(left, s);
            return true;
        }
        if (n == 0) {
            mfds.pin(left);
            return true;
        }
        if (keycode == Input.Keys.MINUS || keycode == Input.Keys.NUMPAD_SUBTRACT) {
            mfds.fold(left);
            return true;
        }
        return false;
    }

    private static int digit(int keycode) {
        if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) return keycode - Input.Keys.NUM_0;
        if (keycode >= Input.Keys.NUMPAD_0 && keycode <= Input.Keys.NUMPAD_9) return keycode - Input.Keys.NUMPAD_0;
        return -1;
    }

    // ---- the rest -------------------------------------------------------------------------------

    private static String up(String s) {
        return StDraw.upper(s);
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
        // The world's own symbology and the console prompt stay classic until this HUD draws its own
        return EnumSet.of(ClassicPart.WORLD_MARKERS, ClassicPart.THREAT_OVERLAY, ClassicPart.PROMPT);
    }
}
