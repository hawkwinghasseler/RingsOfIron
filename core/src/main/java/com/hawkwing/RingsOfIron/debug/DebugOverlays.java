package com.hawkwing.RingsOfIron.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The overlays one game screen can show for a developer, by id, in the order they were added: the debug menu's
 * check boxes and the console's {@code overlay()} both work through it. An overlay is either drawn here (a
 * {@link DebugOverlay}, whose on/off this keeps) or drawn by something else that only lends its switch
 * ({@link #add(String, String, BooleanSupplier, Consumer)}, as the deck renderer's own washes do).
 */
public final class DebugOverlays {
    public static final class Toggle {
        public final String id;
        public final String description;
        /** What draws it here, or null for one drawn elsewhere. */
        public final DebugOverlay overlay;
        private final BooleanSupplier shown;
        private final Consumer<Boolean> show;

        Toggle(String id, String description, DebugOverlay overlay, BooleanSupplier shown, Consumer<Boolean> show) {
            this.id = id;
            this.description = description;
            this.overlay = overlay;
            this.shown = shown;
            this.show = show;
        }

        public boolean isShown() {
            return shown.getAsBoolean();
        }

        public void setShown(boolean on) {
            show.accept(on);
        }

        @Override
        public String toString() {
            return id + (isShown() ? " (shown): " : " (hidden): ") + description;
        }
    }

    private final Map<String, Toggle> toggles = new LinkedHashMap<>();
    private int revision;

    /** An overlay drawn here, off unless {@code on}. Replaces one with the same id. */
    public Toggle add(String id, String description, DebugOverlay overlay, boolean on) {
        boolean[] state = {on};
        return put(new Toggle(id, description, overlay, () -> state[0], v -> state[0] = v));
    }

    /** The switch of an overlay drawn elsewhere: {@code shown} says whether it is on, {@code show} turns it on or off. */
    public Toggle add(String id, String description, BooleanSupplier shown, Consumer<Boolean> show) {
        return put(new Toggle(id, description, null, shown, show));
    }

    private Toggle put(Toggle t) {
        toggles.put(t.id, t);
        revision++;
        return t;
    }

    public Toggle get(String id) {
        return toggles.get(id);
    }

    public boolean isShown(String id) {
        Toggle t = toggles.get(id);
        return t != null && t.isShown();
    }

    /** Turns the overlay on or off; false when there is none by that id. */
    public boolean setShown(String id, boolean on) {
        Toggle t = toggles.get(id);
        if (t == null) return false;
        t.setShown(on);
        return true;
    }

    public List<Toggle> all() {
        return Collections.unmodifiableList(new ArrayList<>(toggles.values()));
    }

    public List<String> ids() {
        return new ArrayList<>(toggles.keySet());
    }

    /** Goes up each time one is added, so the menu knows to rebuild. */
    public int revision() {
        return revision;
    }

    /** The world half of each overlay that is on. */
    public void drawWorld(OverlayView view) {
        for (Toggle t : toggles.values()) {
            if (t.overlay != null && t.isShown()) t.overlay.drawWorld(view);
        }
    }

    /** The screen half of each overlay that is on. */
    public void drawScreen(OverlayView view) {
        for (Toggle t : toggles.values()) {
            if (t.overlay != null && t.isShown()) t.overlay.drawScreen(view);
        }
    }
}
