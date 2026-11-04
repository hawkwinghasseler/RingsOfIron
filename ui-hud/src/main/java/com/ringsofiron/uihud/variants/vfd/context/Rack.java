package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

/**
 * The bottom rack (FILAMENT's change: two racks frame the view): the seat's instruments in one rack
 * along the bottom, H4's lights on its left wing, the seat's primary instrument in a tower in the
 * middle with the master alert cue on its shelf, and H5 on the right wing. Docked or latched the tower
 * sinks and only the shelf with the master lamps stays proud; on deck there is no rack, and the lamps
 * stand alone on a plate of their own at the bottom centre.
 */
final class Rack {
    /** What stands in the middle of the rack. */
    enum Tower {
        /** No rack: on deck. */
        NONE,
        /** H1, the flight display. */
        FLIGHT,
        /** H3, the docking display, the tower grown to the zone's top. */
        DOCKING,
        /** F2, fire control's weapons rack. */
        WEAPONS,
        /** A gun's row and any launcher's. */
        GUN,
        /** Docked or latched: the sunk tower's shelf. */
        SHELF
    }

    static final int CLICK_WARN = 1, CLICK_CAUT = 2, CLICK_SCOPE = 3;

    Tower tower = Tower.NONE;
    /** F2's or the gun's height, which sets its tower's top. */
    float weaponsH;
    /** The master lamps: lit, the newest warning's code, where they stand; on a plate of their own. */
    boolean warn, caut, warnFlash, cautFlash, plate;
    String warnCode = "";
    float cueY;
    /** H4's lamp cells: the tags and their levels (null nominal); three with no shield emitter. */
    final String[] lightTags = new String[4];
    final Level[] lightLevels = new Level[4];
    int lights;
    /** H5 folded to its key, with the count of contacts beyond 50 km. */
    boolean scopeKey;
    int beyond;

    void update(HudData d, Situations s, Cautions c, boolean scopeOpen, int contactsBeyond, float weaponsH, float time) {
        this.weaponsH = weaponsH;
        switch (s.seat) {
            case HELM:
                tower = s.docked || s.latched ? Tower.SHELF : s.approach ? Tower.DOCKING : Tower.FLIGHT;
                break;
            case FIRE_CONTROL:
                tower = s.docked || s.latched ? Tower.SHELF : Tower.WEAPONS;
                break;
            case GUN:
                tower = Tower.GUN;
                break;
            default:
                tower = Tower.NONE;
        }
        plate = tower == Tower.NONE;
        cueY = cueY(tower, weaponsH);
        warn = c.warn;
        caut = c.caut;
        warnCode = c.warnCode;
        // The lamps flash for their first 2 s at most twice a second (5.3), then hold
        warnFlash = warn && c.warnAge < Cautions.PULSE && (time % 0.5f) >= 0.25f;
        cautFlash = caut && c.cautAge < Cautions.PULSE && (time % 0.5f) >= 0.25f;

        boolean shields = d.shieldStatus.present && d.shieldStatus.emitters > 0;
        lights = 0;
        for (Cautions.Light l : Cautions.Light.values()) {
            if (l == Cautions.Light.SHIELDS && !shields) continue;
            lightTags[lights] = l == Cautions.Light.LIFE_SUP ? "LIFE SUP" : l.name();
            lightLevels[lights] = c.lights[l.ordinal()];
            lights++;
        }
        scopeKey = tower != Tower.NONE && tower != Tower.SHELF && !scopeOpen;
        beyond = contactsBeyond;
    }

    /** The lamps' top: on the tower's shelf, above F2, on the sunk shelf, or on deck at the bottom. */
    static float cueY(Tower tower, float weaponsH) {
        switch (tower) {
            case FLIGHT: return Geo.CUE_Y_HELM;
            case DOCKING: return Geo.CUE_Y_DOCK;
            case WEAPONS: return Geo.CUE_Y_RACK;
            case GUN: return gunTop(weaponsH) + 8f;
            case SHELF: return Geo.CUE_Y_SHELF;
            default: return Geo.CUE_Y_DECK;
        }
    }

    /** A gun's tower: its rows under the lamps. */
    static float gunTop(float weaponsH) {
        return Geo.RACK_Y - 4f - weaponsH - 46f;
    }

    /** The tower's top and half width, on the grid. */
    float towerTop() {
        switch (tower) {
            case FLIGHT: return Geo.TOWER_Y;
            case DOCKING: return Geo.DOCK_TOWER_Y;
            case WEAPONS: return Geo.RACK_TOWER_Y;
            case GUN: return gunTop(weaponsH);
            case SHELF: return Geo.SHELF_Y;
            default: return Geo.RACK_Y;
        }
    }

    float towerHalf() {
        switch (tower) {
            case WEAPONS: return Geo.RACK_TOWER_HALF;
            case SHELF: return Geo.SHELF_HALF;
            default: return Geo.TOWER_HALF;
        }
    }

    /** The top of what the rack holds at {@code x} (stage units, from the left), for the world's floor. */
    float top(float x, float width) {
        if (tower == Tower.NONE) return Geo.H;
        float mid = width / 2f, half = towerHalf();
        return x > mid - half && x < mid + half ? Math.min(towerTop(), cueY - 6f) : Geo.RACK_Y;
    }

    void boxes(FilamentActor a, float midX, float bottomY, float rightX) {
        float cx = midX + Geo.W / 2f, y = bottomY + cueY;
        a.box(CLICK_WARN, cx - Geo.CUE_W - 4f, y, Geo.CUE_W, Geo.CUE_H);
        a.box(CLICK_CAUT, cx + 4f, y, Geo.CUE_W, Geo.CUE_H);
        if (scopeKey) a.box(CLICK_SCOPE, rightX + Geo.KEY5_X, bottomY + Geo.KEY5_Y, Geo.KEY5_S, Geo.KEY5_S);
    }

    // ------------------------------------------------------------------ painting

    /** The rack itself across a stage {@code width} wide, with the origin at the grid's bottom group (x 0). */
    void paintRack(Filament f, float width) {
        if (tower == Tower.NONE) return;
        float mid = width / 2f, half = towerHalf();
        edges[0] = -8f;
        edges[1] = mid - half;
        edges[2] = mid + half;
        edges[3] = width + 8f;
        tops[0] = tops[2] = Geo.RACK_Y;
        tops[1] = Math.min(towerTop(), cueY - 8f);
        f.rackColumns(edges, tops, Geo.H + 8f, 7f);
    }

    /** The rack's three columns, the wings and the tower: their edges and tops, refilled each paint. */
    private final float[] edges = new float[4], tops = new float[3];

    /** The master lamps, centred on the grid. */
    void paintCue(Filament f) {
        float cx = Geo.W / 2f, w = Geo.CUE_W, h = Geo.CUE_H, y = cueY;
        if (plate) f.plate(cx - w - 10f, y - 6f, 2f * w + 20f, h + 12f, 0f, 6f);
        f.lampKey(cx - w - 4f, y, w, h, "MASTER WARN", warn ? warnCode : null, Filament.Lamp.RED, warn && !warnFlash);
        f.lampKey(cx + 4f, y, w, h, "MASTER CAUT", null, Filament.Lamp.AMBER, caut && !cautFlash);
    }

    /** H4's compact face: a lamp cell per light on the left wing, always there. */
    void paintLights(Filament f) {
        if (tower == Tower.NONE || lights == 0) return;
        float x = Geo.LIGHTS_X, y = Geo.LIGHTS_Y, w = Geo.LIGHTS_W, h = Geo.LIGHTS_H, gap = 4f;
        float tw = (w - 8f - gap * (lights - 1)) / lights;
        f.module(x, y, w, h, f.o().filaments(0).noGlare().radius(2f));
        for (int i = 0; i < lights; i++) lamp(f, x + 4f + i * (tw + gap), y + 4f, tw, h - 8f, lightTags[i], lightLevels[i]);
    }

    /** A lamp cell: unlit glass with its legend's ghost, an amber frame for a caution, a red block for a warning. */
    static void lamp(Filament f, float x, float y, float w, float h, String tag, Level level) {
        float ty = y + h / 2f + 13f * 0.36f;
        if (level == Level.WARNING) {
            f.rectLit(x, y, w, h, f.o().red().norm().r(1.2f));
            f.knockout(x + 5f, ty, tag, f.o());
        } else if (level == Level.CAUTION) {
            f.frameLit(x, y, w, h, 1.1f, f.o().amber().norm());
            f.legend(x + 5f, ty, tag, f.o().amber().full().noGhost());
        } else {
            f.frameLit(x, y, w, h, 1.1f, f.o().ghostOnly());
            f.legend(x + 5f, ty, tag, f.o().lit(false));
        }
    }

    /** H5 folded: a key with a window showing the count of contacts beyond 50 km. */
    void paintScopeKey(Filament f) {
        if (!scopeKey) return;
        float x = Geo.KEY5_X, y = Geo.KEY5_Y, s = Geo.KEY5_S;
        f.key(x, y, s, s, "", f.o());
        f.module(x + 9f, y + 5f, 30f, 24f, f.o().filaments(0).noGlare().radius(1.5f));
        f.seg7(x + 18.5f, y + 9f, 16f, beyond > 0 ? Integer.toString(Math.min(9, beyond)) : "", f.o().cells(1).dim());
        f.silk(x + s / 2f, y + 43f, "SCOPE", f.o().centre());
    }

    static Phosphor color(Level level) {
        return level == Level.WARNING ? Phosphor.RED : level == Level.CAUTION ? Phosphor.AMBER : Phosphor.ZINC;
    }
}
