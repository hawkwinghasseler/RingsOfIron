package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.ringsofiron.coreengine.components.PlayerControllerComponent;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.uihud.ViewModeController;
import com.ringsofiron.uihud.components.HudMarkerComponent;
import com.ringsofiron.uihud.providers.OrbitInfoProvider;
import com.ringsofiron.uihud.providers.TargetingProvider;

import java.util.Locale;

/**
 * Text readout in the top-left: speed, heading, view mode, locked target and orbit.
 * Values come from the player entity, the optional {@link PhysicsManager} (for the
 * floating origin's own velocity), and the optional targeting and orbit providers.
 */
public class FlightReadoutSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 960;
    private static final Family PLAYER = Family.all(PlayerControllerComponent.class, TransformComponent.class).get();
    private static final ComponentMapper<TransformComponent> TRANSFORMS = ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> BODIES = ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<HudMarkerComponent> MARKERS = ComponentMapper.getFor(HudMarkerComponent.class);

    private final Label speed, heading, mode, target, targetDetail, orbit, orbitDetail;
    private final ViewModeController viewMode;
    private final Vector2 v = new Vector2();
    private final Vector2 tv = new Vector2();
    private ImmutableArray<Entity> players;
    private PhysicsManager physics;
    private TargetingProvider targeting;
    private OrbitInfoProvider orbitInfo;

    public FlightReadoutSystem(Table panel, Skin skin, ViewModeController viewMode) {
        super(DEFAULT_PRIORITY);
        this.viewMode = viewMode;
        panel.left().top();
        panel.add(new Label("FLIGHT", skin, "title")).left().row();
        speed = add(panel, skin, "default");
        heading = add(panel, skin, "default");
        mode = add(panel, skin, "dim");
        panel.add(new Label("TARGET", skin, "title")).left().padTop(8f).row();
        target = add(panel, skin, "default");
        targetDetail = add(panel, skin, "dim");
        panel.add(new Label("ORBIT", skin, "title")).left().padTop(8f).row();
        orbit = add(panel, skin, "default");
        orbitDetail = add(panel, skin, "dim");
    }

    private static Label add(Table panel, Skin skin, String style) {
        Label l = new Label("", skin, style);
        panel.add(l).left().row();
        return l;
    }

    public void setPhysics(PhysicsManager physics) {
        this.physics = physics;
    }

    public void setTargeting(TargetingProvider targeting) {
        this.targeting = targeting;
    }

    public void setOrbitInfo(OrbitInfoProvider orbitInfo) {
        this.orbitInfo = orbitInfo;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(PLAYER);
    }

    @Override
    public void update(float deltaTime) {
        Entity player = players.size() > 0 ? players.first() : null;
        if (player == null) {
            set(speed, "No ship");
            set(heading, "");
        } else {
            TransformComponent t = TRANSFORMS.get(player);
            velocityOf(player, v);
            double vx = v.x + (physics != null ? physics.getOriginVx() : 0.0);
            double vy = v.y + (physics != null ? physics.getOriginVy() : 0.0);
            set(speed, fmt("Speed    %8.1f m/s", Math.hypot(vx, vy)));
            float hdg = ((t.rotation % 360f) + 360f) % 360f;
            set(heading, fmt("Heading  %8.1f deg", hdg));
        }
        set(mode, "View     " + viewMode.getMode() + "   [V]");

        updateTarget(player);
        updateOrbit();
    }

    private void updateTarget(Entity player) {
        Entity locked = targeting != null ? targeting.getLockedTarget() : null;
        if (locked == null || player == null) {
            set(target, targeting == null ? "No fire control" : "No target   [Tab]");
            set(targetDetail, "");
            return;
        }
        HudMarkerComponent m = MARKERS.get(locked);
        TransformComponent pt = TRANSFORMS.get(player);
        TransformComponent tt = TRANSFORMS.get(locked);
        set(target, m != null ? m.label : "Unknown contact");
        if (tt == null) {
            set(targetDetail, "");
            return;
        }
        velocityOf(player, v);
        velocityOf(locked, tv);
        float dx = tt.x - pt.x, dy = tt.y - pt.y;
        float range = (float) Math.sqrt(dx * dx + dy * dy);
        // Closing speed: positive when the gap is shrinking.
        float closing = range > 0f ? -((tv.x - v.x) * dx + (tv.y - v.y) * dy) / range : 0f;
        float tti = targeting.getTimeToImpact();
        // The gun's flight time to the lead point; "TTI" read like the time until the ships meet
        String ttiText = Float.isNaN(tti) ? "no firing solution" : fmt("shot %.1f s", tti);
        set(targetDetail, HudRenderSystem.formatRange(range) + fmt("  closing %+.0f m/s  ", closing) + ttiText);
    }

    private void updateOrbit() {
        OrbitInfoProvider.OrbitInfo o = orbitInfo != null ? orbitInfo.getPlayerOrbit() : null;
        if (o == null) {
            set(orbit, "No orbit");
            set(orbitDetail, "");
            return;
        }
        set(orbit, o.bodyName + "  alt " + HudRenderSystem.formatRange((float) o.altitude)
                + (o.impactWarning ? "   IMPACT" : ""));
        String period = Double.isInfinite(o.period) ? "escape" : fmt("T %.0f s", o.period);
        set(orbitDetail, "Ap " + HudRenderSystem.formatRange((float) o.apoapsis)
                + "  Pe " + HudRenderSystem.formatRange((float) o.periapsis) + "  " + period);
    }

    private static void velocityOf(Entity e, Vector2 out) {
        PhysicsBodyComponent b = BODIES.get(e);
        if (b != null && b.body != null) PhysicsManager.getVelocity(b.body, out);
        else out.setZero();
    }

    private static void set(Label label, String text) {
        if (!label.textEquals(text)) label.setText(text);
    }

    private static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
