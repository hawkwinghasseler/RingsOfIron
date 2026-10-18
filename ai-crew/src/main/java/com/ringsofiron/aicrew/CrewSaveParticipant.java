package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.coreengine.SerializationService;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;

/**
 * Makes crew save and load cleanly. Saves keep each crew member's stats, health, state
 * and tile, and which ship they are aboard. The job in hand is not saved: after a load
 * every claimed task, carry and treatment is dropped, and crew pick their jobs afresh
 * from what the hazard scan and health systems post. Behavior trees are not saved
 * either; each AI crew member gets a fresh one. NPC combat pilots get their targets back.
 *
 * <pre>
 * CrewSaveParticipant.install(crewService, gameEngine);
 * </pre>
 */
public class CrewSaveParticipant implements SaveParticipant {

    private static final Family CREW = Family.all(CrewMemberComponent.class, GridPositionComponent.class).get();
    private static final Family FIGHTING = Family.all(NpcCombatComponent.class).get();

    private final CrewService service;
    private final GameEngine engine;

    private CrewSaveParticipant(CrewService service, GameEngine engine) {
        this.service = service;
        this.engine = engine;
    }

    /** Registers crew saving with {@code engine}'s serializer. */
    public static CrewSaveParticipant install(CrewService service, GameEngine engine) {
        CrewSaveParticipant participant = new CrewSaveParticipant(service, engine);
        SerializationService serializer = engine.getSerializationService();
        serializer.skipComponent(BehaviorTreeComponent.class);
        serializer.addParticipant(participant);
        return participant;
    }

    @Override
    public String saveKey() {
        return "ai-crew";
    }

    @Override
    public void write(Json json) {
        // Nothing to save; the entry exists so read() runs after a load
    }

    @Override
    public void read(Json json, JsonValue data) {
        service.clearPathfinders();
        ImmutableArray<Entity> crew = service.engine().getEntitiesFor(CREW);
        for (int i = 0; i < crew.size(); i++) {
            Entity e = crew.get(i);
            GridPositionComponent p = service.position(e);
            p.ship = p.shipRef == null ? null : p.shipRef.get(engine.getUuidRegistry());
            if (e.getComponent(PlayerAvatarComponent.class) == null
                    && e.getComponent(BehaviorTreeComponent.class) == null) {
                CrewFactory.addBrain(service, e);
            }
        }
        ImmutableArray<Entity> fighting = service.engine().getEntitiesFor(FIGHTING);
        for (int i = 0; i < fighting.size(); i++) {
            NpcCombatComponent combat = fighting.get(i).getComponent(NpcCombatComponent.class);
            combat.target = combat.targetRef == null ? null : combat.targetRef.get(engine.getUuidRegistry());
        }
    }
}
