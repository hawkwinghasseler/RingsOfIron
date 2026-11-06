package com.ringsofiron.uihud.variants.cassette;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.variants.ShellScreen;

import java.util.Locale;

/**
 * Dresses each of the game's menus once it is built ({@code ShellLook#dress}), after the Yard
 * Standard's shell (documentation/ui-overhaul/cassette/shell): the builder's plate over the main
 * menu, the newest save in its tape deck, the pause menu in the ship's hatch, the saves as cassettes,
 * the settings in bays, the mod scripts question as a caution tag, and the keys each takes engraved at
 * its foot.
 *
 * <p>It moves and restyles what the game built and never changes what a control holds or does, with
 * one addition from the style guide (9.7): an order that can't be undone (deleting a save, running mod
 * scripts) sits under a red guard, so the first press lifts the guard and the second gives the order.
 * What it does not find where it looks is left as the game built it.
 */
final class YardShellDress {
    /** How long a lifted guard stays up for the second press, seconds. */
    static final float GUARD_UP = 4f;
    /** The width of the pause menu's buttons, as the mockup has them. */
    private static final float BUTTON_W = 300f;

    private final Skin skin;
    private final YardPaint paint;

    YardShellDress(Skin skin, YardPaint paint) {
        this.skin = skin;
        this.paint = paint;
    }

    void dress(ShellScreen screen, Table panel, Table root) {
        switch (screen) {
            case MAIN: main(panel, root); break;
            case NEW_GAME: newGame(panel); break;
            case PAUSE: pause(panel, root); break;
            case LOAD: load(panel); break;
            case SAVE: save(panel); break;
            case SETTINGS: settings(panel); break;
            case MOD_SCRIPTS: modScripts(panel); break;
            default: break;
        }
        if (screen != ShellScreen.MOD_SCRIPTS) capsOnFaces(panel);
    }

    // --- The main menu: the builder's plate, the buttons and the tape deck ---------------------

    private void main(Table panel, Table root) {
        Label heading = null, sub = null, latest = null, message = null, version = null;
        Array<TextButton> buttons = new Array<>();
        Array<Cell<?>> cells = cells(panel);
        for (int i = 0; i < cells.size; i++) {
            Actor a = cells.get(i).getActor();
            if (a instanceof TextButton) {
                buttons.add((TextButton) a);
            } else if (a instanceof Label) {
                Label l = (Label) a;
                if (is(l, "display")) heading = l;
                else if (is(l, "warn")) message = l;
                else if (is(l, "dim") && buttons.size == 0) sub = l;
                else if (is(l, "dim") && buttons.size == 1) latest = l;
            }
        }
        if (root != null) {
            for (int i = 0; i < root.getChildren().size; i++) {
                Actor a = root.getChildren().get(i);
                if (a instanceof Label && a != panel) version = (Label) a;
            }
        }
        if (heading == null || buttons.size < 2) return;

        panel.clearChildren();
        panel.top();
        Table plate = new Table();
        plate.setBackground(skin.getDrawable("plate"));
        heading.setStyle(style("plate-title"));
        plate.add(heading).row();
        if (sub != null) {
            sub.setStyle(style("plate-sub"));
            plate.add(sub).padTop(-2f);
        }
        panel.add(plate).colspan(2).width(500f).padBottom(28f).row();

        // The choices down the left, Quit set apart below a rule
        Table left = new Table();
        TextButton quit = buttons.peek();
        for (int i = 0; i < buttons.size; i++) {
            TextButton b = buttons.get(i);
            if (b == quit) {
                left.add(image("rule")).fillX().height(12f).pad(6f, 0f, 6f, 0f).row();
            }
            left.add(b).width(254f).pad(5f, 0f, 5f, 0f).row();
        }
        // The newest save in its deck, so the player sees what Continue loads; an empty deck with none
        Table right = new Table();
        Table deck = new Table();
        deck.top().left();
        if (latest != null) {
            deck.setBackground(skin.getDrawable("deck"));
            latest.setStyle(style("typed"));
            latest.setWrap(true);
            latest.setAlignment(Align.topLeft);
            deck.add(latest).width(190f).expand().fill();
        } else {
            deck.setBackground(skin.getDrawable("deck-empty"));
            deck.center();
            deck.add(new Label("No saves yet", skin, "flaps")).padBottom(20f);
        }
        right.add(deck).width(262f).height(170f).row();
        right.add(image("grille")).width(262f).height(176f).padTop(18f);
        panel.add(left).top().padRight(24f);
        panel.add(right).top().row();

        Table foot = new Table();
        foot.add(new Legend(paint).keys("UP", "DOWN").words("choose").gap().keys("ENTER").words("presses")).left().expandX();
        if (version != null) {
            version.remove();
            version.setStyle(style("serial"));
            foot.add(version).right().bottom();
        }
        panel.add(foot).colspan(2).fillX().padTop(24f).row();
        if (message != null) panel.add(message).colspan(2).padTop(4f).row();

        if (root != null) {
            // The faceplate is the desk console's top: it stands under the window and runs off the foot
            root.clearChildren();
            root.add(panel).expand().top().fillY().padTop(Value.percentHeight(0.12f, root));
        }
    }

    // --- New game: the seed in its holder, the sponsor on keys, the notes typed on cards -----

    private void newGame(Table panel) {
        Array<Label> labels = all(panel, Label.class);
        for (int i = 0; i < labels.size; i++) {
            Label l = labels.get(i);
            if (!is(l, "dim")) continue;
            if (l.getWrap()) {
                l.setStyle(style("card"));
                l.setAlignment(Align.left);
            } else {
                // An engraved name, clear of the pointer tab at the control beside it
                l.setStyle(style("legend"));
                Cell<?> c = l.getParent() instanceof Table ? ((Table) l.getParent()).getCell(l) : null;
                if (c != null) c.padRight(Math.max(22f, c.getPadRight()));
            }
        }
        TextButton start = first(all(panel, TextButton.class), "start");
        if (start != null) start.setStyle(skin.get("menu-go", TextButton.TextButtonStyle.class));
        legendAtFoot(panel, new Legend(paint).keys("UP", "DOWN").words("choose").gap().keys("ENTER").words("presses")
                .gap().keys("ESC").words("back"));
    }

    // --- Pause: the ship's console opens a maintenance hatch -------------------------------------

    private void pause(Table panel, Table root) {
        Array<Cell<?>> cells = cells(panel);
        Label help = null, world = null, heading = null, message = null;
        Array<TextButton> buttons = new Array<>();
        for (int i = 0; i < cells.size; i++) {
            Actor a = cells.get(i).getActor();
            if (a instanceof TextButton) {
                buttons.add((TextButton) a);
            } else if (a instanceof Label) {
                Label l = (Label) a;
                if (is(l, "heading")) heading = l;
                else if (is(l, "warn")) message = l;
                else if (is(l, "dim") && buttons.size > 0 && help == null) help = l;
                else if (is(l, "dim") && buttons.size > 0) world = l;
            }
        }
        TextButton resume = first(buttons, "resume"), tow = first(buttons, "call a tow");
        TextButton toMenu = first(buttons, "quit to menu"), quitGame = first(buttons, "quit game");
        if (heading == null || resume == null) return;

        // Rebuilt top to bottom as the mockup has it: the lens, Resume large, the others, the beacon, then the quits
        panel.clearChildren();
        panel.setBackground(skin.getDrawable("hatch-panel"));
        panel.pad(26f, 36f, 26f, 36f);
        panel.defaults().pad(0f);
        panel.top().left();
        heading.setStyle(style("lens"));
        heading.setAlignment(Align.center);
        panel.add(heading).left().width(150f).height(28f).padBottom(26f).row();
        Table first = new Table();
        resume.setStyle(skin.get("menu-go-tall", TextButton.TextButtonStyle.class));
        first.add(resume).width(BUTTON_W);
        // Esc resumes: its key beside the button
        first.add(new Legend(paint).keys("ESC")).padLeft(13f);
        panel.add(first).left().padBottom(14f).row();
        for (int i = 0; i < buttons.size; i++) {
            TextButton b = buttons.get(i);
            if (b == resume || b == tow || b == toMenu || b == quitGame) continue;
            panel.add(b).left().width(BUTTON_W).padBottom(14f).row();
        }
        if (tow != null) {
            // The distress beacon under its own engraved name
            tow.setStyle(skin.get("menu-amber", TextButton.TextButtonStyle.class));
            Table name = new Table();
            name.add(new Label("Beacon", skin, "legend")).left().padRight(8f);
            name.add(image("rule")).expandX().fillX().height(9f);
            panel.add(name).left().width(BUTTON_W).padTop(4f).padBottom(4f).row();
            panel.add(tow).left().width(BUTTON_W).padBottom(14f).row();
        }
        if (toMenu != null || quitGame != null) {
            // The quits stand apart, side by side behind a painted red line
            // The line paints 8 past each end of its image: 10 past the buttons each side
            panel.add(image("red-line")).left().width(BUTTON_W + 4f).height(12f).padLeft(-2f).padTop(10f).padBottom(14f).row();
            Table quits = new Table();
            float half = toMenu != null && quitGame != null ? (BUTTON_W - 20f) / 2f : BUTTON_W;
            if (toMenu != null) quits.add(toMenu).width(half);
            if (quitGame != null) quits.add(quitGame).width(half).padLeft(toMenu != null ? 20f : 0f);
            panel.add(quits).left().padBottom(18f).row();
        }
        if (help != null) {
            panel.add(new Legend(paint).keys("UP", "DOWN").words("or the pad choose").gap().keys("ESC").words("resumes"))
                    .left().padBottom(22f).row();
        }
        if (world != null) {
            world.setStyle(style("dymo"));
            panel.add(world).left().row();
        }
        if (message != null) panel.add(message).left().padTop(8f).row();
        if (root != null) {
            // The faceplate swings out on its hinge, in the hatch's dark frame
            root.clearChildren();
            Table hatch = new Table();
            hatch.setBackground(skin.getDrawable("hatch"));
            hatch.add(panel);
            Table wrap = new Table();
            wrap.add(image("hinge")).width(104f).fillY();
            wrap.add(hatch);
            root.add(wrap).padRight(104f);
        }
    }

    // --- The tape library and the blank cassette ---------------------------------------------------

    private void load(Table panel) {
        Array<Cell<?>> cells = cells(panel);
        for (int i = 0; i < cells.size; i++) {
            Actor a = cells.get(i).getActor();
            if (a instanceof Label && is((Label) a, "heading")) {
                int saves = 0;
                ScrollPane pane = firstOf(panel, ScrollPane.class);
                if (pane != null && pane.getActor() instanceof Table) {
                    Table list = (Table) pane.getActor();
                    for (int j = 0; j < list.getChildren().size; j++) if (isSave(list.getChildren().get(j))) saves++;
                }
                if (saves > 0) {
                    Table head = new Table();
                    cells.get(i).setActor(head);
                    head.add(a).left().expandX();
                    head.add(new Label(Integer.toString(saves), skin, "drum")).right();
                    head.add(new Label(saves == 1 ? "save" : "saves", skin, "legend")).padLeft(6f);
                    cells.get(i).fillX();
                }
                break;
            }
        }
        ScrollPane pane = firstOf(panel, ScrollPane.class);
        if (pane != null && pane.getActor() instanceof Table) {
            Table list = (Table) pane.getActor();
            for (int j = 0; j < list.getChildren().size; j++) {
                Actor a = list.getChildren().get(j);
                if (isSave(a)) {
                    // A save: its cassette's label, typed, with the date it was saved in a rubber stamp
                    Table info = (Table) a;
                    info.setBackground(skin.getDrawable("cassette"));
                    Array<Label> ls = all(info, Label.class);
                    for (int k = 0; k < ls.size; k++) {
                        Label l = ls.get(k);
                        if (k == 0) l.setStyle(style("typed"));
                        else if (k == ls.size - 1 && ls.size > 2) l.setStyle(style("stamp"));
                        else l.setStyle(style("typed-dim"));
                    }
                } else if (a instanceof TextButton) {
                    TextButton b = (TextButton) a;
                    if (named(b, "load")) {
                        b.setStyle(skin.get("menu-go-small", TextButton.TextButtonStyle.class));
                        b.pad(3f, 16f, 10f, 16f);
                        list.getCell(b).minWidth(90f);
                    } else if (named(b, "delete")) {
                        guard(b);
                        b.pad(7f, 18f, 12f, 18f);
                        // Room for the guard's lamp, which sits to its left
                        list.getCell(b).padLeft(28f);
                    }
                } else if (a instanceof Label && is((Label) a, "dim")) {
                    ((Label) a).setStyle(style("flaps"));
                }
            }
        }
        legendAtFoot(panel, new Legend(paint).keys("UP", "DOWN").words("choose").gap().keys("ENTER").words("presses")
                .gap().keys("ESC").words("back"));
    }

    /** A save's row in the load list: its details' table, not one of its buttons (a button is a table too). */
    private static boolean isSave(Actor a) {
        return a instanceof Table && !(a instanceof Button);
    }

    private void save(Table panel) {
        Array<Label> labels = all(panel, Label.class);
        for (int i = 0; i < labels.size; i++) if (is(labels.get(i), "dim")) labels.get(i).setStyle(style("legend"));
        ScrollPane pane = firstOf(panel, ScrollPane.class);
        if (pane != null && pane.getActor() instanceof Group) {
            Array<TextButton> picks = all((Group) pane.getActor(), TextButton.class);
            for (int i = 0; i < picks.size; i++) {
                TextButton b = picks.get(i);
                b.setStyle(skin.get("spine", TextButton.TextButtonStyle.class));
                b.getLabel().setAlignment(Align.left);
            }
        }
        TextButton save = first(all(panel, TextButton.class), "save");
        if (save != null) save.setStyle(skin.get("menu-go", TextButton.TextButtonStyle.class));
        legendAtFoot(panel, new Legend(paint).keys("UP", "DOWN").words("choose").gap().keys("ENTER").words("presses")
                .gap().keys("ESC").words("back"));
    }

    // --- Settings: the maintenance panel, a bay for each group ---------------------------------------

    private void settings(Table panel) {
        Array<Cell<?>> cells = cells(panel);
        // The rows as the game laid them, in order, split at each group's name
        Array<Placed> head = new Array<>(), tail = new Array<>();
        Array<Array<Placed>> groups = new Array<>();
        Array<Label> names = new Array<>();
        for (int i = 0; i < cells.size; i++) {
            Cell<?> c = cells.get(i);
            Actor a = c.getActor();
            if (a instanceof Label && is((Label) a, "title")) {
                names.add((Label) a);
                groups.add(new Array<>());
                continue;
            }
            Placed p = new Placed(c);
            boolean last = a instanceof Table && !(a instanceof Button) && containsButtons((Table) a)
                    || a instanceof Label && is((Label) a, "warn");
            if (last) tail.add(p);
            else if (groups.size == 0) head.add(p);
            else groups.peek().add(p);
        }
        if (groups.size < 2) return;
        panel.clearChildren();
        for (int i = 0; i < head.size; i++) head.get(i).into(panel);
        // Two columns of bays: the first half of the groups down the left, the rest down the right
        Table left = new Table(), right = new Table();
        int split = (groups.size + 1) / 2;
        for (int g = 0; g < groups.size; g++) {
            Table bay = new Table();
            bay.setBackground(skin.getDrawable("bay"));
            bay.top().left();
            bay.defaults().pad(3f);
            names.get(g).setStyle(style("title"));
            bay.add(names.get(g)).left().colspan(3).padBottom(6f).row();
            Array<Placed> rows = groups.get(g);
            for (int r = 0; r < rows.size; r++) rows.get(r).into(bay);
            (g < split ? left : right).add(bay).fillX().expandX().top().padBottom(12f).row();
        }
        panel.add(left).top().fillX().padRight(12f);
        panel.add(right).top().fillX().row();
        for (int i = 0; i < tail.size; i++) {
            Placed p = tail.get(i);
            p.colspan = 2;
            p.into(panel);
        }

        // Inside the bays: engraved names, exact figures on drums, the keys on a patch panel
        Array<Label> labels = all(panel, Label.class);
        for (int i = 0; i < labels.size; i++) {
            Label l = labels.get(i);
            if (is(l, "default") && l.getText().toString().endsWith("%")) l.setStyle(style("drum"));
            else if (is(l, "dim")) l.setStyle(style(inKeys(l) ? "dymo" : "legend"));
        }
        ScrollPane keys = firstOf(panel, ScrollPane.class);
        if (keys != null && keys.getActor() instanceof Group) {
            Array<TextButton> caps = all((Group) keys.getActor(), TextButton.class);
            for (int i = 0; i < caps.size; i++) caps.get(i).setStyle(skin.get("keycap", TextButton.TextButtonStyle.class));
        }
        TextButton apply = first(all(panel, TextButton.class), "apply");
        if (apply != null) apply.setStyle(skin.get("menu-go", TextButton.TextButtonStyle.class));
    }

    /** Whether {@code l} sits in the key bindings' list. */
    private static boolean inKeys(Label l) {
        for (Group g = l.getParent(); g != null; g = g.getParent()) if (g instanceof ScrollPane) return true;
        return false;
    }

    private static boolean containsButtons(Table t) {
        for (int i = 0; i < t.getChildren().size; i++) if (t.getChildren().get(i) instanceof TextButton) return true;
        return false;
    }

    // --- The mod scripts question: a caution tag on a wire over the menu -------------------------------

    private void modScripts(Table panel) {
        if (!(panel instanceof Dialog)) return;
        Dialog d = (Dialog) panel;
        d.pad(124f, 30f, 26f, 30f);
        d.getTitleTable().bottom().left().padBottom(8f);
        d.getTitleLabel().setStyle(style("tag-title"));
        Array<Label> labels = all(d.getContentTable(), Label.class);
        for (int i = 0; i < labels.size; i++) {
            Label l = labels.get(i);
            if (l.getParent() instanceof CheckBox) continue;
            if (is(l, "dim")) l.setStyle(style("typed-dim"));
            else if (is(l, "warn")) l.setStyle(style("typed"));
            else l.setStyle(style("typed"));
        }
        // The answers on a plate riveted to the tag's foot: Data only, the safe one (Esc gives it), then
        // Run scripts under its guard
        Table buttons = d.getButtonTable();
        Array<TextButton> answers = all(buttons, TextButton.class);
        TextButton data = first(answers, "data only"), run = first(answers, "run scripts");
        if (data == null || run == null) return;
        buttons.clearChildren();
        buttons.setBackground(skin.getDrawable("riveted"));
        buttons.add(data).minWidth(150f).padRight(6f);
        buttons.add(new Legend(paint).keys("ESC")).padRight(36f);
        guard(run);
        buttons.add(run).minWidth(150f).padLeft(14f);
        d.getCell(buttons).padTop(14f).fillX();
        // The tag hangs off its wire: a drag would pull it about
        d.setMovable(false);
    }

    // --- Shared pieces ------------------------------------------------------------------------------

    /**
     * The menu's push buttons: their legends on the cap's face, which sits above the lip, rather than
     * the middle of the button.
     */
    private void capsOnFaces(Table panel) {
        Array<TextButton> buttons = all(panel, TextButton.class);
        for (int i = 0; i < buttons.size; i++) {
            TextButton b = buttons.get(i);
            TextButton.TextButtonStyle s = b.getStyle();
            if (s != skin.get("menu", TextButton.TextButtonStyle.class) && s != skin.get("menu-go", TextButton.TextButtonStyle.class)
                    && s != skin.get("menu-amber", TextButton.TextButtonStyle.class)) continue;
            if (Math.abs(b.getPadTop() - b.getPadBottom()) < 0.5f) b.pad(b.getPadTop() - 2f, b.getPadLeft(), b.getPadBottom() + 5f, b.getPadRight());
        }
    }

    /** The keys a menu takes, engraved at its foot over the message line. */
    private void legendAtFoot(Table panel, Legend legend) {
        Array<Cell<?>> cells = cells(panel);
        Cell<?> message = null;
        for (int i = 0; i < cells.size; i++) {
            Actor a = cells.get(i).getActor();
            if (a instanceof Label && is((Label) a, "warn")) message = cells.get(i);
        }
        if (message == null) {
            panel.add(legend).colspan(3).padTop(12f).row();
            return;
        }
        Actor m = message.getActor();
        Table t = new Table();
        message.setActor(t);
        t.add(legend).padTop(4f).row();
        t.add(m).padTop(4f);
    }

    /**
     * Puts {@code b} under a red flip guard: the first press lifts it, and the order goes only with a
     * second press while it is up ({@link #GUARD_UP} seconds). A click and the keyboard's Enter both
     * reach the button as a change event; the first is stopped before the game's listener hears it.
     */
    void guard(TextButton b) {
        b.setStyle(skin.get("guarded", TextButton.TextButtonStyle.class));
        underGuard(b);
    }

    /** The guard's two presses on {@code b}, whatever it looks like. */
    static void underGuard(Button b) {
        b.addCaptureListener(new EventListener() {
            @Override
            public boolean handle(Event event) {
                if (!(event instanceof ChangeListener.ChangeEvent) || event.getTarget() != b) return false;
                b.clearActions();
                if (lifted(b)) {
                    // The guard was up: this press gives the order, and the guard drops back
                    setLifted(b, false);
                    return false;
                }
                setLifted(b, true);
                // Stopped, not cancelled: a click's own change to the button stands, and the order waits
                event.stop();
                b.addAction(Actions.delay(GUARD_UP, Actions.run(() -> setLifted(b, false))));
                return true;
            }
        });
    }

    /** Whether {@code b}'s guard is up. */
    static boolean lifted(Button b) {
        return Boolean.TRUE.equals(b.getUserObject());
    }

    /** Lifts or drops {@code b}'s guard; the checked state draws it, and changes without telling the game. */
    private static void setLifted(Button b, boolean lifted) {
        b.setUserObject(lifted ? Boolean.TRUE : null);
        b.setProgrammaticChangeEvents(false);
        b.setChecked(lifted);
        b.setProgrammaticChangeEvents(true);
    }

    private Label.LabelStyle style(String name) {
        return skin.get(name, Label.LabelStyle.class);
    }

    private boolean is(Label l, String style) {
        return skin.has(style, Label.LabelStyle.class) && l.getStyle() == skin.get(style, Label.LabelStyle.class);
    }

    private Image image(String drawable) {
        return new Image(skin.getDrawable(drawable));
    }

    private static boolean named(TextButton b, String text) {
        return b.getText().toString().trim().toLowerCase(Locale.ROOT).equals(text);
    }

    private static TextButton first(Array<TextButton> buttons, String text) {
        for (int i = 0; i < buttons.size; i++) if (named(buttons.get(i), text)) return buttons.get(i);
        return null;
    }

    /** A table's cells as they stand now, to look through while the table changes. */
    static Array<Cell<?>> cells(Table t) {
        Array<Cell<?>> out = new Array<>(t.getCells().size);
        for (int i = 0; i < t.getCells().size; i++) out.add(t.getCells().get(i));
        return out;
    }

    /** Every {@code type} under {@code g}, in the order they were laid out. */
    static <T extends Actor> Array<T> all(Group g, Class<T> type) {
        Array<T> out = new Array<>();
        collect(g, type, out);
        return out;
    }

    private static <T extends Actor> void collect(Group g, Class<T> type, Array<T> out) {
        for (int i = 0; i < g.getChildren().size; i++) {
            Actor a = g.getChildren().get(i);
            if (type.isInstance(a)) out.add(type.cast(a));
            if (a instanceof Group) collect((Group) a, type, out);
        }
    }

    private static <T extends Actor> T firstOf(Group g, Class<T> type) {
        Array<T> found = all(g, type);
        return found.size == 0 ? null : found.first();
    }

    /** A cell as the game laid it out: its actor and how it was placed, to lay it again elsewhere. */
    static final class Placed {
        final Actor actor;
        final Value minW, minH, prefW, prefH, maxW, maxH, padT, padL, padB, padR, spT, spL, spB, spR;
        final Float fillX, fillY;
        final Integer align, expandX, expandY;
        Integer colspan;
        final boolean endRow;

        Placed(Cell<?> c) {
            actor = c.getActor();
            minW = c.getMinWidthValue();
            minH = c.getMinHeightValue();
            prefW = c.getPrefWidthValue();
            prefH = c.getPrefHeightValue();
            maxW = c.getMaxWidthValue();
            maxH = c.getMaxHeightValue();
            padT = c.getPadTopValue();
            padL = c.getPadLeftValue();
            padB = c.getPadBottomValue();
            padR = c.getPadRightValue();
            spT = c.getSpaceTopValue();
            spL = c.getSpaceLeftValue();
            spB = c.getSpaceBottomValue();
            spR = c.getSpaceRightValue();
            fillX = c.getFillX();
            fillY = c.getFillY();
            align = c.getAlign();
            expandX = c.getExpandX();
            expandY = c.getExpandY();
            colspan = c.getColspan();
            endRow = c.isEndRow();
        }

        void into(Table t) {
            if (actor == null) return;
            Cell<Actor> c = t.add(actor);
            c.minWidth(minW).minHeight(minH).prefWidth(prefW).prefHeight(prefH).maxWidth(maxW).maxHeight(maxH);
            c.padTop(padT).padLeft(padL).padBottom(padB).padRight(padR);
            c.spaceTop(spT).spaceLeft(spL).spaceBottom(spB).spaceRight(spR);
            if (fillX != null && fillY != null) c.fill(fillX, fillY);
            if (align != null) c.align(align);
            if (expandX != null && expandY != null) c.expand(expandX, expandY);
            if (colspan != null) c.colspan(colspan);
            if (endRow) t.row();
        }
    }

    /**
     * The keys a menu takes, engraved: each key on a keycap (the arrows drawn, never typed) and what it
     * does beside it.
     */
    static final class Legend extends Widget {
        private final YardPaint paint;
        private final Array<String> items = new Array<>();
        private static final String GAP = "\u0000gap", KEY = "\u0001";

        Legend(YardPaint paint) {
            this.paint = paint;
        }

        Legend keys(String... names) {
            for (String n : names) items.add(KEY + n);
            return this;
        }

        Legend words(String w) {
            items.add(w.toUpperCase(Locale.ROOT));
            return this;
        }

        Legend gap() {
            items.add(GAP);
            return this;
        }

        @Override
        public float getPrefWidth() {
            float w = 0f;
            for (int i = 0; i < items.size; i++) w += width(items.get(i));
            return w;
        }

        @Override
        public float getPrefHeight() {
            return 17f;
        }

        private float width(String item) {
            if (item.equals(GAP)) return 26f;
            if (item.charAt(0) == KEY.charAt(0)) {
                String k = item.substring(1);
                return (isArrow(k) ? 15f : paint.keyWidth(k)) + 4f;
            }
            return paint.width(YardFonts.Face.LEGEND, 12f, item) + 6f;
        }

        private static boolean isArrow(String k) {
            return k.equals("UP") || k.equals("DOWN") || k.equals("LEFT") || k.equals("RIGHT");
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            validate();
            com.badlogic.gdx.graphics.Color was = batch.getColor().cpy();
            float top = getY() + (getHeight() + getPrefHeight()) / 2f;
            paint.begin(batch, top, parentAlpha * getColor().a, paint.time());
            paint.origin(getX(), 0f);
            float x = 0f;
            for (int i = 0; i < items.size; i++) {
                String item = items.get(i);
                if (item.equals(GAP)) {
                    x += 26f;
                } else if (item.charAt(0) == KEY.charAt(0)) {
                    String k = item.substring(1);
                    if (isArrow(k)) arrowCap(x, 1f, k);
                    else paint.key(x, 1f, k);
                    x += width(item);
                } else {
                    paint.legend(item, x + 2f, 13f, YardSkin.INK);
                    x += width(item);
                }
            }
            paint.origin(0f, 0f);
            batch.setColor(was);
        }

        /** A keycap with an arrow drawn on it. */
        private void arrowCap(float x, float y, String dir) {
            paint.rrect(x, y, 15f, 15f, 2.5f, Yard.RUBBER);
            paint.rrect(x + 1.2f, y + 0.8f, 12.6f, 11f, 2f, Yard.hex("4D4945"));
            float cx = x + 7.5f, cy = y + 5.6f;
            float[] tri;
            switch (dir) {
                case "UP": tri = new float[]{cx - 3.6f, cy + 2.6f, cx, cy - 3.2f, cx + 3.6f, cy + 2.6f}; break;
                case "DOWN": tri = new float[]{cx - 3.6f, cy - 2.6f, cx, cy + 3.2f, cx + 3.6f, cy - 2.6f}; break;
                case "LEFT": tri = new float[]{cx + 3f, cy - 3.6f, cx - 3f, cy, cx + 3f, cy + 3.6f}; break;
                default: tri = new float[]{cx - 3f, cy - 3.6f, cx + 3f, cy, cx - 3f, cy + 3.6f};
            }
            paint.fillConvex(tri, Yard.INK_LIGHT, 1f);
        }
    }
}
