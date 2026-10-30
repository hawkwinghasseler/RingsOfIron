package com.hawkwing.RingsOfIron.game;

import com.hawkwing.RingsOfIron.wiring.PlayerShipStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.ThreatProvider;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Warnings the player hears and reads wherever they are, posted to the {@link NoticeLog}: propellant
 * running low (below what the trip home takes, when navigation can say, else below a fifth of the
 * tanks), and missiles coming in (the outside view has ui-hud's banner too; on deck this is
 * the only sign). A pirate setting course to intercept is {@link HostileTraffic}'s own notice.
 * Each warns once as it starts and again only after it has cleared.
 */
public final class FlightWarnings {
    /** Below this share of the tanks propellant is low, when there is no estimate of the trip home. */
    public static final float LOW_PROPELLANT = 0.2f;
    /** ...and it is warned of again once the tanks are filled back past this. */
    static final float PROPELLANT_CLEAR = 0.25f;
    /** With an estimate of the trip home, propellant is low below this many times what it takes... */
    public static final double RETURN_MARGIN = 1.25;
    /** ...and it is warned of again once the tanks hold this many times it. */
    static final double RETURN_CLEAR = 1.5;
    /** Frames between fresh estimates of the trip home: each plans a route to every station. */
    static final int RETURN_REFRESH = 60;

    /** What the missile warning's notice starts with. */
    public static final String MISSILE_WARNING = "MISSILE WARNING";

    private final GameWorld world;
    private final PlayerShipStatus status;
    private final CombatThreats threats;
    private final ThreatProvider.ThreatFrame frame = new ThreatProvider.ThreatFrame();
    /**
     * Propellant a return to the nearest friendly station needs, kg, or null to warn at
     * {@link #LOW_PROPELLANT} alone, as for an estimate that isn't finite (no station in reach).
     * Navigation's fuel estimate sets it.
     */
    public DoubleSupplier returnPropellantKg;
    private boolean lowWarned;
    private int inbound;
    private int sinceEstimate = RETURN_REFRESH;
    private double returnKg = Double.NaN;
    /** Most missiles inbound at once since the sky was last clear: a warning goes out only above it. */
    private int peak;
    /** This salvo's warning, revised in place as the count climbs; null while the sky is clear. */
    private NoticeLog.Notice salvo;

    public FlightWarnings(GameWorld world) {
        this.world = world;
        this.status = new PlayerShipStatus(world::getPlayer);
        this.threats = new CombatThreats(world);
    }

    /** Once a frame. */
    public void update() {
        if (world.getPlayer() == null) return;
        ShipStatusProvider.ShipStatus s = status.getShipStatus();
        if (++sinceEstimate >= RETURN_REFRESH) {
            sinceEstimate = 0;
            returnKg = returnKg();
        }
        if (s != null) checkPropellant(s.propellant, s.propellantKg, returnKg);
        frame.clear();
        threats.collect(frame);
        missiles(frame.incoming(), frame.soonestImpact());
    }

    /**
     * {@code n} missiles inbound, the first to land in {@code soonest} s (NaN when unknown). A salvo
     * is everything launched until the sky is clear again, and gets one warning: posted as the first
     * missile comes, and revised in place, in the log too, each time the count passes its peak.
     */
    void missiles(int n, float soonest) {
        if (n == 0) {
            peak = 0;
            salvo = null;
        }
        if (n > inbound && n > peak) {
            peak = n;
            String text = String.format(Locale.ROOT, MISSILE_WARNING + ": %d inbound%s", n,
                    Float.isNaN(soonest) ? "" : String.format(Locale.ROOT, ", impact in %.0f s", soonest));
            salvo = salvo == null ? world.notices.replace(MISSILE_WARNING, text, NoticeLog.Kind.WARNING)
                    : world.notices.revise(salvo, text);
        }
        inbound = n;
    }

    void checkPropellant(float share, double kg) {
        checkPropellant(share, kg, returnKg());
    }

    /**
     * Low propellant against {@code home}, the kg the trip home takes (NaN when unknown): below
     * {@link #RETURN_MARGIN} times it, or below {@link #LOW_PROPELLANT} of the tanks without it.
     */
    void checkPropellant(float share, double kg, double home) {
        if (Float.isNaN(share)) return;
        boolean known = Double.isFinite(home);
        if (known ? kg >= home * RETURN_CLEAR : share >= PROPELLANT_CLEAR) {
            lowWarned = false;
            return;
        }
        if (lowWarned || (known ? kg >= home * RETURN_MARGIN : share >= LOW_PROPELLANT)) return;
        lowWarned = true;
        String said = String.format(Locale.ROOT, "Propellant low: %.0f%% (%,.0f kg) left", share * 100f, kg);
        if (known) said += kg < home ? String.format(Locale.ROOT, ", short of the %,.0f kg the trip home takes", home)
                : String.format(Locale.ROOT, ", little more than the %,.0f kg the trip home takes", home);
        world.notices.post(said, NoticeLog.Kind.WARNING);
    }

    /** Navigation's estimate of the trip home, kg, or NaN when there is none or it isn't finite. */
    private double returnKg() {
        double kg = returnPropellantKg == null ? Double.NaN : returnPropellantKg.getAsDouble();
        return Double.isFinite(kg) ? kg : Double.NaN;
    }

    /**
     * True for a missile warning's notice. The status column leaves it out: the banner over the
     * view already says it, larger and live, and the line under it said the same thing twice. It is
     * still heard, and kept in the notice log.
     */
    public static boolean isMissileWarning(NoticeLog.Notice n) {
        return n.text.startsWith(MISSILE_WARNING);
    }

    /** Missiles coming for the player's ship now. */
    public int inbound() {
        return inbound;
    }

    /** Seconds to the soonest impact, or NaN. */
    public float soonestImpact() {
        return frame.soonestImpact();
    }
}
