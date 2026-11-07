package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.OrbitPathProvider;
import com.ringsofiron.uihud.providers.OrbitPathProvider.MarkKind;
import com.ringsofiron.uihud.providers.TrafficMapProvider;
import com.ringsofiron.uihud.providers.TrafficMapProvider.ContactKind;
import com.ringsofiron.uihud.widgets.MapPainter.Role;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPainterTest {
    /** A painter that only writes down what it is asked to draw. */
    static final class Recording extends MapPainter {
        final List<String> drawn = new ArrayList<>();

        Recording() {
            super(null, null);
        }

        boolean drew(String piece) {
            return drawn.contains(piece);
        }

        @Override
        public void path(Batch batch, Role role, float x0, float y0, float x1, float y1, float alpha) {
            drawn.add("path " + role);
        }

        @Override
        public void body(Batch batch, double x, double y, double radius, float left, float bottom, float right, float top,
                         float alpha) {
            drawn.add("body");
        }

        @Override
        public void ghost(Batch batch, float x, float y, float radius, float soiRadius, float alpha) {
            drawn.add("ghost");
        }

        @Override
        public void approach(Batch batch, float shipX, float shipY, float targetX, float targetY, float alpha) {
            drawn.add("approach");
        }

        @Override
        public void burn(Batch batch, Role role, float x, float y, float alpha) {
            drawn.add("burn " + role);
        }

        @Override
        public void ship(Batch batch, float x, float y, float alpha) {
            drawn.add("ship");
        }

        @Override
        public void moon(Batch batch, float x, float y, float size, float alpha) {
            drawn.add("moon");
        }

        @Override
        public void marker(Batch batch, float x, float y, boolean target, float alpha) {
            drawn.add(target ? "marker target" : "marker station");
        }

        @Override
        public void mark(Batch batch, MarkKind kind, Role role, float x, float y, float alpha) {
            drawn.add("mark " + kind + " " + role);
        }

        @Override
        public void contact(Batch batch, ContactKind kind, boolean stale, float x, float y, float alpha) {
            drawn.add("contact " + kind + (stale ? " stale" : ""));
        }

        @Override
        public void objective(Batch batch, float x, float y, float alpha) {
            drawn.add("objective");
        }

        @Override
        public void targetBrackets(Batch batch, float x, float y, float alpha) {
            drawn.add("brackets");
        }

        @Override
        public void regions(Batch batch, Array<TrafficMapProvider.Region> regions, float x, float top, float alpha) {
            drawn.add("regions " + regions.size);
        }
    }

    /** A batch that draws nothing, for drawing without a window. */
    static Batch batch() {
        Color color = new Color(Color.WHITE);
        return (Batch) java.lang.reflect.Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
                (proxy, method, args) -> {
                    Class<?> r = method.getReturnType();
                    if (r == Color.class) return color;
                    if (r == float.class) return 0f;
                    if (r == int.class) return 0;
                    if (r == boolean.class) return false;
                    return null;
                });
    }

    /** Earth with a moon, the orbit now and a planned path with a burn, a station and the target, a proposal and the marks. */
    static OrbitMap map(MapPainter painter) {
        OrbitMap map = new OrbitMap(null);
        map.setPainter(painter);
        map.setShowMarks(true);
        map.setProvider(p -> {
            p.bodyName = "Earth";
            p.bodyRadius = 6.371e6;
            p.shipX = 6.771e6;
            for (int i = 0; i <= 32; i++) {
                double a = Math.PI * 2 * i / 32;
                p.current.add((float) (6.771e6 * Math.cos(a)), (float) (6.771e6 * Math.sin(a)));
                p.planned.add((float) (8e6 * Math.cos(a)), (float) (7e6 * Math.sin(a)));
                p.targetOrbit.add((float) (7.5e6 * Math.cos(a)), (float) (7.5e6 * Math.sin(a)));
                p.proposed.add((float) (7.2e6 * Math.cos(a)), (float) (7.2e6 * Math.sin(a)));
            }
            p.addLeg(true).points.addAll(8e6f, 0f, 9e6f, 1e6f);
            p.nodes.add(6.771e6f, 0f);
            p.nodeLabels.add("1: 0:12:30");
            p.selectedNode = 0;
            p.proposedNodes.add(0f, 7.2e6f);
            p.approach.addAll(7e6f, 1e6f, 7.4e6f, 1.2e6f);
            OrbitPathProvider.MapMarker station = p.addMarker();
            station.name = "Highport";
            station.x = 6.8e6;
            OrbitPathProvider.MapMarker target = p.addMarker();
            target.name = "Raider";
            target.target = true;
            target.x = -6.8e6;
            OrbitPathProvider.MapBody moon = p.addBody();
            moon.name = "Moon";
            moon.x = 3.8e8;
            moon.radius = 1.7e6;
            moon.orbit.addAll(-9e6f, 0f, 9e6f, 1e6f);
            p.addMark(MarkKind.APOAPSIS, 0, 7e6, "Ap 630 km").planned = true;
            p.addMark(MarkKind.IMPACT, 0, -6.371e6, "Impact");
            OrbitPathProvider.PathMark ghost = p.addMark(MarkKind.GHOST, 7.9e6, 0, "");
            ghost.radius = 1.7e5;
            ghost.soiRadius = 6.6e5;
            return true;
        });
        map.setBounds(0f, 0f, 400f, 400f);
        map.act(0f);
        return map;
    }

    @Test
    void theMapDrawsEveryPieceThroughItsPainter() {
        Recording painter = new Recording();
        OrbitMap map = map(painter);
        assertTrue(map.hasPath());
        map.place();
        map.drawPieces(batch(), 1f);
        for (String piece : new String[]{"body", "path " + Role.MOON_ORBIT, "path " + Role.TARGET_ORBIT, "path " + Role.CURRENT,
                "path " + Role.PLANNED, "path " + Role.PLANNED_LEG, "path " + Role.PROPOSED, "ghost", "approach",
                "burn " + Role.SELECTED, "burn " + Role.PROPOSED, "ship", "moon", "marker station", "marker target",
                "mark " + MarkKind.APOAPSIS + " " + Role.PLANNED, "mark " + MarkKind.IMPACT + " " + Role.IMPACT}) {
            assertTrue(painter.drew(piece), piece + " in " + painter.drawn);
        }
    }

    @Test
    void theTrafficDrawsThroughThePainterToo() {
        Recording painter = new Recording();
        OrbitMap map = map(painter);
        map.place();
        TrafficLayer traffic = new TrafficLayer(null, null);
        traffic.setPainter(painter);
        traffic.setProvider(f -> {
            f.frameName = "Earth";
            f.frameRadius = 6.371e6;
            TrafficMapProvider.Contact station = f.addContact();
            station.kind = ContactKind.STATION;
            station.x = 6.8e6;
            TrafficMapProvider.Contact hauler = f.addContact();
            hauler.kind = ContactKind.HAULER;
            hauler.x = -6.8e6;
            hauler.objective = true;
            TrafficMapProvider.Contact pirate = f.addContact();
            pirate.kind = ContactKind.HOSTILE;
            pirate.y = 6.8e6;
            pirate.stale = true;
            pirate.target = true;
            f.addContact().kind = ContactKind.OTHER;
            f.addRegion("Selene Mining Combine: 60 HIGH", true);
        });
        traffic.refresh();
        traffic.draw(batch(), 1f, map, 0f, 0f, 400f, 400f);
        traffic.drawRegions(batch(), 1f, 20f, 100f);
        for (String piece : new String[]{"contact STATION", "contact HAULER", "contact HOSTILE stale", "contact OTHER",
                "objective", "brackets", "regions 1"}) {
            assertTrue(painter.drew(piece), piece + " in " + painter.drawn);
        }
    }

    @Test
    void withoutAPainterOfItsOwnTheMapLooksClassic() {
        OrbitMap map = new OrbitMap(null);
        MapPainter classic = map.getPainter();
        Recording mine = new Recording();
        map.setPainter(mine);
        assertSame(mine, map.getPainter());
        map.setPainter(null);
        assertSame(classic, map.getPainter());
        // The classic colours, as the map had them before it took a painter
        assertEquals(new Color(1f, 0.7f, 0.2f, 1f), classic.color(Role.PLANNED));
        assertEquals(Color.WHITE, classic.color(Role.SELECTED));
    }
}
