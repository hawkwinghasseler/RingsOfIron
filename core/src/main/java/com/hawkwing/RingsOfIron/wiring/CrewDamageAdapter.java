package com.hawkwing.RingsOfIron.wiring;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.combatsys.damage.CrewDamageBridge;
import com.ringsofiron.combatsys.damage.HitCause;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink;
import com.ringsofiron.interiorgrid.crew.GridOccupantQuery;

/**
 * Lets combat-sys find and hurt crew through ai-crew's occupant query and damage sink. Every
 * weapon hit wounds ({@code CrewService.wounds}): a laser's burn arrives as {@code BURN}, kept
 * apart from {@code FIRE}, the health a crew member loses standing in a burning room.
 */
public class CrewDamageAdapter implements CrewDamageBridge {
    private final GridOccupantQuery occupants;
    private final CrewDamageSink sink;

    public CrewDamageAdapter(GridOccupantQuery occupants, CrewDamageSink sink) {
        this.occupants = occupants;
        this.sink = sink;
    }

    @Override
    public void occupantsAt(Entity ship, int x, int y, Array<Entity> out) {
        occupants.occupantsAt(ship, x, y, out);
    }

    @Override
    public void applyDamage(Entity crew, float amount, HitCause cause) {
        sink.applyDamage(crew, amount, toDamageCause(cause));
    }

    public static CrewDamageSink.DamageCause toDamageCause(HitCause cause) {
        if (cause == null) return CrewDamageSink.DamageCause.OTHER;
        switch (cause) {
            case PENETRATOR: return CrewDamageSink.DamageCause.PENETRATOR;
            case SPALL: return CrewDamageSink.DamageCause.SPALL;
            case FRAGMENT: return CrewDamageSink.DamageCause.FRAGMENT;
            case BLAST: return CrewDamageSink.DamageCause.BLAST;
            case FIRE: return CrewDamageSink.DamageCause.BURN;
            case IMPACT: return CrewDamageSink.DamageCause.IMPACT;
            default: return CrewDamageSink.DamageCause.OTHER;
        }
    }
}
