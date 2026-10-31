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
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * ui-hud's boarding window from {@link Boarding} and {@link Salvage}: the ship the player's is
 * latched to, its boarding, and once taken, its parts and cargo.
 */
public class BoardingWork implements BoardingProvider {
    private final GameContext ctx;
    private final DockingSystem docking;
    private final Boarding boarding;
    private final Salvage salvage;

    public BoardingWork(GameContext ctx, DockingSystem docking, Boarding boarding, Salvage salvage) {
        this.ctx = ctx;
        this.docking = docking;
        this.boarding = boarding;
        this.salvage = salvage;
    }

    @Override
    public void collect(BoardingBoard out) {
        Entity ship = ctx.player(), craft = boarding.boardable(ship);
        if (craft == null) return;
        out.byEva = !docking.docked(ship, craft);
        out.craftName = Boarding.name(craft);
        BoardedComponent b = Boarding.of(craft);
        boolean ours = b != null && b.by != null && b.by.get(ctx.engine.getUuidRegistry()) == ship;
        out.boarding = ours && !b.taken;
        out.state = state(ship, craft, b, ours);
        String refused = boarding.refusal(ship, craft);
        out.boardRefusal = refused == null ? "" : refused;
        String noStorm = boarding.refusal(ship, craft, true);
        out.stormRefusal = noStorm == null ? "" : noStorm;
        out.salvage = ours && b.taken;
        if (!out.salvage) {
            out.salvageRefusal = out.byEva ? (refused != null ? refused : "Board it on EVA, or latch on to it to strip it")
                    : ShipDisablement.isDisabled(craft)
                    ? "Take it to strip it: send the boarding party across, or strip a ship no one defends"
                    : "";
            if (out.byEva) return;
            // No one aboard defends it: marking a part to strip takes it
            if (!ShipDisablement.isDisabled(craft) || boarding.defenders(craft) > 0) return;
            out.salvage = true;
        }
        for (Salvage.Part p : salvage.parts(craft)) {
            Part row = out.addPart();
            row.x = p.system.footprint.originX;
            row.y = p.system.footprint.originY;
            row.name = p.name;
            row.value = p.price * Salvage.SALE_SHARE;
            SalvageOrdersComponent.Strip s = salvage.strip(ship, craft, row.x, row.y);
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
                row.name = salvage.partName(s.part);
                row.value = salvage.salePrice(s.part);
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
                    boarding.boardersAboard(ship, craft), boarding.defenders(craft));
        }
        DisabledComponent.Reason r = ShipDisablement.reason(craft);
        return r == null ? "Fit to fight" : "Disabled: " + r.label;
    }

    private String status(Entity craft, SalvageOrdersComponent.Strip s) {
        if (s.off) return s.inHand ? "carrying it home" : "off, waiting to be carried home";
        float done = salvage.progress(craft, s);
        return done > 0 ? String.format(Locale.ROOT, "stripping %.0f%%", 100 * done) : "marked";
    }

    @Override
    public String orderBoarding() {
        return boarding.order(ctx.player());
    }

    @Override
    public String storm() {
        return boarding.storm(ctx.player());
    }

    @Override
    public String recall() {
        return boarding.recall(ctx.player());
    }

    @Override
    public String toggleStrip(int x, int y) {
        return salvage.mark(ctx.player(), x, y);
    }

    @Override
    public String takeCargo(String commodity) {
        return salvage.takeCargo(ctx.player(), commodity);
    }
}
