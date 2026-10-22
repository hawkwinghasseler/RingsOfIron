package com.hawkwing.RingsOfIron.debug;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.game.CrewMedical;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.combatsys.components.MissileComponent;
import com.ringsofiron.combatsys.components.ProjectileComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.Locale;

/** What the console calls things: a crew member by name and state, a ship by its marker, a tile by what is on it. */
public final class DebugNames {
    private DebugNames() {}

    /** A short name for any entity the console might hand back. */
    public static String name(Entity e) {
        if (e == null) return "nothing";
        CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
        if (c != null) return c.name;
        StationComponent st = e.getComponent(StationComponent.class);
        if (st != null) return st.name;
        HudMarkerComponent m = e.getComponent(HudMarkerComponent.class);
        if (m != null && m.label != null && !m.label.isEmpty()) return m.label;
        DefRefComponent def = e.getComponent(DefRefComponent.class);
        if (def != null) return def.defId;
        if (e.getComponent(MissileComponent.class) != null) return "missile";
        if (e.getComponent(ProjectileComponent.class) != null) return "round";
        SubsystemComponent s = e.getComponent(SubsystemComponent.class);
        if (s != null) return lower(s.type.name());
        return "entity";
    }

    /** One line about the target, for the console's title bar and its results. */
    public static String describe(DebugTarget t) {
        if (t == null) return "nothing selected";
        switch (t.kind) {
            case CREW: {
                CrewMemberComponent c = t.entity.getComponent(CrewMemberComponent.class);
                GridPositionComponent p = t.entity.getComponent(GridPositionComponent.class);
                String where = p == null ? "" : " at " + p.tileX() + "," + p.tileY() + " aboard " + name(t.ship);
                return c.name + " (" + lower(c.role.name()) + ", " + CrewMedical.condition(c) + ", "
                        + Math.round(c.health) + " hp, " + c.activity + ")" + where;
            }
            case TILE: {
                Tile tile = t.getTile();
                StringBuilder sb = new StringBuilder("tile ").append(t.tileX).append(',').append(t.tileY)
                        .append(" aboard ").append(name(t.ship));
                if (tile == null) return sb.toString();
                sb.append(" (").append(tile.isWall() ? lower(tile.wallType.name()) + " wall" : tile.isFloor() ? "floor" : "void");
                sb.append(", ").append(Math.round(tile.getIntegrity() * 100f)).append("%");
                if (tile.isBurning()) sb.append(", ").append(lower(tile.fireSize().name())).append(" fire");
                if (tile.isHullBreach()) sb.append(", breached");
                ShipGridComponent g = t.grid();
                if (tile.holdsAir() && g != null) sb.append(", ").append(Math.round(g.pressureRatioAt(t.tileX, t.tileY) * 100f)).append("% air");
                sb.append(')');
                SubsystemComponent s = t.getSystem();
                if (s != null) {
                    sb.append(", ").append(lower(s.type.name())).append(' ').append(Math.round(s.health * 100f)).append('%')
                            .append(s.isOperational ? "" : s.isDisabled() ? " disabled" : " off");
                }
                return sb.toString();
            }
            default:
                return name(t.entity) + " (" + t.kind.label + ")";
        }
    }

    static String lower(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
