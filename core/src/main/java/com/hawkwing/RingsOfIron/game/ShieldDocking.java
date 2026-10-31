package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.ringsofiron.combatsys.shields.ShieldCloudComponent;
import com.ringsofiron.stationsdocking.DockingSystem;

/**
 * Shields go down for docking and come back up after (combat-sys Design 5.11): a ship docked to
 * anything with its shields raised packs its cloud into its emitters, and raises it again once it
 * undocks, unless the crew lowered it themselves. The player hears of it.
 */
public class ShieldDocking extends EntitySystem {
    private static final ComponentMapper<ShieldCloudComponent> CLOUDS = ComponentMapper.getFor(ShieldCloudComponent.class);
    private final GameContext ctx;
    private final DockingSystem docking;
    private ImmutableArray<Entity> shielded;

    public ShieldDocking(GameContext ctx, DockingSystem docking) {
        this.ctx = ctx;
        this.docking = docking;
    }

    @Override
    public void addedToEngine(Engine engine) {
        shielded = engine.getEntitiesFor(Family.all(ShieldCloudComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < shielded.size(); i++) {
            Entity ship = shielded.get(i);
            ShieldCloudComponent cloud = CLOUDS.get(ship);
            boolean docked = docking.dockedTo(ship) != null;
            if (docked && cloud.raised) {
                cloud.raised = false;
                cloud.loweredForDock = true;
                if (ship == ctx.player()) ctx.notices.post("Shields lowered for docking", NoticeLog.Kind.INFO);
            } else if (!docked && cloud.loweredForDock) {
                cloud.raised = true;
                cloud.loweredForDock = false;
                if (ship == ctx.player()) ctx.notices.post("Shields raised", NoticeLog.Kind.INFO);
            }
        }
    }
}
