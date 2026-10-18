package com.ringsofiron.physicsexterior;

import com.badlogic.ashley.signals.Listener;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.utils.Disposable;
import com.ringsofiron.orbital.events.OrbitalEventSystem;
import com.ringsofiron.orbital.events.SoiChange;
import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.physicsexterior.anchor.AnchorModel;
import com.ringsofiron.physicsexterior.anchor.KeplerAnchor;
import com.ringsofiron.physicsexterior.events.AnchorChangeEvent;

/**
 * Keeps orbital-mechanics' copy of the bubble anchor in step with the physics bubble, both ways.
 *
 * Bubble to rails: every anchor change on {@link PhysicsManager#anchorChanges} is forwarded, so a
 * new anchor or a rebase calls {@link OrbitalEventSystem#setAnchor} and a collapse onto rails calls
 * {@link OrbitalEventSystem#clearAnchor}. Rails to bubble: when the event system reports the
 * anchor crossing a sphere of influence, the bubble adopts the new parent and conic. Box2D bodies
 * keep their local positions; only the frame they are measured from changes parent.
 *
 * Construct once both exist; the current anchor is pushed to the event system straight away.
 */
public class OrbitalAnchorBridge implements Disposable {
    private final PhysicsManager physics;
    private final OrbitalEventSystem events;
    private final Listener<AnchorChangeEvent> anchorListener = this::onAnchorChange;
    private final Listener<SoiChange> soiListener = this::onSoiChange;
    /** Set while the bubble adopts an SOI change, so that change is not echoed back to rails. */
    private boolean adoptingSoiChange;

    public OrbitalAnchorBridge(PhysicsManager physics, OrbitalEventSystem events) {
        this.physics = physics;
        this.events = events;
        physics.anchorChanges.add(anchorListener);
        events.soiChanges.add(soiListener);
        forward(physics.getAnchorModel(), physics.getAnchorParentUuid(), physics.getSimSeconds());
    }

    private void onAnchorChange(Signal<AnchorChangeEvent> signal, AnchorChangeEvent event) {
        if (adoptingSoiChange) return;
        if (event.kind == AnchorChangeEvent.Kind.CLEARED) {
            events.clearAnchor();
        } else {
            forward(event.model, event.parentUuid, event.time);
        }
    }

    private void onSoiChange(Signal<SoiChange> signal, SoiChange change) {
        if (!change.anchor) return;
        adoptingSoiChange = true;
        try {
            physics.setAnchorModel(new KeplerAnchor(change.newOrbit), change.newParentUuid);
        } finally {
            adoptingSoiChange = false;
        }
    }

    /**
     * Hands the anchor's conic to the event system. A KeplerAnchor gives its orbit directly; any
     * other model around a named body is fitted with the conic through its current state. An
     * anchor with no parent or no gravity (deep space, the flight demo) is not tracked on rails.
     */
    private void forward(AnchorModel model, String parentUuid, double time) {
        KeplerOrbit orbit = null;
        if (model instanceof KeplerAnchor) {
            orbit = ((KeplerAnchor) model).getOrbit();
        } else if (model != null && model.getMu() > 0 && parentUuid != null) {
            double[] s = new double[4];
            model.stateAt(time, s);
            orbit = KeplerOrbit.fromState(s[0], s[1], s[2], s[3], model.getMu(), time);
        }
        if (orbit != null && parentUuid != null) {
            events.setAnchor(parentUuid, orbit);
        } else {
            events.clearAnchor();
        }
    }

    /** Stops listening; the event system keeps whatever anchor it last had. */
    @Override
    public void dispose() {
        physics.anchorChanges.remove(anchorListener);
        events.soiChanges.remove(soiListener);
    }
}
