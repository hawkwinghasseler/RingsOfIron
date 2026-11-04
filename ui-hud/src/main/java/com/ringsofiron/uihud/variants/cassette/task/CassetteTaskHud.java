package com.ringsofiron.uihud.variants.cassette.task;

import com.ringsofiron.uihud.UiHud;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.variants.ClassicPart;
import com.ringsofiron.uihud.variants.HudVariant;
import com.ringsofiron.uihud.variants.HudVariantHost;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardCanvas;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.task.TaskAlerts.Post;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cassette-futurism art (the Yard Standard, documentation/ui-overhaul/style-cassette.md) on the
 * task-driven functional spec (documentation/ui-overhaul/functional-task.md): a console of separate
 * units, each one instrument for one kind of data, shown by post and situation as spec section 8 says.
 *
 * <p>The whole HUD is one actor ({@link YardCanvas}) painting every unit from one texture page; the
 * units are drawn from their mockups' coordinates and placed each frame by {@link TaskLayout}. The
 * classic HUD keeps only its world overlays: the contact markers and the missile paths.
 */
public class CassetteTaskHud implements HudVariant {
    /** Hostiles within this many metres keep the scope up while docked. */
    static final float DOCKED_SCOPE_RANGE = 50_000f;

    private HudVariantHost host;
    private final TaskFrame frame = new TaskFrame();
    private final TaskLayout layout = new TaskLayout();
    private YardPaint paint;
    private YardCanvas canvas;
    private MasterUnit master;
    private Visor visor;
    private Rack rack;
    private SuitUnit suit;
    private Schematic schematic;
    private FlightPod flight;
    private HelmPod helm;
    private Scope scope;
    private TargetPlate target;
    private GunPanel guns;
    private DefencePod defence;
    private KeySill sill;
    private CrisisPlacard crisis;
    private PromptStrip prompt;
    private Post lastPost;

    @Override
    public void attach(HudVariantHost host) {
        this.host = host;
        paint = new YardPaint();
        paint.fonts.setScale(host.uiScale());
        canvas = new YardCanvas(paint);
        master = new MasterUnit(frame);
        visor = new Visor(frame);
        rack = new Rack(frame);
        suit = new SuitUnit(frame);
        schematic = new Schematic(frame);
        flight = new FlightPod(frame);
        helm = new HelmPod(frame);
        scope = new Scope(frame);
        target = new TargetPlate(frame);
        guns = new GunPanel(frame);
        defence = new DefencePod(frame);
        sill = new KeySill(frame);
        crisis = new CrisisPlacard(frame);
        prompt = new PromptStrip(frame);
        // Painted in this order: the sill under the pods standing on it, the visor and the placard last
        for (YardPart p : new YardPart[]{sill, schematic, suit, master, rack, target, scope, guns, defence, flight, helm, prompt, visor, crisis}) {
            canvas.add(p);
        }
        host.layer().addActor(canvas);
        canvas.setBounds(0f, 0f, host.width(), host.height());
    }

    @Override
    public void update(float delta) {
        HudData d = host.data();
        UiHud hud = host.hud();
        boolean map = hud.isMapOpen();
        frame.read(d, map || hud.openWindow() != null);
        frame.time += delta;
        frame.alerts.update(d, delta, frame.gunnery());
        if (frame.post != lastPost) {
            // The range set by hand holds until the post changes
            scope.resetRange();
            lastPost = frame.post;
        }
        visible(d, map, delta);
        place(d);
        canvas.setBounds(0f, 0f, host.width(), host.height());
    }

    /** Spec section 8: what each post shows, and how docking, combat, the rails warp and a crisis change it. */
    private void visible(HudData d, boolean map, float delta) {
        TaskFrame f = frame;
        boolean screen = f.screen, ship = !f.noShip;
        boolean outside = f.helm() || f.gunnery();

        master.visible = true;
        master.annOn = !screen && ship;
        master.line2 = !map;
        visor.swing(delta, f.alerts.bandUp());
        crisis.visible = f.crisis;

        suit.visible = !screen && ship && f.deck() && SuitUnit.wanted(d);
        schematic.visible = !screen && ship && outside && f.alerts.schematicWanted();

        flight.visible = !screen && ship && f.helm() && !f.rails;
        flight.mode = FlightPod.modeFor(f);
        flight.helmLine = HelmPod.orderStands(d);
        boolean brown = d.powerFrame.ratedWatts > 0 && d.powerFrame.outputWatts <= 0;
        flight.brown = brown;
        master.fltOwnsBurn = flight.visible && flight.mode == FlightPod.Mode.BURN;

        // At the helm the helm line rides on the flight pod; on deck and at gunnery it has a pod of its own
        helm.visible = !screen && ship && (f.deck() || f.gunnery()) && !f.docked;
        helm.finish = f.gunnery() ? Yard.Finish.GUN : Yard.Finish.YARD;

        scope.visible = !screen && ship && outside && !f.rails && (!f.docked || hostileNear(d));
        scope.brown = brown;
        scope.finish = f.gunnery() ? Yard.Finish.GUN : Yard.Finish.YARD;
        scope.chooseRange();

        target.visible = !screen && ship && outside && TargetPlate.wanted(d, f.docked);
        target.identityOnly = flight.visible && flight.mode == FlightPod.Mode.DOCK;
        target.finish = f.gunnery() ? Yard.Finish.GUN : Yard.Finish.YARD;
        schematic.finish = f.gunnery() ? Yard.Finish.GUN : Yard.Finish.YARD;

        boolean armed = d.weaponFrame.mounts.size > 0;
        boolean fight = f.combat || (d.targetStatus.present && d.targetStatus.relation == Relation.HOSTILE && d.contactFrame.locked() != null);
        if (f.gunnery()) {
            guns.visible = !screen && ship && armed && !f.docked && !f.rails;
            guns.compact = false;
        } else {
            guns.visible = !screen && ship && f.helm() && armed && fight && !f.docked && !f.rails;
            guns.compact = true;
        }
        defence.visible = guns.visible && !guns.compact;

        prompt.visible = !screen && ship && f.deck() && !d.promptAction.isEmpty();
        sill.visible = !screen;

        // The sill never repeats a key another unit shows
        sill.elsewhere.clear();
        if (flight.visible) {
            switch (flight.mode) {
                case DOCK: sill.elsewhere.add("ARM_PORT"); sill.elsewhere.add("NEXT_PORT"); sill.elsewhere.add("HELM_DOCK"); break;
                case DEPARTURE: sill.elsewhere.add("UNDOCK"); sill.elsewhere.add("TRADE"); sill.elsewhere.add("YARD"); break;
                case BURN: sill.elsewhere.add("WARP_TO_BURN"); break;
                default: break;
            }
        }
        if (crisis.visible) {
            sill.elsewhere.add("CUT");
            sill.elsewhere.add("ABANDON");
        }
        sill.promptKeys.clear();
        if (prompt.visible) for (String k : PromptStrip.keys(d)) sill.promptKeys.add(k);
        sill.choose(delta);
    }

    private static boolean hostileNear(HudData d) {
        for (Contact c : d.contactFrame.contacts) {
            if (c.relation == Relation.HOSTILE && !Float.isNaN(c.range) && c.range <= DOCKED_SCOPE_RANGE) return true;
        }
        return false;
    }

    private void place(HudData d) {
        TaskLayout l = layout;
        l.width = host.width();
        l.height = host.height();
        l.annOn = master.annOn;
        l.visor = visor.visible;
        l.per = suit.visible;
        l.sch = schematic.visible;
        l.flt = flight.visible;
        l.fltH = flight.height();
        l.hlm = helm.visible;
        l.hlmAtGunnery = frame.gunnery();
        l.scope = scope.visible;
        l.tgt = target.visible;
        l.tgtH = target.height();
        l.wpn = guns.visible;
        l.wpnCompact = guns.compact;
        l.wpnBaseH = guns.heightFor(0);
        l.wpnMounts = guns.mounts();
        l.defence = defence.visible;
        l.defenceH = defence.height();
        l.crs = crisis.visible;
        l.crsH = crisis.height();
        l.prompt = prompt.visible;
        if (prompt.visible) l.promptW = prompt.measure(paint);
        l.sill = sill.visible;
        l.rack = !frame.screen && !frame.noShip;
        l.solve();
        guns.maxRows = guns.compact ? 8 : l.wpnRows;
        l.placeRack(l.rack ? rack.measure(l.rackLimit) : 0f);
        rack.visible = l.rackR.on;

        put(master, l.master);
        put(visor, l.visorR);
        put(rack, l.rackR);
        put(suit, l.perR);
        put(schematic, l.schR);
        put(flight, l.fltR);
        put(helm, l.hlmR);
        put(scope, l.scopeR);
        put(target, l.tgtR);
        put(guns, l.wpnR);
        put(sill, l.sillR);
        put(crisis, l.crsR);
        put(prompt, l.promptR);
        put(defence, l.defR);
    }

    private static void put(YardPart p, TaskLayout.Rect r) {
        if (!r.on) {
            p.visible = false;
            return;
        }
        p.place(r.x, r.y, r.w, r.h);
    }

    @Override
    public void resize(int width, int height, float uiScale) {
        if (paint != null) paint.fonts.setScale(uiScale);
        if (canvas != null && host != null) canvas.setBounds(0f, 0f, host.width(), host.height());
    }

    @Override
    public void detach() {
        if (paint != null) paint.dispose();
        paint = null;
        canvas = null;
    }

    /** The world overlays stay: contact markers and missile paths. Everything else is the Yard's. */
    @Override
    public Set<ClassicPart> keeps() {
        return EnumSet.of(ClassicPart.WORLD_MARKERS, ClassicPart.THREAT_OVERLAY);
    }
}
