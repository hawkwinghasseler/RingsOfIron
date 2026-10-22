package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Round 9 part 2: round 5's career on {@code -PcareerSeed=n}, with the player's people's air traced
 * to {@code build/career-air-<seed>.txt}: when each first stands in air not fit to breathe, suited or
 * not, when a suit runs dry, and when they go down or die of it, with the ship, the room's pressure,
 * whether it is docked, their task, and the oxygen aboard. A probe, not a check:
 * {@code ./gradlew :core:slowTest --tests '*CareerAirTraceTest' -PcareerSeed=1 -PcareerAirTrace}.
 */
@EnabledIfSystemProperty(named = "career.airTrace", matches = ".+")
class CareerAirTraceTest extends Round5CareerTest {
    final StringBuilder trace = new StringBuilder();

    @Override
    String journalName() {
        return "career-air-journal-" + seed;
    }

    @Override
    void start(long seed) {
        super.start(seed);
        world.engine.getEcsEngine().addSystem(new Tracer());
    }

    @AfterEach
    void writeTrace() throws IOException {
        Path out = Paths.get(System.getProperty("user.dir")).resolve("build").resolve("career-air-" + seed + ".txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, trace);
    }

    /** Looks at the player's people twice a second. */
    final class Tracer extends EntitySystem {
        final Set<Entity> watched = new LinkedHashSet<>();
        final Map<Entity, Boolean> inBadAir = new IdentityHashMap<>(), dry = new IdentityHashMap<>();
        final Map<Entity, CrewState> was = new IdentityHashMap<>();
        int frame;

        Tracer() {
            super(Integer.MAX_VALUE);
        }

        @Override
        public void update(float dt) {
            if (++frame % 30 != 0 || game == null || game.player == null) return;
            watched.addAll(world.crewCareer.ownCrew(game.player));
            if (world.captain.avatar() != null) watched.add(world.captain.avatar());
            for (Entity e : watched) {
                CrewMemberComponent c = world.crew.crew(e);
                GridPositionComponent p = world.crew.position(e);
                if (c == null || p == null || p.ship == null) continue;
                ShipGridComponent g = world.crew.grid(p.ship);
                if (g == null) continue;
                CrewState before = was.put(e, c.state);
                if (before == CrewState.DEAD) continue;
                boolean bad = !TaskRules.isBreathable(g, p.tileX(), p.tileY());
                if (bad != inBadAir.getOrDefault(e, false)) log(e, bad ? "into bad air" : "back in good air");
                inBadAir.put(e, bad);
                boolean empty = c.suited && c.suitAir <= 0f;
                if (empty && !dry.getOrDefault(e, false)) log(e, "suit dry");
                dry.put(e, empty);
                if (before != null && before != c.state) {
                    log(e, c.state + (c.lastDamageCause == null ? "" : " (" + c.lastDamageCause.name().toLowerCase(Locale.ROOT) + ")"));
                } else if (bad && !c.breathesFromSuit() && c.lastDamageCause == CrewDamageSink.DamageCause.ASPHYXIATION && frame % 600 == 0) {
                    log(e, "suffocating");
                }
            }
        }

        void log(Entity e, String what) {
            CrewMemberComponent c = world.crew.crew(e);
            GridPositionComponent p = world.crew.position(e);
            ShipGridComponent g = world.crew.grid(p.ship);
            Compartment room = g.compartmentAt(p.tileX(), p.tileY());
            ShipTaskBoardComponent board = world.crew.board(p.ship);
            Entity docked = world.docking.dockedTo(p.ship);
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                    "day %.3f %s%s: %s on %s%s at %d,%d room %s %.1f kPa o%.2f; %s; suited %s air %.0f; task %s; %s%s%s",
                    world.engine.getSimSeconds() / 86_400, c.name, e == world.captain.avatar() ? " (you)" : "", what,
                    p.ship == game.player ? "our ship" : "another craft", docked != null ? " (docked)" : "",
                    p.tileX(), p.tileY(), room == null ? "-" : String.valueOf(room.id), room == null ? 0 : room.pressure(),
                    g.oxygenRatioAt(p.tileX(), p.tileY()), c.state, c.suited, c.suitAir,
                    c.task == null ? "none" : c.task.kind, c.activity,
                    board != null && board.battleStations ? "; battle stations" : "",
                    c.carriedBy != null ? "; carried" : ""));
            ShipSubsystemRegistryComponent registry = p.ship.getComponent(ShipSubsystemRegistryComponent.class);
            if (registry != null) {
                double tanks = 0;
                for (Entity sub : registry.subsystems) {
                    SubsystemComponent s = sub == null ? null : sub.getComponent(SubsystemComponent.class);
                    if (s == null) continue;
                    if (s.type == SubsystemComponent.SubsystemType.TANK && s.fluid == Fluid.OXYGEN) {
                        tanks += s.canSupply() ? s.contents : 0;
                    } else if (s.type == SubsystemComponent.SubsystemType.LIFE_SUPPORT) {
                        line.append(String.format(Locale.ROOT, "; life support%s reserve %.0f kg", s.isOperational ? "" : " down", TankSupply.reserve(s)));
                    }
                }
                line.append(String.format(Locale.ROOT, "; O2 tanks %.0f kg", tanks));
            }
            int breathable = 0, floor = 0, breaches = 0;
            for (int y = 0; y < g.height; y++) {
                for (int x = 0; x < g.width; x++) {
                    com.ringsofiron.interiorgrid.Tile t = g.getTile(x, y);
                    if (t == null) continue;
                    if (t.isHullBreach()) breaches++;
                    if (t.holdsAir()) {
                        floor++;
                        if (TaskRules.isBreathable(g, x, y)) breathable++;
                    }
                }
            }
            line.append(String.format(Locale.ROOT, "; breathable %d/%d, breaches %d, kits %d", breathable, floor, breaches, g.patchKits));
            trace.append(line).append('\n');
        }
    }
}
