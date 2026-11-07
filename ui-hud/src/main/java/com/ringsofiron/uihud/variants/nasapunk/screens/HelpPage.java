package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.KeyHintProvider.HelpFrame;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpSection;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * The controls (F1): first the keys worth knowing where the player is now, then every key the game
 * knows by where it applies, as the classic controls panel lists them, each key on its cap beside
 * what it does, with the player's own bindings.
 */
public final class HelpPage extends Page {
    private final HelpFrame frame = new HelpFrame();

    public HelpPage() {
        super("CONTROLS", ClassicWindow.HELP);
    }

    @Override
    public boolean offered(Boards b) {
        return true;
    }

    @Override
    protected void build(Boards b) {
        if (b.data.keyHints.hints.size > 0) {
            Sect here = sect("HERE");
            here.col(Col.start("KEY", 110f)).col(Col.start("DOES", 0f));
            for (int i = 0; i < b.data.keyHints.hints.size; i++) add(here, "here/" + i, b.data.keyHints.hints.get(i));
        }
        frame.clear();
        if (b.data.hints != null) b.data.hints.collectAll(frame);
        for (int i = 0; i < frame.sections.size; i++) {
            HelpSection hs = frame.sections.get(i);
            Sect s = sect(Fmt.caps(hs.title));
            s.col(Col.start("KEY", 110f)).col(Col.start("DOES", 0f));
            for (int j = 0; j < hs.hints.size; j++) add(s, "key/" + i + "/" + j, hs.hints.get(j));
        }
        if (sects.size == 0) sect("CONTROLS").empty("THE GAME LISTS NO KEYS HERE");
    }

    private static void add(Sect s, String id, KeyHint h) {
        Row r = s.row(id);
        r.add(Fmt.caps(h.key), Cell.Tone.KEY);
        r.add(Fmt.caps(h.label), Cell.Tone.BODY);
    }
}
