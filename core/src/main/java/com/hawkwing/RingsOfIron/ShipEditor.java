package com.hawkwing.RingsOfIron;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.hawkwing.RingsOfIron.art.ArtLibrary;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.interiorgrid.editor.EditorProject;
import com.ringsofiron.moddingdata.defs.ChassisDef;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The ship editor: makes and changes chassis layouts, tile types and ship components, and saves
 * them into the same data files the game loads ({@code assets/data/chassis.toml}, {@code tiles.toml},
 * {@code components.toml}). Run with {@code gradlew :lwjgl3:runShipEditor}, or
 * {@code demos/Ship editor.bat} on Windows. See {@code documentation/ship-editor/Design.md}.
 *
 * <p>The panel on the right has three tabs: Layout, Tiles and Components. On the Layout tab, keys
 * 1-9 pick a tool (paint, component, edge wall, hull piece, covering, erase, port, light, conduit), R rotates
 * the component under the cursor (or what the tool places),
 * and the left mouse button uses the tool on the grid; Shift-drag with the paint tool fills a
 * rectangle. Right or middle drag pans, the wheel zooms (over the panel it scrolls), F fits the
 * ship to the view. Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S saves, Esc quits (twice with unsaved
 * changes). Every edit is built through the game's own loader, so the panel shows what the game
 * will make of the layout, or why it refuses it. A toggles between the art pack and flat colors.
 *
 * <p>Opened from a station yard in the game ({@link Refit}), it edits the layout of the player's
 * ship instead: only that layout, on the Layout tab, and Ctrl+S has the yard rebuild the ship to it
 * rather than writing the data files. Esc goes back to the game.
 */
public class ShipEditor extends EditorCatalogTabs {

    /** A station yard's refit of the player's ship, which the editor is open for. */
    public interface Refit {
        /** The layout the ship is built to now. */
        ChassisDef layout();

        /** Has the yard rebuild the ship to {@code layout}; returns null when done, or why not. */
        String apply(ChassisDef layout);

        /** What the yard would charge for the parts of {@code layout}, as a line, or null when nothing. */
        default String quote(ChassisDef layout) {
            return null;
        }

        /** Back to the game. */
        void close();
    }

    public ShipEditor() {
        this(Paths.get("").toAbsolutePath(), Paths.get("mods").toAbsolutePath());
    }

    public ShipEditor(Path assetsDir, Path modsDir) {
        this(assetsDir, modsDir, null);
    }

    /** The editor open on the layout of a ship a station yard is to rebuild. */
    public ShipEditor(Path assetsDir, Path modsDir, Refit refit) {
        super(assetsDir, modsDir, refit);
    }

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = com.ringsofiron.uihud.HudFonts.body();
        art = ArtLibrary.load(ArtLibrary.DEFAULT_PATH);
        ui = new OrthographicCamera();
        project = EditorProject.load(assetsDir, modsDir);
        if (!project.chassis.isEmpty()) chassisId = project.chassis.keySet().iterator().next();
        if (refit != null) openRefit();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        fit();
        if (refit != null) say("Refit: change the layout, then Ctrl+S to have the yard rebuild the ship, or Esc to leave it");
        else if (!project.problems.isEmpty()) say(project.problems.size() + " problem(s) loading data: " + project.problems.get(0));
        else say("Loaded " + project.chassis.size() + " chassis, " + project.tiles.size() + " tile types and "
                + project.components.size() + " components from " + project.dataDir);
        Gdx.input.setInputProcessor(new EditorInput());
    }

    /** Puts the ship's layout in the project, in place of its chassis as the data has it, and opens it. */
    private void openRefit() {
        ChassisDef def = refit.layout();
        if (def == null) return;
        ChassisModel model = ChassisModel.fromDef(def, catalog());
        EditorProject.Item<ChassisModel> item = project.chassis.get(def.id);
        if (item == null) item = project.addChassis(model);
        item.value = model;
        item.readOnly = false;
        item.dirty = false;
        chassisId = item.id;
        revision++;
    }

    /** Has the yard rebuild the ship to the layout as drawn, and goes back to the game when it has. */
    void applyRefit() {
        ChassisModel m = model();
        if (m == null) return;
        String why = refit.apply(m.toDef(catalog()));
        if (why != null) {
            say(why);
            return;
        }
        refit.close();
    }

    @Override
    public void resize(int width, int height) {
        ui.setToOrtho(false, width, height);
        ui.update();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        art.dispose();
    }

    /**
     * Asks to close the window. False the first time while there are unsaved changes, so a second
     * close request (or Esc) is needed to lose them.
     */
    public boolean requestClose() {
        if (!project.hasUnsavedChanges() || quitArmed) return true;
        quitArmed = true;
        say("Unsaved changes: close again to quit without saving, or Ctrl+S to save");
        return false;
    }

    // ---- Frame ----

    @Override
    public void render() {
        statusAge += Gdx.graphics.getDeltaTime();
        Gdx.gl.glClearColor(0.05f, 0.06f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(ui.combined);
        batch.setProjectionMatrix(ui.combined);
        if (model() != null) drawGrid();
        drawPanel();
    }

    // ---- Input ----

    private final class EditorInput extends InputAdapter {
        /** Ctrl or Cmd, tracked from key events (held-key polling misses it under some window systems). */
        private boolean ctrlDown;

        private float uiY(int screenY) {
            return Gdx.graphics.getHeight() - screenY;
        }

        private void hover(int screenX, int screenY) {
            float fx = (screenX - panX) / scale, fy = (uiY(screenY) - panY) / scale;
            hoverFx = fx;
            hoverFy = fy;
            if (screenX >= panelX()) {
                hoverX = hoverY = Integer.MIN_VALUE;
                return;
            }
            hoverX = (int) Math.floor(fx);
            hoverY = (int) Math.floor(fy);
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            hover(screenX, screenY);
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            hover(screenX, screenY);
            lastMouseX = screenX;
            lastMouseY = screenY;
            if (screenX >= panelX()) {
                float y = uiY(screenY);
                for (Hit h : hits) {
                    if (h.contains(screenX, y)) {
                        // Clicking a field that is being typed in keeps it open
                        h.action.run();
                        return true;
                    }
                }
                commitEdit();
                return true;
            }
            commitEdit();
            if (button == com.badlogic.gdx.Input.Buttons.RIGHT || button == com.badlogic.gdx.Input.Buttons.MIDDLE) {
                panning = true;
                return true;
            }
            if (button == com.badlogic.gdx.Input.Buttons.LEFT && tab == Tab.LAYOUT) {
                toolDown(Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT));
            }
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (panning) {
                panX += screenX - lastMouseX;
                panY -= screenY - lastMouseY;
            }
            lastMouseX = screenX;
            lastMouseY = screenY;
            hover(screenX, screenY);
            ChassisModel m = model();
            if (stroke && m != null) strokeTo(m, catalog());
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            hover(screenX, screenY);
            if (panning) {
                panning = false;
                return true;
            }
            if (button == com.badlogic.gdx.Input.Buttons.LEFT) toolUp();
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            int mx = Gdx.input.getX(), my = Gdx.input.getY();
            if (mx >= panelX()) {
                float max = Math.max(0f, panelContentHeight - Gdx.graphics.getHeight() + LINE * 5);
                // Scrolling down lifts the content: the offset goes negative
                panelScroll = Math.max(-max, Math.min(0f, panelScroll - amountY * LINE * 2));
                return true;
            }
            float before = scale;
            scale = Math.max(4f, Math.min(96f, scale * (float) Math.pow(1.15f, -amountY)));
            float cx = mx, cy = uiY(my);
            panX = cx - (cx - panX) * scale / before;
            panY = cy - (cy - panY) * scale / before;
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == com.badlogic.gdx.Input.Keys.CONTROL_LEFT || keycode == com.badlogic.gdx.Input.Keys.CONTROL_RIGHT
                    || keycode == com.badlogic.gdx.Input.Keys.SYM) {
                ctrlDown = true;
                return true;
            }
            boolean ctrl = ctrlDown;
            if (edit != null) {
                if (keycode == com.badlogic.gdx.Input.Keys.ENTER || keycode == com.badlogic.gdx.Input.Keys.NUMPAD_ENTER) {
                    commitEdit();
                } else if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                    edit = null;
                } else if (keycode == com.badlogic.gdx.Input.Keys.BACKSPACE && edit.text.length() > 0) {
                    edit.text.setLength(edit.text.length() - 1);
                } else if (ctrl && keycode == com.badlogic.gdx.Input.Keys.V) {
                    String clip = Gdx.app.getClipboard().getContents();
                    if (clip != null) edit.text.append(clip.replace("\n", " "));
                }
                return true;
            }
            if (ctrl) {
                if (keycode == com.badlogic.gdx.Input.Keys.S) {
                    if (refit != null) applyRefit();
                    else save();
                }
                else if (keycode == com.badlogic.gdx.Input.Keys.Z) undoStep();
                else if (keycode == com.badlogic.gdx.Input.Keys.Y) redoStep();
                return true;
            }
            switch (keycode) {
                case com.badlogic.gdx.Input.Keys.ESCAPE:
                    if (refit != null) {
                        if (!project.hasUnsavedChanges() || quitArmed) refit.close();
                        else {
                            quitArmed = true;
                            say("Layout changed: Esc again to leave it unbuilt, or Ctrl+S to have the yard rebuild the ship");
                        }
                    } else if (!project.hasUnsavedChanges() || quitArmed) Gdx.app.exit();
                    else requestClose();
                    return true;
                case com.badlogic.gdx.Input.Keys.F:
                    fit();
                    return true;
                case com.badlogic.gdx.Input.Keys.A:
                    artView = !artView;
                    say(drawingArt() ? "Showing the art pack" : art.isEmpty() ? "No art atlas built; showing flat colors"
                            : "Showing flat colors");
                    return true;
                case com.badlogic.gdx.Input.Keys.R:
                    if (!rotateHovered()) rotateTool();
                    return true;
                case com.badlogic.gdx.Input.Keys.X:
                    if (tab == Tab.LAYOUT && tool == Tool.HULL) flipPiece();
                    return true;
                case com.badlogic.gdx.Input.Keys.TAB:
                    if (refit == null) tab = Tab.values()[(tab.ordinal() + 1) % Tab.values().length];
                    return true;
                default:
                    break;
            }
            // 1-9 pick the first nine tools, 0 the tenth
            int n = keycode == com.badlogic.gdx.Input.Keys.NUM_0 ? 9 : keycode - com.badlogic.gdx.Input.Keys.NUM_1;
            if (n >= 0 && n < Tool.values().length) {
                tool = Tool.values()[n];
                tab = Tab.LAYOUT;
                return true;
            }
            return false;
        }

        @Override
        public boolean keyUp(int keycode) {
            if (keycode == com.badlogic.gdx.Input.Keys.CONTROL_LEFT || keycode == com.badlogic.gdx.Input.Keys.CONTROL_RIGHT
                    || keycode == com.badlogic.gdx.Input.Keys.SYM) {
                ctrlDown = false;
            }
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (edit == null) return false;
            if (character >= 32 && character != 127) edit.text.append(character);
            return true;
        }
    }
}
