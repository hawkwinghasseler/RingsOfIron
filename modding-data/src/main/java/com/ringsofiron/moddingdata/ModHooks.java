package com.ringsofiron.moddingdata;

import com.badlogic.ashley.core.EntitySystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Lifecycle callbacks mod scripts can attach to. In {@code init.groovy}:
 * <pre>
 * hooks.onWorldReady { log.info("world is up") }
 * hooks.onTick { float dt -> ... }
 * </pre>
 * Exceptions from a callback are reported against its mod and do not stop the game.
 */
public class ModHooks {

    private static class Hook<T> {
        final String modId;
        final T callback;

        Hook(String modId, T callback) {
            this.modId = modId;
            this.callback = callback;
        }
    }

    /** Callback for {@link #onTick}. */
    @FunctionalInterface
    public interface TickListener {
        void tick(float deltaTime);
    }

    private final LoadReport report;
    private final List<Hook<Runnable>> worldReady = new ArrayList<>();
    private final List<Hook<TickListener>> tick = new ArrayList<>();
    private String currentMod = "game";
    private boolean worldReadyFired;

    public ModHooks(LoadReport report) {
        this.report = report;
    }

    /** Set by the script loader so hooks are credited to the mod that added them. */
    void setCurrentMod(String modId) {
        this.currentMod = modId;
    }

    /** Runs once the world is built; if it already is (a script allowed late), runs right away. */
    public void onWorldReady(Runnable callback) {
        Hook<Runnable> hook = new Hook<>(currentMod, callback);
        worldReady.add(hook);
        if (worldReadyFired) run(hook.modId, callback::run);
    }

    public void onTick(TickListener callback) {
        tick.add(new Hook<>(currentMod, callback));
    }

    /** Called by the game once the first world is built. Later calls do nothing. */
    public void fireWorldReady() {
        if (worldReadyFired) return;
        worldReadyFired = true;
        for (Hook<Runnable> h : new ArrayList<>(worldReady)) {
            run(h.modId, h.callback::run);
        }
    }

    public void fireTick(float deltaTime) {
        for (Hook<TickListener> h : new ArrayList<>(tick)) {
            run(h.modId, () -> h.callback.tick(deltaTime));
        }
    }

    public int tickListenerCount() {
        return tick.size();
    }

    private void run(String modId, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            report.error(modId, "Hook failed: " + e);
        }
    }

    /** An ECS system that fires tick hooks each frame; add it to the engine when any mod uses onTick. */
    public EntitySystem asSystem() {
        return new EntitySystem() {
            @Override
            public void update(float deltaTime) {
                fireTick(deltaTime);
            }
        };
    }
}
