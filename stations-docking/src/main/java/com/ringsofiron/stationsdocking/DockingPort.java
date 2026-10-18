package com.ringsofiron.stationsdocking;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.joints.DistanceJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.ringsofiron.interiorgrid.MountFootprint.MountSize;

/**
 * One docking port of a ship or station: where it sits on the body, its latch collar, and what
 * it is latched to. Plain fields are saved with the entity; the Box2D parts of an active collar
 * are not (a collar that was moving when the game saved starts again retracted).
 *
 * <p>Geometry is in body-local meters: {@link #edgeX}, {@link #edgeY} is the middle of the port's
 * outer hull edge and {@link #normalX}, {@link #normalY} points out into space. The collar sticks
 * {@link #collarDepth} (half a tile) past the edge, so its outer face is at
 * {@code edge + normal * collarDepth}. Two latched ports have their collar faces together.
 */
public class DockingPort {
    public enum State {
        /** Free. */
        IDLE,
        /** An active collar has hold of the other port and is drawing the craft together. */
        CAPTURED,
        /** Latched: the craft are welded together at this port. */
        DOCKED
    }

    public MountSize size = MountSize.S;
    /** Has a telescoping collar on springs; passive ports only have magnets. */
    public boolean active;
    public float edgeX, edgeY;
    public float normalX, normalY = 1f;
    /** Port width along the hull and the opening's width, meters. */
    public float width, openingWidth;
    public float collarDepth;
    /** Bottom-left tile of the port's footprint on the owner's grid; -1 for a port with no grid. */
    public int gridX = -1, gridY = -1;

    /** Magnets on (and, for an active port, collar out). Unpowered ports cannot arm. */
    public boolean armed;
    /**
     * UUID of the craft an active port was armed for when it granted docking clearance; its collar
     * only reaches for that craft. Null when armed by its own crew (reaches for anyone).
     */
    public String armedFor;
    /** Sim second the clearance runs out; the port disarms then unless it has latched. */
    public double clearanceUntil = Double.NaN;
    /**
     * An armed active port keeps its collar in and works like a passive one: set when it was armed
     * for a craft whose own active collar does the reaching.
     */
    public boolean holdCollar;
    /** 1 for a whole collar, 0 once torn off. A torn port cannot arm or latch until repaired. */
    public float collarIntegrity = 1f;

    public State state = State.IDLE;
    /** The craft and port this one is latched to, while not {@link State#IDLE}. */
    public String partnerUuid;
    public int partnerPort = -1;
    /** The other side gave docking clearance (both sides had armed for this dock). */
    public boolean permitted;
    /**
     * This port's own side agreed to the dock (it was armed, by its crew or for the other craft),
     * so its door opens while latched. A port caught unarmed, or a station that refused
     * clearance, keeps its door shut.
     */
    public boolean consented;

    /** How far the active collar is out now, meters. Runtime only. */
    public transient float extension;
    /** The collar head of an active port while it is out, and the joints that carry it. */
    public transient Body head;
    public transient PrismaticJoint guide;
    public transient DistanceJoint spring;
    /** The weld from the collar head to the captured port's craft. */
    public transient Joint capture;
    /** Spring length the collar is driven towards, meters. */
    public transient float springTarget;

    public DockingPort() {
    }

    public DockingTuning tuning() {
        return DockingTuning.of(size);
    }

    public boolean intact() {
        return collarIntegrity > 0f;
    }

    public boolean isFree() {
        return state == State.IDLE;
    }

    /** Collar face middle, body-local x. */
    public float faceX() {
        return edgeX + normalX * collarDepth;
    }

    /** Collar face middle, body-local y. */
    public float faceY() {
        return edgeY + normalY * collarDepth;
    }

    /** Clears the latch record: free, no partner. */
    void release() {
        state = State.IDLE;
        partnerUuid = null;
        partnerPort = -1;
        permitted = false;
        consented = false;
    }

    @Override
    public String toString() {
        return size + (active ? " active" : " passive") + " port at " + edgeX + "," + edgeY + " " + state;
    }
}
