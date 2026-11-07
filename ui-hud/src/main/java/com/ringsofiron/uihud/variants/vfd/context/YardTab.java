package com.ringsofiron.uihud.variants.vfd.context;

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
 * The yard (functional-context 10.3): today's yard window as one list in sections, the soft keys
 * acting on the row in focus. Repairs (the damage and Repair all), supplies (the tanks and their
 * fill), the hardpoints (each with its weapon and the refit under way; Refit opens the weapons on
 * offer for it, new, used or salvaged from the hold), furnishings to buy and place, and Rebuild decks
 * in the ship editor. Away from a yard the same list is the crew's field refit, in the ship's Loadout
 * tab ({@link #fieldTab}): the hardpoints, with the salvaged weapons in the hold to mount on EVA.
 */
class YardTab extends SectionTab {
    static final int REPAIR = ITEM, TANKS = ITEM + 1, POINT = ITEM + 2, FURNISH = ITEM + 3, ARRANGE = ITEM + 4, DECKS = ITEM + 5,
            OFFER = ITEM + 6;
    /** The keys, by place; the last is Back from the weapons on offer. */
    static final int K0 = 0, K1 = 1, K2 = 2, BACK = 3;

    YardBoard board;
    /** The hardpoint whose weapons on offer show, or -1 for the whole yard. */
    int refitting = -1;
    /** The field refit, in the ship's Loadout tab, rather than a station's yard. */
    final boolean fieldTab;

    YardTab() {
        this("YARD", ClassicWindow.YARD, "Y", false);
    }

    YardTab(String name, ClassicWindow window, String key, boolean fieldTab) {
        super(name, window, key);
        this.fieldTab = fieldTab;
        keys.define(4, 6);
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return !b.yard.field && !b.yard.stationName.isEmpty();
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
            add(TANKS, 0, cols.clear().left(tanks, 60).right(fill, 24).toString());
        }
        head(board.field ? "FIELD REFIT: THE CREW MOUNT SALVAGED WEAPONS ON EVA" : "HARDPOINTS");
        for (int i = 0; i < board.hardpoints.size; i++) {
            Hardpoint h = board.hardpoints.get(i);
            add(POINT, i, cols.clear().left(where(h.name), 22).left(h.weapon.toUpperCase(Locale.ROOT), 26)
                    .left(h.progress.toUpperCase(Locale.ROOT), 36).toString());
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
                add(FURNISH, i, cols.clear().left(f.name.toUpperCase(Locale.ROOT), 34).left(f.use.toUpperCase(Locale.ROOT), 26)
                        .right(Glass.thousands(f.price) + " CR", 24).toString());
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
            cols.clear().left(o.name.toUpperCase(Locale.ROOT), 30);
            if (!board.field) cols.right("NEW " + Glass.thousands(o.price), 16).right("USED " + Glass.thousands(o.price * board.usedPrice), 18);
            add(OFFER, i, cols.right(o.salvaged > 0 ? "SALVAGED " + o.salvaged : "", 20).toString());
        }
        if (board.offers.size == 0) note(board.field ? "NO SALVAGED WEAPONS IN THE HOLD" : "NO WEAPONS ON OFFER");
        if (!h.progress.isEmpty()) note(h.progress.toUpperCase(Locale.ROOT));
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
        HudData d = modes.data();
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
                modes.refresh();
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
        // Placing a furnishing or rebuilding the decks is done on the deck or in the editor: the terminal steps aside
        if (began) {
            modes.closeAll();
            return;
        }
        modes.say(fieldTab ? "CREW" : "STATION", said);
        modes.refresh();
    }

    /** From the weapons on offer back to the whole list, the hardpoint in focus. */
    void back() {
        int r = refitting;
        refitting = -1;
        modes.refresh();
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
