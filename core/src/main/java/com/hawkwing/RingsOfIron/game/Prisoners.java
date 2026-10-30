package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.defs.FactionDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.macrosim.MacroSimulation;

/**
 * What the player does with the crews of ships they take ({@link Boarding} holds them aboard the
 * taken ship). Latched to a taken ship, the player brings its prisoners aboard their own
 * ({@link #takeAboard}), as many as there are berths for ({@link CrewHiring#berths}). Docked with
 * clearance, they can:
 * <ul>
 *   <li><b>hand over</b> prisoners of a faction the sponsor fights (outlaws among them) at one of the
 *       sponsor's stations, for {@link #HANDOVER_PAY} and {@link #HANDOVER_STANDING} with the
 *       sponsor each;</li>
 *   <li><b>ransom</b> prisoners back to their own faction at its station, for {@link #RANSOM_PAY}
 *       each and {@link #RANSOM_STANDING} standing with it;</li>
 *   <li><b>release</b> them at any station, for nothing, or {@link #RELEASE_STANDING} with their
 *       faction if it is theirs.</li>
 * </ul>
 * Some outlaws offer to join ({@link #offersToJoin}, one in {@link #JOIN_ODDS}); one who does
 * joins the player's crew green ({@link #JOIN_SKILL}) on a low wage. Prisoners are crew entities
 * like any other, so they save with the game.
 */
public class Prisoners {
    public static final float HANDOVER_PAY = 400f, HANDOVER_STANDING = 1f;
    public static final float RANSOM_PAY = 600f, RANSOM_STANDING = 2f;
    public static final float RELEASE_STANDING = 1f;
    /** One outlaw in this many offers to join. */
    public static final int JOIN_ODDS = 3;
    /** Every skill of an outlaw who joins. */
    public static final float JOIN_SKILL = 0.3f;
    /** Morale of an outlaw who joins. */
    public static final float JOIN_MORALE = 0.5f;

    private final GameContext ctx;
    private final CrewService crew;
    private final DockingSystem docking;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final CrewHiring crewHiring;

    Prisoners(GameContext ctx, CrewService crew, DockingSystem docking, MacroSimulation macroSim, FactionStanding standing, CrewHiring crewHiring) {
        this.ctx = ctx;
        this.crew = crew;
        this.docking = docking;
        this.macroSim = macroSim;
        this.standing = standing;
        this.crewHiring = crewHiring;
    }

    /** Prisoners held aboard {@code ship}: crew of other factions kept there. */
    public List<Entity> aboard(Entity ship) {
        List<Entity> out = new ArrayList<>();
        if (ship == null) return out;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            // Survivors of a rescue are carried, not held
            if (c.held && c.isAlive() && crew.position(e).ship == ship && e.getComponent(SurvivorComponent.class) == null) out.add(e);
        }
        return out;
    }

    /** The taken ship the player's ship is latched to, or null. */
    public Entity takenAlongside() {
        Entity ship = ctx.player();
        Entity other = ship == null ? null : docking.dockedTo(ship);
        BoardedComponent b = other == null ? null : other.getComponent(BoardedComponent.class);
        if (b == null || !b.taken || b.by == null || b.by.get(ctx.engine.getUuidRegistry()) != ship) return null;
        return other;
    }

    /** Brings the prisoners of the taken ship alongside aboard the player's, as berths allow. Null on success, else why not. */
    public String takeAboard() {
        Entity taken = takenAlongside();
        if (taken == null) return "Latch onto a ship you have taken first";
        List<Entity> held = aboard(taken);
        if (held.isEmpty()) return "No one is held aboard it";
        Entity ship = ctx.player();
        int room = CrewHiring.berths(ship) - crewHiring.aboard(ship);
        if (room <= 0) return "No berth free for prisoners";
        int moved = 0;
        for (Entity e : held) {
            if (moved >= room) break;
            int[] tile = crewHiring.freeTile(ship);
            if (tile == null) break;
            crew.releaseTask(e);
            GridPositionComponent p = crew.position(e);
            p.stop();
            p.crossing = null;
            p.setShip(ship);
            p.setTile(tile[0], tile[1]);
            moved++;
        }
        BoardedComponent b = taken.getComponent(BoardedComponent.class);
        b.prisoners = Math.max(0, b.prisoners - moved);
        ctx.notices.post(moved + (moved == 1 ? " prisoner" : " prisoners") + " brought aboard"
                + (moved < held.size() ? ": no berths for the rest" : ""));
        return null;
    }

    /** The station the player is docked at with clearance, or null. */
    private Entity station() {
        Entity ship = ctx.player();
        if (ship == null || !docking.dockedWithClearance(ship)) return null;
        Entity s = docking.dockedTo(ship);
        UuidComponent u = s == null ? null : s.getComponent(UuidComponent.class);
        return u == null || macroSim.station(u.id) == null ? null : s;
    }

    /** Whether prisoners of {@code faction} may be handed over to the sponsor: outlaws, or a faction it fights. */
    public boolean wanted(int faction) {
        FactionStanding s = standing;
        FactionDef def = s.factions().def(faction);
        if (def == null) return false;
        return def.hostileToAll || s.factions().atWar(s.sponsor(), faction);
    }

    /** What can be done with prisoner {@code e} here: "hand over", "ransom", "release", or null if not docked. */
    public String option(Entity e) {
        Entity station = station();
        if (station == null) return null;
        int here = standing.factionOf(station);
        int theirs = crew.crew(e).faction;
        if (here == standing.sponsor() && wanted(theirs)) return "hand over";
        if (here == theirs) return "ransom";
        return "release";
    }

    /**
     * Hands over, ransoms or releases (whichever {@link #option} says) every prisoner aboard. Null
     * on success, else why not.
     */
    public String settleAll() {
        Entity ship = ctx.player();
        if (station() == null) return "Dock at a station to give up prisoners";
        List<Entity> held = aboard(ship);
        if (held.isEmpty()) return "No prisoners aboard";
        for (Entity e : held) settle(e);
        return null;
    }

    /** Hands over, ransoms or releases prisoner {@code e}. Null on success, else why not. */
    public String settle(Entity e) {
        Entity ship = ctx.player();
        String how = option(e);
        if (how == null) return "Dock at a station to give up prisoners";
        if (!aboard(ship).contains(e)) return "Not held aboard";
        CrewMemberComponent c = crew.crew(e);
        FactionStanding s = standing;
        TraderComponent account = ship.getComponent(TraderComponent.class);
        String who = s.factions().name(c.faction);
        double pay = 0;
        switch (how) {
            case "hand over":
                pay = HANDOVER_PAY;
                s.reward(s.sponsor(), HANDOVER_STANDING, "handed over prisoners");
                break;
            case "ransom":
                pay = RANSOM_PAY;
                s.reward(c.faction, RANSOM_STANDING, "ransomed their crew");
                break;
            default:
                if (s.factionOf(station()) == c.faction) s.reward(c.faction, RELEASE_STANDING, "released their crew");
                break;
        }
        if (account != null) account.credits += pay;
        crew.releaseTask(e);
        ctx.engine.getEcsEngine().removeEntity(e);
        String verb = how.equals("hand over") ? "handed over" : how.equals("ransom") ? "ransomed" : "released";
        ctx.notices.post(String.format(Locale.ROOT, "%s (%s) %s%s", c.name, who == null ? "unknown" : who, verb,
                pay > 0 ? String.format(Locale.ROOT, ": +%,.0f cr", pay) : ""));
        return null;
    }

    /**
     * Hands over to {@code station} the prisoners still held aboard {@code prize} as it is sold or
     * turned in: those the sponsor wants pay {@link #HANDOVER_PAY} and {@link #HANDOVER_STANDING} each at a
     * sponsor station, as if brought aboard and handed over; the rest go with the ship for nothing.
     * Returns the credits paid, which the caller adds.
     */
    public double handOverWithPrize(Entity prize, Entity station) {
        FactionStanding s = standing;
        boolean sponsors = station != null && s.factionOf(station) == s.sponsor();
        double pay = 0;
        int wantedCount = 0;
        for (Entity e : aboard(prize)) {
            if (!sponsors || !wanted(crew.crew(e).faction)) continue;
            pay += HANDOVER_PAY;
            wantedCount++;
        }
        if (wantedCount > 0) s.reward(s.sponsor(), HANDOVER_STANDING * wantedCount, "handed over prisoners");
        return pay;
    }

    /** Whether prisoner {@code e} offers to join the player's crew: some outlaws do. */
    public boolean offersToJoin(Entity e) {
        CrewMemberComponent c = crew.crew(e);
        FactionDef def = c == null ? null : standing.factions().def(c.faction);
        if (def == null || !def.hostileToAll) return false;
        return Math.floorMod((c.name + c.role).hashCode(), JOIN_ODDS) == 0;
    }

    /** Takes prisoner {@code e}, who offered, into the player's crew. Null on success, else why not. */
    public String recruit(Entity e) {
        Entity ship = ctx.player();
        if (!aboard(ship).contains(e)) return "Not held aboard";
        if (!offersToJoin(e)) return "They won't serve under you";
        CrewMemberComponent c = crew.crew(e);
        c.held = false;
        c.faction = FactionStanding.PLAYER;
        c.setHome(ship);
        CrewRecord r = c.record();
        r.gunnery = r.tracking = r.engineering = r.marine = JOIN_SKILL;
        r.morale = JOIN_MORALE;
        r.wage = CrewCareer.wageFor(c.role, r);
        r.applyTo(c);
        ctx.notices.post(String.format(Locale.ROOT, "%s joins your crew for %,.0f cr a week", c.name, r.wage));
        return null;
    }
}
