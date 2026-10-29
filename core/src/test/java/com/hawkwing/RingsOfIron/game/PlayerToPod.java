package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.interiorgrid.EscapePodLayout;
import com.ringsofiron.interiorgrid.SubsystemComponent;

/**
 * Puts the player's figure in the bay of {@code ship}'s first usable escape pod, as the player would
 * walk it there: the game never walks the figure itself.
 */
final class PlayerToPod {
    private PlayerToPod() {
    }

    static void board(GameWorld world, Entity ship) {
        Entity pod = AbandonShip.usablePods(ship).get(0);
        int[] bay = EscapePodLayout.bayTile(pod.getComponent(SubsystemComponent.class).footprint);
        world.bridge.stand();
        GridPositionComponent p = world.captain.avatar().getComponent(GridPositionComponent.class);
        p.stop();
        p.setTile(bay[0], bay[1]);
    }
}
