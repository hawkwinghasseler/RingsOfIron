package com.ringsofiron.benchmarks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The new game's busy orbit, scaled up: {@code freighters} extra haulers on rails trading between
 * the three stations, and {@code bubbleShips} extra crewed ships in the bubble around the player,
 * every other pair of corvettes fighting each other. The raider fights the player from the start.
 */
public final class BusyOrbitScene implements AutoCloseable {
    /** Crew aboard each extra ship in the bubble. */
    static final int CREW_PER_SHIP = 4;

    public final GameWorld world;
    public final GameScenario game;
    private final RandomXS128 random = new RandomXS128(7);
    private final Map<ShipGridComponent, List<int[]>> floors = new IdentityHashMap<>();
    private int walking;

    public BusyOrbitScene(int freighters, int bubbleShips) {
        Path assets = Scenes.assets();
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        game.provokeRaider();

        double hold = InteriorGridData.cargoBayVolume(game.content.defs, GameScenario.FREIGHTER_SHIP);
        String[] stations = {game.highport, game.depot, game.lunarMine};
        for (int i = 0; i < freighters; i++) {
            world.macroSim.addFreighter("Bench hauler " + i, hold, 20_000, stations[i % stations.length]);
        }

        Entity rival = null;
        for (int i = 0; i < bubbleShips; i++) {
            boolean corvette = i % 2 == 0;
            float angle = random.nextFloat() * MathUtils.PI2;
            float r = 400f + random.nextFloat() * 2_600f;
            Entity ship = game.content.factory.spawnShip(corvette ? GameScenario.PLAYER_SHIP : GameScenario.FREIGHTER_SHIP,
                    MathUtils.cos(angle) * r, MathUtils.sin(angle) * r, random.nextFloat() * 360f);
            Scenes.crew(world, ship, CREW_PER_SHIP, 3 + i % 2, "Bench " + i);
            if (!corvette) continue;
            // Corvettes pair off and fight each other
            if (rival == null) {
                rival = ship;
            } else {
                fight(ship, rival);
                fight(rival, ship);
                rival = null;
            }
        }
    }

    private static void fight(Entity ship, Entity target) {
        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = target;
        ship.add(new NpcCombatComponent(target));
    }

    /** One 60 Hz frame, with everyone's crew kept walking. */
    public void frame() {
        walking = Scenes.keepWalking(world, random, floors);
        world.update(1f / 60f);
    }

    /** Crew walking at the last frame. */
    public int walking() {
        return walking;
    }

    @Override
    public void close() {
        world.dispose();
    }
}
