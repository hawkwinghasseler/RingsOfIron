package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.YardProvider.Furnishing;
import com.ringsofiron.uihud.providers.YardProvider.Hardpoint;
import com.ringsofiron.uihud.providers.YardProvider.Offer;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * The terminal's Yard (functional-task 7, TRM): today's yard window as one list in three groups, the
 * action keys acting on the row in focus. Repairs (the damage and Repair all), supplies (the tanks and
 * their fill), the refits (each hardpoint with its gun and the refit under way; Refit lists the guns on
 * offer for it, new, used or salvaged from the hold), furnishings to buy and place, and Rebuild decks in
 * the ship editor. Beside the list, what there is to say about the row in focus: the repairs under way,
 * each tank, a hardpoint's gun, a gun on offer against the one fitted with its prices new, used and
 * salvaged. The game gives no gun's numbers (damage, rate, range, power) to compare, so the detail
 * names the guns and their prices only.
 *
 * <p>Away from a yard the same tab is the crew's field refit, the terminal's only tab: the hardpoints,
 * with the salvaged guns in the hold to mount on EVA.
 */
final class YardTab extends SectionTab {
    static final int REPAIR = ITEM, TANKS = ITEM + 1, POINT = ITEM + 2, FURNISH = ITEM + 3, ARRANGE = ITEM + 4, DECKS = ITEM + 5,
            OFFER = ITEM + 6;
    /** The keys, by place; the last is Back from the weapons on offer. */
    static final int K0 = 0, K1 = 1, K2 = 2, BACK = 3;

    YardBoard board;
    /** The hardpoint whose weapons on offer show, or -1 for the whole yard. */
    int refitting = -1;
    YardTab() {
        super("YARD", ClassicWindow.YARD, "Y");
        keys.define(4, 4);
    }

    /** A station's yard while docked at one; away from a yard, the crew's field refit. */
    @Override
    boolean offered(Boards b, HudData d) {
        return b.yard.field || b.docked() && b.yardOffered && !b.yard.stationName.isEmpty();
    }


    @Override
    void shown() {
        refitting = -1;
    }

    @Override
    void build(Boards b, HudData d) {
        board = b.yard;
        if (refitting >= board.hardpoints.size) refitting = -1;
        if (refitting >= 0) offers(board.hardpoints.get(refitting));
        else whole();
    }

    /** Every section of the yard, or the field refit's hardpoints. */
    void whole() {
        if (!board.field) {
            head("REPAIRS");
            String damage = board.damaged == 0 ? "NOTHING NEEDS REPAIRING"
                    : String.format(Locale.ROOT, "%d DAMAGED, %s CR TO REPAIR", board.damaged, Glass.thousands(board.repairCost));
            add(REPAIR, 0, board.repairs.isEmpty() ? damage : Glass.cut(board.repairs, cells - 1));
            head("SUPPLIES");
            String tanks = board.tanks.isEmpty() ? "NO TANKS ABOARD" : board.tanks.toUpperCase(Locale.ROOT);
            String fill = board.refillCost < 0.5 ? "FULL" : "FILL " + Glass.thousands(board.refillCost) + " CR";
            add(TANKS, 0, cols.clear().left(tanks, cells - 17).right(fill, 16).toString());
        }
        head(board.field ? "FIELD REFIT: THE CREW MOUNT SALVAGED WEAPONS ON EVA" : "HARDPOINTS");
        for (int i = 0; i < board.hardpoints.size; i++) {
            Hardpoint h = board.hardpoints.get(i);
            add(POINT, i, cols.clear().left(where(h.name), 12).left(h.weapon.toUpperCase(Locale.ROOT), 22)
                    .left(h.progress.toUpperCase(Locale.ROOT), Math.max(4, cells - 35)).toString());
        }
        if (board.hardpoints.size == 0) note("NO HARDPOINTS ON THIS SHIP");
        if (board.field) {
            if (board.offers.size == 0) note("NO SALVAGED WEAPONS IN THE HOLD TO MOUNT");
            return;
        }
        if (board.furnishings.size > 0 || !board.furnished.isEmpty()) {
            head(board.furnished.isEmpty() ? "FURNISHINGS" : "FURNISHINGS: " + board.furnished.toUpperCase(Locale.ROOT));
            for (int i = 0; i < board.furnishings.size; i++) {
                Furnishing f = board.furnishings.get(i);
                add(FURNISH, i, cols.clear().left(f.name.toUpperCase(Locale.ROOT), Math.max(8, cells - 33)).left(f.use.toUpperCase(Locale.ROOT), 16)
                        .right(Glass.thousands(f.price) + " CR", 16).toString());
            }
            if (!board.furnished.isEmpty()) add(ARRANGE, 0, "MOVE THE FURNISHINGS ABOARD, OR HAND THEM BACK");
        }
        head("DECKS");
        add(DECKS, 0, "REBUILD THE DECKS IN THE SHIP EDITOR");
    }

    /** The weapons on offer for hardpoint {@code h}. */
    private void offers(Hardpoint h) {
        head("REFIT " + where(h.name) + (h.weapon.isEmpty() ? "" : ": " + h.weapon.toUpperCase(Locale.ROOT) + " NOW"));
        for (int i = 0; i < board.offers.size; i++) {
            Offer o = board.offers.get(i);
            cols.clear().left(o.name.toUpperCase(Locale.ROOT), Math.max(8, cells - (board.field ? 13 : 45)));
            if (!board.field) cols.right("NEW " + Glass.thousands(o.price), 16).right("USED " + Glass.thousands(o.price * board.usedPrice), 16);
            add(OFFER, i, cols.right(o.salvaged > 0 ? "SALVAGED " + o.salvaged : "", 12).toString());
        }
        if (board.offers.size == 0) note(board.field ? "NO SALVAGED WEAPONS IN THE HOLD" : "NO WEAPONS ON OFFER");
        if (!h.progress.isEmpty()) note(h.progress.toUpperCase(Locale.ROOT));
    }

    @Override
    void detailFor(int focus) {
        if (board == null || focus < 0 || focus >= kind.size) return;
        int r = ref.get(focus);
        switch (kind.get(focus)) {
            case REPAIR:
                tell(board.damaged == 0 ? "Nothing needs repairing" : board.damaged + " damaged, " + Glass.thousands(board.repairCost) + " cr to repair all");
                if (!board.repairs.isEmpty()) tell(board.repairs);
                tell(Glass.thousands(board.credits) + " cr in hand");
                break;
            case TANKS:
                if (board.tanks.isEmpty()) tell("No tanks aboard");
                else for (String t : board.tanks.split(",")) tell(t.trim());
                tell(board.refillCost < 0.5 ? "Every tank is full" : Glass.thousands(board.refillCost) + " cr to fill them, as far as the credits go");
                break;
            case POINT: {
                Hardpoint h = board.hardpoints.get(r);
                tell(where(h.name) + (h.weapon.isEmpty() ? ": nothing fitted" : ": " + h.weapon));
                if (!h.progress.isEmpty()) tell(h.progress);
                tell(board.offers.size == 0 ? (board.field ? "No salvaged guns in the hold to mount" : "No guns on offer")
                        : board.offers.size + (board.field ? " salvaged in the hold to mount on EVA" : " on offer to fit here"));
                break;
            }
            case FURNISH: {
                Furnishing f = board.furnishings.get(r);
                tell(f.name + ", " + f.use);
                tell(Glass.thousands(f.price) + " cr; it is placed on the deck after buying");
                if (!board.furnished.isEmpty()) tell("Aboard: " + board.furnished);
                break;
            }
            case ARRANGE:
                tell("Move the furnishings aboard about on the deck, or hand them back to the yard");
                break;
            case DECKS:
                tell("Rebuild the ship's decks in the ship editor");
                break;
            case OFFER: {
                Offer o = board.offers.get(r);
                Hardpoint h = refitting >= 0 && refitting < board.hardpoints.size ? board.hardpoints.get(refitting) : null;
                tell(o.name + (h == null ? "" : " for the " + where(h.name).toLowerCase(Locale.ROOT)));
                if (h != null) tell(h.weapon.isEmpty() ? "Fitted now: nothing" : "Fitted now: " + h.weapon);
                if (!board.field) {
                    tell("New " + Glass.thousands(o.price) + " cr, used " + Glass.thousands(o.price * board.usedPrice) + " cr");
                }
                tell(o.salvaged > 0 ? o.salvaged + " salvaged in the hold, fitted for nothing" : "None salvaged in the hold");
                break;
            }
            default:
        }
    }

    /** A hardpoint's place in capitals: "the nose" to "NOSE". */
    static String where(String name) {
        String s = name.startsWith("the ") ? name.substring(4) : name;
        return s.toUpperCase(Locale.ROOT);
    }

    @Override
    void keysFor(int focus) {
        for (int i = 0; i < 4; i++) keys.set(i, "", false);
        if (refitting >= 0) keys.set(BACK, "BACK", true);
        if (board == null || focus < 0 || focus >= kind.size) return;
        int r = ref.get(focus);
        switch (kind.get(focus)) {
            case REPAIR:
                keys.set(K0, "REPAIR ALL", board.damaged > 0 && board.repairs.isEmpty());
                break;
            case TANKS:
                keys.set(K0, "FILL TANKS", board.refillCost >= 0.5 && board.credits >= 1);
                break;
            case POINT: {
                Hardpoint h = board.hardpoints.get(r);
                if (board.field) {
                    keys.set(K0, "MOUNT", board.offers.size > 0 && !h.mounting);
                    keys.set(K1, "CALL OFF", h.mounting && h.callOff);
                } else {
                    keys.set(K0, "REFIT", board.offers.size > 0);
                }
                break;
            }
            case FURNISH:
                keys.set(K0, "BUY", board.credits >= board.furnishings.get(r).price);
                keys.set(K1, "ARRANGE", !board.furnished.isEmpty());
                break;
            case ARRANGE:
                keys.set(K0, "ARRANGE", true);
                break;
            case DECKS:
                keys.set(K0, "REBUILD", true);
                break;
            case OFFER: {
                Offer o = board.offers.get(r);
                Hardpoint h = board.hardpoints.get(refitting);
                if (board.field) {
                    keys.set(K0, "MOUNT", o.salvaged > 0 && !h.mounting);
                } else {
                    keys.set(K0, "FIT NEW", board.credits >= o.price);
                    keys.set(K1, "FIT USED", board.credits >= o.price * board.usedPrice);
                    keys.set(K2, "SALVAGED", o.salvaged > 0);
                }
                break;
            }
            default:
        }
    }

    @Override
    void press(int k, int focus) {
        HudData d = windows.data();
        YardProvider p = d == null ? null : d.yard;
        if (p == null || board == null) return;
        if (k == BACK && refitting >= 0) {
            back();
            return;
        }
        if (focus < 0 || focus >= kind.size) return;
        int r = ref.get(focus);
        String said = null;
        boolean began = false;
        switch (kind.get(focus)) {
            case REPAIR:
                said = p.orderRepairs();
                break;
            case TANKS:
                said = p.orderRefill();
                break;
            case POINT:
                if (board.field && k == K1) {
                    said = p.callOffFieldRefit(board.hardpoints.get(r).index);
                    break;
                }
                // The weapons on offer for it
                refitting = r;
                pages.focus(0);
                windows.refresh();
                return;
            case FURNISH:
                said = k == K1 ? p.arrangeFurnishings() : p.placeFurnishing(board.furnishings.get(r).id);
                began = said == null;
                break;
            case ARRANGE:
                said = p.arrangeFurnishings();
                began = said == null;
                break;
            case DECKS:
                said = p.openLayoutEditor();
                began = said == null;
                break;
            case OFFER: {
                Offer o = board.offers.get(r);
                int hp = board.hardpoints.get(refitting).index;
                said = board.field || k == K2 ? p.orderSalvagedRefit(hp, o.weaponId) : p.orderRefit(hp, o.weaponId, k == K1);
                back();
                break;
            }
            default:
        }
        // Placing a furnishing or rebuilding the decks is done on the deck or in the editor: the link steps aside
        if (began) {
            windows.closeAll();
            return;
        }
        windows.say(said);
        windows.refresh();
    }

    /** From the weapons on offer back to the whole list, the hardpoint in focus. */
    void back() {
        int r = refitting;
        refitting = -1;
        windows.refresh();
        for (int i = 0; i < kind.size; i++) {
            if (kind.get(i) == POINT && ref.get(i) == r) {
                pages.focus(i);
                focusMoved();
                break;
            }
        }
    }

    @Override
    boolean key(int keycode) {
        if (refitting >= 0 && keycode == Keys.BACKSPACE) {
            back();
            return true;
        }
        return super.key(keycode);
    }
}
