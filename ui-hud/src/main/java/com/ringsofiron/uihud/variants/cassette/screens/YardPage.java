package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.YardProvider.Furnishing;
import com.ringsofiron.uihud.providers.YardProvider.Hardpoint;
import com.ringsofiron.uihud.providers.YardProvider.Offer;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The yard (the classic {@code YardWindow}). At a station's yard, Context's M3 Yard: repairs, the
 * tanks filled, each hardpoint's refit (new, used, or salvaged from the hold), furnishings bought
 * and arranged, and the decks rebuilt in the ship editor, as sections of one list whose soft keys
 * follow the selected row. Away from a yard, M2's Loadout: the crew's own refits, each hardpoint with
 * a salvaged weapon from the hold to mount on EVA, or the mounting called off.
 */
final class YardPage extends ListPage {
    private static final int HEAD = 0, REPAIRS = 1, TANKS = 2, POINT = 3, FURNISH = 4, REBUILD = 5;
    private static final String[] HEADS = {"REPAIRS", "SUPPLIES", "REFITS", "FURNISHINGS", "DECKS", "HARDPOINTS"};
    /** True for the crew's own refits away from a yard (M2 Loadout), false for a station's yard. */
    private final boolean field;
    private Boards b;
    private final IntArray kinds = new IntArray(), refs = new IntArray();
    /** The weapon each hardpoint would take: an offer's index, by the hardpoint's. */
    private final IntIntMap choice = new IntIntMap();
    private int furnishing;
    private final Printer printer = new Printer("WORK ORDERS", 7);
    private String visit = "";

    YardPage(boolean field) {
        super(field ? "LOADOUT" : "YARD", field ? "REFIT" : "YARD", ClassicWindow.YARD);
        this.field = field;
    }

    private YardBoard yard() {
        return b.yard;
    }

    @Override
    boolean offered() {
        return b != null && (field ? yard().field : !yard().field && !yard().stationName.isEmpty());
    }

    @Override
    void refresh(Boards boards) {
        b = boards;
        YardBoard y = yard();
        String place = field ? (y.field ? "FIELD" : "") : y.stationName;
        if (!place.equals(visit)) {
            visit = place;
            printer.clear();
        }
        kinds.clear();
        refs.clear();
        if (!field && !y.stationName.isEmpty()) {
            head(0);
            item(REPAIRS, 0);
            head(1);
            item(TANKS, 0);
        }
        if (y.hardpoints.size > 0) {
            // In the field the hardpoints head the list under their own columns: no prices, the hold's count
            head(field ? 5 : 2);
            for (int i = 0; i < y.hardpoints.size; i++) item(POINT, i);
        }
        if (!field && !y.stationName.isEmpty()) {
            if (y.furnishings.size > 0 || !y.furnished.isEmpty()) {
                head(3);
                item(FURNISH, 0);
            }
            head(4);
            item(REBUILD, 0);
        }
        if (furnishing >= y.furnishings.size) furnishing = 0;
        keepSelection();
    }

    private void head(int h) {
        kinds.add(HEAD);
        refs.add(h);
    }

    private void item(int kind, int ref) {
        kinds.add(kind);
        refs.add(ref);
    }

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return kinds.get(i) != HEAD;
    }

    @Override
    String empty() {
        return field ? "NO HARDPOINTS TO REFIT" : "THIS YARD CAN'T WORK ON THE SHIP";
    }

    private Offer offer(Hardpoint h) {
        YardBoard y = yard();
        if (y.offers.size == 0) return null;
        int c = choice.get(h.index, 0);
        if (c >= y.offers.size) c = 0;
        return y.offers.get(c);
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        YardBoard y = yard();
        int kind = kinds.get(i), ref = refs.get(i);
        switch (kind) {
            case HEAD:
                L.at(p, 1, r, HEADS[ref], DIM);
                if (ref == 2 || ref == 5) {
                    L.at(p, 26, r, "ON IT", DIM);
                    L.at(p, 51, r, ref == 5 ? "MOUNT FROM THE HOLD" : "FIT INSTEAD", DIM);
                    if (ref == 2) L.right(p, 75, r, "NEW", DIM);
                    if (ref == 2) L.right(p, 84, r, "USED", DIM);
                    L.right(p, 89, r, ref == 5 ? "IN HOLD" : "HOLD", DIM);
                }
                return;
            case REPAIRS:
                put(p, s, 2, r, "Repairs");
                put(p, s, 26, r, Ledger.cut(!y.repairs.isEmpty() ? y.repairs : y.damaged == 0 ? "Nothing damaged"
                        : y.damaged + (y.damaged == 1 ? " system damaged" : " systems damaged"), 46));
                putR(p, s, 84, r, y.damaged == 0 ? "--" : Fmt.thousands(y.repairCost));
                return;
            case TANKS:
                put(p, s, 2, r, "Tanks");
                put(p, s, 26, r, Ledger.cut(y.tanks.isEmpty() ? "No tanks aboard" : y.tanks, 46));
                putR(p, s, 84, r, y.refillCost <= 0 ? "--" : Fmt.thousands(y.refillCost));
                return;
            case POINT: {
                Hardpoint h = y.hardpoints.get(ref);
                Offer o = offer(h);
                put(p, s, 2, r, Ledger.cut(Fmt.capital(h.name), field ? 22 : 23));
                String on = h.progress.isEmpty() ? (h.weapon.isEmpty() ? "--" : h.weapon) : h.progress;
                put(p, s, 26, r, Ledger.cut(on, 24));
                put(p, s, 51, r, o == null ? "--" : Ledger.cut(o.name, 16));
                if (field) {
                    putR(p, s, 89, r, o == null ? "--" : String.valueOf(o.salvaged));
                } else {
                    putR(p, s, 75, r, o == null ? "--" : Fmt.thousands(o.price));
                    putR(p, s, 84, r, o == null ? "--" : Fmt.thousands(o.price * y.usedPrice));
                    putR(p, s, 89, r, o == null ? "--" : String.valueOf(o.salvaged));
                }
                return;
            }
            case FURNISH: {
                Furnishing f = y.furnishings.size == 0 ? null : y.furnishings.get(furnishing);
                put(p, s, 2, r, "Furnish");
                put(p, s, 26, r, Ledger.cut(y.furnished.isEmpty() ? "Nothing aboard yet" : y.furnished, 24));
                put(p, s, 51, r, f == null ? "--" : Ledger.cut(f.name + (f.use.isEmpty() ? "" : " (" + f.use + ")"), 24));
                putR(p, s, 84, r, f == null ? "--" : Fmt.thousands(f.price));
                return;
            }
            case REBUILD:
                put(p, s, 2, r, "Rebuild decks");
                put(p, 26, r, "In the ship editor: the yard builds the new layout", s);
                return;
            default:
        }
    }

    private void put(YardPaint p, boolean s, float c, int r, String t) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, AMBER);
    }

    private void put(YardPaint p, float c, int r, String t, boolean s) {
        if (s) L.rev(p, c, r, t);
        else L.at(p, c, r, t, DIM);
    }

    private void putR(YardPaint p, boolean s, float c, int r, String t) {
        if (s) L.revRight(p, c, r, t);
        else L.right(p, c, r, t, AMBER);
    }

    @Override
    void keys() {
        if (sel < 0) return;
        YardBoard y = yard();
        int kind = kinds.get(sel), ref = refs.get(sel);
        switch (kind) {
            case REPAIRS:
                softKey("REPAIR ALL", y.damaged == 0 ? "NOTHING DAMAGED" : null);
                return;
            case TANKS:
                softKey("FILL TANKS", y.tanks.isEmpty() ? "NO TANKS ABOARD" : y.refillCost <= 0 ? "THE TANKS ARE FULL" : null);
                return;
            case POINT: {
                Hardpoint h = y.hardpoints.get(ref);
                Offer o = offer(h);
                softKey("NEXT", y.offers.size < 2 || (field && h.mounting) ? y.offers.size == 0 ? noOffers() : "NO OTHER WEAPON" : null);
                if (field) {
                    if (h.mounting) softKey(h.callOff ? "CALL OFF" : "MOUNTING", h.callOff ? null : "ITS PART IS OUT ALREADY");
                    else softKey("MOUNT ON EVA", o == null ? noOffers() : o.salvaged == 0 ? "NONE IN THE HOLD" : null);
                    return;
                }
                String none = o == null ? noOffers() : null;
                softKey("NEW", none != null ? none : y.credits < o.price ? "NOT ENOUGH CREDITS" : null);
                softKey("USED", none != null ? none : y.credits < o.price * y.usedPrice ? "NOT ENOUGH CREDITS" : null);
                softKey("SALVAGED", none != null ? none : o.salvaged == 0 ? "NONE IN THE HOLD" : null);
                return;
            }
            case FURNISH:
                softKey("NEXT", y.furnishings.size < 2 ? y.furnishings.isEmpty() ? "NONE ON SALE" : "NO OTHER ON SALE" : null);
                softKey("BUY AND PLACE", y.furnishings.isEmpty() ? "NONE ON SALE" : null);
                softKey("ARRANGE", null);
                return;
            case REBUILD:
                softKey("REBUILD", null);
                return;
            default:
        }
    }

    private String noOffers() {
        return field ? "NO SALVAGED WEAPON IN THE HOLD" : "THE YARD HAS NO WEAPONS";
    }

    @Override
    void press(int k) {
        YardProvider provider = b.data == null ? null : b.data.yard;
        if (provider == null || sel < 0) return;
        YardBoard y = yard();
        int kind = kinds.get(sel), ref = refs.get(sel);
        String said = null;
        switch (kind) {
            case REPAIRS:
                said = provider.orderRepairs();
                break;
            case TANKS:
                said = provider.orderRefill();
                break;
            case POINT: {
                Hardpoint h = y.hardpoints.get(ref);
                Offer o = offer(h);
                if (k == 0) {
                    if (y.offers.size > 0) choice.put(h.index, (choice.get(h.index, 0) + 1) % y.offers.size);
                    return;
                }
                if (field) said = h.mounting ? provider.callOffFieldRefit(h.index) : o == null ? null : provider.orderSalvagedRefit(h.index, o.weaponId);
                else if (o != null) said = k == 1 ? provider.orderRefit(h.index, o.weaponId, false)
                        : k == 2 ? provider.orderRefit(h.index, o.weaponId, true) : provider.orderSalvagedRefit(h.index, o.weaponId);
                break;
            }
            case FURNISH:
                if (k == 0) {
                    if (y.furnishings.size > 0) furnishing = (furnishing + 1) % y.furnishings.size;
                    return;
                }
                said = k == 1 ? provider.placeFurnishing(y.furnishings.get(furnishing).id) : provider.arrangeFurnishings();
                // Null: the deck has the piece now, to be put down there
                if (said == null) {
                    console.closeSoon();
                    return;
                }
                break;
            case REBUILD:
                said = provider.openLayoutEditor();
                if (said == null) {
                    console.closeSoon();
                    return;
                }
                break;
            default:
        }
        if (said == null || said.isEmpty()) return;
        if (!printer.started()) printer.start(Fmt.upper(field ? "FIELD REFIT" : visit + " YARD") + "  " + Fmt.stamp(b.data.ctx.simSeconds));
        printer.print(said);
        say(said);
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        boolean liv = !field;
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, null, liv);
        int cols = Math.max(90, Ledger.colsIn(bw - 42f)), rows = Math.max(12, Ledger.rowsIn(bh - 322f));
        L.set(bx + 16f, by + 12f, cols, rows);
        L.under(p, false);
        title(p, field ? "LOADOUT  FIELD REFIT ON EVA" : "YARD", field ? "SALVAGED WEAPONS FROM THE HOLD" : "COSTS IN CR");
        drawList(p, hits, 2, rows - 3);
        drawSoftRow(p, hits);
        L.over(p);
        drawSoftKeys(p, hits);
        float pw = Math.min(470f, bw - 480f);
        printer.draw(p, bx + bw - pw - 15f, L.y + L.h + 40f, pw, liv ? BigConsole.LIV_INK2 : com.ringsofiron.uihud.variants.cassette.Yard.INK_LIGHT);
    }
}
