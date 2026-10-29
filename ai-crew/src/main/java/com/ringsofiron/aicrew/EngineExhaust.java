package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.EngineLayout;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

/**
 * The exhaust of a ship's main engines, as it falls on crew out on EVA ({@link EvaSystem}) and on
 * their lines.
 *
 * <p>Each burning engine's exhaust leaves the nozzle as a cone, as wide as the nozzle at its mouth
 * and spreading {@link #SPREAD} either side of its axis. The engine's thrust is the momentum the
 * exhaust carries out through every cross-section of that cone, so the push on whatever is in it is
 * the thrust's share falling on it: the thrust times its broadside ({@link #BROADSIDE} for a crew
 * member) over the cone's cross-section where it is, outward from the cone's tip. Right at the
 * nozzle of the corvette's engines that is hundreds of kilonewtons; a hundred meters back, a few
 * hundred newtons. The gas is hot: past what a suit shrugs off ({@link #BURN_FROM}) it burns, the
 * harder the push the worse ({@link #BURN_PER_KN}), and a line through it where its push is past
 * {@link #LINE_BURNS} burns through.
 *
 * <p>An engine's thrust is the ship's main engine thrust at the stick's throttle, shared among its
 * working engines by the area of their nozzles and their health, as near as the crew need it (the
 * game's own reckoning of each engine's thrust goes by its component). Only engines fitted as
 * systems in engine mounts; a hull's own engines drawn in its art have no exhaust here.
 */
public final class EngineExhaust {
    /** How far the exhaust spreads either side of the nozzle's axis, radians: 15 degrees. */
    public static final float SPREAD = (float) Math.toRadians(15.0);
    /** A crew member's broadside to the exhaust in an EVA suit, square meters. */
    public static final float BROADSIDE = 0.6f;
    /** A push from the exhaust a suit shrugs off, newtons: its heat goes with its push, and past this it burns. */
    public static final float BURN_FROM = 300f;
    /** Health a second burned away for each kilonewton of push past {@link #BURN_FROM}. */
    public static final float BURN_PER_KN = 15f;
    /** Exhaust pushing harder than this, pascals (newtons a square meter), burns through a line. */
    public static final float LINE_BURNS = 20000f;

    private static final float TAN = (float) Math.tan(SPREAD);

    /** Each burning engine: where the middle of its nozzle's mouth is (tiles), which way it points (unit), half its mouth's width (m), thrust (N). */
    private float[] mouthX = new float[4], mouthY = new float[4], axisX = new float[4], axisY = new float[4],
            half = new float[4], thrust = new float[4];
    private int count;
    private float tile = 1f;

    /** Reads how hard {@code ship}'s engines burn now. False if none is burning. */
    public boolean read(Entity ship, ShipGridComponent grid) {
        return read(ship, grid, -1f);
    }

    /**
     * Reads {@code ship}'s engines as they would burn with the stick at {@code throttle} (0 to 1),
     * or as it is if {@code throttle} is negative: for what a burn would do. False if none would burn.
     */
    public boolean read(Entity ship, ShipGridComponent grid, float throttle) {
        count = 0;
        if (ship == null || grid == null) return false;
        tile = grid.tileSize;
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        ThrusterComponent thruster = ship.getComponent(ThrusterComponent.class);
        if (controls == null || thruster == null || thruster.isOutOfFuel() || thruster.forwardThrust <= 0f) return false;
        float stick = Math.min(1f, throttle >= 0f ? throttle : controls.forward);
        if (stick <= 0f) return false;
        float shares = 0f;
        for (int i = 0; i < grid.mounts.size; i++) {
            MountFootprint m = grid.mounts.get(i);
            SubsystemComponent s = m.installed == null ? null : m.installed.getComponent(SubsystemComponent.class);
            if (s == null || s.type != SubsystemComponent.SubsystemType.ENGINE || !s.isOperational || !s.switchedOn || s.isDisabled()) {
                continue;
            }
            float breadth = EngineLayout.breadth(m);
            float share = breadth * breadth * Math.max(0f, Math.min(1f, s.health));
            if (share <= 0f) continue;
            add(m, breadth * tile * 0.5f, share);
            shares += share;
        }
        if (shares <= 0f) {
            count = 0;
            return false;
        }
        float total = stick * thruster.forwardThrust;
        for (int i = 0; i < count; i++) thrust[i] = total * thrust[i] / shares;
        return true;
    }

    private void add(MountFootprint m, float halfWidth, float share) {
        if (count == mouthX.length) {
            int n = count * 2;
            mouthX = java.util.Arrays.copyOf(mouthX, n);
            mouthY = java.util.Arrays.copyOf(mouthY, n);
            axisX = java.util.Arrays.copyOf(axisX, n);
            axisY = java.util.Arrays.copyOf(axisY, n);
            half = java.util.Arrays.copyOf(half, n);
            thrust = java.util.Arrays.copyOf(thrust, n);
        }
        Side n = EngineLayout.nozzle(m);
        float x0 = m.originX, y0 = m.originY, x1 = x0 + m.width(), y1 = y0 + m.height();
        // The mouth is the footprint's edge on the nozzle's side
        mouthX[count] = n == Side.RIGHT ? x1 : n == Side.LEFT ? x0 : (x0 + x1) * 0.5f;
        mouthY[count] = n == Side.TOP ? y1 : n == Side.BOTTOM ? y0 : (y0 + y1) * 0.5f;
        axisX[count] = n.dx;
        axisY[count] = n.dy;
        half[count] = halfWidth;
        thrust[count] = share;
        count++;
    }

    /** True if the last {@link #read} found an engine burning. */
    public boolean burning() {
        return count > 0;
    }

    /**
     * How hard the exhaust of the engines {@link #read} pushes at tile point ({@code x}, {@code y}),
     * pascals: newtons on each square meter of broadside, 0 outside every cone. Its direction, a
     * unit vector along the ship's axes, goes in {@code dir} if that isn't null.
     */
    public float flux(float x, float y, float[] dir) {
        float sx = 0f, sy = 0f;
        for (int i = 0; i < count; i++) {
            float px = (x - mouthX[i]) * tile, py = (y - mouthY[i]) * tile;
            float along = px * axisX[i] + py * axisY[i];
            if (along < 0f) continue;
            float across = Math.abs(px * axisY[i] - py * axisX[i]);
            float r = half[i] + along * TAN;
            if (across > r) continue;
            float q = thrust[i] / ((float) Math.PI * r * r);
            // Outward from the cone's tip, behind the mouth
            float back = half[i] / TAN;
            float ox = px + axisX[i] * back, oy = py + axisY[i] * back;
            float len = (float) Math.sqrt(ox * ox + oy * oy);
            sx += q * ox / len;
            sy += q * oy / len;
        }
        float q = (float) Math.sqrt(sx * sx + sy * sy);
        if (dir != null) {
            dir[0] = q > 0f ? sx / q : 0f;
            dir[1] = q > 0f ? sy / q : 0f;
        }
        return q;
    }

    /** The push on a crew member at tile point ({@code x}, {@code y}), newtons; see {@link #flux}. */
    public float push(float x, float y, float[] dir) {
        return flux(x, y, dir) * BROADSIDE;
    }

    /** True if a crew member at tile point ({@code x}, {@code y}) would be burned there: their push past {@link #BURN_FROM}. */
    public boolean burns(float x, float y) {
        return count > 0 && push(x, y, null) > BURN_FROM;
    }

    /**
     * True if a line laid along {@code rope} (x, y pairs, tiles, the reel's end first) runs through
     * the exhaust where it would burn through: at a point of it, or halfway between two, past the reel.
     */
    public boolean burnsThrough(float[] rope) {
        if (count == 0 || rope == null) return false;
        for (int i = 2; i + 1 < rope.length; i += 2) {
            if (flux(rope[i], rope[i + 1], null) > LINE_BURNS
                    || flux((rope[i] + rope[i - 2]) * 0.5f, (rope[i + 1] + rope[i - 1]) * 0.5f, null) > LINE_BURNS) {
                return true;
            }
        }
        return false;
    }

    /** Health a second a push of {@code push} newtons burns away. */
    public static float burn(float push) {
        return Math.max(0f, push - BURN_FROM) / 1000f * BURN_PER_KN;
    }
}
