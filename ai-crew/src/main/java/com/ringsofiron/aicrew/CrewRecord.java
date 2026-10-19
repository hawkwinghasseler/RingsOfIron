package com.ringsofiron.aicrew;

/**
 * Who a crew member is beyond the moment: their skills and how they grow, their morale and wage,
 * and what they have done. Saved with their {@link CrewMemberComponent}.
 *
 * <p>Skills run from 0 (green) to 1; experience moves a skill a share of the way to
 * {@link #CAP}, so it rises quickly at first and ever more slowly after. {@link #applyTo} turns
 * the skills and morale into the {@link CrewDef} the crew AI works from: gunnery and tracking are
 * the def's own skills, engineering scales how fast they repair, fight fires and seal breaches,
 * and the marine skill how often they hit with small arms. A skill of {@link #BASELINE} is the
 * role's def as it stands, so a crew member who has learned nothing works as before.
 *
 * <p>Experience comes in through the {@code pending} counters, which the crew AI adds to as the
 * crew member works and fights and the game drains (see the game's crew career); they are not saved.
 */
public class CrewRecord {
    /** Highest any skill climbs through experience. */
    public static final float CAP = 0.95f;
    /** The skill at which the role's def is unchanged. */
    public static final float BASELINE = 0.5f;
    /** Morale a crew member settles back to, and starts at. */
    public static final float STEADY_MORALE = 0.7f;
    /** Below this morale work slows to {@link #LOW_MORALE_WORK}. */
    public static final float LOW_MORALE = 0.25f;
    /** Work rate share at low morale. */
    public static final float LOW_MORALE_WORK = 0.6f;

    /** A skill experience raises. */
    public enum Skill {
        /** The ship's guns; tracking rises alongside it. */
        GUNNERY,
        /** Repairs, fire fighting, sealing breaches, yard work and stripping salvage. */
        ENGINEERING,
        /** Fighting aboard: small arms and boarding. */
        MARINE
    }

    public float gunnery = BASELINE;
    public float tracking = BASELINE;
    public float engineering = BASELINE;
    public float marine = BASELINE;
    /** 0 (mutinous) .. 1 (keen). */
    public float morale = STEADY_MORALE;
    /** Credits a week; 0 for crew the player does not pay. */
    public float wage;

    /** Ships beaten while they served aboard. */
    public int shipsBeaten;
    /** Ships taken that they boarded. */
    public int boardings;
    /** Enemies they downed with small arms. */
    public int downed;
    /** Seconds spent on repairs, fires, breaches and yard work. */
    public float workSeconds;

    /** Seconds of engineering work since the game last drained it. */
    public transient float pendingWork;
    /** Small-arms hits since the game last drained them. */
    public transient int pendingHits;
    /** Enemies downed since the game last drained them. */
    public transient int pendingDowns;

    public CrewRecord() {}

    /** A record holding {@code def}'s skills, for a crew member with nothing on file yet. */
    public static CrewRecord from(CrewDef def) {
        CrewRecord r = new CrewRecord();
        r.gunnery = def.gunnerySkill;
        r.tracking = def.trackingSkill;
        return r;
    }

    /** Raises {@code skill} by {@code xp}: each unit of experience closes that share of the gap to {@link #CAP}. */
    public void gain(Skill skill, float xp) {
        if (xp <= 0f) return;
        switch (skill) {
            case GUNNERY:
                gunnery = toward(gunnery, xp);
                tracking = toward(tracking, xp);
                break;
            case ENGINEERING:
                engineering = toward(engineering, xp);
                break;
            default:
                marine = toward(marine, xp);
                break;
        }
    }

    private static float toward(float skill, float xp) {
        if (skill >= CAP) return skill;
        return skill + (CAP - skill) * Math.min(1f, xp);
    }

    public float skill(Skill skill) {
        switch (skill) {
            case GUNNERY: return gunnery;
            case ENGINEERING: return engineering;
            default: return marine;
        }
    }

    /** Moves morale by {@code by}, within 0 .. 1. */
    public void changeMorale(float by) {
        morale = Math.max(0f, Math.min(1f, morale + by));
    }

    /** Share of the usual work rate morale allows. */
    public float workFactor() {
        return morale < LOW_MORALE ? LOW_MORALE_WORK : 1f;
    }

    /** How a skill scales a rate: 1 at {@link #BASELINE}, 0.6 at 0 and 1.4 at 1. */
    public static float factor(float skill) {
        return 0.6f + 0.8f * skill;
    }

    /** Sets {@code crew}'s def from the role's base def, this record's skills and morale. */
    public void applyTo(CrewMemberComponent crew) {
        CrewDef base = CrewDef.forRole(crew.role);
        CrewDef d = crew.def;
        d.gunnerySkill = gunnery;
        d.trackingSkill = tracking;
        float work = factor(engineering) * workFactor();
        d.repairRate = base.repairRate * work;
        d.extinguishRate = base.extinguishRate * work;
        d.sealTime = base.sealTime / work;
        d.patchTime = base.patchTime / work;
        d.resetTime = base.resetTime / work;
        d.isolateTime = base.isolateTime / work;
        d.hitChance = Math.min(0.95f, base.hitChance * factor(marine));
    }

    /** The skills in a line: "Gun 62  Eng 50  Mar 50". */
    public String describeSkills() {
        return String.format(java.util.Locale.ROOT, "Gun %.0f  Eng %.0f  Mar %.0f",
                gunnery * 100f, engineering * 100f, marine * 100f);
    }
}
