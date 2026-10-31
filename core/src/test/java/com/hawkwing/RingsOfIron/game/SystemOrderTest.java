package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.physicsexterior.PhysicsManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the order the wired game runs in. GameWorld adds most of its systems at the same priority,
 * so the order they are added is the order they run; physics step listeners and save participants
 * likewise run in the order they were added. This test lists all three and compares them with
 * system-order.txt, so a refactor that reorders any of them shows up in review as a diff of that
 * file instead of as a silent change in play.
 *
 * <p>When a reorder is meant, regenerate the file and check the diff:
 * {@code ./gradlew :core:test --tests '*SystemOrderTest' -PpinSystemOrder}
 */
class SystemOrderTest {
    static final Path PINNED = Paths.get("src/test/resources/system-order.txt");

    static GameWorld world;

    @BeforeAll
    static void boot() {
        GdxNativesLoader.load();
        world = new GameWorld();
    }

    @AfterAll
    static void dispose() {
        world.dispose();
    }

    @Test
    void systemsStepListenersAndSaveParticipantsRunInThePinnedOrder() throws IOException {
        String actual = describe(world);
        if (Boolean.getBoolean("pin.systemOrder")) {
            Files.createDirectories(PINNED.getParent());
            Files.writeString(PINNED, actual, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(PINNED), PINNED.toAbsolutePath() + " is missing; run with -PpinSystemOrder");
        String pinned = Files.readString(PINNED, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(pinned, actual, "The wired game's run order changed. If that was meant, run "
                + "./gradlew :core:test --tests '*SystemOrderTest' -PpinSystemOrder and review the diff "
                + "of core/src/test/resources/system-order.txt");
    }

    /** Every engine system (in run order), step listener and save participant, one per line. */
    static String describe(GameWorld world) {
        StringBuilder out = new StringBuilder();
        out.append("# The wired game's run order, pinned by SystemOrderTest. Regenerate with\n")
                .append("# ./gradlew :core:test --tests '*SystemOrderTest' -PpinSystemOrder\n");

        out.append("\n[systems] priority, fixed (F) or frame (-), class\n");
        ImmutableArray<EntitySystem> systems = world.engine.getEcsEngine().getSystems();
        for (int i = 0; i < systems.size(); i++) {
            EntitySystem s = systems.get(i);
            out.append(s.priority).append(' ').append(s instanceof FixedLogicSystem ? 'F' : '-')
                    .append(' ').append(name(s)).append('\n');
        }

        out.append("\n[physics step listeners]\n");
        for (Object listener : stepListeners(world.physics)) out.append(name(listener)).append('\n');

        out.append("\n[save participants]\n");
        for (Object p : participants(world.engine.getSerializationService())) out.append(name(p)).append('\n');
        return out.toString();
    }

    /**
     * A class name that survives unrelated edits: package dropped, lambdas named by the class they
     * were written in (their generated suffix changes from run to run).
     */
    static String name(Object o) {
        String n = o.getClass().getName();
        int lambda = n.indexOf("$$Lambda");
        if (lambda >= 0) n = n.substring(0, lambda) + " (lambda)";
        int dot = n.lastIndexOf('.', lambda >= 0 ? lambda : n.length());
        return n.substring(dot + 1);
    }

    @SuppressWarnings("unchecked")
    static List<Object> stepListeners(PhysicsManager physics) {
        Array<PhysicsManager.StepListener> listeners = field(physics, "stepListeners");
        List<Object> out = new ArrayList<>();
        for (PhysicsManager.StepListener l : listeners) out.add(l);
        return out;
    }

    static List<Object> participants(SerializationService service) {
        Array<SaveParticipant> participants = field(service, "participants");
        List<Object> out = new ArrayList<>();
        for (SaveParticipant p : participants) out.add(p);
        return out;
    }

    /** Reads a private list; read only, so the order pin needs no accessor in production code. */
    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name) {
        try {
            Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(owner.getClass().getSimpleName() + "." + name
                    + " moved or was renamed; point SystemOrderTest at its new home", e);
        }
    }
}
