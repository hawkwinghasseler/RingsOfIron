package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.ringsofiron.aicrew.CrewQueries;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.combatsys.components.SensorComponent;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.combatsys.fire.Track;
import com.ringsofiron.combatsys.systems.SensorTrackingSystem;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;

import java.util.function.Supplier;

/**
 * ui-hud's {@link WeaponStatusProvider}: each of the player's mounts from combat-sys, who works it
 * ({@link GunCrews}), whether it can fire and why not, and its chance on the locked target as the
 * fire arcs show it ({@link #odds}).
 */
public class PlayerWeaponStatus implements WeaponStatusProvider {
    /** The chance a shot from a mount hits a target as it dodges, writing the range; NaN with no track. */
    public interface Odds {
        float hitChance(WeaponMountComponent mount, Entity target, float[] range);
    }

    private final GameContext ctx;
    private final GunCrews gunCrews;
    private final CrewService crew;
    private final Bridge bridge;
    private final DockingSystem docking;
    private final SensorTrackingSystem sensors;
    /** The fire arcs' odds ({@code FireArcOverlay#hitChance}), set by the game; null leaves hit chances NaN. */
    public Odds odds;
    /** The player's fire control, for the decoy; set by the game once it has one. */
    public Supplier<PlayerGunnery> gunnery;
    private final RoomNames places = new RoomNames();
    private Entity placesFor;
    private final ObjectIntMap<String> names = new ObjectIntMap<>();
    private final ObjectIntMap<String> numbered = new ObjectIntMap<>();
    private final Vector2 at = new Vector2();
    private final float[] range = new float[1];

    public PlayerWeaponStatus(GameContext ctx, GunCrews gunCrews, CrewService crew, Bridge bridge, DockingSystem docking,
                              SensorTrackingSystem sensors) {
        this.ctx = ctx;
        this.gunCrews = gunCrews;
        this.crew = crew;
        this.bridge = bridge;
        this.docking = docking;
        this.sensors = sensors;
    }

    @Override
    public void collect(WeaponFrame out) {
        Entity ship = ctx.player();
        WeaponsComponent weapons = ship == null ? null : ship.getComponent(WeaponsComponent.class);
        out.holdFire = gunCrews.holdFire;
        PlayerGunnery gun = gunnery == null ? null : gunnery.get();
        out.decoyReload = PlayerGunnery.DECOY_COOLDOWN;
        out.decoyCooldown = gun == null ? 0f : gun.decoyCooldown();
        if (weapons == null) return;
        out.missilesLeft = gun != null ? gun.missilesLeft() : 0;

        SensorComponent sensor = ship.getComponent(SensorComponent.class);
        Entity locked = sensor == null ? null : sensor.lockedTarget;
        HudMarkerComponent marker = locked == null ? null : locked.getComponent(HudMarkerComponent.class);
        out.target = locked == null ? "" : marker != null ? marker.label : "contact";
        Track track = sensor == null || locked == null ? null : sensor.trackFor(locked);
        boolean seen = track != null && track.positionAt(sensors.ageOf(track), at) != null;
        Body body = docking.bodyOf(ship);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (placesFor != ship) {
            placesFor = ship;
            places.reset();
        }
        Entity console = GunCrews.weaponsConsole(ship);
        boolean consoleCrewed = console != null && CrewQueries.gunneryAt(crew, ship, console) != CrewQueries.NO_CREW;

        for (int i = 0; i < weapons.mounts.size; i++) {
            WeaponMountComponent m = weapons.mounts.get(i);
            WeaponDef def = m.def;
            if (def == null) continue;
            Mount row = out.add();
            row.index = i;
            row.weapon = def.displayName();
            row.kind = def.pointDefense ? Kind.POINT_DEFENSE : def.mount == WeaponDef.MountType.LAUNCHER ? Kind.LAUNCHER
                    : def.mount == WeaponDef.MountType.BEAM ? Kind.BEAM : Kind.GUN;
            row.hardpointX = m.hardpoint.x;
            row.hardpointY = m.hardpoint.y;
            row.facingDeg = m.facing * MathUtils.radiansToDegrees;
            row.arcDeg = def.firingArcDeg;
            row.cooldown = m.cooldown;
            row.reload = def.cooldown;
            row.magazine = def.magazine > 0 ? def.magazine : -1;
            row.ammo = def.magazine > 0 ? PlayerGunnery.missilesLeft(m) : -1;
            row.engaging = m.engaged != null && (m.beamOn || m.sinceShot < Math.max(1f, def.cooldown * 2f));
            row.defending = m.defending || (def.pointDefense && m.engaged != null);
            row.weaponsFree = m.weaponsFree;
            if (row.defending) out.pointDefenseActive = true;

            SubsystemComponent s = m.subsystem == null ? null : m.subsystem.getComponent(SubsystemComponent.class);
            if (s != null) {
                row.health = s.health;
                if (s.footprint != null) {
                    row.tileX = s.footprint.originX;
                    row.tileY = s.footprint.originY;
                }
            }
            row.state = state(m, def, s);
            row.controller = PlayerGunnery.inHand(bridge, m) ? Controller.PLAYER
                    : def.pointDefense ? Controller.AUTO
                    : isGun(s) && CrewQueries.gunneryAt(crew, ship, m.subsystem) != CrewQueries.NO_CREW ? Controller.CREW
                    : consoleCrewed ? Controller.CONSOLE : Controller.NONE;

            if (seen && body != null) {
                float facing = body.getAngle() + m.facing;
                float bearing = MathUtils.atan2(at.y - m.muzzle.y, at.x - m.muzzle.x);
                float off = Math.abs(wrap(bearing - facing)) * MathUtils.radiansToDegrees;
                row.inArc = def.firingArcDeg >= 359f || off <= def.firingArcDeg * 0.5f;
                range[0] = Float.NaN;
                row.hitChance = def.mount == WeaponDef.MountType.LAUNCHER ? Float.NaN : odds == null ? Float.NaN : odds.hitChance(m, locked, range);
                row.range = Float.isNaN(range[0]) ? at.dst(m.muzzle) : range[0];
            }
        }
        name(out, grid);
    }

    private WeaponStatusProvider.State state(WeaponMountComponent m, WeaponDef def, SubsystemComponent s) {
        if (s != null && (s.isDestroyed() || s.isDisabled())) return State.DESTROYED;
        if (m.underRefit) return State.REFIT;
        if (s != null && s.shorted) return State.SHORTED;
        if (s != null && s.stunned > 0f) return State.STUNNED;
        if (s != null && (!s.switchedOn || !s.isOperational)) return State.NO_POWER;
        if (def.mount == WeaponDef.MountType.LAUNCHER && PlayerGunnery.missilesLeft(m) == 0) return State.EMPTY;
        if (m.cooldown > 0f) return State.RELOADING;
        double shot = (double) def.powerDraw * def.cooldown;
        if (s != null && s.circuit != null && shot > 0 && s.circuit.stored < shot) return State.CHARGING;
        return State.READY;
    }

    /** Names each mount by where it sits and what it is: "FWD GUN", "PORT PD 2". */
    private void name(WeaponFrame out, ShipGridComponent grid) {
        names.clear();
        numbered.clear();
        for (Mount row : out.mounts) {
            row.name = base(row, grid);
            names.getAndIncrement(row.name, 0, 1);
        }
        for (Mount row : out.mounts) {
            if (names.get(row.name, 0) > 1) row.name = row.name + " " + (numbered.getAndIncrement(row.name, 0, 1) + 1);
        }
    }

    private String base(Mount row, ShipGridComponent grid) {
        String what = row.kind == Kind.POINT_DEFENSE ? "PD" : row.kind == Kind.LAUNCHER ? "MSL" : row.kind == Kind.BEAM ? "BEAM" : "GUN";
        if (grid == null) return what;
        String place = places.place(grid, row.hardpointX, row.hardpointY);
        // "MID" says nothing of a gun's side: drop it, keeping the side
        if (place.equals("MID")) return what;
        if (place.startsWith("MID ")) place = place.substring(4);
        return place + " " + what;
    }

    private static boolean isGun(SubsystemComponent s) {
        return s != null && s.type == SubsystemComponent.SubsystemType.TURRET_MOUNT;
    }

    private static float wrap(float a) {
        while (a > MathUtils.PI) a -= MathUtils.PI2;
        while (a < -MathUtils.PI) a += MathUtils.PI2;
        return a;
    }
}
