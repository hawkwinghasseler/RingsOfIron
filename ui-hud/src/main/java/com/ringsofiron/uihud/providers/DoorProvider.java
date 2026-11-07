package com.ringsofiron.uihud.providers;

/**
 * The door the player's avatar is using ([E] at a door): its name, whether it is locked, the
 * pressure on either side of it, and the player's controls over it. Fed to the {@link
 * com.ringsofiron.uihud.widgets.DoorWindow}.
 */
public interface DoorProvider {

    /** What the window shows of the door in use. */
    final class Door {
        /** E.g. "Door", "Port 1 airlock hatch"; empty when there is no door in use. */
        public String name = "";
        /** E.g. "Unlocked", "Locked: its airlock's outer door is wrecked". */
        public String state = "";
        /** Pressure on the avatar's side and the far side, kPa. */
        public float nearKpa, farKpa;
        /** Whether the door is locked. */
        public boolean locked;
        /** Why the lock can't be worked now (no power), or empty if it can. */
        public String lockBlocked = "";
        /** Whether the crew can be ordered to cut it open (locked, with no power to unlock it). */
        public boolean canCut;
        /** E.g. "Cutting it open is ordered", or empty. */
        public String note = "";

        public void clear() {
            name = state = lockBlocked = note = "";
            nearKpa = farKpa = 0f;
            locked = canCut = false;
        }

        public boolean isOpen() {
            return !name.isEmpty();
        }
    }

    /** Fills {@code out} with the door in use; leaves it clear if there is none (walked away). */
    void collect(Door out);

    /** Locks the door if unlocked, unlocks it if locked; returns what to tell the player. */
    String toggleLock();

    /** Orders the crew to cut the locked, unpowered door open; returns what to tell the player. */
    String orderCut();

    /** The window was closed: the door is no longer in use. */
    void close();
}
