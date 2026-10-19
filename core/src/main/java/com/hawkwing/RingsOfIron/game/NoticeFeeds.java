package com.hawkwing.RingsOfIron.game;

import java.util.List;
import java.util.Locale;

/**
 * Brings the notices systems keep in their own lists into the {@link NoticeLog}, once a frame,
 * and empties the lists: door forcing and what the factions made of it, contracts, promotions
 * and paydays, the yard, boarding, salvage and pirates. New systems post to
 * {@link GameWorld#notices} directly instead.
 */
public final class NoticeFeeds {
    private NoticeFeeds() {
    }

    public static void collect(GameWorld world) {
        NoticeLog log = world.notices;
        if (!world.forcing.lastOutcome.isEmpty()) {
            log.post(world.forcing.lastOutcome);
            world.forcing.lastOutcome = "";
        }
        for (FactionStanding.Act act : world.standing.acts) {
            boolean refused = act.standing < FactionStanding.REFUSE_BELOW;
            log.post(String.format(Locale.ROOT, "%s takes that as a hostile act: standing %+.0f%s",
                    PlayerDocking.name(act.victim), act.standing,
                    refused ? "; its stations will refuse you clearance" : ""),
                    refused ? NoticeLog.Kind.WARNING : NoticeLog.Kind.INFO);
        }
        world.standing.acts.clear();
        // Contracts taken, delivered, done or failed
        for (String news : world.contracts.notices) log.post(news, contractKind(news));
        world.contracts.notices.clear();
        // Promotions and budget paydays
        for (String news : world.standing.notices) {
            log.post(news, news.startsWith("Promoted") ? NoticeLog.Kind.IMPORTANT : NoticeLog.Kind.INFO);
        }
        world.standing.notices.clear();
        // The yard, boarding and salvage each say only how their work last went
        postLast(log, world.yard.notices);
        postLast(log, world.boarding.notices);
        postLast(log, world.salvage.notices);
        // Pirates moving to intercept, robbing freighters, breaking off
        for (String news : world.hostiles.notices) {
            log.post(news, news.endsWith("intercept you") ? NoticeLog.Kind.WARNING : NoticeLog.Kind.INFO);
        }
        world.hostiles.notices.clear();
    }

    /** Contracts done, called off or failed matter; taking one or a delivery on the way is news. */
    static NoticeLog.Kind contractKind(String news) {
        // An operation ending (done, failed, called off) matters as a contract's end does
        if (news.startsWith("Operation") && (news.contains(" done.") || news.contains(" failed.") || news.contains("called off"))) {
            return NoticeLog.Kind.IMPORTANT;
        }
        if (news.startsWith("Contract taken") || !news.startsWith("Contract")) return NoticeLog.Kind.INFO;
        return NoticeLog.Kind.IMPORTANT;
    }

    private static void postLast(NoticeLog log, List<String> notices) {
        if (!notices.isEmpty()) log.post(notices.get(notices.size() - 1));
        notices.clear();
    }
}
