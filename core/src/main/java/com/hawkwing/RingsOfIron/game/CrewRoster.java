package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewDef;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.uihud.providers.CrewProvider;

import java.util.List;
import java.util.Locale;

/**
 * ui-hud's crew window from the player's crew ({@link CrewCareer}), the docked station's hiring
 * pool ({@link CrewHiring}) and the prisoners aboard ({@link Prisoners}). Crew and prisoners are
 * named to the window by their entity's identity, which holds for as long as they are aboard.
 */
public class CrewRoster implements CrewProvider {
    /**
     * What a medic is worth, after the role in the hiring list (round 7): "Medic (2.5x care, none
     * aboard)". Anyone can treat the wounded, but slower ({@link CrewDef#UNTRAINED_CARE}).
     */
    static final String MEDIC_NOTE = String.format(Locale.ROOT, " (%.1fx care", 1f / CrewDef.UNTRAINED_CARE);

    private final GameWorld world;

    public CrewRoster(GameWorld world) {
        this.world = world;
    }

    @Override
    public void collect(CrewSheet out) {
        Entity ship = world.getPlayer();
        if (ship == null) return;
        out.shipName = shipName(ship);
        Entity station = world.crewHiring.hiringStation();
        List<Entity> crew = world.crewCareer.ownCrew(ship);
        for (Entity e : crew) {
            CrewMemberComponent c = world.crew.crew(e);
            CrewRecord r = c.record();
            Member m = out.addMember();
            m.id = id(e);
            m.name = c.name;
            m.role = CrewHiring.roleName(c.role);
            m.skills = r.describeSkills();
            m.condition = CrewCareer.condition(c);
            m.morale = String.format(Locale.ROOT, "Morale %.0f%%%s", r.morale * 100f,
                    r.morale < CrewRecord.LOW_MORALE ? " (slacking)" : "");
            m.wage = r.wage > 0 ? String.format(Locale.ROOT, "%,.0f cr/wk", r.wage) : "Service";
            m.history = CrewCareer.history(r);
            m.dismissable = station != null;
        }
        for (Entity e : world.prisoners.aboard(ship)) {
            CrewMemberComponent c = world.crew.crew(e);
            Prisoner p = out.addPrisoner();
            p.id = id(e);
            p.name = c.name;
            String faction = world.standing.factions().name(c.faction);
            p.detail = (faction == null ? "Unknown" : faction) + ", " + CrewHiring.roleName(c.role).toLowerCase(Locale.ROOT);
            String option = world.prisoners.option(e);
            p.option = option == null ? "" : Character.toUpperCase(option.charAt(0)) + option.substring(1);
            p.offersToJoin = world.prisoners.offersToJoin(e);
            p.condition = CrewMedical.condition(c);
        }
        Entity taken = world.prisoners.takenAlongside();
        out.prisonersAlongside = taken != null && !world.prisoners.aboard(taken).isEmpty();
        out.suits = world.medical.suitsLine();
        out.summary = String.format(Locale.ROOT, "%d / %d berths, %,.0f cr/wk in wages, %d medical stores",
                world.crewHiring.aboard(ship), CrewHiring.berths(ship), world.crewCareer.weeklyWages(ship), world.medical.stores());
        if (station != null) {
            String uuid = station.getComponent(UuidComponent.class).id;
            var node = world.macroSim.station(uuid);
            out.stationName = node == null || node.displayName == null ? "Station" : node.displayName;
            boolean medic = false;
            for (Entity e : crew) medic |= world.crew.crew(e).role == CrewRole.MEDIC;
            for (CrewHiring.Candidate c : world.crewHiring.pool(uuid)) {
                Hire h = out.addHire();
                h.key = c.key;
                h.name = c.name;
                h.role = CrewHiring.roleName(c.role) + (c.role != CrewRole.MEDIC ? "" : medic ? MEDIC_NOTE + ")" : MEDIC_NOTE + ", none aboard)");
                h.skills = c.record.describeSkills();
                h.terms = String.format(Locale.ROOT, "%,.0f cr/wk, %,.0f cr to sign", c.record.wage, c.signing);
            }
        }
    }

    @Override
    public String hire(String key) {
        String why = world.crewHiring.hire(key);
        return why == null ? "Hired" : why;
    }

    @Override
    public String dismiss(long id) {
        Entity e = find(id);
        if (e == null) return "No longer aboard";
        String name = world.crew.crew(e).name;
        String why = world.crewHiring.dismiss(e);
        return why == null ? name + " leaves the ship" : why;
    }

    @Override
    public String toggleSuits() {
        return world.medical.toggleSuits();
    }

    @Override
    public String takePrisoners() {
        String why = world.prisoners.takeAboard();
        return why == null ? "Prisoners brought aboard" : why;
    }

    @Override
    public String settlePrisoner(long id) {
        Entity e = find(id);
        if (e == null) return "No longer aboard";
        String why = world.prisoners.settle(e);
        return why == null ? "Done" : why;
    }

    @Override
    public String recruit(long id) {
        Entity e = find(id);
        if (e == null) return "No longer aboard";
        String why = world.prisoners.recruit(e);
        return why == null ? "They join the crew" : why;
    }

    private static long id(Entity e) {
        return System.identityHashCode(e);
    }

    private Entity find(long id) {
        ImmutableArray<Entity> all = world.crew.crewEntities();
        for (int i = 0; i < all.size(); i++) if (id(all.get(i)) == id) return all.get(i);
        return null;
    }

    /** The ship type's name from data, else "ship". */
    private String shipName(Entity ship) {
        DefRefComponent ref = ship.getComponent(DefRefComponent.class);
        if (ref != null && ref.defId != null && world.getContent() != null) {
            ShipDef def = world.getContent().defs.get(ref.defType, ref.defId, ShipDef.class);
            if (def != null && def.name != null && !def.name.isEmpty()) return def.name;
        }
        return "ship";
    }
}
