package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed.Message;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Where each alert and notice goes in the stations spec (sections 7 and 13): the annunciator's six
 * tiles (one per kind, warnings first, newest first, "+n MORE" past six), the master lamps, which
 * station each alert lights on the board, which alert has the MFD (auto-paging), the callout ticker's
 * lines, and the responses for the prompt line. The game's {@link com.ringsofiron.uihud.data.AlertRegistry}
 * says what is wrong and its notice feed what happened; this decides where it is said. Pure logic, so it
 * is tested without a screen.
 */
final class StationAlerts {
    /** Tiles in the annunciator's row. */
    static final int SLOTS = 6;
    /** Seconds a new tile's ring flashes, unless acknowledged first. */
    static final float FRESH_SECONDS = 4f;
    /** Seconds a cleared tile stays, faded, before its slot empties. */
    static final float FADE_SECONDS = 1.2f;
    /** Seconds a ticker line holds: advisories, then cautions and warnings (7.2). */
    static final float HOLD_ADVISORY = 6f, HOLD_ALERT = 10f;
    /** Lines in the ticker. */
    static final int LINES = 3;
    /** Seconds a response holds on the prompt line (13.6). */
    static final float RESPONSE_SECONDS = 4f;
    /** A notice this soon after one of the player's own keys answers it: a response, not a callout. */
    static final float RESPONSE_WINDOW = 0.6f;
    /** Callouts from one station come at most this often; the rest go to the log only (13.5). */
    static final float STATION_QUIET = 3f;

    /** One annunciator tile: a tag on its lens, a value on its flaps. */
    static final class Tile {
        final String kind;
        String tag = "", value = "";
        Level level = Level.CAUTION;
        boolean acknowledged, fresh;
        /** Real seconds when it first lit, and when its condition cleared (NaN while it holds). */
        float firstSeen, clearedAt = Float.NaN;
        /** Where its worst alert is read (13.2), for a click. */
        Page home = Page.DAMAGE;
        /** The "+n MORE" tile. */
        boolean more;
        /** What the pointer over it says: its worst alert in full. */
        String text = "";

        Tile(String kind) {
            this.kind = kind;
        }

        boolean clearing() {
            return !Float.isNaN(clearedAt);
        }
    }

    /** One callout: the station's tag, who said it and what. */
    static final class Line {
        String tag = "", who = "", text = "";
        /** WARNING or CAUTION mark the line; null for an advisory. */
        Level level;
        float born, hold;
        int count = 1;
        /** Where a click on it goes. */
        Page home;
    }

    final Array<Tile> tiles = new Array<>();
    private final ObjectMap<String, Tile> byKind = new ObjectMap<>();
    private final Array<Tile> order = new Array<>();
    private final Tile more = new Tile("more");
    boolean masterWarning, masterCaution;
    /** Each station's worst caution or warning, null for none: its tile on the board lights in that role. */
    final Level[] station = new Level[Station.values().length];
    /** This frame's newly raised caution or warning that takes a tile, the worst and newest: the auto-page's alert. */
    Alert raised;
    final Array<Line> lines = new Array<>();
    /** The newest response and when it came; "" for none. */
    String response = "";
    float responseAt = -100f;

    private float clock;
    private float lastKey = -100f;
    private final ObjectFloatMap<String> seen = new ObjectFloatMap<>();
    private final ObjectSet<String> present = new ObjectSet<>();
    private final Array<String> gone = new Array<>();
    private final float[] stationSaid = new float[Station.values().length];
    private boolean primed;
    private double noticeTime = Double.NEGATIVE_INFINITY;
    private final ObjectSet<String> noticesAtTime = new ObjectSet<>();
    private final Kinds kinds = new Kinds();
    /** No reactor running, this frame. */
    private boolean blackout;

    StationAlerts() {
        for (int i = 0; i < stationSaid.length; i++) stationSaid[i] = -100f;
    }

    /** The player pressed {@code keycode}: a notice in the next moment answers it. */
    void keyPressed(int keycode) {
        // Flying and walking keys are held and pressed all the time: a notice then is not their answer
        switch (keycode) {
            case Input.Keys.W: case Input.Keys.A: case Input.Keys.S: case Input.Keys.D:
            case Input.Keys.Z: case Input.Keys.C: case Input.Keys.X: case Input.Keys.SPACE:
            case Input.Keys.SHIFT_LEFT: case Input.Keys.SHIFT_RIGHT: case Input.Keys.CONTROL_LEFT: case Input.Keys.CONTROL_RIGHT:
            case Input.Keys.ALT_LEFT: case Input.Keys.ALT_RIGHT:
                return;
            default:
                lastKey = clock;
        }
    }

    float clock() {
        return clock;
    }

    /**
     * Reads this frame's alerts and notices. {@code guide} is the guide card's text, whose notices the
     * card already shows; {@code emitter} says whether DEFENSE has shields to show.
     */
    void update(HudData d, float delta, boolean blackout, boolean emitter, String guide) {
        clock += delta;
        this.blackout = blackout;
        Array<Alert> all = d.alerts.all();
        present.clear();
        raised = null;
        for (int ai = 0; ai < all.size; ai++) {
            Alert a = all.get(ai);
            present.add(a.id);
            if (!seen.containsKey(a.id)) {
                seen.put(a.id, clock);
                fresh(d, a, emitter);
            }
        }
        gone.clear();
        for (ObjectFloatMap.Entry<String> e : seen.entries()) if (!present.contains(e.key)) gone.add(e.key);
        for (String id : gone) seen.remove(id, 0f);

        for (int i = 0; i < station.length; i++) station[i] = null;
        for (int ai = 0; ai < all.size; ai++) {
            Alert a = all.get(ai);
            if (a.level == Level.ADVISORY) continue;
            int s = Station.of(a).ordinal();
            if (station[s] == null || a.level.ordinal() > station[s].ordinal()) station[s] = a.level;
        }

        annunciator(d, all, blackout, emitter);
        notices(d, guide);
        for (int i = lines.size - 1; i >= 0; i--) if (clock - lines.get(i).born > lines.get(i).hold) lines.removeIndex(i);
        if (clock - responseAt > RESPONSE_SECONDS) response = "";
    }

    // --- The annunciator -------------------------------------------------------------------------

    private void annunciator(HudData d, Array<Alert> all, boolean blackout, boolean emitter) {
        kinds.read(d, all, blackout);
        for (Tile t : byKind.values()) t.more = false;
        for (Kinds.Group g : kinds.groups) {
            Tile t = byKind.get(g.kind);
            if (t == null) {
                t = new Tile(g.kind);
                t.firstSeen = clock;
                byKind.put(g.kind, t);
            }
            t.clearedAt = Float.NaN;
            t.tag = g.tag;
            t.value = g.value;
            t.level = g.level;
            t.acknowledged = g.acknowledged;
            t.fresh = !g.acknowledged && clock - t.firstSeen < FRESH_SECONDS;
            t.home = g.worst == null ? Page.POWER : Page.home(g.worst, emitter, d.ctx.disabledReason);
            t.text = g.worst == null ? "No reactor running: every system is without power" : g.worst.text;
        }
        gone.clear();
        for (ObjectMap.Entry<String, Tile> e : byKind.entries()) {
            if (kinds.has(e.key)) continue;
            Tile t = e.value;
            if (!t.clearing()) t.clearedAt = clock;
            t.fresh = false;
            if (clock - t.clearedAt > FADE_SECONDS) gone.add(e.key);
        }
        for (String k : gone) byKind.remove(k);

        order.clear();
        for (Tile t : byKind.values()) order.add(t);
        // Warnings first, then cautions; within each the newest first
        order.sort((a, b) -> a.level != b.level ? b.level.ordinal() - a.level.ordinal() : Float.compare(b.firstSeen, a.firstSeen));
        tiles.clear();
        if (order.size <= SLOTS) tiles.addAll(order);
        else {
            for (int i = 0; i < SLOTS - 1; i++) tiles.add(order.get(i));
            Level worst = Level.CAUTION;
            boolean fresh = false, acked = true;
            int n = 0;
            for (int i = SLOTS - 1; i < order.size; i++) {
                Tile t = order.get(i);
                if (t.clearing()) continue;
                n++;
                if (t.level.ordinal() > worst.ordinal()) worst = t.level;
                fresh |= t.fresh;
                acked &= t.acknowledged;
            }
            more.tag = "+" + n + " MORE";
            more.value = "";
            more.level = worst;
            more.fresh = fresh;
            more.acknowledged = acked;
            more.more = true;
            more.home = order.get(SLOTS - 1).home;
            more.text = n + " more conditions, the oldest cautions";
            if (n > 0) tiles.add(more);
        }

        masterWarning = masterCaution = false;
        for (Tile t : order) {
            if (t.clearing() || t.acknowledged) continue;
            if (t.level == Level.WARNING) masterWarning = true;
            else if (t.level == Level.CAUTION) masterCaution = true;
        }
    }

    /** Whether the annunciator has anything to show: a tile, or a master lamp lit. */
    boolean annunciatorUp() {
        return tiles.size > 0;
    }

    /** Whether alert {@code id} still holds. */
    boolean holds(HudData d, String id) {
        return d.alerts.get(id) != null;
    }

    /**
     * Groups the registry's alerts into tiles, one per kind (13.4). Its tags and values keep to eight
     * capitals each, so they fit any style's tile.
     */
    static final class Kinds {
        static final class Group {
            String kind, tag, value;
            Level level;
            boolean acknowledged;
            Alert worst;
            int count;
            float soonest = Float.NaN;
            String room = "";
        }

        final Array<Group> groups = new Array<>();
        private final Array<Group> pool = new Array<>();

        boolean has(String kind) {
            for (Group g : groups) if (g.kind.equals(kind)) return true;
            return false;
        }

        Group get(String kind) {
            for (Group g : groups) if (g.kind.equals(kind)) return g;
            return null;
        }

        void read(HudData d, Array<Alert> all, boolean blackout) {
            pool.addAll(groups);
            groups.clear();
            int stunned = 0, unpowered = 0, down = 0;
            for (int ai = 0; ai < all.size; ai++) {
                Alert a = all.get(ai);
                String kind = kindOf(a, blackout);
                if (kind == null) continue;
                String fault = a.id.startsWith("sys:") ? fault(a) : "";
                if (fault.equals("stunned")) stunned++;
                else if (fault.equals("no power") || fault.equals("shorted")) unpowered++;
                else if (fault.equals("destroyed") || fault.equals("disabled")) down++;
                Group g = get(kind);
                if (g == null) {
                    g = pool.size > 0 ? pool.pop() : new Group();
                    g.kind = kind;
                    g.level = a.level;
                    g.acknowledged = true;
                    g.worst = a;
                    g.count = 0;
                    g.soonest = Float.NaN;
                    g.room = a.room;
                    groups.add(g);
                } else if (a.level.ordinal() > g.level.ordinal()) {
                    g.level = a.level;
                    g.worst = a;
                    g.room = a.room;
                }
                g.count++;
                g.acknowledged &= a.acknowledged;
                if (!Float.isNaN(a.timer) && (Float.isNaN(g.soonest) || a.timer < g.soonest)) g.soonest = a.timer;
            }
            if (blackout) {
                // No reactor works at all: one warning for the whole grid, whatever else is down
                Group g = get("power");
                if (g == null) {
                    g = pool.size > 0 ? pool.pop() : new Group();
                    g.kind = "power";
                    g.worst = null;
                    g.count = 1;
                    g.acknowledged = acknowledgedBlackout(all);
                    groups.add(g);
                }
                g.level = Level.WARNING;
            }
            for (int i = groups.size - 1; i >= 0; i--) {
                Group g = groups.get(i);
                label(d, g, blackout, stunned, unpowered, down);
                if (g.tag == null) groups.removeIndex(i);
            }
        }

        private static boolean acknowledgedBlackout(Array<Alert> all) {
            for (int ai = 0; ai < all.size; ai++) {
                Alert a = all.get(ai);
                if (a.id.startsWith("sys:REACTOR") && !a.acknowledged) return false;
            }
            return true;
        }

        /** The tile an alert goes on, or null for one that takes none (an advisory, the emergency). */
        static String kindOf(Alert a, boolean blackout) {
            if (a.level == Level.ADVISORY) return null;
            String id = a.id;
            if (id.equals("missile")) return "missile";
            if (id.startsWith("fire:")) return "fire";
            if (id.startsWith("breach:")) return "breach";
            if (id.startsWith("air:")) return "air";
            if (id.startsWith("smoke:")) return "smoke";
            if (id.equals("hull")) return "hull";
            if (id.startsWith("sys:")) {
                String f = fault(a);
                if (blackout && (f.equals("no power") || id.startsWith("sys:REACTOR"))) return "power";
                if (f.equals("stunned")) return "stun";
                if (f.equals("no power") || f.equals("shorted")) return "power";
                if (f.equals("destroyed") || f.equals("disabled")) return "system";
                return null;
            }
            if (id.equals("captain:bleeding")) return "bleeding";
            if (id.equals("captain:suit")) return "suit";
            if (id.equals("captain:air")) return "nosuit";
            if (id.startsWith("casualty:")) return "casualty";
            if (id.startsWith("shield:")) return "shield";
            if (id.equals("oxygen")) return "oxygen";
            if (id.equals("propellant")) return "propellant";
            if (id.equals("hostile")) return "hostile";
            if (id.equals("helm")) return "helm";
            if (id.equals("disabled")) return "disabled";
            // Abandon ship is the status strip's emergency text, not a tile
            return null;
        }

        /** A system alert's fault as the game words it: "shorted", "no power", "destroyed". */
        static String fault(Alert a) {
            int i = a.text.lastIndexOf(": ");
            return i < 0 ? "" : a.text.substring(i + 2).trim().toLowerCase(Locale.ROOT);
        }

        /** Sets the group's tag and value, or a null tag when another tile already says it. */
        private void label(HudData d, Group g, boolean blackout, int stunned, int unpowered, int down) {
            Alert a = g.worst;
            switch (g.kind) {
                case "missile":
                    g.tag = "MISSILE";
                    g.value = Float.isNaN(g.soonest) ? String.valueOf(a.count)
                            : (a.count < 10 ? a.count + "  " : a.count + " ") + YardText.clock(g.soonest);
                    break;
                case "fire": g.tag = "FIRE"; g.value = room(g); break;
                case "breach": g.tag = "BREACH"; g.value = room(g); break;
                case "air": g.tag = a.code.equals("VACUUM") ? "VACUUM" : "PRESSURE"; g.value = room(g); break;
                case "smoke": g.tag = "SMOKE"; g.value = room(g); break;
                case "hull": g.tag = "HULL"; g.value = Math.round(d.shipStatus.structure * 100f) + "%"; break;
                case "power":
                    g.tag = "POWER";
                    g.value = blackout ? "NONE" : unpowered + " SYS";
                    break;
                case "stun":
                    g.tag = "STUN";
                    g.value = stunned > 1 ? stunned + " SYS" : abbrev(a.code) + (Float.isNaN(g.soonest) ? "" : " " + (int) Math.ceil(g.soonest));
                    break;
                case "system":
                    if (down > 1) {
                        g.tag = "SYSTEMS";
                        g.value = down + " DOWN";
                    } else {
                        g.tag = YardText.fit(a.code, 8);
                        g.value = fault(a).equals("destroyed") ? "WRECKED" : "DISABLED";
                    }
                    break;
                case "bleeding": g.tag = "BLEEDING"; g.value = YardText.clock(g.soonest); break;
                case "suit": g.tag = "SUIT AIR"; g.value = YardText.clock(g.soonest); break;
                case "nosuit": g.tag = "NO SUIT"; g.value = YardText.fit(a.room, 8); break;
                case "casualty":
                    g.tag = "CASUALTY";
                    g.value = Float.isNaN(g.soonest) ? String.valueOf(g.count) : g.count + " " + YardText.clock(g.soonest);
                    break;
                case "shield": g.tag = "SHIELD"; g.value = d.alerts.get("shield:tripped") != null ? "TRIP" : "DOWN"; break;
                case "oxygen":
                    g.tag = "O2 TANKS";
                    g.value = a.level == Level.WARNING || Float.isNaN(d.shipStatus.oxygen) ? "DRY" : Math.round(d.shipStatus.oxygen * 100f) + "%";
                    break;
                case "propellant": g.tag = "DELTA-V"; g.value = "LOW"; break;
                case "hostile": g.tag = "HOSTILE"; g.value = "FIRING"; break;
                case "helm": g.tag = "HELM"; g.value = empty(d.ctx.helmFault) ? "EMPTY" : "DOWN"; break;
                case "disabled": {
                    // The disabled ship's tile is its cause's, when that has one up (13.4)
                    String r = d.ctx.disabledReason.toLowerCase(Locale.ROOT);
                    if (r.contains("reactor") && (blackout || has("power") || has("system"))) g.tag = null;
                    else if (r.contains("hull") && has("hull")) g.tag = null;
                    else {
                        g.tag = "DISABLED";
                        g.value = r.contains("reactor") ? "NO POWER" : r.contains("hull") ? "HULL" : r.contains("crew") ? "CREW" : "";
                    }
                    break;
                }
                default: g.tag = null;
            }
            if (g.tag != null) {
                g.tag = YardText.fit(g.tag, 8);
                g.value = YardText.fit(g.value, 8);
            }
        }

        /** The worst room, and +n for the others: "BRIDGE+1". */
        private static String room(Group g) {
            String r = g.room == null ? "" : g.room.toUpperCase(Locale.ROOT);
            if (g.count <= 1) return YardText.fit(r, 8);
            String more = "+" + (g.count - 1);
            return YardText.fit(r, 8 - more.length()) + more;
        }

        static boolean empty(String fault) {
            String f = fault.toLowerCase(Locale.ROOT);
            return f.contains("nobody") || f.contains("no one") || f.contains("empty") || f.contains("unmanned") || f.contains("no crew");
        }
    }

    /** A system's four-letter name, for a value with a number after it: "RCTR 22". */
    static String abbrev(String system) {
        switch (system) {
            case "REACTOR": return "RCTR";
            case "LIFE SUP": return "LSUP";
            case "TURRET": return "GUN";
            case "SHIELDS": return "SHLD";
            case "WEAPONS": return "WPNS";
            case "AIRLOCK": return "LOCK";
            case "ENGINE": return "ENG";
            case "DOCK PORT": return "PORT";
            default: return YardText.fit(system, 4);
        }
    }

    // --- Callouts ----------------------------------------------------------------------------------

    /**
     * A caution or warning first seen this frame: it may take the MFD, and its callout says what the
     * tile cannot (13.5): where, who, which one. With nothing to add it is not spoken.
     */
    private void fresh(HudData d, Alert a, boolean emitter) {
        if (a.level == Level.ADVISORY) return;
        boolean tile = Kinds.kindOf(a, false) != null;
        if (tile && (raised == null || a.level.ordinal() > raised.level.ordinal()
                || a.level == raised.level && a.since >= raised.since)) {
            raised = a;
        }
        String id = a.id;
        Station st = Station.of(a);
        String text = null, who = "SHIP";
        if (id.equals("missile")) {
            String side = side(d);
            if (side != null) text = (a.count > 1 ? "missiles inbound " : "missile inbound ") + side + ".";
            who = crewAt(d, Post.WEAPONS, "SHIP");
        } else if (id.equals("hostile")) {
            text = a.text + ".";
            who = crewAt(d, Post.WEAPONS, "SHIP");
        } else if (id.startsWith("fire:") || id.startsWith("breach:") || id.startsWith("smoke:") || id.startsWith("air:")) {
            // The tile shows the worst room: a second room is news only the callout can give
            if (others(d, id.substring(0, id.indexOf(':') + 1), id)) text = roomLine(a);
        } else if (id.startsWith("sys:")) {
            String f = Kinds.fault(a);
            // In a blackout every system is without power: the POWER tile and Command's callout say so once
            if (!f.isEmpty() && !f.equals("off") && !(blackout && f.equals("no power"))) text = systemLine(d, a, f);
        } else if (id.startsWith("casualty:")) {
            text = a.text + ".";
            who = "SHIP";
        } else if (id.equals("helm")) {
            text = d.ctx.helmFault.isEmpty() ? null : lowerFirst(d.ctx.helmFault) + ".";
        } else if (id.equals("disabled")) {
            text = "disabled, " + d.ctx.disabledReason + ".";
        } else if (id.equals("abandon")) {
            text = "abandon ship: crew to the pods.";
        }
        if (text == null) return;
        if (clock - stationSaid[st.ordinal()] < STATION_QUIET) return;
        stationSaid[st.ordinal()] = clock;
        say(st.tag, who, text, a.level, HOLD_ALERT, Page.home(a, emitter, d.ctx.disabledReason));
    }

    private static boolean others(HudData d, String prefix, String id) {
        for (int oi = 0; oi < d.alerts.all().size; oi++) {
            Alert o = d.alerts.all().get(oi);
            if (o.id.startsWith(prefix) && !o.id.equals(id) && o.level != Level.ADVISORY) return true;
        }
        return false;
    }

    /** "fire in the hold.", "the bridge is in vacuum." */
    private static String roomLine(Alert a) {
        String room = a.room.isEmpty() ? "a room" : "the " + a.room.toLowerCase(Locale.ROOT);
        switch (a.code) {
            case "FIRE": return "fire in " + room + ".";
            case "BREACH": return room + " is venting.";
            case "SMOKE": return "smoke in " + room + ".";
            case "VACUUM": return room + " is in vacuum.";
            default: return "pressure falling in " + room + ".";
        }
    }

    /** A system out: named by its mount where it is a gun, and what clears it. */
    private static String systemLine(HudData d, Alert a, String fault) {
        String name = a.code.toLowerCase(Locale.ROOT);
        boolean mount = false;
        for (int mi = 0; mi < d.weaponFrame.mounts.size; mi++) {
            Mount m = d.weaponFrame.mounts.get(mi);
            if (a.tileX >= 0 && m.tileX == a.tileX && m.tileY == a.tileY) {
                name = m.name;
                mount = true;
                break;
            }
        }
        // A gun's name says where it is; a system's room does
        String where = !mount && !a.room.isEmpty() ? " in the " + a.room.toLowerCase(Locale.ROOT) : "";
        switch (fault) {
            case "shorted": return name + where + " shorted, resets itself.";
            case "stunned": return name + where + " stunned.";
            case "no power": return name + where + " has no power.";
            case "disabled": return name + where + " disabled, needs repair.";
            case "destroyed": return name + where + " destroyed.";
            default: return name + where + " " + fault + ".";
        }
    }

    /** The side the nearest inbound missile comes from, or null when the ship can't tell. */
    static String side(HudData d) {
        Contact best = null;
        for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
            Contact c = d.contactFrame.contacts.get(ci);
            if (c.kind != Kind.MISSILE || !c.threat || Float.isNaN(c.bearingDeg)) continue;
            if (best == null || c.range < best.range) best = c;
        }
        if (best == null) return null;
        float b = best.bearingDeg;
        while (b > 180f) b -= 360f;
        while (b < -180f) b += 360f;
        if (Math.abs(b) <= 30f) return "from dead ahead";
        if (Math.abs(b) >= 150f) return "from astern";
        return b > 0f ? "from port" : "from starboard";
    }

    /** The crew member at {@code post}, or {@code otherwise}. */
    static String crewAt(HudData d, Post post, String otherwise) {
        Seat s = d.stations.at(post);
        return s != null && s.hand == Hand.CREW && !s.who.isEmpty() ? s.who : otherwise;
    }

    private void say(String tag, String who, String text, Level level, float hold, Page home) {
        for (Line l : lines) {
            if (l.text.equals(text) && l.who.equals(who)) {
                l.count++;
                l.born = clock;
                return;
            }
        }
        Line l = new Line();
        l.tag = tag;
        l.who = who;
        l.text = text;
        l.level = level == Level.ADVISORY ? null : level;
        l.born = clock;
        l.hold = hold;
        l.home = home;
        lines.add(l);
        while (lines.size > LINES) lines.removeIndex(0);
    }

    // --- Notices ---------------------------------------------------------------------------------

    /** The game's new notices: responses to the prompt line, the rest to the ticker in the game's words. */
    private void notices(HudData d, String guide) {
        Array<Message> all = d.messages.all();
        if (!primed) {
            // What was said before the HUD came up stays in the log
            primed = true;
            Message n = d.messages.newest();
            if (n != null) {
                noticeTime = n.time;
                for (int mi = 0; mi < all.size; mi++) {
                    Message m = all.get(mi);
                    if (m.time == noticeTime) noticesAtTime.add(m.text);
                }
            }
            return;
        }
        for (int i = all.size - 1; i >= 0; i--) {
            Message m = all.get(i);
            if (m.time < noticeTime) continue;
            if (m.time > noticeTime) {
                noticeTime = m.time;
                noticesAtTime.clear();
            }
            if (!noticesAtTime.add(m.text)) {
                repeat(m);
                continue;
            }
            notice(m, guide);
        }
    }

    /** The same notice again while it is up: its line counts it. */
    private void repeat(Message m) {
        for (Line l : lines) {
            if (l.text.equals(m.text) || m.text.endsWith(l.text)) {
                if (m.count > l.count) {
                    l.count = m.count;
                    l.born = clock;
                }
                return;
            }
        }
    }

    private void notice(Message m, String guide) {
        String text = m.text.trim();
        if (text.isEmpty()) return;
        // The guide card shows the tutorial's words; the tiles show the missiles and the propellant
        if (!guide.isEmpty() && (text.equals(guide) || guide.endsWith(text) || text.endsWith(guide))) return;
        if (text.startsWith("MISSILE WARNING") || text.startsWith("Propellant low")) return;
        // The status strip and Command's callout carry the ship's disablement; the game's own line is the log's
        if (text.startsWith("Your ship is disabled")) return;
        if (clock - lastKey <= RESPONSE_WINDOW) {
            response = text;
            responseAt = clock;
            return;
        }
        String who = "SHIP", body = text;
        int colon = text.indexOf(": ");
        if (colon > 0 && colon <= 20 && !text.substring(0, colon).matches(".*\\d.*")) {
            who = text.substring(0, colon);
            body = text.substring(colon + 2);
        }
        String tag = tagOf(who, body);
        if (Station.byTag(who) != null) {
            // "Helm: approaching Highport": the station is the tag; the ship says it
            who = "SHIP";
        }
        Level level = m.level == Level.WARNING ? Level.CAUTION : Level.ADVISORY;
        Station st = Station.byTag(tag);
        say(tag, who, body, level, level == Level.ADVISORY ? (m.important ? 8f : HOLD_ADVISORY) : HOLD_ALERT, st == null ? null : st.pages()[0]);
        if (m.count > 1) lines.peek().count = m.count;
    }

    /** The station a notice belongs to, by what it is about. */
    static String tagOf(String who, String body) {
        Station named = Station.byTag(who);
        if (named != null) return named.tag;
        String t = body.toLowerCase(Locale.ROOT);
        if (has(t, "clearance", "docking", "docked", "undock", "berth", "beacon", "tow", "port ", "station")) return Station.COMMS.tag;
        if (has(t, "burn", "warp", "orbit", "transfer", "autopilot", "course", "arriv", "plan")) return Station.NAV.tag;
        if (has(t, "missile", "target", "gun", "pirate", "raider", "hostile", "decoy", "shield", "surrender", "struck", "boarding", "salvo", "locked", "lock on")) return Station.TAC.tag;
        if (has(t, "fire", "breach", "leak", "patch", "repair", "power", "reactor", "venting", "oxygen", "hull", "short", "tank", "smoke", "pressure")) return Station.ENG.tag;
        if (has(t, "helm", "heading", "assist", "thrust", "propellant")) return Station.HELM.tag;
        // A station speaking ("Highport: ...") is Comms; the rest is the captain's business
        if (!who.equals("SHIP") && Character.isUpperCase(who.charAt(0)) && who.indexOf(' ') < 0 && t.contains("your ship")) return Station.COMMS.tag;
        return Station.CMD.tag;
    }

    private static boolean has(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
