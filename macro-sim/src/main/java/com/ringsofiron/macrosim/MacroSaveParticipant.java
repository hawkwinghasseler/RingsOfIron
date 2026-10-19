package com.ringsofiron.macrosim;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomyNodeComponent;
import com.ringsofiron.macrosim.economy.TradeOrder;
import com.ringsofiron.macrosim.fleet.FreighterComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saves macro-sim's state under {@code "macro-sim"} in the save's modules section: the clock and next
 * economy pass, every station's economy node, the trade order board, every freighter and the bubble
 * boundary bookkeeping. {@link EconomyNodeComponent} and {@link FreighterComponent} are skipped by the
 * entity serializer and written here instead, as plain JSON; they are re-attached to their entities
 * (found by UUID) on load.
 *
 * <p>Orbits and pending burns are not here: they live in orbital-mechanics' {@code OrbitComponent} and
 * {@code ManeuverNodeComponent} and are saved with the entities, so a freighter mid-transfer resumes
 * its burns and this participant only reschedules its arrival. Use with {@code OrbitalRailsModel};
 * the stub rails keep their orbits outside the ECS and are not saved.
 */
public class MacroSaveParticipant implements SaveParticipant {
    public static final String KEY = "macro-sim";

    private final MacroSimulation sim;

    public MacroSaveParticipant(MacroSimulation sim) {
        this.sim = sim;
    }

    /** Skips the components this participant writes itself and registers it with the service. */
    public static MacroSaveParticipant register(MacroSimulation sim, SerializationService service) {
        service.skipComponent(EconomyNodeComponent.class);
        service.skipComponent(FreighterComponent.class);
        MacroSaveParticipant participant = new MacroSaveParticipant(sim);
        service.addParticipant(participant);
        return participant;
    }

    @Override
    public String saveKey() {
        return KEY;
    }

    @Override
    public void write(Json json) {
        json.writeValue("now", sim.now());
        json.writeValue("nextEconomyPass", sim.nextEconomyPass());
        json.writeValue("nextOrderId", sim.economy().nextOrderId());

        json.writeArrayStart("stations");
        for (EconomyNode n : sim.stations()) {
            json.writeObjectStart();
            json.writeValue("stationUuid", n.stationUuid);
            json.writeValue("stationTypeId", n.stationTypeId);
            json.writeValue("displayName", n.displayName);
            json.writeValue("lastProductionRatio", n.lastProductionRatio);
            json.writeValue("shortfallDays", n.shortfallDays);
            writeMap(json, "inventory", n.inventory);
            writeMap(json, "prices", n.prices);
            writeMap(json, "reservedOut", n.reservedOut);
            writeMap(json, "incoming", n.incoming);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();

        json.writeArrayStart("orders");
        for (TradeOrder o : sim.economy().orders()) {
            json.writeObjectStart();
            json.writeValue("id", o.id);
            json.writeValue("commodity", o.commodity);
            json.writeValue("fromUuid", o.fromUuid);
            json.writeValue("toUuid", o.toUuid);
            json.writeValue("quantity", o.quantity);
            json.writeValue("buyPrice", o.buyPrice);
            json.writeValue("sellPrice", o.sellPrice);
            json.writeValue("claimedBy", o.claimedBy);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();

        json.writeArrayStart("freighters");
        for (Map.Entry<String, FreighterComponent> e : sim.freighters().all().entrySet()) {
            FreighterComponent f = e.getValue();
            json.writeObjectStart();
            json.writeValue("uuid", e.getKey());
            json.writeValue("displayName", f.displayName);
            json.writeValue("state", f.state.name());
            json.writeValue("resumeState", f.resumeState.name());
            json.writeValue("dockedAtUuid", f.dockedAtUuid);
            json.writeValue("destinationUuid", f.destinationUuid);
            json.writeValue("capacity", f.capacity);
            json.writeValue("cargoCommodity", f.cargoCommodity);
            json.writeValue("cargoQuantity", f.cargoQuantity);
            json.writeValue("credits", f.credits);
            json.writeValue("orderId", f.orderId);
            // JSON has no NaN; a missing arrival time means "not travelling".
            if (!Double.isNaN(f.arrivalTime)) json.writeValue("arrivalTime", f.arrivalTime);
            if (f.transitBodyUuid != null) {
                json.writeValue("transitBodyUuid", f.transitBodyUuid);
                json.writeValue("transitDeadline", f.transitDeadline);
            }
            json.writeValue("tripsCompleted", f.tripsCompleted);
            json.writeValue("deltaVUsed", f.deltaVUsed);
            json.writeValue("lastArrivalMiss", f.lastArrivalMiss);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();

        if (sim.bubble() != null) {
            writeSet(json, "inBubble", sim.bubble().inBubble());
            writeSet(json, "fastPasses", sim.bubble().fastPasses());
        }
    }

    @Override
    public void read(Json json, JsonValue data) {
        sim.beginRestore(data.getDouble("now"), data.getDouble("nextEconomyPass"));
        if (sim.bubble() != null) sim.bubble().restore(readSet(data.get("inBubble")), readSet(data.get("fastPasses")));

        for (JsonValue s = child(data, "stations"); s != null; s = s.next) {
            EconomyNode n = new EconomyNode(s.getString("stationUuid"), s.getString("stationTypeId"),
                    s.getString("displayName", null));
            n.lastProductionRatio = s.getDouble("lastProductionRatio", 0);
            n.shortfallDays = s.getDouble("shortfallDays", 0);
            readMap(s.get("inventory"), n.inventory);
            readMap(s.get("prices"), n.prices);
            readMap(s.get("reservedOut"), n.reservedOut);
            readMap(s.get("incoming"), n.incoming);
            sim.restoreStation(n);
        }

        List<TradeOrder> orders = new ArrayList<>();
        Map<Long, TradeOrder> byId = new HashMap<>();
        for (JsonValue o = child(data, "orders"); o != null; o = o.next) {
            TradeOrder order = new TradeOrder(o.getLong("id"), o.getString("commodity"), o.getString("fromUuid"),
                    o.getString("toUuid"), o.getDouble("quantity"), o.getDouble("buyPrice"), o.getDouble("sellPrice"));
            order.claimedBy = o.getString("claimedBy", null);
            orders.add(order);
            byId.put(order.id, order);
        }
        sim.economy().restoreOrders(orders, data.getLong("nextOrderId", 1));

        for (JsonValue v = child(data, "freighters"); v != null; v = v.next) {
            FreighterComponent f = new FreighterComponent(v.getString("displayName", null), v.getDouble("capacity"),
                    v.getDouble("credits"));
            f.state = FreighterComponent.State.valueOf(v.getString("state"));
            f.resumeState = FreighterComponent.State.valueOf(v.getString("resumeState", "IDLE"));
            f.dockedAtUuid = v.getString("dockedAtUuid", null);
            f.destinationUuid = v.getString("destinationUuid", null);
            f.cargoCommodity = v.getString("cargoCommodity", null);
            f.cargoQuantity = v.getDouble("cargoQuantity", 0);
            f.orderId = v.getLong("orderId", 0);
            f.arrivalTime = v.has("arrivalTime") ? v.getDouble("arrivalTime") : Double.NaN;
            f.transitBodyUuid = v.getString("transitBodyUuid", null);
            f.transitDeadline = v.has("transitDeadline") ? v.getDouble("transitDeadline") : Double.NaN;
            f.tripsCompleted = v.getInt("tripsCompleted", 0);
            f.deltaVUsed = v.getDouble("deltaVUsed", 0);
            f.lastArrivalMiss = v.getDouble("lastArrivalMiss", 0);
            sim.restoreFreighter(v.getString("uuid"), f, byId.get(f.orderId));
        }
        // Pirates and patrols are saved with their entities; only their plans need rescheduling
        sim.restoreHostiles();
    }

    private static JsonValue child(JsonValue data, String name) {
        JsonValue array = data.get(name);
        return array == null ? null : array.child;
    }

    private static void writeMap(Json json, String name, Map<String, Double> map) {
        json.writeObjectStart(name);
        for (Map.Entry<String, Double> e : map.entrySet()) json.writeValue(e.getKey(), e.getValue());
        json.writeObjectEnd();
    }

    private static void readMap(JsonValue obj, Map<String, Double> into) {
        into.clear();
        if (obj == null) return;
        for (JsonValue v = obj.child; v != null; v = v.next) into.put(v.name, v.asDouble());
    }

    private static void writeSet(Json json, String name, Set<String> set) {
        json.writeArrayStart(name);
        for (String s : set) json.writeValue(s);
        json.writeArrayEnd();
    }

    private static Set<String> readSet(JsonValue array) {
        Set<String> out = new LinkedHashSet<>();
        if (array != null) for (JsonValue v = array.child; v != null; v = v.next) out.add(v.asString());
        return out;
    }
}
