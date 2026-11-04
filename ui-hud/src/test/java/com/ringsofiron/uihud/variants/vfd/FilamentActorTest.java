package com.ringsofiron.uihud.variants.vfd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FilamentActorTest {

    @Test
    void aClickFindsTheLastBoxAddedUnderIt() {
        FilamentActor actor = new FilamentActor(f -> { }, id -> { });
        actor.setSize(1280f, 800f);
        actor.box(1, 0f, 0f, 100f, 100f);
        actor.box(2, 50f, 50f, 100f, 100f);
        assertEquals(1, actor.idAt(10f, 10f));
        // Where they overlap, the box added last (drawn on top) takes it
        assertEquals(2, actor.idAt(60f, 60f));
        assertEquals(-1, actor.idAt(500f, 500f));
        actor.clearBoxes();
        assertEquals(0, actor.boxCount());
        assertEquals(-1, actor.idAt(10f, 10f));
    }

    @Test
    void theStageHitsItOnlyInsideABoxCountingYDown() {
        FilamentActor actor = new FilamentActor(f -> { }, id -> { });
        actor.setSize(1280f, 800f);
        // A box 16 units from the top: the stage's y is up, so it is 784 down to 768 there
        actor.box(7, 100f, 0f, 50f, 16f);
        assertSame(actor, actor.hit(120f, 790f, true));
        assertNull(actor.hit(120f, 700f, true));
        assertNull(actor.hit(10f, 790f, true));
    }
}
