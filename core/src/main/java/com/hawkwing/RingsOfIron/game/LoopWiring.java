package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.stationsdocking.StationComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The round-2 loop's wiring: what ties factions and rank (category 1) to the systems built beside
 * them. Each of those left a hook with a stand-in; this points the hooks at the career.
 * <ul>
 *   <li>Encounters scale with {@link Difficulty} ({@link HostileTraffic#difficulty}).</li>
 *   <li>Ships of a faction hostile to all are anyone's to board ({@link Boarding#outlaw}).</li>
 *   <li>A rescued player wakes at one of the sponsor's stations, in the best ship their rank may
 *       requisition ({@link Rescue#sponsorStation}, {@link Rescue#requisition}).</li>
 *   <li>A ship the player beats (disabled, surrendered or wrecked within {@link #CREDIT_SECONDS}
 *       of the player's last hit on it) counts as destroyed with the factions, once
 *       ({@link FactionStanding#onShipDestroyed}): it costs standing with its owners and gains
 *       standing with their enemies, so beating pirates is how a career starts.</li>
 * </ul>
 */
public final class LoopWiring {
    /** Ships the service hands out, best first. Freighters are not requisitioned. */
    public static final String[] REQUISITION = {"gunship_heavy", GameScenario.PLAYER_SHIP};
    /** How long after the player's last hit a ship being beaten still counts as theirs, sim seconds. */
    public static final double CREDIT_SECONDS = 120;

    private final GameWorld world;
    /** Sim time of the player's last hit on each ship, by uuid. */
    private final Map<String, Double> lastHit = new HashMap<>();
    /** Ships already counted, by uuid, so a ship that comes back and is beaten again counts once. */
    private final Set<String> credited = new HashSet<>();

    private LoopWiring(GameWorld world) {
        this.world = world;
    }

    /** Wires {@code world}'s systems to its career. Call once all of them exist. */
    public static LoopWiring install(GameWorld world) {
        LoopWiring w = new LoopWiring(world);
        world.hostiles.difficulty = () -> Difficulty.of(world);
        world.boarding.outlaw = faction -> {
            FactionDef def = world.standing.factions().def(faction);
            return def != null && def.hostileToAll;
        };
        world.rescue.sponsorStation = station -> world.standing.factionOf(station) == world.standing.sponsor();
        world.rescue.requisition = new Rescue.Requisition() {
            @Override
            public String ship(Entity station, double credits) {
                return w.bestRequisition();
            }

            @Override
            public double price(String shipId) {
                return 0;
            }
        };
        world.combatEvents.impacts.add((signal, hit) -> w.hit(hit.owner, hit.target));
        world.combatEvents.detonations.add((signal, d) -> w.hit(d.owner, d.target));
        world.disablement.changes.add((signal, change) -> {
            if (change.was == null && change.now != null) w.beaten(change.ship);
        });
        return w;
    }

    /** The best ship of {@link #REQUISITION} the player's rank allows; the starter corvette always. */
    public String bestRequisition() {
        int rank = world.standing.record().rank;
        for (String id : REQUISITION) {
            if (world.getContent() != null && !world.getContent().defs.has("ship", id)) continue;
            if (world.standing.ranks().mayRequisition(rank, id)) return id;
        }
        return GameScenario.PLAYER_SHIP;
    }

    private void hit(Entity owner, Entity target) {
        if (owner == null || target == null || owner == target) return;
        if (owner.getComponent(PlayerControllerComponent.class) == null) return;
        if (target.getComponent(StationComponent.class) != null) return;
        String uuid = uuidOf(target);
        if (uuid != null) lastHit.put(uuid, world.engine.getSimSeconds());
    }

    private void beaten(Entity ship) {
        if (ship.getComponent(PlayerControllerComponent.class) != null) return;
        String uuid = uuidOf(ship);
        if (uuid == null || credited.contains(uuid)) return;
        Double at = lastHit.get(uuid);
        if (at == null || world.engine.getSimSeconds() - at > CREDIT_SECONDS) return;
        credited.add(uuid);
        world.standing.onShipDestroyed(ship);
    }

    /** Whether the player has been credited with beating the ship {@code uuid}. */
    public boolean credited(String uuid) {
        return credited.contains(uuid);
    }

    private static String uuidOf(Entity e) {
        UuidComponent u = e.getComponent(UuidComponent.class);
        return u == null ? null : u.id;
    }
}
