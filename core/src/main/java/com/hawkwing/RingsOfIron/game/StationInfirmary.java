package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.TaskRules;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.Locale;

/**
 * A station's infirmary treats the player's seriously and critically wounded, and anyone knocked
 * out, while the player's ship is docked there (round 8). Towed home from a lost ace fight with
 * one hand left on their feet, seed 2026's crew lay unconscious and untreated at the berth for
 * days, and two of them died there: the crew's care stops in time warp, and one crewman with no
 * medical stores couldn't keep up.
 * <ul>
 *   <li>A patient is anyone of the player's crew aboard the ship or the station, alive, and down
 *       or seriously or critically wounded, whoever else treats them (seed 2026's one hand on their
 *       feet, with no stores left, took twenty minutes over a serious wound). A critical wound is
 *       stabilised at once.</li>
 *   <li>The station's medics treat at a medic's rate in a medbay ({@link CrewDef#MEDBAY_CARE}), a
 *       wound level at a time, until the patient is on their feet: a light wound left once they
 *       are is the crew's own first aid. Anyone with no worse wound still down where they can
 *       breathe is brought round.
 *       It runs on sim time, so time warp at the berth treats them too.</li>
 *   <li>Each level is billed to the player ({@link #CRITICAL_PRICE}, {@link #SERIOUS_PRICE},
 *       {@link #LIGHT_PRICE}), times the station's prices ({@link StationYard#priceFactor}), as
 *       far as the player's credits go.</li>
 * </ul>
 * Not at a station whose crew are the player's enemies, and not while the ship is at battle
 * stations. Nothing here is saved: the care given is kept on the crew.
 */
public final class StationInfirmary {
    /** Credits for bringing a critical, serious or light wound down a level. */
    static final double CRITICAL_PRICE = 300, SERIOUS_PRICE = 150, LIGHT_PRICE = 50;

    private final GameWorld world;
    private final CrewService crew;
    private double last = Double.NaN;

    public StationInfirmary(GameWorld world) {
        this.world = world;
        this.crew = world.crew;
    }

    /** Once a sim tick ({@link GameWorld#tickManagers}). */
    public void update() {
        double now = world.engine.getSimSeconds();
        float dt = Double.isNaN(last) ? 0f : (float) Math.max(0, now - last);
        last = now;
        Entity ship = world.getPlayer();
        Entity station = ship == null ? null : world.docking.dockedTo(ship);
        if (station == null || station.getComponent(StationComponent.class) == null) return;
        if (!crew.friendlyTo(FactionStanding.PLAYER, station)) return;
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        if (board != null && board.battleStations) return;
        for (Entity e : crew.crewEntities()) {
            CrewMemberComponent c = crew.crew(e);
            GridPositionComponent p = crew.position(e);
            if (!patient(c, p, ship, station) || crew.homeOf(e) != ship) continue;
            treat(e, c, station, dt);
        }
    }

    private boolean patient(CrewMemberComponent c, GridPositionComponent p, Entity ship, Entity station) {
        if (c == null || p == null || !c.isAlive() || c.held || c.faction != FactionStanding.PLAYER) return false;
        if (p.ship != ship && p.ship != station) return false;
        return c.wound.compareTo(Wound.SERIOUS) >= 0 || !c.isConscious();
    }

    private void treat(Entity e, CrewMemberComponent c, Entity station, float dt) {
        if (c.wound == Wound.CRITICAL && !c.stabilised) {
            c.stabilised = true;
            world.notices.post(c.name + " is taken into " + ShipNames.name(station) + "'s infirmary", NoticeLog.Kind.INFO);
        }
        float left = dt * CrewDef.MEDBAY_CARE;
        while (c.wound != Wound.NONE && !c.isConscious() && left > 0f) {
            float need = CrewDef.careTime(c.wound) - c.care;
            if (left < need) {
                c.care += left;
                return;
            }
            left -= need;
            Wound was = c.wound;
            crew.easeWound(e);
            bill(station, c, was);
        }
        // Brought round where they can breathe; one down in bad air is the crew's to carry out first
        GridPositionComponent p = crew.position(e);
        if (!c.wound.disabling() && !c.isConscious() && TaskRules.isSafe(crew.grid(p.ship), p.tileX(), p.tileY())) {
            crew.heal(e, c.def.maxHealth);
            world.notices.post(c.name + " is back on their feet after " + ShipNames.name(station) + "'s infirmary",
                    NoticeLog.Kind.INFO);
        }
    }

    private void bill(Entity station, CrewMemberComponent c, Wound was) {
        TraderComponent trader = world.getPlayer().getComponent(TraderComponent.class);
        double price = price(was) * world.yard.priceFactor.applyAsDouble(station);
        if (trader != null) trader.credits = Math.max(0, trader.credits - price);
        world.notices.post(String.format(Locale.ROOT, "%s's infirmary treated %s's %s wound: %.0f cr",
                ShipNames.name(station), c.name, was.name().toLowerCase(Locale.ROOT), price), NoticeLog.Kind.INFO);
    }

    /** Credits for bringing {@code wound} down a level. */
    static double price(Wound wound) {
        switch (wound) {
            case CRITICAL: return CRITICAL_PRICE;
            case SERIOUS: return SERIOUS_PRICE;
            default: return LIGHT_PRICE;
        }
    }
}
