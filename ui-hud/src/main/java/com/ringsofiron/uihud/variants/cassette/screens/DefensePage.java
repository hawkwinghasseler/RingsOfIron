package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.ThreatProvider.Side;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The tactical board's Defense tab (the Stations spec's 10.2): the shield, on a hull with an emitter, by its
 * mode and the mass in its cloud (Engineering's Shields tab has each emitter); the decoy, ready or the
 * seconds until it is; the missiles left to fire; then every missile coming in, by who fired it, its range
 * and when it reaches the ship, the soonest first; then the point defense, mount by mount, with what it is
 * doing. Point defense hunts missiles by itself; one in the captain's hand hunts none, and says so.
 *
 * <p>All of it is the player's ship's; at a prize's weapons console the board falls back to Engineering.
 */
final class DefensePage extends ListPage {
    static final int SHIELD = 0, DECOY = 1, MISSILES = 2, IN_HEAD = 3, INBOUND = 4, PD_HEAD = 5, PD = 6, NO_PD = 7;

    /** Each row's kind, and the index it reads: a missile track's or a mount's. */
    final IntArray kinds = new IntArray(), index = new IntArray();
    private HudData data;
    private boolean own;
    private int inbound;
    private final IntArray in = new IntArray();

    DefensePage() {
        super("DEFENSE", "TAC", ClassicWindow.CONSOLE);
    }

    @Override
    boolean offered() {
        return own;
    }

    @Override
    boolean fitsSide() {
        return true;
    }

    @Override
    void refresh(Boards b) {
        data = b.data;
        own = data != null && ConsolePanels.ship(data) == data.ctx.ship;
        kinds.clear();
        index.clear();
        inbound = 0;
        if (!own) return;
        add(SHIELD, -1);
        add(DECOY, -1);
        add(MISSILES, -1);
        // The missiles coming in, the soonest to strike first
        IntArray in = this.in;
        in.clear();
        for (int i = 0; i < data.threatFrame.missiles.size; i++) if (data.threatFrame.missiles.get(i).side == Side.INCOMING) in.add(i);
        inbound = in.size;
        if (in.size > 0) {
            add(IN_HEAD, -1);
            for (int n = 0; n < in.size; n++) {
                int best = n;
                for (int k = n + 1; k < in.size; k++) if (sooner(track(in.get(k)), track(in.get(best)))) best = k;
                in.swap(n, best);
                add(INBOUND, in.get(n));
            }
        }
        add(PD_HEAD, -1);
        boolean any = false;
        WeaponFrame w = data.weaponFrame;
        for (int i = 0; i < w.mounts.size; i++) {
            if (w.mounts.get(i).kind != Kind.POINT_DEFENSE) continue;
            add(PD, i);
            any = true;
        }
        if (!any) add(NO_PD, -1);
        keepSelection();
    }

    private void add(int kind, int i) {
        kinds.add(kind);
        index.add(i);
    }

    private MissileTrack track(int i) {
        return data == null || i < 0 || i >= data.threatFrame.missiles.size ? null : data.threatFrame.missiles.get(i);
    }

    private static boolean sooner(MissileTrack a, MissileTrack b) {
        float ta = a == null || Float.isNaN(a.timeToImpact) ? Float.MAX_VALUE : a.timeToImpact;
        float tb = b == null || Float.isNaN(b.timeToImpact) ? Float.MAX_VALUE : b.timeToImpact;
        return ta < tb;
    }

    // --- The words -----------------------------------------------------------------------------

    /** The shield in a line: "RAISED  412 OF 600 KG", "NO EMITTER FITTED". */
    static String shield(ShieldStatus s) {
        if (!s.present) return "NO EMITTER FITTED";
        String mode = ShieldsPage.mode(s);
        return mode + "  " + Fmt.thousands(Math.round(s.cloud)) + " OF " + Fmt.thousands(Math.round(s.capacity)) + " KG";
    }

    /** Whether the shield is in trouble: every emitter tripped, or the power stunned. */
    static boolean shieldBad(ShieldStatus s) {
        return s.present && ((s.emittersTripped > 0 && s.emittersWorking == 0) || s.powerStunned);
    }

    /** The decoy: READY, or the seconds until it is. */
    static String decoy(WeaponFrame w) {
        return w.decoyCooldown > 0f ? String.format(Locale.ROOT, "READY IN %.1f S", w.decoyCooldown) : "READY";
    }

    /** A missile coming in: who fired it, and when it strikes ("0:13") or that it is coasting. */
    static String when(MissileTrack t) {
        if (!Float.isNaN(t.timeToImpact)) return "HITS " + YardText.clock(t.timeToImpact);
        return t.burning ? "CLOSING" : "COASTING";
    }

    /** What a point-defense mount is doing: hunting, engaging, in hand (hunts nothing), or its state. */
    static String pd(Mount m, SubsystemComponent sys) {
        if (!m.working()) return GunPage.stateWord(m, sys);
        if (m.controller == Controller.PLAYER) return "IN YOUR HAND: HUNTS NONE";
        if (m.engaging || m.defending) return "ENGAGING";
        return "HUNTING";
    }

    // --- The list ------------------------------------------------------------------------------

    @Override
    int count() {
        return kinds.size;
    }

    @Override
    boolean selectable(int i) {
        return false;
    }

    @Override
    void row(YardPaint p, int i, int r, boolean s) {
        if (data == null) return;
        WeaponFrame w = data.weaponFrame;
        switch (kinds.get(i)) {
            case SHIELD: {
                ShieldStatus st = data.shieldStatus;
                L.at(p, 1, r, "SHIELD", DIM);
                L.at(p, 11, r, Ledger.cut(shield(st), L.cols - 12), shieldBad(st) ? Yard.P_RED : st.present ? AMBER : DIM);
                return;
            }
            case DECOY:
                L.at(p, 1, r, "DECOY", DIM);
                L.at(p, 11, r, Ledger.cut(decoy(w), L.cols - 12), AMBER);
                return;
            case MISSILES:
                L.at(p, 1, r, "MISSILES", DIM);
                L.at(p, 11, r, Ledger.cut(w.missilesLeft == 1 ? "1 LEFT TO FIRE" : w.missilesLeft + " LEFT TO FIRE", L.cols - 12),
                        w.missilesLeft == 0 ? DIM : AMBER);
                return;
            case IN_HEAD:
                L.rule(p, r - 1);
                L.at(p, 1, r, "INBOUND", Yard.P_RED);
                L.right(p, L.cols - 1, r, inbound == 1 ? "1 MISSILE" : inbound + " MISSILES", Yard.P_RED);
                return;
            case INBOUND: {
                MissileTrack t = track(index.get(i));
                if (t == null) return;
                L.at(p, 1, r, Ledger.cut(t.shooter.isEmpty() ? "MISSILE" : "FROM " + Fmt.upper(t.shooter), 18), Yard.P_RED);
                L.right(p, 29, r, ContactsPage.range(t.range), Yard.P_RED);
                L.at(p, 31, r, Ledger.cut(when(t), L.cols - 32), Yard.P_RED);
                return;
            }
            case PD_HEAD:
                L.rule(p, r - 1);
                L.at(p, 1, r, "POINT DEFENSE", DIM);
                L.right(p, L.cols - 1, r, w.pointDefenseActive ? "ENGAGING" : "AUTO", DIM);
                return;
            case PD: {
                int mi = index.get(i);
                if (mi >= w.mounts.size) return;
                Mount m = w.mounts.get(mi);
                Color ink = m.working() ? AMBER : Yard.P_RED;
                L.at(p, 1, r, Ledger.cut(m.name.isEmpty() ? "PD " + (m.index + 1) : Fmt.upper(m.name), 14), ink);
                L.at(p, 16, r, Ledger.cut(pd(m, WeaponsPage.system(data, m)), L.cols - 17), m.controller == Controller.PLAYER ? Yard.P_RED : ink);
                return;
            }
            default:
                L.at(p, 1, r, "NO POINT DEFENSE FITTED", DIM);
        }
    }

    @Override
    void keys() {
    }

    @Override
    void press(int k) {
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 4f, bw = w - 16f, bh = h - 8f;
        BigConsole.bay(p, bx, by, bw, bh, null, false);
        int cols = Ledger.colsIn(bw - 36f), rows = Math.max(6, Ledger.rowsIn(bh - 24f));
        L.set(bx + 12f, by + 10f, cols, rows);
        L.under(p, false);
        String right = inbound > 0 ? inbound + " INBOUND" : data != null && data.shieldStatus.present ? ShieldsPage.mode(data.shieldStatus) : null;
        title(p, "DEFENSE", right);
        drawList(p, hits, 1, rows - 1);
        L.over(p);
    }
}
