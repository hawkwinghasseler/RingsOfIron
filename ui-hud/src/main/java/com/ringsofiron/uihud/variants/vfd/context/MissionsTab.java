package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.MissionProvider.Job;
import com.ringsofiron.uihud.providers.MissionProvider.MissionBoard;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The station's Missions (functional-context 10.3): the offers, each with its giver (its terms, its
 * note and why it is locked under the list), Take on the focused one; the faction tension line; and
 * the contracts already held, one line each with Give up, so the player can weigh a new job against
 * them. Giving one up takes a second press.
 */
final class MissionsTab extends SectionTab {
    static final int OFFER = ITEM, HELD = ITEM + 1;
    static final int TAKE = 0, GIVE_UP = 1;
    /** How long a first press of Give up waits for the second. */
    static final float CONFIRM_FOR = 3f;

    private MissionBoard board;
    /** The contract a first press of Give up is waiting on, and how long it still waits. */
    private long arming = -1;
    private float armLeft;

    MissionsTab() {
        super("MISSIONS", ClassicWindow.MISSIONS, "J");
        keys.define(2, 6);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.docked() && !b.missions.stationName.isEmpty();
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        if (armLeft > 0f && (armLeft -= delta) <= 0f) arming = -1;
    }

    @Override
    void build(Boards b, HudData d) {
        board = b.missions;
        head(board.offers.size == 0 ? "OFFERS" : "OFFERS: " + board.offers.size);
        for (int i = 0; i < board.offers.size; i++) {
            Job j = board.offers.get(i);
            add(OFFER, i, cols.clear().left(j.title.toUpperCase(Locale.ROOT), 50).left(j.giver.toUpperCase(Locale.ROOT), 26)
                    .left(j.locked.isEmpty() ? "" : "LOCKED", 8).toString());
        }
        if (board.offers.size == 0) note("NO OFFERS HERE TODAY");
        if (!board.tension.isEmpty()) note(board.tension.toUpperCase(Locale.ROOT));
        head(board.held.size == 0 ? "HELD" : "HELD: " + board.held.size);
        for (int i = 0; i < board.held.size; i++) {
            Job j = board.held.get(i);
            add(HELD, i, cols.clear().left(j.title.toUpperCase(Locale.ROOT), 50).left(j.giver.toUpperCase(Locale.ROOT), 34).toString());
        }
        if (board.held.size == 0) note("NO CONTRACTS HELD");
    }

    @Override
    void keysFor(int focus) {
        keys.set(TAKE, "TAKE", false).set(GIVE_UP, "GIVE UP", false);
        if (board == null || focus < 0 || focus >= kind.size) return;
        if (kind.get(focus) == OFFER) keys.set(TAKE, "TAKE", board.offers.get(ref.get(focus)).locked.isEmpty());
        else if (kind.get(focus) == HELD) keys.set(GIVE_UP, arming == board.held.get(ref.get(focus)).id ? "SURE?" : "GIVE UP", true);
    }

    @Override
    void detailFor(int focus) {
        if (board == null || focus >= kind.size) return;
        Job j = kind.get(focus) == OFFER ? board.offers.get(ref.get(focus)) : kind.get(focus) == HELD ? board.held.get(ref.get(focus)) : null;
        if (j == null) return;
        tell(j.terms);
        if (!j.locked.isEmpty()) tell("LOCKED: " + j.locked);
        if (!j.note.isEmpty()) tell(j.note);
    }

    @Override
    void press(int k, int focus) {
        HudData d = modes.data();
        if (d == null || d.missions == null || board == null || focus < 0 || focus >= kind.size) return;
        if (kind.get(focus) == OFFER) {
            modes.say("STATION", d.missions.accept(board.offers.get(ref.get(focus)).id));
        } else if (kind.get(focus) == HELD) {
            Job j = board.held.get(ref.get(focus));
            if (arming != j.id) {
                arming = j.id;
                armLeft = CONFIRM_FOR;
                modes.say("STATION", "Press Give up again to abandon " + j.title);
                keysFor(focus);
                return;
            }
            arming = -1;
            modes.say("STATION", d.missions.abandon(j.id));
        }
        modes.refresh();
    }
}
