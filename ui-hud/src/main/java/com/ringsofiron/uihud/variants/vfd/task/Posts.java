package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;

/**
 * Where the captain is, and what changes what a post shows (functional-task 2 and 8): the post (the
 * deck, the helm, or gunnery at the weapons console or one gun), the five conditions (docked,
 * latched, in combat, rails warp, crisis), and whether a screen covers the view. Pure: read from the
 * data each frame, so it is tested without a screen.
 */
final class Posts {
    enum Post {
        /** No ship and no avatar: nothing to show but the clock. */
        NONE,
        DECK,
        HELM,
        GUNNERY
    }

    /** A hostile this close keeps the scope up while docked (8: Docked), m. */
    static final float DOCKED_HOSTILE = 50_000f;

    Post post = Post.NONE;
    /** At one gun's mount rather than the weapons console. */
    boolean atGun;
    boolean docked, latched, combat, rails, crisis;
    /** Why the crisis holds: disabled, abandoning, or stranded with a tow on offer. */
    boolean disabled, abandoning, stranded;
    /** A screen (the map, the terminal, the book) or a window covers zones 3 to 9. */
    boolean screen;
    /** The ship has weapons; and a hostile is locked. */
    boolean armed, hostileLocked;
    /** A hostile within 50 km, which keeps the scope up while docked. */
    boolean hostileNear;

    void update(HudData d, boolean screen) {
        UiContext ctx = d.ctx;
        post = post(ctx);
        atGun = ctx.presence == Presence.GUN;
        this.screen = screen;
        boolean ship = ctx.ship != null;
        docked = ship && ctx.docked;
        latched = ship && ctx.latched;
        rails = ctx.railsWarp && ctx.warp > 1;
        combat = ship && (d.orderState.battleStations || ctx.missilesInbound > 0 || d.threatFrame.incoming() > 0);
        disabled = ship && !ctx.disabledReason.isEmpty();
        abandoning = ship && (ctx.abandoning || d.orderState.abandoning);
        stranded = ship && !docked && !disabled && !abandoning && d.orderState.towRefusal.isEmpty() && d.orderState.towFee > 0;
        crisis = disabled || abandoning || stranded;
        armed = false;
        for (Mount m : d.weaponFrame.mounts) if (m.kind != Kind.POINT_DEFENSE) armed = true;
        hostileLocked = d.targetStatus.present && d.targetStatus.relation == Relation.HOSTILE;
        hostileNear = false;
        for (int i = 0; i < d.contactFrame.contacts.size; i++) {
            var c = d.contactFrame.contacts.get(i);
            if (c.relation == Relation.HOSTILE && !Float.isNaN(c.range) && c.range <= DOCKED_HOSTILE) hostileNear = true;
        }
    }

    static Post post(UiContext ctx) {
        switch (ctx.presence) {
            case HELM: return Post.HELM;
            case WEAPONS:
            case GUN: return Post.GUNNERY;
            case DECK:
                // -PviewAnywhere: the outside view from the deck is the helm's
                return ctx.exteriorView ? Post.HELM : Post.DECK;
            case ABOARD_OTHER:
            case EVA: return Post.DECK;
            default: return Post.NONE;
        }
    }

    /** The helm or gunnery: the posts that look outside. */
    boolean outside() {
        return post == Post.HELM || post == Post.GUNNERY;
    }

    // ------------------------------------------------------------------ the visibility table (8)

    /** The widgets the table shows on every post: CHR, ANN, OBJ, NTF and HNT. Not over a screen. */
    boolean common() {
        return post != Post.NONE && !screen;
    }

    /** FLT: the helm, not in rails warp. */
    boolean flight() {
        return post == Post.HELM && !screen && !rails;
    }

    /** SNS: the helm and gunnery, undocked and not warping; docked only with a hostile within 50 km. */
    boolean scope() {
        return outside() && !screen && !rails && (!docked || hostileNear);
    }

    /** WPN: expanded at gunnery always; compact at the helm with weapons, in combat or a hostile locked. Never docked or warping. */
    boolean weapons() {
        if (!outside() || screen || docked || rails) return false;
        return post == Post.GUNNERY || armed && (combat || hostileLocked);
    }

    /** WPN expanded: at gunnery. */
    boolean weaponsExpanded() {
        return post == Post.GUNNERY;
    }

    /** TGT: the helm and gunnery while a target or a navigation target is set. */
    boolean targetCard(boolean subject) {
        return outside() && !screen && subject;
    }

    /** SCH: the helm and gunnery while any of HULL, FIRE, AIR, POWER, CREW or EVA is lit. */
    boolean schematic(boolean lit) {
        return outside() && !screen && lit;
    }

    /** HLM: deck and gunnery while undocked; at the helm while an order or the autopilot stands. */
    boolean helm(boolean orderStands) {
        if (screen || post == Post.NONE || docked) return false;
        return post == Post.HELM ? orderStands : true;
    }

    /** PER and INT: on deck. */
    boolean deck() {
        return post == Post.DECK && !screen;
    }
}
