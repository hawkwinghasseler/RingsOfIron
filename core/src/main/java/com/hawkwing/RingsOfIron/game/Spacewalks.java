package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaEvent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's crew out on EVA ({@link EvaSystem}), on the game's side. The crew module moves them,
 * works the airlocks and swings them on their tethers; here the exterior world's floating origin is
 * hooked up so its shifts carry them along, rails warp waits while any of them are out (a ship
 * jumping along its orbit would leave them where they were), and what happens to them out there
 * comes up as notices. Someone left behind costs the rest of the crew morale, as a death does.
 */
public final class Spacewalks {
    /** Start of every refusal, for tests and the screen. */
    public static final String REFUSAL = "Crew are out on EVA";

    private final GameWorld world;

    public Spacewalks(GameWorld world) {
        this.world = world;
        world.crew.eva.attach(world.physics);
        world.crew.eva.events.add((signal, e) -> onEvent(e));
        world.engine.addRailsWarpGuard(this::railsWarpBlockedReason);
    }

    /** Start of the refusal while some of the crew are aboard a craft with no dock to their own. */
    public static final String ACROSS = "Crew are across on another ship";

    /** One of the player's crew aboard a craft their own isn't docked to (gone over on EVA), or null. */
    public Entity awayAcross() {
        ImmutableArray<Entity> all = world.crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (!ours(e) || !world.crew.crew(e).isAlive()) continue;
            Entity at = world.crew.position(e).ship, home = world.crew.homeOf(e);
            if (at != null && home != null && at != home && !world.crew.joined(at, home)) return e;
        }
        return null;
    }

    /** The player's crew on an EVA: outside, or in an airlock on the way out or back in. */
    public List<Entity> onEva() {
        List<Entity> out = new ArrayList<>();
        ImmutableArray<Entity> all = world.crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (EvaSystem.of(e) != null && ours(e)) out.add(e);
        }
        return out;
    }

    /** Why rails warp waits, naming who is out; null when nobody is. */
    public String railsWarpBlockedReason() {
        List<Entity> out = onEva();
        if (out.isEmpty()) {
            // Boarders gone over on EVA wait to come back the same way
            Entity away = awayAcross();
            return away == null ? null : ACROSS + ": " + world.crew.crew(away).name + " is aboard "
                    + Boarding.name(world.crew.position(away).ship);
        }
        if (out.size() == 1) return REFUSAL + ": " + world.crew.crew(out.get(0)).name + " is outside";
        return REFUSAL + ": " + out.size() + " of the crew are outside";
    }

    /**
     * The status panel's line while any of the player's crew are on an EVA: who, and where (in an
     * airlock, on the hull, off it on their line, or adrift), with a warning while any are outside:
     * a hard enough burn throws those on the hull off it, and can part the line of those already
     * off it. Null when nobody is out.
     */
    public String statusLine() {
        List<Entity> out = onEva();
        if (out.isEmpty()) return null;
        StringBuilder s = new StringBuilder("EVA: ");
        boolean onHull = false, onLine = false;
        for (int i = 0; i < out.size(); i++) {
            EvaComponent eva = EvaSystem.of(out.get(i));
            if (i > 0) s.append("; ");
            s.append(world.crew.crew(out.get(i)).name).append(' ').append(where(eva));
            onHull |= eva.phase == EvaComponent.Phase.ON_HULL;
            onLine |= eva.phase == EvaComponent.Phase.FREE && eva.tethered;
        }
        if (onHull) s.append(". A hard burn can throw them off the hull");
        else if (onLine) s.append(". A hard burn can part the line");
        return s.toString();
    }

    private static String where(EvaComponent eva) {
        switch (eva.phase) {
            case LEAVING:
                return "in the airlock, going out";
            case RETURNING:
                return "in the airlock, coming in";
            case ON_HULL:
                return eva.tethered ? "on the hull, " + Math.round(eva.lineOut) + " m of line out" : "on the hull, no line";
            default:
                if (eva.crossing && !eva.fell) return "crossing on the jetpack";
                return eva.tethered ? "off the hull on " + Math.round(eva.lineOut) + " m of line" : "adrift, no line";
        }
    }

    private void onEvent(EvaEvent e) {
        if (!ours(e.crew)) return;
        String name = world.crew.crew(e.crew).name;
        switch (e.kind) {
            case OUT:
                world.notices.post(name + " goes out on EVA", NoticeLog.Kind.INFO);
                break;
            case BACK:
                world.notices.post(name + " is back in from EVA", NoticeLog.Kind.INFO);
                break;
            case CROSSING:
                if (e.ship == world.crew.homeOf(e.crew)) world.notices.post(name + " pushes off across to the other ship", NoticeLog.Kind.INFO);
                else world.notices.post(name + " pushes off to come back across", NoticeLog.Kind.INFO);
                break;
            case ABOARD:
                world.notices.post(name + " is aboard " + Boarding.name(e.ship) + " through its airlock", NoticeLog.Kind.INFO);
                break;
            case LOST_GRIP:
                world.notices.post(name + " is thrown off the hull", NoticeLog.Kind.WARNING);
                break;
            case CAUGHT_HULL:
                world.notices.post(name + " has caught hold of the hull again", NoticeLog.Kind.INFO);
                break;
            case TETHER_SNAPPED:
                world.notices.post(name + "'s tether has parted", NoticeLog.Kind.WARNING);
                break;
            case LEFT_BEHIND:
                leftBehind(e, name);
                break;
            default:
                break;
        }
    }

    /** Adrift beyond any way back: gone, and the rest of the crew feel it as they would a death. */
    private void leftBehind(EvaEvent e, String name) {
        world.notices.post(name + " is left behind, adrift", NoticeLog.Kind.WARNING);
        for (Entity other : world.crewCareer.ownCrew(e.ship)) {
            if (other != e.crew) world.crew.crew(other).record().changeMorale(CrewCareer.DEATH_MORALE);
        }
    }

    /** True for the player's own crew, not the figure they walk about as or anyone held. */
    private boolean ours(Entity e) {
        CrewMemberComponent c = e == null ? null : world.crew.crew(e);
        return c != null && c.faction == FactionStanding.PLAYER && !c.held && e.getComponent(PlayerAvatarComponent.class) == null;
    }
}
