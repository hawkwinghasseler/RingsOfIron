package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.ringsofiron.aicrew.CrewRole;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause;
import com.ringsofiron.aicrew.CrewService;

/**
 * The interior tools: the damage sources and crew tools a click on a ship's deck can use, each
 * through the game's own systems ({@link WorldDebug}), with the setting each one is used at. The
 * interior demo picks them with the number keys; the game's interior and crew scenarios from the
 * debug menu. Headless, so a test can drive them as a click would.
 *
 * <p>Weapon fire and rams come in from straight outboard of the tile clicked, or from where a drag
 * started when it started more than {@link #DRAG_TILES} away.
 */
public final class DeckTools {
    /** How far the cursor must drag, tiles, to aim a shot from where it started. */
    public static final float DRAG_TILES = 1.5f;
    static final float[] WOUND_AMOUNTS = {10f, 30f, 50f, 70f, 80f, 90f, 100f, 150f, 200f};
    static final DamageCause[] CAUSES = {DamageCause.SPALL, DamageCause.PENETRATOR, DamageCause.FRAGMENT,
            DamageCause.BLAST, DamageCause.BURN, DamageCause.FIRE, DamageCause.ASPHYXIATION, DamageCause.SMALL_ARMS};
    /** Under 1 km/s a ram is a Box2D rock that crashes into the hull, as ships do (nothing under 25 m/s). */
    static final float[] RAM_SPEEDS = {25f, 50f, 100f, 200f, 500f, 1_500f, 2_000f, 4_000f, 8_000f};
    /** The last is the starter corvette's mass, so a ram hits as one would. */
    static final float[] RAM_MASSES = {200f, 2_000f, 20_000f, 195_100f};
    static final CrewRole[] ROLES = {CrewRole.MEDIC, CrewRole.ENGINEER, CrewRole.CREWMAN, CrewRole.MARINE};
    static final float[] DAMAGE_AMOUNTS = {0.1f, 0.25f, 0.5f, 1f};
    /** The ai-crew faction boarders spawn in, when the ship's own is not it: anyone but the ship's own is hostile to it. */
    static final int BOARDERS = 1;

    public enum Tool {
        WOUND("Wound crew"), WEAPON("Fire weapon"), BLAST("Blast inside"), RAM("Ram"), FIRE("Start fire"),
        BREACH("Breach"), SHORT("Short out"), DAMAGE("Damage tile/system"), SPAWN("Spawn crew"), REMOVE("Remove crew");

        public final String title;

        Tool(String title) {
            this.title = title;
        }
    }

    private final CrewService crewService;
    private final WorldDebug debug;
    public Tool tool = Tool.WOUND;
    private int woundAmount = 4, cause, weapon, warhead, ramSpeed = 1, ramMass = 1, role, damageAmount = 1;
    private boolean boarder;
    /** The crew member the last use hurt or spawned, for a screen to follow; null if none. */
    public Entity touched;

    public DeckTools(CrewService crewService, WorldDebug debug) {
        this.crewService = crewService;
        this.debug = debug;
    }

    /** Steps the tool's setting along by {@code step} (Q / E in the demo): amount, weapon, warhead, speed, role. */
    public void step(int step) {
        switch (tool) {
            case WOUND: woundAmount = Math.floorMod(woundAmount + step, WOUND_AMOUNTS.length); break;
            case WEAPON: weapon = Math.floorMod(weapon + step, debug.weapons().size()); break;
            case BLAST: warhead = Math.floorMod(warhead + step, debug.warheads().size()); break;
            case RAM: ramSpeed = Math.floorMod(ramSpeed + step, RAM_SPEEDS.length); break;
            case SPAWN: role = Math.floorMod(role + step, ROLES.length); break;
            case DAMAGE: damageAmount = Math.floorMod(damageAmount + step, DAMAGE_AMOUNTS.length); break;
            default: break;
        }
    }

    /** Changes the tool's second setting, where it has one (C in the demo): the cause, the mass, crew or boarder. */
    public void alternate() {
        if (tool == Tool.WOUND) cause = (cause + 1) % CAUSES.length;
        if (tool == Tool.RAM) ramMass = (ramMass + 1) % RAM_MASSES.length;
        if (tool == Tool.SPAWN) boarder = !boarder;
    }

    /** True when the tool has a setting {@link #step} changes, and {@link #alternate} a second one. */
    public boolean hasSetting() {
        return !setting().isEmpty();
    }

    public boolean hasAlternate() {
        return tool == Tool.WOUND || tool == Tool.RAM || tool == Tool.SPAWN;
    }

    /** The tool's setting, as ": 80 spall", or empty. */
    public String setting() {
        switch (tool) {
            case WOUND: return String.format(": %.0f %s", WOUND_AMOUNTS[woundAmount], cause(CAUSES[cause]));
            case WEAPON: return ": " + debug.weapons().get(weapon);
            case BLAST: return ": " + debug.warheads().get(warhead);
            case RAM: return RAM_SPEEDS[ramSpeed] < 1000f
                    ? String.format(": %.0f kg at %.0f m/s", RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed])
                    : String.format(": %.0f kg at %.1f km/s", RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed] / 1000f);
            case SPAWN: return ": " + ROLES[role].name().toLowerCase() + (boarder ? ", boarder" : "");
            case DAMAGE: return String.format(": %.0f%%", DAMAGE_AMOUNTS[damageAmount] * 100f);
            default: return "";
        }
    }

    /** How to aim the tool, as the demo's keys name it. */
    public String keys() {
        switch (tool) {
            case WOUND: return "   (Q/E amount, C cause; click someone)";
            case WEAPON: return "   (Q/E weapon; drag in from outside to aim)";
            case BLAST: return "   (Q/E warhead)";
            case RAM: return "   (Q/E speed, C mass; drag in from outside to aim)";
            case SPAWN: return "   (Q/E role, C crew or boarder)";
            case DAMAGE: return "   (Q/E amount)";
            default: return "";
        }
    }

    /**
     * Uses the tool on {@code ship} at {@code at}, in the ship's own frame (meters); {@code from} is
     * where a drag started, or {@code at} for a plain click. Returns what happened, as
     * "Fire weapon at 4,7: Hit".
     */
    public String apply(Entity ship, Vector2 at, Vector2 from) {
        touched = null;
        ShipGridComponent g = ship.getComponent(ShipGridComponent.class);
        Body body = WorldDebug.body(ship);
        float tx = (at.x - g.gridOriginLocal.x) / g.tileSize, ty = (at.y - g.gridOriginLocal.y) / g.tileSize;
        int x = MathUtils.floor(tx), y = MathUtils.floor(ty);
        boolean dragged = from.dst(at) > DRAG_TILES * g.tileSize;
        Vector2 fromWorld = WorldDebug.toWorld(body, from.x, from.y, new Vector2());
        Entity crew = debug.crewAt(ship, tx, ty, 0.8f);
        if (!g.inBounds(x, y) && tool != Tool.WOUND && tool != Tool.REMOVE) return "Off the deck";
        String result;
        switch (tool) {
            case WOUND:
                if (crew == null) {
                    result = "Click a crew member";
                    break;
                }
                debug.hurt(crew, WOUND_AMOUNTS[woundAmount], CAUSES[cause]);
                touched = crew;
                result = String.format("%s: %.0f %s", crewService.crew(crew).name, WOUND_AMOUNTS[woundAmount],
                        cause(CAUSES[cause]));
                break;
            case WEAPON: {
                String id = debug.weapons().get(weapon);
                result = dragged ? debug.fire(ship, id, x, y, fromWorld.x, fromWorld.y) : debug.fire(ship, id, x, y);
                break;
            }
            case BLAST:
                result = debug.blast(ship, debug.warheads().get(warhead), x, y);
                break;
            case RAM:
                if (dragged) {
                    result = debug.ram(ship, x, y, fromWorld.x, fromWorld.y, RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed]);
                } else {
                    // Straight in from outboard: out from the ship's middle through the tile
                    Vector2 c = WorldDebug.toWorld(body, g.gridOriginLocal.x + g.width * g.tileSize / 2f,
                            g.gridOriginLocal.y + g.height * g.tileSize / 2f, new Vector2());
                    Vector2 a = debug.tileWorld(ship, x, y, new Vector2());
                    Vector2 out = a.cpy().sub(c);
                    if (out.isZero(1e-3f)) out.set(0f, 1f);
                    out.nor().add(a);
                    result = debug.ram(ship, x, y, out.x, out.y, RAM_MASSES[ramMass], RAM_SPEEDS[ramSpeed]);
                }
                break;
            case FIRE:
                result = debug.ignite(ship, x, y);
                break;
            case BREACH:
                result = debug.breach(ship, x, y);
                break;
            case SHORT:
                result = debug.shortOut(ship, x, y);
                break;
            case DAMAGE:
                result = debug.damage(ship, x, y, DAMAGE_AMOUNTS[damageAmount]);
                break;
            case SPAWN: {
                int own = crewService.board(ship).faction;
                Entity e = boarder ? debug.spawnCrew(ship, ROLES[role], own == BOARDERS ? own + 1 : BOARDERS, x, y)
                        : debug.spawnCrew(ship, ROLES[role], x, y);
                result = e == null ? "Spawn on open deck" : crewService.crew(e).name + (boarder ? " boards" : " comes aboard");
                touched = e;
                break;
            }
            case REMOVE:
                if (crew == null) {
                    result = "Click a crew member";
                    break;
                }
                result = crewService.crew(crew).name + " taken off";
                debug.removeCrew(crew);
                break;
            default:
                result = "";
        }
        return tool.title + " at " + x + "," + y + ": " + result;
    }

    static String cause(DamageCause c) {
        return c.name().toLowerCase().replace('_', ' ');
    }
}
