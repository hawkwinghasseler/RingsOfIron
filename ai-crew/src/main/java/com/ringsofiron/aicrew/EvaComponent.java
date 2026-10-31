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
 * <p>Crossing to another craft ({@link #crossing}), untethered on the jetpack, the craft they are
 * making for is the one they count as aboard ({@link GridPositionComponent#ship}): its frame is
 * theirs from the moment they push off, its hull the one they catch hold of, and {@link #port} its
 * port they go in by. A hostile craft's airlock door is forced from outside ({@link #breaking}).
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
    /** Seconds off the hull with no line: adrift. */
    public float adrift;
    /**
     * Adrift with no way back on their own, and said so ({@link EvaEvent.Kind#ADRIFT}): their pack
     * can't bring them back from where they are (or is empty), or they are out cold. They wait for
     * the ship to come back for them, and still jet back if it comes close enough. Cleared once they
     * catch hold of the hull again or are taken in tow.
     */
    public boolean stranded;
    /** The goal can't be reached: the line will go no further, or the hull has no way round there. */
    public boolean outOfReach;
    /** Torn off the hull and not yet caught hold again. */
    public boolean fell;
    /** Crawl the whole way: no jetpack hops (their behavior saving the fuel). */
    public boolean noHops;

    /** Crossing to the craft they count as aboard from another, untethered: in by {@link #port} there. */
    public boolean crossing;
    /** That port's outer door is to be forced from outside before they can go in: a hostile craft's. */
    public boolean breaking;
    /** The door is forced open: they go straight into the chamber without a cycle. */
    public boolean forcedIn;
    /**
     * Over on another craft's hull to take a part off it ({@link HullStripTask}): they work there
     * rather than go in, and once the work is done or called off, head back across home.
     */
    public boolean salvaging;
    /**
     * The craft they push off for once out of the airlock and over to the side of the hull facing it,
     * and the port they go in by there; null on an EVA that stays on the hull. Not saved: a load ends
     * a trip out before it leaves the hull.
     */
    public transient com.badlogic.ashley.core.Entity crossTo;
    public transient MountFootprint crossPort;
    public transient boolean crossBreaking;

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
    /** How far past {@link EvaSystem#LINE_RATED} the line has been pulled, newton-seconds ({@link EvaSystem#LINE_GIVE}). */
    public transient float lineStrain;
    /** Seconds before they can catch hold of the hull again after a fall. */
    public transient float regrab;
    /** Seconds the line has been slack while the reel is locked. */
    public transient float slackTime;
    /** The hull the {@link #path} was found on. */
    public transient com.ringsofiron.interiorgrid.HullSurface pathHull;
    /** Firing the jetpack this step; pulling themselves in along the line this step. For the animation. */
    public transient boolean jetting, climbing;
    /**
     * Adrift and holding off the jetpack this step: it can't bring them back from here, or their
     * ship is under way harder than it could follow. {@link #shortOfFuel} for the first.
     */
    public transient boolean waiting, shortOfFuel;
    /** Their suit's air last step, seconds, to see it run out; negative before the first. */
    public transient float airSeen = -1f;
    /**
     * Meters of line it takes to reach them adrift from the nearest port that can cycle (round the
     * hull to the ring tile nearest them, then straight out), worked out now and then; {@code
     * Float.MAX_VALUE} for none. See {@link EvaSystem#retrievable}.
     */
    public transient float reach = Float.MAX_VALUE;
    /** Seconds before {@link #reach} is worked out again. */
    public transient float reachLook;
    /** Off the hull to take hold of this shipmate adrift ({@link RetrieveTask}), set by their behavior; null otherwise. */
    public transient com.badlogic.ashley.core.Entity chase;
    /** The shipmate adrift they have taken hold of and are bringing in, or null. */
    public transient com.badlogic.ashley.core.Entity towing;
    /** Adrift, the shipmate who has taken hold of them and is bringing them in, or null. */
    public transient com.badlogic.ashley.core.Entity heldBy;
    /** The push of their ship's main engines' exhaust on them last step, newtons ({@link EngineExhaust}); 0 out of it. */
    public transient float exhaust;
    /** Caught in the exhaust and said so ({@link EvaEvent.Kind#EXHAUST}); cleared once well out of it. */
    public transient boolean scorched;
    /** On the hull, holding back from crawling on into the exhaust of an engine burning. */
    public transient boolean burnWait;
    /** Which way the jetpack pushed them last, a unit vector in the ship's frame: its puffs go the other way. */
    public transient float jetX, jetY;
    /**
     * The mass of the crate they carry ({@link CrewMemberComponent#crate}), kg, 0 with none: it moves
     * with them, so their hold, their line and their jetpack bear it too. Kept by {@link EvaSystem}.
     */
    public transient float cargo;

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
