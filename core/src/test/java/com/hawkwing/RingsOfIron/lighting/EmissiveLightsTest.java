package com.hawkwing.RingsOfIron.lighting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The light data built from the art's emissive layers. */
class EmissiveLightsTest {
    static final Path ASSETS = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");

    static EmissiveLights art() {
        return EmissiveLights.load(ASSETS);
    }

    @Test
    void everySvgWithAnEmissiveLayerHasItsLights() throws IOException {
        EmissiveLights lights = art();
        Path root = ASSETS.resolve("art");
        List<Path> svgs;
        try (Stream<Path> files = Files.walk(root)) {
            svgs = files.filter(p -> p.toString().endsWith(".svg")).collect(Collectors.toList());
        }
        int emissive = 0;
        for (Path svg : svgs) {
            String key = root.relativize(svg).toString().replace('\\', '/').replaceAll("\\.svg$", "");
            boolean glows = new String(Files.readAllBytes(svg), StandardCharsets.UTF_8).contains("id=\"emissive\"");
            if (glows) emissive++;
            assertEquals(glows, lights.get(key) != null,
                    key + (glows ? " glows but has no lights" : " has lights but no emissive layer")
                            + "; run tools/art/extract_lights.py");
        }
        assertEquals(emissive, lights.size());
    }

    @Test
    void aReactorsCoreIsItsBiggestLight() {
        EmissiveLights.Art reactor = art().get("components/reactor_m");
        assertEquals(128f, reactor.width);
        assertEquals(-128f, reactor.bottom, "drawn from its top-left corner, y up");
        EmissiveLights.Source core = null;
        for (EmissiveLights.Source s : reactor.lights) if (core == null || s.size() > core.size()) core = s;
        assertEquals(64f, core.x, 0.01f);
        assertEquals(-64f, core.y, 0.01f);
        assertEquals(52f, core.size(), 0.01f, "the glow, white-hot center and halo are one light");
        assertTrue(core.color.r > 0.9f && core.color.b < 0.4f, "in the reactor's orange accent, not the white center");
    }

    @Test
    void unpoweredArtGivesOffNoLight() {
        assertNotNull(art().get("components/helm_m"));
        assertNull(art().get("components/helm_m_unpowered"));
    }

    @Test
    void wallLightsWashTheRoomInFrontOfTheirWall() {
        EmissiveLights.Source s = art().get("decor/wall_light").lights[0];
        assertTrue(s.isCone);
        assertEquals(-90f, s.direction, "the wall is at the top of the canvas");
        assertEquals(90f, s.cone, "all the half of the room in front of the wall, not a beam");
    }

    @Test
    void navigationLightsAreRedToPortAndGreenToStarboard() {
        EmissiveLights.Source port = null, starboard = null;
        for (EmissiveLights.Source s : art().get("hulls/corvette").lights) {
            if (s.color.r > 0.9f && s.color.g < 0.5f && s.color.b < 0.5f) port = s;
            if (s.color.g > 0.9f && s.color.r < 0.5f) starboard = s;
        }
        assertTrue(port.y > 0f, "port is +y in the body frame");
        assertTrue(starboard.y < 0f);
    }

    @Test
    void missingDataLeavesTheGameUnlit() {
        assertEquals(0, EmissiveLights.load(ASSETS.resolve("no-such-folder")).size());
    }
}
