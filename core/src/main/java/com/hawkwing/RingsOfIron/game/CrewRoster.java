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
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.macrosim.MacroSimulation;

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

    private final GameContext ctx;
    private final CrewService crewService;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final CrewCareer crewCareer;
    private final CrewHiring crewHiring;
    private final CrewMedical medical;
    private final Prisoners prisoners;
    private final Spacewalks spacewalks;

    public CrewRoster(GameContext ctx, CrewService crewService, MacroSimulation macroSim, FactionStanding standing, CrewCareer crewCareer, CrewHiring crewHiring, CrewMedical medical, Prisoners prisoners, Spacewalks spacewalks) {
        this.ctx = ctx;
        this.crewService = crewService;
        this.macroSim = macroSim;
        this.standing = standing;
        this.crewCareer = crewCareer;
        this.crewHiring = crewHiring;
        this.medical = medical;
        this.prisoners = prisoners;
        this.spacewalks = spacewalks;
    }

    @Override
    public void collect(CrewSheet out) {
        Entity ship = ctx.player();
        if (ship == null) return;
        out.shipName = shipName(ship);
        Entity station = crewHiring.hiringStation();
        List<Entity> crew = crewCareer.ownCrew(ship);
        for (Entity e : crew) {
            CrewMemberComponent c = crewService.crew(e);
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
            m.evaAction = spacewalks.orderFor(e);
        }
        for (Entity e : prisoners.aboard(ship)) {
            CrewMemberComponent c = crewService.crew(e);
            Prisoner p = out.addPrisoner();
            p.id = id(e);
            p.name = c.name;
            String faction = standing.factions().name(c.faction);
            p.detail = (faction == null ? "Unknown" : faction) + ", " + CrewHiring.roleName(c.role).toLowerCase(Locale.ROOT);
            String option = prisoners.option(e);
            p.option = option == null ? "" : Character.toUpperCase(option.charAt(0)) + option.substring(1);
            p.offersToJoin = prisoners.offersToJoin(e);
            p.condition = CrewMedical.condition(c);
        }
        Entity taken = prisoners.takenAlongside();
        out.prisonersAlongside = taken != null && !prisoners.aboard(taken).isEmpty();
        out.suits = medical.suitsLine();
        out.eva = spacewalks.holdLine();
        out.summary = String.format(Locale.ROOT, "%d / %d berths, %,.0f cr/wk in wages, %d medical stores",
                crewHiring.aboard(ship), CrewHiring.berths(ship), crewCareer.weeklyWages(ship), medical.stores());
        if (station != null) {
            String uuid = station.getComponent(UuidComponent.class).id;
            var node = macroSim.station(uuid);
            out.stationName = node == null || node.displayName == null ? "Station" : node.displayName;
            boolean medic = false;
            for (Entity e : crew) medic |= crewService.crew(e).role == CrewRole.MEDIC;
            for (CrewHiring.Candidate c : crewHiring.pool(uuid)) {
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
        String why = crewHiring.hire(key);
        return why == null ? "Hired" : why;
    }

    @Override
    public String dismiss(long id) {
        Entity e = find(id);
        if (e == null) return "No longer aboard";
        String name = crewService.crew(e).name;
        String why = crewHiring.dismiss(e);
        return why == null ? name + " leaves the ship" : why;
    }

    @Override
    public String toggleSuits() {
        return medical.toggleSuits();
    }

    @Override
    public String toggleEva() {
        return spacewalks.toggleHold();
    }

    @Override
    public String evaOrder(long id) {
        Entity e = find(id);
        return e == null ? "No longer aboard" : spacewalks.order(e);
    }

    @Override
    public String takePrisoners() {
        String why = prisoners.takeAboard();
        return why == null ? "Prisoners brought aboard" : why;
    }

    @Override
    public String settlePrisoner(long id) {
        Entity e = find(id);
        if (e == null) return "No longer aboard";
        String why = prisoners.settle(e);
        return why == null ? "Done" : why;
    }

    @Override
    public String recruit(long id) {
        Entity e = find(id);
        if (e == null) return "No longer aboard";
        String why = prisoners.recruit(e);
        return why == null ? "They join the crew" : why;
    }

    private static long id(Entity e) {
        return System.identityHashCode(e);
    }

    private Entity find(long id) {
        ImmutableArray<Entity> all = crewService.crewEntities();
        for (int i = 0; i < all.size(); i++) if (id(all.get(i)) == id) return all.get(i);
        return null;
    }

    /** The ship type's name from data, else "ship". */
    private String shipName(Entity ship) {
        DefRefComponent ref = ship.getComponent(DefRefComponent.class);
        if (ref != null && ref.defId != null && ctx.content() != null) {
            ShipDef def = ctx.content().defs.get(ref.defType, ref.defId, ShipDef.class);
            if (def != null && def.name != null && !def.name.isEmpty()) return def.name;
        }
        return "ship";
    }
}
