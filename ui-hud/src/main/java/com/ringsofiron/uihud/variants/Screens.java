package com.ringsofiron.uihud.variants;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Who shows each of the classic windows, the map's panels and the game's text panels
 * ({@link ClassicWindow}): the classic HUD, or the variant drawing now when it
 * {@link HudVariant#replaces replaces} that window. {@code UiHud} opens, closes and toggles them
 * here and asks here whether one is open, so the game's keys, Esc and its other checks (no flying
 * keys into a console, the pad's menu mode) work the same whoever draws the window.
 *
 * <p>The classic window stays the game's word on whether it can open ({@link Classic#available}):
 * the market only while docked, boarding only while latched. A variant's screen for a window that
 * can no longer open is closed on the next {@link #update}.
 *
 * <p>The map is shared: its orbit canvas is the classic HUD's whoever draws its panels, so opening
 * the map opens the canvas and, under a variant that replaces it, the variant's panels too.
 */
public final class Screens {
    /** A classic window as seen here: whether it can open now, whether it is open, opening or closing it. */
    public interface Classic {
        boolean available();

        boolean isOpen();

        /** Opens or closes it; whether it is open after. */
        boolean setOpen(boolean open);
    }

    private final EnumMap<ClassicWindow, Classic> classic = new EnumMap<>(ClassicWindow.class);
    private final EnumSet<ClassicWindow> replaced = EnumSet.noneOf(ClassicWindow.class);
    private final EnumSet<ClassicWindow> reopen = EnumSet.noneOf(ClassicWindow.class);
    private HudVariant variant;

    /** What the classic HUD does for {@code window}. One each; a window none is registered for never opens. */
    public void register(ClassicWindow window, Classic c) {
        classic.put(window, c);
    }

    /**
     * The variant drawing from now on, or null for the classic HUD; which windows it replaces is read
     * now. {@code wasOpen} is what was open before the switch ({@link #opened}, asked before the old
     * variant was detached): those open again, shown by whoever shows them now, so cycling variants
     * with a window up keeps the window. Not a console's (E opens it again) or the game-over screen
     * (it opens by itself).
     */
    public void setVariant(HudVariant next, EnumSet<ClassicWindow> wasOpen) {
        // Classic windows the new variant shows its own screens for close under it
        for (ClassicWindow w : ClassicWindow.values()) {
            if (w == ClassicWindow.MAP || next == null || !next.replaces(w)) continue;
            Classic c = classic.get(w);
            if (c != null && c.isOpen()) c.setOpen(false);
        }
        variant = next;
        replaced.clear();
        if (next != null) for (ClassicWindow w : ClassicWindow.values()) if (next.replaces(w)) replaced.add(w);
        reopen.clear();
        if (wasOpen != null) reopen.addAll(wasOpen);
        reopen.remove(ClassicWindow.CONSOLE);
        reopen.remove(ClassicWindow.GAME_OVER);
        for (ClassicWindow w : reopen) {
            if (w == ClassicWindow.MAP) {
                // The canvas stayed up: only the new variant's panels need asking
                if (replaced(w) && isOpen(w)) variant.open(w);
            } else if (!isOpen(w)) {
                open(w);
            }
        }
    }

    /** Adds every window open now (classic or the variant's) to {@code out}. */
    public void opened(EnumSet<ClassicWindow> out) {
        for (ClassicWindow w : ClassicWindow.values()) if (isOpen(w)) out.add(w);
    }

    /** Whether the variant drawing now shows its own screen for {@code window}. */
    public boolean replaced(ClassicWindow window) {
        return variant != null && replaced.contains(window);
    }

    /** Whether {@code window} can open now, as the classic HUD would let it. */
    public boolean available(ClassicWindow window) {
        Classic c = classic.get(window);
        return c != null && c.available();
    }

    /** Whether {@code window} is open, whoever shows it. The map is open while its canvas is. */
    public boolean isOpen(ClassicWindow window) {
        if (replaced(window) && window != ClassicWindow.MAP) return variant.showing(window);
        Classic c = classic.get(window);
        return c != null && c.isOpen();
    }

    /** Opens {@code window} if it can open now; whether it is open after. */
    public boolean open(ClassicWindow window) {
        Classic c = classic.get(window);
        if (window == ClassicWindow.MAP) {
            if (c == null || !c.setOpen(true)) return false;
            if (replaced(window)) variant.open(window);
            return true;
        }
        if (!replaced(window)) return c != null && c.setOpen(true);
        return available(window) && variant.open(window);
    }

    /** Closes {@code window} if it is open, whoever shows it. */
    public void close(ClassicWindow window) {
        if (replaced(window)) {
            if (variant.showing(window)) variant.close(window);
            if (window != ClassicWindow.MAP) return;
        }
        Classic c = classic.get(window);
        if (c != null && c.isOpen()) c.setOpen(false);
    }

    /** Its key: closes {@code window} if it is open, else opens it if it can. True when it did either. */
    public boolean toggle(ClassicWindow window) {
        if (isOpen(window)) {
            close(window);
            return true;
        }
        return open(window);
    }

    /** Esc: closes the topmost window that is open ({@link ClassicWindow#ESCAPE_ORDER}); true if one closed. */
    public boolean back() {
        for (ClassicWindow w : ClassicWindow.ESCAPE_ORDER) {
            if (isOpen(w)) {
                close(w);
                return true;
            }
        }
        return false;
    }

    /** Whether a window Esc would close is open: the map, a window, the log or the controls. Not the game-over screen. */
    public boolean anyOpen() {
        for (ClassicWindow w : ClassicWindow.ESCAPE_ORDER) if (isOpen(w)) return true;
        return false;
    }

    /** Whether a screen of the variant's shows, the map's panels included. */
    public boolean variantShowing() {
        if (variant == null) return false;
        for (ClassicWindow w : replaced) if (variant.showing(w)) return true;
        return false;
    }

    /**
     * Once a frame: closes the variant's screens for windows that can no longer open (undocked, the
     * ships parted), opens its game-over screen when the career ends, and keeps the map's canvas and
     * the variant's panels for it open together.
     */
    public void update() {
        if (variant == null) return;
        for (ClassicWindow w : replaced) {
            if (w == ClassicWindow.MAP) {
                Classic c = classic.get(w);
                boolean canvas = c != null && c.isOpen();
                boolean panels = variant.showing(w);
                if (canvas && !panels) c.setOpen(false);
                else if (!canvas && panels) variant.close(w);
            } else if (w == ClassicWindow.GAME_OVER) {
                boolean over = available(w);
                if (over && !variant.showing(w)) variant.open(w);
                else if (!over && variant.showing(w)) variant.close(w);
            } else if (variant.showing(w) && !available(w)) {
                variant.close(w);
            }
        }
    }
}
