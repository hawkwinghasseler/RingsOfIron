package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaEvent;
import com.ringsofiron.aicrew.EvaOrders;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.coreengine.components.TransientComponent;
import com.ringsofiron.shipops.Boarding;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.systems.HudRenderSystem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.physicsexterior.PhysicsManager;

/**
 * The player's crew out on EVA ({@link EvaSystem}), on the game's side. The crew module moves them,
 * works the airlocks and swings them on their tethers; here the exterior world's floating origin is
 * hooked up so its shifts carry them along, rails warp waits while any of them are out (a ship
 * jumping along its orbit would leave them where they were), and what happens to them out there
 * comes up as notices. Anyone adrift gets a marker on the HUD, so the ship can find them and come
 * back for them. Someone left behind costs the rest of the crew morale, as a death does. The status
 * line warns of anyone in the way of the main engines' exhaust ({@link
 * com.ringsofiron.aicrew.EngineExhaust}), before a burn. The player's orders on EVA ({@link
 * EvaOrders}) are given here for the crew window: the ship's hold on going out, and sending one
 * out, calling one in and letting one out again.
 */
public final class Spacewalks {
    /** Start of every refusal, for tests and the screen. */
    public static final String REFUSAL = "Crew are out on EVA";
    /** Caught hold again after this long adrift, seconds, the notice says how long. */
    static final float LONG_ADRIFT = 30f;

    private final GameContext ctx;
    private final CrewService crew;
    private final CrewCareer crewCareer;
    private final PhysicsManager physics;
    /** The marker on the HUD of each of the player's crew adrift. */
    private final Map<Entity, Entity> markers = new IdentityHashMap<>();
    private final float[] range = new float[2];

    public Spacewalks(GameContext ctx, CrewService crew, CrewCareer crewCareer, PhysicsManager physics) {
        this.ctx = ctx;
        this.crew = crew;
        this.crewCareer = crewCareer;
        this.physics = physics;
        crew.eva.attach(physics);
        crew.eva.events.add((signal, e) -> onEvent(e));
        ctx.engine.addRailsWarpGuard(this::railsWarpBlockedReason);
        ctx.engine.addSystem(new PlaceMarkers());
    }

    /** Start of the refusal while some of the crew are aboard a craft with no dock to their own. */
    public static final String ACROSS = "Crew are across on another ship";

    /** One of the player's crew aboard a craft their own isn't docked to (gone over on EVA), or null. */
    public Entity awayAcross() {
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (!ours(e) || !crew.crew(e).isAlive()) continue;
            Entity at = crew.position(e).ship, home = crew.homeOf(e);
            if (at != null && home != null && at != home && !crew.joined(at, home)) return e;
        }
        return null;
    }

    /**
     * Puts each marker over the one adrift it marks, once the frame's physics has stepped and just
     * before the HUD draws. A marker has no body, so every physics step carries it back by the
     * bubble's travel, as if fixed to the planet: placed any earlier, it would be drawn that far off
     * them (at orbital speed, a hundred meters and more a frame).
     */
    private final class PlaceMarkers extends EntitySystem {
        PlaceMarkers() {
            super(HudRenderSystem.DEFAULT_PRIORITY - 1);
        }

        @Override
        public void update(float deltaTime) {
            for (Map.Entry<Entity, Entity> m : markers.entrySet()) place(m.getKey(), m.getValue());
        }
    }

    /** Puts marker {@code m} where {@code e} is, both in the bubble's meters. */
    private static void place(Entity e, Entity m) {
        EvaComponent eva = EvaSystem.of(e);
        if (eva == null || !eva.worldValid) return;
        TransformComponent t = m.getComponent(TransformComponent.class);
        t.x = eva.wx;
        t.y = eva.wy;
    }

    /**
     * The player's crew on an EVA: outside, or in an airlock on the way out or back in; not the
     * body of one who died adrift, who isn't coming back.
     */
    public List<Entity> onEva() {
        List<Entity> out = new ArrayList<>();
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (EvaSystem.of(e) != null && ours(e) && !(isAdrift(e) && crew.crew(e).state == CrewState.DEAD)) out.add(e);
        }
        return out;
    }

    /**
     * True if {@code e} is off the hull with no line, and no shipmate has hold of them: not on their
     * way across to another craft, unless thrown off or stranded on the way.
     */
    public static boolean isAdrift(Entity e) {
        EvaComponent eva = EvaSystem.of(e);
        return eva != null && eva.phase == EvaComponent.Phase.FREE && !eva.tethered && eva.heldBy == null
                && (!eva.crossing || eva.fell || eva.stranded);
    }

    /** Why rails warp waits, naming who is out; null when nobody is. */
    public String railsWarpBlockedReason() {
        List<Entity> out = onEva();
        if (out.isEmpty()) {
            // Boarders gone over on EVA wait to come back the same way
            Entity away = awayAcross();
            return away == null ? null : ACROSS + ": " + crew.crew(away).name + " is aboard "
                    + Boarding.name(crew.position(away).ship);
        }
        if (out.size() == 1) {
            Entity e = out.get(0);
            return REFUSAL + ": " + crew.crew(e).name + (isAdrift(e) ? " is adrift" : " is outside");
        }
        return REFUSAL + ": " + out.size() + " of the crew are outside";
    }

    /**
     * The status panel's line while any of the player's crew are on an EVA: who, and where (in an
     * airlock, on the hull, off it on their line, or adrift, how far off and how long their air will
     * last), with a warning while any are outside: a hard enough burn throws those on the hull off it,
     * and can part the line of those already off it; and anyone in the way of the main engines'
     * exhaust, whom a burn would burn, or whose line it would burn through. Null when nobody is out.
     */
    public String statusLine() {
        List<Entity> out = onEva();
        if (out.isEmpty()) return null;
        StringBuilder s = new StringBuilder("EVA: ");
        boolean onHull = false, onLine = false;
        List<String> behind = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            Entity e = out.get(i);
            EvaComponent eva = EvaSystem.of(e);
            String name = crew.crew(e).name;
            if (i > 0) s.append("; ");
            s.append(name).append(' ').append(where(e, eva));
            onHull |= eva.phase == EvaComponent.Phase.ON_HULL;
            onLine |= eva.phase == EvaComponent.Phase.FREE && eva.tethered;
            if (crew.eva.behindEngines(e)) behind.add(name);
        }
        if (onHull) s.append(". A hard burn can throw them off the hull");
        else if (onLine) s.append(". A hard burn can part the line");
        if (!behind.isEmpty()) {
            s.append(". ").append(String.join(" and ", behind)).append(behind.size() == 1 ? " is" : " are")
                    .append(" in the way of the main engines' exhaust");
        }
        return s.toString();
    }

    private String where(Entity e, EvaComponent eva) {
        switch (eva.phase) {
            case LEAVING:
                return "in the airlock, going out";
            case RETURNING:
                return "in the airlock, coming in";
            case ON_HULL:
                return eva.tethered ? "on the hull, " + Math.round(eva.lineOut) + " m of line out" : "on the hull, no line";
            default:
                if (eva.crossing && !eva.fell && !eva.stranded) return "crossing on the jetpack";
                if (eva.tethered) {
                    String line = "off the hull on " + Math.round(eva.lineOut) + " m of line";
                    CrewMemberComponent towed = eva.towing == null ? null : crew.crew(eva.towing);
                    return towed == null ? line : line + ", bringing " + towed.name + " in";
                }
                if (eva.heldBy != null) return "in tow, being brought in";
                return adrift(e, eva);
        }
    }

    /** "adrift 1.2 km off, drifting away at 3 m/s, 31 min of air: waiting to be picked up". */
    private String adrift(Entity e, EvaComponent eva) {
        CrewMemberComponent c = crew.crew(e);
        StringBuilder s = new StringBuilder("adrift");
        if (crew.eva.rangeOf(e, range)) {
            s.append(' ').append(distance(range[0])).append(" off");
            float closing = range[1];
            if (Math.abs(closing) >= 0.5f) {
                s.append(closing > 0f ? ", closing at " : ", drifting away at ").append(speed(Math.abs(closing)));
            }
        }
        s.append(", ").append(Math.max(0, Math.round(c.suitAir / 60f))).append(" min of air");
        if (!c.isConscious()) s.append(", out cold");
        else if (eva.stranded && !eva.jetting) s.append(": waiting to be picked up");
        else if (eva.jetting) s.append(": jetting back");
        return s.toString();
    }

    static String distance(float meters) {
        return meters >= 1000f ? String.format(Locale.ROOT, "%.1f km", meters / 1000f) : Math.round(meters) + " m";
    }

    static String speed(float mps) {
        return mps >= 10f ? Math.round(mps) + " m/s" : String.format(Locale.ROOT, "%.1f m/s", mps);
    }

    /**
     * Once a frame, after the engine's update: gives each of the player's crew adrift, alive, a
     * marker on the HUD ({@link PlaceMarkers} keeps it over them), and drops the markers of those
     * who aren't any more.
     */
    public void update() {
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (!ours(e) || !isAdrift(e) || crew.crew(e).state == CrewState.DEAD) continue;
            if (!EvaSystem.of(e).worldValid || markers.containsKey(e)) continue;
            Entity m = ctx.engine.createEntity();
            m.add(new TransformComponent());
            m.add(new TransientComponent());
            m.add(new HudMarkerComponent(crew.crew(e).name + ", adrift", HudMarkerComponent.Kind.FRIENDLY).radius(1f));
            place(e, m);
            ctx.engine.addEntity(m);
            markers.put(e, m);
        }
        for (Iterator<Map.Entry<Entity, Entity>> it = markers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Entity> m = it.next();
            Entity e = m.getKey();
            boolean keep = e.getComponent(CrewMemberComponent.class) != null && isAdrift(e)
                    && crew.crew(e).state != CrewState.DEAD && all.contains(e, true);
            if (keep) continue;
            ctx.engine.removeEntity(m.getValue());
            it.remove();
        }
    }

    /** The crew window's line on whether the crew go out on EVA on their own; empty with no ship. */
    public String holdLine() {
        ShipTaskBoardComponent board = crew.board(ctx.player());
        if (board == null) return "";
        return board.evaHeld ? "EVA: held in, only crew sent out go out" : "EVA: crew go out for work outside as it comes";
    }

    /** Holds the player's crew in from EVA, or lets them go out again; returns what came of it. */
    public String toggleHold() {
        Entity ship = ctx.player();
        if (crew.board(ship) == null) return "";
        return EvaOrders.toggleHold(crew, ship) ? "Nobody goes out on EVA unless sent, and anyone out is called back in"
                : "Crew go out on EVA for work outside as it comes";
    }

    /**
     * What the player can order {@code e} to do about EVA, for the crew window: "Call in" while they
     * are out or sent out, "Let out" while kept in, "Send out" while there is work outside for them,
     * or nothing.
     */
    public String orderFor(Entity e) {
        CrewMemberComponent c = crew.crew(e);
        if (c == null || !ours(e)) return "";
        switch (c.evaOrder) {
            case IN:
                return "Let out";
            case OUT:
                return "Call in";
            default:
                if (EvaSystem.of(e) != null) return c.state == CrewState.DEAD ? "" : "Call in";
                return c.isConscious() && c.wearsSuits() && EvaOrders.workFor(crew, e) ? "Send out" : "";
        }
    }

    /** Gives {@code e} the order {@link #orderFor} offers; returns what came of it. */
    public String order(Entity e) {
        CrewMemberComponent c = crew.crew(e);
        if (c == null) return "No longer aboard";
        switch (orderFor(e)) {
            case "Send out": {
                String why = EvaOrders.sendOut(crew, e);
                return why != null ? why : c.name + " is going out for the work outside";
            }
            case "Call in": {
                boolean out = EvaSystem.of(e) != null;
                EvaOrders.callIn(crew, e);
                return c.name + (out ? " is coming back in, and stays in till let out" : " stays in till let out");
            }
            case "Let out":
                EvaOrders.letOut(crew, e);
                return c.name + " goes out on EVA for work as it comes again";
            default:
                return "";
        }
    }

    /** The marker over {@code e} adrift, or null: for tests. */
    Entity marker(Entity e) {
        return markers.get(e);
    }

    private void onEvent(EvaEvent e) {
        if (!ours(e.crew)) return;
        String name = crew.crew(e.crew).name;
        switch (e.kind) {
            case OUT:
                ctx.notices.post(name + " goes out on EVA", NoticeLog.Kind.INFO);
                break;
            case BACK:
                ctx.notices.post(name + " is back in from EVA", NoticeLog.Kind.INFO);
                break;
            case CROSSING:
                if (e.ship == crew.homeOf(e.crew)) ctx.notices.post(name + " pushes off across to the other ship", NoticeLog.Kind.INFO);
                else ctx.notices.post(name + " pushes off to come back across", NoticeLog.Kind.INFO);
                break;
            case ABOARD:
                ctx.notices.post(name + " is aboard " + Boarding.name(e.ship) + " through its airlock", NoticeLog.Kind.INFO);
                break;
            case LOST_GRIP:
                ctx.notices.post(name + " is thrown off the hull", NoticeLog.Kind.WARNING);
                break;
            case CAUGHT_HULL:
                if (e.amount >= LONG_ADRIFT) {
                    ctx.notices.post(name + " has caught hold of the hull again after " + duration(e.amount) + " adrift",
                            NoticeLog.Kind.INFO);
                } else {
                    ctx.notices.post(name + " has caught hold of the hull again", NoticeLog.Kind.INFO);
                }
                break;
            case TETHER_SNAPPED:
                ctx.notices.post(name + "'s tether has parted", NoticeLog.Kind.WARNING);
                break;
            case EXHAUST:
                ctx.notices.post(name + " is caught in the main engines' exhaust", NoticeLog.Kind.WARNING);
                break;
            case TETHER_BURNED:
                ctx.notices.post(name + "'s tether has burned through in the main engines' exhaust", NoticeLog.Kind.WARNING);
                break;
            case ADRIFT:
                ctx.notices.post(name + " is adrift " + distance(e.amount) + " off with no way back alone: bring the ship to them",
                        NoticeLog.Kind.WARNING);
                break;
            case AIR_OUT:
                ctx.notices.post(name + "'s suit is out of air", NoticeLog.Kind.WARNING);
                break;
            case TAKEN_IN_TOW:
                if (e.by != null && crew.crew(e.by) != null) {
                    ctx.notices.post(crew.crew(e.by).name + " has hold of " + name + " and is bringing them in", NoticeLog.Kind.INFO);
                }
                break;
            case LEFT_BEHIND:
                leftBehind(e, name);
                break;
            default:
                break;
        }
    }

    static String duration(float seconds) {
        int s = Math.round(seconds);
        return s < 120 ? s + " s" : Math.round(s / 60f) + " min";
    }

    /** Adrift beyond any way back: gone, and the rest of the crew feel it as they would a death. */
    private void leftBehind(EvaEvent e, String name) {
        if (crew.crew(e.crew).state == CrewState.DEAD) {
            // Their death has been felt already
            ctx.notices.post(name + "'s body is left behind, adrift", NoticeLog.Kind.INFO);
            return;
        }
        ctx.notices.post(name + " is left behind, adrift", NoticeLog.Kind.WARNING);
        for (Entity other : crewCareer.ownCrew(e.ship)) {
            if (other != e.crew) crew.crew(other).record().changeMorale(CrewCareer.DEATH_MORALE);
        }
    }

    /** True for the player's own crew, not the figure they walk about as or anyone held. */
    private boolean ours(Entity e) {
        CrewMemberComponent c = e == null ? null : crew.crew(e);
        return c != null && c.faction == FactionStanding.PLAYER && !c.held && e.getComponent(PlayerAvatarComponent.class) == null;
    }
}
