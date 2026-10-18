package com.ringsofiron.combatsys.events;

import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.utils.Array;

/**
 * The combat event bus. Shared by every combat system in one simulation; other modules
 * (ui-hud, audio-spatial, ai-crew) subscribe to the signals.
 *
 * Impacts and detonations are queued, not dispatched, so {@code ImpactResolutionSystem} can
 * resolve them in a deterministic order once per tick. The signals fire after resolution.
 */
public class CombatEvents {
    /** Raw impacts waiting to be resolved this tick. */
    public final Array<ImpactEvent> pendingImpacts = new Array<>();
    public final Array<DetonationEvent> pendingDetonations = new Array<>();

    /** Every impact, after it has been resolved. */
    public final Signal<ImpactEvent> impacts = new Signal<>();
    public final Signal<DetonationEvent> detonations = new Signal<>();
    /** One per impact on a ship with an interior grid. */
    public final Signal<HullHitEvent> hullHits = new Signal<>();
    /** What changed inside a ship after each impact or detonation, when anything did. */
    public final Signal<DamageReport> damage = new Signal<>();
    /** A weapon fired: for muzzle flashes and sound. */
    public final Signal<ShotFiredEvent> shots = new Signal<>();

    /** Current fixed tick, advanced by {@code BallisticsSystem}. */
    public long tick;
}
