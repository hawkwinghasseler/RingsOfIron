package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.coreengine.SaveParticipant;
import com.ringsofiron.macrosim.rails.StateVector;

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

    private final GameWorld world;
    private String uuid;
    private double establishedAt = Double.NaN;
    private boolean found;
    private boolean fallen;
    private float since = CHECK_SECONDS;
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

    /** The base falls: no more pirates gather there. {@code why} is for the notice. */
    public void fall(String why) {
        if (!standing()) return;
        fallen = true;
        found = true;
        world.notices.post(name() + " has fallen" + (why == null ? "" : ": " + why), NoticeLog.Kind.IMPORTANT);
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
        look(world.macroSim.bubble().inBubble().contains(uuid));
    }

    /** Finds the base if the player is near or it is {@code here} in the bubble, and lets it fall if it is here and empty. */
    void look(boolean here) {
        if (uuid == null) return;
        if (!found && (here || near())) reveal(null);
        if (here && standing() && !anyPirateAboard()) fall("no pirate is left alive aboard");
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
    }

    @Override
    public void read(Json json, JsonValue data) {
        establishedAt = data.getDouble("establishedAt", Double.NaN);
        found = data.getBoolean("found", false);
        fallen = data.getBoolean("fallen", false);
    }
}
