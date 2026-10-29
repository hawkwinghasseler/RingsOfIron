package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.BoardingProvider.BoardingBoard;
import com.ringsofiron.uihud.providers.BoardingProvider.Cargo;
import com.ringsofiron.uihud.providers.BoardingProvider.Part;

import java.util.Locale;

/**
 * The ship the player's is latched to, to board and strip: how it stands, a button to send the
 * boarding party across (or call it back), and once it is taken, one row per system that can come
 * off it, with what it sells for and a button to mark it for stripping, then one row per cargo
 * aboard with a button to carry it all over. Refreshes from the {@link BoardingProvider} a few
 * times a second; closes itself when the ships part.
 */
public class BoardingWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.5f;

    private final Skin skin;
    private final BoardingBoard board = new BoardingBoard();
    private final Label state;
    private final TextButton boardButton, stormButton;
    private final Label status;
    private final Table rows = new Table();
    private BoardingProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;
    /** What the rows were last built for, to rebuild them only when it changes. */
    private String layout = "";

    public BoardingWindow(Skin skin) {
        super("", skin);
        this.skin = skin;
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        state = new Label("", skin);
        boardButton = button("", () -> {
            if (provider != null) say(board.boarding ? provider.recall() : provider.orderBoarding());
        });
        stormButton = button("Force a way in", () -> {
            if (provider != null) say(provider.storm());
        });
        status = new Label("", skin, "dim");
        Table top = new Table();
        top.add(state).left().padRight(14f);
        top.add(boardButton);
        top.add(stormButton).padLeft(6f);
        add(top).left().row();
        add(rows).left().padTop(8f).row();
        add(status).left().padTop(8f).row();
        add(button("Close [K]", () -> setOpen(false))).right().padTop(10f);
        setVisible(false);
    }

    public void setProvider(BoardingProvider provider) {
        this.provider = provider;
        refresh();
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** True while latched to another ship. */
    public boolean isAvailable() {
        return board.isOpen();
    }

    /** Opens the window if latched to another ship; closing always works. Returns whether it is now open. */
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

    /** Pulls the board from the provider and updates the window. */
    public void refresh() {
        sinceRefresh = 0;
        board.clear();
        if (provider != null) provider.collect(board);
        if (!board.isOpen()) {
            if (isVisible()) setVisible(false);
            return;
        }
        getTitleLabel().setText(board.craftName.toUpperCase(Locale.ROOT));
        state.setText(board.state);
        boardButton.setText(board.boarding ? "Recall boarders" : board.byEva ? "Send boarders on EVA" : "Send boarding party");
        boardButton.setVisible(!board.salvage);
        boardButton.setDisabled(!board.boarding && !board.boardRefusal.isEmpty());
        // Storming forces every door: for a ship still able to fight, or one whose doors are held
        stormButton.setVisible(!board.salvage && !board.boarding && board.stormRefusal.isEmpty());
        String now = layoutKey();
        if (!now.equals(layout)) rebuildRows();
        layout = now;
    }

    private String layoutKey() {
        StringBuilder sb = new StringBuilder(board.salvage ? "S" : "-").append(board.salvageRefusal);
        for (Part p : board.parts) sb.append('|').append(p.x).append(',').append(p.y).append(p.status).append(p.marked);
        for (Cargo c : board.cargo) sb.append('|').append(c.commodity).append(Math.round(c.units)).append(Math.round(c.onOrder));
        return sb.toString();
    }

    private void rebuildRows() {
        rows.clearChildren();
        if (!board.salvage) {
            rows.add(new Label(board.salvageRefusal.isEmpty() ? board.boardRefusal : board.salvageRefusal, skin, "dim")).left();
            if (isVisible()) pack();
            return;
        }
        rows.add(new Label("System", skin, "dim")).left().padRight(14f);
        rows.add(new Label("Sells for", skin, "dim")).right().padRight(14f);
        rows.add(new Label("", skin, "dim")).left().padRight(14f);
        rows.add().row();
        if (board.parts.size == 0) rows.add(new Label("Nothing left to strip", skin, "dim")).colspan(4).left().row();
        for (Part p : board.parts) {
            rows.add(new Label(p.name, skin)).left().padRight(14f);
            rows.add(new Label(formatCredits(p.value), skin)).right().padRight(14f);
            rows.add(new Label(p.status, skin, "dim")).left().padRight(14f);
            int x = p.x, y = p.y;
            TextButton b = button(p.marked ? "Leave it" : "Strip", () -> {
                if (provider != null) say(provider.toggleStrip(x, y));
            });
            b.setDisabled(p.marked && !p.status.startsWith("marked") && !p.status.startsWith("stripping"));
            rows.add(b).left().row();
        }
        if (board.cargo.size > 0) {
            rows.add(new Label("Cargo aboard", skin, "dim")).left().padTop(8f).colspan(4).row();
            for (Cargo c : board.cargo) {
                rows.add(new Label(c.commodity, skin)).left().padRight(14f);
                rows.add(new Label(formatUnits(c.units), skin)).right().padRight(14f);
                rows.add(new Label(c.onOrder > 0.05 ? formatUnits(c.onOrder) + " coming over" : "", skin, "dim")).left().padRight(14f);
                String id = c.commodity;
                TextButton b = button("Take all", () -> {
                    if (provider != null) say(provider.takeCargo(id));
                });
                b.setDisabled(c.onOrder > 0.05);
                rows.add(b).left().row();
            }
        }
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

    public BoardingBoard getBoard() {
        return board;
    }

    static String formatCredits(double credits) {
        return String.format(Locale.ROOT, "%,.0f cr", credits);
    }

    static String formatUnits(double units) {
        return String.format(Locale.ROOT, "%,.0f", units);
    }
}
