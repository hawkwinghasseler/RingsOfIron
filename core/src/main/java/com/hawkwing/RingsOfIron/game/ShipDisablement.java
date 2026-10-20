package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.damage.IonStun;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.function.DoubleSupplier;

/**
 * Decides which ships in the bubble are disabled (GDD section 5, Technical Design Decisions
 * section 6) and puts a {@link DisabledComponent} on them. Every {@link #INTERVAL} sim seconds
 * each ship is judged, in this order:
 *
 * <ol>
 *   <li>hull integrity (the mean over its hull tiles) below {@link #WRECK_HULL}: a wreck, for good;</li>
 *   <li>already surrendered or abandoned: stays so;</li>
 *   <li>hull integrity below {@link #DISABLED_HULL};</li>
 *   <li>it has reactors and none is running;</li>
 *   <li>it has crew of its own aboard and none of them is conscious;</li>
 *   <li>an NPC ship in a fight that is losing it surrenders (see {@link MoraleComponent}):
 *       hull below its surrender line, its own crew mostly down, or every gun silenced.</li>
 * </ol>
 *
 * A disabled NPC ship stops: its combat and route pilots are taken off, thrust cut, guns held and
 * its sensor lock dropped. Its HUD marker is greyed and says why. Combat keeps hitting a disabled
 * ship like any other, so enough damage turns it into a wreck, whose reactors go cold. The player's
 * own ship keeps its controls; what is left of it is theirs to fly, or to abandon.
 *
 * <p>Stations are never judged. A ship whose crew is not simulated (no crew aboard) is judged on
 * hull and power alone.
 */
public class ShipDisablement extends EntitySystem {
    /** Sim seconds between judgements. */
    public static final double INTERVAL = 0.5;
    /** Mean hull integrity below which a ship is disabled. */
    public static final float DISABLED_HULL = 0.45f;
    /** Mean hull integrity below which a ship breaks into a wreck. */
    public static final float WRECK_HULL = 0.25f;
    /** Default hull integrity below which an NPC in a fight surrenders. */
    public static final float SURRENDER_HULL = 0.55f;
    /** Default share of an NPC ship's own crew still conscious below which it surrenders. */
    public static final float SURRENDER_CREW = 0.5f;
    /** A disabled ship's marker tint. */
    public static final Color DISABLED_COLOR = new Color(0.6f, 0.6f, 0.62f, 1f);

    private static final Family SHIPS = Family.all(ShipGridComponent.class, ShipSubsystemRegistryComponent.class,
            PhysicsBodyComponent.class).exclude(StationComponent.class).get();
    private static final ComponentMapper<DisabledComponent> DISABLED = ComponentMapper.getFor(DisabledComponent.class);
    private static final ComponentMapper<ShipGridComponent> GRID = ComponentMapper.getFor(ShipGridComponent.class);
    private static final ComponentMapper<ShipSubsystemRegistryComponent> REGISTRY =
            ComponentMapper.getFor(ShipSubsystemRegistryComponent.class);
    private static final ComponentMapper<HudMarkerComponent> MARKER = ComponentMapper.getFor(HudMarkerComponent.class);
    private static final ComponentMapper<MoraleComponent> MORALE = ComponentMapper.getFor(MoraleComponent.class);

    /** A ship was disabled, changed reason, or recovered. */
    public static final class Change {
        public final Entity ship;
        /** What it was before, or null if it was not disabled. */
        public final DisabledComponent.Reason was;
        /** What it is now, or null once it has recovered. */
        public final DisabledComponent.Reason now;

        Change(Entity ship, DisabledComponent.Reason was, DisabledComponent.Reason now) {
            this.ship = ship;
            this.was = was;
            this.now = now;
        }
    }

    /** Every change of a ship's disabled state, as it happens. */
    public final Signal<Change> changes = new Signal<>();

    private final DoubleSupplier simSeconds;
    private ImmutableArray<Entity> ships;
    private ImmutableArray<Entity> crew;
    private double next = Double.NEGATIVE_INFINITY;

    public ShipDisablement(DoubleSupplier simSeconds) {
        this.simSeconds = simSeconds;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(SHIPS);
        crew = engine.getEntitiesFor(CrewService.CREW);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        ships = null;
        crew = null;
    }

    @Override
    public void update(float deltaTime) {
        if (ships == null) return;
        double now = simSeconds.getAsDouble();
        if (now < next) return;
        next = now + INTERVAL;
        for (int i = 0; i < ships.size(); i++) judge(ships.get(i));
    }

    /** Judges one ship now, and returns its state: null when it is fit to fight. */
    public DisabledComponent.Reason judge(Entity ship) {
        DisabledComponent current = DISABLED.get(ship);
        DisabledComponent.Reason reason = assess(ship, current == null ? null : current.reason);
        set(ship, reason);
        if (reason != null && !isPlayer(ship)) stop(ship);
        return reason;
    }

    /** What state {@code ship} should be in, given the one it is in. */
    DisabledComponent.Reason assess(Entity ship, DisabledComponent.Reason current) {
        float hull = hullIntegrity(GRID.get(ship));
        if (hull < WRECK_HULL || current == DisabledComponent.Reason.WRECKED) return DisabledComponent.Reason.WRECKED;
        if (current != null && current.sticky()) return current;
        if (hull < DISABLED_HULL) return DisabledComponent.Reason.HULL;
        ShipSubsystemRegistryComponent registry = REGISTRY.get(ship);
        if (hasReactor(registry) && !reactorRunning(registry)) return DisabledComponent.Reason.NO_POWER;
        int[] count = crewCount(ship);
        if (count[0] > 0 && count[1] == 0) return DisabledComponent.Reason.NO_CREW;
        if (losing(ship, hull, count)) return DisabledComponent.Reason.SURRENDERED;
        return null;
    }

    /** True for an NPC ship in a fight, willing to give up, that is losing it. */
    private boolean losing(Entity ship, float hull, int[] count) {
        if (isPlayer(ship) || ship.getComponent(NpcCombatComponent.class) == null) return false;
        MoraleComponent morale = MORALE.get(ship);
        if (morale != null && !morale.surrenders) return false;
        float hullLine = morale == null ? SURRENDER_HULL : morale.surrenderHull;
        float crewLine = morale == null ? SURRENDER_CREW : morale.surrenderCrew;
        if (hull < hullLine) return true;
        if (count[0] > 0 && count[1] < crewLine * count[0]) return true;
        return gunsSilenced(ship);
    }

    /**
     * Puts {@code ship} in state {@code reason} (null clears it), keeping its marker in step, and
     * tells {@link #changes}. Boarding and abandoning ship set the sticky states through this.
     */
    public void set(Entity ship, DisabledComponent.Reason reason) {
        DisabledComponent current = DISABLED.get(ship);
        DisabledComponent.Reason was = current == null ? null : current.reason;
        if (was == reason) return;
        HudMarkerComponent marker = MARKER.get(ship);
        if (reason == null) {
            ship.remove(DisabledComponent.class);
            if (marker != null) {
                if (current.markerLabel != null) marker.label = current.markerLabel;
                marker.color.set(marker.kind.defaultColor);
            }
            holdFire(ship, false);
        } else {
            if (current == null) {
                current = new DisabledComponent(reason, simSeconds.getAsDouble());
                current.markerLabel = marker == null ? null : marker.label;
                ship.add(current);
            }
            current.reason = reason;
            if (marker != null) {
                marker.label = (current.markerLabel == null ? "" : current.markerLabel + " ") + "(" + reason.label + ")";
                marker.color.set(DISABLED_COLOR);
            }
            if (reason == DisabledComponent.Reason.WRECKED) coldReactors(REGISTRY.get(ship));
        }
        changes.dispatch(new Change(ship, was, reason));
    }

    /** Takes a disabled NPC ship's pilots off, cuts its thrust and holds its fire. */
    private static void stop(Entity ship) {
        ship.remove(NpcCombatComponent.class);
        ship.remove(NpcPilotComponent.class);
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        if (controls != null) {
            controls.clear();
            controls.rotationAssist = false;
        }
        holdFire(ship, true);
    }

    private static void holdFire(Entity ship, boolean hold) {
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons != null) {
            for (WeaponMountComponent m : weapons.mounts) {
                m.weaponsFree = !hold;
                if (hold) m.target = null;
            }
        }
        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        if (hold && sensor != null) sensor.lockedTarget = null;
    }

    private static void coldReactors(ShipSubsystemRegistryComponent registry) {
        if (registry == null) return;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
    }

    /** True if {@code ship} is disabled in any way. */
    public static boolean isDisabled(Entity ship) {
        return ship != null && DISABLED.get(ship) != null;
    }

    /** Why {@code ship} is disabled, or null. */
    public static DisabledComponent.Reason reason(Entity ship) {
        DisabledComponent d = ship == null ? null : DISABLED.get(ship);
        return d == null ? null : d.reason;
    }

    private static boolean isPlayer(Entity ship) {
        return ship.getComponent(PlayerControllerComponent.class) != null;
    }

    /** Mean integrity of a grid's hull tiles, 1 when it has none. */
    public static float hullIntegrity(ShipGridComponent grid) {
        if (grid == null) return 1f;
        float sum = 0f;
        int n = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t == null || t.wallType != Tile.WallType.HULL) continue;
                sum += t.getIntegrity();
                n++;
            }
        }
        return n == 0 ? 1f : sum / n;
    }

    static boolean hasReactor(ShipSubsystemRegistryComponent registry) {
        if (registry == null) return false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR) return true;
        }
        return false;
    }

    /** True if one of the ship's reactors is switched on, intact enough and producing. */
    public static boolean reactorRunning(ShipSubsystemRegistryComponent registry) {
        if (registry == null) return false;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.REACTOR && s.isOperational) return true;
        }
        return false;
    }

    /**
     * The ship's own crew aboard it: {total, conscious}. Its own crew are of the faction that works
     * its task board and call no other craft home, so boarders and a station's loaders don't count.
     */
    int[] crewCount(Entity ship) {
        int[] out = new int[2];
        if (crew == null) return out;
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        int faction = board == null ? 0 : board.faction;
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            GridPositionComponent p = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (p.ship != ship || c.faction != faction || (c.home != null && c.home != ship)) continue;
            out[0]++;
            if (c.isConscious()) out[1]++;
        }
        return out;
    }

    /**
     * True if the ship has guns (point defense aside) and none of them can fire. Guns an ion hit
     * has knocked out (or whose power it has) don't count: the crew wait for them to come back.
     */
    static boolean gunsSilenced(Entity ship) {
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons == null || IonStun.anyStunned(ship)) return false;
        boolean any = false;
        for (WeaponMountComponent m : weapons.mounts) {
            if (m.def == null || m.def.pointDefense) continue;
            any = true;
            SubsystemComponent sub = m.subsystem == null ? null : m.subsystem.getComponent(SubsystemComponent.class);
            if (sub == null || (!sub.isDisabled() && sub.isOperational)) return false;
        }
        return any;
    }
}
