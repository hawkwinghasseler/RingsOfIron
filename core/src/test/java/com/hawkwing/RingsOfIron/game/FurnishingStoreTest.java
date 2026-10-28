package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.interiorgrid.Furnishing;
import com.ringsofiron.interiorgrid.FurnishingType;
import com.ringsofiron.interiorgrid.Furnishings;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SuitLockers;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.macrosim.economy.TraderComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Buying furniture at a station's yard and having it set down at an exact spot on the deck: docked
 * at Highport at the start of a career, the loaders carry it aboard and it is paid for as it lands.
 */
class FurnishingStoreTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    Entity highport;
    ShipGridComponent grid;
    TraderComponent trader;

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
        highport = world.engine.getEntity(game.highport);
        assertSame(highport, world.docking.dockedTo(game.player));
        grid = game.player.getComponent(ShipGridComponent.class);
        trader = game.player.getComponent(TraderComponent.class);
        trader.credits = 100_000;
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void theYardSellsRecreationFurnishingsFromTheData() {
        FurnishingType couch = world.furnishings.catalog().get("couch");
        assertNotNull(couch);
        assertEquals(FurnishingType.Use.LOUNGE, couch.use);
        assertTrue(world.furnishings.offers().size() >= 15, "a good range: " + world.furnishings.offers().size());
        assertTrue(world.furnishings.catalog().get("rug").flat);
        for (FurnishingType t : world.furnishings.offers()) assertTrue(t.price > 0, t.id);
    }

    @Test
    void anOrderedCouchIsCarriedAboardAndSetDownExactlyWhereItWasPut() {
        FurnishingType couch = world.furnishings.catalog().get("couch");
        float[] spot = spotFor(couch, 30f);
        double before = trader.credits, price = world.furnishings.price(couch, highport);
        String said = world.furnishings.order(game.player, highport, "couch", spot[0], spot[1], 30f);
        assertTrue(said.contains("ordered"), said);
        assertEquals(before, trader.credits, 1e-6, "paid for when it lands");
        assertEquals(1, world.furnishings.pending(game.player).size());
        assertNotNull(world.furnishings.refusal(game.player, couch, spot[0], spot[1], 30f), "the spot is held");
        assertTrue(runUntil(FurnishingStore.LATE_SECONDS + 60, () -> bought(grid).size == 1), "it comes aboard");
        Furnishing f = bought(grid).first();
        assertEquals(spot[0], f.x(), 1e-4f);
        assertEquals(spot[1], f.y(), 1e-4f);
        assertEquals(30f, f.rotation, 1e-4f);
        assertEquals(before - price, trader.credits, 1e-6);
        assertTrue(world.furnishings.pending(game.player).isEmpty());
    }

    @Test
    void leavingBeforeItComesAboardCallsTheOrderOffUnpaid() {
        FurnishingType arcade = world.furnishings.catalog().get("arcade_cabinet");
        float[] spot = spotFor(arcade, 0f);
        world.furnishings.order(game.player, highport, "arcade_cabinet", spot[0], spot[1], 0f);
        double before = trader.credits;
        assertTrue(world.docking.undock(game.player));
        run(10);
        assertTrue(world.furnishings.pending(game.player).isEmpty());
        assertEquals(0, bought(grid).size);
        assertEquals(before, trader.credits, 1e-6);
    }

    @Test
    void piecesAboardCanBeMovedAndSoldBack() {
        FurnishingType chair = world.furnishings.catalog().get("armchair");
        float[] spot = spotFor(chair, 0f, 45f);
        assertNull(Furnishings.place(grid, Furnishing.at(chair, spot[0], spot[1], 0f)));
        FurnishingPlacement arrange = FurnishingPlacement.arrange(world, game.player, highport);
        arrange.point(spot[0], spot[1]);
        assertTrue(arrange.click().startsWith("Moving"));
        assertNotNull(arrange.held());
        arrange.turn(45f);
        assertNull(arrange.refusal(), "turning it where it stands fits");
        assertTrue(arrange.click().endsWith("moved"));
        Furnishing moved = bought(grid).first();
        assertEquals(45f, moved.rotation, 1e-4f);
        double before = trader.credits;
        arrange.point(spot[0], spot[1]);
        assertTrue(arrange.sell().startsWith("The yard took"));
        assertEquals(before + chair.price * FurnishingStore.BUY_BACK, trader.credits, 1e-6);
        assertEquals(0, bought(grid).size);
    }

    @Test
    void placingFromTheYardWindowOrdersWhereThePointerIs() {
        FurnishingType table = world.furnishings.catalog().get("card_table");
        float[] spot = spotFor(table, 0f);
        FurnishingPlacement placing = FurnishingPlacement.buy(world, game.player, highport, table);
        placing.point(spot[0], spot[1]);
        assertNull(placing.refusal());
        assertNotNull(placing.ghost());
        assertTrue(placing.click().contains("ordered"));
        assertEquals(1, world.furnishings.pending(game.player).size());
        assertNotNull(placing.refusal(), "a second can't go on top of the first");
        placing.back();
        assertTrue(placing.isDone());
    }

    @Test
    void furnishingsAreSavedWithTheShip(@TempDir Path dir) {
        FurnishingType couch = world.furnishings.catalog().get("couch");
        float[] spot = spotFor(couch, 90f);
        Furnishing f = Furnishing.at(couch, spot[0], spot[1], 90f);
        assertNull(Furnishings.place(grid, f));
        f.fuel = couch.fuel / 2f;
        FileHandle file = new FileHandle(dir.resolve("quicksave.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        ShipGridComponent loaded = game.player.getComponent(ShipGridComponent.class);
        assertEquals(1, bought(loaded).size);
        Furnishing back = bought(loaded).first();
        assertEquals("couch", back.type.id);
        assertEquals(spot[0], back.x(), 1e-4f);
        assertEquals(0.5f, back.condition(), 1e-4f);
        assertEquals(SuitLockers.count(grid, Suit.Kind.IVA), SuitLockers.count(loaded, Suit.Kind.IVA), "the suits come back in their lockers");
        assertEquals(SuitLockers.count(grid, Suit.Kind.EVA), SuitLockers.count(loaded, Suit.Kind.EVA));
    }

    @Test
    void downtimeLiftsMoraleUpToTheCeiling() {
        Entity hand = null;
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (c.home == game.player && e != game.avatar) hand = e;
        }
        assertNotNull(hand);
        CrewRecord r = hand.getComponent(CrewMemberComponent.class).record();
        r.morale = 0.5f;
        r.pendingLeisure = 0.1f;
        run(120);
        assertEquals(0.6f, r.morale, 0.01f);
        r.morale = 0.85f;
        r.pendingLeisure = 0.2f;
        run(120);
        assertEquals(CrewCareer.LEISURE_CEILING, r.morale, 0.01f, "downtime alone lifts it only so far");
    }

    /** The furnishings aboard {@code grid} bought at a yard: all but the suit lockers it was built with. */
    private static com.badlogic.gdx.utils.Array<Furnishing> bought(ShipGridComponent grid) {
        com.badlogic.gdx.utils.Array<Furnishing> out = new com.badlogic.gdx.utils.Array<>();
        for (Furnishing f : Furnishings.all(grid)) if (!SuitLockers.isLocker(f)) out.add(f);
        return out;
    }

    /** The middle of the first floor spot aboard where {@code type} fits turned each of {@code rotations}. */
    private float[] spotFor(FurnishingType type, float... rotations) {
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                for (float dx : new float[]{0.5f, 0.3f, 0.7f}) {
                    float px = x + dx, py = y + 0.5f;
                    boolean fits = true;
                    for (float r : rotations) fits &= world.furnishings.refusal(game.player, type, px, py, r) == null;
                    if (fits) return new float[]{px, py};
                }
            }
        }
        fail("no room aboard for a " + type.id);
        return null;
    }

    private boolean runUntil(double seconds, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < seconds / FRAME; i++) {
            if (done.getAsBoolean()) return true;
            world.update(FRAME);
        }
        return done.getAsBoolean();
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }
}
