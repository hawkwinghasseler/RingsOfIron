package com.ringsofiron.uihud.providers;

/**
 * The player's own body: health, wound, suit and the air in the room they stand in. Filled by the
 * game wiring layer from ai-crew's avatar and interior-grid's compartments.
 */
public interface CaptainStatusProvider {
    /** Fills {@code out}, already cleared; leaves {@link CaptainStatus#present} false with no avatar. */
    void read(CaptainStatus out);

    final class CaptainStatus {
        public boolean present;
        /** Health as a share of the most it can be, 0 to 1. */
        public float health = 1f;
        /** "conscious", "unconscious" or "dead". */
        public String state = "conscious";
        /** The wound's name, or "" with none. */
        public String wound = "";
        /** Seconds until bleeding out, NaN when not bleeding. */
        public float bleedOut = Float.NaN;

        public boolean suited;
        /** "IVA" or "EVA" while suited, else "". */
        public String suitKind = "";
        /** Seconds of air in the suit, and what it holds full; NaN unsuited. */
        public float suitAir = Float.NaN;
        public float suitAirCapacity = Float.NaN;

        /** The room's name ({@code RoomNames}), or "" outside a room. */
        public String room = "";
        /** The room's pressure and oxygen as shares of normal cabin air, and smoke 0..1; NaN outside. */
        public float roomPressure = Float.NaN;
        public float roomOxygen = Float.NaN;
        public float roomSmoke = Float.NaN;
        /** What the captain carries in hand, or "". */
        public String carrying = "";

        public void clear() {
            present = false;
            health = 1f;
            state = "conscious";
            wound = "";
            bleedOut = Float.NaN;
            suited = false;
            suitKind = "";
            suitAir = Float.NaN;
            suitAirCapacity = Float.NaN;
            room = "";
            roomPressure = Float.NaN;
            roomOxygen = Float.NaN;
            roomSmoke = Float.NaN;
            carrying = "";
        }
    }
}
