package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.YardProvider.Hardpoint;
import com.ringsofiron.uihud.providers.YardProvider.Offer;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * A station's yard while the ship is docked there: what is damaged and what putting it right costs,
 * with a button to order the repairs, then one row per weapon hardpoint with the weapon on it, a
 * weapon to fit instead (click to pick the next), and buttons to order it new or used, or fitted
 * from a salvaged one in the ship's hold. Rebuild
 * decks opens the ship editor for the yard to rebuild the ship to a new layout, and Ships for sale
 * opens the shipyard window where the station sells ships. Furnish picks a piece of furniture or
 * decor (click to pick the next) to buy and place on the deck, and Arrange moves what is aboard;
 * both close the window for the deck, where the piece is put down. Refreshes
 * from the {@link YardProvider} a few times a second; closes itself when the ship leaves the yard.
 *
 * <p>Away from a yard it is the crew's field refit ({@link YardBoard#field}): the hardpoints only,
 * each with a salvaged weapon in the hold to mount instead (click to pick the next), and Mount on
 * EVA to have an engineer take it out and mount it, or Call off while its crate is still inside.
 */
public class YardWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.5f;

    private final Skin skin;
    private final YardBoard board = new YardBoard();
    private final Label summary;
    private final Label repairs;
    private final TextButton repair;
    private final Label status;
    private final Label tanks;
    private final TextButton refill;
    private final TextButton ships;
    private Runnable openShipyard;
    private BooleanSupplier sellsShips = () -> false;
    private final TextButton furnishing, place, arrange;
    private final Label furnished;
    private int furnishingChoice;
    private final Table rows = new Table();
    private final Array<Row> rowWidgets = new Array<>();
    /** The window's body, laid out for a yard or for the crew's field refit, and its parts. */
    private final Table content = new Table();
    private final Table top = new Table(), fuel = new Table(), furnish = new Table(), bottom = new Table(),
            fieldBottom = new Table();
    private final Label fieldNote;
    /** Which the body and rows are laid out for: a field refit, or a yard. */
    private boolean shownField, rowsField;
    private YardProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;

    private final class Row {
        final int index;
        final boolean field;
        final Label weapon, progress;
        final TextButton choice, fitNew, fitUsed, fitSalvaged;
        int offer;
        /** A field refit there that can be called off: the salvaged button calls it off. */
        boolean callOff;

        Row(Hardpoint h, boolean field) {
            index = h.index;
            this.field = field;
            weapon = new Label("", skin);
            progress = new Label("", skin, "dim");
            choice = button("", () -> offer = board.offers.size == 0 ? 0 : (offer + 1) % board.offers.size);
            fitNew = button("", () -> order(false));
            fitUsed = button("", () -> order(true));
            fitSalvaged = button("", () -> {
                if (provider == null) return;
                if (field && callOff) {
                    say(provider.callOffFieldRefit(index));
                    return;
                }
                if (board.offers.size == 0) return;
                Offer o = board.offers.get(Math.min(offer, board.offers.size - 1));
                say(provider.orderSalvagedRefit(index, o.weaponId));
            });
            rows.add(new Label(capitalized(h.name), skin)).left().padRight(14f);
            rows.add(weapon).left().padRight(14f);
            rows.add(choice).padRight(6f);
            if (!field) {
                rows.add(fitNew).padRight(3f);
                rows.add(fitUsed).padRight(3f);
            }
            rows.add(fitSalvaged).padRight(10f);
            rows.add(progress).left().row();
        }

        void update(Hardpoint h) {
            weapon.setText(h.weapon);
            progress.setText(h.progress);
            Offer o = board.offers.size == 0 ? null : board.offers.get(Math.min(offer, board.offers.size - 1));
            choice.setText(o == null ? "-" : o.name + " >");
            if (field) {
                callOff = h.callOff;
                choice.setDisabled(o == null || h.mounting);
                if (h.mounting) {
                    fitSalvaged.setText(h.callOff ? "Call off" : "Mounting");
                    fitSalvaged.setDisabled(!h.callOff);
                } else {
                    fitSalvaged.setText(o == null ? "Mount on EVA" : "Mount on EVA (" + o.salvaged + ")");
                    fitSalvaged.setDisabled(o == null || o.salvaged == 0);
                }
                return;
            }
            fitNew.setText(o == null ? "New" : "New " + formatCredits(o.price));
            fitUsed.setText(o == null ? "Used" : "Used " + formatCredits(o.price * board.usedPrice));
            fitNew.setDisabled(o == null || board.credits < o.price);
            fitUsed.setDisabled(o == null || board.credits < o.price * board.usedPrice);
            fitSalvaged.setText(o == null || o.salvaged == 0 ? "Salvaged" : "Salvaged (" + o.salvaged + ")");
            fitSalvaged.setDisabled(o == null || o.salvaged == 0);
        }

        private void order(boolean used) {
            if (provider == null || board.offers.size == 0) return;
            Offer o = board.offers.get(Math.min(offer, board.offers.size - 1));
            say(provider.orderRefit(index, o.weaponId, used));
        }
    }

    public YardWindow(Skin skin) {
        super("", skin);
        this.skin = skin;
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        summary = new Label("", skin);
        repairs = new Label("", skin, "dim");
        repair = button("Repair all", () -> {
            if (provider != null) say(provider.orderRepairs());
        });
        status = new Label("", skin, "dim");
        tanks = new Label("", skin);
        refill = button("Refill tanks", () -> {
            if (provider != null) say(provider.orderRefill());
        });
        top.add(summary).left().padRight(14f);
        top.add(repair);
        fuel.add(tanks).left().padRight(14f);
        fuel.add(refill);
        fieldNote = new Label("", skin, "dim");
        furnishing = button("", () -> furnishingChoice = board.furnishings.size == 0 ? 0 : (furnishingChoice + 1) % board.furnishings.size);
        place = button("", () -> {
            if (provider == null || board.furnishings.size == 0) return;
            String said = provider.placeFurnishing(board.furnishings.get(Math.min(furnishingChoice, board.furnishings.size - 1)).id);
            if (said == null) setOpen(false);
            else say(said);
        });
        arrange = button("Arrange aboard...", () -> {
            if (provider == null) return;
            String said = provider.arrangeFurnishings();
            if (said == null) setOpen(false);
            else say(said);
        });
        furnished = new Label("", skin, "dim");
        furnish.add(new Label("Furnish", skin)).left().padRight(14f);
        furnish.add(furnishing).padRight(6f);
        furnish.add(place).padRight(10f);
        furnish.add(arrange).padRight(10f);
        furnish.add(furnished).left();
        bottom.add(button("Rebuild decks...", () -> {
            if (provider == null) return;
            String said = provider.openLayoutEditor();
            if (said == null) setOpen(false);
            else say(said);
        })).padRight(10f);
        ships = button("Ships for sale...", () -> {
            if (openShipyard != null) openShipyard.run();
        });
        bottom.add(ships).padRight(10f);
        bottom.add(button("Close [Y]", () -> setOpen(false)));
        fieldBottom.add(button("Close [Y]", () -> setOpen(false)));
        add(content);
        layoutFor(false);
        setVisible(false);
    }

    /** Lays the body out for the crew's field refit, or for a station's yard. */
    private void layoutFor(boolean field) {
        shownField = field;
        content.clearChildren();
        if (field) {
            content.add(fieldNote).left().row();
            content.add(rows).left().padTop(8f).row();
            content.add(status).left().padTop(8f).row();
            content.add(fieldBottom).right().padTop(10f);
            return;
        }
        content.add(top).left().row();
        content.add(repairs).left().padTop(4f).row();
        content.add(fuel).left().padTop(4f).row();
        content.add(rows).left().padTop(8f).row();
        content.add(furnish).left().padTop(8f).row();
        content.add(status).left().padTop(8f).row();
        content.add(bottom).right().padTop(10f);
    }

    public void setProvider(YardProvider provider) {
        this.provider = provider;
        refresh();
    }

    /** The Ships for sale button: {@code open} runs on a click, enabled while {@code available} is true. */
    public void setShipyard(Runnable open, BooleanSupplier available) {
        this.openShipyard = open;
        this.sellsShips = available;
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** True while a yard can work on the ship, or the crew can refit it themselves ({@link #isField}). */
    public boolean isAvailable() {
        return board.isOpen();
    }

    /** True while there is no yard, only the crew's own field refit. */
    public boolean isField() {
        return board.field;
    }

    /** Opens the yard if docked at one; closing always works. Returns whether the window is now open. */
    public boolean setOpen(boolean open) {
        if (open) {
            refresh();
            if (!board.isOpen()) return false;
            status.setText("");
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

    /** Pulls the board from the provider and updates the summary and every row. */
    public void refresh() {
        sinceRefresh = 0;
        board.clear();
        if (provider != null) provider.collect(board);
        if (!board.isOpen()) {
            if (isVisible()) setVisible(false);
            return;
        }
        if (board.field != shownField) {
            layoutFor(board.field);
            if (isVisible()) pack();
        }
        if (rowWidgets.size != board.hardpoints.size || rowsField != board.field) rebuildRows();
        if (board.field) {
            getTitleLabel().setText("FIELD REFIT");
            fieldNote.setText(board.offers.size == 0 ? "No yard here, and no salvaged weapons in the hold for the crew to mount"
                    : "No yard here: an engineer can mount a salvaged weapon from the hold on EVA");
            for (int i = 0; i < rowWidgets.size; i++) rowWidgets.get(i).update(board.hardpoints.get(i));
            return;
        }
        getTitleLabel().setText(board.stationName.toUpperCase(Locale.ROOT) + " YARD");
        summary.setText(String.format(Locale.ROOT, "Credits %s     %s", formatCredits(board.credits), damageLine(board)));
        repair.setDisabled(board.damaged == 0 || !board.repairs.isEmpty());
        repairs.setText(board.repairs);
        tanks.setText(board.tanks.isEmpty() ? "No tanks aboard" : board.tanks);
        refill.setText(board.refillCost < 0.5 ? "Tanks full" : "Refill tanks " + formatCredits(board.refillCost));
        refill.setDisabled(board.refillCost < 0.5 || board.credits < 1);
        ships.setDisabled(!sellsShips.getAsBoolean());
        YardProvider.Furnishing f = board.furnishings.size == 0 ? null
                : board.furnishings.get(Math.min(furnishingChoice, board.furnishings.size - 1));
        furnishing.setText(f == null ? "-" : f.name + (f.use.isEmpty() ? "" : " (" + f.use + ")") + " >");
        furnishing.setDisabled(f == null);
        place.setText(f == null ? "Buy and place" : "Buy and place " + formatCredits(f.price));
        place.setDisabled(f == null || board.credits < f.price);
        furnished.setText(board.furnished);
        for (int i = 0; i < rowWidgets.size; i++) rowWidgets.get(i).update(board.hardpoints.get(i));
    }

    private void rebuildRows() {
        rowsField = board.field;
        rows.clearChildren();
        rowWidgets.clear();
        rows.add(new Label("Hardpoint", skin, "dim")).left().padRight(14f);
        rows.add(new Label("Fitted", skin, "dim")).left().padRight(14f);
        rows.add(new Label(board.field ? "Mount instead" : "Fit instead", skin, "dim")).colspan(board.field ? 2 : 4).left().row();
        for (Hardpoint h : board.hardpoints) rowWidgets.add(new Row(h, board.field));
        if (isVisible()) pack();
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
                if (!b.isDisabled()) action.run();
            }
        });
        return b;
    }

    public YardBoard getBoard() {
        return board;
    }

    /** "Nothing needs repairing", or how much does and what it costs. */
    static String damageLine(YardBoard board) {
        if (board.damaged == 0) return "Nothing needs repairing";
        return String.format(Locale.ROOT, "%d damaged, %s to repair", board.damaged, formatCredits(board.repairCost));
    }

    static String capitalized(String s) {
        if (s.startsWith("the ")) s = s.substring(4);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String formatCredits(double credits) {
        return String.format(Locale.ROOT, "%,.0f cr", credits);
    }
}
