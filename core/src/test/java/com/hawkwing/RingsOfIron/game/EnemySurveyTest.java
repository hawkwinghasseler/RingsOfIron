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
 * from 4 km. Round 6 (part 4) adds the frigate, and the pirate base's defence: the player jumps in 8 km
 * from the base, which scrambles its wing and mans its guns, and fights them all until it strikes.
 * The quick check here runs in the suite; the full survey (10 fights each) runs with
 * {@code ./gradlew :core:test --tests '*EnemySurveyTest' -PenemySurvey} and writes its table to
 * {@code core/build/enemy-survey.txt} ({@code -PsurveyRows=raider} keeps only rows whose enemy names it).
 * Round 8 adds the game's own start for the lone and wing rows, a hunt from the rails ({@link #hunt}):
 * the survey prints those rows from both starts, and {@code -PsurveyStart=intercept} makes the hunt the
 * main rows' start.
 */
class EnemySurveyTest {
    static final float FRAME = 1f / 60f;
    /** Longest fight, sim seconds. */
    static final float FIGHT_SECONDS = 400f;
    /** The player's ships: the corvette as a Lieutenant, the gunship as a Lieutenant Commander, the frigate as a Commander. */
    static final String[] OUR_SHIPS = {"Corvette", "Gunship", "Frigate"};
    static final int CORVETTE = 0, GUNSHIP = 1, FRIGATE = 2;
    /** The enemy "ship" that stands for the pirate base's whole defence. */
    static final String BASE = "base";

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    /** One fight's outcome. */
    static final class Result {
        String outcome;
        double seconds;
        int fired, hits;
        /** Rounds the base's gun platforms fired (a raid only), and how many hit us. */
        int gunFired, gunHits;
        float hull0, hull1;
        int crew0, crew1;
        int enemyCrew0, enemyCrew1;
        String loadouts = "";
        double difficulty;
        /** How each enemy ship ended, for the detail lines: beaten and why, or still fighting. */
        String ends = "";
        /** Why our ship was beaten, when it was. */
        String ourEnd = "";
        /** The base's wing, by name in launch order (a raid only). */
        final List<String> wing = new ArrayList<>();
        /** How each enemy ship came into the bubble, on the intercept start. */
        String entry = "";

        @Override
        public String toString() {
            return String.format("%s in %.0f s; they fired %d, %d hit%s; our hull %.2f -> %.2f; crew %d -> %d; theirs %d -> %d [%s, difficulty %.2f]%s",
                    outcome, seconds, fired, hits, gunFired > 0 ? String.format("; its guns fired %d, %d hit", gunFired, gunHits) : "", hull0, hull1, crew0, crew1, enemyCrew0, enemyCrew1, loadouts, difficulty,
                    ends.isEmpty() ? "" : " (" + ends + ")") + (ourEnd.isEmpty() ? "" : "; we were beaten: " + ourEnd)
                    + (entry.isEmpty() ? "" : "; came in " + entry);
        }
    }

    @Test
    void aLoneRaiderShootsBackAtTheCorvette() {
        Result r = fight(2026, false, HostileTraffic.RAIDER, 1, "raider_chaingun");
        System.out.println("RESULT " + r);
        assertTrue(r.fired > 0, "it fired: " + r);
    }

    @Test
    void theBasesDefenceFightsTheCorvette() {
        Result r = fight(2026, CORVETTE, BASE, 0, null);
        System.out.println("RESULT " + r);
        assertTrue(r.fired > 0, "it fired: " + r);
        assertTrue(r.loadouts.contains("battery_"), "its guns joined the defence: " + r);
    }

    @Test
    @EnabledIfSystemProperty(named = "enemy.probe", matches = ".+")
    void probe() {
        String[] p = System.getProperty("enemy.probe").split(",");
        // seed, our ship (false/true for the corvette/gunship, or 0-2), enemy ship (or "base"), wing size[, loadout]
        int ours = p[1].equals("true") ? GUNSHIP : p[1].equals("false") ? CORVETTE : Integer.parseInt(p[1]);
        System.out.println("RESULT " + fight(Long.parseLong(p[0]), ours, p[2], Integer.parseInt(p[3]), p.length > 4 ? p[4] : null));
    }

    @Test
    @EnabledIfSystemProperty(named = "enemy.survey", matches = ".+")
    void survey() throws IOException {
        StringBuilder out = new StringBuilder("| Enemy | Our ship | Won | Ran | Lost | Fights they fired in | Rounds at us | Hits | Base guns' rounds | Our hull after (min-mean) | Our crew killed | Their crew left | Time to beat (mean) |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        // The lone and wing rows again from the other start, so both show (round 8: -PsurveyStart picks the main rows' start)
        String other = INTERCEPT.equals(START) ? REST : INTERCEPT;
        String[][] enemies = {{"Lone raider", HostileTraffic.RAIDER, "1", null, START}, {"Wing of two raiders", HostileTraffic.RAIDER, "2", null, START},
                {"Lone raider, " + startName(other), HostileTraffic.RAIDER, "1", null, other},
                {"Wing of two raiders, " + startName(other), HostileTraffic.RAIDER, "2", null, other},
                {"Pirate gunship", HostileTraffic.PIRATE_GUNSHIP, "1", null, REST}, {"Ace's gunship", HostileTraffic.PIRATE_GUNSHIP, "1", "ace", REST},
                {"Ace's chaingun raider", HostileTraffic.RAIDER, "1", "ace:raider_ace_chaingun", REST},
                {"The base's defence", BASE, "0", null, REST}};
        int trials = Integer.getInteger("enemy.survey.trials", 10);
        String only = System.getProperty("enemy.survey.rows", "").toLowerCase(java.util.Locale.ROOT);
        StringBuilder detail = new StringBuilder();
        for (String[] enemy : enemies) {
            if (!enemy[0].toLowerCase(java.util.Locale.ROOT).contains(only)) continue;
            for (int ship = CORVETTE; ship <= FRIGATE; ship++) {
                // An ace takes a gunship only once the player flies one
                if ("ace".equals(enemy[3]) && ship == CORVETTE) continue;
                int won = 0, ran = 0, lost = 0, fired = 0, hits = 0, killed = 0, theirs = 0, firedIn = 0, gunFired = 0, gunHits = 0;
                float minHull = 1, sumHull = 0;
                double beatTime = 0;
                for (int i = 0; i < trials; i++) {
                    Result r = fight(1 + i, ship, enemy[1], Integer.parseInt(enemy[2]), enemy[3], enemy[4]);
                    detail.append(enemy[0]).append(" v ").append(OUR_SHIPS[ship].toLowerCase(java.util.Locale.ROOT)).append(", seed ").append(1 + i)
                            .append(": ").append(r).append('\n');
                    switch (r.outcome) {
                        case "won": won++; beatTime += r.seconds; break;
                        case "ran": ran++; break;
                        case "lost": lost++; break;
                        default: break;
                    }
                    fired += r.fired;
                    hits += r.hits;
                    gunFired += r.gunFired;
                    gunHits += r.gunHits;
                    if (r.fired + r.gunFired > 0) firedIn++;
                    minHull = Math.min(minHull, r.hull1);
                    sumHull += r.hull1;
                    killed += r.crew0 - r.crew1;
                    theirs += r.enemyCrew1;
                }
                out.append(String.format("| %s | %s | %d | %d | %d | %d | %.0f | %.0f | %s | %.2f-%.2f | %.1f | %.1f | %s |%n", enemy[0],
                        OUR_SHIPS[ship], won, ran, lost, firedIn, fired / (double) trials, hits / (double) trials,
                        BASE.equals(enemy[1]) ? String.format("%.0f (%.0f hit)", gunFired / (double) trials, gunHits / (double) trials) : "-", minHull,
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
     * A new seeded Patrol career in the corvette as a Lieutenant, or in the gunship bought at Anvil Yards
     * as a Lieutenant Commander; then a wing of {@code size} pirates of {@code shipId} (armed with
     * {@code loadout}, or for the difficulty when null; "ace" makes the first an ace on its refit)
     * comes for the player from 4 km and both sides fight with everything they have.
     */
    static Result fight(long seed, boolean gunship, String shipId, int size, String loadout) {
        return fight(seed, gunship ? GUNSHIP : CORVETTE, shipId, size, loadout);
    }

    /**
     * As {@link #fight(long, boolean, String, int, String)}, in {@code ours} ({@link #CORVETTE}, {@link #GUNSHIP}
     * or {@link #FRIGATE}, bought at Anvil Yards as a Commander and crewed from its hiring pool); a
     * {@code shipId} of {@link #BASE} raids the pirate base instead, from 8 km.
     */
    /**
     * Gives the ranks the survey's standing earned at once, and lets the yard sell a new rank's ship: the
     * survey isn't about round 7's time in grade, so each rank counts as held long ago.
     */
    static void servedTime(GameWorld world) {
        ServiceRecord record = world.standing.record();
        for (int i = 0; i < world.standing.ranks().size(); i++) {
            record.promotedAt = -1e9;
            world.standing.promoteDue();
        }
        record.promotedAt = -1e9;
        java.util.Arrays.fill(record.promotions, -1e9);
    }

    static Result fight(long seed, int ours, String shipId, int size, String loadout) {
        return fight(seed, ours, shipId, size, loadout, START);
    }

    /**
     * As {@link #fight(long, int, String, int, String)}, from {@code start}: {@link #REST} brings the wing in
     * at rest 4 km out (the survey's start since round 5), {@link #INTERCEPT} lets it hunt the player from
     * its lurk as the game does ({@link #hunt}).
     */
    static Result fight(long seed, int ours, String shipId, int size, String loadout, String start) {
        boolean gunship = ours != CORVETTE;
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        GameWorld world = new GameWorld();
        try {
            GameScenario game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(seed, "patrol"));
            game.beginCareer();
            run(world, 120);
            assertTrue(world.docking.undock(game.player));
            run(world, 60);
            float need = world.standing.ranks().get(ours + 1).standing - world.standing.standing(world.standing.sponsor());
            if (need > 0) world.standing.reward(world.standing.sponsor(), need + 0.5f, "survey");
            servedTime(world);
            if (gunship) {
                assertTrue(game.dockAt(game.shipyard));
                run(world, 120);
                game.player.getComponent(TraderComponent.class).credits = ours == FRIGATE ? 700_000 : 400_000;
                String said = world.shipyard.buy(ours == FRIGATE ? "patrol_frigate" : "patrol_gunship");
                assertTrue(said.startsWith("Bought"), said);
                run(world, 120);
                if (ours == FRIGATE) crewUp(world, game);
                assertTrue(world.docking.undock(game.player));
                run(world, 60);
            }
            if (BASE.equals(shipId)) {
                world.pirateBase.reveal(null);
                return RaidRoutine.fly(world, game, () -> { });
            }
            String lurk = game.highport;
            // Armed as the difficulty would arm it, drawn by the seed (the top-up's own draw is the same in every game)
            // "ace", or "ace:<fit>" for an ace armed with that fit (the career's first ace, Red Kestrel)
            boolean ace = loadout != null && (loadout.equals("ace") || loadout.startsWith("ace:"));
            if (ace) loadout = loadout.startsWith("ace:") ? loadout.substring(4) : null;
            String fit = loadout != null ? loadout : Loadouts.pick(world.combatDefs, shipId, Difficulty.of(world), false,
                    new java.util.Random(seed * 7_919L + shipId.hashCode())).id;
            List<String> ids = world.hostiles.spawnWing(shipId, size, lurk, fit);
            assertEquals(size, ids.size());
            List<Entity> ships = new ArrayList<>();
            List<HostileComponent> hs = new ArrayList<>();
            Result r = new Result();
            r.difficulty = Difficulty.of(world);
            if (ace) {
                HostileComponent h = world.macroSim.hostiles().get(ids.get(0));
                assertTrue(PirateAces.promote(world, world.engine.getEntity(ids.get(0)), h, world.macroSim.hostiles().all().values(),
                        r.difficulty, false));
                if (loadout != null) {
                    Loadouts.apply(world.engine.getEntity(ids.get(0)), world.combatDefs.loadouts.get(loadout), world.combatDefs);
                    h.loadout = loadout;
                }
            }
            for (int i = 0; i < ids.size(); i++) {
                HostileComponent h = world.macroSim.hostiles().get(ids.get(i));
                hs.add(h);
                r.loadouts += (i == 0 ? "" : ", ") + h.loadout;
                if (INTERCEPT.equals(start)) {
                    ships.add(world.engine.getEntity(ids.get(i)));
                } else {
                    h.targetUuid = HostileComponent.PLAYER;
                    ships.add(bringIn(world, ids.get(i), 4_000, i * 400));
                }
            }
            if (INTERCEPT.equals(start)) r.entry = hunt(world, game.player, ids);
            Entity player = game.player;
            PlayerGunnery gun = new PlayerGunnery(world, player);
            int[] fired = new int[1], hits = new int[1];
            world.combatEvents.shots.add((sig, shot) -> { if (ships.contains(shot.shooter)) fired[0]++; });
            world.combatEvents.impacts.add((sig, hit) -> { if (ships.contains(hit.owner) && hit.target == player) hits[0]++; });
            if (DEBUG) world.combatEvents.impacts.add((sig, hit) -> {
                if (hit.target == player) System.out.println(String.format("IMPACT t=%.2f by %s round %s ram=%s at (%.1f,%.1f) v=(%.0f,%.0f)",
                        world.engine.getSimSeconds(), hit.owner == player ? "US" : ships.contains(hit.owner) ? "THEM" : String.valueOf(hit.owner),
                        hit.def == null ? null : hit.def.id, hit.ram, hit.worldPoint.x, hit.worldPoint.y, hit.velocity.x, hit.velocity.y));
            });
            if (DEBUG) world.combatEvents.shots.add((sig, shot) -> { if (shot.shooter == player) System.out.println(String.format(
                    "SHOT t=%.2f %s from (%.1f,%.1f) dir (%.2f,%.2f)", world.engine.getSimSeconds(), shot.weaponDefId,
                    shot.muzzlePoint.x, shot.muzzlePoint.y, shot.direction.x, shot.direction.y)); });
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
                    if (s.getComponent(PhysicsBodyComponent.class) == null) {
                        // A wingman still on its way in from the rails
                        HostileComponent h = s.getComponent(HostileComponent.class);
                        if (h != null && h.state == HostileComponent.State.INTERCEPTING && h.huntingPlayer()) anyLeft = true;
                        continue;
                    }
                    if (FirstPlay.beaten(world, s)) continue;
                    anyLeft = true;
                    if (target == null) target = s;
                }
                if (target != null && gun.getTarget() != target) gun.setTarget(target);
                gun.fireGuns();
                gun.launchMissile();
                gun.update(FRAME);
                if (DEBUG && frame[0]++ % 15 == 0) debug(world, player, ships, hs);
                boolean done = !anyLeft || FirstPlay.beaten(world, player) || alive(world, player) == 0 || world.getCrash() != null;
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
                r.ends += (r.ends.isEmpty() ? "" : ", ") + end(world, s);
                boolean beaten = FirstPlay.beaten(world, s);
                allBeaten &= beaten;
                anyEscaped |= !beaten && s.getComponent(PhysicsBodyComponent.class) == null;
            }
            r.outcome = FirstPlay.beaten(world, player) || r.crew1 == 0 ? "lost" : allBeaten ? "won" : anyEscaped && over ? "ran" : "draw";
            if ("lost".equals(r.outcome)) r.ourEnd = String.valueOf(ShipDisablement.reason(player));
            return r;
        } finally {
            world.dispose();
        }
    }

    /** Hires the best of Anvil Yards' pool until the player's ship has the crew it calls for. */
    private static void crewUp(GameWorld world, GameScenario game) {
        var def = world.getContent().defs.get("ship",
                game.player.getComponent(com.ringsofiron.moddingdata.components.DefRefComponent.class).defId,
                com.ringsofiron.moddingdata.defs.ShipDef.class);
        for (int tries = 0; tries < 20 && alive(world, game.player) < def.crew; tries++) {
            CrewHiring.Candidate best = null;
            for (CrewHiring.Candidate c : world.crewHiring.pool(game.shipyard)) if (best == null) best = c;
            if (best == null || world.crewHiring.hire(best.key) != null) break;
        }
        run(world, 60);
    }

    static final boolean DEBUG = Boolean.getBoolean("enemy.debug");

    /** The survey's starts for the lone and wing rows: at rest 4 km out, or hunting the player from the rails. */
    static final String REST = "rest", INTERCEPT = "intercept";
    static String startName(String start) {
        return INTERCEPT.equals(start) ? "hunting from the rails" : "at rest 4 km out";
    }

    /** The start of the survey's main lone and wing rows, the quick checks and the probe ({@code -PsurveyStart}). */
    static final String START = System.getProperty("enemy.survey.start", REST);

    /**
     * The game's own start: the wing hunts the player from its lurk off Highport, on the rendezvous
     * {@code HostileTrafficController} plans, and comes into the bubble when the rails bring it within
     * 20 km. The world's other pirates rest meanwhile, and the wing is made bold enough to hunt whatever
     * the player flies (a lone raider wouldn't dare a gunship). Returns how each ship came in: range,
     * closing speed and how far its nose was off the player.
     */
    static String hunt(GameWorld world, Entity player, List<String> ids) {
        var hostiles = world.macroSim.hostiles();
        double now = world.macroSim.now();
        for (var e : hostiles.all().entrySet()) {
            if (!ids.contains(e.getKey()) && e.getValue().isPirate()) e.getValue().restUntil = now + 1e7;
        }
        var nerve = hostiles.playerFirepower;
        hostiles.playerFirepower = () -> 0;
        hostiles.playerGraceUntil = 0;
        HostileComponent leader = hostiles.get(ids.get(0));
        String[] came = new String[ids.size()];
        double until = now + 12 * 3600.0;
        boolean any = false;
        for (int frame = 0; frame < 400_000 && world.macroSim.now() < until && !any; frame++) {
            if (leader.huntingPlayer() && leader.state == HostileComponent.State.INTERCEPTING) hostiles.playerFirepower = nerve;
            if (world.timeWarp.getWarp() == 1 && world.getCrash() == null) {
                for (int level : new int[]{10_000, 1_000, 100, 10}) if (world.timeWarp.setWarp(level)) break;
            }
            world.update(FRAME);
            for (int i = 0; i < ids.size(); i++) any |= world.engine.getEntity(ids.get(i)).getComponent(PhysicsBodyComponent.class) != null;
        }
        hostiles.playerFirepower = nerve;
        world.timeWarp.setWarp(1);
        assertTrue(any, "the wing came hunting into the bubble: " + leader.state + " " + leader.targetUuid);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            Entity s = world.engine.getEntity(ids.get(i));
            b.append(i == 0 ? "" : ", ").append(arrival(s, player));
        }
        return b.toString();
    }

    /** Range, closing speed, relative speed and nose off the player of a ship in the bubble, or "on the rails". */
    static String arrival(Entity ship, Entity player) {
        PhysicsBodyComponent pb = ship.getComponent(PhysicsBodyComponent.class);
        if (pb == null || pb.body == null) return "still on the rails";
        var us = player.getComponent(PhysicsBodyComponent.class).body;
        com.badlogic.gdx.math.Vector2 to = us.getPosition().cpy().sub(pb.body.getPosition());
        com.badlogic.gdx.math.Vector2 rv = pb.body.getLinearVelocity().cpy().sub(us.getLinearVelocity());
        float units = com.ringsofiron.physicsexterior.PhysicsManager.METERS_PER_UNIT;
        float range = to.len() * units, closing = rv.dot(to.cpy().nor()) * units, speed = rv.len() * units;
        float off = Math.abs(com.badlogic.gdx.math.MathUtils.radDeg * com.badlogic.gdx.math.MathUtils.atan2(
                to.y * (float) Math.cos(pb.body.getAngle()) - to.x * (float) Math.sin(pb.body.getAngle()),
                to.x * (float) Math.cos(pb.body.getAngle()) + to.y * (float) Math.sin(pb.body.getAngle())));
        return String.format(java.util.Locale.ROOT, "%.1f km, closing %.0f m/s (%.0f m/s), nose %.0f deg off", range / 1000f, closing, speed, off);
    }

    /** How an enemy ship ended a fight: why it is beaten (disabled, its reactor down, its guns out), or "fighting". */
    static String end(GameWorld world, Entity ship) {
        if (ship.getComponent(PhysicsBodyComponent.class) == null) return "gone";
        if (!FirstPlay.beaten(world, ship)) return "fighting";
        var why = ShipDisablement.reason(ship);
        if (why != null) return why.toString().toLowerCase(java.util.Locale.ROOT);
        var reg = ship.getComponent(com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent.class);
        if (reg != null) for (Entity e : reg.subsystems) {
            var sub = e.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class);
            if (sub != null && sub.type == com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.REACTOR && sub.isOperational) return "disarmed";
        }
        return "unpowered";
    }

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
            float closing = pb == null || pb.body == null ? 0 : pb.body.getLinearVelocity().cpy().sub(pp.body.getLinearVelocity())
                    .dot(pp.body.getPosition().cpy().sub(pb.body.getPosition()).nor()) * 10f;
            var fc = s.getComponent(com.ringsofiron.physicsexterior.components.FlightControlComponent.class);
            int[] up = world.disablement.crewCount(s);
            b.append(String.format(" | crew %d/%d", up[1], up[0]));
            for (Entity e : world.engine.getEcsEngine().getEntitiesFor(com.ringsofiron.aicrew.CrewService.CREW)) {
                var gp = e.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class);
                if (gp.ship != s) continue;
                var cm = e.getComponent(com.ringsofiron.aicrew.CrewMemberComponent.class);
                b.append(String.format(" {%s %s @%d,%d %s}", cm.role, cm.state, gp.tileX(), gp.tileY(), cm.activity));
            }
            var th = s.getComponent(com.ringsofiron.physicsexterior.components.ThrusterComponent.class);
            if (pb != null && pb.body != null && th != null) b.append(String.format(" | mass %.0f fwd %.2g rev %.2g lat %.2g torque %.2g inertia %.3g",
                    com.ringsofiron.physicsexterior.PhysicsManager.getMass(pb.body), th.forwardThrust, th.reverseThrust, th.lateralThrust, th.maxTorque,
                    com.ringsofiron.physicsexterior.PhysicsManager.getInertia(pb.body)));
            b.append(String.format(" | mode=%s closing=%.0f m/s fwd=%.2f turn=%.2f", combat == null ? "-" : combat.mode, closing,
                    fc == null ? 0f : fc.forward, fc == null ? 0f : fc.turn));
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
        var ours = player.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
        b.append(" || us: hull=").append(String.format("%.2f", hull(player))).append(" disabled=").append(ShipDisablement.reason(player));
        if (ours != null) for (var m : ours.mounts) {
            var sub = m.subsystem == null ? null : m.subsystem.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class);
            b.append(String.format(" [%s op=%s pow=%s hp=%.2f shots=%d]", m.def == null ? null : m.def.id,
                    sub == null ? "-" : sub.isOperational, sub == null ? "-" : sub.powered, sub == null ? -1f : sub.health, m.shotsFired));
        }
        var reg = player.getComponent(com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent.class);
        if (reg != null) for (Entity se : reg.subsystems) {
            var sub = se.getComponent(com.ringsofiron.interiorgrid.SubsystemComponent.class);
            if (sub != null && sub.type == com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType.REACTOR)
                b.append(String.format(" reactor op=%s hp=%.2f", sub.isOperational, sub.health));
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

    static int alive(GameWorld world, Entity ship) {
        int n = 0;
        for (Entity e : world.crew.crewEntities()) {
            if (world.crew.position(e).ship == ship
                    && e.getComponent(com.ringsofiron.aicrew.CrewMemberComponent.class).isAlive()) n++;
        }
        return n;
    }

    static float hull(Entity ship) {
        return ShipDisablement.hullIntegrity(ship.getComponent(ShipGridComponent.class));
    }

    private static void run(GameWorld world, int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    static boolean runUntil(GameWorld world, float seconds, BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            world.update(FRAME);
            if (done.getAsBoolean()) return true;
        }
        return done.getAsBoolean();
    }
}
