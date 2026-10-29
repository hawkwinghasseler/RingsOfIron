package com.hawkwing.RingsOfIron.game;

import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.HullShape;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.stationsdocking.DockingData;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;

import java.util.List;

/**
 * The hull macro-sim's freighters wear in the bubble. They have no interior (no grid, crew or
 * systems), but they look, collide and dock as the {@link GameScenario#FREIGHTER_SHIP}'s chassis:
 * drawn with its hull art and engines, shaped like its layout, and docked by the passive Medium
 * port in its bow. Before, they flew a plain 40 m by 12 m box drawn as its outline.
 */
public final class FreighterHull {
    /** The chassis id, which names its hull art. */
    public final String chassis;
    /** The chassis's layout as built, put on no craft: what its art and outline are placed by. */
    public final ShipGridComponent grid;
    /** The engines in the layout, drawn on the hull. */
    public final Array<ChassisBlueprintLoader.SubsystemPlacement> engines = new Array<>();
    /** The outline, in convex pieces, body-local meters ({@link HullShape}). */
    public final List<float[]> pieces;
    /** The bow port, or null when the chassis has no passive Medium port. */
    private final DockingPort bow;

    private FreighterHull(String chassis, ChassisBlueprintLoader.LoadResult built, DockingPort bow) {
        this.chassis = chassis;
        this.grid = built.grid;
        this.pieces = HullShape.pieces(built.grid);
        this.bow = bow;
        for (ChassisBlueprintLoader.SubsystemPlacement p : built.subsystems) {
            if (p.type == SubsystemComponent.SubsystemType.ENGINE) engines.add(p);
        }
    }

    /** The freighters' hull from {@code defs}, or null when they have no Hauler with a chassis to wear. */
    static FreighterHull of(DefRegistry defs) {
        if (defs == null || !defs.has("ship", GameScenario.FREIGHTER_SHIP)) return null;
        ShipDef ship = defs.get("ship", GameScenario.FREIGHTER_SHIP, ShipDef.class);
        if (ship == null || ship.chassis == null || !defs.has("chassis", ship.chassis)) return null;
        ChassisDef def = defs.require("chassis", ship.chassis, ChassisDef.class);
        ChassisBlueprintLoader.LoadResult built = ChassisBlueprintLoader.load(
                InteriorGridData.toBlueprint(def, InteriorGridData.catalog(defs)));
        built.grid.chassis = def.id;
        DockingPort bow = null;
        for (DockingPort p : DockingData.portsFor(built.grid, def).ports) {
            if (p.size == MountFootprint.MountSize.M && !p.active) {
                bow = p;
                break;
            }
        }
        return new FreighterHull(def.id, built, bow);
    }

    /**
     * A fresh port where the bow port is, as a port on a craft with no grid: null when the
     * chassis has none.
     */
    DockingPort bowPort() {
        if (bow == null) return null;
        return DockingPortsComponent.hullPort(bow.size, false, bow.edgeX, bow.edgeY, bow.normalX, bow.normalY);
    }

    /** Area-weighted middle of the outline, body-local meters, into {@code out} as x, y. */
    void centroid(float[] out) {
        float area = 0f, cx = 0f, cy = 0f;
        for (float[] v : pieces) {
            for (int i = 0; i < v.length; i += 2) {
                int j = (i + 2) % v.length;
                float cross = v[i] * v[j + 1] - v[j] * v[i + 1];
                area += cross;
                cx += (v[i] + v[j]) * cross;
                cy += (v[i + 1] + v[j + 1]) * cross;
            }
        }
        out[0] = area == 0f ? 0f : cx / (3f * area);
        out[1] = area == 0f ? 0f : cy / (3f * area);
    }
}
