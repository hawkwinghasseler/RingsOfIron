package com.ringsofiron.audiospatial.backend;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;

/**
 * Bookkeeping shared by the built-in backends: instances, the listener and global
 * parameters. Released instances are dropped once they stop, as in FMOD.
 */
public abstract class SimulatedBackend implements AudioBackend {
    protected final AudioEventCatalog catalog;
    protected final Array<SimulatedInstance> instances = new Array<>();
    protected float listenerX, listenerY, listenerVx, listenerVy;
    private final ObjectFloatMap<String> globals = new ObjectFloatMap<>();

    protected SimulatedBackend(AudioEventCatalog catalog) {
        this.catalog = catalog;
    }

    public AudioEventCatalog getCatalog() { return catalog; }

    @Override public void loadBank(String path) {}

    @Override
    public AudioEventInstance createInstance(String eventPath) {
        SimulatedInstance i = newInstance(catalog.get(eventPath), eventPath);
        instances.add(i);
        return i;
    }

    private SimulatedInstance newInstance(SoundEventDef def, String requestedPath) {
        // Unknown paths get the fallback rules under their own name
        SoundEventDef d = def.path.equals(requestedPath) ? def
                : new SoundEventDef(requestedPath, def.looping, def.duration, def.minDistance, def.maxDistance, def.volume);
        return createSimulated(d);
    }

    /** Creates this backend's instance type. */
    protected SimulatedInstance createSimulated(SoundEventDef def) {
        return new SimulatedInstance(def);
    }

    @Override
    public void setListener(float x, float y, float vx, float vy) {
        listenerX = x; listenerY = y; listenerVx = vx; listenerVy = vy;
    }

    @Override public void setGlobalParameter(String name, float value) { globals.put(name, value); }

    @Override public float getGlobalParameter(String name, float defaultValue) { return globals.get(name, defaultValue); }

    @Override
    public void update(float deltaTime) {
        for (int i = instances.size - 1; i >= 0; i--) {
            SimulatedInstance inst = instances.get(i);
            inst.advance(deltaTime, listenerX, listenerY);
            if (inst.isReleased() && !inst.isPlaying()) {
                instances.removeIndex(i);
                onRemoved(inst);
            }
        }
    }

    /** Called when a released instance has stopped and been dropped. */
    protected void onRemoved(SimulatedInstance instance) {}

    /** Live instances, including stopped ones that have not been released. */
    public Array<SimulatedInstance> getInstances() { return instances; }

    public float getListenerX() { return listenerX; }
    public float getListenerY() { return listenerY; }

    @Override
    public void dispose() {
        instances.clear();
    }
}
