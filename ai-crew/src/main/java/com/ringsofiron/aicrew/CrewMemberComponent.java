package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.EntityRef;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.Suit;

/**
 * A crew member: stats, health, consciousness state, role and what they are doing.
 * Their place on the ship is in {@link GridPositionComponent}.
 *
 * Saves keep who the crew member is and their health, but not the job in hand: the
 * claimed task and the carry and treatment links are transient. After a load
 * {@link CrewSaveParticipant} sets carried casualties down where they are and crew pick
 * their jobs afresh from the hazards and casualties around them.
 */
public class CrewMemberComponent implements Component {

    public String name = "Crew";
    public CrewRole role = CrewRole.CREWMAN;
    public CrewDef def = new CrewDef();
    /**
     * Skills, morale, wage and history ({@link CrewRecord}); read it through {@link #record()}, which
     * makes one from the def for a crew member saved before records existed.
     */
    public CrewRecord record;
    /** Crew of different factions on the same ship are hostile to each other. */
    public int faction = 0;

    public float health;
    public CrewState state = CrewState.CONSCIOUS;
    public CrewDamageSink.DamageCause lastDamageCause;
    /** Worst wound from hits ({@link Wound}); health is the environment's (air, heat, fire). */
    public Wound wound = Wound.NONE;
    /** Points of injury from hits, added up: how bad {@link #wound} is ({@link CrewDef#woundFor}). */
    public float injury;
    /** What gave the wound, for the notice if it kills. */
    public CrewDamageSink.DamageCause woundCause;
    /** Seconds a critical wound can still go untreated before it kills. */
    public float bleedOut;
    /**
     * A critical wound whose bleeding someone has stopped ({@link StabiliseTask}): its
     * {@link #bleedOut} clock is held until treatment brings it down a level or a new hit opens it.
     */
    public boolean stabilised;
    /** Seconds of a baseline medic's care given toward bringing the wound down a level. */
    public float care;
    /** Seconds a light or serious wound has been easing by itself. */
    public float healing;

    /** The board task this crew member has claimed, or null. */
    public transient CrewTask task;
    /** The unconscious crew member this one is carrying, or null. */
    public transient Entity carrying;
    /** The crew member carrying this one, or null. */
    public transient Entity carriedBy;
    /** Goods in hand on a carry task, or null. */
    public transient Load load;
    /**
     * The craft this crew member works for, when it is not always the one they are aboard: a
     * station's loaders walk aboard docked ships and come home. Null means the craft they are on.
     */
    public transient Entity home;
    /** {@link #home} as saved. */
    public EntityRef homeRef;
    /** The medic treating this crew member, or null. */
    public transient Entity treatedBy;
    /** Item in hand, for display. */
    public String equippedItem = "";
    /**
     * Held prisoner: crew of a ship that was taken. They neither fight nor are fought, and take no
     * work; they stay where they are kept.
     */
    public boolean held;
    /** Whether this crew member has a space suit on. */
    public boolean suited;
    /** Seconds of air left in the suit they have on. */
    public float suitAir;
    /** The kind of suit they have on, while {@link #suited}. */
    public Suit.Kind suitKind = Suit.Kind.IVA;
    /** Delta-v left in the jetpack of the suit they have on, m/s: an EVA suit's. */
    public float suitJet;
    /**
     * Seconds spent so far taking their suit off at work ({@code CrewTasks.DoWork}), so a save
     * part way through doesn't start it again (round 9).
     */
    public float unsuiting;
    /** Seconds spent so far putting their suit on at a post ({@code CrewTasks.DoWork}); saved, as {@link #unsuiting}. */
    public float suiting;

    /** Seconds until this crew member can fire again. */
    public float fireCooldown = 0f;
    /** Short description of the current activity, for debug display and the HUD. */
    public String activity = "idle";
    /**
     * Share of the timed job in hand done, 0 to 1, for the bar drawn over them; negative while at
     * none. Set each step the work goes on ({@link #showProgress}); {@code CrewDecisionSystem}
     * clears it before each step of the crew's trees.
     */
    public transient float progress = -1f;

    public CrewMemberComponent() {
        this.health = def.maxHealth;
        this.suitAir = def.suitAir;
    }

    public CrewMemberComponent(String name, CrewRole role, int faction) {
        this.name = name;
        this.role = role;
        this.faction = faction;
        this.def = CrewDef.forRole(role);
        this.health = def.maxHealth;
        this.suitAir = def.suitAir;
        this.record = CrewRecord.from(def);
    }

    /**
     * Seconds a job must take before it gets a bar: a quick one is over before a bar would tell the
     * player anything. Jobs that show themselves (a fire shrinking, cargo in hand) get none at all.
     */
    public static final float PROGRESS_MIN_SECONDS = 10f;

    /**
     * Shows {@code done} of {@code of} seconds as the share of the job in hand done ({@link #progress});
     * no bar for a job shorter than {@link #PROGRESS_MIN_SECONDS}.
     */
    public void showProgress(float done, float of) {
        progress = of >= PROGRESS_MIN_SECONDS ? Math.max(0f, Math.min(1f, done / of)) : -1f;
    }

    /** This crew member's record, made from their def if they have none yet. */
    public CrewRecord record() {
        if (record == null) record = CrewRecord.from(def);
        return record;
    }

    /** True while this crew member breathes from their suit: wearing it, with air left in it. */
    public boolean breathesFromSuit() {
        return suited && suitAir > 0f;
    }

    /** True if this crew member can wear a suit at all ({@link CrewDef#suitAir} isn't 0). */
    public boolean wearsSuits() {
        return def.suitAir > 0f;
    }

    /**
     * Puts a fresh pressure suit on them, or takes theirs off and away: for tests and the dev
     * console, where no locker is wanted. A crew member's own suits come from lockers ({@link #wear},
     * {@link #takeOff}).
     */
    public void setSuited(boolean on) {
        if (on) {
            wear(Suit.iva());
            suitAir = def.suitAir;
        } else {
            takeOff();
            suitAir = def.suitAir;
        }
    }

    /** Puts {@code suit} on them: from then on its air and jetpack are theirs ({@link #suitAir}, {@link #suitJet}). */
    public void wear(Suit suit) {
        suited = true;
        suitKind = suit.kind;
        suitAir = suit.air;
        suitJet = suit.jet;
        unsuiting = 0f;
        suiting = 0f;
        equippedItem = suit.kind.noun;
    }

    /** Takes off the suit they have on and returns it, with the air and jetpack it has left; null if they wear none. */
    public Suit takeOff() {
        Suit off = suited ? new Suit(suitKind, suitAir, suitJet) : null;
        suited = false;
        suitJet = 0f;
        unsuiting = 0f;
        suiting = 0f;
        equippedItem = "";
        return off;
    }

    /** True while they wear an EVA suit. */
    public boolean inEvaSuit() {
        return suited && suitKind == Suit.Kind.EVA;
    }

    /** Seconds of air the suit they have on holds full: an EVA suit's, or their pressure suit's ({@link CrewDef#suitAir}). */
    public float suitCapacity() {
        return suitKind == Suit.Kind.EVA ? Suit.EVA_AIR : def.suitAir;
    }

    /** Seconds it takes them to put on a suit of {@code kind}; taking one off takes half. */
    public float suitTime(Suit.Kind kind) {
        return kind == Suit.Kind.EVA ? Suit.EVA_DON : def.suitTime;
    }

    /** Share of their walking speed the suit they have on leaves them: 1 with none. */
    public float suitPace() {
        if (!suited) return 1f;
        return suitKind == Suit.Kind.EVA ? Suit.EVA_SPEED : def.suitSpeedFactor;
    }

    /** Goods carried by hand on a {@link CarryTask}. */
    public static final class Load {
        public final CarryTask task;
        public final double units;

        public Load(CarryTask task, double units) {
            this.task = task;
            this.units = units;
        }
    }

    /** Makes {@code ship} this crew member's home, keeping {@link #homeRef} in step. */
    public void setHome(Entity ship) {
        home = ship;
        homeRef = ship != null && ship.getComponent(UuidComponent.class) != null ? EntityRef.of(ship) : null;
    }

    public boolean isConscious() {
        return state == CrewState.CONSCIOUS;
    }

    public boolean isAlive() {
        return state != CrewState.DEAD;
    }

    /** Share of the usual work and walking speed the wound leaves. */
    public float woundPace() {
        return wound == Wound.LIGHT ? def.woundedWorkFactor : 1f;
    }

    /** Share of the usual work rate: slowed by a light wound and by a suit, an EVA suit's stiff gloves most. */
    public float workPace() {
        return woundPace() * (!suited ? 1f : suitKind == Suit.Kind.EVA ? Suit.EVA_WORK : def.suitWorkFactor);
    }

    /**
     * How fast this crew member treats the wounded: 1 for a medic of {@link CrewRecord#BASELINE}
     * medicine, who brings a wound down a level in {@link CrewDef#careTime}; anyone else at
     * {@link CrewDef#UNTRAINED_CARE}. Skill, morale and their own wound scale it.
     */
    public float careRate() {
        return careRate(Wound.SERIOUS);
    }

    /**
     * {@link #careRate()} for a wound of level {@code wound}: a light wound is first aid, which
     * anyone gives at a medic's rate for their skill (round 8).
     */
    public float careRate(Wound wound) {
        CrewRecord r = record();
        float base = role == CrewRole.MEDIC || wound == Wound.LIGHT ? 1f : CrewDef.UNTRAINED_CARE;
        return base * CrewRecord.factor(r.medicine) * r.workFactor() * workPace();
    }

    public float healthFraction() {
        return def.maxHealth > 0f ? health / def.maxHealth : 0f;
    }
}
