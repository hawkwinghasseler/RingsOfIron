package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.shipops.BoardedComponent;
import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.shipops.CargoOrdersComponent;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.Salvage;
import com.ringsofiron.shipops.SalvageOrdersComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.uihud.providers.BoardingProvider;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * ui-hud's boarding window from {@link Boarding} and {@link Salvage}: the ship the player's is
 * latched to, its boarding, and once taken, its parts and cargo.
 */
public class BoardingWork implements BoardingProvider {
    private final GameWorld world;

    public BoardingWork(GameWorld world) {
        this.world = world;
    }

    @Override
    public void collect(BoardingBoard out) {
        Entity ship = world.getPlayer(), craft = world.boarding.boardable(ship);
        if (craft == null) return;
        out.byEva = !world.docking.docked(ship, craft);
        out.craftName = Boarding.name(craft);
        BoardedComponent b = Boarding.of(craft);
        boolean ours = b != null && b.by != null && b.by.get(world.engine.getUuidRegistry()) == ship;
        out.boarding = ours && !b.taken;
        out.state = state(ship, craft, b, ours);
        String refused = world.boarding.refusal(ship, craft);
        out.boardRefusal = refused == null ? "" : refused;
        String noStorm = world.boarding.refusal(ship, craft, true);
        out.stormRefusal = noStorm == null ? "" : noStorm;
        out.salvage = ours && b.taken;
        if (!out.salvage) {
            out.salvageRefusal = out.byEva ? (refused != null ? refused : "Board it on EVA, or latch on to it to strip it")
                    : ShipDisablement.isDisabled(craft)
                    ? "Take it to strip it: send the boarding party across, or strip a ship no one defends"
                    : "";
            if (out.byEva) return;
            // No one aboard defends it: marking a part to strip takes it
            if (!ShipDisablement.isDisabled(craft) || world.boarding.defenders(craft) > 0) return;
            out.salvage = true;
        }
        for (Salvage.Part p : world.salvage.parts(craft)) {
            Part row = out.addPart();
            row.x = p.system.footprint.originX;
            row.y = p.system.footprint.originY;
            row.name = p.name;
            row.value = p.price * Salvage.SALE_SHARE;
            SalvageOrdersComponent.Strip s = world.salvage.strip(ship, craft, row.x, row.y);
            row.marked = s != null;
            row.status = s == null ? "" : status(craft, s);
        }
        // Parts already off and on their way home
        SalvageOrdersComponent orders = ship.getComponent(SalvageOrdersComponent.class);
        if (orders != null) {
            EntityRef ref = EntityRef.of(craft);
            for (SalvageOrdersComponent.Strip s : orders.strips) {
                if (!s.off || !ref.equals(s.craft)) continue;
                Part row = out.addPart();
                row.x = s.x;
                row.y = s.y;
                row.name = world.salvage.partName(s.part);
                row.value = world.salvage.salePrice(s.part);
                row.marked = true;
                row.status = status(craft, s);
            }
        }
        CargoHoldComponent hold = craft.getComponent(CargoHoldComponent.class);
        CargoOrdersComponent cargo = ship.getComponent(CargoOrdersComponent.class);
        if (hold != null) {
            Set<String> held = new TreeSet<>();
            for (CargoHoldComponent.CargoStack s : hold.stacks) {
                if (s != null && s.units > 1e-6) held.add(s.commodity);
            }
            for (String c : held) {
                Cargo row = out.addCargo();
                row.commodity = c;
                row.units = hold.amount(c);
                row.onOrder = cargo == null ? 0 : Math.max(0, cargo.pending(EntityRef.of(craft), c));
            }
        }
    }

    private String state(Entity ship, Entity craft, BoardedComponent b, boolean ours) {
        if (ours && b.taken) return "Taken" + (b.prisoners > 0 ? ", " + b.prisoners + " held" : "");
        if (ours) {
            return String.format(Locale.ROOT, "Boarding: %d of ours aboard, %d of theirs standing",
                    world.boarding.boardersAboard(ship, craft), world.boarding.defenders(craft));
        }
        DisabledComponent.Reason r = ShipDisablement.reason(craft);
        return r == null ? "Fit to fight" : "Disabled: " + r.label;
    }

    private String status(Entity craft, SalvageOrdersComponent.Strip s) {
        if (s.off) return s.inHand ? "carrying it home" : "off, waiting to be carried home";
        float done = world.salvage.progress(craft, s);
        return done > 0 ? String.format(Locale.ROOT, "stripping %.0f%%", 100 * done) : "marked";
    }

    @Override
    public String orderBoarding() {
        return world.boarding.order(world.getPlayer());
    }

    @Override
    public String storm() {
        return world.boarding.storm(world.getPlayer());
    }

    @Override
    public String recall() {
        return world.boarding.recall(world.getPlayer());
    }

    @Override
    public String toggleStrip(int x, int y) {
        return world.salvage.mark(world.getPlayer(), x, y);
    }

    @Override
    public String takeCargo(String commodity) {
        return world.salvage.takeCargo(world.getPlayer(), commodity);
    }
}
