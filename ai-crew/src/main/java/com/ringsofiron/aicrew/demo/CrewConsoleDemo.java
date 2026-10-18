package com.ringsofiron.aicrew.demo;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Runs {@link CrewScenario} headless and prints the ship as text every few seconds:
 * crew as the first letter of their name (upper case conscious, lower case unconscious,
 * x dead, boarders as B), % system mounts, F fire, ~ thin air, O hull breach, = sealed,
 * | closed and / open doors in the edge walls.
 *
 * Run with {@code gradlew :ai-crew:runCrewConsoleDemo}.
 */
public class CrewConsoleDemo {

    public static void main(String[] args) {
        float duration = args.length > 0 ? Float.parseFloat(args[0]) : 75f;
        CrewScenario s = new CrewScenario();
        s.log = line -> System.out.printf("%6.1fs  ** %s **%n", s.time, line);
        float dt = 1f / 30f, printEvery = 5f, nextPrint = 0f;
        while (s.time < duration) {
            s.update(dt);
            if (s.time >= nextPrint) {
                print(s);
                nextPrint += printEvery;
            }
        }
        print(s);
    }

    static void print(CrewScenario s) {
        ShipGridComponent g = s.grid;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- t=%.1fs ---%n", s.time));
        for (int y = g.height - 1; y >= 0; y--) {
            for (int x = 0; x < g.width; x++) {
                EdgeWall left = g.getEdge(x, y, com.ringsofiron.interiorgrid.Side.LEFT);
                if (x > 0) sb.append(left == null ? ' ' : (left.blocks() ? '|' : '/'));
                sb.append(glyph(s, x, y));
            }
            sb.append('\n');
        }
        SubsystemComponent r = s.reactor.getComponent(SubsystemComponent.class);
        sb.append(String.format("reactor %.0f%%   open tasks: %s%n", r.health * 100f, openTasks(s)));
        for (Entity e : s.crewMembers) {
            CrewMemberComponent c = s.crew.crew(e);
            GridPositionComponent p = s.crew.position(e);
            sb.append(String.format("  %-10s %-8s f%d %5.1f hp %-11s (%2d,%d) %s%n", c.name, c.role, c.faction, c.health,
                    c.state, p.tileX(), p.tileY(), c.activity));
        }
        System.out.print(sb);
    }

    private static String openTasks(CrewScenario s) {
        StringBuilder sb = new StringBuilder();
        for (CrewTask t : s.board.tasks) if (t.isActive()) sb.append(t).append(' ');
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static char glyph(CrewScenario s, int x, int y) {
        for (Entity e : s.crewMembers) {
            GridPositionComponent p = s.crew.position(e);
            if (p.tileX() != x || p.tileY() != y) continue;
            CrewMemberComponent c = s.crew.crew(e);
            if (c.carriedBy != null) continue;
            if (c.state == CrewState.DEAD) return 'x';
            char ch = c.faction != 0 ? 'B' : c.name.charAt(0);
            return c.state == CrewState.CONSCIOUS ? ch : Character.toLowerCase(ch);
        }
        Tile t = s.grid.getTile(x, y);
        if (t.isVoid()) return ' ';
        if (t.isHullBreach()) return 'O';
        if (t.bulkheadSealed) return '=';
        if (t.isWall()) return '#';
        if (t.mount != null) return '%';
        if (t.isBurning()) return 'F';
        if (s.grid.oxygenRatioAt(x, y) < 0.5f) return '~';
        return '.';
    }
}
