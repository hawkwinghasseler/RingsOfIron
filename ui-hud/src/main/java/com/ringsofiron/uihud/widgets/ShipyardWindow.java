package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.ShipyardProvider;
import com.ringsofiron.uihud.providers.ShipyardProvider.Ship;
import com.ringsofiron.uihud.providers.ShipyardProvider.Yard;

import java.util.Locale;

/**
 * The shipyard: while docked at a station that sells ships, each ship with its price after the
 * trade-in and a button to buy it (or why it can't be bought yet); with a prize in tow near a
 * station, buttons to sell it or turn it in. Refreshes from the {@link ShipyardProvider} a few
 * times a second.
 */
public class ShipyardWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.5f;

    private final Skin skin;
    private final Yard yard = new Yard();
    private final Label current;
    private final Label status;
    private final Table ships = new Table();
    private final Table prize = new Table();
    private ShipyardProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;
    /** What the tables show, so rows are only rebuilt when it changes. */
    private String shown = null;
    private final Array<Label> terms = new Array<>();

    public ShipyardWindow(Skin skin) {
        super("", skin);
        this.skin = skin;
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        current = new Label("", skin, "dim");
        status = new Label("", skin, "dim");
        add(current).left().row();
        add(ships).left().padTop(8f).row();
        add(prize).left().padTop(12f).row();
        add(status).left().padTop(8f).row();
        add(button("Close [Y]", () -> setOpen(false))).right().padTop(10f);
        setVisible(false);
    }

    public void setProvider(ShipyardProvider provider) {
        this.provider = provider;
        refresh();
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** True while docked at a yard that sells ships, or with a prize to hand over. */
    public boolean isAvailable() {
        return yard.isOpen();
    }

    /** Opens the window if there is anything to show; closing always works. Returns whether it is now open. */
    public boolean setOpen(boolean open) {
        if (open) {
            refresh();
            if (!yard.isOpen()) return false;
            status.setText("");
            shown = null;
            refresh();
            setVisible(true);
            pack();
            if (getStage() != null) {
                setPosition((getStage().getWidth() - getWidth()) / 2f, (getStage().getHeight() - getHeight()) / 2f);
            }
            toFront();
        } else {
            setVisible(false);
        }
        return isVisible();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH_INTERVAL) refresh();
    }

    /** Pulls the yard from the provider and rebuilds the rows. */
    public void refresh() {
        sinceRefresh = 0;
        yard.clear();
        if (provider != null) provider.collect(yard);
        if (!yard.isOpen()) {
            if (isVisible()) setVisible(false);
            return;
        }
        getTitleLabel().setText(title(yard));
        current.setText(yard.current);
        String now = key();
        if (!now.equals(shown)) {
            rebuild();
            if (isVisible()) pack();
            shown = now;
        }
        for (int i = 0; i < terms.size && i < yard.ships.size; i++) terms.get(i).setText(yard.ships.get(i).terms);
    }

    private void rebuild() {
        terms.clear();
        ships.clearChildren();
        if (!yard.stationName.isEmpty()) {
            ships.add(new Label("Ships for sale", skin, "dim")).left().colspan(4).row();
            if (yard.ships.size == 0) ships.add(new Label("None today", skin)).left().colspan(4).row();
            for (Ship s : yard.ships) {
                ships.add(new Label(s.name, skin)).left().padRight(14f);
                ships.add(new Label(s.seller, skin, "dim")).left().padRight(14f);
                Label t = new Label(s.terms, skin);
                terms.add(t);
                ships.add(t).left().padRight(14f);
                if (s.locked.isEmpty()) {
                    String id = s.id;
                    ships.add(button("Buy", () -> say(provider.buy(id)))).row();
                } else {
                    ships.add(new Label(s.locked, skin, "warn")).left().row();
                }
            }
        }
        prize.clearChildren();
        if (!yard.prize.isEmpty()) {
            prize.add(new Label("Prize in tow", skin, "dim")).left().colspan(3).row();
            prize.add(new Label(yard.prize, skin)).left().colspan(3).row();
            row(prize, "Sell it", yard.sellTerms, yard.sellLocked, () -> say(provider.sellPrize()));
            row(prize, "Turn it in", yard.turnInTerms, yard.turnInLocked, () -> say(provider.turnInPrize()));
        }
    }

    private void row(Table table, String action, String what, String locked, Runnable run) {
        table.add(new Label(what, skin)).left().padRight(14f);
        if (locked.isEmpty()) table.add(button(action, run)).left().row();
        else table.add(new Label(locked, skin, "warn")).left().row();
    }

    private String key() {
        StringBuilder s = new StringBuilder(yard.stationName).append('|');
        for (Ship ship : yard.ships) s.append(ship.id).append(ship.locked.isEmpty() ? ',' : '!').append(ship.locked).append(',');
        s.append('|').append(yard.prize).append(yard.sellLocked).append(yard.turnInLocked).append(yard.sellTerms).append(yard.turnInTerms);
        return s.toString();
    }

    private void say(String message) {
        refresh();
        status.setText(message == null ? "" : message);
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.pad(2f, 8f, 2f, 8f);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!b.isDisabled() && provider != null) action.run();
            }
        });
        return b;
    }

    public Yard getYard() {
        return yard;
    }

    /** "HIGHPORT SHIPYARD" while docked at one, else "PRIZE". */
    static String title(Yard yard) {
        return yard.stationName.isEmpty() ? "PRIZE" : yard.stationName.toUpperCase(Locale.ROOT) + " SHIPYARD";
    }
}
