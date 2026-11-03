package com.ringsofiron.uihud.providers;

/**
 * The docking approach, as numbers for a docking display: which port is selected, whether the
 * station has given clearance, and how the port lines up with the one it is going to. Filled once a
 * frame by the game wiring layer from the ship's docking ports and the docking system.
 */
public interface DockingApproachProvider {
    /** Fills {@code out}, already cleared; leaves {@link DockingApproach#present} false for a ship with no port. */
    void read(DockingApproach out);

    /** Where the selected port stands. */
    enum Capture {
        /** Free, with no port to line up with in range. */
        FREE,
        /** Free, lining up with a port in range. */
        APPROACH,
        /** An active collar has hold and is drawing the ships together. */
        CAPTURED,
        /** Latched. */
        DOCKED
    }

    /** What the nearest station said when asked for clearance. */
    enum Clearance {
        /** Not asked, or the port is not armed. */
        NONE,
        GRANTED,
        DENIED,
        /** It has no free port of this size. */
        NO_PORT,
        /** A struck ship's port: it does not refuse, so no one is asked. */
        YIELDS
    }

    final class DockingApproach {
        /** How far guidance looks for a port to line up with, m. */
        public static final float RANGE = 5_000f;

        public boolean present;
        /** The selected port: its number (1-based) of how many, its size ("S", "M", "L"), armed, and its collar's state. */
        public int port, ports;
        public String portSize = "";
        public boolean armed, active, collarTorn, collarJammed;
        public Capture capture = Capture.FREE;

        /** The craft whose port it lines up with, or that it is docked to ("Highport"), or "". */
        public String other = "";
        /** That port's number there (1-based) and size, or 0 and "". */
        public int otherPort;
        public String otherPortSize = "";

        public Clearance clearance = Clearance.NONE;
        /** Why clearance was refused, as the station said it ("" when it gave none). */
        public String refusal = "";
        /** Seconds the clearance has left before the port disarms; NaN with none. */
        public float clearanceLeft = Float.NaN;

        /** Face to face, m; off the port's centreline, m (+ port side as the approaching ship sees it); turn still needed, degrees (+ counter-clockwise). NaN with no port in range. */
        public float range = Float.NaN, lateral = Float.NaN, angleDeg = Float.NaN;
        /** Closing along the port's normal, m/s (− drawing apart), and the fastest closing counted safe at this range. */
        public float closing = Float.NaN, safeSpeed = Float.NaN;
        /** Docked: how hard the latch strains, 0 to 1, and whether it has settled rigid. */
        public float strain = Float.NaN;
        public boolean rigid;

        public void clear() {
            present = false;
            port = ports = 0;
            portSize = "";
            armed = active = collarTorn = collarJammed = false;
            capture = Capture.FREE;
            other = "";
            otherPort = 0;
            otherPortSize = "";
            clearance = Clearance.NONE;
            refusal = "";
            clearanceLeft = Float.NaN;
            range = lateral = angleDeg = Float.NaN;
            closing = safeSpeed = Float.NaN;
            strain = Float.NaN;
            rigid = false;
        }
    }
}
