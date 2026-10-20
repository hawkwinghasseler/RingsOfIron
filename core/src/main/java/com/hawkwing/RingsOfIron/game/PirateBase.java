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
 * base still in the bubble, the base strikes its colours and falls (round 7: wherever in the bubble the
 * player is; round 5 also asked them to be within 20 km, which nothing on screen said). A fallen base
 * eases the strain between the lawful factions by {@link #FALL_RELIEF} (the pirates were what set
 * them against each other). The defenders are saved, so a raid broken off and flown again meets
 * whoever is left of the wing rather than a new one.
 *
 * <p>Round 6: a fallen base does not stay empty. Once the lawful factions have spent
 * {@link com.ringsofiron.moddingdata.defs.TensionDef#baseRisesAfterDays} days with any pair of them
 * at high tension ({@link FactionTension#anyHigh}) since it fell, the raiders move back in
 * ({@link #rise}): it stands again where it was, known, crewed and growing, so the late career keeps
 * a base to raid.
 *
 * <p>Round 6: the defence launches in an order, and the base has guns of its own. The lightest
 * defender (by {@link HostileTraffic#firepower}) goes out first, alone; the rest follow as a wing
 * once it is beaten or {@link #SECOND_WAVE_SECONDS} have passed, and a railgun raider never leads
 * (round 5 on seed 2026: a railgun raider first put 33 of 33 rounds into the frigate and decided the
 * raid). At its first scramble the base also mans {@link #BATTERIES} gun platforms
 * ({@link HostileTraffic#BATTERY}) moored beside it, which fire on the player while it stands. It
 * strikes once its wing is beaten, whatever its guns, which fall silent with it. They stay moored
 * there, saved like any hostile, and open fire again when the player comes back.
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
    /** How far from the base the defenders launch, m, each a little further round. */
    static final double LAUNCH_OFFSET = 4_000;
    /**
     * Difficulty for each defender past the first two: the knob to tune the raid by. Round 7 lowered
     * round 5's 1 to 0.6, so a Commander's gunship met three and a Captain's frigate four. Round 8
     * raises it to 0.8: a Commander's gunship still meets three, a Captain's frigate (about 2.5) three
     * now, since with the helm holding on the base under fire four left the frigate 5 wins in 10.
     */
    public static final double DIFFICULTY_PER_DEFENDER = 0.8;
    /** Most defenders the base scrambles. */
    public static final int MAX_DEFENDERS = 4;
    /** Tension eased between every pair of lawful factions when the base falls. */
    public static final float FALL_RELIEF = 15f;
    /** The rest of the defence launches this long after the first defender at the latest, sim seconds (round 6: 90). */
    public static final double SECOND_WAVE_SECONDS = 45;
    /** Gun platforms the base mans. */
    public static final int BATTERIES = 2;
    /** How far behind the base, along its orbit, the first gun platform is moored, and each next one further, m. */
    public static final double BATTERY_SPACING = 1_500;

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
    /** Defenders scrambled but not yet launched (the second wave), by name; launched after the engine's update. */
    private final List<String> waiting = new ArrayList<>();
    /** Sim second the second wave launches at the latest; NaN when none waits. */
    private double waveAt = Double.NaN;
    private boolean waveDue;
    /** The names of the gun platforms it has manned; empty until its first scramble. */
    private final List<String> guns = new ArrayList<>();
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
        clearWave();
        guns.clear();
    }

    private void clearWave() {
        waiting.clear();
        waveAt = Double.NaN;
        waveDue = false;
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

    /** True for one of the base's gun platforms ({@code displayName}) once the base has struck: it fires no more. */
    public boolean silenced(String displayName) {
        return uuid != null && fallen && guns.contains(displayName);
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
        // Round 8: its darts in flight go ballistic, so the missile warning clears with the fall
        // (round 7's window: the warning stayed up about a minute after it struck)
        for (String name : defenders) world.missiles.goBallistic(shipNamed(name));
        for (String name : guns) world.missiles.goBallistic(shipNamed(name));
        defenders.clear();
        scrambleDue = false;
        strainedSeconds = 0;
        clearWave();
        // Its guns fall silent with it
        for (String name : guns) {
            HostileComponent h = hostileNamed(name);
            if (h != null) h.targetUuid = null;
        }
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
        clearWave();
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
     * {@link #DIFFICULTY_PER_DEFENDER} above 1, up to {@link #MAX_DEFENDERS}. A Captain's frigate
     * (difficulty about 2.3 to 2.5) meets four (round 7; three before).
     */
    public static int defendersFor(double d) {
        return Math.min(MAX_DEFENDERS, 2 + (int) Math.floor(Math.max(0, d - 1) / DIFFICULTY_PER_DEFENDER));
    }

    /** The names of the gun platforms it has manned, beaten ones included; empty before its first scramble. */
    public List<String> guns() {
        return java.util.Collections.unmodifiableList(guns);
    }

    /**
     * The names of the wing it scrambled, in the order they launch: first the lone defender, then the
     * second wave. Its guns ({@link #guns}) are not among them: the base strikes once its wing is
     * beaten, whatever its guns. Empty before a raid or once it has fallen.
     */
    public List<String> defenders() {
        return java.util.Collections.unmodifiableList(defenders);
    }

    /** True if the pirate named {@code name} is one of the wing the base scrambled, while it stands. */
    public boolean defending(String name) {
        return standing() && name != null && defenders.contains(name);
    }

    /**
     * The player lost the raid: the defence that beat them is forgotten, so a defender leaving them
     * drifting is never counted as driven off, and a raid flown again meets a new scramble. Its guns
     * stand down until the player comes back for another try.
     */
    public void raidLost() {
        if (defenders.isEmpty()) return;
        defenders.clear();
        clearWave();
        for (String name : guns) {
            HostileComponent h = hostileNamed(name);
            if (h != null) h.targetUuid = null;
        }
        world.notices.post("The raid on " + name() + " is lost: it still stands", NoticeLog.Kind.IMPORTANT);
    }

    /** True while a defender it scrambled is still in the fight: in the bubble, not beaten and not driven off. */
    public boolean defended() {
        for (String id : defenders) if (fighting(id)) return true;
        return false;
    }

    private boolean fighting(String name) {
        String id = uuidOf(name);
        Entity e = id == null ? null : world.engine.getEntity(id);
        if (e == null || !world.macroSim.bubble().inBubble().contains(id) || FirstPlay.beaten(world, e)) return false;
        // Broken off and running (a wingman goes when its leader is beaten) is driven off: on seed 12
        // the last defender fled with its leader and, still in the bubble, kept the base from striking
        HostileComponent h = e.getComponent(HostileComponent.class);
        return h == null || !h.brokeOff;
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

    private HostileComponent hostileNamed(String name) {
        String id = uuidOf(name);
        return id == null ? null : world.macroSim.hostiles().get(id);
    }

    /** The ship of the hostile named {@code name}, or null if none flies now. */
    private Entity shipNamed(String name) {
        String id = uuidOf(name);
        return id == null ? null : world.engine.getEntity(id);
    }

    /** The uuid of the hostile named {@code name}, or null if none flies now. */
    private String uuidOf(String name) {
        for (var e : world.macroSim.hostiles().all().entrySet()) if (name.equals(e.getValue().displayName)) return e.getKey();
        return null;
    }

    /**
     * Flies out a scramble {@link #update} found due: {@link #defendersFor} raiders put in launch
     * order ({@link #launchOrder}), the first launched alone beside the base and hunting the player
     * and the rest left for the second wave; and, the first time, its gun platforms manned. Or the
     * second wave, once due. Call after the engine's update.
     */
    public void afterUpdate() {
        if (waveDue) {
            waveDue = false;
            launchWave();
        }
        if (!scrambleDue) return;
        scrambleDue = false;
        if (!standing() || world.getPlayer() == null) return;
        StateVector at = new StateVector();
        if (!offset(world.macroSim.now(), at)) return;
        int n = defendersFor(Difficulty.of(world));
        List<String> wing = launchOrder(world.hostiles.raid(uuid, n));
        if (wing.isEmpty()) return;
        defenders.clear();
        clearWave();
        for (String id : wing) {
            HostileComponent h = world.macroSim.hostiles().get(id);
            if (h == null) continue;
            h.targetUuid = HostileComponent.PLAYER;
            defenders.add(h.displayName);
        }
        // The rest fly as a wing: the second leads it
        for (int i = 2; i < wing.size(); i++) {
            HostileComponent h = world.macroSim.hostiles().get(wing.get(i));
            if (h != null) h.wingOf = wing.get(1);
        }
        launch(wing.subList(0, 1));
        for (int i = 1; i < wing.size(); i++) waiting.add(defenders.get(i));
        if (!waiting.isEmpty()) waveAt = world.macroSim.now() + SECOND_WAVE_SECONDS;
        if (guns.isEmpty()) {
            for (String id : world.hostiles.moorGuns(uuid, BATTERIES, BATTERY_SPACING)) {
                HostileComponent h = world.macroSim.hostiles().get(id);
                if (h != null) guns.add(h.displayName);
            }
        }
        boolean manned = false;
        for (String name : guns) {
            HostileComponent h = hostileNamed(name);
            Entity e = h == null ? null : world.engine.getEntity(uuidOf(name));
            if (e == null || FirstPlay.beaten(world, e)) continue;
            h.targetUuid = HostileComponent.PLAYER;
            manned = true;
        }
        world.notices.post(String.format(java.util.Locale.ROOT, "%s scrambles %d raider%s against you%s", name(), wing.size(),
                wing.size() == 1 ? "" : "s", manned ? ", and its guns open up" : ""), NoticeLog.Kind.WARNING);
    }

    /**
     * The defence's launch order: lightest first by {@link HostileTraffic#firepower} (ties by name),
     * and a railgun raider never first while there is another.
     */
    List<String> launchOrder(List<String> wing) {
        List<String> out = new ArrayList<>(wing);
        java.util.Comparator<String> light = java.util.Comparator.comparingDouble(id -> {
            HostileComponent h = world.macroSim.hostiles().get(id);
            return h == null ? 0 : h.firepower;
        });
        out.sort(light.thenComparing(id -> {
            HostileComponent h = world.macroSim.hostiles().get(id);
            return h == null || h.displayName == null ? "" : h.displayName;
        }));
        if (out.size() > 1 && railgun(out.get(0))) {
            for (int i = 1; i < out.size(); i++) {
                if (!railgun(out.get(i))) {
                    out.add(0, out.remove(i));
                    break;
                }
            }
        }
        return out;
    }

    /** True if the ship {@code id} carries a railgun. */
    private boolean railgun(String id) {
        Entity e = world.engine.getEntity(id);
        var weapons = e == null ? null : e.getComponent(com.ringsofiron.combatsys.components.WeaponsComponent.class);
        if (weapons == null) return false;
        for (var m : weapons.mounts) if (m.def != null && m.def.id != null && m.def.id.startsWith("railgun")) return true;
        return false;
    }

    /** Launches the second wave: those still waiting and not already in the bubble. */
    private void launchWave() {
        List<String> ids = new ArrayList<>();
        for (String name : waiting) {
            String id = uuidOf(name);
            if (id != null && !world.macroSim.bubble().inBubble().contains(id)) ids.add(id);
        }
        clearWave();
        if (!standing() || ids.isEmpty()) return;
        if (launch(ids)) world.notices.post(String.format(java.util.Locale.ROOT, "%s launches %d more raider%s", name(),
                ids.size(), ids.size() == 1 ? "" : "s"), NoticeLog.Kind.WARNING);
    }

    /** Launches {@code ids} from the base's side, a little apart, where the bubble takes them in at once. */
    private boolean launch(List<String> ids) {
        double now = world.macroSim.now();
        StateVector at = new StateVector();
        if (!offset(now, at)) return false;
        String parent = world.macroSim.gateway().anchorParentUuid();
        for (int i = 0; i < ids.size(); i++) {
            double angle = 2 * Math.PI * i / ids.size();
            StateVector s = world.macroSim.gateway().anchorStateAt(now, new StateVector());
            world.macroSim.rails().attach(ids.get(i), parent, s.set(s.x + at.x + LAUNCH_OFFSET * Math.cos(angle),
                    s.y + at.y + LAUNCH_OFFSET * Math.sin(angle), s.vx, s.vy), now);
        }
        return true;
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
     * last wing are still about; once they are all beaten or driven off, it strikes, here at any range.
     */
    void look(boolean here) {
        if (uuid == null) return;
        if (!found && (here || near())) reveal(null);
        if (here && standing() && !anyPirateAboard()) fall("no pirate is left alive aboard");
        if (!here || !standing()) return;
        double range = playerRange();
        if (!waiting.isEmpty() && (world.macroSim.now() >= waveAt || !leadDefenderFighting())) waveDue = true;
        // Round 7: the fight drifts (a real-window raid ended 30 km off, the base still in the bubble),
        // so the base strikes wherever the player is once it is here and its defence is beaten
        // The player beaten, the raid is lost: the defence leaves them drifting as winners, not
        // driven off (round 7, real window: the frigate's reactor went down and Rustwake struck to
        // it). The wing is forgotten, and none scrambles against a beaten ship, so a raid flown
        // again meets a new one
        Entity player = world.getPlayer();
        if (player != null && ShipDisablement.isDisabled(player) && FirstPlay.beaten(world, player)) {
            raidLost();
            return;
        }
        if (!defenders.isEmpty() && waiting.isEmpty() && !defended()) {
            fall("its defenders are beaten, and it strikes its colours");
        } else if (!anyDefenderLeft() && range <= SCRAMBLE_RANGE) {
            scrambleDue = true;
        } else if (range <= SCRAMBLE_RANGE) {
            // Back for another try: whoever is left of the defence, and its guns, go for the player again
            for (String name : defenders) {
                HostileComponent h = hostileNamed(name);
                if (h != null && !h.brokeOff) h.targetUuid = HostileComponent.PLAYER;
            }
            for (String name : guns) {
                HostileComponent h = hostileNamed(name);
                if (h != null) h.targetUuid = HostileComponent.PLAYER;
            }
        }
    }

    /** True while the defender that launched first is still in the fight. */
    private boolean leadDefenderFighting() {
        return !defenders.isEmpty() && fighting(defenders.get(0));
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
        json.writeValue("waiting", waiting.toArray(new String[0]), String[].class);
        if (!Double.isNaN(waveAt)) json.writeValue("waveAt", waveAt);
        json.writeValue("guns", guns.toArray(new String[0]), String[].class);
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
        clearWave();
        readNames(json, data, "waiting", waiting);
        waveAt = data.getDouble("waveAt", Double.NaN);
        guns.clear();
        readNames(json, data, "guns", guns);
    }

    private static void readNames(Json json, JsonValue data, String key, List<String> into) {
        String[] names = data.has(key) ? json.readValue(String[].class, data.get(key)) : null;
        if (names != null) for (String n : names) if (n != null) into.add(n);
    }
}
