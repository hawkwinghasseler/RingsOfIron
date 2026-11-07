package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.providers.KeyHintProvider.HelpFrame;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpSection;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.ClassicWindow;

/**
 * The controls (F1): first the keys worth knowing where the player is now, then every key the game
 * knows by where it applies, as the classic controls panel lists them, leaving out the section that
 * is all where the player is now; each key on its cap against what it does, with the player's own
 * bindings. A frame whose hint line already says where the player is now can list every key by
 * where it applies alone ({@link #byWhereOnly}).
 */
public final class HelpPage extends Page {
    private final HelpFrame frame = new HelpFrame();
    /** HERE's keys, each as its key and what it does. */
    private final com.badlogic.gdx.utils.ObjectSet<String> here = new com.badlogic.gdx.utils.ObjectSet<>();
    /** Whether HERE leads the keys. */
    private boolean leadHere = true;

    public HelpPage() {
        super("CONTROLS", ClassicWindow.HELP);
    }

    /** Lists every key by where it applies and no more: no HERE, and so no section left out for being all of it. */
    public HelpPage byWhereOnly() {
        leadHere = false;
        return this;
    }

    @Override
    public boolean offered(Boards b) {
        return true;
    }

    @Override
    protected void build(Boards b) {
        here.clear();
        if (leadHere && b.data.keyHints.hints.size > 0) {
            Sect s = sect("HERE");
            s.col(Col.end("", 84f)).col(Col.start("", 0f));
            for (int i = 0; i < b.data.keyHints.hints.size; i++) {
                KeyHint h = b.data.keyHints.hints.get(i);
                add(s, "here/" + i, h);
                here.add(h.key + "|" + h.label);
            }
        }
        frame.clear();
        if (b.data.hints != null) b.data.hints.collectAll(frame);
        for (int i = 0; i < frame.sections.size; i++) {
            HelpSection hs = frame.sections.get(i);
            // where the player is now is HERE already: its own section would say it all again
            if (allHere(hs)) continue;
            Sect s = sect(Fmt.caps(hs.title));
            s.col(Col.end("", 84f)).col(Col.start("", 0f));
            for (int j = 0; j < hs.hints.size; j++) add(s, "key/" + i + "/" + j, hs.hints.get(j));
        }
        if (sects.size == 0) sect("CONTROLS").empty("THE GAME LISTS NO KEYS HERE");
    }

    private boolean allHere(HelpSection hs) {
        if (here.size == 0 || hs.hints.size == 0) return false;
        for (int j = 0; j < hs.hints.size; j++) if (!here.contains(hs.hints.get(j).key + "|" + hs.hints.get(j).label)) return false;
        return true;
    }

    private static void add(Sect s, String id, KeyHint h) {
        Row r = s.row(id);
        r.add(Fmt.caps(h.key), Cell.Tone.KEY);
        r.add(Fmt.caps(h.label), Cell.Tone.BODY);
    }
}
