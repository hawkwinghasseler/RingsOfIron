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
    /** With -PwoundTrace, each trial's crew every 10 s after the fight, to {@code core/build/wound-trace.txt}. */
    static final StringBuilder TRACE = new StringBuilder();

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
        /** Of our dead, those who died before the fight was over; hits we took after it. */
        int deadInFight, hitsAfter;
        final Map<Wound, Integer> wounds = new EnumMap<>(Wound.class);
        /** What killed each of our dead. */
        final List<String> causes = new ArrayList<>();
    }

    @Test
    void theAceFightAgainWithWounds() throws IOException {
        String trials = System.getProperty("wound.survey");
        Assumptions.assumeTrue(trials != null, "run with -PwoundSurvey=N");
        int n = Integer.parseInt(trials);
        List<Trial> all = new ArrayList<>();
        // -PwoundRefits=raider_ace_railgun surveys one refit only
        String[] refits = System.getProperty("wound.survey.refits", String.join(",", REFITS)).split(",");
        for (String refit : refits) for (int i = 0; i < n; i++) all.add(trial(refit, 2026 + i));
        StringBuilder out = new StringBuilder("| Ace refit | Seed | Result | Our hull | Our crew wounded (light/serious/critical) | Our crew dead (in the fight) | Of | Hits after | Ace crew alive |\n|---|---|---|---|---|---|---|---|---|\n");
        for (Trial t : all) {
            out.append(String.format(Locale.ROOT, "| %s | %d | %s in %.0f s | %.2f | %d/%d/%d | %d of %d (%d) | %s | %d | %d of %d |%n",
                    t.refit, t.seed, t.beaten ? "beaten" : t.lost ? "we lost" : "neither", t.seconds, t.hull,
                    t.wounds.getOrDefault(Wound.LIGHT, 0), t.wounds.getOrDefault(Wound.SERIOUS, 0),
                    t.wounds.getOrDefault(Wound.CRITICAL, 0), t.dead, t.crew, t.deadInFight, String.join(", ", t.causes),
                    t.hitsAfter, t.aceAlive, t.aceCrew));
        }
        for (String refit : refits) {
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
        if (TRACE.length() > 0) Files.writeString(Paths.get(System.getProperty("user.dir")).resolve("build/wound-trace.txt"), TRACE);
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
            // Promotion may already have fitted it, when the refit is the one its difficulty picks
            if (!def.id.equals(h.loadout)) assertTrue(Loadouts.apply(ship, def, world.combatDefs) > 0, "refitted with " + refit);
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
            boolean trace = Boolean.getBoolean("wound.survey.trace");
            Map<Entity, Float> injury = new java.util.IdentityHashMap<>();
            runUntil(world, 300f, () -> {
                if (trace) {
                    for (Entity e : ours) {
                        CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                        float was = injury.getOrDefault(e, 0f);
                        if (c.injury != was) {
                            TRACE.append(String.format(Locale.ROOT, "HIT %s %d t=%.1f %s +%.0f -> %.0f %s at %d,%d (%s)%n", refit, seed,
                                    world.engine.getSimSeconds() - t0, c.name, c.injury - was, c.injury, c.wound,
                                    e.getComponent(GridPositionComponent.class).tileX(), e.getComponent(GridPositionComponent.class).tileY(), c.activity));
                            injury.put(e, c.injury);
                        }
                    }
                }
                if (trace && Math.round((world.engine.getSimSeconds() - t0) * 60) % 120 == 0) {
                    StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "AT %s %d t=%.0f", refit, seed, world.engine.getSimSeconds() - t0));
                    for (Entity e : ours) {
                        CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                        GridPositionComponent p = e.getComponent(GridPositionComponent.class);
                        line.append(String.format(Locale.ROOT, " [%s %d,%d %s %s]", c.name, p.tileX(), p.tileY(), c.wound, c.activity));
                    }
                    TRACE.append(line).append('\n');
                }
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
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                if (c.wound != Wound.NONE) t.wounds.merge(c.wound, 1, Integer::sum);
                if (!c.isAlive()) t.deadInFight++;
            }
            Entity player = game.player;
            world.combatEvents.impacts.add((signal, hit) -> {
                if (hit.target == player) t.hitsAfter++;
            });
            // Ten minutes for the medics (and for critical wounds left untreated to run out)
            if (Boolean.getBoolean("wound.survey.trace")) {
                for (int k = 0; k <= 60; k++) {
                    StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "TRACE %s %d t+%ds:", refit, seed, k * 10));
                    ShipGridComponent g = game.player.getComponent(ShipGridComponent.class);
                    for (Entity e : ours) {
                        CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                        GridPositionComponent p = e.getComponent(GridPositionComponent.class);
                        line.append(String.format(Locale.ROOT, " [%s %s %s%s h%.0f bo%.0f suit%s/%.0f air%s %s]", c.name, c.state, c.wound,
                                c.stabilised ? "+stab" : "", c.health, c.bleedOut, c.suited, c.suitAir,
                                com.ringsofiron.aicrew.TaskRules.isBreathable(g, p.tileX(), p.tileY()), c.activity));
                    }
                    line.append(" stores ").append(g.medicalStores).append(" kits ").append(g.patchKits)
                            .append(" disabled ").append(ShipDisablement.reason(game.player));
                    int floor = 0, breathable = 0, breaches = 0;
                    for (int y = 0; y < g.height; y++) {
                        for (int x = 0; x < g.width; x++) {
                            com.ringsofiron.interiorgrid.Tile tile = g.getTile(x, y);
                            if (tile == null) continue;
                            if (tile.isHullBreach()) breaches++;
                            if (tile.holdsAir()) {
                                floor++;
                                if (com.ringsofiron.aicrew.TaskRules.isBreathable(g, x, y)) breathable++;
                            }
                        }
                    }
                    line.append(" breathable ").append(breathable).append('/').append(floor).append(" breaches ").append(breaches);
                    for (Entity sub : game.player.getComponent(com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent.class).subsystems) {
                        com.ringsofiron.interiorgrid.SubsystemComponent ss = sub.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class);
                        if (ss.type == com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.REACTOR
                                || ss.type == com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.LIFE_SUPPORT) {
                            line.append(String.format(Locale.ROOT, " %s %.2f%s%s", ss.type, ss.health, ss.isOperational ? "" : " down",
                                    ss.switchedOn ? "" : " off"));
                        }
                    }
                    TRACE.append(line).append('\n');
                    run(world, 600);
                }
            } else {
                run(world, 60 * 60 * 10);
            }
            for (Entity e : ours) {
                CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
                if (c.isAlive()) continue;
                t.dead++;
                t.causes.add(String.valueOf(c.lastDamageCause).toLowerCase(Locale.ROOT));
            }
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
