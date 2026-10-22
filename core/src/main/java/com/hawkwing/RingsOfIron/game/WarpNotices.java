package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.orbital.components.CelestialBodyComponent;
import com.ringsofiron.orbital.warp.TimeWarpController;

/**
 * What time warp tells the player, as notices: a step refused, and why warp dropped out ("Dropped
 * out of warp: a planned burn is due"). The game and the orbit demo both post through here.
 */
public final class WarpNotices {
    private WarpNotices() {
    }

    /** Posts refusals and drop-outs of {@code world}'s time warp to its notices from now on. */
    public static void attach(GameWorld world) {
        world.timeWarp.changes.add((signal, change) -> {
            if (change.blockedReason != null) world.notices.post("Time warp refused: " + change.blockedReason);
            else if (change.reason != null && change.reason != TimeWarpController.DropReason.REQUESTED) {
                world.notices.post(dropNotice(world, change));
            }
        });
    }

    /** Steps warp up or down one level; a refused step is posted with its reason. Returns whether it stepped. */
    public static boolean step(GameWorld world, int direction) {
        if (world.timeWarp.step(direction)) return true;
        String reason = world.engine.getRailsWarpBlockedReason();
        world.notices.post("Time warp refused" + (reason == null ? "" : ": " + reason));
        return false;
    }

    /** Why warp stopped, in words, not the reason's enum name. */
    public static String dropNotice(GameWorld world, TimeWarpController.WarpChange change) {
        String why = null;
        switch (change.reason) {
            case PLAYER_EVENT:
                if (change.event == null) break;
                switch (change.event.type) {
                    case MANEUVER: why = "a planned burn is due"; break;
                    case SOI_ENTRY: why = "entering " + bodyName(world, change.event.targetUuid) + "'s sphere of influence"; break;
                    case SOI_EXIT: why = "leaving this body's sphere of influence"; break;
                    case IMPACT: why = "on course to hit the surface"; break;
                    default: break;
                }
                break;
            case ENCOUNTER:
                why = (change.encounter == null ? "a craft" : PlayerDocking.name(change.encounter)) + " is coming within 20 km";
                break;
            default:
                break;
        }
        return why == null ? "Dropped out of warp" : "Dropped out of warp: " + why;
    }

    /** A body's name, or the craft's name for anything that is not a body. */
    public static String bodyName(GameWorld world, String uuid) {
        Entity e = uuid == null ? null : world.universe.entity(uuid);
        CelestialBodyComponent body = e == null ? null : e.getComponent(CelestialBodyComponent.class);
        return body != null && body.name != null ? body.name : world.nav.target.name(uuid);
    }
}
