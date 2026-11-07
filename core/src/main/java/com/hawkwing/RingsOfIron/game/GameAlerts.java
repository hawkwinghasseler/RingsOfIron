package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.PlayerAvatarComponent;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
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
 */
public class GameAlerts {
    /** Suit air below this share of full is a caution, below {@link #SUIT_WARNING} a warning. */
    static final float SUIT_CAUTION = 0.25f, SUIT_WARNING = 0.1f;
    /** Hull integrity below this is a warning (below {@link ShipAlerts#HULL_DAMAGED} a caution). */
    static final float HULL_WARNING = 0.4f;
    /** Oxygen tanks below this share are a caution. */
    static final float OXYGEN_CAUTION = 0.2f;

    private final GameContext ctx;
    private final FlightWarnings warnings;
    private final Spacewalks spacewalks;
    private final CrewService crew;
    private final HudData data;
    private static final Side[] SIDES = Side.values();
    private final RoomNames rooms = new RoomNames();
    private final IntArray breached = new IntArray();
    private Entity roomsFor;
    private final ShipAlerts scan = new ShipAlerts();
    private final PowerStatusProvider.PowerFrame power = new PowerStatusProvider.PowerFrame();

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
        missiles(data.threatFrame, alerts);
        if (warnings.propellantLow()) {
            ShipStatus s = data.shipStatus;
            double home = warnings.homePropellantKg();
            alerts.raise("propellant", Double.isFinite(home) && s.propellantKg < home ? Level.WARNING : Level.CAUTION, Category.FLIGHT)
                    .set("FUEL", String.format(Locale.ROOT, "Propellant low: %,.0f kg left", s.propellantKg)
                            + (Double.isFinite(home) ? String.format(Locale.ROOT, ", %,.0f kg takes you home", home) : ""));
        }

        DisabledComponent.Reason disabled = ShipDisablement.reason(ship);
        if (disabled != null) alerts.raise("disabled", Level.WARNING, Category.SHIP).set("DISABLED", "Ship disabled: " + disabled.label);
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
        alerts.raise("missile", Level.WARNING, Category.TACTICAL)
                .set("MISSILE", n == 1 ? "Missile inbound" : n + " missiles inbound")
                .count(n)
                .timer(soonest, Float.isNaN(soonest) ? TimerKind.NONE : TimerKind.HARM);
    }

    /** Each room's fire, breach, air and smoke. */
    private void rooms(ShipGridComponent grid, AlertRegistry alerts) {
        for (Compartment c : rooms.rooms(grid)) {
            String name = rooms.of(grid, c);
            int burning = 0, fx = -1, fy = -1;
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
            }
            if (burning > 0) {
                alerts.raise("fire:" + name, Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in " + name)
                        .room(name).tile(fx, fy).count(burning);
            }
            int breaches = breaches(grid, c, breached);
            if (breaches > 0) {
                int at = breached.first();
                alerts.raise("breach:" + name, Level.WARNING, Category.LIFE_SUPPORT).set("BREACH", "Hull breach in " + name)
                        .room(name).tile(at % grid.width, at / grid.width).count(breaches);
            }
            // An airlock pumped down for a spacewalk is empty on purpose
            if (name.startsWith("AIRLOCK")) continue;
            float p = c.pressureRatio();
            if (p < ShipAlerts.VACUUM_PRESSURE) {
                alerts.raise("air:" + name, Level.WARNING, Category.LIFE_SUPPORT).set("VACUUM", name + " is in vacuum").room(name);
            } else if (p < ShipAlerts.LOW_PRESSURE) {
                alerts.raise("air:" + name, Level.CAUTION, Category.LIFE_SUPPORT)
                        .set("PRESSURE", String.format(Locale.ROOT, "%s at %.0f%% pressure", name, p * 100f)).room(name);
            }
            if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) {
                alerts.raise("smoke:" + name, Level.CAUTION, Category.LIFE_SUPPORT).set("SMOKE", "Smoke in " + name).room(name);
            }
        }
    }

    /**
     * The hull breaches that open onto the room: the breached hull tiles 4-adjacent to its tiles,
     * with no closed edge wall between, each counted once. A breach is space-exposed, so it is never
     * one of the room's own tiles. Fills {@code into} with their packed indices, lowest first.
     */
    static int breaches(ShipGridComponent grid, Compartment c, IntArray into) {
        into.clear();
        for (int i = 0; i < c.tiles.size; i++) {
            int packed = c.tiles.get(i);
            int x = packed % grid.width, y = packed / grid.width;
            for (Side s : SIDES) {
                int nx = x + s.dx, ny = y + s.dy;
                Tile nb = grid.getTile(nx, ny);
                if (nb == null || !nb.isHullBreach() || grid.isEdgeBlocked(x, y, nx, ny)) continue;
                int n = ny * grid.width + nx;
                if (!into.contains(n)) into.add(n);
            }
        }
        into.sort();
        return into.size;
    }

    private void hull(ShipGridComponent grid, AlertRegistry alerts) {
        float hull = scan.scan(grid).hullIntegrity;
        if (hull < HULL_WARNING) {
            alerts.raise("hull", Level.WARNING, Category.ENGINEERING).set("HULL", String.format(Locale.ROOT, "Hull at %.0f%%", hull * 100f));
        } else if (hull < ShipAlerts.HULL_DAMAGED) {
            alerts.raise("hull", Level.CAUTION, Category.ENGINEERING).set("HULL", String.format(Locale.ROOT, "Hull at %.0f%%", hull * 100f));
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
            alerts.raise("sys:" + r.system + ":" + r.tileX + "," + r.tileY, level, Category.ENGINEERING)
                    .set(r.system, r.system + (r.room.isEmpty() ? "" : " in " + r.room) + ": " + what)
                    .room(r.room).tile(r.tileX, r.tileY).timer(r.timer, Float.isNaN(r.timer) ? TimerKind.NONE : timer);
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
            if (e.getComponent(PlayerAvatarComponent.class) != null) continue;
            CrewMemberComponent c = e.getComponent(CrewMemberComponent.class);
            if (c == null || c.wound != Wound.CRITICAL || c.stabilised) continue;
            alerts.raise("casualty:" + c.name, Level.WARNING, Category.CREW).set("CASUALTY", c.name + " is bleeding out")
                    .timer(c.bleedOut, TimerKind.HARM);
        }
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
        for (NoticeLog.Notice n : ctx.notices.history()) {
            if (kept++ >= MessageFeed.KEEP) break;
            MessageFeed.Message m = feed.add(n.text, n.kind == NoticeLog.Kind.WARNING ? Level.WARNING : Level.ADVISORY, n.time);
            m.important = n.kind == NoticeLog.Kind.IMPORTANT;
            m.count = n.count;
            m.secondsLeft = showing.contains(n) ? n.secondsLeft() : 0f;
        }
    }
}
