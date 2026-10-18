package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import java.util.EnumSet;
import java.util.Set;

/**
 * Consoles the crew can walk up to and use: one on each helm, weapons, life support and reactor
 * aboard a ship, at the system's footprint origin. The HUD shows "Press [E]" near one and opens
 * its window. Call once a ship spawned from data has its interior and subsystems.
 */
public final class ShipConsoles {
    /** The systems that get a console. */
    public static final Set<SubsystemType> USABLE =
            EnumSet.of(SubsystemType.HELM, SubsystemType.WEAPONS, SubsystemType.LIFE_SUPPORT, SubsystemType.REACTOR);

    private ShipConsoles() {}

    /** Adds a console to each usable system of {@code ship} that has none yet. Returns how many it added. */
    public static int install(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return 0;
        UuidComponent uuid = ship.getComponent(UuidComponent.class);
        int added = 0;
        for (Entity system : registry.subsystems) {
            SubsystemComponent sub = system.getComponent(SubsystemComponent.class);
            if (sub == null || !USABLE.contains(sub.type)) continue;
            if (system.getComponent(InteractableConsoleComponent.class) != null) continue;
            InteractableConsoleComponent console = new InteractableConsoleComponent(sub.type.name().toLowerCase(),
                    title(sub.type), ship, sub.footprint.originX, sub.footprint.originY);
            if (uuid != null) console.shipUuid = uuid.id;
            system.add(console);
            added++;
        }
        return added;
    }

    /** "LIFE_SUPPORT" as "Life support". */
    static String title(SubsystemType type) {
        String name = type.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
