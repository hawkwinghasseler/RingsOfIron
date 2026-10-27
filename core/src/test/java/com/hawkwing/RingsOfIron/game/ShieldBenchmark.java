package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.combatsys.shields.ShieldModel;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Playtest benchmark: every weapon against a shielded ship (Oct 2026, the particle cloud of
 * combat-sys Design.md 5.11). Each weapon puts a volley into the middle of a pinned, crewed hull
 * from its port beam, once with the ship's shields raised and once with them lowered, so the
 * difference is the cloud's alone: no aim error, no manoeuvring, the same tiles struck. A gun fires
 * at its cooldown for {@link #WINDOW} seconds from {@link #RANGE}; a launcher empties one magazine from
 * {@link #MISSILE_RANGE}
 * (the target's turrets are held stunned, so its point defense doesn't decide the result); a beam
 * burns at its cooldown. The table gives hull taken, systems' health lost, crew killed or wounded,
 * the share of each hit's energy the cloud took, and the cloud mass left.
 * Run with {@code ./gradlew :core:playtestBenchmark --tests '*ShieldBenchmark'}; the tester's
 * checklist is the "Shields" section of {@code documentation/playtest/Balance benchmarks.md}.
 */
@Tag("playtest")
class ShieldBenchmark {
    static final float FRAME = 1f / 60f;
    /** How long a gun or beam keeps firing, sim seconds. */
    static final float WINDOW = 30f;
    /** How far out a gun's rounds are put in flight, meters. */
    static final float RANGE = 2_000f;
    /**
     * How far out a missile is launched, meters: far enough for a slow-burning torpedo to build the
     * speed that drives it through the hull (from the debug standoff of 600 m it bounced off).
     */
    static final float MISSILE_RANGE = 4_000f;
    /** Seconds after the last shot for rounds to land and fires to show; a missile gets {@link #MISSILE_SETTLE}. */
    static final float SETTLE = 12f, MISSILE_SETTLE = 40f;
    /** The shielded hulls: the gunship's Small emitter and the reaver's four Medium ones. */
    static final String[] HULLS = {"gunship_heavy", "reaver_pirate"};
    static final String[] WEAPONS = {"chaingun_mk1", "coilgun_mk1", "railgun_mk1", "laser_mk1", "ion_mk1", "ion_disruptor",
            "cannon_heavy", "railgun_heavy", "breaching_cannon", "laser_heavy",
            "dart_rack", "lance_rack", "harpoon_launcher", "torpedo_tube"};

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    /** One volley's outcome. */
    static final class Volley {
        int shots;
        /** Hull and wall integrity lost, in tiles' worth. */
        double hullTaken;
        /** Subsystem health lost, in whole systems' worth. */
        double systemsTaken;
        int killed, wounded, stunnedPeak, hits, intercepted, interceptHits, detonated;
        /** Joules point defense put into the missiles. */
        double interceptEnergy;
        /** Joules the cloud took, and the joules that reached the hull past it. */
        double shielded, absorbed;
        float cloudBefore, cloudAfter;

        double shieldShare() {
            return shielded + absorbed <= 0 ? 0 : shielded / (shielded + absorbed);
        }
    }

    static Volley volley(String hull, String weaponId, boolean shields) {
        return volley(hull, weaponId, shields, false);
    }

    /** As {@link #volley(String, String, boolean)}; {@code defended}: a launcher's missiles meet the ship's point defense. */
    static Volley volley(String hull, String weaponId, boolean shields, boolean defended) {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        InteriorLab lab = new InteriorLab(assets);
        try {
            lab.load(hull);
            Entity ship = lab.ship;
            ShieldModel.raise(ship, shields);
            run(lab, ShieldModel.STOW_TIME + 2f);
            Volley v = new Volley();
            ShieldCloudComponent cloud = ship.getComponent(ShieldCloudComponent.class);
            v.cloudBefore = cloud == null ? 0f : cloud.cloud();
            lab.world.combatEvents.hullHits.add((s, h) -> {
                if (h.ship != ship) return;
                v.shielded += h.shielded;
                v.absorbed += h.energyAbsorbed;
                v.hits++;
            });
            lab.world.combatEvents.intercepts.add((s, i) -> { v.interceptHits++; v.interceptEnergy += i.energy; if (i.destroyed) v.intercepted++; });
            lab.world.combatEvents.detonations.add((s, d) -> v.detonated++);
            WeaponDef def = lab.world.combatDefs.weapons.get(weaponId);
            assertNotNull(def, weaponId);
            List<int[]> tiles = new ArrayList<>();
            ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    Tile t = grid.getTile(x, y);
                    if (t != null && !t.isVoid()) tiles.add(new int[]{x, y});
                }
            }
            // The same tiles in the same order with shields up and down: one dice per hull and weapon
            Random dice = new Random(hull.hashCode() * 31L + weaponId.hashCode());
            double structure = structure(grid), systems = systems(ship);
            boolean launcher = def.missile != null;
            // Point defense would take most missiles before they meet the cloud: hold it down
            if (launcher && !defended) holdTurrets(ship);
            int shots = launcher ? Math.max(1, def.magazine) : Math.max(1, (int) (WINDOW / def.cooldown));
            float range = launcher ? MISSILE_RANGE : def.mount == WeaponDef.MountType.BEAM ? Float.NaN : RANGE;
            for (int i = 0; i < shots; i++) {
                int[] at = tiles.get(dice.nextInt(tiles.size()));
                lab.debug.fireFrom(ship, weaponId, at[0], at[1], 90f, range);
                v.shots++;
                float gap = launcher ? Math.max(def.cooldown, 1f) : def.cooldown;
                for (float t = 0; t < gap; t += FRAME) {
                    if (launcher && !defended) holdTurrets(ship);
                    lab.update(FRAME);
                    if (!launcher) v.stunnedPeak = Math.max(v.stunnedPeak, stunned(ship));
                }
            }
            for (float t = 0, settle = launcher ? MISSILE_SETTLE : SETTLE; t < settle; t += FRAME) {
                if (launcher && !defended) holdTurrets(ship);
                lab.update(FRAME);
                if (!launcher) v.stunnedPeak = Math.max(v.stunnedPeak, stunned(ship));
            }
            v.hullTaken = structure - structure(grid);
            v.systemsTaken = systems - systems(ship);
            for (Entity c : lab.debug.crewAboard(ship)) {
                CrewMemberComponent m = lab.world.crew.crew(c);
                if (m.state == CrewState.DEAD) v.killed++;
                else if (m.wound != com.ringsofiron.aicrew.Wound.NONE) v.wounded++;
            }
            v.cloudAfter = cloud == null ? 0f : cloud.cloud();
            return v;
        } finally {
            lab.dispose();
        }
    }

    private static void run(InteriorLab lab, float seconds) {
        for (float t = 0; t < seconds; t += FRAME) lab.update(FRAME);
    }

    /** Integrity summed over every wall and hull tile. */
    private static double structure(ShipGridComponent grid) {
        double sum = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t != null && t.isWall()) sum += t.getIntegrity();
            }
        }
        return sum;
    }

    /** Health summed over every subsystem. */
    private static double systems(Entity ship) {
        ShipSubsystemRegistryComponent r = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (r == null) return 0;
        double sum = 0;
        for (Entity e : r.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null) sum += s.health;
        }
        return sum;
    }

    static void holdTurrets(Entity ship) {
        ShipSubsystemRegistryComponent r = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (r == null) return;
        for (Entity e : r.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.type == SubsystemComponent.SubsystemType.TURRET_MOUNT) s.stunned = Math.max(s.stunned, 1f);
        }
    }

    private static int stunned(Entity ship) {
        ShipSubsystemRegistryComponent r = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (r == null) return 0;
        int n = 0;
        for (Entity e : r.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s != null && s.stunned > 0f) n++;
        }
        return n;
    }

    private static String describe(Volley v) {
        return String.format(Locale.ROOT, "%d shots, %d hits: hull %.2f tiles, systems %.2f, %d killed, %d wounded, %d stunned, %d shot down (%d point-defense hits, %.1f MJ), %d went off; cloud took %.0f%% (%.1f MJ), mass %.0f -> %.0f kg",
                v.shots, v.hits, v.hullTaken, v.systemsTaken, v.killed, v.wounded, v.stunnedPeak, v.intercepted, v.interceptHits, v.interceptEnergy / 1e6, v.detonated, 100 * v.shieldShare(), v.shielded / 1e6, v.cloudBefore, v.cloudAfter);
    }

    @Test
    void shieldsTakeMostFromLightFastRoundsAndLeastFromHeavyOnes() {
        BalanceReport report = new BalanceReport("Shields");
        Map<String, Volley> up = new LinkedHashMap<>(), down = new LinkedHashMap<>();
        List<String> table = new ArrayList<>();
        double lancesDown = 0, dartsDown = 0;
        for (String hull : HULLS) {
            for (String w : WEAPONS) {
                Volley u = volley(hull, w, true), d = volley(hull, w, false);
                up.put(hull + "/" + w, u);
                down.put(hull + "/" + w, d);
                double kept = d.hullTaken <= 0 ? Double.NaN : u.hullTaken / d.hullTaken;
                String line = String.format(Locale.ROOT, "| %s | %s | %.2f | %.2f | %s | %.2f | %.2f | %d/%d | %d/%d | %d/%d | %.0f%% | %.0f -> %.0f |",
                        hull, w, u.hullTaken, d.hullTaken, Double.isNaN(kept) ? "-" : String.format(Locale.ROOT, "%.2f", kept), u.systemsTaken, d.systemsTaken,
                        u.killed, d.killed, u.wounded, d.wounded, u.stunnedPeak, d.stunnedPeak, 100 * u.shieldShare(), u.cloudBefore, u.cloudAfter);
                table.add(line);
                System.out.println("Shield volley " + hull + " " + w + " up: " + describe(u) + "; down: " + describe(d));
            }
        }
        System.out.println("| Hull | Weapon | Hull taken, shields up | down | Up over down | Systems up | down | Killed up/down | Wounded up/down | Stunned up/down | Cloud took | Cloud kg |");
        System.out.println("|---|---|---|---|---|---|---|---|---|---|---|---|");
        table.forEach(System.out::println);
        for (String k : up.keySet()) report.note(k, "up: " + describe(up.get(k)) + "; down: " + describe(down.get(k)));
        // Through point defense too: the gunship's flak against each launcher's magazine, shields up
        for (String w : new String[]{"dart_rack", "lance_rack", "harpoon_launcher", "torpedo_tube"}) {
            Volley v = volley("gunship_heavy", w, true, true);
            report.note("gunship_heavy/" + w + " through flak", describe(v));
            if (w.equals("torpedo_tube")) report.atLeast("Torpedoes that get through the gunship's flak, share", 1.0 - (double) v.intercepted / v.shots, 0.5);
            if (w.equals("lance_rack")) lancesDown = (double) v.intercepted / v.shots;
            if (w.equals("dart_rack")) dartsDown = (double) v.intercepted / v.shots;
        }
        report.atMost("Lances shot down over darts shot down", dartsDown <= 0 ? lancesDown : lancesDown / dartsDown, 1.0);
        Volley chain = up.get("reaver_pirate/chaingun_mk1"), chainDown = down.get("reaver_pirate/chaingun_mk1");
        Volley coil = up.get("reaver_pirate/coilgun_mk1"), coilDown = down.get("reaver_pirate/coilgun_mk1");
        report.atMost("Medium shields: chaingun hull damage kept", chain.hullTaken / chainDown.hullTaken, 0.5);
        report.atMost("Medium shields: coilgun hull damage kept", coil.hullTaken / coilDown.hullTaken, 0.5);
        for (String w : new String[]{"cannon_heavy", "railgun_heavy"}) {
            report.atLeast("Medium shields: " + w + " hull damage kept", up.get("reaver_pirate/" + w).hullTaken / down.get("reaver_pirate/" + w).hullTaken, 0.85);
        }
        Volley torp = up.get("gunship_heavy/torpedo_tube");
        report.atLeast("Hull tiles per torpedo, Small shields up", torp.hullTaken / torp.shots, 1.0);
        report.note("table", "\n\n| Hull | Weapon | Hull taken up (tiles) | down | Up over down | Systems up | down | Killed up/down | Wounded up/down | Stunned up/down | Cloud took | Cloud kg |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n" + String.join("\n", table) + "\n\n|");
        report.finish();
    }
}
