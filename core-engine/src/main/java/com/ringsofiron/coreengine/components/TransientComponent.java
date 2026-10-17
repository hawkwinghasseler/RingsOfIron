package com.ringsofiron.coreengine.components;

import com.badlogic.ashley.core.Component;

/**
 * A tag component used to identify entities that should not be serialized when saving the game state.
 * Examples include temporary visual effects, projectiles, or cached data entities.
 */
public class TransientComponent implements Component {
    // Empty tag component
}
