package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * The raid on the pirates' base, flown one way by the enemy survey ({@link EnemySurveyTest}) and the
 * round 5 career ({@link Round5CareerTest}), so the career's raid is a sample of the fight the survey
 * measures (round 8). The helm holds position on the base; each defender is fought as the base
 * launches it, none brought in early; and the ship never jumps once the fight is on. Round 7's career
 * brought the second wave in at 3 km and jumped beside any defender past 2.5 km, which kept it away
 * from the base's guns: it took the base at hull 0.83 to 0.99 where the survey's frigate lost 4 in 10.
 */
final class RaidRoutine {
    /** How far from the base the player jumps in, m. */
    static final float JUMP_IN = 8_000;
    /** Longest raid on the base, sim seconds: a wing in two waves and the base's guns. */
    static final float RAID_SECONDS = 900f;

    private RaidRoutine() {
    }

    /**
     * The raid: the player jumps in {@link #JUMP_IN} from the pirate base ({@code arrived} runs then,
     * before any shot), which scrambles its wing and mans its guns, and fights each in the order the
     * base lists them until it strikes, the player is beaten, or {@link #RAID_SECONDS} pass.
     */
    static EnemySurveyTest.Result fly(GameWorld world, GameScenario game, Runnable arrived) {
        return fly(world, game, arrived, -1);
    }

    /**
     * As {@link #fly(GameWorld, GameScenario, Runnable)}; with {@code redraw} of 0 or more, every
     * defender and gun platform the base drew with a railgun is refitted as it scrambles, from the fits
     * without one, by dice seeded from {@code redraw} ({@link #withoutRailguns}): the survey's no-railgun
     * row (round 10), the draw the career meets most.
     */
    static EnemySurveyTest.Result fly(GameWorld world, GameScenario game, Runnable arrived, long redraw) {
        return fly(world, game, arrived, redraw, 0);
    }

    /**
     * As {@link #fly(GameWorld, GameScenario, Runnable, long)}; with {@code enoughShots} above 0 the raid
     * ends once the base's wing has fired that many rounds: a quick check that the defence
     * fights needn't play out the whole raid.
     */
    static EnemySurveyTest.Result fly(GameWorld world, GameScenario game, Runnable arrived, long redraw, int enoughShots) {
        EnemySurveyTest.Result r = new EnemySurveyTest.Result();
        r.difficulty = Difficulty.of(world);
        Entity base = world.engine.getEntity(world.pirateBase.uuid());
        world.rescue.jumpBeside(game.player, base, JUMP_IN);
        arrived.run();
        // The helm holds position on the base, as a player would, or the guns' recoil pushes the ship out
        // past where the base strikes (round 6: 7 km became 25 km over a raid)
        holdOn(world, game, world.pirateBase.uuid());
        EnemySurveyTest.runUntil(world, 20f, () -> !world.pirateBase.defenders().isEmpty());
        List<String> names = new ArrayList<>(world.pirateBase.defenders());
        assertFalse(names.isEmpty(), "the base scrambled");
        if (redraw >= 0) withoutRailguns(world, redraw);
        r.wing.addAll(names);
        List<Entity> ships = new ArrayList<>();
        for (String name : names) {
            for (var e : world.macroSim.hostiles().all().entrySet()) {
                if (!name.equals(e.getValue().displayName)) continue;
                ships.add(world.engine.getEntity(e.getKey()));
                r.loadouts += (r.loadouts.isEmpty() ? "" : ", ") + e.getValue().loadout;
            }
        }
        // Its guns fire from their moorings; the base strikes once the wing is beaten, so they are not fought
        List<Entity> batteries = new ArrayList<>();
        for (String name : world.pirateBase.guns()) {
            for (var e : world.macroSim.hostiles().all().entrySet()) {
                if (!name.equals(e.getValue().displayName)) continue;
                r.loadouts += ", " + e.getValue().loadout;
                batteries.add(world.engine.getEntity(e.getKey()));
            }
        }
        Entity player = game.player;
        PlayerGunnery gun = new PlayerGunnery(world, player);
        int[] fired = new int[1], hits = new int[1], gunFired = new int[1], gunHits = new int[1];
        world.combatEvents.shots.add((sig, shot) -> {
            if (ships.contains(shot.shooter)) fired[0]++;
            else if (batteries.contains(shot.shooter)) gunFired[0]++;
        });
        world.combatEvents.impacts.add((sig, hit) -> {
            if (hit.target != player) return;
            if (ships.contains(hit.owner)) hits[0]++;
            else if (batteries.contains(hit.owner)) gunHits[0]++;
        });
        // The trace (-PenemyDebug): who hit us with what, and the hull after each ten seconds
        java.util.Map<String, int[]> hitsBy = new java.util.TreeMap<>();
        if (EnemySurveyTest.DEBUG) world.combatEvents.impacts.add((sig, hit) -> {
            if (hit.target != player) return;
            HostileComponent hc = hit.owner == null ? null : hit.owner.getComponent(HostileComponent.class);
            String key = (hc == null ? String.valueOf(hit.owner) : hc.displayName) + " " + (hit.def == null ? "ram/blast" : hit.def.id);
            hitsBy.computeIfAbsent(key, k -> new int[1])[0]++;
        });
        java.util.Map<String, int[]> shotsBy = new java.util.TreeMap<>();
        if (EnemySurveyTest.DEBUG) world.combatEvents.shots.add((sig, shot) -> {
            if (shot.shooter == player) return;
            HostileComponent hc = shot.shooter == null ? null : shot.shooter.getComponent(HostileComponent.class);
            shotsBy.computeIfAbsent((hc == null ? "?" : hc.displayName) + " " + shot.weaponDefId, k -> new int[1])[0]++;
        });
        double t0 = world.engine.getSimSeconds();
        double[] end = {Double.NaN};
        int[] frame = new int[1];
        // The captain killed and the crew carrying them home ends the raid: the bubble is gone with them
        boolean[] carried = new boolean[1];
        world.carriedHome.add((sig, tow) -> carried[0] = true);
        r.hull0 = EnemySurveyTest.hull(player);
        r.crew0 = EnemySurveyTest.alive(world, player);
        for (Entity s : ships) r.enemyCrew0 += EnemySurveyTest.alive(world, s);
        boolean over = EnemySurveyTest.runUntil(world, RAID_SECONDS, () -> {
            Entity target = null;
            for (Entity s : ships) {
                if (s.getComponent(PhysicsBodyComponent.class) == null || FirstPlay.beaten(world, s)) continue;
                target = s;
                break;
            }
            if (target != null && gun.getTarget() != target) gun.setTarget(target);
            gun.fireGuns();
            gun.launchMissile();
            gun.update(EnemySurveyTest.FRAME);
            // A threat drops a standing helm order (HelmOrders.threat): the player gives it again
            if (frame[0] % 300 == 0 && world.nav.helm.mode() == HelmOrderComponent.Mode.NONE) {
                world.nav.target.select(world.pirateBase.uuid());
                world.nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION);
            }
            if (EnemySurveyTest.DEBUG && frame[0] % 600 == 0) {
                var pp = player.getComponent(PhysicsBodyComponent.class).body.getPosition();
                float m = com.ringsofiron.physicsexterior.PhysicsManager.METERS_PER_UNIT;
                com.badlogic.gdx.math.Vector2 bp0 = new com.badlogic.gdx.math.Vector2(), bv0 = new com.badlogic.gdx.math.Vector2(),
                        pp0 = new com.badlogic.gdx.math.Vector2(), pv0 = new com.badlogic.gdx.math.Vector2();
                boolean seen0 = com.ringsofiron.combatsys.ContactState.read(base, bp0, bv0);
                com.ringsofiron.combatsys.ContactState.read(player, pp0, pv0);
                StringBuilder b = new StringBuilder(String.format("TRACE t=%.0f hull=%.3f base %.0f m%s", world.engine.getSimSeconds() - t0,
                        EnemySurveyTest.hull(player), bp0.dst(pp0), seen0 ? "" : " (unseen)"));
                List<Entity> all = new ArrayList<>(ships);
                all.addAll(batteries);
                for (Entity sh : all) {
                    PhysicsBodyComponent pb = sh.getComponent(PhysicsBodyComponent.class);
                    HostileComponent hc = sh.getComponent(HostileComponent.class);
                    b.append(String.format(" | %s %s %.0f m", hc.displayName, FirstPlay.beaten(world, sh) ? "beaten" : "up",
                            pb == null || pb.body == null ? -1f : pb.body.getPosition().dst(pp) * m));
                }
                b.append(" || shots ").append(shotsBy.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()[0])
                        .collect(java.util.stream.Collectors.joining(", ")));
                b.append(" || hits ").append(hitsBy.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()[0])
                        .collect(java.util.stream.Collectors.joining(", ")));
                System.out.println(b);
            }
            if (EnemySurveyTest.DEBUG && (frame[0] % 1800 == 0 || FirstPlay.beaten(world, player))) {
                StringBuilder b = new StringBuilder(String.format("RAID t=%.0f", world.engine.getSimSeconds()));
                for (int i = 0; i < ships.size(); i++) {
                    Entity sh = ships.get(i);
                    PhysicsBodyComponent pb = sh.getComponent(PhysicsBodyComponent.class);
                    HostileComponent hc = sh.getComponent(HostileComponent.class);
                    b.append(String.format(" | %s body=%s beaten=%s why=%s alive=%d hull=%.2f broke=%s d=%.0f", names.get(i), pb != null,
                            FirstPlay.beaten(world, sh), ShipDisablement.reason(sh), EnemySurveyTest.alive(world, sh), EnemySurveyTest.hull(sh), hc == null ? null : hc.brokeOff,
                            pb == null || pb.body == null ? -1f : pb.body.getPosition().dst(player.getComponent(PhysicsBodyComponent.class).body.getPosition())));
                }
                com.badlogic.gdx.math.Vector2 bp = new com.badlogic.gdx.math.Vector2(), bv = new com.badlogic.gdx.math.Vector2(),
                        pp = new com.badlogic.gdx.math.Vector2(), pv = new com.badlogic.gdx.math.Vector2();
                boolean seen = com.ringsofiron.combatsys.ContactState.read(base, bp, bv);
                com.ringsofiron.combatsys.ContactState.read(player, pp, pv);
                b.append(String.format(" || us why=%s beaten=%s base %s at %.0f m, closing %.1f m/s, helm %s", ShipDisablement.reason(player),
                        FirstPlay.beaten(world, player), seen, bp.dst(pp), bv.dst(pv), world.nav.helm.describe()));
                System.out.println(b);
            }
            frame[0]++;
            boolean done = !world.pirateBase.standing() || FirstPlay.beaten(world, player) || EnemySurveyTest.alive(world, player) == 0
                    || world.getCrash() != null || carried[0] || (enoughShots > 0 && fired[0] >= enoughShots);
            if (done) end[0] = world.engine.getSimSeconds();
            return done;
        });
        r.seconds = (Double.isNaN(end[0]) ? world.engine.getSimSeconds() : end[0]) - t0;
        r.fired = fired[0];
        r.gunFired = gunFired[0];
        r.gunHits = gunHits[0];
        r.hits = hits[0];
        r.hull1 = EnemySurveyTest.hull(player);
        r.crew1 = EnemySurveyTest.alive(world, player);
        boolean anyEscaped = false;
        for (Entity s : ships) {
            r.enemyCrew1 += EnemySurveyTest.alive(world, s);
            r.ends += (r.ends.isEmpty() ? "" : ", ") + EnemySurveyTest.end(world, s);
            anyEscaped |= !FirstPlay.beaten(world, s) && s.getComponent(PhysicsBodyComponent.class) == null;
        }
        r.outcome = FirstPlay.beaten(world, player) || r.crew1 == 0 || carried[0] ? "lost" : !world.pirateBase.standing() ? "won"
                : anyEscaped && over ? "ran" : "draw";
        if (carried[0]) r.ourEnd = "the captain was killed";
        return r;
    }

    /**
     * Refits each of the base's defenders and gun platforms that carries a railgun with a fit drawn,
     * by weight, from those its hull may draw at the difficulty that carry none; the dice are seeded
     * from {@code seed} and the ship's name, so the same seed draws the same. Hardpoints the new fit
     * leaves alone go back to the hull's stock weapon.
     */
    static void withoutRailguns(GameWorld world, long seed) {
        List<String> names = new ArrayList<>(world.pirateBase.defenders());
        names.addAll(world.pirateBase.guns());
        double d = Difficulty.of(world);
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            HostileComponent h = e.getValue();
            Entity ship = world.engine.getEntity(e.getKey());
            if (!names.contains(h.displayName) || ship == null || !railgun(ship)) continue;
            List<com.ringsofiron.combatsys.defs.LoadoutDef> fits = new ArrayList<>();
            double total = 0;
            for (var l : com.ringsofiron.combatsys.modding.Loadouts.candidates(world.combatDefs, h.shipId, d, false)) {
                boolean rail = false;
                for (String w : l.weapons) rail |= w != null && w.startsWith("railgun");
                if (rail) continue;
                fits.add(l);
                total += l.weight;
            }
            if (fits.isEmpty()) continue;
            double roll = new java.util.Random(seed * 7_919L + h.displayName.hashCode()).nextDouble() * total;
            var fit = fits.get(fits.size() - 1);
            for (var l : fits) {
                roll -= l.weight;
                if (roll < 0) {
                    fit = l;
                    break;
                }
            }
            stock(world, ship, h.shipId);
            com.ringsofiron.combatsys.modding.Loadouts.apply(ship, fit, world.combatDefs);
            h.loadout = fit.id;
            h.firepower = HostileTraffic.firepower(ship);
        }
    }

    /** True if {@code ship} mounts a railgun. */
    private static boolean railgun(Entity ship) {
        var weapons = ship.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
        if (weapons == null) return false;
        for (var m : weapons.mounts) if (m.def != null && m.def.id != null && m.def.id.startsWith("railgun")) return true;
        return false;
    }

    /** Puts the hull {@code shipId}'s stock weapon back on each of {@code ship}'s hardpoints. */
    private static void stock(GameWorld world, Entity ship, String shipId) {
        var def = world.getContent().defs.get("ship", shipId, com.ringsofiron.moddingdata.defs.ShipDef.class);
        var weapons = ship.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
        for (int i = 0; i < weapons.mounts.size && i < def.hardpoints.size(); i++) {
            var w = world.combatDefs.weapons.get(def.hardpoints.get(i).weapon);
            var mount = weapons.mounts.get(i);
            if (w == null || w == mount.def) continue;
            mount.def = w;
            mount.cooldown = w.cooldown;
            mount.beamTime = 0f;
            mount.biasSet = false;
        }
    }

    /** The helmsman takes the helm and holds position on {@code uuid}. */
    static void holdOn(GameWorld world, GameScenario game, String uuid) {
        world.nav.target.select(uuid);
        world.bridge.stand();
        EnemySurveyTest.runUntil(world, 60f, () -> world.bridge.helmsman(game.player) != null);
        String said = world.nav.helm.give(HelmOrderComponent.Mode.HOLD_POSITION);
        assertTrue(said.startsWith("Helm: hold position"), said);
    }
}
