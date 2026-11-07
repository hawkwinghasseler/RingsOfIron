package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.badlogic.gdx.utils.IntIntMap;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.providers.YardProvider.Hardpoint;
import com.ringsofiron.uihud.providers.YardProvider.Offer;
import com.ringsofiron.uihud.providers.YardProvider.YardBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * The crew's field refit away from a yard (Y with no yard): a row a hardpoint with the weapon on it
 * and a salvaged one from the hold to mount instead (left and right step through them), mounted by
 * an engineer on EVA, or called off while its crate is still inside, as the classic window has it.
 */
public final class LoadoutPage extends Page {
    public static final String SOURCE = "ENG";

    private final IntIntMap pick = new IntIntMap();

    public LoadoutPage() {
        super("LOADOUT", ClassicWindow.YARD);
    }

    @Override
    public boolean offered(Boards b) {
        return b.yard.field;
    }

    @Override
    protected void build(Boards b) {
        YardBoard y = b.yard;
        Sect s = sect("FIELD REFIT").empty("NO HARDPOINTS");
        s.sub(y.offers.size == 0 ? "NO SALVAGED WEAPONS IN THE HOLD" : "AN ENGINEER MOUNTS ONE ON EVA");
        s.col(Col.start("MOUNT", 96f)).col(Col.start("FITTED", 160f)).col(Col.start("MOUNT INSTEAD", 170f)).col(Col.end("IN THE HOLD", 90f));
        for (int i = 0; i < y.hardpoints.size; i++) {
            Hardpoint h = y.hardpoints.get(i);
            Offer o = offer(y, h.index);
            Row r = s.row("hp/" + h.index);
            r.add(Fmt.mount(h.name), Cell.Tone.NAME);
            r.add(h.weapon.isEmpty() ? "—" : Fmt.caps(h.weapon), Cell.Tone.BODY);
            r.add(o == null ? "—" : "‹ " + Fmt.caps(o.name) + " ›", h.mounting ? Cell.Tone.LABEL : Cell.Tone.CYAN);
            r.add(o == null ? "" : Integer.toString(o.salvaged), Cell.Tone.VALUE);
            if (!h.progress.isEmpty()) r.sub = Fmt.caps(h.progress);
            int index = h.index;
            if (y.offers.size > 1 && !h.mounting) r.step = d -> pick.put(index, Math.floorMod(pick.get(index, 0) + d, y.offers.size));
            if (h.mounting) {
                if (h.callOff) r.act("CALL OFF", true, "", order(SOURCE, () -> provider().callOffFieldRefit(index)));
                else r.act("MOUNTING", false, "ITS PART IS OUT ALREADY", null).on(true);
            } else {
                boolean can = o != null && o.salvaged > 0;
                r.act("MOUNT ON EVA", can, "NOTHING SALVAGED IN THE HOLD", can ? order(SOURCE, () -> provider().orderSalvagedRefit(index, o.weaponId)) : null);
            }
            r.detail.add(Line.pair("FITTED", h.weapon.isEmpty() ? "—" : Fmt.caps(h.weapon), "", Cell.Tone.VALUE));
            if (o != null) r.detail.add(Line.pair("MOUNT INSTEAD", Fmt.caps(o.name), "", Cell.Tone.CYAN).note(o.salvaged + " IN THE HOLD"));
            if (!h.progress.isEmpty()) r.detail.add(Line.pair("UNDER WAY", Fmt.caps(h.progress), "", Cell.Tone.GREEN));
        }
        s.note(y.offers.size == 0 ? "NO YARD HERE, AND NO SALVAGED WEAPONS IN THE HOLD FOR THE CREW TO MOUNT"
                : "NO YARD HERE: AN ENGINEER CAN MOUNT A SALVAGED WEAPON FROM THE HOLD ON EVA");
        if (y.offers.size > 1) s.note("← AND → STEP THE CURSOR'S MOUNT THROUGH THE HOLD");
    }

    private Offer offer(YardBoard y, int index) {
        if (y.offers.size == 0) return null;
        return y.offers.get(Math.floorMod(pick.get(index, 0), y.offers.size));
    }

    private YardProvider provider() {
        return host.data().yard;
    }
}
