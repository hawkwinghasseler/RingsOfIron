package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Round 5 part 2's survey: round 4's ace fight (planning/round4-balance.md) again, now that hits
 * wound rather than kill. Each trial is a new seeded game with an ace refitted as given, fought from
 * the starting corvette with every gun firing, then ten minutes for the medics. Reports our hull,
 * the wounds and deaths on both sides, to {@code core/build/wound-survey.txt}.
 *
 * <p>It takes several minutes, so it runs only when asked:
 * {@code ./gradlew :core:test --tests '*CrewWoundSurveyTest' -PwoundSurvey=10} (trials per refit).
 */
class CrewWoundSurveyTest {
    static final float FRAME = 1f / 60f;
    static final String[] REFITS = {"raider_ace_chaingun", "raider_ace_railgun"};

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    static final class Trial {
        String refit;
        long seed;
        boolean beaten, lost;
        double seconds;
        float hull;
        int crew, dead, aceCrew, aceAlive;
        final Map<Wound, Integer> wounds = new EnumMap<>(Wound.class);
    }

    @Test
    void theAceFightAgainWithWounds() throws IOException {
        String trials = System.getProperty("wound.survey");
        Assumptions.assumeTrue(trials != null, "run with -PwoundSurvey=N");
        int n = Integer.parseInt(trials);
        List<Trial> all = new ArrayList<>();
        for (String refit : REFITS) for (int i = 0; i < n; i++) all.add(trial(refit, 2026 + i));
        StringBuilder out = new StringBuilder("| Ace refit | Seed | Result | Our hull | Our crew wounded (light/serious/critical) | Our crew dead | Ace crew alive |\n|---|---|---|---|---|---|---|\n");
        for (Trial t : all) {
            out.append(String.format(Locale.ROOT, "| %s | %d | %s in %.0f s | %.2f | %d/%d/%d | %d of %d | %d of %d |%n",
                    t.refit, t.seed, t.beaten ? "beaten" : t.lost ? "we lost" : "neither", t.seconds, t.hull,
                    t.wounds.getOrDefault(Wound.LIGHT, 0), t.wounds.getOrDefault(Wound.SERIOUS, 0),
                    t.wounds.getOrDefault(Wound.CRITICAL, 0), t.dead, t.crew, t.aceAlive, t.aceCrew));
        }
        for (String refit : REFITS) {
            int fights = 0, dead = 0, crew = 0, beaten = 0, wounded = 0, aceAlive = 0, aceCrew = 0;
            for (Trial t : all) {
                if (!t.refit.equals(refit)) continue;
                fights++;
                dead += t.dead;
                crew += t.crew;
                if (t.beaten) beaten++;
                for (int w : t.wounds.values()) wounded += w;
                aceAlive += t.aceAlive;
                aceCrew += t.aceCrew;
            }
            out.append(String.format(Locale.ROOT, "%n%s: %d fights, beaten %d; our crew wounded %d and dead %d of %d; the ace's crew alive %d of %d.",
                    refit, fights, beaten, wounded, dead, crew, aceAlive, aceCrew));
        }
        System.out.println(out);
        Files.writeString(Paths.get(System.getProperty("user.dir")).resolve("build/wound-survey.txt"), out.toString());
    }

    private Trial trial(String refit, long seed) {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(seed, "patrol"));
            game.beginCareer();
            run(world, 120);
            Trial t = new Trial();
            t.refit = refit;
            t.seed = seed;
            String id = null;
            HostileComponent h = null;
            for (Map.Entry<String, HostileComponent> e : world.macroSim.hostiles().all().entrySet()) {
                Entity candidate = world.engine.getEntity(e.getKey());
                if (e.getValue().isPirate() && e.getValue().wingOf == null && !e.getValue().ace
                        && HostileTraffic.RAIDER.equals(e.getValue().shipId) && candidate != null && world.universe.orbit(candidate) != null) {
                    id = e.getKey();
                    h = e.getValue();
                }
            }
            assertNotNull(id, "a raider to make an ace of");
            Entity ship = world.engine.getEntity(id);
            assertTrue(PirateAces.promote(world, ship, h, world.macroSim.hostiles().all().values(), Difficulty.of(world), true));
            LoadoutDef def = world.combatDefs.loadouts.get(refit);
            assertNotNull(def, refit);
            assertTrue(Loadouts.apply(ship, def, world.combatDefs) > 0, "refitted with " + refit);
            h.loadout = def.id;
            h.firepower = HostileTraffic.firepower(ship);
            world.docking.undock(game.player);
            run(world, 60);
            h.targetUuid = HostileComponent.PLAYER;
            bringIn(world, id, 3_000, 0);
            t.aceCrew = aboard(world, ship, false);
            List<Entity> ours = world.crewCareer.ownCrew(game.player);
            t.crew = ours.size();
            PlayerGunnery gun = new PlayerGunnery(world, game.player);
            gun.setTarget(ship);
            double t0 = world.engine.getSimSeconds();
            HostileComponent ace = h;
            runUntil(world, 300f, () -> {
                gun.fireGuns();
                gun.launchMissile();
                gun.update(FRAME);
                return ace.partDropped || ShipDisablement.isDisabled(ship) || ShipDisablement.isDisabled(game.player)
                        || world.getCrash() != null;
            });
            t.seconds = world.engine.getSimSeconds() - t0;
            t.beaten = h.partDropped || ShipDisablement.isDisabled(ship);
            t.lost = !t.beaten && ShipDisablement.isDisabled(game.player);
            t.hull = ShipDisablement.hullIntegrity(game.player.getComponent(ShipGridComponent.class));
            for (Entity e : ours) {
                Wound w = e.getComponent(CrewMemberComponent.class).wound;
                if (w != Wound.NONE) t.wounds.merge(w, 1, Integer::sum);
            }
            // Ten minutes for the medics (and for critical wounds left untreated to run out)
            run(world, 60 * 60 * 10);
            for (Entity e : ours) if (!e.getComponent(CrewMemberComponent.class).isAlive()) t.dead++;
            t.aceAlive = aboard(world, ship, true);
            return t;
        } finally {
            world.dispose();
        }
    }

    /** The ship's own crew aboard it; only the living if {@code living}. */
    private static int aboard(GameWorld world, Entity ship, boolean living) {
        int n = 0;
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (e.getComponent(GridPositionComponent.class).ship == ship && c.faction != FactionStanding.PLAYER
                    && (!living || c.isAlive())) n++;
        }
        return n;
    }

    private static void run(GameWorld world, int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private static void runUntil(GameWorld world, float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME && !done.getAsBoolean(); i++) world.update(FRAME);
    }

    private static void bringIn(GameWorld world, String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(), s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
    }
}
