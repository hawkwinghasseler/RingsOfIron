package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.YardProvider.Furnishing;
import com.ringsofiron.uihud.providers.YardProvider.Hardpoint;
import com.ringsofiron.uihud.providers.YardProvider.Offer;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * A station's yard (Y while docked at one), as the classic window has it: the repairs and what they
 * cost, the tanks and filling them, a row a hardpoint with the weapon on it and one on sale to fit
 * instead (left and right step through them), new, used or salvaged from the hold, the furnishings on
 * sale to place on the deck, and the yard's own orders: rebuild the decks in the ship editor, and the
 * ships for sale where the station sells them.
 */
public final class YardPage extends Page {
    public static final String SOURCE = "YARD";

    /** Each hardpoint's pick among the weapons on sale, by its index. */
    private final IntIntMap pick = new IntIntMap();

    public YardPage() {
        super("YARD", ClassicWindow.YARD);
    }

    @Override
    public boolean offered(Boards b) {
        return !b.yard.stationName.isEmpty() && !b.yard.field;
    }

    @Override
    protected void build(Boards b) {
        YardBoard y = b.yard;
        repairs(y);
        tanks(b, y);
        refits(y);
        furnishings(y);
        act("REBUILD DECKS", true, "", orderOrClose(SOURCE, () -> provider().openLayoutEditor()));
        boolean ships = b.shipyardOffered && host != null && host.available(ClassicWindow.SHIPYARD);
        act("SHIPS FOR SALE", ships, b.shipyardOffered ? "NONE TODAY" : "THIS STATION SELLS NO SHIPS", () -> host.open(ClassicWindow.SHIPYARD));
    }

    private void repairs(YardBoard y) {
        Sect s = sect("REPAIRS");
        boolean under = !y.repairs.isEmpty();
        if (y.damaged == 0 && !under) s.sub("NOTHING NEEDS REPAIRING");
        else if (y.damaged > 0) s.sub(y.damaged + " DAMAGED · ABOUT " + Fmt.cr(y.repairCost));
        if (under) s.line(Line.text(Fmt.caps(y.repairs), Cell.Tone.GREEN));
        s.act("REPAIR ALL", y.damaged > 0 && !under, under ? "UNDER WAY" : "NOTHING NEEDS REPAIRING", order(SOURCE, () -> provider().orderRepairs())).on(under);
    }

    private void tanks(Boards b, YardBoard y) {
        Sect s = sect("TANKS").empty("NO TANKS ABOARD");
        boolean full = y.refillCost < 0.5;
        s.sub(b.tanks.size == 0 ? "" : full ? "FULL" : "FILL TANKS: " + Fmt.cr(y.refillCost));
        s.col(Col.start("TANK", 150f)).col(Col.end("LEVEL", 60f));
        for (int i = 0; i < b.tanks.size; i++) {
            float f = b.tankFull.get(i);
            Row r = s.row("tank/" + b.tanks.get(i));
            r.add(b.tanks.get(i), Cell.Tone.NAME);
            r.add(f >= 0.995f ? "FULL" : Fmt.percent(f), f < 0.25f ? Cell.Tone.AMBER : Cell.Tone.VALUE);
            r.bar = f;
        }
        s.act("FILL TANKS", !full && y.credits >= 1, full ? "TANKS FULL" : "NO CREDITS", order(SOURCE, () -> provider().orderRefill()));
    }

    private void refits(YardBoard y) {
        Sect s = sect("REFITS").empty("NO HARDPOINTS");
        s.sub(y.offers.size == 0 ? "NOTHING ON SALE" : y.offers.size + (y.offers.size == 1 ? " WEAPON ON SALE" : " WEAPONS ON SALE"));
        s.col(Col.start("MOUNT", 96f)).col(Col.start("FITTED", 150f)).col(Col.start("FIT INSTEAD", 160f))
                .col(Col.end("NEW", 70f)).col(Col.end("USED", 70f)).col(Col.end("SALVAGED", 80f));
        for (int i = 0; i < y.hardpoints.size; i++) {
            Hardpoint h = y.hardpoints.get(i);
            Offer o = offer(y, h.index);
            Row r = s.row("hp/" + h.index);
            r.add(Fmt.mount(h.name), Cell.Tone.NAME);
            r.add(h.weapon.isEmpty() ? "—" : Fmt.caps(h.weapon), Cell.Tone.BODY);
            r.add(o == null ? "—" : "‹ " + Fmt.caps(o.name) + " ›", Cell.Tone.CYAN);
            r.add(o == null ? "" : Fmt.credits(o.price), Cell.Tone.VALUE);
            r.add(o == null ? "" : Fmt.credits(o.price * y.usedPrice), Cell.Tone.VALUE);
            r.add(o == null || o.salvaged == 0 ? "" : o.salvaged + " IN THE HOLD", Cell.Tone.GREEN);
            if (!h.progress.isEmpty()) r.sub = Fmt.caps(h.progress);
            int index = h.index;
            if (y.offers.size > 1) r.step = d -> pick.put(index, Math.floorMod(pick.get(index, 0) + d, y.offers.size));
            boolean any = o != null;
            r.act("FIT NEW", any && y.credits >= o.price, any ? "NOT ENOUGH CREDITS" : "NOTHING ON SALE",
                    any ? order(SOURCE, () -> provider().orderRefit(index, o.weaponId, false)) : null);
            r.act("FIT USED", any && y.credits >= o.price * y.usedPrice, any ? "NOT ENOUGH CREDITS" : "NOTHING ON SALE",
                    any ? order(SOURCE, () -> provider().orderRefit(index, o.weaponId, true)) : null);
            r.act("FIT SALVAGED", any && o.salvaged > 0, any ? "NONE SALVAGED IN THE HOLD" : "NOTHING ON SALE",
                    any ? order(SOURCE, () -> provider().orderSalvagedRefit(index, o.weaponId)) : null);
            r.detail.add(Line.pair("FITTED", h.weapon.isEmpty() ? "—" : Fmt.caps(h.weapon), "", Cell.Tone.VALUE));
            if (o != null) {
                r.detail.add(Line.pair("FIT INSTEAD", Fmt.caps(o.name), "", Cell.Tone.CYAN));
                r.detail.add(Line.pair("NEW", Fmt.credits(o.price), "CR", Cell.Tone.VALUE));
                r.detail.add(Line.pair("USED", Fmt.credits(o.price * y.usedPrice), "CR", Cell.Tone.VALUE)
                        .note(Math.round(y.usedPrice * 100) + "% OF NEW"));
                r.detail.add(Line.pair("SALVAGED", o.salvaged == 0 ? "NONE IN THE HOLD" : o.salvaged + " IN THE HOLD, FREE", "", Cell.Tone.VALUE));
            }
            if (!h.progress.isEmpty()) r.detail.add(Line.pair("UNDER WAY", Fmt.caps(h.progress), "", Cell.Tone.GREEN));
        }
        if (y.offers.size > 1) s.note("← AND → STEP THE CURSOR'S MOUNT THROUGH WHAT IS ON SALE");
    }

    private void furnishings(YardBoard y) {
        Sect s = sect("FURNISHINGS").empty("NOTHING ON SALE");
        s.sub(y.furnished.isEmpty() ? "NONE ABOARD" : Fmt.caps(y.furnished));
        s.col(Col.start("PIECE", 190f)).col(Col.start("FOR", 110f)).col(Col.end("CR", 70f));
        for (int i = 0; i < y.furnishings.size; i++) {
            Furnishing f = y.furnishings.get(i);
            Row r = s.row("furn/" + f.id);
            r.add(Fmt.caps(f.name), Cell.Tone.NAME);
            r.add(Fmt.caps(f.use), Cell.Tone.LABEL);
            r.add(Fmt.credits(f.price), Cell.Tone.VALUE);
            String id = f.id;
            r.act("BUY AND PLACE", y.credits >= f.price, "NOT ENOUGH CREDITS", orderOrClose(SOURCE, () -> provider().placeFurnishing(id)));
        }
        s.act("ARRANGE", true, "", orderOrClose(SOURCE, () -> provider().arrangeFurnishings()));
        if (y.furnishings.size > 0) s.note("BUY AND PLACE PUTS IT ON THE DECK UNDER THE POINTER, TO TURN AND CLICK DOWN");
    }

    /** Hardpoint {@code index}'s pick among the weapons on sale, or null with none on sale. */
    private Offer offer(YardBoard y, int index) {
        if (y.offers.size == 0) return null;
        int at = Math.floorMod(pick.get(index, 0), y.offers.size);
        return y.offers.get(at);
    }

    private YardProvider provider() {
        return host.data().yard;
    }
}
