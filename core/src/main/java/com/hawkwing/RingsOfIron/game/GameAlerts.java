package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.ShipTaskBoardComponent;
import com.ringsofiron.aicrew.StabiliseTask;
import com.ringsofiron.aicrew.TreatTask;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.aicrew.btree.CrewTasks;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.crew.CrewTask;
import com.ringsofiron.interiorgrid.crew.RepairTask;
import com.ringsofiron.interiorgrid.crew.RescueTask;
import com.ringsofiron.shipops.AbandonShip;
import com.ringsofiron.shipops.DisabledComponent;
import com.ringsofiron.shipops.ShipDisablement;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.AlertRegistry.TimerKind;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.ThreatProvider.ThreatFrame;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;

import java.util.List;
import java.util.Locale;

/**
 * Raises the game's alerts into ui-hud's {@link AlertRegistry} each frame, and copies its notices
 * into the {@link MessageFeed}: the common data the HUD variants read. Every condition that holds is
 * raised again each frame; one that is not has cleared.
 *
 * <p>Sources: missiles inbound (with the burn-aware impact time), propellant against the trip home,
 * each room's fire, breach, air and smoke, the hull, every system that needs power and is not
 * working, the ship disabled or abandoning, the captain's body and suit, critically wounded crew,
 * crew outside, the helm, hostiles, the shield, life support's oxygen and empty launchers.
 *
 * <p>Each alert crew can work says how many are on it ({@link AlertRegistry.Alert#handling}), from
 * the tasks claimed on the ship's task board: putting out a fire in the room, sealing its breaches,
 * repairing or resetting the system, treating the casualty, repairing the hull. It names one of them
 * ({@link AlertRegistry.Alert#who}): one at the work before one on the way, then the soonest done,
 * with the seconds of work they have left at their pace where the work has a set time.
 */
public class GameAlerts {
    /** Suit air below this share of full is a caution, below {@link #SUIT_WARNING} a warning. */
    static final float SUIT_CAUTION = 0.25f, SUIT_WARNING = 0.1f;
    /** Hull integrity below this is a warning (below {@link ShipAlerts#HULL_DAMAGED} a caution). */
    static final float HULL_WARNING = 0.4f;
    /** Oxygen tanks below this share are a caution. */
    static final float OXYGEN_CAUTION = 0.2f;
    /**
     * Alerts a notice can belong to ({@link NoticeLog.Notice#alert}), by id: the salvo's notice, low
     * propellant's and the ship disabled's each have an alert carrying their condition.
     */
    public static final String MISSILE = "missile", PROPELLANT = "propellant", DISABLED = "disabled";

    private final GameContext ctx;
    private final FlightWarnings warnings;
    private final Spacewalks spacewalks;
    private final CrewService crew;
    private final HudData data;
    private final RoomNames rooms = new RoomNames();
    private Entity roomsFor;
    private final ShipAlerts scan = new ShipAlerts();
    private final PowerStatusProvider.PowerFrame power = new PowerStatusProvider.PowerFrame();
    /** This frame's claimed tasks on the ship's board, and the crew counted so far by {@link #working}. */
    private final Array<CrewTask> claimed = new Array<>();
    private final Array<Entity> counted = new Array<>();
    /** The task each of {@link #counted} was counted for, by index; null for a medic treating without one. */
    private final Array<CrewTask> countedTasks = new Array<>();
    /** A system's repairers work from beside its footprint: within this many tiles of its origin. */
    static final int REPAIR_REACH = 3;

    public GameAlerts(GameContext ctx, FlightWarnings warnings, Spacewalks spacewalks, CrewService crew, HudData data) {
        this.ctx = ctx;
        this.warnings = warnings;
        this.spacewalks = spacewalks;
        this.crew = crew;
        this.data = data;
        ctx.notices.posted.add((signal, notice) -> data.messages.posted());
    }

    /**
     * Raises this frame's alerts and refreshes the messages, from the data's snapshots: call after
     * {@link HudData#refresh}.
     */
    public void update() {
        AlertRegistry alerts = data.alerts;
        alerts.begin(ctx.engine.getSimSeconds());
        try {
            Entity ship = ctx.player();
            if (ship != null) raise(ship, alerts);
        } finally {
            alerts.end();
        }
        messages();
    }

    private void raise(Entity ship, AlertRegistry alerts) {
        UiContext ctx = data.ctx;
        claimed(ship);
        missiles(data.threatFrame, alerts);
        if (warnings.propellantLow()) {
            ShipStatus s = data.shipStatus;
            double home = warnings.homePropellantKg();
            alerts.raise(PROPELLANT, Double.isFinite(home) && s.propellantKg < home ? Level.WARNING : Level.CAUTION, Category.FLIGHT)
                    .set("FUEL", String.format(Locale.ROOT, "Propellant low: %,.0f kg left", s.propellantKg)
                            + (Double.isFinite(home) ? String.format(Locale.ROOT, ", %,.0f kg takes you home", home) : ""));
        }

        DisabledComponent.Reason disabled = ShipDisablement.reason(ship);
        if (disabled != null) alerts.raise(DISABLED, Level.WARNING, Category.SHIP).set("DISABLED", "Ship disabled: " + disabled.label);
        if (AbandonShip.abandoning(ship)) alerts.raise("abandon", Level.WARNING, Category.SHIP).set("ABANDON", "Abandon ship: crew to the pods");

        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid != null) {
            if (roomsFor != ship) {
                roomsFor = ship;
                rooms.reset();
            }
            rooms(grid, alerts);
            hull(grid, alerts);
        }
        systems(alerts);
        captain(data.captainStatus, alerts);
        crew(ship, alerts);

        if (ctx.crewOutside) {
            String line = spacewalks.statusLine();
            alerts.raise("eva", Level.ADVISORY, Category.CREW).set("EVA", line == null ? "Crew outside" : line);
        }
        if (!ctx.helmFault.isEmpty()) alerts.raise("helm", Level.CAUTION, Category.FLIGHT).set("HELM", "Helm: " + ctx.helmFault);
        TargetStatus target = data.targetStatus;
        if (target.present && target.firingOnUs) {
            alerts.raise("hostile", Level.CAUTION, Category.TACTICAL).set("HOSTILE", target.label + " has you locked");
        } else if (ctx.combat && ctx.missilesInbound == 0) {
            alerts.raise("hostile", Level.ADVISORY, Category.TACTICAL).set("HOSTILE", "Hostile contact");
        }
        shield(data.shieldStatus, ctx, alerts);
        oxygen(data.shipStatus, alerts);
        for (WeaponStatusProvider.Mount m : data.weaponFrame.mounts) {
            if (m.state == WeaponStatusProvider.State.EMPTY) {
                alerts.raise("empty:" + m.index, Level.ADVISORY, Category.TACTICAL).set("MSL EMPTY", m.name + " has no missiles left");
            }
        }
    }

    private static void missiles(ThreatFrame frame, AlertRegistry alerts) {
        int n = frame.incoming();
        if (n == 0) return;
        float soonest = frame.soonestImpact();
        alerts.raise(MISSILE, Level.WARNING, Category.TACTICAL)
                .set("MISSILE", n == 1 ? "Missile inbound" : n + " missiles inbound")
                .count(n)
                .timer(soonest, Float.isNaN(soonest) ? TimerKind.NONE : TimerKind.HARM);
    }

    /** Each room's fire, breach, air and smoke. */
    private void rooms(ShipGridComponent grid, AlertRegistry alerts) {
        for (Compartment c : rooms.rooms(grid)) {
            String name = rooms.of(grid, c);
            int burning = 0, breaches = 0, fx = -1, fy = -1, bx = -1, by = -1;
            for (int i = 0; i < c.tiles.size; i++) {
                int packed = c.tiles.get(i);
                int x = packed % grid.width, y = packed / grid.width;
                Tile t = grid.getTile(x, y);
                if (t == null) continue;
                if (t.isBurning()) {
                    burning++;
                    fx = x;
                    fy = y;
                }
                if (t.isHullBreach()) {
                    breaches++;
                    bx = x;
                    by = y;
                }
            }
            if (burning > 0) {
                working(grid, c, CrewTask.Kind.EXTINGUISH, null);
                onIt(alerts.raise("fire:" + name, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + name)
                        .room(name).tile(fx, fy).count(burning));
            }
            // The breach and the room's air share the crew sealing it
            working(grid, c, CrewTask.Kind.SEAL_BREACH, CrewTask.Kind.PATCH_LEAK);
            if (breaches > 0) {
                onIt(alerts.raise("breach:" + name, Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in " + name)
                        .room(name).tile(bx, by).count(breaches));
            }
            // An airlock pumped down for a spacewalk is empty on purpose
            if (name.startsWith("AIRLOCK")) continue;
            float p = c.pressureRatio();
            if (p < ShipAlerts.VACUUM_PRESSURE) {
                onIt(alerts.raise("air:" + name, Level.WARNING, Category.LIFE_SUPPORT).set("VACUUM", name + " is in vacuum").room(name));
            } else if (p < ShipAlerts.LOW_PRESSURE) {
                onIt(alerts.raise("air:" + name, Level.CAUTION, Category.LIFE_SUPPORT)
                        .set("PRESSURE", String.format(Locale.ROOT, "%s at %.0f%% pressure", name, p * 100f)).room(name));
            }
            if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) {
                alerts.raise("smoke:" + name, Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in " + name).room(name);
            }
        }
    }

    private void hull(ShipGridComponent grid, AlertRegistry alerts) {
        float hull = scan.scan(grid).hullIntegrity;
        if (hull < ShipAlerts.HULL_DAMAGED) {
            hullCrew();
            onIt(alerts.raise("hull", hull < HULL_WARNING ? Level.WARNING : Level.CAUTION, Category.ENGINEERING)
                    .set("HULL", String.format(Locale.ROOT, "Hull at %.0f%%", hull * 100f)));
        }
    }

    /** Every system that needs power and is not working: from the data's power snapshot. */
    private void systems(AlertRegistry alerts) {
        for (PowerStatusProvider.SystemRow r : data.powerFrame.faults) {
            boolean reactor = "REACTOR".equals(r.system);
            Level level;
            String what;
            TimerKind timer = TimerKind.NONE;
            switch (r.fault) {
                case DESTROYED: level = Level.WARNING; what = "destroyed"; break;
                case DISABLED: level = reactor ? Level.WARNING : Level.CAUTION; what = "disabled"; break;
                case SHORTED: level = reactor ? Level.WARNING : Level.CAUTION; what = "shorted"; timer = TimerKind.RECOVERY; break;
                case STUNNED: level = reactor ? Level.WARNING : Level.CAUTION; what = "stunned"; timer = TimerKind.RECOVERY; break;
                case NO_POWER: level = Level.CAUTION; what = "no power"; break;
                default: level = Level.ADVISORY; what = "off"; break;
            }
            AlertRegistry.Alert a = alerts.raise("sys:" + r.system + ":" + r.tileX + "," + r.tileY, level, Category.ENGINEERING)
                    .set(r.system, r.system + (r.room.isEmpty() ? "" : " in " + r.room) + ": " + what)
                    .room(r.room).tile(r.tileX, r.tileY).timer(r.timer, Float.isNaN(r.timer) ? TimerKind.NONE : timer);
            // Crew repair what is wrecked and reset what is shorted; a stun wears off and power comes from elsewhere
            boolean crewWork = r.fault == PowerStatusProvider.Fault.DESTROYED || r.fault == PowerStatusProvider.Fault.DISABLED
                    || r.fault == PowerStatusProvider.Fault.SHORTED;
            if (crewWork) {
                repairing(r.tileX, r.tileY);
                onIt(a);
            }
        }
    }

    private static void captain(CaptainStatus c, AlertRegistry alerts) {
        if (!c.present) return;
        if (!Float.isNaN(c.bleedOut)) {
            alerts.raise("captain:bleeding", Level.WARNING, Category.CAPTAIN).set("BLEEDING", "You are bleeding out")
                    .timer(c.bleedOut, TimerKind.HARM);
        }
        if (c.suited && c.suitAirCapacity > 0f) {
            float share = c.suitAir / c.suitAirCapacity;
            if (share < SUIT_CAUTION) {
                alerts.raise("captain:suit", share < SUIT_WARNING ? Level.WARNING : Level.CAUTION, Category.CAPTAIN)
                        .set("SUIT AIR", String.format(Locale.ROOT, "Suit air %.0f%%", share * 100f)).timer(c.suitAir, TimerKind.HARM);
            }
        } else if (!Float.isNaN(c.roomPressure) && c.roomPressure < ShipAlerts.LOW_PRESSURE) {
            alerts.raise("captain:air", Level.WARNING, Category.CAPTAIN).set("NO SUIT", "No suit, and the air is thin").room(c.room);
        }
    }

    /** Crew with a critical wound that is bleeding. */
    private void crew(Entity ship, AlertRegistry alerts) {
        ImmutableArray<Entity> aboard = crew.aboard(ship);
        for (int i = 0; i < aboard.size(); i++) {
            Entity e = aboard.get(i);
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (e.getComponent(PlayerAvatarComponent.class) != null) {
                // The captain's own wound: a medic on it, or nobody
                AlertRegistry.Alert bleeding = alerts.get("captain:bleeding");
                if (bleeding != null && c != null) {
                    treating(e, c);
                    onIt(bleeding);
                }
                continue;
            }
            if (c == null || c.wound != Wound.CRITICAL || c.stabilised) continue;
            treating(e, c);
            onIt(alerts.raise("casualty:" + c.name, Level.WARNING, Category.CREW).set("CASUALTY", c.name + " is bleeding out")
                    .timer(c.bleedOut, TimerKind.HARM));
        }
    }

    /** The tasks crew have claimed on {@code ship}'s board, for this frame's handling counts. */
    private void claimed(Entity ship) {
        claimed.clear();
        ShipTaskBoardComponent board = ship.getComponent(ShipTaskBoardComponent.class);
        if (board == null) return;
        for (CrewTask t : board.tasks) {
            if (t.status == CrewTask.Status.CLAIMED && t.claimedBy != null) claimed.add(t);
        }
    }

    /** Crew working a task of kind {@code a} or {@code b} (null for one kind) on a tile of {@code room}: one each. */
    private void working(ShipGridComponent grid, Compartment room, CrewTask.Kind a, CrewTask.Kind b) {
        startCount();
        for (CrewTask t : claimed) {
            if (t.kind != a && t.kind != b) continue;
            if (t.x < 0 || t.y < 0 || t.x >= grid.width || t.y >= grid.height) continue;
            if (room.tiles.contains(t.y * grid.width + t.x)) count(t.claimedBy, t);
        }
    }

    /** Crew repairing or resetting the system whose mount starts at {@code x, y}. */
    private void repairing(int x, int y) {
        startCount();
        if (x < 0 || y < 0) return;
        for (CrewTask t : claimed) {
            if (t.kind != CrewTask.Kind.REPAIR && t.kind != CrewTask.Kind.RESET_SHORT) continue;
            if (Math.abs(t.x - x) <= REPAIR_REACH && Math.abs(t.y - y) <= REPAIR_REACH) count(t.claimedBy, t);
        }
    }

    /** Crew mending the hull: framing wrecked tiles back up from inside or patching them from outside. */
    private void hullCrew() {
        startCount();
        for (CrewTask t : claimed) {
            boolean hullWork = t.kind == CrewTask.Kind.PATCH_HULL || t.kind == CrewTask.Kind.REPAIR_RCS
                    || t instanceof RepairTask && ((RepairTask) t).subsystem == null;
            if (hullWork) count(t.claimedBy, t);
        }
    }

    /** Crew tending {@code patient}: the medic treating them, and whoever is stabilising or carrying them. */
    private void treating(Entity patient, CrewMemberComponent c) {
        startCount();
        if (c.treatedBy != null) count(c.treatedBy, null);
        for (CrewTask t : claimed) {
            Entity of = t instanceof TreatTask ? ((TreatTask) t).patient
                    : t instanceof StabiliseTask ? ((StabiliseTask) t).patient
                    : t instanceof RescueTask ? ((RescueTask) t).casualty : null;
            if (of == patient) count(t.claimedBy, t);
        }
    }

    private void startCount() {
        counted.clear();
        countedTasks.clear();
    }

    private void count(Entity crewMember, CrewTask t) {
        if (counted.contains(crewMember, true)) return;
        counted.add(crewMember);
        countedTasks.add(t);
    }

    /**
     * Says on {@code a} who of the crew just counted are on it: how many, and the one to name, one at
     * the work before one on the way to it, then the soonest done.
     */
    private AlertRegistry.Alert onIt(AlertRegistry.Alert a) {
        a.handling(counted.size);
        CrewMemberComponent lead = null;
        boolean leadAt = false;
        float leadLeft = Float.NaN;
        for (int i = 0; i < counted.size; i++) {
            CrewMemberComponent c = counted.get(i).getComponent(CrewMemberComponent.class);
            if (c == null) continue;
            CrewTask t = countedTasks.get(i);
            boolean at = atWork(c, t);
            float left = workLeft(c, t, at);
            boolean better;
            if (lead == null) better = true;
            else if (at != leadAt) better = at;
            else better = !Float.isNaN(left) && (Float.isNaN(leadLeft) || left < leadLeft);
            if (!better) continue;
            lead = c;
            leadAt = at;
            leadLeft = left;
        }
        if (lead != null) a.crew(lead.name, leadAt, leadLeft);
        return a;
    }

    /**
     * Whether {@code c} is at {@code t} now, working it as their activity says, rather than on the way
     * ("going to ..."); a medic treating with no task is at the patient.
     */
    static boolean atWork(CrewMemberComponent c, CrewTask t) {
        if (t == null) return true;
        return c.task == t && CrewTasks.describe(t).equals(c.activity);
    }

    /**
     * Seconds of work {@code c} has left on {@code t} at their pace (a light wound and a suit slow it):
     * what is left once at it, where the game shows the share done, and the whole job while on the
     * way. NaN for work with no set time (a fire, a patient) or when the game can't say.
     */
    static float workLeft(CrewMemberComponent c, CrewTask t, boolean at) {
        if (t == null || c.def == null) return Float.NaN;
        float pace = Math.max(0.05f, c.workPace());
        float time;
        switch (t.kind) {
            case RESET_SHORT: time = c.def.resetTime; break;
            case SEAL_BREACH: time = c.def.sealTime; break;
            case PATCH_LEAK: time = c.def.patchTime; break;
            case ISOLATE_TANK: time = c.def.isolateTime; break;
            case REPAIR: {
                // A repair runs at a rate on the system's health, which is its progress either way
                Entity of = t instanceof RepairTask ? ((RepairTask) t).subsystem : null;
                SubsystemComponent s = of == null ? null : of.getComponent(SubsystemComponent.class);
                if (s == null || c.def.repairRate <= 0f) return Float.NaN;
                return Math.max(0f, 1f - s.health) / (c.def.repairRate * pace);
            }
            default: return Float.NaN;
        }
        if (!at) return time / pace;
        return c.progress < 0f ? Float.NaN : (1f - c.progress) * time / pace;
    }

    private static void shield(ShieldStatus s, UiContext ctx, AlertRegistry alerts) {
        if (!s.present) return;
        if (s.emittersTripped > 0) {
            alerts.raise("shield:tripped", Level.CAUTION, Category.TACTICAL).set("SHIELD", "Shield emitter tripped").count(s.emittersTripped);
        }
        if (!s.raised && !s.loweredForDock && ctx.combat) {
            alerts.raise("shield:down", Level.CAUTION, Category.TACTICAL).set("SHIELD", "Shield down with hostiles about");
        }
    }

    private static void oxygen(ShipStatus s, AlertRegistry alerts) {
        if (!Double.isNaN(s.emergencyOxygenKg)) {
            alerts.raise("oxygen", Level.WARNING, Category.LIFE_SUPPORT)
                    .set("O2", String.format(Locale.ROOT, "Oxygen tanks dry: emergency oxygen %.0f%%", s.emergencyOxygen * 100f));
        } else if (!Float.isNaN(s.oxygen) && s.oxygen < OXYGEN_CAUTION) {
            alerts.raise("oxygen", Level.CAUTION, Category.LIFE_SUPPORT)
                    .set("O2", String.format(Locale.ROOT, "Oxygen %.0f%%", s.oxygen * 100f));
        }
    }

    /** The notice log, newest first, into the feed. */
    private void messages() {
        MessageFeed feed = data.messages;
        feed.clear();
        List<NoticeLog.Notice> showing = ctx.notices.showing();
        int kept = 0;
        for (NoticeLog.Notice n : ctx.notices.log()) {
            if (kept++ >= MessageFeed.KEEP) break;
            MessageFeed.Message m = feed.add(n.text, n.kind == NoticeLog.Kind.WARNING ? Level.WARNING : Level.ADVISORY, n.time);
            m.important = n.kind == NoticeLog.Kind.IMPORTANT;
            m.count = n.count;
            m.secondsLeft = showing.contains(n) ? n.secondsLeft() : 0f;
            m.alert = n.alert;
        }
    }
}
