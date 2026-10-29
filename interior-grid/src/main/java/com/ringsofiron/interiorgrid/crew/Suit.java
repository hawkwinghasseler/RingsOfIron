package com.ringsofiron.interiorgrid.crew;

/**
 * A space suit off a crew member's back: hung in a suit locker ({@link
 * com.ringsofiron.interiorgrid.SuitLockers}), or in someone's hands on its way to a casualty or back
 * to a locker. Worn, its state is the crew member's ({@code CrewMemberComponent.suited}, {@code
 * suitAir}, {@code suitKind}, {@code suitJet}) until it comes off again.
 *
 * <p>Two kinds. An {@link Kind#IVA} pressure suit is for inside the ship: on in seconds, light
 * enough to work in, with {@link #IVA_AIR} of air. An {@link Kind#EVA} suit is for
 * going outside: hard-shelled, with a life support pack good for {@link #EVA_AIR}, a tether reel and
 * a cold-gas jetpack of {@link #JET_DV}; it takes {@link #EVA_DON} to get into and is clumsy indoors.
 */
public final class Suit {

    public enum Kind {
        /** A pressure suit for the crew inside the ship. */
        IVA("pressure suit"),
        /** A suit for going outside, with a jetpack and a tether. */
        EVA("EVA suit");

        /** What the crew call it: "pressure suit". */
        public final String noun;

        Kind(String noun) {
            this.noun = noun;
        }
    }

    /** Seconds of air a pressure suit holds when full: {@code CrewDef.suitAir}'s figure. */
    public static final float IVA_AIR = 600f;
    /** Seconds of air an EVA suit's life support pack holds when full: 40 minutes. */
    public static final float EVA_AIR = 2400f;
    /** Seconds to get into an EVA suit (with a hand from the suit's own checks); getting out takes half. */
    public static final float EVA_DON = 40f;
    /** Walking speed in an EVA suit inside the ship, as a share of their own: bulky in a corridor. */
    public static final float EVA_SPEED = 0.6f;
    /** Work rate in an EVA suit inside the ship, or outside, as a share of their own: stiff gloves. */
    public static final float EVA_WORK = 0.65f;
    /** Delta-v of a full EVA jetpack, m/s. */
    public static final float JET_DV = 200f;
    /** Kilograms of oxygen a full EVA suit takes: four IVA suits' worth. */
    public static final double EVA_OXYGEN_KG = 0.2;
    /** Kilograms of the ship's propellant a jetpack takes to fill from empty. */
    public static final double JET_PROPELLANT_KG = 20.0;

    public Kind kind = Kind.IVA;
    /** Seconds of air left in it. */
    public float air;
    /** Delta-v left in its jetpack, m/s; 0 for a suit with none (IVA). */
    public float jet;

    /** For saves. */
    public Suit() {
    }

    public Suit(Kind kind, float air, float jet) {
        this.kind = kind;
        this.air = air;
        this.jet = kind == Kind.EVA ? jet : 0f;
    }

    /** A full EVA suit. */
    public static Suit eva() {
        return new Suit(Kind.EVA, EVA_AIR, JET_DV);
    }

    /** A full pressure suit. */
    public static Suit iva() {
        return new Suit(Kind.IVA, IVA_AIR, 0f);
    }

    /** A full suit of {@code kind}. */
    public static Suit full(Kind kind) {
        return kind == Kind.EVA ? eva() : iva();
    }

    /** Seconds of air a suit of {@code kind} holds full. */
    public static float capacity(Kind kind) {
        return kind == Kind.EVA ? EVA_AIR : IVA_AIR;
    }

    /** Seconds of air it holds full. */
    public float capacity() {
        return capacity(kind);
    }

    /** True once its air and its jetpack are full. */
    public boolean isFull() {
        return air >= capacity() && (kind != Kind.EVA || jet >= JET_DV);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Suit(%s, %.0f s, %.0f m/s)", kind, air, jet);
    }
}
