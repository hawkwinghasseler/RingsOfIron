package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.combatsys.defs.CombatDefs;
import com.ringsofiron.combatsys.defs.LoadoutDef;
import com.ringsofiron.combatsys.modding.Loadouts;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.MacroSimulation;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.hostile.HostileTrafficController;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static com.hawkwing.RingsOfIron.game.HostileTraffic.BATTERY;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.GUNSHIP;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.PIRATES;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.PIRATE_CREW;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.PIRATE_GUNSHIP;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.RAIDER;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.SELENE;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.SELENE_CREW;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.SPAWN_CLEARANCE;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.SPONSOR;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.SPONSOR_CREW;
import static com.hawkwing.RingsOfIron.game.HostileTraffic.TRADERS;

/**
 * {@link HostileTraffic}'s spawning: the pirate population topped up to the difficulty, raids, wings,
 * patrols, and the pirate base's moored gun platforms, each a crewed ship armed from its loadouts.
 */
final class HostileSpawns {

    private static final CrewRole[] CREW_ROLES = {CrewRole.CREWMAN, CrewRole.MARINE, CrewRole.ENGINEER,
            CrewRole.MEDIC, CrewRole.MARINE, CrewRole.CREWMAN};
    private final HostileTraffic traffic;
    private final GameContext ctx;
    private final CrewService crewService;
    private final MacroSimulation macroSim;
    private final FactionStanding standing;
    private final CombatDefs combatDefs;
    private final ContractBoard contracts;
    private final Supplier<WorldEvents> events;
    private final HostileTrafficController controller;
    /** The pirates' and patrols' name counter, saved with the game. */
    int spawned;
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    HostileSpawns(HostileTraffic traffic, GameContext ctx, CrewService crewService, MacroSimulation macroSim,
                  FactionStanding standing, CombatDefs combatDefs, ContractBoard contracts, Supplier<WorldEvents> events,
                  HostileTrafficController controller) {
        this.traffic = traffic;
        this.ctx = ctx;
        this.crewService = crewService;
        this.macroSim = macroSim;
        this.standing = standing;
        this.combatDefs = combatDefs;
        this.contracts = contracts;
        this.events = events;
        this.controller = controller;
    }

    /**
     * Spawns pirates until the population is what {@link HostileTraffic#difficulty} asks for; once the player flies
     * a gunship, an ace takes one of the pirates' gunships ({@link PirateAces#gunshipAce}).
     */
    void topUp(double now, boolean anywhere) {
        if (traffic.lurks.length == 0) return;
        double d = traffic.difficulty.getAsDouble();
        int extra = traffic.extraRaiders.getAsInt();
        int raiders = HostileTraffic.raidersFor(d) + extra - count(RAIDER), gunships = HostileTraffic.gunshipsFor(d) - countGunships();
        for (int i = 0; i < gunships; i++) spawnPirate(PIRATE_GUNSHIP, now, anywhere, null);
        if (Difficulty.fliesAtLeast(standing, ctx.player(), ctx.content(), PIRATE_GUNSHIP)) {
            String ace = PirateAces.gunshipAce(ctx.engine, combatDefs, () -> events.get().seed, controller.all(), d);
            if (ace != null) {
                HostileComponent h = controller.get(ace);
                ctx.notices.post("Word on the lanes: the ace " + h.displayName + " flies a pirate gunship now",
                        NoticeLog.Kind.WARNING);
                PirateAces.postBounty(ctx.engine, macroSim, standing, contracts, ace);
            }
        }
        // The base's band gathers at the base; the rest at the lurks in turn
        int atBase = traffic.extraLurk == null ? 0 : Math.max(0, extra - countAt(traffic.extraLurk));
        for (int i = 0; i < raiders; i++) spawnPirate(RAIDER, now, anywhere, i < atBase ? traffic.extraLurk : null);
    }

    int countGunships() {
        int n = 0;
        for (HostileComponent h : controller.all().values()) if (h.isPirate() && HostileTraffic.gunshipHull(h.shipId)) n++;
        return n;
    }

    int count(String shipId) {
        int n = 0;
        for (HostileComponent h : controller.all().values()) if (h.isPirate() && !h.moored() && shipId.equals(h.shipId)) n++;
        return n;
    }

    int countAt(String lurk) {
        int n = 0;
        for (HostileComponent h : controller.all().values()) if (h.isPirate() && !h.moored() && lurk.equals(h.stationUuid)) n++;
        return n;
    }

    /**
     * Spawns a pirate at {@code at} if given (and out of the player's sight), else at the next lurk that is.
     * A raider with no place given flies on the wing of a raider leading fewer than the difficulty's
     * {@link Difficulty#wingSize}, by its lurk.
     */
    void spawnPirate(String shipId, double now, boolean anywhere, String at) {
        String leader = RAIDER.equals(shipId) && at == null ? openWing(Difficulty.wingSize(traffic.difficulty.getAsDouble())) : null;
        String[] choices = at != null && (anywhere || !nearPlayer(at, now)) ? new String[]{at} : traffic.lurks;
        for (int k = 0; k < choices.length; k++) {
            String lurk = leader != null ? controller.get(leader).stationUuid : choices[(spawned + k) % choices.length];
            if (!anywhere && nearPlayer(lurk, now)) {
                if (leader == null) continue;
                leader = null;
                k = -1;
                continue;
            }
            spawnPirateAt(shipId, lurk, leader, null);
            return;
        }
    }

    /**
     * A pirate of {@code shipId} lurking by {@code lurk}, armed from its hull's loadouts and flying as
     * {@code leader}'s wingman if given; its uuid, or null if the data has no such ship. {@code loadoutId}
     * names its loadout; null picks one for the difficulty.
     */
    String spawnPirateAt(String shipId, String lurk, String leader, String loadoutId) {
        Entity ship = spawnShip(shipId, PIRATE_CREW);
        if (ship == null) return null;
        String name = (HostileTraffic.gunshipHull(shipId) ? "Pirate gunship " : "Pirate raider ") + (++spawned);
        LoadoutDef loadout = loadoutId != null ? combatDefs.loadouts.get(loadoutId)
                : Loadouts.pick(combatDefs, shipId, traffic.difficulty.getAsDouble(), false, loadoutDice(shipId, spawned));
        if (loadout != null) Loadouts.apply(ship, loadout, combatDefs);
        HostileComponent h = new HostileComponent(name, shipId, PIRATES, HostileComponent.Role.PIRATE,
                HostileTraffic.firepower(ship));
        h.loadout = loadout == null ? null : loadout.id;
        h.wingOf = leader;
        // Spawned mid-update, the ship isn't registered by uuid until the update ends
        ship.add(h);
        String uuid = ship.getComponent(UuidComponent.class).id;
        macroSim.addPirate(uuid, h, lurk);
        return uuid;
    }

    /**
     * The dice the {@code n}th spawn's loadout is drawn with: the career's seed ({@link WorldEvents#seed},
     * saved with the game) mixed with the spawn count and the hull, so two careers meet different fits
     * and the same seed meets the same ones (round 6: the spawn count and hull alone gave every career
     * the same fits).
     */
    Random loadoutDice(String shipId, int n) {
        return new Random(NewGame.mix(events.get().seed, n * 7_919L + shipId.hashCode()));
    }

    /**
     * Raiders come to work {@code station}'s lanes: {@code count} more pirates of {@link HostileTraffic#RAIDER}
     * lurking by it, on top of the usual population ({@link WorldEvents}' raid). Call outside the
     * engine's update. Returns their uuids.
     */
    List<String> raid(String station, int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uuid = spawnPirateAt(RAIDER, station, null, null);
            if (uuid != null) out.add(uuid);
        }
        return out;
    }

    /**
     * {@code count} gun platforms of {@link HostileTraffic#BATTERY} moored at {@code station} (the pirate base's guns),
     * each {@code spacing} m further behind it along its orbit, armed from their loadouts like a spawn.
     * Call outside the engine's update. Returns their uuids.
     */
    List<String> moorGuns(String station, int count, double spacing) {
        List<String> out = new ArrayList<>();
        boolean railgun = false;
        for (int i = 0; i < count; i++) {
            Entity ship = spawnShip(BATTERY, PIRATE_CREW);
            if (ship == null) break;
            String name = "Den battery " + (++spawned);
            java.util.Random dice = loadoutDice(BATTERY, spawned);
            LoadoutDef loadout = Loadouts.pick(combatDefs, BATTERY, traffic.difficulty.getAsDouble(), false, dice);
            // One railgun platform at most: the next draws again from the fits without one
            if (loadout != null && railgun && railgun(loadout)) loadout = pickWithoutRailgun(traffic.difficulty.getAsDouble(), dice);
            railgun |= loadout != null && railgun(loadout);
            if (loadout != null) Loadouts.apply(ship, loadout, combatDefs);
            HostileComponent h = new HostileComponent(name, BATTERY, PIRATES, HostileComponent.Role.PIRATE, HostileTraffic.firepower(ship));
            h.loadout = loadout == null ? null : loadout.id;
            h.mooring = spacing * (i + 1);
            ship.add(h);
            String uuid = ship.getComponent(UuidComponent.class).id;
            macroSim.addPirate(uuid, h, station);
            out.add(uuid);
        }
        // Round 10: a railgun platform takes the mooring nearest the station, and the others guard the
        // approach, where their chainguns reach a ship holding off the base
        for (int i = 1; i < out.size(); i++) {
            HostileComponent h = macroSim.hostiles().get(out.get(i)), first = macroSim.hostiles().get(out.get(0));
            LoadoutDef fit = h == null || h.loadout == null ? null : combatDefs.loadouts.get(h.loadout);
            if (first == null || fit == null || !railgun(fit)) continue;
            double near = first.mooring;
            first.mooring = h.mooring;
            h.mooring = near;
            macroSim.addPirate(out.get(0), first, station);
            macroSim.addPirate(out.get(i), h, station);
            break;
        }
        return out;
    }

    private static boolean railgun(LoadoutDef loadout) {
        for (String w : loadout.weapons) if (w != null && w.startsWith("railgun")) return true;
        return false;
    }

    /** A gun platform's fit drawn as {@link Loadouts#pick} draws, from those without a railgun; null if none. */
    LoadoutDef pickWithoutRailgun(double d, java.util.Random dice) {
        List<LoadoutDef> fits = new ArrayList<>();
        double total = 0;
        for (LoadoutDef l : Loadouts.candidates(combatDefs, BATTERY, d, false)) {
            if (railgun(l)) continue;
            fits.add(l);
            total += l.weight;
        }
        double roll = dice.nextDouble() * total;
        for (LoadoutDef l : fits) {
            roll -= l.weight;
            if (roll < 0) return l;
        }
        return fits.isEmpty() ? null : fits.get(fits.size() - 1);
    }

    /**
     * A wing of {@code size} pirates of {@code shipId} lurking by {@code lurk}: the first leads and the
     * rest fly on its wing. Each is armed with {@code loadoutId}, or as the difficulty picks when null.
     * Call outside the engine's update. Returns their uuids, the leader first.
     */
    List<String> spawnWing(String shipId, int size, String lurk, String loadoutId) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String uuid = spawnPirateAt(shipId, lurk, out.isEmpty() ? null : out.get(0), loadoutId);
            if (uuid != null) out.add(uuid);
        }
        return out;
    }

    /**
     * A raider leading a wing with room in it, {@code size} with the leader: the first, by uuid order,
     * of those with the fewest wingmen. Null when every wing is full, or wings are one strong.
     */
    String openWing(int size) {
        if (size <= 1) return null;
        Map<String, Integer> wingmen = new HashMap<>();
        for (HostileComponent h : controller.all().values()) if (h.wingOf != null) wingmen.merge(h.wingOf, 1, Integer::sum);
        String best = null;
        int fewest = Integer.MAX_VALUE;
        for (Map.Entry<String, HostileComponent> en : controller.all().entrySet()) {
            HostileComponent h = en.getValue();
            if (!h.isPirate() || !RAIDER.equals(h.shipId) || h.wingOf != null || h.ace || h.stationUuid == null) continue;
            int n = wingmen.getOrDefault(en.getKey(), 0);
            if (n >= size - 1) continue;
            if (n < fewest || (n == fewest && en.getKey().compareTo(best) < 0)) {
                best = en.getKey();
                fewest = n;
            }
        }
        return best;
    }

    void spawnPatrol(String faction, String[] route) {
        int crew = SPONSOR.equals(faction) ? SPONSOR_CREW : SELENE.equals(faction) ? SELENE_CREW
                : standing.factions().index(faction);
        Entity ship = spawnShip(GUNSHIP, crew);
        if (ship == null) return;
        String name = (SPONSOR.equals(faction) ? "Patrol cutter " : SELENE.equals(faction) ? "Selene guard "
                : TRADERS.equals(faction) ? "Free Trader escort " : "Patrol ") + (++spawned);
        HostileComponent h = new HostileComponent(name, GUNSHIP, faction, HostileComponent.Role.PATROL,
                HostileTraffic.firepower(ship));
        ship.add(h);
        macroSim.addPatrol(ship.getComponent(UuidComponent.class).id, h, route);
    }

    /** True if {@code station} is close enough to the player that a ship appearing there would be seen. */
    boolean nearPlayer(String station, double now) {
        var rails = macroSim.rails();
        if (!rails.isOnRails(station)) return true;
        String parent = rails.parentOf(station);
        if (!parent.equals(macroSim.gateway().anchorParentUuid())) return false;
        rails.stateAt(station, now, a);
        macroSim.gateway().anchorStateAt(now, b);
        return Math.hypot(a.x - b.x, a.y - b.y) < SPAWN_CLEARANCE;
    }

    /**
     * A crewed, armed ship of {@code shipId}, in the engine but not in the bubble: macro-sim puts it on
     * the rails, and the bubble builds its body from its layout when it comes in.
     */
    Entity spawnShip(String shipId, int crewFaction) {
        var content = ctx.content();
        if (content == null || !content.defs.has("ship", shipId)) return null;
        Entity ship = content.factory.spawnShip(shipId, 0f, 0f, 0f);
        ship.remove(PhysicsBodyComponent.class);
        ShipConsoles.install(ship);
        CrewFactory.ensureBoard(ship).faction = crewFaction;
        standing.factions().crewHabits(CrewFactory.ensureBoard(ship));
        ShipDef def = content.defs.get("ship", shipId, ShipDef.class);
        List<int[]> floor = FloorTiles.of(ship.getComponent(ShipGridComponent.class));
        int crew = def == null ? 0 : Math.min(def.crew, floor.size());
        for (int i = 0; i < crew; i++) {
            int[] t = floor.get(i * floor.size() / crew);
            CrewFactory.spawn(crewService, ship, "Crew " + (i + 1), CREW_ROLES[i % CREW_ROLES.length], crewFaction,
                    t[0], t[1]);
        }
        return ship;
    }

}
