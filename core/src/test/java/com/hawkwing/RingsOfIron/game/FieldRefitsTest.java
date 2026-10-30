package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.providers.YardProvider;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The patrol career's corvette, cast off from Highport with a salvaged railgun in its hold: ordered
 * from the yard window away from any yard, an engineer takes it out on EVA and mounts it on the
 * nose, and brings the coilgun it replaced in to the hold. A save made with the crate out in hand
 * loads with it back in the hold, and the work goes on.
 */
class FieldRefitsTest {
    static final float FRAME = 1f / 60f;
    static final String RAILGUN = "railgun_mk1";

    GameWorld world;
    GameScenario game;
    CargoHoldComponent hold;
    YardWork yard;
    int nose;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
        assertTrue(world.docking.undock(game.player), "cast off");
        run(60 * 5);
        hold = game.player.getComponent(CargoHoldComponent.class);
        assertEquals(1.0, hold.load(StationYard.PART + RAILGUN, 1, Salvage.CRATE_VOLUME, Salvage.CRATE_MASS), 1e-9);
        yard = new YardWork(world.context, world.docking, world.combatDefs, world.yard, world.fieldRefits, world.furnishings);
        YardProvider.YardBoard board = new YardProvider.YardBoard();
        yard.collect(board);
        assertTrue(board.field, "no yard here: the crew's own refit");
        nose = -1;
        for (YardProvider.Hardpoint h : board.hardpoints) if (h.name.equals("the nose")) nose = h.index;
        assertTrue(nose >= 0, "the nose hardpoint");
        YardProvider.Offer railgun = null;
        for (YardProvider.Offer o : board.offers) if (o.weaponId.equals(RAILGUN)) railgun = o;
        assertNotNull(railgun, "the salvaged railgun on offer");
        assertEquals(1, railgun.salvaged);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    private WeaponMountComponent noseMount() {
        return game.player.getComponent(WeaponsComponent.class).mounts.get(nose);
    }

    private boolean mounted() {
        return noseMount().def.id.equals(RAILGUN) && world.fieldRefits.order(game.player, nose) == null;
    }

    @Test
    void anEngineerTakesTheRailgunOutMountsItOnTheNoseAndBringsTheCoilgunIn() {
        String old = noseMount().def.id;
        String said = yard.orderSalvagedRefit(nose, RAILGUN);
        assertTrue(said.startsWith("An engineer will take the salvaged"), said);
        YardProvider.YardBoard board = new YardProvider.YardBoard();
        yard.collect(board);
        assertTrue(board.hardpoints.get(nose).callOff, "can be called off while the crate is inside");
        assertTrue(board.hardpoints.get(nose).progress.contains("in the hold"), board.hardpoints.get(nose).progress);
        boolean carried = false, atMount = false;
        // The turret's condition the moment the railgun goes on: the crew repair it after
        float condition = Float.NaN;
        SubsystemComponent turret = noseMount().subsystem.getComponent(SubsystemComponent.class);
        for (int i = 0; i < 60 * 60 * 15 && !mounted(); i++) {
            run(1);
            for (Entity e : world.spacewalks.onEva()) {
                CrewMemberComponent c = world.crew.crew(e);
                EvaComponent eva = EvaSystem.of(e);
                if (c.crate != null && eva.outside() && eva.cargo == (float) Salvage.CRATE_MASS) carried = true;
            }
            FieldRefitComponent.Order o = world.fieldRefits.order(game.player, nose);
            atMount |= o != null && o.stage == FieldRefitComponent.Stage.AT_MOUNT;
            if (Float.isNaN(condition) && noseMount().def.id.equals(RAILGUN)) condition = turret.health;
        }
        assertTrue(carried, "the crate carried out on EVA, its mass with theirs");
        assertTrue(atMount, "clipped on at the hardpoint for the work");
        assertTrue(mounted(), "the railgun on the nose, and the order over");
        assertEquals(0.0, hold.amount(StationYard.PART + RAILGUN), 1e-9, "out of the hold");
        assertEquals(1.0, hold.amount(StationYard.PART + old), 1e-9, "the " + old + " brought in to the hold");
        assertTrue(condition <= StationYard.USED_CONDITION + 1e-6, "a used part's condition: " + condition);
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains(" mounted the salvaged ")));
        for (int i = 0; i < 60 * 5 && !world.spacewalks.onEva().isEmpty(); i++) run(60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "everyone back in");
    }

    @Test
    void theNoseGoesQuietOnceTheWorkOnItStartsAndStaysSoTillTheRailgunIsOn() {
        String coilgun = noseMount().def.id;
        assertTrue(world.weaponControl.available(noseMount()), "the coilgun ready to fire");
        yard.orderSalvagedRefit(nose, RAILGUN);
        boolean readyTillThen = true;
        FieldRefitComponent.Order o = null;
        for (int i = 0; i < 60 * 60 * 10 && (o == null || o.worked <= 0f); i++) {
            run(1);
            o = world.fieldRefits.order(game.player, nose);
            if (o == null || o.worked <= 0f) readyTillThen &= world.weaponControl.available(noseMount());
        }
        assertNotNull(o);
        assertTrue(o.worked > 0f, "the work at the hardpoint begun");
        assertTrue(readyTillThen, "the coilgun in action while the railgun was fetched and carried out");
        run(2);
        assertEquals(coilgun, noseMount().def.id);
        assertFalse(world.weaponControl.available(noseMount()), "the coilgun out of action once the work starts");
        assertTrue(world.notices.history().stream().anyMatch(n -> n.text.contains(" is unbolting the ")));
        YardProvider.YardBoard board = new YardProvider.YardBoard();
        yard.collect(board);
        assertTrue(board.hardpoints.get(nose).progress.contains("out of action"), board.hardpoints.get(nose).progress);

        // Called in part way: the work done stands, and the coilgun stays unbolted
        Entity engineer = world.spacewalks.onEva().get(0);
        assertEquals("Call in", world.spacewalks.orderFor(engineer));
        world.spacewalks.order(engineer);
        for (int i = 0; i < 60 * 5 && !world.spacewalks.onEva().isEmpty(); i++) run(60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "back in");
        o = world.fieldRefits.order(game.player, nose);
        assertEquals(FieldRefitComponent.Stage.AT_MOUNT, o.stage, "the crate left clipped on at the hardpoint");
        assertEquals(coilgun, noseMount().def.id);
        assertFalse(world.weaponControl.available(noseMount()), "still out of action with the work half done");

        assertEquals("Let out", world.spacewalks.orderFor(engineer));
        world.spacewalks.order(engineer);
        for (int i = 0; i < 60 * 20 && !mounted(); i++) run(60);
        assertTrue(mounted(), "the railgun on");
        run(2);
        assertTrue(world.weaponControl.available(noseMount()), "the railgun ready to fire");
    }

    @Test
    void aSaveWithTheCrateOutInHandLoadsWithItBackInTheHoldAndTheWorkGoesOn(@TempDir Path dir) {
        yard.orderSalvagedRefit(nose, RAILGUN);
        boolean out = false;
        for (int i = 0; i < 60 * 60 * 10 && !out; i++) {
            run(1);
            FieldRefitComponent.Order o = world.fieldRefits.order(game.player, nose);
            out = o != null && o.stage == FieldRefitComponent.Stage.CARRIED && o.carrier != null && EvaSystem.outside(o.carrier);
        }
        assertTrue(out, "out on the hull with it");
        assertEquals(0.0, hold.amount(StationYard.PART + RAILGUN), 1e-9);
        FileHandle file = new FileHandle(dir.resolve("crate.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        hold = game.player.getComponent(CargoHoldComponent.class);
        run(2);
        FieldRefitComponent.Order o = world.fieldRefits.order(game.player, nose);
        assertNotNull(o, "the order saved");
        assertEquals(1.0, hold.amount(StationYard.PART + RAILGUN), 1e-9, "the crate back in the hold");
        assertTrue(o.stage == FieldRefitComponent.Stage.HOLD || o.stage == FieldRefitComponent.Stage.CARRIED, o.stage.name());
        for (int i = 0; i < 60 * 20 && !mounted(); i++) run(60);
        assertTrue(mounted(), "mounted after the load all the same");
    }
}
