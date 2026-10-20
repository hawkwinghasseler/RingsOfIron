package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Wounds, medics and suits in the game (round 5 part 2). ai-crew wounds the crew and treats them;
 * this side of it:
 * <ul>
 *   <li><b>Battle stations.</b> A ship is at battle stations while an armed ship fights it (an NPC
 *       captain has it as their target), while it fights one itself, and for {@link #BATTLE_HOLD}
 *       seconds after a shot fired or taken. Its crew suit up then, unless the player turned that
 *       off for their ship ({@link ShipTaskBoardComponent#suitsAtBattleStations}, from the crew
 *       window).</li>
 *   <li><b>Notices.</b> The player hears as their crew are wounded (a critical wound with the time
 *       left to treat it), as a prisoner held aboard is critically wounded, and when a taken ship's
 *       prisoners need bringing aboard to be treated. Deaths are {@link CrewCareer}'s.</li>
 * </ul>
 * The wounds themselves are kept on the crew, so nothing here is saved; battle stations are worked
 * out afresh each frame.
 */
public final class CrewMedical {
    /** Seconds a ship stays at battle stations after the last shot it fired or took. */
    public static final double BATTLE_HOLD = 60;

    private static final Family BOARDS = Family.all(ShipTaskBoardComponent.class).get();
    private static final Family FIGHTERS = Family.all(NpcCombatComponent.class).get();

    private final GameWorld world;
    private final CrewService crew;
    private final ImmutableArray<Entity> boards, fighters;
    /** Sim time of each ship's last shot fired or taken. */
    private final Map<Entity, Double> lastShot = new IdentityHashMap<>();
    /** The wound each crew member had at the last update, so a worse one is told once. */
    private final Map<Entity, Wound> seen = new IdentityHashMap<>();

    public CrewMedical(GameWorld world) {
        this.world = world;
        this.crew = world.crew;
        this.boards = world.engine.getEcsEngine().getEntitiesFor(BOARDS);
        this.fighters = world.engine.getEcsEngine().getEntitiesFor(FIGHTERS);
        world.combatEvents.shots.add((signal, shot) -> shot(shot.shooter));
        world.combatEvents.impacts.add((signal, hit) -> {
            shot(hit.owner);
            shot(hit.target);
        });
        world.boarding.taken.add((signal, ship) -> taken(ship));
        world.hints.register(HintRegistry.Context.ANY, "crew (wounded need a medic)", 4, Action.CREW)
                .onlyWhen(this::criticalAboard);
    }

    private void shot(Entity ship) {
        if (ship != null) lastShot.put(ship, world.engine.getSimSeconds());
    }

    /** Once a frame. */
    public void update() {
        double now = world.engine.getSimSeconds();
        for (int i = 0; i < boards.size(); i++) {
            Entity ship = boards.get(i);
            ship.getComponent(ShipTaskBoardComponent.class).battleStations = inBattle(ship, now);
        }
        lastShot.values().removeIf(t -> now - t > BATTLE_HOLD);
        noticeWounds();
    }

    /** Whether {@code ship} is at battle stations at sim time {@code now}. */
    boolean inBattle(Entity ship, double now) {
        Double shot = lastShot.get(ship);
        if (shot != null && now - shot <= BATTLE_HOLD) return true;
        if (ShipDisablement.isDisabled(ship)) return false;
        NpcCombatComponent own = ship.getComponent(NpcCombatComponent.class);
        if (own != null && own.target != null) return true;
        for (int i = 0; i < fighters.size(); i++) {
            Entity other = fighters.get(i);
            if (other != ship && fighters.get(i).getComponent(NpcCombatComponent.class).target == ship
                    && !ShipDisablement.isDisabled(other)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player's ship is at battle stations now. */
    public boolean atBattleStations() {
        Entity ship = world.getPlayer();
        ShipTaskBoardComponent b = ship == null ? null : ship.getComponent(ShipTaskBoardComponent.class);
        return b != null && b.battleStations;
    }

    /** Turns suits at battle stations on or off for the player's ship; returns what it says. */
    public String toggleSuits() {
        Entity ship = world.getPlayer();
        ShipTaskBoardComponent b = ship == null ? null : ship.getComponent(ShipTaskBoardComponent.class);
        if (b == null) return "No ship";
        b.suitsAtBattleStations = !b.suitsAtBattleStations;
        return b.suitsAtBattleStations ? "The crew will suit up at battle stations"
                : "The crew will stay out of their suits at battle stations: quicker at work, but a breach can suffocate them";
    }

    /** "Suits at battle stations: on", for the crew window; empty with no ship. */
    public String suitsLine() {
        Entity ship = world.getPlayer();
        ShipTaskBoardComponent b = ship == null ? null : ship.getComponent(ShipTaskBoardComponent.class);
        if (b == null) return "";
        return "Suits at battle stations: " + (b.suitsAtBattleStations ? "on" : "off") + (b.battleStations ? " (at battle stations)" : "");
    }

    /** Medical stores aboard the player's ship. */
    public int stores() {
        Entity ship = world.getPlayer();
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        return grid == null ? 0 : grid.medicalStores;
    }

    /** True while one of the player's crew, or a prisoner aboard, is critically wounded. */
    private boolean criticalAboard() {
        Entity ship = world.getPlayer();
        if (ship == null) return false;
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            if (c.isAlive() && c.wound == Wound.CRITICAL && crew.position(e).ship == ship) return true;
        }
        return false;
    }

    /** Tells the player of each of their crew, or prisoner aboard, whose wound has got worse. */
    private void noticeWounds() {
        Entity ship = world.getPlayer();
        ImmutableArray<Entity> all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            CrewMemberComponent c = crew.crew(e);
            Wound was = seen.put(e, c.isAlive() ? c.wound : Wound.NONE);
            if (ship == null || !c.isAlive() || was == null || c.wound.compareTo(was) <= 0) continue;
            boolean own = c.faction == FactionStanding.PLAYER && !c.held && crew.homeOf(e) == ship;
            boolean prisoner = c.held && crew.position(e).ship == ship;
            if (!own && !prisoner) continue;
            String who = prisoner ? "Prisoner " + c.name : c.name;
            world.notices.post(describe(who, c), c.wound == Wound.CRITICAL ? NoticeLog.Kind.IMPORTANT
                    : c.wound == Wound.SERIOUS ? NoticeLog.Kind.WARNING : NoticeLog.Kind.INFO);
        }
        seen.keySet().removeIf(e -> crew.crew(e) == null);
    }

    static String describe(String who, CrewMemberComponent c) {
        switch (c.wound) {
            case CRITICAL:
                return String.format(Locale.ROOT, "%s is critically wounded: treat within %s or they die", who,
                        clock(c.bleedOut));
            case SERIOUS:
                return who + " is seriously wounded and down until treated";
            default:
                return who + " is lightly wounded and working slower";
        }
    }

    /** A ship is taken: if any of its prisoners will die untreated, say so. */
    private void taken(Entity ship) {
        Entity player = world.getPlayer();
        BoardedComponent b = ship.getComponent(BoardedComponent.class);
        if (player == null || b == null || b.by == null || b.by.get(world.engine.getUuidRegistry()) != player) return;
        int critical = 0;
        for (Entity e : world.prisoners.aboard(ship)) if (crew.crew(e).wound == Wound.CRITICAL) critical++;
        if (critical == 0) return;
        world.notices.post(String.format(Locale.ROOT, "%d of the prisoners %s critically wounded: bring them aboard to treat them",
                critical, critical == 1 ? "is" : "are"), NoticeLog.Kind.WARNING);
    }

    /**
     * A crew member's state for the roster: "OK 100%", "Unconscious 20%", "Light wound 90%",
     * "Serious wound, down", "Critical wound, 4:10 to live", "Critical wound, stabilised",
     * "Critical wound, being treated", "Dead".
     */
    public static String condition(CrewMemberComponent c) {
        if (c.state == CrewState.DEAD) return "Dead";
        String health = String.format(Locale.ROOT, "%.0f%%", c.healthFraction() * 100f);
        switch (c.wound) {
            case CRITICAL:
                return c.treatedBy != null ? "Critical wound, being treated"
                        : c.stabilised ? "Critical wound, stabilised"
                        : "Critical wound, " + clock(c.bleedOut) + " to live";
            case SERIOUS:
                return c.treatedBy != null ? "Serious wound, being treated" : "Serious wound, down";
            case LIGHT:
                return "Light wound " + health;
            default:
                return (c.state == CrewState.CONSCIOUS ? "OK " : "Unconscious ") + health;
        }
    }

    /** "4:10". */
    static String clock(float seconds) {
        int s = Math.max(0, (int) Math.ceil(seconds));
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
