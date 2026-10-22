package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.signals.Listener;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.events.DamageReport;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Round 10's trace of one career fight from our side ({@code -PcareerFightTrace}): once a second, each
 * of our crew (state, tile, suit, wound and what they are doing), our shorted or stunned systems, the
 * reactor, the range and why we are disabled; and as they come, each hit on us (what it shorted,
 * burst, breached and hurt) and every notice. It runs on past the fight, so deaths and boarders show.
 * Lines go to {@code build/fight-trace-<seed>.txt}. It only reads; the fight plays as it would without it.
 */
final class FightTrace {
    private final GameWorld world;
    private final CareerScript script;
    private final Entity player, them;
    private final List<String> lines = new ArrayList<>();
    private final Path out;
    private final EntitySystem watch;
    private final Listener<DamageReport> hits;
    private final Listener<NoticeLog.Notice> notices;
    private double next, end = Double.POSITIVE_INFINITY;

    FightTrace(CareerScript script, Entity player, Entity them, String what) {
        this.script = script;
        this.world = script.world;
        long seed = script.seed;
        this.player = player;
        this.them = them;
        out = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("fight-trace-" + seed + ".txt");
        double t0 = world.engine.getSimSeconds();
        next = t0;
        line("=== %s, day %.2f", what, t0 / 86_400);
        hits = (sig, r) -> {
            if (r.ship != player && r.ship != them) return;
            ShipGridComponent grid = r.ship.getComponent(ShipGridComponent.class);
            StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "HIT t=%.2f on %s:", t(), r.ship == player ? "US" : "THEM"));
            for (int i = 0; i < r.damagedSubsystems.size; i++) {
                SubsystemComponent sub = r.damagedSubsystems.get(i).getComponent(SubsystemComponent.class);
                if (sub != null) b.append(String.format(Locale.ROOT, " %s hp %.2f%s", sub.type, sub.health, sub.shorted ? " SHORTED" : ""));
            }
            for (int i = 0; i < r.shorts.size; i++) b.append(" short@").append(tile(grid, r.shorts.get(i)));
            for (int i = 0; i < r.bursts.size; i++) b.append(" BURST@").append(tile(grid, r.bursts.get(i)));
            if (r.breaches.size > 0) b.append(" breaches ").append(r.breaches.size);
            for (int i = 0; i < r.injuredCrew.size; i++) {
                CrewMemberComponent c = r.injuredCrew.get(i).getComponent(CrewMemberComponent.class);
                if (c != null) b.append(String.format(Locale.ROOT, " crew %s %s %s %s", c.name, c.state, c.wound, c.woundCause));
            }
            lines.add(b.toString());
        };
        world.combatEvents.damage.add(hits);
        notices = (sig, n) -> line("NOTICE t=%.2f %s", t(), n.text);
        world.notices.posted.add(notices);
        watch = new EntitySystem(Integer.MAX_VALUE - 1) {
            @Override
            public void update(float deltaTime) {
                double now = world.engine.getSimSeconds();
                if (now >= end) {
                    finish();
                    return;
                }
                if (now < next) return;
                next += 1;
                lines.add(second());
            }
        };
        world.engine.getEcsEngine().addSystem(watch);
    }

    /** Traces on for {@code seconds} past now, then writes the file. */
    void closeAfter(double seconds) {
        line("=== fight over at t=%.2f", t());
        end = world.engine.getSimSeconds() + seconds;
    }

    private double t() {
        return world.engine.getSimSeconds();
    }

    private void line(String fmt, Object... args) {
        lines.add(String.format(Locale.ROOT, fmt, args));
    }

    private static String tile(ShipGridComponent grid, int p) {
        return grid == null ? Integer.toString(p) : (p % grid.width) + "," + (p / grid.width);
    }

    private String second() {
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "t=%.0f range=%.0f hull=%.2f/%.2f disabled=%s",
                t(), script.range(player, them), script.hull(player), script.hull(them),
                ShipDisablement.isDisabled(player) ? ShipDisablement.reason(player) : "-"));
        b.append(" | ours:").append(systems(player)).append(" | theirs:").append(systems(them));
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            GridPositionComponent g = e.getComponent(GridPositionComponent.class);
            if (g == null || g.ship != player) continue;
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            b.append(String.format(Locale.ROOT, " | %s %s %s @%d,%d %s%s \"%s\"", c.name, c.role, c.state, g.tileX(), g.tileY(),
                    c.suited ? String.format(Locale.ROOT, "suit %.0f", c.suitAir) : "unsuited",
                    c.wound == null || c.wound.name().equals("NONE") ? "" : String.format(Locale.ROOT, " %s bleed %.0f", c.wound, c.bleedOut),
                    c.activity));
        }
        return b.toString();
    }

    /** The reactor's state, and every system shorted, stunned or out. */
    private static String systems(Entity ship) {
        ShipSubsystemRegistryComponent reg = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (reg == null) return " -";
        StringBuilder b = new StringBuilder();
        for (Entity e : reg.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null) continue;
            boolean reactor = s.type == SubsystemComponent.SubsystemType.REACTOR;
            if (!reactor && !s.shorted && s.stunned <= 0 && s.isOperational) continue;
            b.append(String.format(Locale.ROOT, " %s(hp %.2f%s%s%s%s)", s.type, s.health, s.shorted ? String.format(Locale.ROOT, " SHORT %.0fs", s.shortedFor) : "",
                    s.stunned > 0 ? " stun" : "", s.isOperational ? "" : " out", s.powered ? "" : " unpowered"));
        }
        return b.length() == 0 ? " all up" : b.toString();
    }

    private void finish() {
        world.engine.getEcsEngine().removeSystem(watch);
        world.combatEvents.damage.remove(hits);
        world.notices.posted.remove(notices);
        end = Double.POSITIVE_INFINITY;
        try {
            Files.createDirectories(out.getParent());
            Files.write(out, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        lines.clear();
    }
}
