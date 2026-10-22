package com.hawkwing.RingsOfIron.debug;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.hawkwing.RingsOfIron.game.GameScenario;
import com.hawkwing.RingsOfIron.game.GameWorld;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.moddingdata.console.ScriptConsole;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The developer console on a real career: clicking to select, and the commands acting on the selection. */
class DevConsoleTest {
    GameWorld world;
    GameScenario game;
    DevConsole dev;
    @TempDir
    Path scripts;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        dev = new DevConsole(world, game, scripts, null);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    Entity crewNamed(String name) {
        for (Entity e : dev.actions.crewAboard(game.player)) {
            if (name.equals(e.getComponent(CrewMemberComponent.class).name)) return e;
        }
        throw new AssertionError("no " + name);
    }

    String last() {
        List<ScriptConsole.Line> lines = dev.console.lines();
        return lines.get(lines.size() - 1).text;
    }

    @Test
    void clicksCycleThroughTheCrewMemberTheirTileAndTheShipAndEscLetsGo() {
        Entity ada = crewNamed("Ada");
        Vector2 at = new Vector2();
        assertTrue(dev.picker.locate(DebugTarget.crew(ada, game.player), at) > 0f);
        List<Entity> decks = List.of(game.player);
        DebugTarget first = dev.click(at.x, at.y, true, 0.05f, decks);
        assertEquals(DebugTarget.Kind.CREW, first.kind);
        assertEquals(ada, first.entity);
        assertTrue(last().startsWith("Selected Ada"), last());
        assertEquals(DebugTarget.Kind.TILE, dev.click(at.x, at.y, true, 0.05f, decks).kind);
        assertEquals(DebugTarget.Kind.SHIP, dev.click(at.x, at.y, true, 0.05f, decks).kind);
        assertEquals(ada, dev.click(at.x, at.y, true, 0.05f, decks).entity, "round again to the crew member");
        assertTrue(dev.deselect());
        assertNull(dev.selected());
        assertFalse(dev.deselect(), "nothing left to let go of");

        // Outside, the ship is under its own middle
        Vector2 ship = new Vector2();
        dev.picker.locate(DebugTarget.craft(game.player, false), ship);
        DebugTarget outside = dev.click(ship.x, ship.y, false, 1f, null);
        assertEquals(game.player, outside.entity);
        assertNull(dev.click(ship.x + 50_000f, ship.y, false, 1f, null), "nothing out there");
    }

    @Test
    void killAndHealActOnTheSelectedCrewMember() {
        Entity ada = crewNamed("Ada");
        CrewMemberComponent c = ada.getComponent(CrewMemberComponent.class);
        dev.picker.select(DebugTarget.crew(ada, game.player));
        dev.console.submit("kill()");
        assertEquals(CrewState.DEAD, c.state);
        dev.console.submit("heal");
        assertTrue(last().contains("revive()"), last());
        dev.console.submit("revive()");
        assertEquals(CrewState.CONSCIOUS, c.state);
        dev.console.submit("ko()");
        assertEquals(CrewState.UNCONSCIOUS, c.state);
        dev.console.submit("heal()");
        assertEquals(CrewState.CONSCIOUS, c.state);
        assertEquals(c.def.maxHealth, c.health, 1e-3);
    }

    @Test
    void aSecondHitOnSomeoneCriticalHalvesTheirTime() {
        Entity bea = crewNamed("Bea");
        CrewMemberComponent c = bea.getComponent(CrewMemberComponent.class);
        dev.picker.select(DebugTarget.crew(bea, game.player));
        dev.console.submit("wound(95)");
        assertEquals(Wound.CRITICAL, c.wound);
        float full = c.bleedOut;
        assertTrue(last().contains("to bleed out"), last());
        dev.console.submit("wound 80");
        assertEquals(full * 0.5f, c.bleedOut, 1e-3);
        assertTrue(last().contains("(was "), last());
        dev.console.submit("crew().bleedOut = 3");
        assertEquals(3f, c.bleedOut, 1e-3);
        dev.console.submit("wound(250, 'BLAST')");
        assertEquals(CrewState.DEAD, c.state);
    }

    @Test
    void crewCommandsNeedACrewMemberAndSaySo() {
        dev.console.submit("kill()");
        assertTrue(last().startsWith("Nothing selected"), last());
        dev.picker.select(DebugTarget.craft(game.player, false));
        dev.console.submit("revive()");
        assertTrue(last().startsWith("Select a crew member first"), last());
    }

    @Test
    void fireBreachAndRepairActOnTheSelectedTile() {
        ShipGridComponent g = game.player.getComponent(ShipGridComponent.class);
        int[] floor = null, hull = null;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                Tile t = g.getTile(x, y);
                if (floor == null && t.holdsAir() && t.mount == null) floor = new int[]{x, y};
                if (hull == null && t.wallType == Tile.WallType.HULL) hull = new int[]{x, y};
            }
        }
        dev.picker.select(DebugTarget.tile(game.player, floor[0], floor[1]));
        dev.console.submit("fire()");
        assertTrue(g.getTile(floor[0], floor[1]).isBurning(), last());
        dev.console.submit("extinguish()");
        assertFalse(g.getTile(floor[0], floor[1]).isBurning());
        dev.picker.select(DebugTarget.tile(game.player, hull[0], hull[1]));
        dev.console.submit("breach()");
        assertTrue(g.getTile(hull[0], hull[1]).isHullBreach());
        dev.console.submit("repair(player)");
        assertFalse(g.getTile(hull[0], hull[1]).isHullBreach());
        dev.console.submit("damage 50");
        assertEquals(0.5f, g.getTile(hull[0], hull[1]).getIntegrity(), 1e-3);
    }

    @Test
    void weaponsBlastsRamsAndShortsGoThroughTheDemosTools() {
        Entity bea = crewNamed("Bea");
        dev.picker.select(DebugTarget.crew(bea, game.player));
        dev.console.submit("weapons()");
        dev.console.submit("shoot 'railgun_mk1'");
        assertTrue(last().contains("fired") || last().contains("launched") || last().contains("burning"), last());
        dev.console.submit("blast()");
        assertTrue(last().contains("went off"), last());
        dev.console.submit("ram 0.1, 195");
        assertTrue(last().contains("195000 kg at 100 m/s"), "a slow ram crashes a real rock into the hull: " + last());
        dev.console.submit("ram 3, 1");
        assertTrue(last().contains("3.0 km/s"), last());
        dev.console.submit("shortOut()");
        assertTrue(dev.console.lines().stream().noneMatch(l -> l.kind == ScriptConsole.Kind.ERROR),
                dev.console.lines().stream().map(l -> l.text).collect(java.util.stream.Collectors.joining("\n")));
        // A few seconds of the world: the round, the rock and the blast land on the deck
        for (int i = 0; i < 180; i++) world.update(1f / 60f);
    }

    @Test
    void boardersAreHostileAndRemoveTakesThemAway() {
        dev.picker.select(DebugTarget.craft(game.player, false));
        dev.console.submit("boarder()");
        DebugTarget b = dev.selected();
        assertTrue(b.isCrew(), last());
        CrewMemberComponent them = b.entity.getComponent(CrewMemberComponent.class);
        CrewMemberComponent ada = crewNamed("Ada").getComponent(CrewMemberComponent.class);
        assertTrue(world.crew.hostile(them, ada));
        int before = dev.actions.crewAboard(game.player).size();
        dev.console.submit("remove()");
        assertEquals(before - 1, dev.actions.crewAboard(game.player).size());
        assertNull(dev.selected());
    }

    @Test
    void spawnPutsANewCrewMemberAboardAndSelectsThem() {
        int before = dev.actions.crewAboard(game.player).size();
        dev.picker.select(DebugTarget.craft(game.player, false));
        dev.console.submit("spawn 'medic', 'Doc'");
        assertEquals(before + 1, dev.actions.crewAboard(game.player).size());
        DebugTarget doc = dev.selected();
        assertTrue(doc.isCrew());
        CrewMemberComponent c = doc.entity.getComponent(CrewMemberComponent.class);
        assertEquals("Doc", c.name);
        assertEquals(CrewRole.MEDIC, c.role);
        dev.console.submit("aboard()");
        assertTrue(last().contains("aboard") || dev.console.lines().stream().anyMatch(l -> l.text.startsWith("Doc")));
    }

    @Test
    void spawnShipPutsAShipAheadOfThePlayer() {
        dev.console.submit("ships()");
        String id = world.getContent().defs.ids("ship").get(0);
        dev.console.submit("spawnShip '" + id + "'");
        DebugTarget t = dev.selected();
        assertNotNull(t, last());
        assertEquals(DebugTarget.Kind.SHIP, t.kind);
        assertNotEquals(game.player, t.entity);
        dev.console.submit("spawnShip 'no_such_ship'");
        assertTrue(last().startsWith("No ship called"), last());
    }

    @Test
    void scriptsInTheScriptFolderRunByNameAgainstTheSelection() throws Exception {
        Files.writeString(scripts.resolve("heal_everyone.groovy"),
                "aboard(player).each { heal(it) }\nprintln \"healed ${aboard(player).size()}\"\n", StandardCharsets.UTF_8);
        Entity cole = crewNamed("Cole");
        dev.actions.wound(cole, 80f, null);
        dev.console.submit("heal_everyone");
        assertEquals(Wound.NONE, cole.getComponent(CrewMemberComponent.class).wound, last());
        assertTrue(last().startsWith("healed"), last());
    }

    @Test
    void theCheckedInScriptsRun() {
        Path real = Paths.get(System.getProperty("user.dir")).getParent().resolve("scripts");
        DevConsole d = new DevConsole(world, game, real, null);
        Entity cole = crewNamed("Cole");
        d.picker.select(DebugTarget.crew(cole, game.player));
        d.console.submit("crit_test");
        String said = d.console.lines().get(d.console.lines().size() - 1).text;
        assertTrue(said.startsWith("Bleed out: 300 s after the first hit, 150 s"), said);
        d.console.submit("heal_crew");
        assertEquals(Wound.NONE, cole.getComponent(CrewMemberComponent.class).wound);
        d.picker.select(DebugTarget.craft(game.player, false));
        d.console.submit("fire_drill 2");
        ShipGridComponent g = game.player.getComponent(ShipGridComponent.class);
        int burning = 0;
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) if (g.getTile(x, y).isBurning()) burning++;
        assertEquals(2, burning);
        int before = d.actions.crewAboard(game.player).size();
        d.console.submit("crew_up");
        assertEquals(before + 4, d.actions.crewAboard(game.player).size());
        assertTrue(d.console.lines().stream().noneMatch(l -> l.kind == ScriptConsole.Kind.ERROR),
                d.console.lines().stream().map(l -> l.text).collect(java.util.stream.Collectors.joining("\n")));
    }

    @Test
    void theSelectionAndThePlayerAreLiveVariables() {
        assertNull(dev.console.submit("selected"));
        dev.picker.select(DebugTarget.crew(crewNamed("Ada"), game.player));
        dev.console.submit("selected.entity == aboard(player).find { crew(it).name == 'Ada' }.entity");
        assertEquals("true", last());
        dev.console.submit("selected = null");
        assertTrue(last().contains("can't be assigned"), last());
    }

    @Test
    void overlayTogglesWhatTheHostDraws() {
        boolean[] shown = {false};
        dev.addOverlay("heat", "tile heat", () -> shown[0], on -> shown[0] = on);
        assertEquals("heat (hidden): tile heat", dev.console.execute("overlay()"));
        assertEquals("heat shown", dev.console.execute("overlay('heat')"));
        assertTrue(dev.overlayShown("heat"));
        assertEquals("heat hidden", dev.console.execute("overlay('heat')"));
        assertEquals("heat hidden", dev.console.execute("overlay('heat', false)"));
        assertEquals("heat shown", dev.console.execute("overlay('heat', true)"));
        assertNull(dev.console.execute("overlay('smoke')"));
        assertTrue(dev.overlayShown("heat"));
    }
}
