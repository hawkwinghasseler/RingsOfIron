package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Hire;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;

import java.util.Locale;

/**
 * The crew roster: everyone aboard with their skills, condition, morale, wage and record, and a
 * button to let each go while docked; while docked at a station that hires, who is looking for a
 * berth, with a button to hire each; and the prisoners held aboard, with what can be done with
 * them, and how each is; and whether the crew suit up at battle stations, with a button to
 * change it. Refreshes from the {@link CrewProvider} a few times a second.
 */
public class CrewWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.5f;
    /** Space between the window's edge and its rows, left and right. */
    static final float SIDE_PAD = 20f;

    private final Skin skin;
    private final CrewSheet sheet = new CrewSheet();
    private final Label summary;
    private final Label status;
    private final Table crew = new Table();
    private final Table hires = new Table();
    private final Table prisoners = new Table();
    private CrewProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;
    /** What the rows were built from, so they are only rebuilt when it changes. */
    private String shown = null;
    /** Labels updated in place: each crew member's condition, morale and record. */
    private final Array<Label> conditions = new Array<>(), morales = new Array<>(), histories = new Array<>(),
            skills = new Array<>(), prisonerConditions = new Array<>();
    /** Suits at battle stations, and the button that turns them on or off. */
    private final Label suits;

    public CrewWindow(Skin skin) {
        super("", skin);
        this.skin = skin;
        setModal(false);
        setMovable(true);
        pad(28f, SIDE_PAD, 12f, SIDE_PAD);
        getTitleTable().padLeft(8f);
        summary = new Label("", skin, "dim");
        status = new Label("", skin, "dim");
        suits = new Label("", skin);
        add(summary).left().row();
        Table suitRow = new Table();
        suitRow.add(suits).left().padRight(12f);
        suitRow.add(button("Change", () -> say(provider.toggleSuits())));
        add(suitRow).left().padTop(4f).row();
        add(crew).left().padTop(8f).row();
        add(prisoners).left().padTop(12f).row();
        add(hires).left().padTop(12f).row();
        add(status).left().padTop(8f).row();
        add(button("Close [I]", () -> setOpen(false))).right().padTop(10f);
        setVisible(false);
    }

    public void setProvider(CrewProvider provider) {
        this.provider = provider;
        refresh();
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** True while the player has a ship. */
    public boolean isAvailable() {
        return sheet.isOpen();
    }

    /** Opens the roster if there is a ship; closing always works. Returns whether the window is now open. */
    public boolean setOpen(boolean open) {
        if (open) {
            refresh();
            if (!sheet.isOpen()) return false;
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

    /** Pulls the sheet from the provider and rebuilds the rows if who is listed has changed. */
    public void refresh() {
        sinceRefresh = 0;
        sheet.clear();
        if (provider != null) provider.collect(sheet);
        if (!sheet.isOpen()) {
            if (isVisible()) setVisible(false);
            return;
        }
        getTitleLabel().setText(title(sheet));
        summary.setText(sheet.summary);
        suits.setText(sheet.suits);
        String now = signature();
        if (!now.equals(shown)) {
            rebuild();
            if (isVisible()) pack();
            shown = now;
        }
        for (int i = 0; i < conditions.size && i < sheet.crew.size; i++) {
            Member m = sheet.crew.get(i);
            conditions.get(i).setText(m.condition);
            morales.get(i).setText(m.morale);
            histories.get(i).setText(m.history);
            skills.get(i).setText(m.skills);
        }
        for (int i = 0; i < prisonerConditions.size && i < sheet.prisoners.size; i++) {
            prisonerConditions.get(i).setText(sheet.prisoners.get(i).condition);
        }
        // A condition that grows ("OK 100%" to "Critical wound, 4:39 to live") widens its row: grow
        // the window about its centre, or the rows run out past its left edge (round 9, real window)
        if (isVisible() && (getPrefWidth() > getWidth() + 0.5f || getPrefHeight() > getHeight() + 0.5f)) {
            float cx = getX() + getWidth() / 2f, cy = getY() + getHeight() / 2f;
            pack();
            setPosition(cx - getWidth() / 2f, cy - getHeight() / 2f);
        }
    }

    private void rebuild() {
        conditions.clear();
        morales.clear();
        histories.clear();
        skills.clear();
        crew.clearChildren();
        // Name, role and skills take the same widths for crew and hires, so the two lists line up
        float[] cols = new float[3];
        for (Member m : sheet.crew) widen(cols, m.name, m.role, m.skills);
        if (!sheet.stationName.isEmpty()) for (Hire h : sheet.hires) widen(cols, h.name, h.role, h.skills);
        crew.add(new Label("Aboard", skin, "dim")).left().colspan(7).row();
        if (sheet.crew.size == 0) crew.add(new Label("No crew but you", skin)).left().colspan(7).row();
        for (Member m : sheet.crew) {
            crew.add(new Label(m.name, skin)).left().width(cols[0]).padRight(12f);
            crew.add(new Label(m.role, skin, "dim")).left().width(cols[1]).padRight(12f);
            Label skill = new Label(m.skills, skin), cond = new Label(m.condition, skin), morale = new Label(m.morale, skin);
            skills.add(skill);
            conditions.add(cond);
            morales.add(morale);
            crew.add(skill).left().width(cols[2]).padRight(12f);
            crew.add(cond).left().padRight(12f);
            crew.add(morale).left().padRight(12f);
            crew.add(new Label(m.wage, skin)).left().padRight(12f);
            if (m.dismissable) {
                long id = m.id;
                crew.add(button("Let go", () -> say(provider.dismiss(id)))).row();
            } else {
                crew.add().row();
            }
            Label history = new Label(m.history, skin, "dim");
            histories.add(history);
            crew.add(history).left().colspan(7).padLeft(16f).padBottom(4f).row();
        }

        prisoners.clearChildren();
        prisonerConditions.clear();
        if (sheet.prisoners.size > 0 || sheet.prisonersAlongside) {
            prisoners.add(new Label("Prisoners", skin, "dim")).left().colspan(5).row();
            for (Prisoner p : sheet.prisoners) {
                prisoners.add(new Label(p.name, skin)).left().padRight(12f);
                prisoners.add(new Label(p.detail, skin, "dim")).left().padRight(12f);
                Label cond = new Label(p.condition, skin);
                prisonerConditions.add(cond);
                prisoners.add(cond).left().padRight(12f);
                long id = p.id;
                if (!p.option.isEmpty()) prisoners.add(button(p.option, () -> say(provider.settlePrisoner(id)))).padRight(6f);
                else prisoners.add();
                if (p.offersToJoin) prisoners.add(button("Recruit", () -> say(provider.recruit(id))));
                else prisoners.add();
                prisoners.row();
            }
            if (sheet.prisonersAlongside) {
                prisoners.add(button("Bring prisoners aboard", () -> say(provider.takePrisoners()))).left().colspan(5).row();
            }
        }

        hires.clearChildren();
        if (!sheet.stationName.isEmpty()) {
            hires.add(new Label("Looking for a berth", skin, "dim")).left().colspan(5).row();
            if (sheet.hires.size == 0) hires.add(new Label("No one today", skin)).left().colspan(5).row();
            for (Hire h : sheet.hires) {
                hires.add(new Label(h.name, skin)).left().width(cols[0]).padRight(12f);
                hires.add(new Label(h.role, skin, "dim")).left().width(cols[1]).padRight(12f);
                hires.add(new Label(h.skills, skin)).left().width(cols[2]).padRight(12f);
                hires.add(new Label(h.terms, skin)).left().padRight(12f);
                String key = h.key;
                hires.add(button("Hire", () -> say(provider.hire(key)))).row();
            }
        }
    }

    /** Widens each of {@code cols} to fit the matching text. */
    private void widen(float[] cols, String... texts) {
        for (int i = 0; i < cols.length; i++) cols[i] = Math.max(cols[i], new Label(texts[i], skin).getPrefWidth());
    }

    private String signature() {
        StringBuilder s = new StringBuilder(sheet.stationName).append('|');
        for (Member m : sheet.crew) s.append(m.id).append(m.dismissable ? '+' : '-').append(m.wage).append(m.skills).append(',');
        s.append('|');
        for (Prisoner p : sheet.prisoners) s.append(p.id).append(p.option).append(p.offersToJoin).append(',');
        s.append(sheet.prisonersAlongside).append('|');
        for (Hire h : sheet.hires) s.append(h.key).append(',');
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

    public CrewSheet getSheet() {
        return sheet;
    }

    /** "CREW OF THE CORVETTE". */
    static String title(CrewSheet sheet) {
        return "CREW OF THE " + sheet.shipName.toUpperCase(Locale.ROOT);
    }
}
