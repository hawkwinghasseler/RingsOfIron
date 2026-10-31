package com.hawkwing.RingsOfIron;

import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipPartCatalog;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.editor.ChassisModel;
import com.ringsofiron.moddingdata.defs.ChassisDef;

import java.nio.file.Path;
import java.util.Locale;

/**
 * The {@link ShipEditor}'s grid tools: what each tool does as the mouse goes down, drags and comes up.
 */
abstract class EditorTools extends EditorCanvas {

    EditorTools(Path assetsDir, Path modsDir, ShipEditor.Refit refit) {
        super(assetsDir, modsDir, refit);
    }

    // ---- Grid tools ----

    void toolDown(boolean shift) {
        ChassisModel m = model();
        if (m == null || !m.inBounds(hoverX, hoverY)) return;
        if (!editable()) {
            say("This chassis comes from a mod and cannot be edited here");
            return;
        }
        ShipPartCatalog cat = catalog();
        switch (tool) {
            case PAINT:
                if (shift) {
                    rectStartX = hoverX;
                    rectStartY = hoverY;
                    return;
                }
                if (cat.tile(tileId) == null) return;
                beginEdit();
                stroke = true;
                strokeTo(m, cat);
                break;
            case ERASE: {
                // An RCS block or a light comes off first, leaving whatever stands on its tile
                Side side = nearestSide();
                ChassisDef.Rcs block = m.rcsAt(hoverX, hoverY, side);
                if (block != null) {
                    beginEdit();
                    m.rcs.remove(block);
                    changed();
                    say("Took off the RCS block at " + hoverX + "," + hoverY + " " + side);
                    break;
                }
                if (m.lightAt(hoverX, hoverY, side) != null) {
                    beginEdit();
                    m.toggleLight(hoverX, hoverY, side);
                    changed();
                    say("Took down the light at " + hoverX + "," + hoverY + " " + side);
                    break;
                }
                // Then a conduit along the edge the cursor is close to
                Side edge = conduitSide();
                if (edge != null && m.edgeConduitAt(hoverX, hoverY, edge) != null) {
                    beginEdit();
                    m.toggleEdgeConduit(hoverX, hoverY, edge);
                    changed();
                    say("Took up the conduit at " + hoverX + "," + hoverY + " " + edge);
                    break;
                }
                // Then a pipe along that edge, or across the tile
                if (m.pipeAt(hoverX, hoverY, edge) != null) {
                    beginEdit();
                    m.togglePipe(hoverX, hoverY, edge, cat);
                    changed();
                    say("Took up the pipe at " + hoverX + "," + hoverY + (edge == null ? "" : " " + edge));
                    break;
                }
                beginEdit();
                stroke = true;
                strokeTo(m, cat);
                break;
            }
            case COVERING:
                rectStartX = hoverX;
                rectStartY = hoverY;
                break;
            case COMPONENT: {
                MountFootprint f = ghostFootprint(cat);
                if (f == null) return;
                beginEdit();
                m.place(componentId, size, f.originX, f.originY, rotation, cat);
                changed();
                ShipPartCatalog.ComponentType type = cat.component(componentId);
                say("Placed " + (type != null ? type.name : componentId) + " " + size + " at " + f.originX + "," + f.originY);
                break;
            }
            case EDGE: {
                Side side = nearestSide();
                beginEdit();
                m.toggleEdgeWall(hoverX, hoverY, side, weight.name(), bulkhead);
                changed();
                break;
            }
            case HULL: {
                int[] o = pieceOrigin();
                ChassisModel trial = m.copy();
                if (trial.addHullPiece(pieceShape, o[0], o[1], pieceWidth, pieceHeight, rotation, weight.name()) == null) {
                    say("The piece would not fit on the grid here");
                    return;
                }
                beginEdit();
                m.addHullPiece(pieceShape, o[0], o[1], pieceWidth, pieceHeight, rotation, weight.name());
                changed();
                break;
            }
            case PORT: {
                ChassisModel.Mount mount = m.mountAt(hoverX, hoverY, cat);
                if (mount == null || !ChassisModel.isPort(mount, cat)) {
                    say("No docking port here");
                    return;
                }
                beginEdit();
                mount.active = !mount.active;
                changed();
                say("Port " + (mount.active ? "active: it extends a latch collar" : "passive: magnets only"));
                break;
            }
            case LIGHT: {
                Side side = nearestSide();
                boolean had = m.lightAt(hoverX, hoverY, side) != null;
                beginEdit();
                m.toggleLight(hoverX, hoverY, side);
                changed();
                say(had ? "Took down the light" : "Hung a light on the " + side.name().toLowerCase() + " wall of " + hoverX + "," + hoverY);
                break;
            }
            case RCS: {
                Side side = nearestSide();
                ChassisDef.Rcs had = m.rcsAt(hoverX, hoverY, side);
                beginEdit();
                String why = m.toggleRcs(hoverX, hoverY, side, rcsKind, cat);
                if (why != null) {
                    undo.pop();
                    say("Can't place RCS: " + why);
                    break;
                }
                changed();
                say(had != null && had.kind.equals(rcsKind) ? "Took off the RCS block"
                        : "Bolted " + rcsLabel(rcsKind) + " RCS onto the " + side.name().toLowerCase(Locale.ROOT)
                        + " edge of " + hoverX + "," + hoverY);
                break;
            }
            case CONDUIT:
                conduitDown(m, cat);
                break;
            case PIPE:
                pipeDown(m, cat);
                break;
            default:
                break;
        }
    }

    /**
     * Near an edge, runs a conduit along it or takes up the one there; in the middle of a floor
     * tile, lays a conduit across the tile or takes it up.
     */
    void conduitDown(ChassisModel m, ShipPartCatalog cat) {
        Side side = conduitSide();
        if (side != null) {
            boolean had = m.edgeConduitAt(hoverX, hoverY, side) != null;
            beginEdit();
            m.toggleEdgeConduit(hoverX, hoverY, side);
            changed();
            say(had ? "Took up the conduit" : "Ran a conduit along the " + side.name().toLowerCase(Locale.ROOT)
                    + " edge of " + hoverX + "," + hoverY);
            return;
        }
        ShipPartCatalog.TileType t = cat.tileFor(m.tiles[hoverY][hoverX]);
        if (t == null || (t.base != ShipPartCatalog.TileBase.FLOOR && t.base != ShipPartCatalog.TileBase.CONDUIT)) {
            say("A conduit across a tile needs a floor; run it along an edge to go through a wall");
            return;
        }
        if (m.mountAt(hoverX, hoverY, cat) != null) {
            say("A component stands here; run the conduit along an edge of its tiles");
            return;
        }
        boolean had = t.base == ShipPartCatalog.TileBase.CONDUIT;
        char symbol = had ? ChassisModel.symbolFor(cat, ShipPartCatalog.TileBase.FLOOR, '.')
                : ChassisModel.symbolFor(cat, ShipPartCatalog.TileBase.CONDUIT, '+');
        beginEdit();
        m.paint(hoverX, hoverY, symbol, cat);
        changed();
        say(had ? "Took up the conduit across " + hoverX + "," + hoverY : "Laid a conduit across " + hoverX + "," + hoverY);
    }

    /**
     * Near an edge, runs a pipe along it or takes up the one there; in the middle of a tile, lays a
     * pipe across it (under the deck, components included) or takes it up.
     */
    void pipeDown(ChassisModel m, ShipPartCatalog cat) {
        Side side = conduitSide();
        boolean had = m.pipeAt(hoverX, hoverY, side) != null;
        beginEdit();
        if (!m.togglePipe(hoverX, hoverY, side, cat)) {
            undo.pop();
            say("A pipe across a tile needs a floor; run it along an edge to go through a wall");
            return;
        }
        changed();
        String where = side == null ? "across " + hoverX + "," + hoverY
                : "along the " + side.name().toLowerCase(Locale.ROOT) + " edge of " + hoverX + "," + hoverY;
        say(had ? "Took up the pipe " + where : "Laid a pipe " + where);
    }

    /**
     * Paints or erases every tile from the last one the stroke reached to the hovered one, so a
     * fast drag leaves no gaps. Each tile is done once per visit.
     */
    void strokeTo(ChassisModel m, ShipPartCatalog cat) {
        if (hoverX == strokeLastX && hoverY == strokeLastY) return;
        int x0 = strokeLastX == Integer.MIN_VALUE ? hoverX : strokeLastX;
        int y0 = strokeLastX == Integer.MIN_VALUE ? hoverY : strokeLastY;
        int steps = Math.max(Math.abs(hoverX - x0), Math.abs(hoverY - y0));
        boolean first = strokeLastX == Integer.MIN_VALUE;
        for (int i = first ? 0 : 1; i <= steps; i++) {
            int x = steps == 0 ? hoverX : Math.round(x0 + (hoverX - x0) * (float) i / steps);
            int y = steps == 0 ? hoverY : Math.round(y0 + (hoverY - y0) * (float) i / steps);
            if (!m.inBounds(x, y)) continue;
            if (tool == Tool.PAINT) {
                ShipPartCatalog.TileType t = cat.tile(tileId);
                if (t != null) m.paint(x, y, t.symbol, cat);
            } else if (tool == Tool.ERASE) {
                say("Erased " + m.erase(x, y, cat));
            }
        }
        strokeLastX = hoverX;
        strokeLastY = hoverY;
        changed();
    }

    void toolUp() {
        ChassisModel m = model();
        stroke = false;
        strokeLastX = strokeLastY = Integer.MIN_VALUE;
        if (rectStartX == Integer.MIN_VALUE || m == null) return;
        int x0 = rectStartX, y0 = rectStartY;
        rectStartX = Integer.MIN_VALUE;
        int x1 = Math.max(0, Math.min(m.width() - 1, hoverX)), y1 = Math.max(0, Math.min(m.height() - 1, hoverY));
        ShipPartCatalog cat = catalog();
        beginEdit();
        if (tool == Tool.PAINT) {
            ShipPartCatalog.TileType t = cat.tile(tileId);
            if (t != null) m.fill(x0, y0, x1, y1, t.symbol, cat);
        } else if (tool == Tool.COVERING) {
            m.addCovering(covering.name(), x0, y0, x1, y1);
            say("Laid " + covering.name().toLowerCase(Locale.ROOT));
        }
        changed();
    }
}
