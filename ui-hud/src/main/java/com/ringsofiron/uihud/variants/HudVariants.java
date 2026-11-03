package com.ringsofiron.uihud.variants;

import com.badlogic.gdx.utils.Array;

import java.util.function.Supplier;

/**
 * The one place every HUD variant is registered: the classic HUD and the nine UI overhaul
 * combinations, three functional specs by three art styles. Each combination lives in its own
 * package, {@code com.ringsofiron.uihud.variants.<style>.<spec>}, and is listed here once; its PR
 * replaces the class's body, not this list.
 *
 * <p>The player's pick is kept as an id ({@link Entry#id}, "classic" or "style.spec") in the
 * settings file; {@link #setChosen} is how the game tells the HUD, as {@code UiScale} is for scale.
 */
public final class HudVariants {
    public static final String CLASSIC = "classic";

    /** The three art directions. */
    public enum Style {
        NASAPUNK("nasapunk", "Nasa-punk"),
        VFD("vfd", "90s VFD"),
        CASSETTE("cassette", "Cassette-futurism");

        public final String id, label;

        Style(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** The three functional specs (documentation/ui-overhaul). */
    public enum Spec {
        /** Ship stations, diegetic consoles (functional-stations.md). */
        STATIONS("stations", "Stations"),
        /** By player context: seat, situation, mode (functional-context.md). */
        CONTEXT("context", "Player context"),
        /** By the player's tasks and decisions (functional-task.md). */
        TASK("task", "Player tasks");

        public final String id, label;

        Spec(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static final class Entry {
        public final String id;
        /** Null for the classic HUD. */
        public final Style style;
        public final Spec spec;
        private final Supplier<HudVariant> factory;

        Entry(String id, Style style, Spec spec, Supplier<HudVariant> factory) {
            this.id = id;
            this.style = style;
            this.spec = spec;
            this.factory = factory;
        }

        /** What the settings list shows: "Classic", "Nasa-punk: Stations". */
        public String label() {
            return style == null ? "Classic" : style.label + ": " + spec.label;
        }

        /** A new instance, or null for the classic HUD (which needs none). */
        public HudVariant create() {
            return factory == null ? null : factory.get();
        }
    }

    private static final Array<Entry> ENTRIES = new Array<>();

    static {
        ENTRIES.add(new Entry(CLASSIC, null, null, null));
        add(Style.NASAPUNK, Spec.STATIONS, com.ringsofiron.uihud.variants.nasapunk.stations.NasaPunkStationsHud::new);
        add(Style.NASAPUNK, Spec.CONTEXT, com.ringsofiron.uihud.variants.nasapunk.context.NasaPunkContextHud::new);
        add(Style.NASAPUNK, Spec.TASK, com.ringsofiron.uihud.variants.nasapunk.task.NasaPunkTaskHud::new);
        add(Style.VFD, Spec.STATIONS, com.ringsofiron.uihud.variants.vfd.stations.VfdStationsHud::new);
        add(Style.VFD, Spec.CONTEXT, com.ringsofiron.uihud.variants.vfd.context.VfdContextHud::new);
        add(Style.VFD, Spec.TASK, com.ringsofiron.uihud.variants.vfd.task.VfdTaskHud::new);
        add(Style.CASSETTE, Spec.STATIONS, com.ringsofiron.uihud.variants.cassette.stations.CassetteStationsHud::new);
        add(Style.CASSETTE, Spec.CONTEXT, com.ringsofiron.uihud.variants.cassette.context.CassetteContextHud::new);
        add(Style.CASSETTE, Spec.TASK, com.ringsofiron.uihud.variants.cassette.task.CassetteTaskHud::new);
    }

    private static void add(Style style, Spec spec, Supplier<HudVariant> factory) {
        ENTRIES.add(new Entry(style.id + "." + spec.id, style, spec, factory));
    }

    private static volatile String chosen = CLASSIC;

    private HudVariants() {
    }

    /** Every variant, the classic HUD first, then by style and spec. Do not change the array. */
    public static Array<Entry> all() {
        return ENTRIES;
    }

    /** The entry with {@code id}, or the classic HUD's for an id that is unknown. */
    public static Entry find(String id) {
        for (Entry e : ENTRIES) if (e.id.equals(id)) return e;
        return ENTRIES.first();
    }

    /** Whether {@code id} names a variant. */
    public static boolean known(String id) {
        if (id == null) return false;
        for (Entry e : ENTRIES) if (e.id.equals(id)) return true;
        return false;
    }

    /** The variant the player picked; the HUD switches to it on its next frame. */
    public static void setChosen(String id) {
        chosen = known(id) ? id : CLASSIC;
    }

    public static String getChosen() {
        return chosen;
    }
}
