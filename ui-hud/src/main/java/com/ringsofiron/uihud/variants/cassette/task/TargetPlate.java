package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.NavigationProvider;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardSymbols;
import com.ringsofiron.uihud.variants.cassette.YardSymbols.Rel;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Z6, the target plate (TGT), set into the right edge: one subject, the locked target when there is
 * one, else the navigation target. A ship's plate gives its name and kind, relation and owner, state
 * and reason, the two threat lenses, the hull estimate in four bands, range and closing, and the closest
 * pass; a station's gives its owner, your standing, its services as a gang of lenses, and range. With
 * both targets set, the navigation target gets one line under the plate.
 */
final class TargetPlate extends YardPart {
    static final float W = 300f, H_FULL = 204f, H_ID = 64f, H_RAILS = 92f, NAV_LINE = 26f;
    private static final float X = 980f, Y = 332f;

    private final TaskFrame f;
    Yard.Finish finish = Yard.Finish.YARD;
    /** FLT is in DOCK: the plate keeps its identity lines, range and speeds are FLT's. */
    boolean identityOnly;

    TargetPlate(TaskFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** Whether there is a subject at all. */
    static boolean wanted(HudData d, boolean docked) {
        if (d.targetStatus.present) return !docked || !d.ctx.stationName.equalsIgnoreCase(d.targetStatus.label);
        if (docked) return false;
        return d.navReadout.present;
    }

    private boolean shipSubject() {
        return f.data.targetStatus.present;
    }

    /** The navigation target gets its own line under the combat target's plate. */
    private boolean navLine() {
        HudData d = f.data;
        return d.targetStatus.present && d.navReadout.present && !d.navReadout.name.equalsIgnoreCase(d.targetStatus.label);
    }

    float height() {
        float h = f.rails ? H_RAILS : identityOnly ? H_ID : H_FULL;
        return h + (navLine() ? NAV_LINE : 0f);
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        p.unit(X, Y, W, h, finish, "r");
        if (shipSubject()) ship(p, d);
        else station(p, d);
        if (navLine()) {
            float ny = Y + h - NAV_LINE - 2f;
            NavReadout nav = d.navReadout;
            p.rect(992f, ny, 276f, 1f, finish.lo, 0.8f);
            p.legend("NAV", 994f, ny + 17f, finish.ink);
            p.flaps(1024f, ny + 4f, YardText.fit(nav.name, 16), 16, 8f, 17f, 13f, Yard.FLAP_TEXT);
            p.drumsR(1240f, ny + 4f, YardText.km(nav.distance).trim(), 11f, 17f, 15f, 66f, false);
            p.legend("KM", 1245f, ny + 17f, finish.ink);
        }
    }

    private void ship(YardPaint p, HudData d) {
        TargetStatus t = d.targetStatus;
        Contact c = d.contactFrame.locked();
        Color ink = finish.ink;
        p.flaps(994f, Y + 12f, YardText.fit(t.label, 18), 18, 8f, 18f, 13.5f, Yard.FLAP_TEXT);
        p.flaps(1144f, Y + 12f, c == null ? "" : YardText.fit(kind(c.kind), 12), 12, 8.6f, 18f, 13f, Yard.FLAP_TEXT);
        if (f.rails) {
            rangeRow(p, t.range, t.closing, Y + 38f);
            passRow(p, t, Y + 64f);
            return;
        }
        relFlap(p, 994f, Y + 38f, rel(t.relation, c));
        String contract = d.navReadout.present && d.navReadout.name.equalsIgnoreCase(t.label) ? d.navReadout.contract : "";
        if (!contract.isEmpty()) p.paperTag(1196f, Y + 37f, 70f, YardText.cut(contract.split(" ")[0], 8));
        if (identityOnly) return;
        String[] st = state(t, c, d);
        p.flaps(994f, Y + 64f, st[0], 11, 8.4f, 18f, 13f, Yard.FLAP_TEXT);
        p.flaps(1092f, Y + 64f, YardText.fit(st[1], 14), 14, 8.4f, 18f, 13f, st[1].isEmpty() ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);
        boolean firing = t.firingOnUs && (d.ctx.missilesInbound > 0 || d.ctx.condition == com.ringsofiron.uihud.providers.UiContextProvider.Condition.RED);
        boolean locked = t.firingOnUs && !firing;
        sevLens(p, 994f, Y + 92f, 130f, 18f, "FIRING ON YOU", Yard.RED_LAMP, firing, true);
        sevLens(p, 1134f, Y + 92f, 130f, 18f, "HAS YOU LOCKED", Yard.AMBER_LAMP, locked, false);
        p.legend("HULL", 994f, Y + 127f, ink);
        p.legend("EST.", 994f, Y + 140f, finish.dim);
        p.bands(1034f, Y + 119f, 232f, new String[]{"INTACT", "DAMAGED", "HEAVY", "CRIPPLED"}, hullBand(t.hull));
        rangeRow(p, t.range, t.closing, Y + 151f);
        passRow(p, t, Y + 177f);
    }

    private void rangeRow(YardPaint p, float range, float closing, float y) {
        Color ink = finish.ink;
        p.legend("RANGE", 994f, y + 15f, ink);
        // Each field's drums end at a fixed edge, so its unit and the next field never move
        p.drumsR(1084f, y, YardText.km(range).trim(), 13f, 20f, 16f, 46f, false);
        p.legend("KM", 1089f, y + 15f, ink);
        p.flaps(1110f, y + 2f, Float.isNaN(closing) ? "" : closing >= 0f ? "CLOSING" : "OPENING", 7, 8.4f, 17f, 13f, Yard.FLAP_TEXT);
        String cl = Float.isNaN(closing) ? "    " : YardText.count(Math.abs(closing), 4);
        p.drumsR(1230f, y, cl, 13f, 20f, 16f, 52f, false);
        p.legend("M/S", 1235f, y + 15f, ink);
    }

    private void passRow(YardPaint p, TargetStatus t, float y) {
        if (Float.isNaN(t.cpaTime) || t.cpaTime <= 0f || Float.isNaN(t.cpaDistance)) return;
        Color ink = finish.ink;
        p.legend("PASSES", 994f, y + 15f, ink);
        p.drumsR(1084f, y, YardText.km(t.cpaDistance).trim(), 13f, 20f, 16f, 40f, false);
        p.legend("KM", 1089f, y + 15f, ink);
        p.legend("IN", 1116f, y + 15f, ink);
        p.drums(1136f, y, YardText.clock(t.cpaTime), 12f, 20f, 15f, false);
    }

    private void station(YardPaint p, HudData d) {
        NavReadout nav = d.navReadout;
        StationInfo s = nav.station;
        Color ink = finish.ink;
        p.flaps(994f, Y + 12f, YardText.fit(nav.name, 18), 18, 8f, 18f, 13.5f, Yard.FLAP_TEXT);
        p.flaps(1144f, Y + 12f, YardText.fit(kind(nav.kind), 12), 12, 8.6f, 18f, 13f, Yard.FLAP_TEXT);
        if (f.rails) {
            rangeRow(p, (float) nav.distance, nav.closing, Y + 38f);
            return;
        }
        relFlap(p, 994f, Y + 38f, rel(nav.relation, null));
        p.flaps(1079f, Y + 38f, YardText.fit(s.owner, 13), 13, 8.4f, 18f, 13f, Yard.FLAP_TEXT);
        if (!nav.contract.isEmpty()) p.paperTag(1196f, Y + 37f, 70f, YardText.cut(nav.contract.split(" ")[0], 8));
        if (identityOnly) return;
        if (nav.kind == NavigationProvider.Kind.STATION && s.present) {
            p.legend("STANDING", 994f, Y + 79f, ink);
            p.flaps(1056f, Y + 64f, YardText.fit(s.standingName, 10), 10, 8.4f, 18f, 13f, Yard.FLAP_TEXT);
            p.push(1158f, Y + 64f, 106f, 18f, new String[]{"YOUR SPONSOR"}, Yard.WHITE_LAMP, s.sponsor, YardPaint.Kind.INDICATOR, false, false);
            String[] names = {"MARKET", "YARD", "SHIPS", "JOBS", "HIRING"};
            boolean[] has = {s.market, s.yard, s.shipyard, s.contracts, s.hiring};
            for (int i = 0; i < names.length; i++) {
                p.push(994f + i * 55f, Y + 92f, 50f, 18f, new String[]{names[i]}, Yard.WHITE_LAMP, has[i], YardPaint.Kind.INDICATOR, false, false);
            }
            boolean near = !Double.isNaN(nav.distance) && nav.distance <= 50_000.0;
            p.legend("CLEARANCE", 994f, Y + 134f, ink);
            p.flaps(1066f, Y + 120f, near ? (s.refusal.isEmpty() ? "WOULD CLEAR" : "WOULD REFUSE") : "", 12, 8.4f, 18f, 13f, s.refusal.isEmpty() ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);
        }
        rangeRow(p, (float) nav.distance, nav.closing, Y + 151f);
    }

    static String kind(ContactProvider.Kind k) {
        switch (k) {
            case STATION: return "STATION";
            case MISSILE: return "MISSILE";
            case DERELICT: return "DERELICT";
            case WAYPOINT: return "WAYPOINT";
            default: return "SHIP";
        }
    }

    static String kind(NavigationProvider.Kind k) {
        switch (k) {
            case STATION: return "STATION";
            case WRECK: return "WRECK";
            case BODY: return "BODY";
            case POINT: return "POINT";
            default: return "SHIP";
        }
    }

    static Rel rel(Relation r, Contact c) {
        if (c != null && (c.kind == ContactProvider.Kind.DERELICT || c.state == ContactProvider.State.WRECKED)) return Rel.INERT;
        switch (r) {
            case HOSTILE: case MISSILE: return Rel.HOSTILE;
            case FRIENDLY: return Rel.FRIENDLY;
            default: return Rel.NEUTRAL;
        }
    }

    /** The relation on split flaps: a symbol cell, then the word (a neutral that fires on you turns hostile). */
    private static void relFlap(YardPaint p, float x, float y, Rel rel) {
        float cw = 8.4f, ch = 18f;
        p.rrect(x - 2f, y - 2f, cw + 4f, ch + 4f, 1.5f, Yard.FLAP_FRAME);
        p.rect(x + 0.5f, y, cw - 1f, ch, Yard.FLAP_CELL);
        YardSymbols.rel(p, rel, x + cw / 2f, y + ch / 2f + (rel == Rel.OWN ? 1.5f : 0f), rel == Rel.OWN ? 2.8f : 3.2f, Yard.FLAP_TEXT, 0f, true, 1.1f, YardSymbols.State.ACTIVE, false);
        p.flaps(x + cw + 3f, y, rel.name(), 8, cw, ch, 13f, Yard.FLAP_TEXT);
    }

    /** The state and its reason, as the plate words them. */
    static String[] state(TargetStatus t, Contact c, HudData d) {
        String s = t.state.toLowerCase(Locale.ROOT);
        if (c != null) {
            switch (c.state) {
                case WRECKED: return new String[]{"WRECKED", ""};
                case DISABLED: return new String[]{"DISABLED", reason(c.disabledReason)};
                case STRUCK: return new String[]{"STRUCK", "HOLD YOUR FIRE"};
                case SILENCED: return new String[]{"SILENCED", "HOLD YOUR FIRE"};
                case RUNNING: return new String[]{"RUNNING", ""};
                default: break;
            }
            if (c.kind == ContactProvider.Kind.DERELICT) return new String[]{"DERELICT", ""};
        }
        if (s.contains("struck")) return new String[]{"STRUCK", "HOLD YOUR FIRE"};
        if (s.contains("silenced")) return new String[]{"SILENCED", "HOLD YOUR FIRE"};
        if (s.contains("running")) return new String[]{"RUNNING", ""};
        if (t.relation == Relation.HOSTILE && (t.firingOnUs || d.ctx.combat)) return new String[]{"FIGHTING", ""};
        return new String[]{"UNDER WAY", ""};
    }

    private static String reason(String r) {
        if (r == null || r.isEmpty()) return "";
        return r.replace('_', ' ');
    }

    /** Four bands, as the sensors give no more: intact, damaged, heavily damaged, crippled. */
    static int hullBand(float hull) {
        if (Float.isNaN(hull)) return -1;
        if (hull >= 0.9f) return 0;
        if (hull >= 0.6f) return 1;
        if (hull >= 0.3f) return 2;
        return 3;
    }

    private static void sevLens(YardPaint p, float x, float y, float w, float h, String name, Yard.Lamp lamp, boolean lit, boolean warning) {
        p.severityLens(x, y, w, h, name, lit ? lamp : Yard.WHITE_LAMP, lit, warning, false);
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        float ly = localY(py);
        if (shipSubject() && ly >= Y + 60f && ly < Y + 86f) {
            String[] st = state(d.targetStatus, d.contactFrame.locked(), d);
            if (st[1].equals("HOLD YOUR FIRE")) return "Struck: firing on it lifts the surrender";
        }
        return null;
    }
}
