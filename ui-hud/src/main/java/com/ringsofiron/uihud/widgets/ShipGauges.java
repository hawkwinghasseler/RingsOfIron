package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.ringsofiron.uihud.HudSkin;
import com.ringsofiron.uihud.providers.ShipStatusProvider;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;

import java.util.Locale;

/**
 * The ship's four gauges (GDD section 2): propellant, structure, power and oxygen, each a bar with
 * its value beside it. Bars turn amber below {@link #LOW} and red below {@link #CRITICAL}.
 */
public class ShipGauges extends Table {
    public static final float LOW = 0.5f;
    public static final float CRITICAL = 0.25f;
    private static final float BAR_WIDTH = 120f;
    private static final float BAR_HEIGHT = 9f;

    private final Bar propellant, structure, power, oxygen;
    private final Label propellantText, structureText, powerText, oxygenText;
    private ShipStatusProvider provider;

    public ShipGauges(Skin skin) {
        TextureRegion white = skin.getRegion("white");
        left().top();
        add(new Label("SHIP STATUS", skin, "title")).left().colspan(3).row();
        propellant = new Bar(white);
        structure = new Bar(white);
        power = new Bar(white);
        oxygen = new Bar(white);
        propellantText = row(skin, "Propellant", propellant);
        structureText = row(skin, "Structure", structure);
        powerText = row(skin, "Power", power);
        oxygenText = row(skin, "Oxygen", oxygen);
    }

    private Label row(Skin skin, String name, Bar bar) {
        add(new Label(name, skin, "dim")).left().padRight(8f).minWidth(70f);
        add(bar).size(BAR_WIDTH, BAR_HEIGHT).padRight(8f);
        Label text = new Label("", skin, "default");
        add(text).left().minWidth(110f).row();
        return text;
    }

    public void setProvider(ShipStatusProvider provider) {
        this.provider = provider;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        ShipStatus s = provider == null ? null : provider.getShipStatus();
        if (s == null) {
            for (Bar b : new Bar[]{propellant, structure, power, oxygen}) b.level = Float.NaN;
            for (Label l : new Label[]{propellantText, structureText, powerText, oxygenText}) set(l, "--");
            return;
        }
        propellant.level = s.propellant;
        set(propellantText, propellantText(s));
        structure.level = s.structure;
        set(structureText, percent(s.structure));
        power.level = s.power;
        set(powerText, powerText(s));
        // With no oxygen tanks the bar shows the air in the worst room instead
        oxygen.level = Float.isNaN(s.oxygen) ? s.cabinPressure : s.oxygen;
        set(oxygenText, oxygenText(s));
    }

    static String propellantText(ShipStatus s) {
        if (Float.isNaN(s.propellant)) return "no tanks";
        return percent(s.propellant) + String.format(Locale.ROOT, "  %,.0f kg", s.propellantKg);
    }

    static String powerText(ShipStatus s) {
        if (Float.isNaN(s.power) || s.powerOutputWatts <= 0) return "NO POWER";
        return percent(s.power) + "  " + watts(s.powerOutputWatts);
    }

    static String oxygenText(ShipStatus s) {
        String air = s.cabinPressure < 0.1f ? "  VACUUM" : s.cabinPressure < 0.7f ? "  LOW AIR" : "";
        if (Float.isNaN(s.oxygen)) return "air " + percent(s.cabinPressure) + air;
        return percent(s.oxygen) + air;
    }

    static String percent(float share) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0f, share) * 100f);
    }

    static String watts(double w) {
        if (w >= 1e6) return String.format(Locale.ROOT, "%.1f MW", w / 1e6);
        if (w >= 1e3) return String.format(Locale.ROOT, "%.0f kW", w / 1e3);
        return String.format(Locale.ROOT, "%.0f W", w);
    }

    /** The bar's color at {@code level}: cyan, amber below {@link #LOW}, red below {@link #CRITICAL}. */
    public static Color levelColor(float level) {
        if (Float.isNaN(level)) return HudSkin.HUD_DIM;
        if (level < CRITICAL) return HudSkin.HUD_ALERT;
        if (level < LOW) return HudSkin.HUD_WARN;
        return HudSkin.HUD_CYAN;
    }

    private static void set(Label label, String text) {
        if (!label.textEquals(text)) label.setText(text);
    }

    /** A horizontal bar filled to {@link #level}; empty and dim at NaN. */
    static final class Bar extends Widget {
        private final TextureRegion white;
        float level = Float.NaN;

        Bar(TextureRegion white) {
            this.white = white;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color old = batch.getColor().cpy();
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            batch.setColor(0.1f, 0.2f, 0.3f, 0.9f * parentAlpha);
            batch.draw(white, x, y, w, h);
            if (!Float.isNaN(level)) {
                Color c = levelColor(level);
                batch.setColor(c.r, c.g, c.b, parentAlpha);
                batch.draw(white, x + 1f, y + 1f, Math.max(0f, Math.min(1f, level)) * (w - 2f), h - 2f);
            }
            batch.setColor(old);
        }

        @Override
        public float getPrefWidth() {
            return BAR_WIDTH;
        }

        @Override
        public float getPrefHeight() {
            return BAR_HEIGHT;
        }
    }
}
