package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Round 9 part 4's trace: loads the career's "Before the raid" save and plays it as
 * tools/playtest/mid-late.sh does, logging every hit or shot from the player on a struck ship and
 * every surrender and comeback. PRIZE_TRACE_SAVE names the save folder; PRIZE_TRACE_MODE is window (the
 * default), career (RaidRoutine's raid), or bounty (the gunship's bounty fight lost on purpose, logging
 * the boarder's range).
 */
@Tag("slow")
class PrizeTraceProbe {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    PlayerGunnery gun;
    final Map<Entity, Double> struckAt = new HashMap<>();

    @Test
    void trace() {
        String saves = System.getenv("PRIZE_TRACE_SAVE");
        if (saves == null) return;
        GdxNativesLoader.load();
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        boolean bounty = "bounty".equals(System.getenv("PRIZE_TRACE_MODE"));
        game.quickLoad(new FileHandle(Paths.get(saves, bounty ? "Gunship_bounty_fight.json" : "Before_the_raid.json").toFile()));
        world.notices.posted.add((s, n) -> log("NOTICE " + n.text));
        world.disablement.changes.add((s, c) -> {
            if (c.now == DisabledComponent.Reason.SURRENDERED) struckAt.put(c.ship, now());
            if (c.was == DisabledComponent.Reason.SURRENDERED || c.now == DisabledComponent.Reason.SURRENDERED)
                log("STRUCK " + name(c.ship) + " " + c.was + " -> " + c.now);
        });
        world.combatEvents.shots.add((s, shot) -> {
            if (shot.shooter != game.player || !struckAt.containsKey(shot.target)) return;
            log("SHOT at " + ShipDisablement.reason(shot.target) + " " + name(shot.target) + " weapon " + shot.weaponDefId + " (" + since(shot.target) + " s after it struck)"
                    + " trigger-locked " + (gun.getTarget() == shot.target));
        });
        world.combatEvents.impacts.add((s, hit) -> {
            if (hit.owner != game.player || !struckAt.containsKey(hit.target)) return;
            log("HIT on " + ShipDisablement.reason(hit.target) + " " + name(hit.target) + " by " + (hit.def == null ? "?" : hit.def.id)
                    + " pd=" + hit.pointDefense + " aimedAt=" + name(hit.aimedAt) + " fired " + hit.sinceFired + " s ago, "
                    + since(hit.target) + " s after it struck, at " + range(hit.target) + " m");
        });
        world.combatEvents.detonations.add((s, d) -> {
            if (d.owner != game.player || !struckAt.containsKey(d.target)) return;
            log("DETONATION at struck " + name(d.target) + " aimedAt=" + name(d.aimedAt));
        });
        gun = new PlayerGunnery(world, game.player);
        if (bounty) bounty();
        else if ("career".equals(System.getenv("PRIZE_TRACE_MODE"))) career();
        else window();
    }

    private void career() {
        // The save is RaidRoutine's arrived(): carry on as the career does, from holding on the base
        RaidRoutine.holdOn(world, game, world.pirateBase.uuid());
        int frame = 0;
        for (float t = 0; t < RaidRoutine.RAID_SECONDS; t += FRAME) {
            Entity target = null;
            for (String name : world.pirateBase.defenders()) {
                Entity s = ship(name);
                if (s == null || s.getComponent(PhysicsBodyComponent.class) == null || FirstPlay.beaten(world, s)) continue;
                target = s;
                break;
            }
            if (target != null && gun.getTarget() != target) gun.setTarget(target);
            gun.fireGuns();
            gun.launchMissile();
            gun.update(FRAME);
            world.update(FRAME);
            if (++frame % 600 == 0) state();
            if (!world.pirateBase.standing() || FirstPlay.beaten(world, game.player)) break;
        }
        state();
        if (!world.pirateBase.standing()) afterTheFall();
    }

    /** The bounty fight lost on purpose: the main guns never fire, and the boarder's range is logged. */
    private void bounty() {
        world.combatEvents.impacts.add((s, hit) -> {
            Entity boarder = world.pirateBoarding.boarder();
            if (hit.owner == game.player && hit.target != null && hit.target == boarder) {
                log("HIT on the boarder " + name(boarder) + " by " + (hit.def == null ? "?" : hit.def.id) + " pd=" + hit.pointDefense + " at " + range(boarder) + " m");
            }
        });
        Entity last = null;
        for (float t = 0; t < 900; t += 1) {
            pause(1);
            Entity boarder = world.pirateBoarding.boarder();
            if (boarder != null && (boarder != last || ((int) t) % 15 == 0)) log("BOARDER " + name(boarder) + " at " + range(boarder) + " m");
            last = boarder;
        }
        state();
    }

    private void window() {
        pause(14);
        tab();
        hold(5);
        pause(20 + 2 + 2 + 30 + 2);
        for (int i = 0; i < 80; i++) {
            String state = state();
            if (state.contains("base fallen") || state.contains("we are beaten")) break;
            if (state.contains("target none") || state.contains("target beaten")) tab();
            log("KEY r: missile " + gun.launchMissile());
            hold(6);
        }
        state();
        if (!world.pirateBase.standing()) afterTheFall();
    }

    /**
     * Round 10: a minute of Space after the fall, as part 6's window does it, pressing Tab whenever
     * the lock is gone or beaten. Logs the fallen base's batteries as struck, and every hit on us.
     */
    private void afterTheFall() {
        for (String name : world.pirateBase.guns()) {
            Entity g = ship(name);
            if (g != null) struckAt.putIfAbsent(g, now());
        }
        int[] onUs = new int[1];
        world.combatEvents.impacts.add((s, hit) -> {
            if (hit.target != game.player || hit.owner == null || hit.owner == game.player) return;
            onUs[0]++;
            HostileComponent h = hit.owner.getComponent(HostileComponent.class);
            log("HIT on us by " + name(hit.owner) + " (" + ShipDisablement.reason(hit.owner) + (h != null && h.brokeOff ? ", running" : "")
                    + ") with " + (hit.def == null ? "?" : hit.def.id));
        });
        log("AFTER THE FALL: a minute of Space");
        for (int i = 0; i < 10; i++) {
            state();
            tabToOneFighting();
            hold(6);
        }
        state();
        log("AFTER THE FALL: " + onUs[0] + " hits on us");
    }

    /**
     * As part 6's script does it: Tab until the gun has a ship still fighting (not struck, not
     * beaten), at most once round the contacts; with none, the lock is let go and Space fires at nothing.
     */
    private void tabToOneFighting() {
        Entity t = gun.getTarget();
        if (t != null && !struck(t) && !FirstPlay.beaten(world, t)) return;
        for (int n = 0; n < game.contacts.size; n++) {
            tab();
            t = gun.getTarget();
            if (t != null && !struck(t) && !FirstPlay.beaten(world, t)) return;
        }
        gun.setTarget(null);
        log("KEY none: nothing is left fighting, the lock is let go");
    }

    private void tab() {
        gun.setTarget(game.nextContact(gun.getTarget()));
        Entity t = gun.getTarget();
        log("KEY Tab: target " + name(t) + (struck(t) ? " (struck " + since(t) + " s ago)" : FirstPlay.beaten(world, t) ? " (beaten)" : ""));
    }

    private void hold(float seconds) {
        for (float t = 0; t < seconds; t += FRAME) {
            gun.fireGuns();
            step();
        }
    }

    private void pause(float seconds) {
        for (float t = 0; t < seconds; t += FRAME) step();
    }

    private void step() {
        gun.update(FRAME);
        world.update(FRAME);
    }

    private String state() {
        Entity target = gun.getTarget();
        String s = String.format("STATE base %s, hull %.2f%s, target %s", world.pirateBase.standing() ? "standing" : "fallen",
                ShipDisablement.hullIntegrity(game.player.getComponent(com.ringsofiron.interiorgrid.ShipGridComponent.class)),
                ShipDisablement.isDisabled(game.player) ? ", we are beaten" : "",
                target == null ? "none" : FirstPlay.beaten(world, target) ? "beaten" : name(target));
        StringBuilder b = new StringBuilder(s);
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            Entity x = world.engine.getEntity(e.getKey());
            if (x == null || x.getComponent(PhysicsBodyComponent.class) == null) continue;
            b.append(String.format(" | %s %s at %d m, %s%s", e.getValue().displayName, e.getValue().loadout, range(x),
                    ShipDisablement.reason(x), e.getValue().brokeOff ? " running" : ""));
        }
        WeaponsComponent w = game.player.getComponent(WeaponsComponent.class);
        for (WeaponMountComponent m : w.mounts) b.append(String.format(" | mount %s free=%s engaged %s", m.def.id, m.weaponsFree, name(m.engaged)));
        log(b.toString());
        return s;
    }

    private Entity ship(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) {
            if (name.equals(e.getValue().displayName)) return world.engine.getEntity(e.getKey());
        }
        return null;
    }

    private boolean struck(Entity e) {
        if (e == null) return false;
        if (ShipDisablement.reason(e) == DisabledComponent.Reason.SURRENDERED) return true;
        HostileComponent h = e.getComponent(HostileComponent.class);
        return h != null && world.pirateBase.silenced(h.displayName);
    }

    private String since(Entity e) {
        Double at = struckAt.get(e);
        return at == null ? "?" : String.format("%.1f", now() - at);
    }

    private double now() {
        return world.engine.getSimSeconds();
    }

    private String name(Entity e) {
        if (e == null) return "none";
        HostileComponent h = e.getComponent(HostileComponent.class);
        if (h != null) return h.displayName;
        if (e.getComponent(MissileComponent.class) != null) return "a missile from " + name(e.getComponent(MissileComponent.class).launcher);
        if (e == game.player) return "us";
        return PlayerDocking.name(e);
    }

    private long range(Entity e) {
        PhysicsBodyComponent a = e.getComponent(PhysicsBodyComponent.class), b = game.player.getComponent(PhysicsBodyComponent.class);
        if (a == null || a.body == null || b == null || b.body == null) return -1;
        return Math.round(PhysicsManager.getPosition(a.body, new Vector2()).dst(PhysicsManager.getPosition(b.body, new Vector2())));
    }

    private void log(String s) {
        System.out.printf("PRIZE %7.1f %s%n", world.engine.getSimSeconds(), s);
    }
}
