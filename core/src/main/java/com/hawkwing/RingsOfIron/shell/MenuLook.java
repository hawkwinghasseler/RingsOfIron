package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.ShellLook;

import java.util.function.Function;

/**
 * The menus' look for the HUD variant picked ({@link ShellLook}), or none for the classic menus. A
 * new look is made when the pick changes, and the last one disposed. A look that fails to start, or
 * fails later, leaves the classic menus up, as a variant that fails to attach leaves the classic
 * HUD; it is not made again until the variant is picked again.
 */
final class MenuLook {
    private final Function<String, HudVariant> variants;
    private String id;
    private ShellLook look;
    private Skin skin;
    /** A look that failed in use, kept until the menus are built again without its skin. */
    private ShellLook failed;

    /** @param variants a new, unattached instance of the variant with an id; null for the classic HUD */
    MenuLook(Function<String, HudVariant> variants) {
        this.variants = variants;
    }

    /**
     * Catches up with the variant picked, {@code id}, at the UI scale {@code uiScale}. True when the
     * look changed, so the menus are built again (with {@link #skin}, or the classic skin for null).
     */
    boolean sync(String id, float uiScale) {
        boolean changed = failed != null;
        if (failed != null) {
            quietly(failed);
            failed = null;
        }
        if (id.equals(this.id)) return changed;
        changed |= look != null;
        this.id = id;
        if (look != null) quietly(look);
        look = null;
        skin = null;
        ShellLook next = null;
        try {
            HudVariant v = variants.apply(id);
            next = v == null ? null : v.shellLook();
            Skin s = next == null ? null : next.skin();
            if (s == null) {
                if (next != null) quietly(next);
                return changed;
            }
            next.rescale(uiScale);
            look = next;
            skin = s;
            return true;
        } catch (RuntimeException e) {
            if (next != null) quietly(next);
            log("The menus' look for " + id + " failed to start; the classic menus stay", e);
            return changed;
        }
    }

    /**
     * The look threw while it drew or dressed the menus ({@code what}): the classic menus are back
     * from now on, and the next {@link #sync} says the look changed, once, so they are built again.
     */
    void fail(String what, RuntimeException e) {
        if (look == null) return;
        log("The menus' look for " + id + " failed to " + what + "; the classic menus are back", e);
        // Not disposed yet: the menus showing still use its skin until they are built again
        failed = look;
        look = null;
        skin = null;
    }

    /** The look, or null for the classic menus. */
    ShellLook look() {
        return look;
    }

    /** The look's skin, or null for the classic one. */
    Skin skin() {
        return skin;
    }

    void dispose() {
        if (look != null) quietly(look);
        if (failed != null) quietly(failed);
        look = null;
        failed = null;
        skin = null;
    }

    private static void quietly(ShellLook l) {
        try {
            l.dispose();
        } catch (RuntimeException e) {
            log("The menus' last look failed to dispose", e);
        }
    }

    private static void log(String message, RuntimeException e) {
        if (Gdx.app != null) Gdx.app.error("RingsOfIron", message, e);
    }
}
