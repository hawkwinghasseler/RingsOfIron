package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round 6 part 1, from seed 12's career: a boarding order is a party member's own. Before, the party's
 * orders were anyone's and one to a room, so on a raider of one room the crewman at the helm took the
 * only order and boarded alone against two pirates, while the marine stayed behind.
 */
class BoardingPartyTest {
    static final float FRAME = 1f / 60f;
    static final int RAIDERS = 1;

    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        CrewFactory.ensureBoard(game.raider).faction = RAIDERS;
        List<int[]> floor = GameScenario.floorTiles(game.raider.getComponent(ShipGridComponent.class));
        for (int i = 0; i < 2; i++) {
            int[] t = floor.get(floor.size() - 1 - i);
            CrewFactory.spawn(world.crew, game.raider, "Raider " + i, CrewRole.CREWMAN, RAIDERS, t[0], t[1]);
        }
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(float seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }

    @Test
    void everyMarineGetsAnOrderOfTheirOwnAndNobodyElseBoards() {
        int[] t = GameScenario.floorTiles(game.player.getComponent(ShipGridComponent.class)).get(0);
        Entity second = CrewFactory.spawn(world.crew, game.player, "Dag", CrewRole.MARINE, 0, t[0], t[1]);
        for (Entity e : game.raider.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        world.docking.dockInPlace(game.player, 0, game.raider, 0);
        run(2);
        assertTrue(ShipDisablement.isDisabled(game.raider));
        List<Entity> party = new java.util.ArrayList<>();
        for (Entity e : world.boarding.party(game.player)) party.add(e);
        assertEquals(2, party.size(), "the two marines");
        assertTrue(party.contains(second));

        String said = world.boarding.order(game.player);
        assertTrue(said.startsWith("Boarding party ordered aboard"), said);
        ShipTaskBoardComponent board = game.player.getComponent(ShipTaskBoardComponent.class);
        Set<Entity> ordered = new HashSet<>(), boarded = new HashSet<>();
        for (int i = 0; i < 10 / FRAME; i++) {
            world.update(FRAME);
            for (CrewTask task : board.tasks) {
                if (task instanceof BoardTask && task.isActive()) ordered.add(((BoardTask) task).member);
            }
            for (Entity e : world.crewCareer.ownCrew(game.player)) {
                CrewMemberComponent c = world.crew.crew(e);
                if (c.task instanceof BoardTask) boarded.add(e);
            }
        }
        assertEquals(new HashSet<>(party), ordered, "an order for each marine, at once");
        assertEquals(new HashSet<>(party), boarded, "only the marines took them");
    }
}
