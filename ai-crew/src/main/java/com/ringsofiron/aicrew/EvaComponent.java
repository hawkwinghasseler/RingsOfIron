package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.MountFootprint;

/**
 * A crew member on an EVA ({@link EvaSystem}): from stepping into an airlock's chamber on the way
 * out until the hatch opens behind them on the way back in.
 *
 * <p>Outside, they cling to the hull ({@link Phase#ON_HULL}) at a tile of its outer ring ({@link
 * com.ringsofiron.interiorgrid.HullSurface}), crawling round it hand over hand, or they are off it
 * ({@link Phase#FREE}): on a jetpack hop, swinging on their tether, or adrift. Off the hull they are
 * a point mass in the physics world's frame; on it, they go where the hull goes for as long as
 * their grip holds. The tether runs from the airlock's reel, beside its outer door, and is a rope of
 * {@link EvaSystem#ROPE_SEGMENTS} segments that drapes, wraps round the hull and goes taut.
 *
 * <p>Saved with the crew member: the phase, where they are and how fast they are going relative to
 * their ship (so the save doesn't depend on the physics world's frame), the line out and the port.
 * The world-frame state and the rope's shape are rebuilt after a load.
 */
public class EvaComponent implements Component {

    /** Where on the EVA a crew member is. */
    public enum Phase {
        /** In the chamber of the airlock they are going out through, waiting for the door. */
        LEAVING,
        /** Clinging to the hull: crawling along it, or holding still to work. */
        ON_HULL,
        /** Off the hull: on a jetpack hop, on the end of their tether, or adrift. */
        FREE,
        /** Back in the chamber, waiting for the air and the hatch. */
        RETURNING
    }

    public Phase phase = Phase.LEAVING;
    /** Index in the ship's {@code grid.mounts} of the docking port they went out through. */
    public int port = -1;
    /** That port, linked from {@link #port}. */
    public transient MountFootprint portMount;

    /** The ring tile they cling at, or last clung at. */
    public int ringX, ringY;
    /** Where they are relative to their ship, tiles from its grid's corner (the hug point while on the hull). */
    public float rx, ry;
    /** How fast they move relative to their ship, tiles a second along its axes. */
    public float rvx, rvy;
    /** Which way they face, radians in the ship's frame: along their crawl, or toward where they fly. */
    public float facing;

    /** The ring tile they are making for, set each step by their behavior; {@link #NONE} to hold still. */
    public int goalX = NONE, goalY = NONE;
    /** Fly to the goal on the jetpack rather than crawl to it (once off the hull). */
    public boolean hop;
    /** On the way back to the airlock. */
    public boolean returning;
    /** Crawling progress for the animation, tiles crawled. */
    public float crawled;

    /** Still clipped to the reel. */
    public boolean tethered = true;
    /** Meters of line out of the reel. */
    public float lineOut;
    /** The reel has locked against a sudden pull, like a seatbelt; it frees once the line goes slack. */
    public boolean reelLocked;
    /** The line's pull, newtons, last step. */
    public float tension;
    /** How far past their grip their hold on the hull has been pulled, newton-seconds ({@link EvaSystem#GRIP_SLIP}). */
    public float strain;
    /** Seconds adrift with no line and no way back. */
    public float adrift;
    /** The goal can't be reached: the line will go no further, or the hull has no way round there. */
    public boolean outOfReach;
    /** Torn off the hull and not yet caught hold again. */
    public boolean fell;
    /** Crawl the whole way: no jetpack hops (their behavior saving the fuel). */
    public boolean noHops;

    /** The ring path being crawled, tiles, the current one left out. Rebuilt as needed. */
    public final transient Array<int[]> path = new Array<>();
    /** How far along the path's first step, 0 to 1. */
    public transient float stepDone;
    /** How far short of where the crawl puts them they caught hold, tiles; closed at crawling speed. */
    public transient float slideX, slideY;
    /** World-frame state, meters and m/s in the physics world's frame; rebuilt from {@link #rx} etc. when not {@link #worldValid}. */
    public transient float wx, wy, vx, vy;
    public transient boolean worldValid;
    /** The ship's physics body that world-frame state was worked in (null for a ship without one). */
    public transient com.badlogic.gdx.physics.box2d.Body frameBody;
    /** The hull point's world velocity last step, for its acceleration. */
    public transient float hullVx, hullVy;
    public transient boolean hullVValid;
    /** Their ship's velocity and spin last step, m/s and rad/s, for how hard its hull drags while they are off it. */
    public transient float shipVx, shipVy, shipOmega;
    public transient boolean shipVValid;
    /** Their ship's acceleration and its spin's, smoothed over {@link EvaSystem#DRAG_TAU}: m/s2 and rad/s2. */
    public transient float shipAx, shipAy, shipAlpha;
    /** The rope's points in the world frame, x then y, the reel first and the crew member last; null until laid out. */
    public transient float[] rope, ropePrev;
    /** The line's stretch last step, meters; and smoothed over a few steps, which its pull goes by. */
    public transient float stretch, stretchF;
    /** Seconds before they can catch hold of the hull again after a fall. */
    public transient float regrab;
    /** Seconds the line has been slack while the reel is locked. */
    public transient float slackTime;
    /** The hull the {@link #path} was found on. */
    public transient com.ringsofiron.interiorgrid.HullSurface pathHull;
    /** Firing the jetpack this step; pulling themselves in along the line this step. For the animation. */
    public transient boolean jetting, climbing;
    /** Which way the jetpack pushed them last, a unit vector in the ship's frame: its puffs go the other way. */
    public transient float jetX, jetY;

    /** "No goal". */
    public static final int NONE = Integer.MIN_VALUE;

    public EvaComponent() {
    }

    public EvaComponent(int port, MountFootprint portMount) {
        this.port = port;
        this.portMount = portMount;
    }

    /** True once out of the airlock: on the hull or off it. */
    public boolean outside() {
        return phase == Phase.ON_HULL || phase == Phase.FREE;
    }

    /** True while making for {@link #goalX}, {@link #goalY}. */
    public boolean hasGoal() {
        return goalX != NONE;
    }

    /** True when on the hull at the goal, done crawling. */
    public boolean atGoal() {
        return phase == Phase.ON_HULL && hasGoal() && ringX == goalX && ringY == goalY && path.size == 0;
    }
}
