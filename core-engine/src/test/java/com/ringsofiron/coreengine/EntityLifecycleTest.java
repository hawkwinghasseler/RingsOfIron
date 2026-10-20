package com.ringsofiron.coreengine;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import org.junit.jupiter.api.Test;

/** Entities coming and going: the UUID registry, entity references, and what the engine refuses mid-update. */
class EntityLifecycleTest {

    static class NoOpFixed extends FixedLogicSystem {
        NoOpFixed() {
            super(1f / 60f);
        }

        @Override
        protected void updateFixed(float deltaTime) {
        }
    }

    @Test
    void anEntityIsFoundByItsUuidOnlyWhileItIsInTheEngine() {
        GameEngine game = new GameEngine();
        Entity e = game.createEntityWithUuid();
        String id = e.getComponent(UuidComponent.class).id;
        assertNull(game.getEntity(id), "not until it is added");
        game.addEntity(e);
        assertSame(e, game.getEntity(id));
        EntityRef ref = EntityRef.of(e);
        assertSame(e, ref.get(game.getUuidRegistry()));
        game.removeEntity(e);
        assertNull(game.getEntity(id));
        assertNull(ref.get(game.getUuidRegistry()), "a reference to a removed entity resolves to nothing");
    }

    @Test
    void whatIsLoadedAfterAClearIsNotTakenByRemovalsTheClearSetOff() {
        GameEngine game = new GameEngine();
        game.getEcsEngine().addSystem(new NoOpFixed());
        Entity ship = game.createEntityWithUuid();
        Entity aboard = game.createEntityWithUuid();
        game.addEntity(ship);
        game.addEntity(aboard);
        // As the crew go with their ship
        game.getEcsEngine().addEntityListener(new com.badlogic.ashley.core.EntityListener() {
            @Override public void entityAdded(Entity e) {}
            @Override public void entityRemoved(Entity e) {
                if (e == ship) game.removeEntity(aboard);
            }
        });
        game.clear();

        // A load: the engine's pool hands the freed entities out again
        Entity[] loaded = new Entity[4];
        for (int i = 0; i < loaded.length; i++) {
            loaded[i] = game.createEntityWithUuid();
            game.addEntity(loaded[i]);
        }
        game.update(1f / 60f);
        assertEquals(loaded.length, game.getEcsEngine().getEntities().size(), "every loaded entity is still there");
    }

    @Test
    void freshUuidsAreUnique() {
        GameEngine game = new GameEngine();
        for (int i = 0; i < 1000; i++) game.addEntity(game.createEntityWithUuid());
        assertEquals(1000, game.getUuidRegistry().size());
    }

    @Test
    void anEntityWithoutAUuidCannotBeReferenced() {
        GameEngine game = new GameEngine();
        Entity e = game.createEntity();
        game.addEntity(e);
        assertThrows(IllegalArgumentException.class, () -> EntityRef.of(e));
        assertEquals(0, game.getUuidRegistry().size());
        assertNull(new EntityRef().get(game.getUuidRegistry()), "an empty reference");
    }

    @Test
    void referencesAreEqualByUuid() {
        assertEquals(new EntityRef("a"), new EntityRef("a"));
        assertEquals(new EntityRef("a").hashCode(), new EntityRef("a").hashCode());
        assertNotEquals(new EntityRef("a"), new EntityRef("b"));
        assertEquals(new EntityRef(), new EntityRef());
        assertNotEquals(new EntityRef("a"), "a");
    }

    /**
     * A craft replaced by a new entity with the same UUID (added first, then the old one removed)
     * must stay findable: removing the old entity may only unregister itself.
     */
    @Test
    void removingAnEntityDoesNotUnregisterItsReplacement() {
        GameEngine game = new GameEngine();
        Entity old = game.createEntity();
        old.add(new UuidComponent("ship"));
        game.addEntity(old);
        Entity replacement = game.createEntity();
        replacement.add(new UuidComponent("ship"));
        game.addEntity(replacement);
        assertSame(replacement, game.getEntity("ship"));
        game.removeEntity(old);
        assertSame(replacement, game.getEntity("ship"), "the replacement is still registered");
    }

    @Test
    void clearEmptiesTheEngineAndTheRegistry() {
        GameEngine game = new GameEngine();
        for (int i = 0; i < 5; i++) game.addEntity(game.createEntityWithUuid());
        game.clear();
        assertEquals(0, game.getEcsEngine().getEntities().size());
        assertEquals(0, game.getUuidRegistry().size());
    }

    @Test
    void theClockCannotBeDrivenFromInsideAnUpdate() {
        GameEngine game = new GameEngine();
        RuntimeException[] caught = new RuntimeException[1];
        game.addSystem(new EntitySystem() {
            @Override
            public void update(float deltaTime) {
                try {
                    game.stepTicks(1);
                } catch (IllegalStateException e) {
                    caught[0] = e;
                }
            }
        });
        game.update(1f / 60f);
        assertNotNull(caught[0], "stepping from a system would re-enter the update");
    }

    @Test
    void railsWarpCannotGoBackwardsAndZeroIsANoOp() {
        GameEngine game = new GameEngine();
        game.stepTicks(10);
        assertThrows(IllegalArgumentException.class, () -> game.advanceRails(-1));
        assertTrue(game.advanceRails(0));
        assertEquals(10, game.getTick());
    }

    @Test
    void aRemovedFixedSystemIsLetGo() {
        GameEngine game = new GameEngine();
        NoOpFixed fixed = new NoOpFixed();
        game.addSystem(fixed);
        assertTrue(fixed.isBoundTo(game));
        game.removeSystem(fixed);
        assertFalse(fixed.isBoundTo(game));
        assertNull(game.getSystem(NoOpFixed.class));
    }

    @Test
    void aTimeStepMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new FixedLogicSystem(0f) {
            @Override
            protected void updateFixed(float deltaTime) {
            }
        });
    }
}
