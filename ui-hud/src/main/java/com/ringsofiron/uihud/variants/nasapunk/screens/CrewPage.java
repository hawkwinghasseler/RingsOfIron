package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;
import com.ringsofiron.uihud.variants.ClassicWindow;

import java.util.Locale;

/**
 * The crew roster (I), as the classic window has it: a row each of the crew aboard with role, skills,
 * condition, morale and wage, their record under it, their EVA order and, docked, letting them go
 * (pressed twice); the ship's two standing orders, suits at battle stations and EVA; and the
 * prisoners held, each to hand over, ransom or release where the station takes them, or recruit
 * when they offer to join, with the taken ship's alongside to bring aboard.
 */
public final class CrewPage extends Page {
    public static final String SOURCE = "CREW";

    public CrewPage() {
        super("CREW", ClassicWindow.CREW);
    }

    @Override
    public boolean offered(Boards b) {
        return b.crew.isOpen();
    }

    @Override
    protected void build(Boards b) {
        CrewSheet c = b.crew;
        Sect s = sect("ABOARD").empty("NO CREW BUT YOU").sub(Fmt.caps(c.summary));
        s.col(Col.start("NAME", 130f)).col(Col.start("ROLE", 96f)).col(Col.start("SKILLS", 170f)).col(Col.start("CONDITION", 190f))
                .col(Col.end("MORALE", 66f)).col(Col.end("WAGE", 96f));
        for (int i = 0; i < c.crew.size; i++) {
            Member m = c.crew.get(i);
            Row r = s.row("crew/" + m.id);
            r.add(Fmt.caps(m.name), Cell.Tone.NAME);
            r.add(Fmt.caps(m.role), Cell.Tone.LABEL);
            r.add(Fmt.caps(m.skills), Cell.Tone.BODY);
            r.add(Fmt.caps(m.condition), condition(m.condition));
            r.add(morale(m.morale), Cell.Tone.VALUE);
            r.add(Fmt.caps(m.wage), Cell.Tone.BODY);
            r.sub = Fmt.caps(m.history);
            long id = m.id;
            if (!m.evaAction.isEmpty()) r.act(Fmt.caps(m.evaAction), true, "", order(SOURCE, () -> provider().evaOrder(id)));
            if (m.dismissable) r.act("LET GO", true, "", order(SOURCE, () -> provider().dismiss(id))).guarded();
            r.detail.add(Line.pair("NAME", Fmt.caps(m.name), "", Cell.Tone.NAME));
            r.detail.add(Line.pair("ROLE", Fmt.caps(m.role), "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("SKILLS", Fmt.caps(m.skills), "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("CONDITION", Fmt.caps(m.condition), "", condition(m.condition)));
            r.detail.add(Line.pair("MORALE", morale(m.morale), "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("WAGE", Fmt.caps(m.wage), "", Cell.Tone.VALUE));
            if (!m.history.isEmpty()) r.detail.add(Line.pair("RECORD", Fmt.caps(m.history), "", Cell.Tone.LABEL));
        }
        if (!c.suits.isEmpty() || !c.eva.isEmpty()) {
            Sect o = sect("STANDING ORDERS");
            o.col(Col.start("", 0f));
            if (!c.suits.isEmpty()) {
                Row r = o.row("order/suits");
                r.add(Fmt.caps(c.suits), Cell.Tone.BODY);
                r.act("CHANGE", true, "", order(SOURCE, () -> provider().toggleSuits()));
            }
            if (!c.eva.isEmpty()) {
                Row r = o.row("order/eva");
                r.add(Fmt.caps(c.eva), Cell.Tone.BODY);
                r.act("CHANGE", true, "", order(SOURCE, () -> provider().toggleEva()));
            }
        }
        if (c.prisoners.size > 0 || c.prisonersAlongside) prisoners(c);
        if (c.crew.size > 0 && c.crew.first().dismissable) s.note("LET GO IS PRESSED TWICE");
    }

    private void prisoners(CrewSheet c) {
        Sect p = sect("PRISONERS").empty("NONE ABOARD");
        p.col(Col.start("NAME", 130f)).col(Col.start("WHO", 230f)).col(Col.start("CONDITION", 190f));
        for (int i = 0; i < c.prisoners.size; i++) {
            Prisoner pr = c.prisoners.get(i);
            Row r = p.row("prisoner/" + pr.id);
            r.add(Fmt.caps(pr.name), Cell.Tone.NAME);
            r.add(Fmt.caps(pr.detail), Cell.Tone.LABEL);
            r.add(Fmt.caps(pr.condition), condition(pr.condition));
            long id = pr.id;
            if (!pr.option.isEmpty()) r.act(Fmt.caps(pr.option), true, "", order(SOURCE, () -> provider().settlePrisoner(id)));
            if (pr.offersToJoin) r.act("RECRUIT", true, "", order(SOURCE, () -> provider().recruit(id)));
            if (pr.offersToJoin) r.sub = "OFFERS TO JOIN THE CREW";
            r.detail.add(Line.pair("NAME", Fmt.caps(pr.name), "", Cell.Tone.NAME));
            r.detail.add(Line.pair("WHO", Fmt.caps(pr.detail), "", Cell.Tone.VALUE));
            r.detail.add(Line.pair("CONDITION", Fmt.caps(pr.condition), "", condition(pr.condition)));
        }
        if (c.prisonersAlongside) p.act("BRING ABOARD", true, "", order(SOURCE, () -> provider().takePrisoners()));
        if (c.prisoners.size > 0 && c.prisoners.first().option.isEmpty()) p.note("DOCKED, A STATION TAKES THEM OFF YOUR HANDS");
    }

    /** How a condition reads: well, hurt, or dying. */
    public static Cell.Tone condition(String condition) {
        String c = condition == null ? "" : condition.toLowerCase(Locale.ROOT);
        if (c.contains("critical") || c.contains("dying") || c.contains("to live")) return Cell.Tone.RED;
        if (c.contains("wound") || c.contains("unconscious")) return Cell.Tone.AMBER;
        if (c.contains("dead")) return Cell.Tone.LABEL;
        return Cell.Tone.VALUE;
    }

    /** "Morale 70%" to "70%". */
    static String morale(String m) {
        String t = Fmt.caps(m);
        return t.startsWith("MORALE ") ? t.substring(7) : t;
    }

    private CrewProvider provider() {
        return host.data().crew;
    }
}
