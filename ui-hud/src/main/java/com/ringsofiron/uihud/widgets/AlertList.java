package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.ringsofiron.uihud.minimap.ShipAlerts;

import java.util.Locale;

/** Short text list of shipboard alerts shown beside the minimap. */
public class AlertList extends Table {
    private final Skin skin;
    private final Label hull;

    public AlertList(Skin skin) {
        this.skin = skin;
        left().top();
        hull = new Label("", skin, "dim");
        show(null);
    }

    public void show(ShipAlerts a) {
        clearChildren();
        if (a == null) return;
        hull.setText(String.format(Locale.ROOT, "HULL %3.0f%%", a.hullIntegrity * 100f));
        hull.setStyle(skin.get(a.hullIntegrity < ShipAlerts.HULL_DAMAGED ? "warn" : "dim", Label.LabelStyle.class));
        add(hull).left().row();
        if (a.breaches > 0) line("HULL BREACH x" + a.breaches, "alert");
        if (a.burningTiles > 0) line("FIRE  " + a.burningTiles + " tiles", "alert");
        if (a.vacuumCompartments > 0) line("VACUUM  " + a.vacuumCompartments + " rooms", "alert");
        int venting = a.lowPressureCompartments - a.vacuumCompartments;
        if (venting > 0) line("LOW PRESSURE  " + venting + " rooms", "warn");
        if (a.smokyCompartments > 0) line("SMOKE  " + a.smokyCompartments + " rooms", "warn");
        if (!a.any()) line("ALL SYSTEMS NOMINAL", "dim");
    }

    private void line(String text, String style) {
        add(new Label(text, skin, style)).left().row();
    }
}
