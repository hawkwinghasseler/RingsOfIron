package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.hawkwing.RingsOfIron.wiring.AvatarInteractor;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.CaptainStatusProvider;

/** ui-hud's {@link CaptainStatusProvider}: the player's avatar from ai-crew, and the room it stands in. */
public class PlayerCaptainStatus implements CaptainStatusProvider {
    private final AvatarInteractor avatars;
    private final RoomNames rooms = new RoomNames();
    private Entity roomsFor;

    public PlayerCaptainStatus(AvatarInteractor avatars) {
        this.avatars = avatars;
    }

    @Override
    public void read(CaptainStatus out) {
        Entity avatar = avatars.getAvatar();
        CrewMemberComponent c = avatar == null ? null : avatar.getComponent(CrewMemberComponent.class);
        if (c == null) return;
        out.present = true;
        float max = c.def == null || c.def.maxHealth <= 0f ? 100f : c.def.maxHealth;
        out.health = Math.max(0f, Math.min(1f, c.health / max));
        out.state = c.state.name().toLowerCase(java.util.Locale.ROOT);
        out.wound = c.wound == Wound.NONE ? "" : c.wound.label;
        out.bleedOut = c.wound == Wound.CRITICAL && !c.stabilised ? c.bleedOut : Float.NaN;
        out.suited = c.suited;
        if (c.suited) {
            out.suitKind = c.suitKind.name();
            out.suitAir = c.suitAir;
            out.suitAirCapacity = Suit.capacity(c.suitKind);
        }
        out.carrying = c.carrying != null ? name(c.carrying) : c.equippedItem == null ? "" : c.equippedItem;

        GridPositionComponent pos = avatar.getComponent(GridPositionComponent.class);
        ShipGridComponent grid = pos == null || pos.ship == null ? null : pos.ship.getComponent(ShipGridComponent.class);
        if (grid == null) return;
        int x = (int) Math.floor(pos.x), y = (int) Math.floor(pos.y);
        Tile t = grid.inBounds(x, y) ? grid.getTile(x, y) : null;
        Compartment room = t == null ? null : t.compartment;
        if (room == null) return;
        if (roomsFor != pos.ship) {
            roomsFor = pos.ship;
            rooms.reset();
        }
        out.room = rooms.of(grid, room);
        out.roomPressure = room.pressureRatio();
        out.roomOxygen = room.oxygenRatio();
        out.roomSmoke = Math.max(0f, Math.min(1f, room.smokeDensity() / TileShade.THICK_SMOKE));
    }

    private static String name(Entity e) {
        CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
        return c == null ? "" : c.name;
    }
}
