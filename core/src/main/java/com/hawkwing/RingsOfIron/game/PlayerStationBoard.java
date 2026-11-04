package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewQueries;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.ManConsoleTask;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.providers.StationBoardProvider;

import java.util.Comparator;

/**
 * ui-hud's {@link StationBoardProvider}: each console aboard the player's ship ({@link ShipConsoles}),
 * named by its place ("HELM", "FWD GUN"), and who works it: the player seated there, a crew member
 * manning it, one on the way to it, or nobody; and how many of the crew are free for a post.
 */
public class PlayerStationBoard implements StationBoardProvider {
    private static final Comparator<Seat> BY_POST = (a, b) -> Integer.compare(a.post.ordinal(), b.post.ordinal());

    private final GameContext ctx;
    private final CrewService crew;
    private final Bridge bridge;
    private final RoomNames places = new RoomNames();
    private Entity placesFor;
    private final ObjectIntMap<String> names = new ObjectIntMap<>(), numbered = new ObjectIntMap<>();

    public PlayerStationBoard(GameContext ctx, CrewService crew, Bridge bridge) {
        this.ctx = ctx;
        this.crew = crew;
        this.bridge = bridge;
    }

    @Override
    public void collect(StationBoard out) {
        Entity ship = ctx.player();
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return;
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (placesFor != ship) {
            placesFor = ship;
            places.reset();
        }
        SubsystemType seat = bridge.seat();
        Entity seatedGun = bridge.seatedGun();
        // Indexed: Bridge.console walks the same list, and its iterator can't be nested
        for (int i = 0; i < registry.subsystems.size; i++) {
            Entity e = registry.subsystems.get(i);
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null || !ShipConsoles.USABLE.contains(s.type) || s.isDestroyed()) continue;
            Seat row = out.add();
            row.post = post(s.type);
            row.console = e;
            row.working = !s.isDisabled() && s.switchedOn && s.isOperational;
            if (s.footprint != null) {
                row.tileX = s.footprint.originX;
                row.tileY = s.footprint.originY;
            }
            row.name = base(row, s, grid);
            boolean player = s.type == SubsystemType.TURRET_MOUNT ? e == seatedGun
                    : (seat == s.type || (s.type == SubsystemType.HELM && bridge.playerAtHelm())) && Bridge.console(ship, s.type) == s;
            if (player) {
                row.hand = Hand.PLAYER;
                row.who = "you";
            } else {
                hand(row, ship, e);
            }
        }
        out.seats.sort(BY_POST);
        number(out);
        count(out, ship);
    }

    /** A crew member manning {@code console}, else one on the way to it. */
    private void hand(Seat row, Entity ship, Entity console) {
        ImmutableArray<Entity> aboard = crew.aboard(ship);
        Entity coming = null;
        for (int i = 0; i < aboard.size(); i++) {
            Entity c = aboard.get(i);
            if (c.getComponent(PlayerAvatarComponent.class) != null) continue;
            CrewMemberComponent m = crew.crew(c);
            if (m == null || !(m.task instanceof ManConsoleTask) || ((ManConsoleTask) m.task).console != console) continue;
            if (CrewQueries.isManning(c, console)) {
                row.hand = Hand.CREW;
                row.who = m.name;
                return;
            }
            if (m.task.claimedBy == c && m.isConscious()) coming = c;
        }
        if (coming != null) {
            row.hand = Hand.COMING;
            row.who = crew.crew(coming).name;
        }
    }

    /** The crew aboard of the ship's own faction, the player left out, and those conscious with no task. */
    private void count(StationBoard out, Entity ship) {
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        int faction = board == null ? 0 : board.faction;
        ImmutableArray<Entity> aboard = crew.aboard(ship);
        for (int i = 0; i < aboard.size(); i++) {
            Entity c = aboard.get(i);
            CrewMemberComponent m = crew.crew(c);
            if (m == null || m.faction != faction || !m.isAlive() || m.held || c.getComponent(PlayerAvatarComponent.class) != null) continue;
            out.crewAboard++;
            if (m.isConscious() && m.task == null) out.crewFree++;
        }
    }

    private String base(Seat row, SubsystemComponent s, ShipGridComponent grid) {
        switch (row.post) {
            case HELM: return "HELM";
            case WEAPONS: return "WEAPONS";
            case LIFE_SUPPORT: return "LIFE SUPPORT";
            case REACTOR: return "REACTOR";
            default:
                if (grid == null || s.footprint == null) return "GUN";
                // Its footprint's middle in body meters; "MID" says nothing of a gun's side, as the weapon rows name them
                float x = grid.gridOriginLocal.x + (s.footprint.originX + s.footprint.width() * 0.5f) * grid.tileSize;
                float y = grid.gridOriginLocal.y + (s.footprint.originY + s.footprint.height() * 0.5f) * grid.tileSize;
                String place = places.place(grid, x, y);
                if (place.equals("MID")) return "GUN";
                if (place.startsWith("MID ")) place = place.substring(4);
                return place + " GUN";
        }
    }

    /** "PORT GUN 2": a number after a name more than one post shares. */
    private void number(StationBoard out) {
        names.clear();
        numbered.clear();
        for (int i = 0; i < out.seats.size; i++) names.getAndIncrement(out.seats.get(i).name, 0, 1);
        for (int i = 0; i < out.seats.size; i++) {
            Seat s = out.seats.get(i);
            if (names.get(s.name, 0) > 1) s.name = s.name + " " + (numbered.getAndIncrement(s.name, 0, 1) + 1);
        }
    }

    private static Post post(SubsystemType type) {
        switch (type) {
            case HELM: return Post.HELM;
            case WEAPONS: return Post.WEAPONS;
            case LIFE_SUPPORT: return Post.LIFE_SUPPORT;
            case REACTOR: return Post.REACTOR;
            default: return Post.GUN;
        }
    }
}
