package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.FurnishingType;
import com.ringsofiron.interiorgrid.Furnishings;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.util.Locale;

/**
 * Putting furniture down on the player's deck from the yard window, at an exact spot and angle:
 * the piece follows the pointer, turns with the wheel (or Q and E), and a click orders it there
 * ({@link FurnishingStore#order}). Arranging picks up a piece aboard with a click and puts it down
 * with another ({@link FurnishingStore#move}); X hands the piece under the pointer back to the yard.
 * Right click or Esc stops. Headless: the game screen feeds it the pointer in the deck's tiles and
 * draws {@link #ghost()}.
 */
public final class FurnishingPlacement {
    /** Degrees a turn of the wheel or a press of Q or E turns the piece. */
    public static final float TURN_STEP = 15f;
    /** Degrees a fine turn (with Shift) turns it. */
    public static final float FINE_STEP = 1f;

    private final GameWorld world;
    private final Entity ship, station;
    /** The kind being bought; null while arranging. */
    private final FurnishingType buying;
    /** The piece picked up while arranging, or null. */
    private Furnishing held;
    private float x = Float.NaN, y = Float.NaN, rotation;
    private boolean done;

    private FurnishingPlacement(GameWorld world, Entity ship, Entity station, FurnishingType buying) {
        this.world = world;
        this.ship = ship;
        this.station = station;
        this.buying = buying;
    }

    /** Placing a new {@code type} on {@code ship}, docked at {@code station}. */
    public static FurnishingPlacement buy(GameWorld world, Entity ship, Entity station, FurnishingType type) {
        return new FurnishingPlacement(world, ship, station, type);
    }

    /** Moving the pieces aboard {@code ship} about. */
    public static FurnishingPlacement arrange(GameWorld world, Entity ship, Entity station) {
        return new FurnishingPlacement(world, ship, station, null);
    }

    public Entity ship() {
        return ship;
    }

    public boolean isBuying() {
        return buying != null;
    }

    public boolean isDone() {
        return done;
    }

    /** The pointer is over ({@code gx}, {@code gy}) on the ship's deck, tiles from the grid's corner. */
    public void point(float gx, float gy) {
        x = gx;
        y = gy;
    }

    /** Turns the piece by {@code degrees}, counter-clockwise. */
    public void turn(float degrees) {
        rotation = ((rotation + degrees) % 360f + 360f) % 360f;
    }

    public float rotation() {
        return rotation;
    }

    /** The kind under the pointer now: the one being bought, or the one picked up; null with nothing in hand. */
    public FurnishingType inHand() {
        return buying != null ? buying : held == null ? null : held.type;
    }

    /** The piece in hand where it would go, or null with nothing in hand or the pointer off the deck. */
    public Furnishing ghost() {
        FurnishingType t = inHand();
        if (t == null || Float.isNaN(x)) return null;
        return Furnishing.at(t, x, y, rotation);
    }

    /** The piece picked up while arranging, still standing where it was; null if none. */
    public Furnishing held() {
        return held;
    }

    /** Why the piece in hand can't go where the pointer is, or null if it can (or nothing is in hand). */
    public String refusal() {
        Furnishing g = ghost();
        if (g == null) return null;
        ShipGridComponent grid = grid();
        if (grid == null) return "Nothing to put it on";
        if (held != null) {
            // Its own old spot doesn't count against it
            Furnishings.remove(grid, held);
            try {
                return Furnishings.refusal(grid, g, spots());
            } finally {
                Furnishings.restore(grid, held);
            }
        }
        return Furnishings.refusal(grid, g, spots());
    }

    /** The furnishing under the pointer aboard, or null. */
    public Furnishing hovered() {
        ShipGridComponent grid = grid();
        return grid == null || Float.isNaN(x) ? null : Furnishings.at(grid, x, y);
    }

    /** A left click: order the piece here, pick one up, or put the one in hand down. Returns what to tell the player, or null. */
    public String click() {
        if (Float.isNaN(x) || !stillDocked()) return null;
        if (buying != null) return world.furnishings.order(ship, station, buying.id, x, y, rotation);
        if (held == null) {
            held = hovered();
            if (held == null) return "Click a piece aboard to move it";
            rotation = held.rotation;
            return "Moving " + held.type.the() + ": click where it goes";
        }
        String said = world.furnishings.move(ship, station, held, x, y, rotation);
        if (said.endsWith(" moved")) held = null;
        return said;
    }

    /** X: the yard takes the piece under the pointer (or in hand) back. */
    public String sell() {
        if (buying != null || !stillDocked()) return null;
        Furnishing f = held != null ? held : hovered();
        if (f == null) return null;
        held = null;
        return world.furnishings.sell(ship, station, f);
    }

    /** Right click or Esc: puts back what is in hand, or stops. Returns what to tell the player, or null. */
    public String back() {
        if (held != null) {
            held = null;
            return null;
        }
        done = true;
        return null;
    }

    /** What the player can do now, for the hint line. */
    public String hint() {
        Furnishing hover = buying == null && held == null ? hovered() : null;
        if (buying != null) {
            return String.format(Locale.ROOT, "Placing %s (%,.0f cr): click to order it there, wheel or Q/E turns it (Shift: finely), right click or Esc stops",
                    buying.the(), world.furnishings.price(buying, station));
        }
        if (held != null) return "Moving " + held.type.the() + ": click to put it down, wheel or Q/E turns it, right click puts it back";
        if (hover != null) {
            return String.format(Locale.ROOT, "%s: click to move it, X sells it back for %,.0f cr",
                    FurnishingStore.capitalized(hover.type.the()), FurnishingStore.buyBack(hover));
        }
        return "Arranging: click a piece aboard to move it, X over one sells it back, right click or Esc stops";
    }

    /** Ends placing once the ship leaves the yard. */
    public boolean stillDocked() {
        boolean docked = station != null && world.yard.canWork(ship, station);
        if (!docked) done = true;
        return docked;
    }

    private ShipGridComponent grid() {
        return ship.getComponent(ShipGridComponent.class);
    }

    private java.util.List<Furnishing> spots() {
        java.util.List<Furnishing> out = new java.util.ArrayList<>();
        for (FurnishingStore.Order o : world.furnishings.pending(ship)) out.add(o.furnishing);
        return out;
    }

    /** The pointer at world point ({@code wx}, {@code wy}), meters, in the deck's tiles: {gx, gy}. */
    public static float[] deckPoint(ShipGridComponent grid, com.badlogic.gdx.physics.box2d.Body body, float wx, float wy) {
        com.badlogic.gdx.math.Vector2 l = WorldDebug.toLocal(body, wx, wy, new com.badlogic.gdx.math.Vector2());
        return new float[]{(l.x - grid.gridOriginLocal.x) / grid.tileSize, (l.y - grid.gridOriginLocal.y) / grid.tileSize};
    }
}
