package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;

import java.util.Locale;

/**
 * The mission board: while docked, the station's jobs with their pay, standing and time, and a
 * button to take each (or why it can't be taken yet); always, the contracts held, with a button to
 * give each up. Refreshes from the {@link MissionProvider} a few times a second.
 */
public class MissionWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.5f;

    private final Skin skin;
    private final MissionBoard board = new MissionBoard();
    private final Label career;
    private final Label status;
    private final Table offers = new Table();
    private final Table held = new Table();
    private MissionProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;
    /** Ids shown in each table, so rows are only rebuilt when the jobs change. */
    private String shown = null;
    private final Array<Label> offerTerms = new Array<>(), heldTitles = new Array<>(), heldTerms = new Array<>();

    public MissionWindow(Skin skin) {
        super("", skin);
        this.skin = skin;
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        career = new Label("", skin, "dim");
        status = new Label("", skin, "dim");
        add(career).left().row();
        add(offers).left().padTop(8f).row();
        add(held).left().padTop(12f).row();
        add(status).left().padTop(8f).row();
        add(button("Close [J]", () -> setOpen(false))).right().padTop(10f);
        setVisible(false);
    }

    public void setProvider(MissionProvider provider) {
        this.provider = provider;
        refresh();
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** True while there is a board to show or a contract held. */
    public boolean isAvailable() {
        return board.isOpen();
    }

    /** Opens the board if there is anything on it; closing always works. Returns whether the window is now open. */
    public boolean setOpen(boolean open) {
        if (open) {
            refresh();
            if (!board.isOpen()) return false;
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

    /** Pulls the board from the provider and rebuilds the rows. */
    public void refresh() {
        sinceRefresh = 0;
        board.clear();
        if (provider != null) provider.collect(board);
        if (!board.isOpen()) {
            if (isVisible()) setVisible(false);
            return;
        }
        getTitleLabel().setText(title(board));
        career.setText(board.career);
        String now = ids();
        if (!now.equals(shown)) {
            rebuild();
            if (isVisible()) pack();
            shown = now;
        }
        for (int i = 0; i < offerTerms.size && i < board.offers.size; i++) offerTerms.get(i).setText(board.offers.get(i).terms);
        for (int i = 0; i < heldTerms.size && i < board.held.size; i++) {
            heldTitles.get(i).setText(board.held.get(i).title);
            heldTerms.get(i).setText(board.held.get(i).terms);
        }
    }

    private void rebuild() {
        offerTerms.clear();
        heldTitles.clear();
        heldTerms.clear();
        offers.clearChildren();
        if (!board.stationName.isEmpty()) {
            offers.add(new Label("On offer", skin, "dim")).left().colspan(4).row();
            if (board.offers.size == 0) offers.add(new Label("Nothing today", skin)).left().colspan(4).row();
            for (Job j : board.offers) {
                offers.add(new Label(j.title, skin)).left().padRight(14f);
                offers.add(new Label(j.giver, skin, "dim")).left().padRight(14f);
                Label terms = new Label(j.terms, skin);
                offerTerms.add(terms);
                offers.add(terms).left().padRight(14f);
                if (j.locked.isEmpty()) {
                    long id = j.id;
                    offers.add(button("Take", () -> say(provider.accept(id)))).row();
                } else {
                    offers.add(new Label(j.locked, skin, "warn")).left().row();
                }
                if (!j.note.isEmpty()) offers.add(new Label("  " + j.note, skin, "dim")).left().colspan(4).padBottom(4f).row();
            }
        }
        held.clearChildren();
        held.add(new Label("Your contracts", skin, "dim")).left().colspan(3).row();
        if (board.held.size == 0) held.add(new Label("None", skin)).left().colspan(3).row();
        for (Job j : board.held) {
            Label title = new Label(j.title, skin), terms = new Label(j.terms, skin);
            heldTitles.add(title);
            heldTerms.add(terms);
            held.add(title).left().padRight(14f);
            held.add(terms).left().padRight(14f);
            long id = j.id;
            held.add(button("Abandon", () -> say(provider.abandon(id)))).row();
        }
    }

    private String ids() {
        StringBuilder s = new StringBuilder();
        for (Job j : board.offers) s.append(j.id).append(j.locked.isEmpty() ? ',' : '!');
        s.append('|');
        for (Job j : board.held) s.append(j.id).append(',');
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

    public MissionBoard getBoard() {
        return board;
    }

    /** "HIGHPORT MISSION BOARD" while docked, else "CONTRACTS". */
    static String title(MissionBoard board) {
        return board.stationName.isEmpty() ? "CONTRACTS" : board.stationName.toUpperCase(Locale.ROOT) + " MISSION BOARD";
    }
}
