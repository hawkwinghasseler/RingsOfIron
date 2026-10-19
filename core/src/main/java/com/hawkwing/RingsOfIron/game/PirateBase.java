package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The pirates' hidden base: a station of the hostile-to-all faction on an eccentric high orbit,
 * where pirates rest and resupply. It is off the traffic map until found, by coming within
 * {@link #DETECT_RANGE} of it, by it coming into the bubble, or by {@link #reveal} (an operation's
 * intelligence, say).
 *
 * <p>While it stands, the pirate population grows: {@link #extraRaiders} more raiders than the
 * difficulty asks for, one more every {@link #GROWTH_DAYS} up to {@link #MAX_EXTRA}, spawned at the
 * base. It falls through {@link #fall} (a raid that disables or takes it), or by itself once no
 * living pirate is left aboard; then the growth stops.
 *
 * <p>Round 5: the base fights back. When the player comes within {@link #SCRAMBLE_RANGE} of it in
 * the bubble, it scrambles a wing of {@link #defendersFor defenders} (a leader and its wingmen) that
 * goes straight for the player. Once every defender is beaten or driven out of the bubble with the
 * player still within {@link #STRIKE_RANGE}, the base strikes its colours and falls. A fallen base
 * eases the strain between the lawful factions by {@link #FALL_RELIEF} (the pirates were what set
 * them against each other). The defenders are saved, so a raid broken off and flown again meets
 * whoever is left of the wing rather than a new one.
 *
 * <p>Round 6: a fallen base does not stay empty. Once the lawful factions have spent
 * {@link com.ringsofiron.moddingdata.defs.TensionDef#baseRisesAfterDays} days with any pair of them
 * at high tension ({@link FactionTension#anyHigh}) since it fell, the raiders move back in
 * ({@link #rise}): it stands again where it was, known, crewed and growing, so the late career keeps
 * a base to raid.
 */
public class PirateBase extends EntitySystem implements SaveParticipant {
    /** How close the player must come to find it, m. */
    public static final double DETECT_RANGE = 2_000e3;
    /** Days between each extra raider the base adds while it stands. */
    public static final double GROWTH_DAYS = 14;
    /** Most extra raiders the base keeps. */
    public static final int MAX_EXTRA = 4;
    /** Seconds between looks at whether the player is near or the base's crew are gone. */
    static final float CHECK_SECONDS = 2f;
    /** How close the player must come to the base, in the bubble, for it to scramble its defenders, m. */
    public static final double SCRAMBLE_RANGE = 15_000;
    /** How close the player must still be when the last defender is beaten for the base to strike, m. */
    public static final double STRIKE_RANGE = 20_000;
    /** How far from the base the defenders launch, m, each a little further round. */
    static final double LAUNCH_OFFSET = 4_000;
    /** Difficulty for each defender past the first two (round 6: 1.5; it was 1). */
    public static final double DIFFICULTY_PER_DEFENDER = 1.5;
    /** Most defenders the base scrambles. */
    public static final int MAX_DEFENDERS = 4;
    /** Tension eased between every pair of lawful factions when the base falls. */
    public static final float FALL_RELIEF = 15f;

    private final GameWorld world;
    private String uuid;
    private double establishedAt = Double.NaN;
    private boolean found;
    private boolean fallen;
    private float since = CHECK_SECONDS;
    /** The names of the wing it scrambled, leader first; saved by name, since a load gives ships new uuids. */
    private final List<String> defenders = new ArrayList<>();
    /** A scramble seen due in {@link #update}, flown out after the engine's update. */
    private boolean scrambleDue;
    /** Sim seconds spent fallen with some pair of lawful factions at high tension, towards {@link #rise}. */
    private double strainedSeconds;
    /** Sim second of the last look at that, NaN before the first. */
    private double lastStrainLook = Double.NaN;
    private final StateVector a = new StateVector();
    private final StateVector b = new StateVector();

    PirateBase(GameWorld world) {
        this.world = world;
    }

    /** Makes the station {@code uuid} the pirates' base, standing and unfound, from sim second {@code now}. */
    public void establish(String uuid, double now) {
        this.uuid = uuid;
        establishedAt = now;
        found = false;
        fallen = false;
        defenders.clear();
        scrambleDue = false;
        strainedSeconds = 0;
        lastStrainLook = Double.NaN;
    }

    /** Points at a loaded game's base, which has a new uuid; null if the save has none. */
    public void relink(String uuid) {
        this.uuid = uuid;
    }

    /** The base station's uuid, or null in a game without one. */
    public String uuid() {
        return uuid;
    }

    /** True once the player knows where it is. */
    public boolean found() {
        return found;
    }

    /** True while it stands. */
    public boolean standing() {
        return uuid != null && !fallen;
    }

    /** True if {@code stationUuid} is the base and the player has not found it yet: keep it off the map. */
    public boolean hidden(String stationUuid) {
        return uuid != null && !found && uuid.equals(stationUuid);
    }

    /** The player learns where the base is, {@code how} saying how (null for no notice). */
    public void reveal(String how) {
        if (uuid == null || found) return;
        found = true;
        world.notices.post(how == null ? "Pirate base located: " + name() + ", now on the traffic map"
                : how + ": " + name() + " is on the traffic map", NoticeLog.Kind.IMPORTANT);
    }

    /**
     * The base falls: no more pirates gather there, and the lawful factions' strain eases by
     * {@link #FALL_RELIEF}. {@code why} is for the notice.
     */
    public void fall(String why) {
        if (!standing()) return;
        fallen = true;
        found = true;
        defenders.clear();
        scrambleDue = false;
        strainedSeconds = 0;
        world.notices.post(name() + " has fallen" + (why == null ? "" : ": " + why), NoticeLog.Kind.IMPORTANT);
        world.tension.easeAll(FALL_RELIEF);
        // The boards offer what its fall opens (a Captain's operation) at once
        world.contracts.worldChanged();
    }

    /**
     * The raiders move back into the fallen base: it stands again, known to the player, crewed anew
     * if none of its pirates lived, and grows from now. Nothing happens to a base that stands.
     */
    public void rise() {
        if (uuid == null || standing()) return;
        fallen = false;
        found = true;
        establishedAt = world.macroSim.now();
        strainedSeconds = 0;
        defenders.clear();
        scrambleDue = false;
        Entity station = world.engine.getEntity(uuid);
        int pirates = world.standing.factions().index(HostileTraffic.PIRATES);
        if (station != null && pirates >= 0 && !anyPirateAboard()) StationCrew.spawnCrew(world.crew, station, pirates);
        world.notices.post("Raiders have moved back into " + name() + ": the pirates have a base again", NoticeLog.Kind.IMPORTANT);
    }

    /** Days the fallen base has spent with the factions strained, towards rising again. */
    public double strainedDays() {
        return strainedSeconds / 86_400;
    }

    /** While it lies fallen, counts the time any pair of lawful factions runs high, and rises once it is enough. */
    private void strain() {
        double now = world.macroSim.now();
        double since = Double.isNaN(lastStrainLook) ? 0 : Math.max(0, now - lastStrainLook);
        lastStrainLook = now;
        if (uuid == null || standing()) return;
        if (world.tension.anyHigh()) strainedSeconds += since;
        if (strainedSeconds >= world.tension.tuning().baseRisesAfterDays * 86_400) rise();
    }

    /**
     * Defenders the base scrambles at difficulty {@code d}: two, and one more for each
     * {@link #DIFFICULTY_PER_DEFENDER} above 1, up to {@link #MAX_DEFENDERS}. Round 6: one more each
     * 1.5 rather than each point, so a Captain's frigate (difficulty about 2.3) meets two, and wins.
     */
    public static int defendersFor(double d) {
        return Math.min(MAX_DEFENDERS, 2 + (int) Math.floor(Math.max(0, d - 1) / DIFFICULTY_PER_DEFENDER));
    }

    /** The names of the wing it scrambled, leader first; empty before a raid or once it has fallen. */
    public List<String> defenders() {
        return java.util.Collections.unmodifiableList(defenders);
    }

    /** True while a defender it scrambled is still in the fight: in the bubble and not beaten. */
    public boolean defended() {
        for (String id : defenders) if (fighting(id)) return true;
        return false;
    }

    private boolean fighting(String name) {
        String id = uuidOf(name);
        Entity e = id == null ? null : world.engine.getEntity(id);
        return e != null && world.macroSim.bubble().inBubble().contains(id) && !FirstPlay.beaten(world, e);
    }

    /** True while a defender it scrambled is alive and unbeaten anywhere, in the bubble or out on the rails. */
    private boolean anyDefenderLeft() {
        for (String name : defenders) {
            String id = uuidOf(name);
            Entity e = id == null ? null : world.engine.getEntity(id);
            if (e != null && !FirstPlay.beaten(world, e)) return true;
        }
        return false;
    }

    /** The uuid of the hostile named {@code name}, or null if none flies now. */
    private String uuidOf(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) if (name.equals(e.getValue().displayName)) return e.getKey();
        return null;
    }

    /**
     * Flies out a scramble {@link #update} found due: {@link #defendersFor} raiders, the first leading
     * the rest, launched beside the base and hunting the player. Call after the engine's update.
     */
    public void afterUpdate() {
        if (!scrambleDue) return;
        scrambleDue = false;
        if (!standing() || world.getPlayer() == null) return;
        double now = world.macroSim.now();
        StateVector at = new StateVector();
        if (!offset(now, at)) return;
        int n = defendersFor(Difficulty.of(world));
        List<String> wing = world.hostiles.raid(uuid, n);
        if (wing.isEmpty()) return;
        defenders.clear();
        String parent = world.macroSim.gateway().anchorParentUuid();
        for (int i = 0; i < wing.size(); i++) {
            String id = wing.get(i);
            HostileComponent h = world.macroSim.hostiles().get(id);
            if (h != null) {
                h.wingOf = i == 0 ? null : wing.get(0);
                h.targetUuid = HostileComponent.PLAYER;
                defenders.add(h.displayName);
            }
            // Launched from the base's side, a little apart, where the bubble takes them in at once
            double angle = 2 * Math.PI * i / wing.size();
            StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
            world.macroSim.rails().attach(id, parent, s.set(s.x + at.x + LAUNCH_OFFSET * Math.cos(angle),
                    s.y + at.y + LAUNCH_OFFSET * Math.sin(angle), s.vx, s.vy), now);
        }
        world.notices.post(String.format(java.util.Locale.ROOT, "%s scrambles %d raider%s against you", name(), wing.size(),
                wing.size() == 1 ? "" : "s"), NoticeLog.Kind.WARNING);
    }

    /** Raiders beyond the difficulty's that the base keeps now: one more each {@link #GROWTH_DAYS} it stands. */
    public int extraRaiders() {
        if (!standing() || Double.isNaN(establishedAt)) return 0;
        double days = (world.macroSim.now() - establishedAt) / 86_400;
        return (int) Math.min(MAX_EXTRA, Math.floor(days / GROWTH_DAYS));
    }

    /** The base's name, for notices. */
    public String name() {
        var node = uuid == null ? null : world.macroSim.station(uuid);
        return node == null || node.displayName == null ? "The pirate base" : node.displayName;
    }

    @Override
    public void update(float deltaTime) {
        if (uuid == null) return;
        since += deltaTime;
        if (since < CHECK_SECONDS) return;
        since = 0f;
        strain();
        // A station stays on the rails; close by, the bubble shows it as a kinematic transient
        look(world.macroSim.bubble().inBubble().contains(uuid) || world.macroSim.bubble().fastPasses().contains(uuid));
    }

    /**
     * Finds the base if the player is near or it is {@code here} in the bubble, and lets it fall if it
     * is here and empty. Here with the player close, it scrambles its defenders, unless some of the
     * last wing are still about; once they are all beaten or driven off, it strikes.
     */
    void look(boolean here) {
        if (uuid == null) return;
        if (!found && (here || near())) reveal(null);
        if (here && standing() && !anyPirateAboard()) fall("no pirate is left alive aboard");
        if (!here || !standing()) return;
        double range = playerRange();
        if (!defenders.isEmpty() && !defended() && range <= STRIKE_RANGE) {
            fall("its defenders are beaten, and it strikes its colours");
        } else if (!anyDefenderLeft() && range <= SCRAMBLE_RANGE) {
            scrambleDue = true;
        }
    }

    /** Meters from the player's ship, in the bubble, to the base; infinite if the ship is not in the bubble. */
    private double playerRange() {
        Entity player = world.getPlayer();
        PhysicsBodyComponent b = player == null ? null : player.getComponent(PhysicsBodyComponent.class);
        StateVector at = new StateVector();
        if (b == null || b.body == null || !offset(world.macroSim.now(), at)) return Double.POSITIVE_INFINITY;
        var p = PhysicsManager.getPosition(b.body, new com.badlogic.gdx.math.Vector2());
        return Math.hypot(at.x - p.x, at.y - p.y);
    }

    /** The base's place in the bubble at sim second {@code t}: its rails state less the anchor's. False if it is elsewhere. */
    private boolean offset(double t, StateVector out) {
        var rails = world.macroSim.rails();
        if (!rails.isOnRails(uuid) || !rails.parentOf(uuid).equals(world.macroSim.gateway().anchorParentUuid())) return false;
        rails.stateAt(uuid, t, out);
        world.macroSim.gateway().anchorStateAt(t, b);
        out.set(out.x - b.x, out.y - b.y, out.vx - b.vx, out.vy - b.vy);
        return true;
    }

    /** True if the player is within {@link #DETECT_RANGE} of the base, both round the same body. */
    private boolean near() {
        var rails = world.macroSim.rails();
        if (!rails.isOnRails(uuid) || !rails.parentOf(uuid).equals(world.macroSim.gateway().anchorParentUuid())) return false;
        double now = world.macroSim.now();
        rails.stateAt(uuid, now, a);
        world.macroSim.gateway().anchorStateAt(now, b);
        return Math.hypot(a.x - b.x, a.y - b.y) < DETECT_RANGE;
    }

    private boolean anyPirateAboard() {
        Entity station = world.engine.getEntity(uuid);
        if (station == null) return true;
        int pirates = world.standing.factions().index(HostileTraffic.PIRATES);
        for (Entity e : world.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            GridPositionComponent at = e.getComponent(GridPositionComponent.class);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (at != null && at.ship == station && c != null && c.faction == pirates && c.isAlive()) return true;
        }
        return false;
    }

    @Override
    public String saveKey() {
        return "pirateBase";
    }

    @Override
    public void write(Json json) {
        json.writeValue("establishedAt", establishedAt);
        json.writeValue("found", found);
        json.writeValue("fallen", fallen);
        json.writeValue("defenders", defenders.toArray(new String[0]), String[].class);
        json.writeValue("strainedSeconds", strainedSeconds);
        json.writeValue("lastStrainLook", lastStrainLook);
    }

    @Override
    public void read(Json json, JsonValue data) {
        establishedAt = data.getDouble("establishedAt", Double.NaN);
        found = data.getBoolean("found", false);
        fallen = data.getBoolean("fallen", false);
        defenders.clear();
        String[] wing = data.has("defenders") ? json.readValue(String[].class, data.get("defenders")) : null;
        if (wing != null) for (String id : wing) if (id != null) defenders.add(id);
        scrambleDue = false;
        strainedSeconds = data.getDouble("strainedSeconds", 0);
        lastStrainLook = data.getDouble("lastStrainLook", Double.NaN);
    }
}
