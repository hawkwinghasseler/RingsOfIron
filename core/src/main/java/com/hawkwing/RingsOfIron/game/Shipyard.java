package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.signals.Signal;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.aicrew.CrewFactory;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.FloorTiles;
import com.ringsofiron.interiorgrid.InteriorGridData;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.DefRegistry;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ChassisDef;
import com.ringsofiron.moddingdata.defs.FactionDef;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.moddingdata.defs.ShipSaleDef;
import com.ringsofiron.moddingdata.defs.ShipyardDef;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.HintRegistry;
import com.ringsofiron.uihud.components.HudMarkerComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ship procurement (GDD section 5, round 3 category 3): buying ships at a faction's yards, and
 * bringing taken ships home as prizes.
 *
 * <p><b>Buying.</b> Docked with clearance at a station that sells ships ({@code [shipyard.<id>]}),
 * the player can buy, from the shipyard window (the yard's Ships for sale, on {@link Action#YARD}), any of the owner's {@code [ship_sale.<id>]} ships their rank and standing
 * allow. The ship they fly is traded in at {@link #TRADE_IN_SHARE} of its value times its
 * condition; the new ship is latched to the station in its place, with the player's credits, the
 * cargo (which must fit) and every crew member who calls the old ship home, the player's avatar
 * seated at its helm. The old ship goes to the yard.
 *
 * <p><b>Prizes.</b> A ship the player's crew have taken ({@link Boarding#takenBy}) and that stays
 * latched to the player's ship is towed: its collars are rigged for towing
 * ({@link com.ringsofiron.stationsdocking.DockingSystem#towLocked}), so the dock's weld never tears
 * and the pair flies as one body with their combined mass, the engines pushing through the pair's
 * centre of mass ({@link FlightControlComponent#towing}); it goes onto the rails with the player in
 * time warp. Within
 * {@link #PRIZE_RANGE} of a station that deals with the player the station's tugs take it in:
 * sold for {@link #PRIZE_SHARE} of its value ({@link #STRIPPED_PRIZE_SHARE} once anything has
 * been stripped off it), or, at the sponsor's station, turned in for standing instead: its
 * bounty's standing. The player never commands two ships: a prize is towed, not flown.
 *
 * <p>Purchases and prizes happen at once, so nothing here is saved: the new ship and the career
 * are saved as they always are.
 */
public class Shipyard extends EntitySystem {
    /**
     * Share of the old ship's value, times its condition, the yard pays on a trade-in. Round 4: 80%
     * (was 60%), so a lieutenant commander's pay and fights buy the gunship (round4-balance.md).
     */
    public static final double TRADE_IN_SHARE = 0.8;
    /**
     * Share of a prize's value a station pays for it. Round 4: a twentieth (was a quarter, which paid
     * ten bounties for a raider), so a raider prize pays one to two bounties.
     */
    public static final double PRIZE_SHARE = 0.05;
    /** Share once anything has been stripped off it: under the whole prize's, as before. */
    public static final double STRIPPED_PRIZE_SHARE = 0.02;
    /** How close to a station, centre to centre, a prize has to be brought for its tugs to take it, m. */
    public static final double PRIZE_RANGE = 2_000;

    private static final Family STATIONS = Family.all(StationComponent.class).get();

    /** A ship on sale where the player is docked. */
    public static final class Listing {
        public final String id;
        public final String shipId;
        public final String name;
        /** Faction number of the seller. */
        public final int seller;
        public final double price;
        /** Rank on the sponsor's ladder needed, or -1 for none. */
        public final int rank;
        public final float standing;

        Listing(String id, String shipId, String name, int seller, double price, int rank, float standing) {
            this.id = id;
            this.shipId = shipId;
            this.name = name;
            this.seller = seller;
            this.price = price;
            this.rank = rank;
            this.standing = standing;
        }
    }

    /** A ship bought, or a prize sold or turned in. */
    public static final class Deal {
        public enum Kind { BOUGHT, PRIZE_SOLD, PRIZE_TURNED_IN }

        public final Kind kind;
        /** The ship the player gave up: their old ship, or the prize. */
        public final Entity gone;
        /** The ship bought, or null for a prize. */
        public final Entity newShip;
        public final Entity station;
        /** Credits the player paid (a purchase, after the trade-in) or got (a sale); 0 for a turn-in. */
        public final double credits;
        /** Standing gained with the station's owner; 0 but for a turn-in. */
        public final float standing;

        Deal(Kind kind, Entity gone, Entity newShip, Entity station, double credits, float standing) {
            this.kind = kind;
            this.gone = gone;
            this.newShip = newShip;
            this.station = station;
            this.credits = credits;
            this.standing = standing;
        }
    }

    /** Every deal, once it is done. The new ship is the player's when it fires. */
    public final Signal<Deal> deals = new Signal<>();

    private final GameWorld world;
    private final double[] stateA = new double[4], stateB = new double[4];
    /** The controls last given a prize to tow. */
    private FlightControlComponent towed;

    Shipyard(GameWorld world) {
        this.world = world;
        // Once taken, a prize's collars are rigged for towing: the weld holds under the engines' full
        // thrust. A yard's berth clamps hold a ship it is working on the same way (StationYard.holds)
        world.docking.towLocked = (a, b) -> tows(a, b) || tows(b, a) || world.yard.holds(a, b) || world.yard.holds(b, a);
        world.hints.register(HintRegistry.Context.LATCHED, "hand over the prize", 3, Action.YARD)
                .onlyWhen(() -> prize() != null && prizeStation() != null);
    }

    /** Points the player's engines through the centre of mass of the pair while a prize is in tow. */
    @Override
    public void update(float deltaTime) {
        Entity ship = world.getPlayer();
        FlightControlComponent controls = ship == null ? null : ship.getComponent(FlightControlComponent.class);
        if (controls != towed && towed != null) towed.towing = null;
        towed = controls;
        if (controls == null) return;
        Entity prize = prize();
        controls.towing = prize != null && world.docking.isRigid(ship) ? bodyOf(prize) : null;
    }

    /** Whether {@code ship}, the player's, has taken {@code prize} and so tows it. */
    private boolean tows(Entity ship, Entity prize) {
        return ship.getComponent(PlayerControllerComponent.class) != null && world.boarding.takenBy(prize, ship);
    }

    // ------------------------------------------------------------------
    // Buying
    // ------------------------------------------------------------------

    /** The station the player is docked at with clearance, or null. */
    public Entity dockedStation() {
        Entity ship = world.getPlayer();
        Entity station = ship == null ? null : world.docking.dockedTo(ship);
        if (station == null || station.getComponent(StationComponent.class) == null) return null;
        return world.docking.dockedWithClearance(ship) ? station : null;
    }

    /** Whether {@code station} sells ships: it is one of its owner's yards. */
    public boolean sellsShips(Entity station) {
        return yardOf(station) != null;
    }

    /** The ships {@code station} sells, in the data's order; empty if it is no yard. */
    public List<Listing> listings(Entity station) {
        List<Listing> out = new ArrayList<>();
        DefRegistry defs = defs();
        if (defs == null || yardOf(station) == null) return out;
        int owner = world.standing.factionOf(station);
        RankLadder ranks = world.standing.ranks();
        for (ShipSaleDef sale : defs.all("ship_sale", ShipSaleDef.class)) {
            if (sale.ship == null || !defs.has("ship", sale.ship) || sale.seller == null) continue;
            if (world.standing.factions().index(sale.seller) != owner) continue;
            ShipDef ship = defs.get("ship", sale.ship, ShipDef.class);
            out.add(new Listing(sale.id, sale.ship, ship.name == null ? sale.ship : ship.name, owner, sale.price,
                    sale.rank == null ? -1 : ranks.indexOf(sale.rank), sale.standing));
        }
        return out;
    }

    /** The listing {@code id} at the station the player is docked at, or null. */
    public Listing listing(String id) {
        Entity station = dockedStation();
        if (station == null) return null;
        for (Listing l : listings(station)) if (l.id.equals(id)) return l;
        return null;
    }

    /**
     * Why the player can't buy {@code listing} where they are docked, or null when they can: the
     * rank ladder's chassis limit, the listing's rank and standing, the price after the trade-in,
     * yard work under way, and whether the cargo and the people aboard fit the new ship.
     */
    public String refusal(Listing listing) {
        Entity ship = world.getPlayer();
        Entity station = dockedStation();
        if (ship == null || station == null || !sellsShips(station)) return "Dock at a shipyard first";
        if (listing == null) return "Not sold here";
        RankLadder ranks = world.standing.ranks();
        int rank = world.standing.record().rank;
        boolean sponsor = listing.seller == world.standing.sponsor();
        if (sponsor && listing.rank > rank) return "Needs rank " + ranks.get(listing.rank).name;
        if (!ranks.mayFly(rank, listing.shipId)) {
            int lowest = ranks.lowestToFly(listing.shipId);
            return lowest < 0 ? "Too big for your commission" : "Needs rank " + ranks.get(lowest).name;
        }
        double wait = world.standing.newChassisWait(listing.shipId);
        if (sponsor && wait > 0) {
            return String.format(Locale.ROOT, "Sold to a new %s after %.1f more days in rank",
                    world.standing.rankName(ranks.lowestToFly(listing.shipId)), wait / 86_400);
        }
        if (world.standing.standing(listing.seller) < listing.standing) {
            return String.format(Locale.ROOT, "Needs standing %.0f with the %s", listing.standing,
                    world.standing.factions().name(listing.seller));
        }
        YardOrdersComponent orders = ship.getComponent(YardOrdersComponent.class);
        if (orders != null && !orders.isEmpty()) return "Wait for the yard's work on your ship to finish";
        if (FieldRefits.partsOut(ship)) return "Wait for your crew's mounting work outside to finish";
        if (AbandonShip.abandoning(ship)) return "Your crew are abandoning ship";
        double cost = cost(listing, ship);
        if (cost > credits(ship) + 1e-6) return String.format(Locale.ROOT, "Needs %,.0f cr after the trade-in", cost);
        CargoHoldComponent hold = ship.getComponent(CargoHoldComponent.class);
        double cargo = hold == null ? 0 : hold.usedVolume();
        double room = holdVolume(listing.shipId);
        if (cargo > room + 1e-6) {
            return String.format(Locale.ROOT, "Unload %.0f m3 of cargo first: the %s holds %.0f m3", cargo - room, listing.name, room);
        }
        int people = crewOf(ship).size;
        int seats = podSeats(listing.shipId);
        if (people > seats) return "The " + listing.name + "'s pods seat " + seats + "; you have " + people + " aboard";
        return null;
    }

    /** What {@code listing} costs the player after trading in {@code ship}; negative if the yard pays them. */
    public double cost(Listing listing, Entity ship) {
        return listing.price - tradeIn(ship);
    }

    /** What the yard pays for {@code ship} on a trade-in: its value times its condition times {@link #TRADE_IN_SHARE}. */
    public static double tradeIn(Entity ship) {
        return ship == null ? 0 : Math.round(TRADE_IN_SHARE * Difficulty.shipValue(ship) * condition(ship));
    }

    /** 0 (wrecked) to 1 (sound): the mean integrity of the hull times the mean health of the systems. */
    public static float condition(Entity ship) {
        float hull = ShipDisablement.hullIntegrity(ship.getComponent(ShipGridComponent.class));
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        float sum = 0f;
        int n = 0;
        if (registry != null) {
            for (Entity e : registry.subsystems) {
                SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
                if (s == null) continue;
                sum += Math.max(0f, Math.min(1f, s.health));
                n++;
            }
        }
        return Math.max(0f, Math.min(1f, hull * (n == 0 ? 1f : sum / n)));
    }

    /**
     * Buys listing {@code id} at the station the player is docked at, trading in the ship they fly.
     * Returns what the yard says: the refusal, or the deal done.
     */
    public String buy(String id) {
        Listing listing = listing(id);
        String no = refusal(listing);
        if (no != null) return no;
        Entity old = world.getPlayer();
        Entity station = dockedStation();
        double cost = cost(listing, old);
        double tradeIn = tradeIn(old);
        String oldName = name(old);

        // The station port the old ship holds, to latch the new one in its place
        int stationPort = -1;
        DockingPortsComponent oldPorts = old.getComponent(DockingPortsComponent.class);
        if (oldPorts != null) {
            for (DockingPort p : oldPorts.ports) {
                if (p.state == DockingPort.State.DOCKED && p.partnerPort >= 0 && world.docking.docked(old, station)) stationPort = p.partnerPort;
            }
        }

        Entity ship = exchange(old, listing.shipId, credits(old) - cost);

        // The new one takes a free port of its size
        if (station != null) dockAt(ship, station, stationPort);

        String deal = cost >= 0 ? String.format(Locale.ROOT, "paid %,.0f cr", cost)
                : String.format(Locale.ROOT, "%,.0f cr to you", -cost);
        String message = String.format(Locale.ROOT, "Bought the %s at %s: %s, with %,.0f cr for the %s", listing.name,
                ShipNames.name(station), deal, tradeIn, oldName);
        world.notices.post(message, NoticeLog.Kind.IMPORTANT);
        deals.dispatch(new Deal(Deal.Kind.BOUGHT, old, ship, station, cost, 0f));
        return message;
    }

    /**
     * For the developer console: the player's ship swapped for a new {@code shipId} where it is,
     * free and with no yard, rank or room checks; credits, cargo and crew go across as for a
     * purchase, and a ship docked anywhere stays docked there. Returns what happened.
     */
    public String swap(String shipId) {
        Entity old = world.getPlayer();
        if (old == null) return "No player ship";
        if (world.getContent() == null || !world.getContent().defs.ids("ship").contains(shipId)) return "No ship " + shipId;
        Entity station = world.docking.dockedTo(old);
        int stationPort = -1;
        DockingPortsComponent oldPorts = old.getComponent(DockingPortsComponent.class);
        if (oldPorts != null && station != null) {
            for (DockingPort p : oldPorts.ports) {
                if (p.state == DockingPort.State.DOCKED && p.partnerPort >= 0) stationPort = p.partnerPort;
            }
        }
        Entity ship = exchange(old, shipId, credits(old));
        if (station != null) dockAt(ship, station, stationPort);
        deals.dispatch(new Deal(Deal.Kind.BOUGHT, old, ship, station, 0, 0f));
        return "Now flying a " + shipId;
    }

    /**
     * A new {@code shipId} where {@code old} is, made the player's: their controls, {@code credits},
     * cargo and crew go across (the avatar to the first open tile), and the old ship is undocked and
     * taken out of the world. A purchase does this; so does a debug scenario that changes the player's
     * hull. The caller seats the avatar and docks the new ship as it needs.
     */
    public Entity exchange(Entity old, String shipId, double credits) {
        float x = 0f, y = 0f, angle = 0f;
        PhysicsBodyComponent body = old.getComponent(PhysicsBodyComponent.class);
        if (body != null && body.body != null) {
            x = body.body.getPosition().x;
            y = body.body.getPosition().y;
            angle = (float) Math.toDegrees(body.body.getAngle());
        }
        Entity ship = world.getContent().factory.spawnShip(shipId, x, y, angle);

        // Off the old ship: the player's controls, credits, cargo and crew
        old.remove(PlayerControllerComponent.class);
        ship.add(new PlayerControllerComponent());
        ShipConsoles.install(ship);
        TraderComponent oldAccount = old.getComponent(TraderComponent.class);
        ship.add(new TraderComponent(credits, 0));
        if (oldAccount != null) oldAccount.credits = 0;
        ShipCargo.attach(ship);
        moveCargo(old, ship);
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;
        moveCrew(old, ship);

        world.docking.undock(old);
        retire(old);
        return ship;
    }

    /** Latches {@code ship}'s first port onto {@code station}: at {@code preferred} if it fits and is free, else any free port of its size. */
    private void dockAt(Entity ship, Entity station, int preferred) {
        DockingPortsComponent ports = station.getComponent(DockingPortsComponent.class);
        DockingPortsComponent own = ship.getComponent(DockingPortsComponent.class);
        if (ports == null || own == null || own.ports.length == 0) return;
        MountFootprint.MountSize size = own.ports[0].size;
        int pick = -1;
        for (int i = 0; i < ports.ports.length; i++) {
            DockingPort p = ports.ports[i];
            if (p.size != size || p.state == DockingPort.State.DOCKED) continue;
            if (pick < 0 || i == preferred) pick = i;
        }
        if (pick >= 0) world.docking.dockInPlace(ship, 0, station, pick);
    }

    /** Loads the old ship's cargo into the new one's hold. */
    private static void moveCargo(Entity from, Entity to) {
        CargoHoldComponent a = from.getComponent(CargoHoldComponent.class), b = to.getComponent(CargoHoldComponent.class);
        if (a == null || b == null) return;
        for (int i = 0; i < a.stacks.length; i++) {
            CargoHoldComponent.CargoStack s = a.stacks[i];
            if (s == null || s.commodity == null || s.units <= 0) continue;
            b.load(s.commodity, s.units, s.unitVolume, s.unitMass);
            a.stacks[i] = null;
        }
        a.revision++;
    }

    /** Everyone who calls {@code from} home, and everyone aboard it, goes aboard {@code to} and calls it home. */
    void moveCrew(Entity from, Entity to) {
        Array<Entity> crew = crewOf(from);
        List<int[]> floor = FloorTiles.of(to.getComponent(ShipGridComponent.class));
        CrewFactory.ensureBoard(to);
        world.crew.clearPathfinders();
        sendStaffHome(from);
        // The avatar first, so it gets the first tile; the helm seat is the caller's to give
        crew.sort((p, q) -> Boolean.compare(q.getComponent(PlayerAvatarComponent.class) != null,
                p.getComponent(PlayerAvatarComponent.class) != null));
        for (int i = 0; i < crew.size; i++) {
            Entity e = crew.get(i);
            world.crew.releaseTask(e);
            GridPositionComponent pos = world.crew.position(e);
            pos.stop();
            pos.crossing = null;
            pos.setShip(to);
            if (!floor.isEmpty()) {
                int[] t = floor.get(i * floor.size() / Math.max(1, crew.size));
                pos.setTile(t[0], t[1]);
            }
            CrewMemberComponent c = world.crew.crew(e);
            c.setHome(to);
            PlayerAvatarComponent walk = e.getComponent(PlayerAvatarComponent.class);
            if (walk != null) walk.moveX = walk.moveY = 0f;
        }
    }

    /** A station's technicians or loaders still aboard {@code ship} step back aboard their station before it goes. */
    private void sendStaffHome(Entity ship) {
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = world.crew.crew(e);
            GridPositionComponent p = world.crew.position(e);
            if (c == null || p == null || p.ship != ship || c.home == null || c.home == ship
                    || c.home.getComponent(StationComponent.class) == null) continue;
            List<int[]> floor = FloorTiles.of(c.home.getComponent(ShipGridComponent.class));
            if (floor.isEmpty()) continue;
            world.crew.releaseTask(e);
            p.stop();
            p.crossing = null;
            p.setShip(c.home);
            int[] t = floor.get(floor.size() / 2);
            p.setTile(t[0], t[1]);
        }
    }

    /** The living crew (and the player's avatar) aboard {@code ship} or calling it home. */
    Array<Entity> crewOf(Entity ship) {
        Array<Entity> out = new Array<>();
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = world.crew.crew(e);
            GridPositionComponent p = world.crew.position(e);
            if (c == null || p == null || !c.isAlive()) continue;
            // A station's own people aboard (a technician walking back from the yard's work) stay with it:
            // on round 6's seed 2026 the frigate carried off Highport's technicians, and its yard could do no more
            if (c.home != null && c.home != ship && c.home.getComponent(StationComponent.class) != null) continue;
            if (p.ship == ship || c.home == ship) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Prizes
    // ------------------------------------------------------------------

    /** The taken ship latched to the player's, which they are towing, or null. */
    public Entity prize() {
        Entity ship = world.getPlayer();
        DockingPortsComponent ports = ship == null ? null : ship.getComponent(DockingPortsComponent.class);
        if (ports == null) return null;
        for (DockingPort p : ports.ports) {
            if (p.state != DockingPort.State.DOCKED || p.partnerUuid == null) continue;
            Entity other = world.engine.getEntity(p.partnerUuid);
            if (other == null || other.getComponent(StationComponent.class) != null) continue;
            if (world.boarding.takenBy(other, ship)) return other;
        }
        return null;
    }

    /**
     * The station whose tugs would take the prize in: the nearest within {@link #PRIZE_RANGE} that
     * deals with the player (not hostile to all, and not refusing them clearance), or null.
     */
    public Entity prizeStation() {
        Entity ship = world.getPlayer();
        Body own = bodyOf(ship);
        if (own == null) return null;
        world.physics.getTrueState(own, stateA);
        Entity best = null;
        double bestD = PRIZE_RANGE;
        for (Entity station : world.engine.getEcsEngine().getEntitiesFor(STATIONS)) {
            Body b = world.docking.bodyOf(station);
            if (b == null || !deals(station)) continue;
            world.physics.getTrueState(b, stateB);
            double d = Math.hypot(stateB[0] - stateA[0], stateB[1] - stateA[1]);
            if (d <= bestD) {
                bestD = d;
                best = station;
            }
        }
        return best;
    }

    /** Whether {@code station} does business with the player. */
    private boolean deals(Entity station) {
        int owner = world.standing.factionOf(station);
        FactionDef def = world.standing.factions().def(owner);
        if (def != null && def.hostileToAll) return false;
        return world.standing.refusal(station, world.getPlayer()) == null;
    }

    /** What a station pays for {@code prize}: {@link #PRIZE_SHARE} of its value, or {@link #STRIPPED_PRIZE_SHARE} once stripped. */
    public static double prizeValue(Entity prize) {
        SalvagedComponent stripped = prize.getComponent(SalvagedComponent.class);
        double share = stripped != null && !stripped.stripped.isEmpty() ? STRIPPED_PRIZE_SHARE : PRIZE_SHARE;
        return Math.round(share * Difficulty.shipValue(prize));
    }

    /**
     * Standing the sponsor gives for {@code prize} turned in: that of a bounty on it the player took
     * (held or done), else what a bounty on it would give, by its value against the player's ship.
     */
    public float prizeStanding(Entity prize) {
        UuidComponent u = prize.getComponent(UuidComponent.class);
        if (u != null) {
            for (Contract c : world.contracts.active()) {
                if (c.type == Contract.Type.BOUNTY && u.id.equals(c.targetUuid)) return c.standing;
            }
            for (Contract c : world.contracts.history()) {
                if (c.type == Contract.Type.BOUNTY && u.id.equals(c.targetUuid)) return c.standing;
            }
        }
        double own = Difficulty.shipValue(world.getPlayer());
        double ratio = own <= 0 ? 1 : Difficulty.shipValue(prize) / own;
        int tier = ratio <= 1.2 ? 0 : ratio <= 2 ? 1 : 2;
        return 6 + 3 * tier;
    }

    /** Why the prize can't be handed over now, or null when it can; {@code turnIn} for the sponsor's standing. */
    public String prizeRefusal(boolean turnIn) {
        Entity prize = prize();
        if (prize == null) return "No prize in tow: take a ship and stay latched to it";
        Entity station = prizeStation();
        if (station == null) return String.format(Locale.ROOT, "Bring it within %.0f km of a station", PRIZE_RANGE / 1000);
        if (turnIn && world.standing.factionOf(station) != world.standing.sponsor()) {
            return "Only the " + world.standing.factions().name(world.standing.sponsor()) + "'s stations take prizes in for standing";
        }
        int aboard = 0;
        Entity ship = world.getPlayer();
        for (Entity e : world.crew.crewEntities()) {
            CrewMemberComponent c = world.crew.crew(e);
            GridPositionComponent p = world.crew.position(e);
            if (c != null && p != null && c.isAlive() && !c.held && p.ship == prize && c.home == ship) aboard++;
        }
        if (aboard > 0) return aboard + " of your crew are still aboard: recall them first";
        return null;
    }

    /** Sells the prize in tow to the station in range. Returns what the station says. */
    public String sellPrize() {
        String no = prizeRefusal(false);
        if (no != null) return no;
        Entity prize = prize(), station = prizeStation();
        double paid = prizeValue(prize);
        String name = name(prize);
        double forPrisoners = world.prisoners.handOverWithPrize(prize, station);
        paid += forPrisoners;
        TraderComponent trader = world.getPlayer().getComponent(TraderComponent.class);
        if (trader != null) trader.credits += paid;
        int held = handOver(prize);
        String message = String.format(Locale.ROOT, "%s's tugs took the %s in: +%,.0f cr%s", ShipNames.name(station), name,
                paid, held > 0 ? ", " + held + " prisoners handed over" : "");
        world.notices.post(message, NoticeLog.Kind.IMPORTANT);
        deals.dispatch(new Deal(Deal.Kind.PRIZE_SOLD, prize, null, station, paid, 0f));
        return message;
    }

    /** Turns the prize in tow over to the sponsor's station in range, for standing. Returns what the station says. */
    public String turnInPrize() {
        String no = prizeRefusal(true);
        if (no != null) return no;
        Entity prize = prize(), station = prizeStation();
        float standing = prizeStanding(prize);
        String name = name(prize);
        double forPrisoners = world.prisoners.handOverWithPrize(prize, station);
        TraderComponent trader = world.getPlayer().getComponent(TraderComponent.class);
        if (trader != null) trader.credits += forPrisoners;
        int held = handOver(prize);
        world.standing.reward(world.standing.sponsor(), standing, "turned in a prize");
        String message = String.format(Locale.ROOT, "Turned the %s in at %s: standing %+.0f with the %s%s", name,
                ShipNames.name(station), standing, world.standing.factions().name(world.standing.sponsor()),
                held > 0 ? ", " + held + " prisoners handed over"
                        + (forPrisoners > 0 ? String.format(Locale.ROOT, " (+%,.0f cr)", forPrisoners) : "") : "");
        world.notices.post(message, NoticeLog.Kind.IMPORTANT);
        deals.dispatch(new Deal(Deal.Kind.PRIZE_TURNED_IN, prize, null, station, 0, standing));
        return message;
    }

    /** Unlatches the prize and gives it up, with whoever is held aboard. Returns how many were. */
    private int handOver(Entity prize) {
        int held = 0;
        for (Entity e : crewOf(prize)) if (world.crew.crew(e).held) held++;
        world.docking.undock(world.getPlayer());
        retire(prize);
        return held;
    }

    // ------------------------------------------------------------------

    /** Takes {@code ship} out of the world with its systems and anyone still aboard. */
    void retire(Entity ship) {
        Engine ecs = world.engine.getEcsEngine();
        Array<Entity> gone = new Array<>();
        for (Entity e : world.crew.crewEntities()) {
            GridPositionComponent p = world.crew.position(e);
            if (p != null && p.ship == ship) gone.add(e);
        }
        for (Entity e : gone) {
            world.crew.releaseTask(e);
            ecs.removeEntity(e);
        }
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) for (Entity system : registry.subsystems) if (system != null) ecs.removeEntity(system);
        UuidComponent u = ship.getComponent(UuidComponent.class);
        if (u == null || !world.macroSim.destroy(u.id)) ecs.removeEntity(ship);
    }

    private Body bodyOf(Entity ship) {
        PhysicsBodyComponent pb = ship == null ? null : ship.getComponent(PhysicsBodyComponent.class);
        return pb == null ? null : pb.body;
    }

    /** The {@code [shipyard.<id>]} that makes {@code station} one of its owner's yards, or null. */
    private ShipyardDef yardOf(Entity station) {
        DefRegistry defs = defs();
        StationComponent sc = station == null ? null : station.getComponent(StationComponent.class);
        if (defs == null || sc == null) return null;
        int owner = world.standing.factionOf(station);
        for (ShipyardDef yard : defs.all("shipyard", ShipyardDef.class)) {
            if (yard.faction == null || world.standing.factions().index(yard.faction) != owner) continue;
            if (yard.stations.contains(sc.name) || (sc.typeId != null && yard.stationTypes.contains(sc.typeId))) return yard;
        }
        return null;
    }

    /** Cargo volume of {@code shipId}'s hold, m3. */
    private double holdVolume(String shipId) {
        try {
            return InteriorGridData.cargoBayVolume(defs(), shipId);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Seats in the escape pods {@code shipId}'s chassis carries: its pod mounts, and pods drawn into its map. */
    int podSeats(String shipId) {
        DefRegistry defs = defs();
        ShipDef ship = defs == null ? null : defs.get("ship", shipId, ShipDef.class);
        ChassisDef chassis = ship == null || ship.chassis == null ? null : defs.get("chassis", ship.chassis, ChassisDef.class);
        if (chassis == null) return 0;
        int seats = 0;
        for (ChassisDef.Mount m : chassis.mounts) {
            if ("escape_pod".equals(m.component)) seats += AbandonShip.seats(MountFootprint.MountSize.valueOf(m.size));
        }
        int drawn = 0;
        if (chassis.rows != null) for (String row : chassis.rows) for (char ch : row.toCharArray()) if (ch == 'B') drawn++;
        // A pod drawn into the map is a Small one, two tiles to a pod
        return seats + drawn / 2 * AbandonShip.seats(MountFootprint.MountSize.S);
    }

    private static double credits(Entity ship) {
        TraderComponent t = ship == null ? null : ship.getComponent(TraderComponent.class);
        return t == null ? 0 : t.credits;
    }

    private DefRegistry defs() {
        return world.getContent() == null ? null : world.getContent().defs;
    }

    /** A ship's name: its marker's label, else its ship def's name. */
    String name(Entity ship) {
        HudMarkerComponent m = ship.getComponent(HudMarkerComponent.class);
        if (m != null && m.label != null && !m.label.isEmpty()) {
            return m.label.replace(ContractBoard.MARKER_SUFFIX, "").replace(" (abandoned)", "");
        }
        DefRefComponent ref = ship.getComponent(DefRefComponent.class);
        DefRegistry defs = defs();
        ShipDef def = ref == null || defs == null ? null : defs.get("ship", ref.defId, ShipDef.class);
        return def != null && def.name != null ? def.name : "ship";
    }
}
