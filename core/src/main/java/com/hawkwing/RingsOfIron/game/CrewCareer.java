package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewQueries;
import com.ringsofiron.aicrew.CrewRecord;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the player's crew make of their service ({@link CrewRecord}): experience from what they do,
 * morale from how they are treated, and their wages.
 *
 * <ul>
 *   <li><b>Veterancy.</b> Gunners manning the weapons console learn from each hit the ship's guns
 *       land; engineers from every second of repairs, fire fighting, sealing breaches, forcing
 *       doors and yard work; marines from each small-arms hit, each enemy downed and each ship they
 *       board and take. Skills rise toward {@link CrewRecord#CAP} ever more slowly.</li>
 *   <li><b>Morale.</b> Drifts back toward {@link CrewRecord#STEADY_MORALE} by {@link #MORALE_DRIFT}
 *       a day. Unpaid wages cost {@link #UNPAID_MORALE}, a shipmate killed {@link #DEATH_MORALE}
 *       and crew lost in a rescue {@link #LOST_MORALE} each; a ship beaten or taken lifts it. Below
 *       {@link CrewRecord#LOW_MORALE} work slows.</li>
 *   <li><b>Wages.</b> Each payday, with the budget ({@link ServiceBudget#paydays}), every hired crew
 *       member's wage comes out of the ship's credits, as far as they go. Crew the service put
 *       aboard (the starting crew, and those requisitioned with a ship) draw no wage from the
 *       player: their wage is 0.</li>
 * </ul>
 * Everything is kept on the crew's records, so nothing here is saved; a load starts the morale
 * clock over ({@link #restartClock}).
 */
public class CrewCareer extends FixedLogicSystem {
    /** Seconds between updates. */
    static final float INTERVAL = 1f;
    /** Gunnery experience per hit of the ship's guns, for whoever mans the weapons console. */
    public static final float GUNNERY_XP_PER_HIT = 0.004f;
    /** Engineering experience per second of work. */
    public static final float ENGINEERING_XP_PER_SECOND = 0.0008f;
    /** Marine experience per small-arms hit and per enemy downed. */
    public static final float MARINE_XP_PER_HIT = 0.01f, MARINE_XP_PER_DOWN = 0.03f;
    /** Marine experience for boarding a ship that is taken. */
    public static final float MARINE_XP_PER_BOARDING = 0.05f;
    /** Morale regained (or lost, above steady) toward {@link CrewRecord#STEADY_MORALE} per in-game day. */
    public static final float MORALE_DRIFT = 0.05f;
    public static final float UNPAID_MORALE = -0.25f, PAID_MORALE = 0.02f;
    public static final float DEATH_MORALE = -0.1f, LOST_MORALE = -0.05f;
    public static final float WIN_MORALE = 0.05f;
    static final double DAY = 86_400;

    private final GameWorld world;
    private final CrewService crew;
    /** Who was alive at the last update, so a death is noticed once. */
    private final Map<Entity, Boolean> alive = new IdentityHashMap<>();
    private double last = Double.NaN;

    CrewCareer(GameWorld world) {
        super(INTERVAL);
        this.world = world;
        this.crew = world.crew;
        world.combatEvents.impacts.add((signal, hit) -> {
            if (hit.owner != null && hit.owner == world.getPlayer()) gunnersHit(hit.owner);
        });
        world.loop.beaten.add((signal, ship) -> {
            for (Entity e : ownCrew(world.getPlayer())) {
                CrewRecord r = record(e);
                r.shipsBeaten++;
                r.changeMorale(WIN_MORALE);
            }
        });
        world.boarding.taken.add((signal, target) -> taken(target));
        world.budget.paydays.add(this::payWages);
        world.rescue.rescues.add((signal, r) -> {
            if (r.lost <= 0) return;
            for (Entity e : ownCrew(r.newShip)) record(e).changeMorale(LOST_MORALE * r.lost);
        });
    }

    @Override
    protected void updateFixed(float deltaTime) {
        update();
    }

    /** Starts morale's drift from the next update, for a load: the time before it is not drifted. */
    public void restartClock() {
        last = Double.NaN;
    }

    /** Drains the crew's experience, moves morale and notices deaths. */
    public void update() {
        double now = world.engine.getSimSeconds();
        double days = Double.isNaN(last) ? 0 : Math.max(0, now - last) / DAY;
        last = now;
        Entity ship = world.getPlayer();
        List<Entity> own = ownCrew(ship);
        for (Entity e : own) {
            CrewMemberComponent c = crew.crew(e);
            CrewRecord r = c.record();
            boolean low = r.morale < CrewRecord.LOW_MORALE;
            boolean learned = drain(r);
            if (days > 0) drift(r, (float) days);
            if (learned || low != (r.morale < CrewRecord.LOW_MORALE)) r.applyTo(c);
        }
        noticeDeaths(ship);
    }

    /** Turns a record's pending work and fights into experience; true if any skill moved. */
    private static boolean drain(CrewRecord r) {
        boolean any = false;
        if (r.pendingWork > 0f) {
            r.workSeconds += r.pendingWork;
            r.gain(CrewRecord.Skill.ENGINEERING, r.pendingWork * ENGINEERING_XP_PER_SECOND);
            r.pendingWork = 0f;
            any = true;
        }
        if (r.pendingHits > 0 || r.pendingDowns > 0) {
            r.downed += r.pendingDowns;
            r.gain(CrewRecord.Skill.MARINE, r.pendingHits * MARINE_XP_PER_HIT + r.pendingDowns * MARINE_XP_PER_DOWN);
            r.pendingHits = r.pendingDowns = 0;
            any = true;
        }
        return any;
    }

    static void drift(CrewRecord r, float days) {
        float d = CrewRecord.STEADY_MORALE - r.morale;
        float step = MORALE_DRIFT * days;
        r.changeMorale(Math.abs(d) <= step ? d : Math.signum(d) * step);
    }

    /** A shipmate of the player's who has died since the last update costs the rest morale. */
    private void noticeDeaths(Entity ship) {
        ImmutableArray<Entity> all = crew.crewEntities();
        List<Entity> died = new ArrayList<>();
        Map<Entity, Boolean> was = new IdentityHashMap<>(alive);
        alive.clear();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (!isPlayerCrew(e, c)) continue;
            boolean now = c.state != CrewState.DEAD;
            alive.put(e, now);
            if (Boolean.TRUE.equals(was.get(e)) && !now) died.add(e);
        }
        for (Entity e : died) {
            world.notices.post(crew.crew(e).name + " is dead", NoticeLog.Kind.IMPORTANT);
            Entity home = crew.homeOf(e);
            for (Entity other : ownCrew(home)) record(other).changeMorale(DEATH_MORALE);
        }
    }

    /** The ship's guns hit: whoever mans its weapons console learns from it. */
    private void gunnersHit(Entity ship) {
        for (Entity e : ownCrew(ship)) {
            if (!CrewQueries.isManning(e, SubsystemComponent.SubsystemType.WEAPONS)) continue;
            CrewMemberComponent c = crew.crew(e);
            c.record().gain(CrewRecord.Skill.GUNNERY, GUNNERY_XP_PER_HIT);
            c.record().applyTo(c);
        }
    }

    /** A craft is taken: the player's crew aboard it, or on their way in the boarding party, boarded it. */
    private void taken(Entity target) {
        Entity ship = world.getPlayer();
        BoardedComponent b = target.getComponent(BoardedComponent.class);
        if (ship == null || b == null || b.by == null || b.by.get(world.engine.getUuidRegistry()) != ship) return;
        for (Entity e : ownCrew(ship)) {
            CrewMemberComponent c = crew.crew(e);
            boolean party = c.task instanceof BoardTask && ((BoardTask) c.task).ship == target;
            if (crew.position(e).ship != target && !party) continue;
            CrewRecord r = c.record();
            r.boardings++;
            r.gain(CrewRecord.Skill.MARINE, MARINE_XP_PER_BOARDING);
            r.changeMorale(WIN_MORALE);
            r.applyTo(c);
        }
    }

    /** Pays the week's wages from the player's ship, as far as its credits go. Returns what was paid. */
    public double payWages() {
        Entity ship = world.getPlayer();
        TraderComponent account = ship == null ? null : ship.getComponent(TraderComponent.class);
        if (account == null) return 0;
        double paid = 0;
        int unpaid = 0;
        for (Entity e : ownCrew(ship)) {
            CrewRecord r = record(e);
            if (r.wage <= 0f) continue;
            if (account.credits >= r.wage) {
                account.credits -= r.wage;
                paid += r.wage;
                r.changeMorale(PAID_MORALE);
            } else {
                unpaid++;
                r.changeMorale(UNPAID_MORALE);
                r.applyTo(crew.crew(e));
            }
        }
        if (paid > 0) world.notices.post(String.format(Locale.ROOT, "Crew wages: -%,.0f cr", paid));
        if (unpaid > 0) {
            world.notices.post(unpaid + (unpaid == 1 ? " crew member" : " crew") + " went unpaid: morale falls",
                    NoticeLog.Kind.WARNING);
        }
        return paid;
    }

    /** Credits a week the ship's hired crew draw. */
    public double weeklyWages(Entity ship) {
        double sum = 0;
        for (Entity e : ownCrew(ship)) sum += record(e).wage;
        return sum;
    }

    /**
     * The player's own crew of {@code ship}: alive, not held, working for it, not the avatar. Empty
     * for no ship.
     */
    public List<Entity> ownCrew(Entity ship) {
        List<Entity> out = new ArrayList<>();
        if (ship == null) return out;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (isPlayerCrew(e, c) && c.isAlive() && crew.homeOf(e) == ship) out.add(e);
        }
        return out;
    }

    private static boolean isPlayerCrew(Entity e, CrewMemberComponent c) {
        return c.faction == FactionStanding.PLAYER && !c.held && e.getComponent(PlayerAvatarComponent.class) == null;
    }

    private CrewRecord record(Entity e) {
        return crew.crew(e).record();
    }

    /** Base weekly wage of a role, for a crew member of {@link CrewRecord#BASELINE} skill. */
    public static float baseWage(CrewRole role) {
        switch (role) {
            case ENGINEER: return 250f;
            case MEDIC: return 250f;
            case MARINE: return 220f;
            default: return 150f;
        }
    }

    /** The skill a role is hired for. */
    public static float mainSkill(CrewRole role, CrewRecord r) {
        switch (role) {
            case ENGINEER: return r.engineering;
            case MARINE: return r.marine;
            case MEDIC: return (r.gunnery + r.engineering + r.marine) / 3f;
            default: return r.gunnery;
        }
    }

    /** What a crew member of {@code role} with {@code r}'s skills asks a week: more for more skill, to the nearest 10 cr. */
    public static float wageFor(CrewRole role, CrewRecord r) {
        return Math.round(baseWage(role) * (0.5f + mainSkill(role, r)) / 10f) * 10f;
    }

    /** A crew member's state for the roster: "OK 100%", "Unconscious 20%", "Dead". */
    public static String condition(CrewMemberComponent c) {
        if (c.state == CrewState.DEAD) return "Dead";
        String s = c.state == CrewState.CONSCIOUS ? "OK" : "Unconscious";
        return String.format(Locale.ROOT, "%s %.0f%%", s, c.healthFraction() * 100f);
    }

    /** "3 ships beaten, 1 boarded, 2 downed, 0.4 h work", leaving out what is 0; "New" for nothing. */
    public static String history(CrewRecord r) {
        List<String> parts = new ArrayList<>();
        if (r.shipsBeaten > 0) parts.add(r.shipsBeaten + (r.shipsBeaten == 1 ? " ship beaten" : " ships beaten"));
        if (r.boardings > 0) parts.add(r.boardings + " boarded");
        if (r.downed > 0) parts.add(r.downed + " downed");
        if (r.workSeconds >= 360f) parts.add(String.format(Locale.ROOT, "%.1f h work", r.workSeconds / 3600f));
        return parts.isEmpty() ? "New" : String.join(", ", parts);
    }
}
