package com.ringsofiron.moddingdata;

import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.moddingdata.defs.SoundEventData;
import com.ringsofiron.moddingdata.defs.StationTypeDef;

import java.util.Map;

/**
 * Checks references between definitions after everything is loaded, so a typo in a mod
 * shows up in the load report instead of as a crash mid-game. Problems are warnings;
 * nothing is removed.
 */
public final class DefValidator {

    private DefValidator() {
    }

    public static void validate(DefRegistry defs, AssetRegistry assets, LoadReport report) {
        // First make sure every def of a registered type converts.
        for (String type : defs.types()) {
            Class<?> cls = defs.registeredClass(type);
            if (cls == null) continue;
            for (DefEntry e : defs.entries(type)) {
                try {
                    defs.convert(e, cls);
                } catch (IllegalArgumentException ex) {
                    report.error(e.lastSource(), ex.getMessage());
                }
            }
        }

        for (DefEntry e : defs.entries("ship")) {
            ShipDef ship;
            try {
                ship = defs.convert(e, ShipDef.class);
            } catch (IllegalArgumentException ex) {
                continue; // already reported
            }
            if (ship.chassis != null && !defs.has("chassis", ship.chassis)) {
                report.warn(e.lastSource(), e + " uses unknown chassis '" + ship.chassis + "'");
            }
            if (ship.texture != null && assets != null && !assets.has(ship.texture)) {
                report.warn(e.lastSource(), e + " uses unknown texture key '" + ship.texture + "'");
            }
            for (ShipDef.Hardpoint hp : ship.hardpoints) {
                if (hp.weapon != null && !defs.has("weapon", hp.weapon)) {
                    report.warn(e.lastSource(), e + " mounts unknown weapon '" + hp.weapon + "'");
                }
            }
        }

        for (DefEntry e : defs.entries("station_type")) {
            StationTypeDef st;
            try {
                st = defs.convert(e, StationTypeDef.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            checkCommodities(defs, report, e, st.produces);
            checkCommodities(defs, report, e, st.consumes);
            if (st.blueprint != null) {
                if (!defs.has("chassis", st.blueprint)) {
                    report.warn(e.lastSource(), e + " uses unknown blueprint '" + st.blueprint + "'");
                } else if (!stationChassis(defs, st.blueprint)) {
                    report.warn(e.lastSource(), e + " uses blueprint '" + st.blueprint
                            + "', which is not marked station = true");
                }
            }
        }

        for (DefEntry e : defs.entries("sound_event")) {
            SoundEventData sound;
            try {
                sound = defs.convert(e, SoundEventData.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (sound.path == null || sound.path.isEmpty()) {
                report.warn(e.lastSource(), e + " has no path, so it cannot be played");
            }
        }

        for (DefEntry e : defs.entries("faction")) {
            FactionDef faction;
            try {
                faction = defs.convert(e, FactionDef.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            for (String enemy : faction.enemies) {
                if (!defs.has("faction", enemy)) report.warn(e.lastSource(), e + " is at war with unknown faction '" + enemy + "'");
            }
        }

        for (DefEntry e : defs.entries("rank")) {
            RankDef rank;
            try {
                rank = defs.convert(e, RankDef.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (rank.maxChassis != null && !defs.has("chassis", rank.maxChassis)) {
                report.warn(e.lastSource(), e + " allows unknown chassis '" + rank.maxChassis + "'");
            }
        }
    }

    private static boolean stationChassis(DefRegistry defs, String id) {
        try {
            return defs.require("chassis", id, ChassisDef.class).station;
        } catch (IllegalArgumentException ex) {
            return false; // a broken chassis is reported on its own
        }
    }

    private static void checkCommodities(DefRegistry defs, LoadReport report, DefEntry e, Map<String, Float> goods) {
        if (goods == null) return;
        for (String c : goods.keySet()) {
            if (!defs.has("commodity", c)) report.warn(e.lastSource(), e + " trades unknown commodity '" + c + "'");
        }
    }
}
