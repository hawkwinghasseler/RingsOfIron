package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.interiorgrid.GridLink;
import com.ringsofiron.interiorgrid.ShipGridComponent;
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
    /** Seconds a part's reach is kept before it is looked at again. */
    static final double REACH_SECONDS = 2;
    private final ObjectMap<Long, Boolean> reach = new ObjectMap<>();
    private Entity reachOf;
    private int reachLinks;
    private double reachAt = Double.NEGATIVE_INFINITY;

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
        boolean docked = docking.docked(ship, craft);
        boolean alongside = !docked && boarding.alongside(ship, craft);
        out.byEva = !docked;
        out.craftName = Boarding.name(craft);
        BoardedComponent b = Boarding.of(craft);
        boolean ours = b != null && b.by != null && b.by.get(ctx.engine.getUuidRegistry()) == ship;
        out.boarding = ours && !b.taken;
        out.taken = ours && b.taken;
        out.disabled = ShipDisablement.isDisabled(craft);
        out.prisoners = out.taken ? b.prisoners : 0;
        out.aboard = ours ? boarding.boardersAboard(ship, craft) : 0;
        out.defenders = out.taken ? 0 : boarding.defenders(craft);
        out.state = state(ship, craft, b, ours);
        String refused = boarding.refusal(ship, craft);
        out.boardRefusal = refused == null ? "" : refused;
        String noStorm = boarding.refusal(ship, craft, true);
        out.stormRefusal = noStorm == null ? "" : noStorm;
        out.salvage = ours && b.taken;
        if (!out.salvage) {
            boolean disabled = out.disabled;
            // No one aboard defends it: marking a part to strip takes it, latched on or alongside
            if (!disabled || boarding.defenders(craft) > 0 || out.byEva && !alongside) {
                out.salvageRefusal = !disabled ? (out.byEva && refused != null ? refused : "")
                        : boarding.defenders(craft) > 0
                        ? "Take it to strip it: send the boarding party across, or strip a ship no one defends"
                        : "Stand off alongside it to strip its hull on EVA, or latch on to it";
                return;
            }
            out.salvage = true;
        }
        if (out.byEva && !alongside) out.salvageRefusal = "Stand off alongside it to strip its hull on EVA";
        for (Salvage.Part p : salvage.parts(craft)) {
            Part row = out.addPart();
            row.x = p.system.footprint.originX;
            row.y = p.system.footprint.originY;
            row.name = p.name;
            row.value = p.price * Salvage.SALE_SHARE;
            SalvageOrdersComponent.Strip s = salvage.strip(ship, craft, row.x, row.y);
            row.marked = s != null;
            if (s != null) {
                row.status = salvage.status(ship, craft, s);
                row.toggleable = Salvage.cancellable(s);
            } else if (out.byEva && !salvage.onHull(craft, row.x, row.y)) {
                row.status = "inside the hull: latch on to strip it";
                row.toggleable = false;
                row.reach = "latch on";
                row.reachWhy = row.status;
            } else if (!out.byEva && !reachable(ship, craft, row.x, row.y)) {
                // The game finds this out once the strip is ordered; the board says it before
                row.reach = "EVA only";
                row.reachWhy = "can't be reached from inside: stand off alongside to strip it on EVA";
            }
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
                row.off = true;
                row.status = salvage.status(ship, craft, s);
                row.toggleable = false;
            }
        }
        // Cargo goes across a dock only
        CargoHoldComponent hold = docked ? craft.getComponent(CargoHoldComponent.class) : null;
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

    /**
     * Whether the part at (x, y) on {@code craft} can be walked to from the dock: the path search
     * runs again only when the craft or the dock changes, or every {@link #REACH_SECONDS}, not on
     * every collect.
     */
    private boolean reachable(Entity ship, Entity craft, int x, int y) {
        double now = ctx.engine.getSimSeconds();
        int links = links(ship, craft);
        if (craft != reachOf || links != reachLinks || now - reachAt > REACH_SECONDS || now < reachAt) {
            reach.clear();
            reachOf = craft;
            reachLinks = links;
            reachAt = now;
        }
        long key = ((long) x << 32) | (y & 0xffffffffL);
        Boolean known = reach.get(key);
        if (known == null) {
            known = salvage.reachable(ship, craft, x, y);
            reach.put(key, known);
        }
        return known;
    }

    /** The links between {@code ship} and {@code craft}, and how many can be crossed, as one number that changes with either. */
    private static int links(Entity ship, Entity craft) {
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        if (g == null) return 0;
        int n = 0, open = 0;
        for (GridLink l : g.links) {
            if (l.otherShip != craft) continue;
            n++;
            if (l.canCross()) open++;
        }
        return n * 64 + open;
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
