package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A surrender holds (round 6): a pirate that gave up stays a prize when its guns are working again,
 * when the player is taken away (as by a tow) and through a save, until the player fires on it again.
 */
class SurrenderHoldsTest {
    static final float FRAME = 1f / 60f;

    Path assets;
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aSurrenderedRaiderStaysAPrizeWithItsGunsBack(@TempDir Path dir) {
        String id = pirate();
        HostileComponent h = world.macroSim.hostiles().get(id);
        String name = h.displayName;
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 2_500, 0);
        run(3);
        assertNotNull(ship.getComponent(NpcCombatComponent.class), "it came to fight");

        // Every gun silenced: it surrenders. Then its crew get the guns working again.
        for (SubsystemComponent s : turrets(ship)) s.health = 0f;
        run(2);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
        for (SubsystemComponent s : turrets(ship)) s.health = 1f;
        AtomicInteger shots = shotsBy(ship);
        run(30);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship), "a surrender holds");
        assertNull(ship.getComponent(NpcCombatComponent.class), "it does not fight again");
        assertEquals(0, shots.get(), "and holds its fire");
        assertTrue(h.surrendered);
        assertFalse(h.huntingPlayer(), "it has let go of the player");

        // The player is taken off to a station, as a tow does: it does not follow, or fire
        world.rescue.jumpBeside(game.player, world.engine.getEntity(game.highport), 3_000);
        run(30);
        assertEquals(0, shots.get(), "a prize does not follow the player and fire on them");
        assertFalse(h.huntingPlayer());

        // Through a save
        FileHandle file = new FileHandle(dir.resolve("prize.json").toFile());
        assertNull(game.quickSave(file));
        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            Entity prize = named(fresh, name);
            for (int i = 0; i < 30 / FRAME; i++) fresh.update(FRAME);
            assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(prize), "a load keeps the surrender");
            assertNull(prize.getComponent(NpcCombatComponent.class));
            assertNotSame(loaded.player, prize.getComponent(SensorComponent.class).lockedTarget, "its guns are off the player");
        } finally {
            fresh.dispose();
        }

        // Fired on again, it is back in the fight
        assertTrue(h.surrendered);
        world.hostiles.onShotAt(ship, game.player);
        assertFalse(h.surrendered);
        run(3);
        assertNotEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship), "attacked again, the surrender is off");
    }

    @Test
    void aMissileFiredBeforeTheSurrenderDoesNotLiftIt() {
        String id = pirate();
        HostileComponent h = world.macroSim.hostiles().get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 2_500, 0);
        run(3);
        for (SubsystemComponent s : turrets(ship)) s.health = 0f;
        run(2);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
        run(15);

        // A heavy missile's fragments, launched 20 s ago, land after the grace (seed 12's second raider)
        world.combatEvents.impacts.dispatch(hitFromPlayer(ship, 20f));
        assertTrue(h.surrendered, "fire from before the surrender doesn't count");
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));

        // A round fired now does
        world.combatEvents.impacts.dispatch(hitFromPlayer(ship, 0.5f));
        assertFalse(h.surrendered, "fired on again, the surrender is off");
    }

    @Test
    void aBeatenAceStaysStruckWhenFiredOnAgain() {
        String id = pirate();
        HostileComponent h = world.macroSim.hostiles().get(id);
        h.targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 2_500, 0);
        assertTrue(PirateAces.promote(world, ship, h, world.macroSim.hostiles().all().values(), 1, false));
        run(3);
        for (SubsystemComponent s : turrets(ship)) s.health = 0f;
        run(2);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
        assertTrue(h.partDropped, "beaten: its part is crated and its bounty pays");
        assertTrue(FirstPlay.aceBeaten(ship));
        for (SubsystemComponent s : turrets(ship)) s.health = 1f;
        run(15);

        // The player's guns go on firing past the grace: the ace stays struck, and out of the fight
        AtomicInteger shots = shotsBy(ship);
        world.combatEvents.impacts.dispatch(hitFromPlayer(ship, 0.5f));
        world.hostiles.onShotAt(ship, game.player);
        assertTrue(h.surrendered);
        run(10);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship), "never back in action");
        assertNull(ship.getComponent(NpcCombatComponent.class));
        assertFalse(h.brokeOff, "nor running");
        assertEquals(0, shots.get());
    }

    @Test
    void strayFireNeverBringsAPrizeBack() {
        // Round 9: in round 8's window the point defense, firing at the base's darts, hit the struck
        // raiders behind them; only fire aimed at the prize itself counts
        String id = pirate();
        HostileComponent h = world.macroSim.hostiles().get(id);
        Entity ship = surrendered(id);
        run(15);
        Entity dart = new Entity();

        var pd = hitFromPlayer(ship, 0.5f);
        pd.aimedAt = dart;
        pd.pointDefense = true;
        world.combatEvents.impacts.dispatch(pd);
        assertTrue(h.surrendered, "a point-defense round doesn't count");

        var other = hitFromPlayer(ship, 0.5f);
        other.aimedAt = world.engine.getEntity(world.macroSim.hostiles().all().keySet().stream()
                .filter(k -> !k.equals(id)).findFirst().orElseThrow());
        world.combatEvents.impacts.dispatch(other);
        assertTrue(h.surrendered, "nor a round aimed at another ship");

        var barrel = hitFromPlayer(ship, 0.5f);
        barrel.aimedAt = null;
        world.combatEvents.impacts.dispatch(barrel);
        assertTrue(h.surrendered, "nor one fired down the barrel at nothing");
        run(3);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));

        // A round aimed at the prize, fired after the grace, still does
        world.combatEvents.impacts.dispatch(hitFromPlayer(ship, 0.5f));
        assertFalse(h.surrendered, "aimed fire brings it back");
    }

    @Test
    void aimedFireWithinTheGraceDoesNotCount() {
        String id = pirate();
        HostileComponent h = world.macroSim.hostiles().get(id);
        Entity ship = surrendered(id);
        run(3);
        world.combatEvents.impacts.dispatch(hitFromPlayer(ship, 0.5f));
        assertTrue(h.surrendered, "still inside the grace");
    }

    @Test
    void thePlayersGunsFiringAtWillNeverTakeAStruckShip() {
        String id = pirate();
        Entity ship = surrendered(id);
        run(15);
        // Every mount weapons free and locked on the prize, as the crewed guns would be
        var weapons = game.player.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
        for (var m : weapons.mounts) m.weaponsFree = true;
        game.player.getComponent(SensorComponent.class).lockedTarget = ship;
        AtomicInteger atPrize = new AtomicInteger();
        world.combatEvents.shots.add((signal, shot) -> {
            if (shot.shooter == game.player && shot.target == ship) atPrize.incrementAndGet();
        });
        for (int i = 0; i < 10 / FRAME; i++) {
            world.update(FRAME);
            for (var m : weapons.mounts) assertNotSame(ship, m.engaged, m.def.id + " took the prize as its target");
        }
        assertEquals(0, atPrize.get(), "no shot at the prize");
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));

        // The player's own trigger still fires on it, and that brings it back
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(ship);
        for (int i = 0; i < 5 / FRAME && atPrize.get() == 0; i++) {
            gun.fireGuns();
            world.update(FRAME);
        }
        assertTrue(atPrize.get() > 0, "the trigger fires on it");
        assertFalse(world.macroSim.hostiles().get(id).surrendered, "aimed fire after the grace brings it back");
    }

    @Test
    void theTriggerHeldOnAShipThatStrikesDoesNotBringItBack() {
        // Part 6's window: after the fall, Space held with the lock still on a struck raider brought it
        // back into action, and the guns wrecked it
        String id = pirate();
        world.macroSim.hostiles().get(id).targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 2_500, 0);
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(ship);
        run(3);
        for (SubsystemComponent s : turrets(ship)) s.health = 0f;
        run(2);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
        assertNull(gun.getTarget(), "the lock lets go of a ship that strikes");
        for (int i = 0; i < 20 / FRAME; i++) {
            gun.fireGuns();
            world.update(FRAME);
        }
        assertTrue(world.macroSim.hostiles().get(id).surrendered, "the trigger held past the grace doesn't bring it back");
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
    }

    @Test
    void thePlayersGunsFiringAtWillSpareAFallenBasesBatteries() {
        // Round 10: after the fall our guns went on hitting Den batteries 23 and 24, which are only
        // silenced, not surrendered
        besideTheBase();
        world.pirateBase.fall("a test");
        run(1);
        java.util.List<Entity> batteries = new java.util.ArrayList<>();
        for (String name : world.pirateBase.guns()) batteries.add(named(world, name));
        assertFalse(batteries.isEmpty());
        var weapons = game.player.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
        for (var m : weapons.mounts) m.weaponsFree = true;
        game.player.getComponent(SensorComponent.class).lockedTarget = batteries.get(0);
        AtomicInteger atBattery = new AtomicInteger();
        world.combatEvents.shots.add((signal, shot) -> {
            if (shot.shooter == game.player && batteries.contains(shot.target)) atBattery.incrementAndGet();
        });
        for (int i = 0; i < 10 / FRAME; i++) {
            world.update(FRAME);
            for (var m : weapons.mounts) assertFalse(batteries.contains(m.engaged), m.def.id + " took a silenced battery as its target");
        }
        assertEquals(0, atBattery.get(), "no shot at a fallen base's battery");

        // The trigger still fires on one: aimed fire is the player's choice
        PlayerGunnery gun = new PlayerGunnery(world, game.player);
        gun.setTarget(batteries.get(0));
        for (int i = 0; i < 5 / FRAME && atBattery.get() == 0; i++) {
            gun.fireGuns();
            world.update(FRAME);
        }
        assertTrue(atBattery.get() > 0, "the trigger fires on it");
    }

    @Test
    void ourMissilesSeekersNeverLockAFallenBasesBattery() {
        // Part 6's window on 8eeedc6: 41 hits on Den battery 23 in a second, 12 s after the fall with
        // no lock: a heavy missile fired at a raider had locked the battery instead and burst on it
        besideTheBase();
        world.pirateBase.fall("a test");
        run(1);
        Entity battery = named(world, world.pirateBase.guns().get(0));
        assertTrue(Struck.struck(world, battery));
        var at = com.ringsofiron.physicsexterior.PhysicsManager.getPosition(
                battery.getComponent(PhysicsBodyComponent.class).body, new com.badlogic.gdx.math.Vector2());
        // A harpoon of ours 3 km off, flying straight at the battery, fired at nothing
        Entity harpoon = world.missiles.launch(world.combatDefs.missile("harpoon"), game.player, null, null,
                at.x + 3_000, at.y, -200, 0, (float) Math.PI);
        var m = harpoon.getComponent(com.ringsofiron.combatsys.components.MissileComponent.class);
        AtomicInteger bursts = new AtomicInteger();
        world.combatEvents.detonations.add((signal, d) -> {
            if (d.target == battery) bursts.incrementAndGet();
        });
        for (int i = 0; i < 20 / FRAME; i++) {
            world.update(FRAME);
            assertNotSame(battery, m.seekerLock, "the seeker locked a silenced battery");
        }
        assertEquals(0, bursts.get(), "no warhead fired on it");
    }

    @Test
    void aDefenderStruckBeforeTheFallLetsGoOfThePlayer() {
        // Part 6's window: struck raiders 20 and 21 still read target=@player in `hostiles`, since the
        // base's call to arms sets every defender on the player again while it stands
        besideTheBase();
        String name = world.pirateBase.defenders().get(0);
        Entity defender = null;
        for (int i = 0; i < 600; i++) {
            defender = named(world, name);
            if (defender.getComponent(PhysicsBodyComponent.class) != null) break;
            world.update(FRAME);
        }
        run(3);
        for (SubsystemComponent s : turrets(defender)) s.health = 0f;
        run(2);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(defender));
        HostileComponent h = defender.getComponent(HostileComponent.class);
        h.targetUuid = HostileComponent.PLAYER;
        run(2 * PirateBase.CHECK_SECONDS + 1);
        world.pirateBase.fall("a test");
        assertFalse(h.huntingPlayer(), "a struck defender doesn't hunt the player after the fall");
    }

    @Test
    void atTheFallTheBasesRunningDefendersStrikeWithIt() {
        // Round 9's window on 502e8e2: raiders 20 and 22 were running, not struck, at the fall, and
        // raider 22 hit us 85 times in the next 24 s
        besideTheBase();
        String name = world.pirateBase.defenders().get(0);
        Entity defender = null;
        for (int i = 0; i < 600; i++) {
            defender = named(world, name);
            if (defender.getComponent(PhysicsBodyComponent.class) != null) break;
            world.update(FRAME);
        }
        run(5);
        HostileComponent h = defender.getComponent(HostileComponent.class);
        assertNotNull(defender.getComponent(PhysicsBodyComponent.class), "the defender is out");
        assertNull(ShipDisablement.reason(defender), "and fit to fight");
        // It runs, its guns still on the player as a running pirate's are
        world.macroSim.hostiles().breakOff(defender.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id, world.macroSim.now());
        defender.getComponent(SensorComponent.class).lockedTarget = game.player;
        assertTrue(h.brokeOff);

        world.pirateBase.fall("a test");
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(defender), "it strikes with the base");
        assertTrue(h.surrendered);
        assertNull(defender.getComponent(SensorComponent.class).lockedTarget, "its guns let go of the player at once");
        assertFalse(h.huntingPlayer(), "nor does it hunt the player in hostiles");
        AtomicInteger shots = shotsBy(defender);
        run(24);
        assertEquals(0, shots.get(), "a struck defender holds its fire");
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(defender), "and stays struck");
    }

    private void besideTheBase() {
        world.docking.undock(game.player);
        run(1);
        world.rescue.jumpBeside(game.player, world.engine.getEntity(world.pirateBase.uuid()), 8_000);
        run(2 * PirateBase.CHECK_SECONDS + 1);
        assertFalse(world.pirateBase.defenders().isEmpty(), "the base scrambled");
    }

    // ------------------------------------------------------------------

    /** Brings in raider {@code id} to fight the player, then silences its guns: it surrenders. */
    private Entity surrendered(String id) {
        world.macroSim.hostiles().get(id).targetUuid = HostileComponent.PLAYER;
        Entity ship = bringIn(id, 2_500, 0);
        run(3);
        for (SubsystemComponent s : turrets(ship)) s.health = 0f;
        run(2);
        assertEquals(DisabledComponent.Reason.SURRENDERED, ShipDisablement.reason(ship));
        return ship;
    }

    /** A round from the player's ship, aimed at {@code ship}, fired {@code sinceFired} seconds ago. */
    private com.ringsofiron.combatsys.events.ImpactEvent hitFromPlayer(Entity ship, float sinceFired) {
        var hit = new com.ringsofiron.combatsys.events.ImpactEvent();
        hit.target = ship;
        hit.owner = game.player;
        hit.aimedAt = ship;
        hit.sinceFired = sinceFired;
        return hit;
    }

    private AtomicInteger shotsBy(Entity ship) {
        AtomicInteger n = new AtomicInteger();
        world.combatEvents.shots.add((signal, shot) -> {
            if (shot.shooter == ship) n.incrementAndGet();
        });
        return n;
    }

    private static Iterable<SubsystemComponent> turrets(Entity ship) {
        java.util.List<SubsystemComponent> out = new java.util.ArrayList<>();
        for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemType.TURRET_MOUNT) out.add(s);
        }
        return out;
    }

    private static Entity named(GameWorld w, String name) {
        for (Map.Entry<String, HostileComponent> e : w.macroSim.hostiles().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return w.engine.getEntity(e.getKey());
        }
        fail("no " + name);
        return null;
    }

    private String pirate() {
        for (Map.Entry<String, HostileComponent> e : world.macroSim.hostiles().all().entrySet()) {
            if (e.getValue().isPirate() && HostileTraffic.RAIDER.equals(e.getValue().shipId)) return e.getKey();
        }
        fail("no raider");
        return null;
    }

    private Entity bringIn(String uuid, float dx, float dy) {
        double now = world.macroSim.now();
        StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
        world.macroSim.rails().attach(uuid, GameScenario.EARTH, s.set(s.x + dx, s.y + dy, s.vx, s.vy), now);
        Entity ship = world.engine.getEntity(uuid);
        for (int i = 0; i < 120 && ship.getComponent(PhysicsBodyComponent.class) == null; i++) world.update(FRAME);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class), "came into the bubble");
        return ship;
    }

    private void run(double seconds) {
        for (int i = 0; i < seconds / FRAME; i++) world.update(FRAME);
    }
}
