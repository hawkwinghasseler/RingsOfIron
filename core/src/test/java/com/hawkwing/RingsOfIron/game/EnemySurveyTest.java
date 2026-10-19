package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Round 5's enemy survey (planning/round5-plan.md, part 4): a lone raider, a wing of two raiders and a
 * pirate gunship, each against the corvette and the gunship, fought on the real seeded game the way
 * {@link Round3CareerTest} fights, with every gun and missile. Each pirate comes hunting the player
 * from 4 km. The quick check here runs in the suite; the full survey (10 fights each) runs with
 * {@code ./gradlew :core:test --tests '*EnemySurveyTest' -PenemySurvey} and writes its table to
 * {@code core/build/enemy-survey.txt}.
 */
class EnemySurveyTest {
    static final float FRAME = 1f / 60f;
    /** Longest fight, sim seconds. */
    static final float FIGHT_SECONDS = 400f;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    /** One fight's outcome. */
    static final class Result {
        String outcome;
        double seconds;
        int fired, hits;
        float hull0, hull1;
        int crew0, crew1;
        int enemyCrew0, enemyCrew1;
        String loadouts = "";

        @Override
        public String toString() {
            return String.format("%s in %.0f s; they fired %d, %d hit; our hull %.2f -> %.2f; crew %d -> %d; theirs %d -> %d [%s]",
                    outcome, seconds, fired, hits, hull0, hull1, crew0, crew1, enemyCrew0, enemyCrew1, loadouts);
        }
    }

    @Test
    void aLoneRaiderShootsBackAtTheCorvette() {
        Result r = fight(2026, false, HostileTraffic.RAIDER, 1, "raider_chaingun");
        System.out.println("RESULT " + r);
        assertTrue(r.fired > 0, "it fired: " + r);
    }

    @Test
    @EnabledIfSystemProperty(named = "enemy.probe", matches = ".+")
    void probe() {
        String[] p = System.getProperty("enemy.probe").split(",");
        System.out.println("RESULT " + fight(Long.parseLong(p[0]), Boolean.parseBoolean(p[1]), p[2], Integer.parseInt(p[3]),
                p.length > 4 ? p[4] : null));
    }

    @Test
    @EnabledIfSystemProperty(named = "enemy.survey", matches = ".+")
    void survey() throws IOException {
        StringBuilder out = new StringBuilder("| Enemy | Our ship | Won | Ran | Lost | Rounds at us | Hits | Our hull after (min-mean) | Our crew killed | Their crew left | Time to beat (mean) |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|\n");
        String[][] enemies = {{"Lone raider", HostileTraffic.RAIDER, "1"}, {"Wing of two raiders", HostileTraffic.RAIDER, "2"},
                {"Pirate gunship", HostileTraffic.PIRATE_GUNSHIP, "1"}};
        int trials = Integer.getInteger("enemy.survey.trials", 10);
        StringBuilder detail = new StringBuilder();
        for (String[] enemy : enemies) {
            for (boolean gunship : new boolean[]{false, true}) {
                int won = 0, ran = 0, lost = 0, fired = 0, hits = 0, killed = 0, theirs = 0;
                float minHull = 1, sumHull = 0;
                double beatTime = 0;
                for (int i = 0; i < trials; i++) {
                    Result r = fight(1 + i, gunship, enemy[1], Integer.parseInt(enemy[2]), null);
                    detail.append(enemy[0]).append(gunship ? " v gunship" : " v corvette").append(", seed ").append(1 + i)
                            .append(": ").append(r).append('\n');
                    switch (r.outcome) {
                        case "won": won++; beatTime += r.seconds; break;
                        case "ran": ran++; break;
                        case "lost": lost++; break;
                        default: break;
                    }
                    fired += r.fired;
                    hits += r.hits;
                    minHull = Math.min(minHull, r.hull1);
                    sumHull += r.hull1;
                    killed += r.crew0 - r.crew1;
                    theirs += r.enemyCrew1;
                }
                out.append(String.format("| %s | %s | %d | %d | %d | %.0f | %.0f | %.2f-%.2f | %.1f | %.1f | %s |%n", enemy[0],
                        gunship ? "Gunship" : "Corvette", won, ran, lost, fired / (double) trials, hits / (double) trials, minHull,
                        sumHull / trials, killed / (double) trials, theirs / (double) trials,
                        won == 0 ? "-" : String.format("%.0f s", beatTime / won)));
            }
        }
        out.append('\n').append(detail);
        Path file = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("enemy-survey.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, out.toString().getBytes());
        System.out.println(out);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * A new seeded Patrol career in the corvette, or in the gunship bought at Anvil Yards; then a wing
     * of {@code size} pirates of {@code shipId} (armed with {@code loadout}, or for the difficulty when
     * null) comes for the player from 4 km and both sides fight with everything they have.
     */
    static Result fight(long seed, boolean gunship, String shipId, int size, String loadout) {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(seed, "patrol"));
            game.beginCareer();
            run(world, 120);
            assertTrue(world.docking.undock(game.player));
            run(world, 60);
            if (gunship) {
                assertTrue(game.dockAt(game.shipyard));
                run(world, 120);
                float need = world.standing.ranks().get(2).standing - world.standing.standing(world.standing.sponsor());
                if (need > 0) world.standing.reward(world.standing.sponsor(), need + 0.5f, "survey");
                game.player.getComponent(TraderComponent.class).credits = 400_000;
                String said = world.shipyard.buy("patrol_gunship");
                assertTrue(said.startsWith("Bought"), said);
                run(world, 120);
                assertTrue(world.docking.undock(game.player));
                run(world, 60);
            }
            String lurk = game.highport;
            // Armed as the difficulty would arm it, drawn by the seed (the top-up's own draw is the same in every game)
            String fit = loadout != null ? loadout : Loadouts.pick(world.combatDefs, shipId, Difficulty.of(world), false,
                    new java.util.Random(seed * 7_919L + shipId.hashCode())).id;
            List<String> ids = world.hostiles.spawnWing(shipId, size, lurk, fit);
            assertEquals(size, ids.size());
            List<Entity> ships = new ArrayList<>();
            List<HostileComponent> hs = new ArrayList<>();
            Result r = new Result();
            for (int i = 0; i < ids.size(); i++) {
                HostileComponent h = world.macroSim.hostiles().get(ids.get(i));
                h.targetUuid = HostileComponent.PLAYER;
                hs.add(h);
                r.loadouts += (i == 0 ? "" : ", ") + h.loadout;
                ships.add(bringIn(world, ids.get(i), 4_000, i * 400));
            }
            Entity player = game.player;
            PlayerGunnery gun = new PlayerGunnery(world, player);
            int[] fired = new int[1], hits = new int[1];
            world.combatEvents.shots.add((sig, shot) -> { if (ships.contains(shot.shooter)) fired[0]++; });
            world.combatEvents.impacts.add((sig, hit) -> { if (ships.contains(hit.owner) && hit.target == player) hits[0]++; });
            int[] frame = new int[1];
            double t0 = world.engine.getSimSeconds();
            double[] end = {Double.NaN};
            r.hull0 = hull(player);
            r.crew0 = alive(world, player);
            for (Entity s : ships) r.enemyCrew0 += alive(world, s);
            boolean over = runUntil(world, FIGHT_SECONDS, () -> {
                Entity target = null;
                boolean anyLeft = false;
                for (Entity s : ships) {
                    if (s.getComponent(PhysicsBodyComponent.class) == null || FirstPlay.beaten(world, s)) continue;
                    anyLeft = true;
                    if (target == null) target = s;
                }
                if (target != null && gun.getTarget() != target) gun.setTarget(target);
                gun.fireGuns();
                gun.launchMissile();
                gun.update(FRAME);
                if (DEBUG && frame[0]++ % 60 == 0) debug(world, player, ships, hs);
                boolean done = !anyLeft || FirstPlay.beaten(world, player) || world.getCrash() != null;
                if (done) end[0] = world.engine.getSimSeconds();
                return done;
            });
            r.seconds = (Double.isNaN(end[0]) ? world.engine.getSimSeconds() : end[0]) - t0;
            r.fired = fired[0];
            r.hits = hits[0];
            r.hull1 = hull(player);
            r.crew1 = alive(world, player);
            boolean allBeaten = true, anyEscaped = false;
            for (Entity s : ships) {
                r.enemyCrew1 += alive(world, s);
                boolean beaten = FirstPlay.beaten(world, s);
                allBeaten &= beaten;
                anyEscaped |= !beaten && s.getComponent(PhysicsBodyComponent.class) == null;
            }
            r.outcome = FirstPlay.beaten(world, player) ? "lost" : allBeaten ? "won" : anyEscaped && over ? "ran" : "draw";
            return r;
        } finally {
            world.dispose();
        }
    }

    static final boolean DEBUG = Boolean.getBoolean("enemy.debug");

    private static void debug(GameWorld world, Entity player, List<Entity> ships, List<HostileComponent> hs) {
        StringBuilder b = new StringBuilder(String.format("t=%.0f", world.engine.getSimSeconds()));
        for (int i = 0; i < ships.size(); i++) {
            Entity s = ships.get(i);
            HostileComponent h = hs.get(i);
            PhysicsBodyComponent pb = s.getComponent(PhysicsBodyComponent.class);
            PhysicsBodyComponent pp = player.getComponent(PhysicsBodyComponent.class);
            float d = pb == null || pb.body == null ? -1 : pb.body.getPosition().dst(pp.body.getPosition());
            var sensor = s.getComponent(com.ringsofiron.combatsys.components.SensorComponent.class);
            var combat = s.getComponent(com.ringsofiron.aicrew.pilot.NpcCombatComponent.class);
            b.append(String.format(" | d=%.0f hull=%.2f broke=%s lock=%s track=%s stance=%s dis=%s alive=%d", d, h.hull, h.brokeOff,
                    sensor == null ? "-" : sensor.lockedTarget == player, sensor == null ? "-" : sensor.trackFor(player) != null,
                    combat == null ? "-" : combat.stance, ShipDisablement.isDisabled(s), alive(world, s)));
            var tr = sensor == null ? null : sensor.trackFor(player);
            if (tr != null && pb != null && pb.body != null) b.append(String.format(" trk p=(%.0f,%.0f) true=(%.0f,%.0f) me=(%.0f,%.0f) v=(%.1f,%.1f) truev=(%.1f,%.1f) a=(%.2f,%.2f) fixes=%d last=%.2f",
                    tr.p.x, tr.p.y, pp.body.getPosition().x, pp.body.getPosition().y, pb.body.getPosition().x, pb.body.getPosition().y,
                    tr.v.x, tr.v.y, pp.body.getLinearVelocity().x, pp.body.getLinearVelocity().y, tr.a.x, tr.a.y, tr.fixes, tr.lastFixTime));
            var w = s.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
            if (w != null) for (var m : w.mounts) {
                var sub = m.subsystem == null ? null : m.subsystem.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class);
                b.append(String.format(" [%s eng=%s sol=%s on=%s cd=%.1f free=%s skill=%.2f op=%s pow=%s on=%s hp=%.2f shots=%d trig=%s]", m.def == null ? null : m.def.id,
                        m.engaged == player, m.solution.valid, m.onTarget, m.cooldown, m.weaponsFree, m.gunnerySkill,
                        sub == null ? "-" : sub.isOperational, sub == null ? "-" : sub.powered, sub == null ? "-" : sub.switchedOn,
                        sub == null ? -1f : sub.health, m.shotsFired, m.engagedFor));
            }
        }
        System.out.println(b);
    }

    private static Entity bringIn(GameWorld world, String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, world.macroSim.gateway().anchorParentUuid(),
                s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private static int alive(GameWorld world, Entity ship) {
        int n = 0;
        for (Entity e : world.crew.crewEntities()) {
            if (world.crew.position(e).ship == ship
                    && e.getComponent(com.ringsofiron.aicrew.CrewMemberComponent.class).isAlive()) n++;
        }
        return n;
    }

    private static float hull(Entity ship) {
        return ShipDisablement.hullIntegrity(ship.getComponent(ShipGridComponent.class));
    }

    private static void run(GameWorld world, int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private static boolean runUntil(GameWorld world, float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }
}
