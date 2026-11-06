package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CrewProvider;
import com.ringsofiron.uihud.providers.CrewProvider.CrewSheet;
import com.ringsofiron.uihud.providers.CrewProvider.Member;
import com.ringsofiron.uihud.providers.CrewProvider.Prisoner;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

import java.util.Locale;

/**
 * The ship's Crew (functional-context 10.2): today's crew roster, a member a row with their role,
 * skills, condition, morale and wage (their record under the list), with the EVA order the game offers
 * for them and Dismiss while docked (twice, since it can't be undone); the suits at battle stations and
 * the hold on EVA for everyone; and the prisoners aboard with what can be done with them here, and the
 * taken ship's to bring aboard.
 */
final class CrewTab extends SectionTab {
    static final int MEMBER = ITEM, PRISONER = ITEM + 1, ALONGSIDE = ITEM + 2;
    static final int ORDER = 0, SECOND = 1, SUITS = 2, EVA = 3;
    static final float CONFIRM_FOR = 3f;

    private CrewSheet sheet;
    private long arming = -1;
    private float armLeft;

    CrewTab() {
        super("CREW", ClassicWindow.CREW, "I");
        keys.define(4, 6);
        hasDetail = true;
    }

    @Override
    boolean offered(Boards b, HudData d) {
        return b.crew.isOpen();
    }

    @Override
    void update(Boards b, HudData d, float delta) {
        if (armLeft > 0f && (armLeft -= delta) <= 0f) {
            arming = -1;
            keysFor(pages.focus());
        }
    }

    @Override
    void build(Boards b, HudData d) {
        sheet = b.crew;
        head(sheet.summary.isEmpty() ? "ABOARD" : "ABOARD: " + sheet.summary.toUpperCase(Locale.ROOT));
        if (sheet.crew.size > 0) {
            note(cols.clear().left("NAME", 15).gap(1).left("ROLE", 10).gap(1).left("SKILLS", 32).gap(2).left("CONDITION", 28).gap(2).left("MORALE", 13)
                    .gap(1).left("WAGE", 14).toString());
        }
        for (int i = 0; i < sheet.crew.size; i++) {
            Member m = sheet.crew.get(i);
            // "Morale 40% (slacking)" to "40% SLACKING"
            String morale = m.morale.toUpperCase(Locale.ROOT).replace("MORALE ", "").replace("(", "").replace(")", "");
            add(MEMBER, i, cols.clear().left(m.name.toUpperCase(Locale.ROOT), 15).gap(1).left(m.role.toUpperCase(Locale.ROOT), 10).gap(1)
                    .left(m.skills.toUpperCase(Locale.ROOT), 32).gap(2).left(m.condition.toUpperCase(Locale.ROOT), 28).gap(2).left(morale, 13).gap(1)
                    .left(m.wage.toUpperCase(Locale.ROOT), 14).toString());
        }
        if (!sheet.suits.isEmpty()) note(sheet.suits.toUpperCase(Locale.ROOT));
        if (!sheet.eva.isEmpty()) note(sheet.eva.toUpperCase(Locale.ROOT));
        if (sheet.prisoners.size > 0 || sheet.prisonersAlongside) {
            head(sheet.prisoners.size == 0 ? "PRISONERS" : "PRISONERS: " + sheet.prisoners.size);
            for (int i = 0; i < sheet.prisoners.size; i++) {
                Prisoner p = sheet.prisoners.get(i);
                add(PRISONER, i, cols.clear().left(p.name.toUpperCase(Locale.ROOT), 18).left(p.detail.toUpperCase(Locale.ROOT), 30)
                        .left(p.condition.toUpperCase(Locale.ROOT), 20).left(p.offersToJoin ? "OFFERS TO JOIN" : "", 16).toString());
            }
            if (sheet.prisonersAlongside) add(ALONGSIDE, 0, "THE TAKEN SHIP'S PRISONERS, TO BRING ABOARD");
        }
    }

    @Override
    void keysFor(int focus) {
        boolean member = sheet != null && focus >= 0 && focus < kind.size && kind.get(focus) == MEMBER;
        boolean prisoner = sheet != null && focus >= 0 && focus < kind.size && kind.get(focus) == PRISONER;
        boolean alongside = sheet != null && focus >= 0 && focus < kind.size && kind.get(focus) == ALONGSIDE;
        keys.set(ORDER, "", false).set(SECOND, "", false);
        if (member) {
            Member m = sheet.crew.get(ref.get(focus));
            keys.set(ORDER, m.evaAction.isEmpty() ? "EVA" : m.evaAction.toUpperCase(Locale.ROOT), !m.evaAction.isEmpty());
            keys.set(SECOND, arming == m.id ? "SURE?" : "DISMISS", m.dismissable);
        } else if (prisoner) {
            Prisoner p = sheet.prisoners.get(ref.get(focus));
            keys.set(ORDER, p.option.isEmpty() ? "SETTLE" : p.option.toUpperCase(Locale.ROOT), !p.option.isEmpty());
            keys.set(SECOND, "RECRUIT", p.offersToJoin);
        } else if (alongside) {
            keys.set(ORDER, "TAKE THEM", true);
        }
        keys.set(SUITS, "SUITS", sheet != null && !sheet.suits.isEmpty());
        keys.set(EVA, "HOLD EVA", sheet != null && !sheet.eva.isEmpty());
    }

    @Override
    void detailFor(int focus) {
        if (sheet == null || focus >= kind.size) return;
        if (kind.get(focus) == MEMBER) {
            Member m = sheet.crew.get(ref.get(focus));
            if (!m.history.isEmpty()) tell(m.name + ": " + m.history);
        } else if (kind.get(focus) == PRISONER) {
            Prisoner p = sheet.prisoners.get(ref.get(focus));
            tell(p.option.isEmpty() ? "NOTHING CAN BE DONE WITH THEM UNTIL THE SHIP DOCKS" : p.option + " THEM HERE");
        }
    }

    @Override
    void press(int k, int focus) {
        HudData d = modes.data();
        CrewProvider p = d == null ? null : d.crew;
        if (p == null || sheet == null) return;
        String said = null;
        if (k == SUITS) {
            said = p.toggleSuits();
        } else if (k == EVA) {
            said = p.toggleEva();
        } else if (focus >= 0 && focus < kind.size) {
            int r = ref.get(focus);
            switch (kind.get(focus)) {
                case MEMBER: {
                    Member m = sheet.crew.get(r);
                    if (k == ORDER) {
                        said = p.evaOrder(m.id);
                    } else if (arming != m.id) {
                        arming = m.id;
                        armLeft = CONFIRM_FOR;
                        modes.say("CREW", "Press Dismiss again to let " + m.name + " go");
                        keysFor(focus);
                        return;
                    } else {
                        arming = -1;
                        said = p.dismiss(m.id);
                    }
                    break;
                }
                case PRISONER: {
                    Prisoner q = sheet.prisoners.get(r);
                    said = k == ORDER ? p.settlePrisoner(q.id) : p.recruit(q.id);
                    break;
                }
                case ALONGSIDE:
                    said = p.takePrisoners();
                    break;
                default:
            }
        }
        modes.say("CREW", said);
        modes.refresh();
    }
}
