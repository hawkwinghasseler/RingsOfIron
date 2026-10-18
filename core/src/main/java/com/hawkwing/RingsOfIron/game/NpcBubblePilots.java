package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.pilot.NpcPilotComponent;
import com.ringsofiron.aicrew.pilot.PilotBurn;
import com.ringsofiron.coreengine.UuidRegistry;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands a ship that macro-sim brings into the bubble to ai-crew's NPC pilot, which flies the burns
 * its rails plan had left. Ships without propulsion get a default freighter drive.
 */
public class NpcBubblePilots implements BubbleTransitionListener {
    private final UuidRegistry uuids;

    public NpcBubblePilots(UuidRegistry uuids) {
        this.uuids = uuids;
    }

    @Override
    public void onEnterBubble(String uuid, List<Burn> cancelledBurns, double t) {
        Entity ship = uuids.getEntity(uuid);
        if (ship == null) return;
        if (ship.getComponent(ThrusterComponent.class) == null) {
            ship.add(new ThrusterComponent(100_000f, 50_000f, 20_000f, 2_000_000f));
        }
        if (ship.getComponent(FlightControlComponent.class) == null) ship.add(new FlightControlComponent());
        List<PilotBurn> burns = new ArrayList<>();
        for (Burn b : cancelledBurns) burns.add(new PilotBurn(b.time, b.prograde, b.radial));
        ship.add(new NpcPilotComponent(burns));
    }

    @Override
    public void onExitBubble(String uuid, double t) {
        Entity ship = uuids.getEntity(uuid);
        if (ship != null) ship.remove(NpcPilotComponent.class);
    }
}
