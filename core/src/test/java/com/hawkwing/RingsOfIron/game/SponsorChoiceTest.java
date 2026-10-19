package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A new career may serve the Selene Combine instead of the Patrol, from its ice mine above the Moon. */
class SponsorChoiceTest {
    static final float FRAME = 1f / 60f;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (world != null) world.dispose();
    }

    private void start(NewGame newGame) {
        world = new GameWorld();
        game = GameScenario.start(world, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"), newGame);
    }

    @Test
    void thePatrolIsTheDefaultWithItsOwnRanks() {
        start(NewGame.designed());
        assertEquals("patrol", world.standing.factions().id(world.standing.sponsor()));
        assertEquals("Ensign, Terran Patrol Authority", world.standing.describeRank());
        assertEquals("Highport", game.homeName());
        game.beginCareer();
        assertSame(world.engine.getEntity(game.highport), world.docking.dockedTo(game.player));
    }

    @Test
    void aSeleneCareerStartsDockedAtShackletonWithTheCombinesRanks() {
        start(NewGame.designed().withSponsor("selene"));
        int selene = world.standing.factions().index("selene");
        assertEquals(selene, world.standing.sponsor());
        assertEquals("Prospector, Selene Mining Combine", world.standing.describeRank());
        assertEquals(GameScenario.LUNAR_MINE_NAME, game.homeName());
        assertEquals(GameScenario.MOON, world.macroSim.gateway().anchorParentUuid(), "the bubble starts above the Moon");
        game.beginCareer();
        Entity mine = world.engine.getEntity(game.lunarMine);
        assertSame(mine, world.docking.dockedTo(game.player));
        assertSame(mine, world.engine.getEntity(game.home()));

        // Shackleton's board is Selene's, and the raider outside is on it
        boolean raider = false;
        for (Contract c : world.contracts.offers(game.lunarMine)) {
            assertEquals("selene", c.giver);
            raider |= c.type == Contract.Type.BOUNTY && world.universe.uuidOf(game.raider).equals(c.targetUuid);
        }
        assertTrue(raider, "the bounty on the raider outside");
        // Selene's standing climbs the ladder under its own names
        world.standing.reward(selene, 20f, "test");
        assertEquals("Surveyor", world.standing.rankName());
        assertTrue(world.standing.notices.peek().startsWith("Promoted to Surveyor of the Selene Mining Combine"));
    }

    @Test
    void onlyASponsorableFactionMayBeServed() {
        start(NewGame.designed().withSponsor("pirates"));
        assertEquals("patrol", world.standing.factions().id(world.standing.sponsor()));
    }

    @Test
    void theGameRunsAboveTheMoon() {
        start(NewGame.seeded(3, "selene"));
        game.beginCareer();
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertTrue(world.docking.undock(game.player));
        for (int i = 0; i < 600; i++) world.update(FRAME);
        assertNull(world.getCrash(), "nothing hit the Moon");
        assertTrue(world.timeWarp.setWarp(1_000), String.valueOf(world.engine.getRailsWarpBlockedReason()));
        for (int i = 0; i < 300; i++) world.update(FRAME);
        world.timeWarp.setWarp(1);
        for (int i = 0; i < 60; i++) world.update(FRAME);
        assertNull(world.getCrash());
    }

    @Test
    void theSponsorsGuardsAreFriendly() {
        start(NewGame.designed().withSponsor("selene"));
        for (HostileComponent h : world.macroSim.hostiles().all().values()) {
            if (h.role != HostileComponent.Role.PATROL) continue;
            Entity ship = world.engine.getEntity(world.macroSim.hostiles().all().entrySet().stream()
                    .filter(e -> e.getValue() == h).findFirst().get().getKey());
            HudMarkerComponent.Kind kind = HostileTraffic.markerFor(ship, "selene").kind;
            assertEquals(HostileTraffic.SELENE.equals(h.faction) ? HudMarkerComponent.Kind.FRIENDLY
                    : HudMarkerComponent.Kind.NEUTRAL, kind, h.displayName);
        }
    }

    @Test
    void aSaveKeepsTheSponsorAndItsHome(@TempDir Path dir) {
        start(NewGame.designed().withSponsor("selene"));
        game.beginCareer();
        FileHandle file = new FileHandle(dir.resolve("selene.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, WiderWorldTest.assets(), WiderWorldTest.assets().resolve("none"));
            loaded.quickLoad(file);
            assertEquals("selene", fresh.standing.factions().id(fresh.standing.sponsor()));
            assertEquals(GameScenario.LUNAR_MINE_NAME, loaded.homeName());
            assertEquals("Prospector, Selene Mining Combine", fresh.standing.describeRank());
        } finally {
            fresh.dispose();
        }
    }
}
