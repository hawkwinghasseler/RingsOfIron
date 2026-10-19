package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.ThreatProvider;

import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * Warnings the player hears and reads wherever they are, posted to the {@link NoticeLog}: propellant
 * running low, and missiles coming in (the outside view has ui-hud's banner too; on deck this is
 * the only sign). A pirate setting course to intercept is {@link HostileTraffic}'s own notice.
 * Each warns once as it starts and again only after it has cleared.
 */
public final class FlightWarnings {
    /** Below this share of the tanks propellant is low. */
    public static final float LOW_PROPELLANT = 0.2f;
    /** ...and it is warned of again once the tanks are filled back past this. */
    static final float PROPELLANT_CLEAR = 0.25f;

    private final GameWorld world;
    private final PlayerShipStatus status;
    private final CombatThreats threats;
    private final ThreatProvider.ThreatFrame frame = new ThreatProvider.ThreatFrame();
    /**
     * Propellant a return to the nearest friendly station needs, kg, or null to warn at
     * {@link #LOW_PROPELLANT} alone. Navigation's fuel estimate sets it.
     */
    public DoubleSupplier returnPropellantKg;
    private boolean lowWarned;
    private int inbound;
    /** Most missiles inbound at once since the sky was last clear: a warning goes out only above it. */
    private int peak;

    public FlightWarnings(GameWorld world) {
        this.world = world;
        this.status = new PlayerShipStatus(world::getPlayer);
        this.threats = new CombatThreats(world);
    }

    /** Once a frame. */
    public void update() {
        if (world.getPlayer() == null) return;
        ShipStatusProvider.ShipStatus s = status.getShipStatus();
        if (s != null) checkPropellant(s.propellant, s.propellantKg);
        frame.clear();
        threats.collect(frame);
        int n = frame.incoming();
        if (n == 0) peak = 0;
        if (n > inbound && n > peak) {
            peak = n;
            float t = frame.soonestImpact();
            // The count rises and falls as missiles launch and point defense takes them: a warning goes
            // out when more are coming than at any time since the sky was clear, and replaces the last
            world.notices.replace("MISSILE WARNING", String.format(Locale.ROOT, "MISSILE WARNING: %d inbound%s", n,
                    Float.isNaN(t) ? "" : String.format(Locale.ROOT, ", impact in %.0f s", t)), NoticeLog.Kind.WARNING);
        }
        inbound = n;
    }

    void checkPropellant(float share, double kg) {
        if (Float.isNaN(share)) return;
        if (share >= PROPELLANT_CLEAR) {
            lowWarned = false;
            return;
        }
        if (lowWarned || share >= LOW_PROPELLANT) return;
        if (returnPropellantKg != null && kg >= returnPropellantKg.getAsDouble()) return;
        lowWarned = true;
        world.notices.post(String.format(Locale.ROOT, "Propellant low: %.0f%% (%,.0f kg) left", share * 100f, kg),
                NoticeLog.Kind.WARNING);
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
