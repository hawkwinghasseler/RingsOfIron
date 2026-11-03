package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Post;

/**
 * What every unit of the Task-driven Cassette HUD reads this frame: the data, the alert routing, and
 * the post and conditions the visibility rules (spec section 8) turn on.
 */
final class TaskFrame {
    HudData data;
    final TaskAlerts alerts = new TaskAlerts();

    /** Where the captain is: at the helm, at gunnery (the weapons console or a gun), or on foot. */
    Post post = Post.DECK;
    /** No ship and no avatar: nothing but the clock. */
    boolean noShip;
    /** At a single gun rather than the weapons console. */
    boolean atGun;
    boolean docked;
    /** Rails warp: FLT, the scope and the weapons hide. */
    boolean rails;
    boolean combat;
    /** A screen (the map or a window) is up: zones 3 to 9 are covered. */
    boolean screen;
    /** Disabled, stranded or abandoning: the crisis placard is up. */
    boolean crisis;
    /** Not disabled, but the ship can't get home by itself and the tow would come. */
    boolean stranded;
    /** Seconds the HUD has been up, real time. */
    float time;

    void read(HudData d, boolean screenOpen) {
        data = d;
        Presence p = d.ctx.presence;
        noShip = p == Presence.NONE || d.ctx.ship == null;
        atGun = p == Presence.GUN;
        post = p == Presence.HELM ? Post.HELM : p == Presence.WEAPONS || p == Presence.GUN ? Post.GUNNERY : Post.DECK;
        // Seated with the deck view up is the deck, as far as the screen goes
        if (post != Post.DECK && !d.ctx.exteriorView) post = Post.DECK;
        docked = d.ctx.docked;
        rails = d.ctx.railsWarp && d.ctx.warp > 1;
        combat = d.ctx.combat;
        screen = screenOpen;
        boolean disabled = !d.ctx.disabledReason.isEmpty();
        boolean abandoning = d.ctx.abandoning || d.orderState.abandoning;
        // The beacon refuses a ship that can still fly home, so a tow on offer means the ship is stranded
        stranded = !disabled && !abandoning && !noShip && !docked && d.orders != null && d.orderState.towRefusal.isEmpty();
        crisis = disabled || abandoning || stranded;
    }

    boolean helm() {
        return post == Post.HELM;
    }

    boolean gunnery() {
        return post == Post.GUNNERY;
    }

    boolean deck() {
        return post == Post.DECK;
    }
}
