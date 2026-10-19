package com.ringsofiron.moddingdata;

import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.OperationDef;
import com.ringsofiron.moddingdata.defs.RankDef;
import com.ringsofiron.moddingdata.defs.ShipSaleDef;
import com.ringsofiron.moddingdata.defs.ShipyardDef;
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

        for (DefEntry e : defs.entries("operation")) {
            OperationDef op;
            try {
                op = defs.convert(e, OperationDef.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (op.giver != null && !op.giver.isEmpty() && !defs.has("faction", op.giver)) {
                report.warn(e.lastSource(), e + " is given by unknown faction '" + op.giver + "'");
            }
            if (op.rank != null && !defs.has("rank", op.rank)) {
                report.warn(e.lastSource(), e + " needs unknown rank '" + op.rank + "'");
            }
            if (op.steps.isEmpty()) report.warn(e.lastSource(), e + " has no steps");
            for (String step : op.steps) {
                if (!OPERATION_STEPS.contains(step)) report.warn(e.lastSource(), e + " has unknown step '" + step + "'");
            }
        }

        for (DefEntry e : defs.entries("shipyard")) {
            ShipyardDef yard;
            try {
                yard = defs.convert(e, ShipyardDef.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (yard.faction == null || !defs.has("faction", yard.faction)) {
                report.warn(e.lastSource(), e + " belongs to unknown faction '" + yard.faction + "'");
            }
            for (String type : yard.stationTypes) {
                if (!defs.has("station_type", type)) report.warn(e.lastSource(), e + " names unknown station type '" + type + "'");
            }
        }

        for (DefEntry e : defs.entries("ship_sale")) {
            ShipSaleDef sale;
            try {
                sale = defs.convert(e, ShipSaleDef.class);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (sale.ship == null || !defs.has("ship", sale.ship)) report.warn(e.lastSource(), e + " sells unknown ship '" + sale.ship + "'");
            if (sale.seller == null || !defs.has("faction", sale.seller)) {
                report.warn(e.lastSource(), e + " is sold by unknown faction '" + sale.seller + "'");
            }
            if (sale.rank != null && !defs.has("rank", sale.rank)) report.warn(e.lastSource(), e + " needs unknown rank '" + sale.rank + "'");
            if (sale.price < 0) report.warn(e.lastSource(), e + " has a negative price");
        }
    }

    /** The contract types an operation's steps may be. */
    public static final java.util.Set<String> OPERATION_STEPS =
            java.util.Set.of("patrol", "recon", "bounty", "escort", "convoy", "delivery", "intel", "raid");

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
