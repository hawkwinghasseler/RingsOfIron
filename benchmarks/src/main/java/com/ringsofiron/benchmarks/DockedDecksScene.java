package com.ringsofiron.benchmarks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.shipops.StationCrew;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hub station holding still with {@code docked} ships latched to its ports (corvettes and
 * haulers in turn, each at a free port of its own size), every deck crewed and everyone kept
 * walking, so each deck has its own Box2D crew world stepping every tick.
 */
public final class DockedDecksScene implements AutoCloseable {
    static final String HUB = "station_hub";
    static final int CREW_PER_SHIP = 6;

    public final GameWorld world;
    public final Entity hub;
    private final RandomXS128 random = new RandomXS128(11);
    private final Map<ShipGridComponent, List<int[]>> floors = new IdentityHashMap<>();
    private int docked;
    private int walking;

    public DockedDecksScene(int ships) {
        Path assets = Scenes.assets();
        world = new GameWorld();
        world.loadContent(assets, assets.resolve("no-mods-here"));
        hub = world.engine.createEntityWithUuid();
        hub.add(new TransformComponent());
        world.engine.addEntity(hub);
        DockingData.buildStation(hub, world.getContent().defs.require("chassis", HUB, ChassisDef.class), HUB,
                world.engine, "Bench Hub", null);
        StationCrew.spawnCrew(world.crew, hub);
        hub.add(new KinematicTransientComponent((t, out) -> {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            out[3] = 0.0;
        }, 30f));

        DockingPort[] hubPorts = hub.getComponent(DockingPortsComponent.class).ports;
        boolean[] taken = new boolean[hubPorts.length];
        for (int i = 0; i < ships; i++) {
            String type = i % 2 == 0 ? GameScenario.PLAYER_SHIP : GameScenario.FREIGHTER_SHIP;
            Entity ship = world.getContent().factory.spawnShip(type, 0f, -80f - 60f * i, 0f);
            if (i == 0) ship.add(new PlayerControllerComponent());
            Scenes.crew(world, ship, CREW_PER_SHIP, 0, "Bench " + i);
            DockingPort[] own = ship.getComponent(DockingPortsComponent.class).ports;
            dock:
            for (int a = 0; a < own.length; a++) {
                for (int b = 0; b < hubPorts.length; b++) {
                    if (taken[b] || hubPorts[b].size != own[a].size) continue;
                    world.docking.dockInPlace(ship, a, hub, b);
                    taken[b] = true;
                    docked++;
                    break dock;
                }
            }
        }
        // Long enough for power to be routed and the latches to settle
        for (int i = 0; i < 75; i++) world.update(1f / 60f);
    }

    /** Ships that found a free port of their size. */
    public int docked() {
        return docked;
    }

    /** One 60 Hz frame, with everyone kept walking. */
    public void frame() {
        walking = Scenes.keepWalking(world, random, floors);
        world.update(1f / 60f);
    }

    public int walking() {
        return walking;
    }

    @Override
    public void close() {
        world.dispose();
    }
}
