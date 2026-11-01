package com.ringsofiron.aicrew.demo;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.GridHazardSystem;
import com.ringsofiron.interiorgrid.LifeSupportSystem;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;

/**
 * A small three-room ship with a crew of five, and a scripted run of trouble: a fire in
 * the carpeted mess, a damaged reactor, a hull breach on the bridge and two boarders.
 * Shared by the console demo, the graphical demo and the tests.
 *
 * <pre>
 *  engineering | mess (carpet) | bridge
 *  door (8,3)    door (16,4)
 * </pre>
 */
public class CrewScenario {

    public static final String[] BLUEPRINT = {
        "#########################",
        "#.......................#",
        "#.......................#",
        "#.......................#",
        "#.......................#",
        "#.......................#",
        "#.......................#",
        "#########################"
    };
    /** Columns whose left edge carries a dividing wall, and the y of each wall's door. */
    public static final int WALL_A_X = 8, DOOR_A_Y = 3;
    public static final int WALL_B_X = 16, DOOR_B_Y = 4;

    /** Fire start, in the mess. */
    public static final int FIRE_X = 12, FIRE_Y = 2;
    /** Hull breach, top wall of the bridge. */
    public static final int BREACH_X = 20, BREACH_Y = 7;

    public final Engine engine = new Engine();
    public final CrewService crew;
    public final Entity ship = new Entity();
    public final ShipGridComponent grid;
    public final ShipTaskBoardComponent board;
    public final Array<Entity> subsystems = new Array<>();
    public final Array<Entity> crewMembers = new Array<>();
    public Entity reactor;

    /** Simulation time, seconds. */
    public float time;
    private int nextEvent;
    /** Called with a line of text whenever a scripted event fires. */
    public java.util.function.Consumer<String> log = s -> {};

    public CrewScenario() {
        this(true);
    }

    /** @param withCrew false for an empty ship (tests add their own crew) */
    public CrewScenario(boolean withCrew) {
        ChassisBlueprint bp = new ChassisBlueprint(BLUEPRINT).tileSize(1f)
                .mount("life_support", MountFootprint.MountSize.S, 2, 2, 1)
                .mount("reactor", MountFootprint.MountSize.S, 2, 5)
                .mount("helm", MountFootprint.MountSize.S, 20, 5, 1)
                .conduitRun(2, 5, 20, 5)
                .conduitRun(2, 2, 2, 5);
        for (int y = 1; y <= 6; y++) {
            bp.edgeWall(WALL_A_X, y, Side.LEFT, WeightClass.LIGHT, y == DOOR_A_Y);
            bp.edgeWall(WALL_B_X, y, Side.LEFT, WeightClass.LIGHT, y == DOOR_B_Y);
        }
        bp.covering(FloorCovering.CARPET, WALL_A_X, 1, WALL_B_X - WALL_A_X, 6);
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(bp);
        grid = result.grid;
        CompartmentBuilder.rebuildIfChanged(grid);

        engine.addSystem(new GridHazardSystem());
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
        crew = CrewFactory.install(engine);

        ship.add(grid);
        ShipSubsystemRegistryComponent reg = new ShipSubsystemRegistryComponent(ship);
        ship.add(reg);
        board = CrewFactory.ensureBoard(ship);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement placement : result.subsystems) {
            Entity sys = new Entity();
            sys.add(new SubsystemComponent(placement.footprint, placement.type));
            placement.footprint.installed = sys;
            engine.addEntity(sys);
            reg.registerSubsystem(sys);
            subsystems.add(sys);
            if (placement.type == SubsystemComponent.SubsystemType.REACTOR) reactor = sys;
        }

        // Suit lockers for the five of them and a spare
        com.ringsofiron.interiorgrid.SuitOutfit.outfit(grid, null, 5);
        if (withCrew) {
            crewMembers.add(CrewFactory.spawn(crew, ship, "Okafor", CrewRole.ENGINEER, 0, 3, 4));
            crewMembers.add(CrewFactory.spawn(crew, ship, "Lindqvist", CrewRole.ENGINEER, 0, 5, 1));
            crewMembers.add(CrewFactory.spawn(crew, ship, "Reyes", CrewRole.MARINE, 0, 10, 5));
            crewMembers.add(CrewFactory.spawn(crew, ship, "Hart", CrewRole.MEDIC, 0, 6, 6));
            crewMembers.add(CrewFactory.spawn(crew, ship, "Tamura", CrewRole.CREWMAN, 0, 19, 4));
        }
    }

    /** Advances the simulation by dt seconds, firing scripted events on schedule. */
    public void update(float dt) {
        time += dt;
        runEvents();
        engine.update(dt);
    }

    /** Runs the scripted trouble. Call {@link #update} instead to also step the engine. */
    void runEvents() {
        if (nextEvent == 0 && time >= 2f) {
            startFire();
            nextEvent++;
        } else if (nextEvent == 1 && time >= 5f) {
            reactor.getComponent(SubsystemComponent.class).health = 0.4f;
            log.accept("Reactor hit: health 40%");
            nextEvent++;
        } else if (nextEvent == 2 && time >= 10f) {
            breachHull();
            nextEvent++;
        } else if (nextEvent == 3 && time >= 40f) {
            crewMembers.add(CrewFactory.spawn(crew, ship, "Boarder-1", CrewRole.MARINE, 1, 1, 6));
            crewMembers.add(CrewFactory.spawn(crew, ship, "Boarder-2", CrewRole.MARINE, 1, 1, 1));
            log.accept("Boarders in engineering!");
            nextEvent++;
        }
    }

    public void startFire() {
        Tile t = grid.getTile(FIRE_X, FIRE_Y);
        t.ignite();
        t.fireIntensity = 0.3f;
        log.accept("Fire in the mess at " + FIRE_X + "," + FIRE_Y);
    }

    public void breachHull() {
        grid.getTile(BREACH_X, BREACH_Y).setIntegrity(0f);
        grid.gridDirty = true;
        log.accept("Hull breach on the bridge at " + BREACH_X + "," + BREACH_Y);
    }

    /** The crew member standing on (x, y), or null. */
    public CrewMemberComponent crewAt(int x, int y) {
        for (Entity e : crewMembers) {
            GridPositionComponent p = crew.position(e);
            if (p.tileX() == x && p.tileY() == y) return crew.crew(e);
        }
        return null;
    }
}
