package com.ringsofiron.macrosim;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.macrosim.bubble.BubbleGateway;
import com.ringsofiron.macrosim.bubble.BubbleTransitionListener;
import com.ringsofiron.macrosim.bubble.BubbleTransitionSystem;
import com.ringsofiron.macrosim.bubble.EncounterForecast;
import com.ringsofiron.macrosim.bubble.EncounterPredictionTask;
import com.ringsofiron.macrosim.economy.CommodityCatalog;
import com.ringsofiron.macrosim.economy.EconomyNode;
import com.ringsofiron.macrosim.economy.EconomyNodeComponent;
import com.ringsofiron.macrosim.economy.EconomySimulation;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.macrosim.fleet.FreighterController;
import com.ringsofiron.macrosim.rails.RailsModel;
import com.ringsofiron.macrosim.rails.RailsModel.Burn;
import com.ringsofiron.macrosim.rails.StateVector;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Entry point of the macro-sim module: the living universe outside the player bubble.
 * Owns the economy, the NPC freighter fleet, background encounter prediction and the bubble
 * transitions. Driven by sim time through {@link #advanceTo(double)}, on the main thread; a rails
 * warp jump is one call with a large target time.
 */
public class MacroSimulation {
    /** Sim seconds between background encounter predictions. */
    public static final double PREDICTION_INTERVAL = 0.5;
    /** How far ahead each prediction looks, sim seconds. Raised by time warp to see the next drop-out. */
    public static final double DEFAULT_PREDICTION_HORIZON = 3600;

    private final GameEngine engine;
    private final RailsModel rails;
    private final BubbleGateway gateway;
    private final ExecutorService predictionExecutor;
    private final MacroEventQueue events = new MacroEventQueue();
    private final EconomySimulation economy;
    private final FreighterController freighters;
    private final BubbleTransitionSystem bubble;
    private final Map<String, EconomyNode> stations = new LinkedHashMap<>();
    private final Set<String> massiveBodies = new HashSet<>();

    private double now;
    private double nextEconomyPass;
    private boolean bubbleSuspended;
    private double predictionHorizon = DEFAULT_PREDICTION_HORIZON;
    private double nextPrediction;
    private Future<EncounterForecast> pendingForecast;

    /**
     * @param gateway            the bubble, or null to run without one (pure background universe)
     * @param predictionExecutor background thread for encounter prediction, or null to predict inline
     */
    public MacroSimulation(GameEngine engine, RailsModel rails, BubbleGateway gateway, CommodityCatalog catalog,
                           ExecutorService predictionExecutor, double startTime) {
        this.engine = engine;
        this.rails = rails;
        this.gateway = gateway;
        this.predictionExecutor = predictionExecutor;
        this.economy = new EconomySimulation(catalog);
        this.freighters = new FreighterController(rails, economy, events, stations::get);
        this.now = startTime;
        this.nextPrediction = startTime;
        this.bubble = gateway == null ? null : new BubbleTransitionSystem(rails, gateway);
        if (bubble != null) {
            bubble.addListener(new BubbleTransitionListener() {
                @Override
                public void onEnterBubble(String uuid, List<Burn> cancelledBurns, double t) {
                    freighters.onEnterBubble(uuid);
                }

                @Override
                public void onExitBubble(String uuid, double t) {
                    freighters.onExitBubble(uuid, t);
                }
            });
        }
        scheduleEconomyPass(startTime + EconomySimulation.PASS_INTERVAL);
    }

    private void scheduleEconomyPass(double at) {
        nextEconomyPass = at;
        events.schedule(at, () -> {
            economy.pass(stations.values(), EconomySimulation.PASS_INTERVAL);
            freighters.assignIdle(at);
            scheduleEconomyPass(at + EconomySimulation.PASS_INTERVAL);
        });
    }

    /** Sim second of the next economy pass. */
    public double nextEconomyPass() {
        return nextEconomyPass;
    }

    /**
     * Clears all macro state and sets the clock, before {@link MacroSaveParticipant} restores a save.
     * The economy pass resumes at {@code nextEconomyPass}.
     */
    void beginRestore(double now, double nextEconomyPass) {
        events.clear();
        stations.clear();
        freighters.clear();
        if (pendingForecast != null) pendingForecast.cancel(true);
        pendingForecast = null;
        this.now = now;
        this.nextPrediction = now;
        scheduleEconomyPass(nextEconomyPass);
    }

    /** Re-attaches a saved economy node to its station entity. */
    void restoreStation(EconomyNode node) {
        Entity e = engine.getEntity(node.stationUuid);
        if (e == null) return;
        e.add(new EconomyNodeComponent(node));
        stations.put(node.stationUuid, node);
        if (bubble != null) bubble.markRailsOnly(node.stationUuid);
    }

    /** Re-attaches a saved freighter to its entity. */
    void restoreFreighter(String uuid, FreighterComponent f, com.ringsofiron.macrosim.economy.TradeOrder order) {
        Entity e = engine.getEntity(uuid);
        if (e == null) return;
        e.add(f);
        freighters.restore(uuid, f, order);
    }

    /** Marks a UUID as a massive body (planet, moon): never handed to the bubble or predicted. */
    public void registerMassiveBody(String uuid) {
        massiveBodies.add(uuid);
    }

    /** Creates a station entity on rails with a fresh economy node. Returns its UUID. */
    public String addStation(String name, String stationTypeId, String parentUuid, StateVector state, double stockFactor) {
        Entity e = engine.createEntity();
        UuidComponent id = new UuidComponent();
        e.add(id);
        EconomyNode node = new EconomyNode(id.id, stationTypeId, name);
        economy.initialize(node, stockFactor);
        e.add(new EconomyNodeComponent(node));
        engine.addEntity(e);
        stations.put(id.id, node);
        rails.attach(id.id, parentUuid, state, now);
        if (bubble != null) bubble.markRailsOnly(id.id);
        return id.id;
    }

    /** Creates a freighter entity docked at a station. Returns its UUID. */
    public String addFreighter(String name, double capacity, double credits, String stationUuid) {
        Entity e = engine.createEntity();
        UuidComponent id = new UuidComponent();
        e.add(id);
        FreighterComponent f = new FreighterComponent(name, capacity, credits);
        e.add(f);
        engine.addEntity(e);
        freighters.addDocked(id.id, f, stationUuid, now);
        return id.id;
    }

    /**
     * Advances the macro universe to sim second {@code t}: rails and macro events fire in time order,
     * then encounter prediction and the bubble boundary are updated once at {@code t}.
     */
    public void advanceTo(double t) {
        if (t < now) return;
        while (events.nextTime() <= t) {
            double te = events.nextTime();
            rails.processEventsUntil(te);
            now = te;
            events.runNext(te);
        }
        rails.processEventsUntil(t);
        now = t;
        // No anchor parent: the bubble is not in orbit around anything, so nothing can meet it.
        if (gateway == null || gateway.anchorParentUuid() == null) return;
        updatePrediction();
        if (!bubbleSuspended) bubble.update(t, PREDICTION_INTERVAL * 2);
    }

    /**
     * Stops bubble entries and exits while the bubble is collapsed for rails warp; prediction keeps
     * running so warp can drop out for the next encounter. Resuming reconciles with the rebuilt bubble.
     */
    public void setBubbleSuspended(boolean suspended) {
        if (bubbleSuspended && !suspended && bubble != null) bubble.reconcile(now);
        bubbleSuspended = suspended;
    }

    public boolean isBubbleSuspended() {
        return bubbleSuspended;
    }

    private void updatePrediction() {
        if (gateway == null) return;
        if (pendingForecast != null && pendingForecast.isDone()) {
            try {
                bubble.setForecast(pendingForecast.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                throw new IllegalStateException("Encounter prediction failed", ex.getCause());
            }
            pendingForecast = null;
        }
        if (pendingForecast != null || now < nextPrediction) return;
        Set<String> excluded = new HashSet<>(massiveBodies);
        excluded.addAll(bubble.inBubble());
        excluded.addAll(bubble.fastPasses());
        EncounterPredictionTask task = new EncounterPredictionTask(rails.snapshot(), gateway.anchorTrajectory(),
                gateway.anchorParentUuid(), excluded, now, predictionHorizon, BubbleTransitionSystem.ENTER_RADIUS);
        if (predictionExecutor == null) {
            bubble.setForecast(task.call());
        } else {
            pendingForecast = predictionExecutor.submit(task);
        }
        nextPrediction = now + PREDICTION_INTERVAL;
    }

    /** Sim second rails warp must drop out at for the next predicted bubble encounter, or {@code +inf}. */
    public double nextDropOutTime() {
        EncounterForecast f = bubble == null ? null : bubble.forecast();
        return f == null ? Double.POSITIVE_INFINITY : f.earliestEntry;
    }

    public void setPredictionHorizon(double seconds) {
        predictionHorizon = seconds;
    }

    /** Position of a rails body relative to the bubble anchor, for map and HUD rendering. */
    public StateVector relativeToAnchor(String uuid, double t, StateVector out) {
        StateVector anchor = gateway.anchorStateAt(t, new StateVector());
        rails.stateAt(uuid, t, out);
        return out.setDifference(out, anchor);
    }

    public double now() {
        return now;
    }

    public RailsModel rails() {
        return rails;
    }

    public EconomySimulation economy() {
        return economy;
    }

    public FreighterController freighters() {
        return freighters;
    }

    public BubbleGateway gateway() {
        return gateway;
    }

    public BubbleTransitionSystem bubble() {
        return bubble;
    }

    public EconomyNode station(String uuid) {
        return stations.get(uuid);
    }

    public Collection<EconomyNode> stations() {
        return stations.values();
    }
}
