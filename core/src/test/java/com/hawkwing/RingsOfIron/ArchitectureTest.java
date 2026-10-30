package com.hawkwing.RingsOfIron;

import static org.junit.jupiter.api.Assertions.*;

import com.hawkwing.RingsOfIron.game.GameWorld;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The architecture rules the refactor works toward, written as plain tests over the sources and
 * compiled classes. Each rule lists today's violations as an allow-list: a new violation fails, and
 * so does an allow-listed one that has gone away, so each list only ever shrinks. When a refactoring
 * PR removes a violation, it deletes the line here too.
 */
class ArchitectureTest {
    /** Tests run in core/; the modules sit beside it. */
    static final Path ROOT = Paths.get("..");
    static final Path GAME = ROOT.resolve("core/src/main/java/com/hawkwing/RingsOfIron/game");

    /** Packages game logic must not use: the shell, rendering, debug tools and libGDX graphics. */
    static final Pattern FORBIDDEN_IN_GAME = Pattern.compile(
            "\\bcom\\.hawkwing\\.RingsOfIron\\.(shell|render|debug)\\b|\\bcom\\.badlogic\\.gdx\\.graphics\\b");

    /** Game classes that use a forbidden package today, and which; Phase 2 empties this. */
    static final Map<String, Set<String>> GAME_ALLOWED = Map.ofEntries(
            Map.entry("Autopilot", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("BerthVenting", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("CaptainSafety", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("CrewMedical", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("DamageControl", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("DeckView", Set.of("com.badlogic.gdx.graphics")),
            Map.entry("FireArcOverlay", Set.of("com.badlogic.gdx.graphics")),
            Map.entry("FirstPlay", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("GameWorld", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("HelmOrders", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("HintRegistry", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("NavOverlay", Set.of("com.badlogic.gdx.graphics")),
            Map.entry("Navigation", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("OpeningGuide", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("PlayerOrbitPath", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("PlayerTransfer", Set.of("com.hawkwing.RingsOfIron.shell")),
            Map.entry("ShipDisablement", Set.of("com.badlogic.gdx.graphics")),
            Map.entry("Shipyard", Set.of("com.hawkwing.RingsOfIron.shell")));

    /** Each module's source package under com.ringsofiron, and its folder. */
    static final Map<String, String> MODULE_PACKAGES = Map.ofEntries(
            Map.entry("coreengine", "core-engine"),
            Map.entry("orbital", "orbital-mechanics"),
            Map.entry("physicsexterior", "physics-exterior"),
            Map.entry("interiorgrid", "interior-grid"),
            Map.entry("aicrew", "ai-crew"),
            Map.entry("macrosim", "macro-sim"),
            Map.entry("combatsys", "combat-sys"),
            Map.entry("uihud", "ui-hud"),
            Map.entry("audiospatial", "audio-spatial"),
            Map.entry("moddingdata", "modding-data"),
            Map.entry("stationsdocking", "stations-docking"),
            Map.entry("artpipeline", "art-pipeline"));

    /**
     * Which modules each module's main code may use. The layering as it stands: each module uses
     * only modules below it. A new module (Phase 2's ship-systems, career, ship-ops) is added here
     * with the modules it needs, and never with core or ui-hud.
     */
    static final Map<String, Set<String>> MODULE_USES = Map.ofEntries(
            Map.entry("core-engine", Set.of()),
            Map.entry("orbital-mechanics", Set.of("core-engine")),
            Map.entry("physics-exterior", Set.of("core-engine", "modding-data", "orbital-mechanics")),
            Map.entry("interior-grid", Set.of("core-engine", "modding-data")),
            Map.entry("ai-crew", Set.of("core-engine", "interior-grid", "physics-exterior")),
            Map.entry("macro-sim", Set.of("core-engine", "modding-data", "orbital-mechanics", "physics-exterior")),
            Map.entry("combat-sys", Set.of("core-engine", "interior-grid", "modding-data", "physics-exterior")),
            Map.entry("ui-hud", Set.of("core-engine", "interior-grid", "modding-data", "physics-exterior")),
            Map.entry("audio-spatial", Set.of("core-engine", "interior-grid", "physics-exterior")),
            Map.entry("modding-data", Set.of("core-engine")),
            Map.entry("stations-docking",
                    Set.of("core-engine", "interior-grid", "modding-data", "orbital-mechanics", "physics-exterior")),
            Map.entry("art-pipeline", Set.of()));

    /**
     * Classes whose constructor takes the whole GameWorld today. Phase 1 narrows them to the
     * collaborators they use, leaving about ten top-level coordinators; no class joins this list.
     */
    static final Set<String> TAKES_GAME_WORLD = Set.of(
            "debug.DebugActions", "debug.DebugPicker", "debug.DevConsole", "debug.ScenarioSetup",
            "game.AirWatch", "game.AudioLab", "game.Autopilot", "game.BerthVenting", "game.BoardingWork",
            "game.Bridge", "game.BurnPlanner", "game.CaptainSafety", "game.CombatThreats", "game.ContractBoard",
            "game.ContractMissions", "game.CrewCareer", "game.CrewHiring", "game.CrewMedical", "game.CrewRoster",
            "game.DamageControl", "game.DeckTools", "game.Derelicts", "game.DistressBeacon", "game.ExteriorPick",
            "game.FactionTension", "game.FieldRefits", "game.FireArcOverlay", "game.FirstPlay", "game.FlightField",
            "game.FlightWarnings", "game.FreighterTraffic", "game.FurnishingPlacement", "game.FurnishingStore",
            "game.GameHud", "game.GameScenario", "game.GunCrews", "game.HelmOrders", "game.HostileTraffic",
            "game.JammedCollars", "game.LoopWiring", "game.MapManeuvers", "game.MarketTrade", "game.NavOverlay",
            "game.NavTarget", "game.Navigation", "game.OpeningGuide", "game.PirateBase", "game.PirateBoarding",
            "game.PirateSightings", "game.PirateUpkeep", "game.PlayerDocking", "game.PlayerGunnery",
            "game.PlayerOrbitPath", "game.PlayerTransfer", "game.Prisoners", "game.RailsTrafficMap", "game.Rescue",
            "game.ShieldDocking", "game.Shipyard", "game.ShipyardWork", "game.SilencedMarkers", "game.Spacewalks",
            "game.StationInfirmary", "game.StationTraffic", "game.WorldDebug", "game.WorldEvents", "game.YardWork");

    @Test
    void gameLogicDoesNotUseTheShellRenderingOrGraphics() throws IOException {
        Map<String, Set<String>> found = new TreeMap<>();
        for (Path file : javaFiles(GAME)) {
            Set<String> used = matches(FORBIDDEN_IN_GAME, code(file));
            if (!used.isEmpty()) found.put(GAME.relativize(file).toString().replace(".java", "").replace('\\', '/'), used);
        }
        List<String> problems = new ArrayList<>();
        found.forEach((cls, used) -> {
            Set<String> extra = new TreeSet<>(used);
            extra.removeAll(GAME_ALLOWED.getOrDefault(cls, Set.of()));
            if (!extra.isEmpty()) problems.add("game." + cls + " uses " + extra);
        });
        GAME_ALLOWED.forEach((cls, allowed) -> {
            Set<String> gone = new TreeSet<>(allowed);
            gone.removeAll(found.getOrDefault(cls, Set.of()));
            if (!gone.isEmpty()) problems.add("game." + cls + " no longer uses " + gone
                    + ": take it off ArchitectureTest.GAME_ALLOWED");
        });
        assertTrue(problems.isEmpty(), "Game logic must not use the shell, render, debug or "
                + "com.badlogic.gdx.graphics packages:\n  " + String.join("\n  ", problems));
    }

    @Test
    void modulesUseOnlyTheModulesBelowThem() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern modulePackage = Pattern.compile("\\bcom\\.ringsofiron\\.([a-z]+)\\b");
        Pattern corePackage = Pattern.compile("\\bcom\\.hawkwing\\.RingsOfIron\\b");
        Set<String> listed = new TreeSet<>(MODULE_USES.keySet());
        for (String module : MODULE_PACKAGES.values()) {
            Path src = ROOT.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(src), src + " is missing");
            Map<String, Set<String>> uses = new TreeMap<>();
            for (Path file : javaFiles(src)) {
                String code = code(file);
                String name = src.relativize(file).toString().replace('\\', '/');
                if (corePackage.matcher(code).find()) problems.add(module + " uses core, in " + name);
                Matcher m = modulePackage.matcher(code);
                while (m.find()) {
                    String used = MODULE_PACKAGES.get(m.group(1));
                    if (used == null) {
                        problems.add(module + " uses com.ringsofiron." + m.group(1)
                                + ", which no module owns, in " + name);
                    } else if (!used.equals(module)) {
                        uses.computeIfAbsent(used, k -> new TreeSet<>()).add(name);
                    }
                }
            }
            Set<String> allowed = MODULE_USES.get(module);
            if (allowed == null) {
                problems.add(module + " is not in ArchitectureTest.MODULE_USES");
                continue;
            }
            listed.remove(module);
            uses.forEach((used, files) -> {
                if (!allowed.contains(used)) problems.add(module + " uses " + used + ", in " + files);
            });
            for (String gone : allowed) {
                if (!uses.containsKey(gone)) problems.add(module + " no longer uses " + gone
                        + ": take it off ArchitectureTest.MODULE_USES");
            }
        }
        for (String stale : listed) problems.add(stale + " is in MODULE_USES but not MODULE_PACKAGES");
        assertTrue(problems.isEmpty(), "Module layering broke:\n  " + String.join("\n  ", problems));
    }

    @Test
    void noNewClassTakesTheWholeGameWorld() throws IOException, URISyntaxException, ClassNotFoundException {
        Path classes = Paths.get(GameWorld.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String base = "com.hawkwing.RingsOfIron.";
        Set<String> found = new TreeSet<>();
        for (Path file : files(classes, ".class")) {
            String name = classes.relativize(file).toString().replace('\\', '/').replace('/', '.');
            name = name.substring(0, name.length() - ".class".length());
            if (!name.startsWith(base) || name.startsWith(base + "game.GameWorld")) continue;
            Class<?> c = Class.forName(name, false, GameWorld.class.getClassLoader());
            // Anonymous and local classes take the world only as a captured variable of their outer one
            if (c.isAnonymousClass() || c.isLocalClass() || c.isSynthetic()) continue;
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                if (Modifier.isPrivate(k.getModifiers()) && k.isSynthetic()) continue;
                for (Class<?> p : k.getParameterTypes()) {
                    if (p == GameWorld.class) found.add(name.substring(base.length()).replace('$', '.'));
                }
            }
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(TAKES_GAME_WORLD);
        Set<String> gone = new TreeSet<>(TAKES_GAME_WORLD);
        gone.removeAll(found);
        List<String> problems = new ArrayList<>();
        if (!added.isEmpty()) problems.add("New classes take the whole GameWorld; give them the services they use: " + added);
        if (!gone.isEmpty()) problems.add("No longer take GameWorld; take them off ArchitectureTest.TAKES_GAME_WORLD: " + gone);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static Set<String> matches(Pattern pattern, String code) {
        Set<String> out = new TreeSet<>();
        Matcher m = pattern.matcher(code);
        while (m.find()) out.add(m.group());
        return out;
    }

    static List<Path> javaFiles(Path dir) throws IOException {
        return files(dir, ".java");
    }

    static List<Path> files(Path dir, String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(suffix)).sorted().collect(Collectors.toList());
        }
    }

    /** A source file with its comments and string and char literals blanked, so only code is checked. */
    static String code(Path file) throws IOException {
        String s = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 2;
                out.append(' ');
            } else if (s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                i = end < 0 ? s.length() : end + 3;
                out.append("\"\"");
            } else if (c == '"' || c == '\'') {
                i++;
                while (i < s.length() && s.charAt(i) != c && s.charAt(i) != '\n') i += s.charAt(i) == '\\' ? 2 : 1;
                i++;
                out.append(c).append(c);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
