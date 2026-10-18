package com.ringsofiron.moddingdata;

import com.badlogic.ashley.core.Entity;

/**
 * Adds one module's components to an entity being spawned from a definition. Each module
 * registers its own assembler with {@link EntityFactory#registerAssembler}, so this module
 * never needs to know about physics, combat or crew components.
 */
@FunctionalInterface
public interface ComponentAssembler {
    /**
     * @param entity the new entity; already has a UUID, a transform and a DefRefComponent,
     *               and is not yet added to the engine
     * @param def    the merged definition; use {@code factory.defs().convert(def, MyDef.class)}
     */
    void assemble(Entity entity, DefEntry def, EntityFactory factory);
}
