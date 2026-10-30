package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.aicrew.Wound;
import com.ringsofiron.career.Contract;
import com.ringsofiron.career.FactionStanding;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.macrosim.economy.CommodityType;
import com.ringsofiron.macrosim.economy.TraderComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.macrosim.MacroSimulation;
import java.util.function.Supplier;

import static com.hawkwing.RingsOfIron.game.ContractBoard.*;

/**
 * {@link ContractBoard}'s rescues and recoveries: survivors picked up from a pod and carried home,
 * and a wreck's cargo carried across and handed over at the station.
 */
final class RescueContracts {
    private final ContractBoard board;
    private final GameContext ctx;
    private final CrewService crew;
    private final MacroSimulation macroSim;
    private final DockingSystem docking;
    private final FactionStanding standing;
    private final Supplier<CrewHiring> crewHiring;
    private final Supplier<Derelicts> derelicts;

    RescueContracts(ContractBoard board) {
        this.board = board;
        this.ctx = board.ctx;
        this.crew = board.crew;
        this.macroSim = board.macroSim;
        this.docking = board.docking;
        this.standing = board.standing;
        this.crewHiring = board.crewHiring;
        this.derelicts = board.derelicts;
    }

    /**
     * Picks the pod's survivors up with a slow close pass, as many as there are free berths for, and
     * brings them aboard as people ({@link SurvivorComponent}); docked at the station, those still alive
     * go ashore and are paid for. Any left in the pod for want of berths are left to the station's tug.
     */
    void checkRescue(Contract c, Entity player) {
        if (c.progress == 0) {
            Derelicts.Derelict pod = derelicts.get().get(c.targetUuid);
            if (pod == null) {
                board.end(c, Contract.Status.FAILED, c.targetName + "'s air ran out");
                return;
            }
            if (!derelicts.get().closeTo(player, pod)) return;
            int room = CrewHiring.berths(player) - crewHiring.get().aboard(player);
            int n = (int) Math.min(Math.round(c.quantity), Math.max(0, room));
            if (n <= 0) {
                // Said once a pass, not every frame
                if (c.arrivedAt < 0) board.notices.add("No berth free for " + c.targetName + "'s survivors");
                c.arrivedAt = board.now();
                return;
            }
            int[] hurt = new int[2];
            int aboard = boardSurvivors(c, player, n, podExposure(pod), hurt);
            if (aboard <= 0) return;
            derelicts.get().remove(pod.id);
            c.progress = 1;
            c.arrivedAt = -1;
            c.carried = aboard;
            board.notices.add(String.format(Locale.ROOT, "Picked up %d survivors from %s; now to %s%s%s", aboard, c.targetName,
                    c.destinationName, aboard < c.quantity ? String.format(Locale.ROOT, " (no berths for %.0f more)",
                            c.quantity - aboard) : "",
                    hurt[0] == 0 ? "" : String.format(Locale.ROOT, ". %d hurt%s: your crew will tend them", hurt[0],
                            hurt[1] == 0 ? "" : String.format(Locale.ROOT, ", %d critically", hurt[1]))));
            return;
        }
        if (!board.dockedAt(player, c.destinationUuid)) return;
        if (c.carried < 0) {
            // Picked up before survivors came aboard as people: paid by the pod's count, as then
            board.end(c, Contract.Status.DONE, "survivors brought home");
            return;
        }
        int alive = landSurvivors(player, c.id);
        if (alive == 0) {
            board.end(c, Contract.Status.FAILED, "none of the survivors lived");
            return;
        }
        c.reward = RESCUE_PAY + RESCUE_PER_SURVIVOR * alive;
        board.end(c, Contract.Status.DONE, alive + (alive == 1 ? " survivor" : " survivors") + " brought home");
    }

    /** How far a pod's air has run down, 0 (just cast off) to 1 (out of air). */
    double podExposure(Derelicts.Derelict pod) {
        double left = pod.expires - board.now();
        return Math.max(0, Math.min(1, 1 - left / Derelicts.POD_AIR));
    }

    /**
     * Brings {@code n} of rescue {@code c}'s survivors aboard {@code ship}, on free deck tiles; returns
     * how many came. Round 6: they come aboard as the pod left them ({@link #hurtInPod}), the worse the
     * longer it drifted ({@code exposure}); {@code hurt} gets how many are wounded and how many of
     * them critically.
     */
    int boardSurvivors(Contract c, Entity ship, int n, double exposure, int[] hurt) {
        String from = c.targetName == null ? "the pod" : c.targetName;
        String crewOf = from.endsWith("'s pod") ? from.substring(0, from.length() - 6) : from;
        int faction = standing.factionOf(ctx.engine.getEntity(c.stationUuid));
        int aboard = 0;
        for (int i = 0; i < n; i++) {
            int[] tile = crewHiring.get().freeTile(ship);
            if (tile == null) break;
            Entity e = com.ringsofiron.aicrew.CrewFactory.spawn(crew, ship, crewOf + " hand " + (i + 1),
                    com.ringsofiron.aicrew.CrewRole.CREWMAN, faction, tile[0], tile[1]);
            // Carried, not crew: no work, no fights, a berth taken
            crew.crew(e).held = true;
            SurvivorComponent s = new SurvivorComponent();
            s.contract = c.id;
            s.from = from;
            e.add(s);
            Wound w = hurtInPod(e, exposure, new Random(NewGame.mix(c.id, i)));
            if (w != Wound.NONE) hurt[0]++;
            if (w == Wound.CRITICAL) hurt[1]++;
            aboard++;
        }
        return aboard;
    }

    /** Share of a pod's survivors wounded when picked up at once, and the further share by the time its air runs out. */
    static final double POD_HURT = 0.25, POD_HURT_LATE = 0.5;

    /** Share of the wounded critically hurt by the time the air runs out (none at once), and seriously at any time. */
    static final double POD_CRITICAL_LATE = 0.3, POD_SERIOUS = 0.4;

    /** Share of their health the stale air of a pod about to run out has taken. */
    static final float POD_STALE_AIR = 0.4f;

    /**
     * A survivor as the pod leaves them: short of breath by how long it drifted, and wounded one time in
     * {@link #POD_HURT} (more the longer), seriously or critically more often late. Their wound is treated
     * aboard like a crew member's (they are cared for as held), so one left untreated can die before the
     * station. Returns the wound.
     */
    Wound hurtInPod(Entity e, double exposure, Random dice) {
        com.ringsofiron.aicrew.CrewMemberComponent m = crew.crew(e);
        if (exposure > 0) crew.applyDamage(e, (float) (POD_STALE_AIR * exposure) * m.def.maxHealth,
                com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.ASPHYXIATION);
        if (dice.nextDouble() >= POD_HURT + POD_HURT_LATE * exposure) return Wound.NONE;
        double v = dice.nextDouble();
        Wound w = v < POD_CRITICAL_LATE * exposure ? Wound.CRITICAL : v < POD_CRITICAL_LATE * exposure + POD_SERIOUS ? Wound.SERIOUS : Wound.LIGHT;
        crew.applyDamage(e, m.def.injuryFor(w), com.ringsofiron.interiorgrid.crew.CrewDamageSink.DamageCause.FRAGMENT);
        return m.wound;
    }

    /**
     * Sends ashore the survivors aboard {@code ship} picked up for contract {@code contract}, or with
     * {@code contract} negative, every survivor whose rescue is no longer held. Returns how many
     * went ashore alive; the dead are taken off too.
     */
    int landSurvivors(Entity ship, long contract) {
        int alive = 0;
        var all = crew.crewEntities();
        List<Entity> ashore = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            SurvivorComponent s = e.getComponent(SurvivorComponent.class);
            if (s == null || crew.position(e).ship != ship) continue;
            if (contract >= 0 ? s.contract != contract : held(s.contract)) continue;
            ashore.add(e);
        }
        for (Entity e : ashore) {
            if (crew.crew(e).isAlive()) alive++;
            crew.releaseTask(e);
            ctx.engine.getEcsEngine().removeEntity(e);
        }
        return alive;
    }

    boolean held(long contract) {
        for (Contract c : board.active) if (c.id == contract) return true;
        return false;
    }

    /** Survivors aboard {@code ship} picked up for a rescue, alive. */
    int survivorsAboard(Entity ship) {
        int n = 0;
        var all = crew.crewEntities();
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            if (e.getComponent(SurvivorComponent.class) != null && crew.position(e).ship == ship
                    && crew.crew(e).isAlive()) n++;
        }
        return n;
    }

    /**
     * Holds alongside the wreck for {@link Derelicts#SALVAGE_SECONDS} while the crew carry its cargo into
     * the hold, then hands it over on docking at the station; a hold too full for all of it takes what fits.
     */
    void checkRecovery(Contract c, Entity player) {
        TraderComponent trader = player.getComponent(TraderComponent.class);
        CommodityType type = macroSim.economy().catalog().commodity(c.commodity);
        if (c.progress == 0) {
            Derelicts.Derelict wreck = derelicts.get().get(c.targetUuid);
            if (wreck == null) {
                board.end(c, Contract.Status.FAILED, c.targetName + " drifted out of reach");
                return;
            }
            if (!derelicts.get().closeTo(player, wreck)) {
                c.arrivedAt = -1;
                return;
            }
            if (c.arrivedAt < 0) {
                c.arrivedAt = board.now();
                board.notices.add("Holding alongside " + c.targetName + ": the crew are carrying the " + c.commodityName + " across");
                return;
            }
            if (board.now() - c.arrivedAt < Derelicts.SALVAGE_SECONDS || trader == null || type == null) return;
            double stowed = trader.stow(type, c.quantity);
            if (stowed < 0.5) {
                c.arrivedAt = board.now();
                board.notices.add("No room in the hold for the " + c.commodityName);
                return;
            }
            if (stowed < c.quantity - 0.5) {
                // What did not fit is left; the pay is for what comes home
                c.reward = Math.round(c.reward * stowed / c.quantity);
                c.quantity = Math.floor(stowed);
            }
            derelicts.get().remove(wreck.id);
            c.progress = 1;
            board.notices.add(String.format(Locale.ROOT, "Recovered %.0f %s from %s; now to %s", c.quantity, c.commodityName,
                    c.targetName, c.destinationName));
            return;
        }
        if (!board.dockedAt(player, c.destinationUuid) || trader == null || type == null) return;
        double units = trader.handOver(type, c.quantity - c.delivered);
        if (units <= 0) return;
        c.delivered += units;
        if (c.delivered < c.quantity - 0.5) c.reward = Math.round(c.reward * c.delivered / c.quantity);
        board.end(c, Contract.Status.DONE, c.commodityName + " recovered");
    }
}
