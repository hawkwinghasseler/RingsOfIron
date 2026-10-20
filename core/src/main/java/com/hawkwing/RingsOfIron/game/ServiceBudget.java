package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.systems.FixedLogicSystem;
import com.ringsofiron.macrosim.economy.TraderComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Pays the player's rank budget ({@link RankLadder}) into their ship's credits once each in-game
 * week, by the sim clock, with a notice. The first payday is a week after the career starts; time
 * warp that skips several weeks pays each of them. A rank with no budget pays nothing.
 */
public class ServiceBudget extends FixedLogicSystem {
    /** An in-game week, in sim seconds. */
    public static final double WEEK = 7 * 86_400;
    /** Seconds between checks; paydays are a week apart. */
    static final float INTERVAL = 1f;

    private final FactionStanding standing;
    private final Supplier<Entity> player;
    private final DoubleSupplier now;
    /** Run once each payday, after the budget is paid in (crew wages go out here). */
    public final List<Runnable> paydays = new ArrayList<>();

    public ServiceBudget(FactionStanding standing, Supplier<Entity> player, DoubleSupplier now) {
        super(INTERVAL);
        this.standing = standing;
        this.player = player;
        this.now = now;
    }

    @Override
    protected void updateFixed(float deltaTime) {
        // A promotion held back for time in grade comes as the days pass, docked or not
        standing.promoteDue();
        pay();
    }

    /** Pays every payday up to now. Returns the credits paid. */
    public double pay() {
        ServiceRecord record = standing.record();
        double t = now.getAsDouble();
        if (record.nextPay < 0) {
            record.nextPay = t + WEEK;
            return 0;
        }
        Entity ship = player.get();
        TraderComponent trader = ship == null ? null : ship.getComponent(TraderComponent.class);
        if (trader == null) return 0; // no ship to pay into: paydays wait for one
        double paid = 0;
        while (t >= record.nextPay) {
            double budget = standing.rank().budget;
            trader.credits += budget;
            record.budgetPaid += budget;
            paid += budget;
            record.nextPay += WEEK;
            for (Runnable r : paydays) r.run();
        }
        if (paid > 0) {
            standing.notices.add(String.format(Locale.ROOT, "Budget from the %s: +%,.0f cr",
                    standing.factions().name(standing.sponsor()), paid));
        }
        return paid;
    }

    /** Sim seconds until the next payday, or a week if it is not yet set. */
    public double untilNextPay() {
        double next = standing.record().nextPay;
        return next < 0 ? WEEK : Math.max(0, next - now.getAsDouble());
    }

    /** One line for the HUD: rank, budget and when it is next paid. */
    public String describe() {
        double left = untilNextPay();
        long days = (long) (left / 86_400), hours = (long) (left / 3_600) % 24;
        return String.format(Locale.ROOT, "%s   Budget %,.0f cr/wk in %dd %02dh", standing.rankName(),
                standing.rank().budget, days, hours);
    }
}
