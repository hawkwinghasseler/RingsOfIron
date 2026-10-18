package com.ringsofiron.coreengine;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Module-global state that is not an entity component but must be saved, e.g. orbital-mechanics'
 * pending event queue or the bubble anchor. Register with
 * {@link SerializationService#addParticipant(SaveParticipant)}.
 *
 * Participants are read after all entities are loaded, so they can resolve {@link EntityRef}s.
 */
public interface SaveParticipant {
    /** Unique key for this participant's object in the save's "modules" section. */
    String saveKey();

    /** Writes fields into an object the service has already opened, e.g. {@code json.writeValue("x", x)}. */
    void write(Json json);

    /** Reads back what {@link #write(Json)} wrote. Not called if the save has no entry for this key. */
    void read(Json json, JsonValue data);
}
