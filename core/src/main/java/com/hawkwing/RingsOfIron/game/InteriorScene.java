package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The interior scenario, in the career itself: the player's ship alone in low Earth orbit with its
 * crew aboard, to hurt in every way the game can and watch how the crew and the wound rules answer.
 * It is {@link InteriorLab}'s bench moved into the running game: the career's raider is sent away,
 * the ship is {@link #pinned} where it is, and {@link #setHull} swaps the player onto any hull in the
 * data with its crew walked across and topped up to the hull's complement. {@link WorldDebug} has the
 * damage sources ({@link DeckTools} picks one and aims it), {@link #attack} sets an armed ship on it,
 * and {@link WoundWatch} keeps the account of everyone's wounds.
 *
 * <p>Headless: the game's debug menu loads it and calls {@link #update} after each world step; tests
 * run it the same way.
 */
public class InteriorScene {
    /** Who tops up each hull's crew, in order: a medic first, so every hull has one. */
    static final CrewRole[] ROLES = InteriorLab.ROLES;
    static final String[] NAMES = InteriorLab.NAMES;

    public final GameScenario game;
    public final GameWorld world;
    public final WorldDebug debug;
    public final WoundWatch watch;
    /** The hull the player flies here, as a ship id. */
    public String hull;
    /** The armed ship set on the player, or null, and its ship id. */
    public Entity attacker;
    public String attackerHull;
    /** Holds the ship (and an attacker) still where they started. */
    public boolean pinned = true;
    /** Called with the new ship when {@link #setHull} puts the player in another one. */
    public java.util.function.Consumer<Entity> shipChanged = ship -> {};

    private final Vector2 shipHome = new Vector2(), attackerHome = new Vector2();
    private float shipAngle, attackerAngle;
    private int named;

    /**
     * Sets the scenario up in {@code game}'s world: no raider, undocked, the player in {@code hull}
     * (the career's own corvette if null or not in the data) with a full crew and a medic among them.
     * {@code tools} is the world's {@link WorldDebug} when the game already has one (its console's); null makes one.
     */
    public InteriorScene(GameScenario game, String hull, WorldDebug tools) {
        this.game = game;
        this.world = game.world;
        this.debug = tools != null ? tools : new WorldDebug(world);
        this.watch = new WoundWatch(world.crew, world.engine::getSimSeconds);
        sendRaiderAway();
        world.docking.undock(game.player);
        this.hull = GameScenario.PLAYER_SHIP;
        // The career's own corvette as it is, or another hull in its place
        if (hull == null || hull.equals(this.hull) || setHull(hull).startsWith("No hull")) settle(game.player);
    }

    private void sendRaiderAway() {
        if (game.raider == null) return;
        Entity raider = game.raider;
        game.contacts.removeValue(raider, true);
        world.shipyard.retire(raider);
        game.raider = null;
    }

    /** The player's ship. */
    public Entity ship() {
        return game.player;
    }

    /** The hulls the scenario can fly: every ship in the data with a layout to walk, in data order. */
    public List<String> hulls() {
        List<String> out = new ArrayList<>();
        for (String id : world.getContent().defs.ids("ship")) {
            ShipDef def = def(id);
            if (def != null && def.chassis != null) out.add(id);
        }
        return out;
    }

    /** Puts the player in the hull {@code step} along from this one in {@link #hulls()}, wrapping round. */
    public String cycle(int step) {
        List<String> all = hulls();
        int i = Math.max(0, all.indexOf(hull));
        return setHull(all.get(Math.floorMod(i + step, all.size())));
    }

    /**
     * Puts the player in a fresh {@code shipId} where their ship is now, as a yard does: the controls,
     * credits, cargo and crew go across (the dead are left with the old ship), the old ship goes, and
     * the crew are topped up to the new hull's complement. The same hull again gives a fresh one, as
     * good as new. Returns what happened.
     */
    public String setHull(String shipId) {
        if (!world.getContent().defs.has("ship", shipId) || def(shipId).chassis == null) return "No hull " + shipId;
        Entity old = game.player;
        Body body = WorldDebug.body(old);
        float x = 0f, y = 0f, angle = 90f;
        if (body != null) {
            x = body.getPosition().x * PhysicsManager.METERS_PER_UNIT;
            y = body.getPosition().y * PhysicsManager.METERS_PER_UNIT;
            angle = body.getAngle() * MathUtils.radiansToDegrees;
        }
        Entity ship = world.getContent().factory.spawnShip(shipId, x, y, angle);
        old.remove(PlayerControllerComponent.class);
        ship.add(new PlayerControllerComponent());
        ShipConsoles.install(ship);
        TraderComponent account = old.getComponent(TraderComponent.class);
        ship.add(new TraderComponent(account == null ? GameScenario.STARTING_CREDITS : account.credits, 0));
        ShipCargo.attach(ship);
        FlightControlComponent controls = ship.getComponent(FlightControlComponent.class);
        if (controls != null) controls.rotationAssist = true;
        world.shipyard.moveCrew(old, ship);
        world.shipyard.retire(old);
        game.takeShip(ship);
        hull = shipId;
        return settle(ship);
    }

    /** The crew topped up, the ship still and pinned here, the attacker's lock moved onto it. */
    private String settle(Entity ship) {
        topUpCrew(ship);
        Body body = WorldDebug.body(ship);
        if (body != null) {
            body.setLinearVelocity(0f, 0f);
            body.setAngularVelocity(0f);
            shipHome.set(body.getPosition());
            shipAngle = body.getAngle();
        }
        if (attacker != null) {
            SensorComponent sensor = attacker.getComponent(SensorComponent.class);
            if (sensor != null) sensor.lockedTarget = ship;
        }
        watch.update();
        String text = name(hull) + ": " + debug.crewAboard(ship).size() + " aboard";
        watch.note(text);
        shipChanged.accept(ship);
        return text;
    }

    /** Brings the living crew aboard {@code ship} up to its hull's complement, a medic first if there is none. */
    private void topUpCrew(Entity ship) {
        ShipDef def = def(hull);
        int faction = world.crew.board(ship).faction, count = 0;
        boolean medic = false;
        for (Entity e : debug.crewAboard(ship)) {
            CrewMemberComponent c = world.crew.crew(e);
            if (c == null || !c.isAlive() || c.faction != faction) continue;
            count++;
            medic |= c.role == CrewRole.MEDIC;
        }
        if (!medic && spawn(ship, CrewRole.MEDIC) != null) count++;
        while (def != null && count < def.crew && spawn(ship, ROLES[count % ROLES.length]) != null) count++;
    }

    /** One of the ship's own crew, named from the scenario's list, on the first free deck tile. */
    private Entity spawn(Entity ship, CrewRole role) {
        int[] t = debug.freeTile(ship);
        if (t == null) return null;
        Entity e = debug.spawnCrew(ship, role, t[0], t[1]);
        if (e != null) world.crew.crew(e).name = NAMES[named++ % NAMES.length];
        return e;
    }

    /**
     * Sets an uncrewed {@code shipId} on the player's ship from {@code bearingDeg} (counter-clockwise
     * from the bow) at {@link InteriorLab#ATTACK_RANGE}: its sensors lock the ship and every gun, beam
     * and launcher fires at will. Replaces any attacker already there. Returns what happened, or why not.
     */
    public String attack(String shipId, float bearingDeg) {
        ceaseFire();
        if (!world.getContent().defs.has("ship", shipId)) return "No ship " + shipId;
        float a = shipAngle + bearingDeg * MathUtils.degreesToRadians;
        Vector2 at = new Vector2(shipHome).scl(PhysicsManager.METERS_PER_UNIT)
                .add(MathUtils.cos(a) * InteriorLab.ATTACK_RANGE, MathUtils.sin(a) * InteriorLab.ATTACK_RANGE);
        attackerHull = shipId;
        attacker = world.getContent().factory.spawnShip(shipId, at.x, at.y, a * MathUtils.radiansToDegrees + 180f);
        ShipConsoles.install(attacker);
        SensorComponent sensor = attacker.getComponent(SensorComponent.class);
        if (sensor != null) sensor.lockedTarget = game.player;
        Body body = WorldDebug.body(attacker);
        if (body != null) {
            attackerHome.set(body.getPosition());
            attackerAngle = body.getAngle();
        }
        String text = String.format("%s opens fire from %.0f deg, %.1f km", name(shipId), bearingDeg,
                InteriorLab.ATTACK_RANGE / 1000f);
        watch.note(text);
        return name(shipId) + " attacking";
    }

    /** Takes the attacker away; its rounds and missiles already in flight fly on. */
    public void ceaseFire() {
        if (attacker == null) return;
        world.shipyard.retire(attacker);
        attacker = null;
        watch.note("Attacker gone");
    }

    /** After each world step: holds the ships still while {@link #pinned}, and brings the wound account up to date. */
    public void update() {
        if (attacker != null && attacker.getComponent(PhysicsBodyComponent.class) == null) attacker = null;
        if (pinned) {
            hold(game.player, shipHome, shipAngle);
            hold(attacker, attackerHome, attackerAngle);
        }
        watch.update();
    }

    /** Lets the ship drift, or pins it where it is now. Returns whether it is pinned. */
    public boolean togglePinned() {
        pinned = !pinned;
        Body body = WorldDebug.body(game.player);
        if (pinned && body != null) {
            shipHome.set(body.getPosition());
            shipAngle = body.getAngle();
        }
        return pinned;
    }

    private static void hold(Entity e, Vector2 home, float angle) {
        Body body = WorldDebug.body(e);
        if (body == null) return;
        body.setTransform(home, angle);
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
    }

    /** The hull's name in the data, or its id. */
    public String name(String shipId) {
        ShipDef def = def(shipId);
        return def == null || def.name == null ? shipId : def.name;
    }

    private ShipDef def(String shipId) {
        return world.getContent().defs.get("ship", shipId, ShipDef.class);
    }

    /** Lets go of the world: the attacker goes, and the tools' step listener stops if the scenario made them. */
    public void dispose(boolean ownTools) {
        ceaseFire();
        if (ownTools) debug.dispose();
    }
}
