package com.ringsofiron.uihud.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

/**
 * Tags an entity (with a {@code TransformComponent}) for an on-screen marker: corner brackets
 * and a nameplate while it is on screen, an edge arrow pointing at it while it is off screen.
 */
public class HudMarkerComponent implements Component {

    public enum Kind {
        HOSTILE(new Color(1f, 0.3f, 0.25f, 1f)),
        FRIENDLY(new Color(0.35f, 1f, 0.45f, 1f)),
        NEUTRAL(new Color(0.85f, 0.85f, 0.85f, 1f)),
        STATION(new Color(0.4f, 0.75f, 1f, 1f)),
        WAYPOINT(new Color(1f, 0.85f, 0.25f, 1f));

        public final Color defaultColor;

        Kind(Color defaultColor) {
            this.defaultColor = defaultColor;
        }
    }

    /** A disabled ship's marker tint. */
    public static final Color DISABLED_COLOR = new Color(0.6f, 0.6f, 0.62f, 1f);

    public String label = "";
    public Kind kind = Kind.NEUTRAL;
    /** Marker tint; defaults to the kind's color. */
    public final Color color = new Color();
    /** Show an arrow at the screen edge when the entity is off screen. */
    public boolean showOffscreen = true;
    /** Approximate radius in meters, so brackets frame the whole object when zoomed in. */
    public float radius = 10f;

    public HudMarkerComponent() {
        color.set(kind.defaultColor);
    }

    public HudMarkerComponent(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
        color.set(kind.defaultColor);
    }

    public HudMarkerComponent radius(float radius) {
        this.radius = radius;
        return this;
    }
}
