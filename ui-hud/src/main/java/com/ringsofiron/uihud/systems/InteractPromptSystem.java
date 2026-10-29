package com.ringsofiron.uihud.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.ringsofiron.uihud.components.InteractableConsoleComponent;
import com.ringsofiron.uihud.providers.InteractorProvider;

/**
 * Finds the nearest {@link InteractableConsoleComponent} within {@link #INTERACT_RADIUS} tiles
 * of the player's avatar and shows "Press [E] for the ... panel" for it. {@link #interact()} (bound
 * to E by the HUD) opens that console through the {@link ConsoleOpener}. Something else the game
 * lets the avatar use where it stands (a suit locker) is an {@link Interaction}: its prompt shows
 * instead when it is nearer than any console.
 */
public class InteractPromptSystem extends EntitySystem {
    public static final int DEFAULT_PRIORITY = 950;
    /** Tiles between the avatar and the console's tile center. */
    public static final float INTERACT_RADIUS = 1.5f;

    /** Opens the UI for a console entity; supplied by the HUD facade. */
    public interface ConsoleOpener {
        void open(Entity console, InteractableConsoleComponent data);
    }

    /** Something besides a console that E uses where the avatar stands: a suit locker, say. */
    public interface Interaction {
        /**
         * What E does here, finishing "Press [E] to ...", e.g. "put on a pressure suit"; null when
         * there is nothing to use.
         */
        String prompt();

        /** Squared distance, tiles, from the avatar to the thing used: the nearer of it and a console wins. */
        float distance2();

        /** Uses it. */
        void act();
    }

    private static final Family CONSOLES = Family.all(InteractableConsoleComponent.class).get();
    private static final ComponentMapper<InteractableConsoleComponent> MAPPER = ComponentMapper.getFor(InteractableConsoleComponent.class);

    private final Label prompt;
    private final ConsoleOpener opener;
    private final Vector2 avatar = new Vector2();
    private ImmutableArray<Entity> consoles;
    private InteractorProvider interactor;
    private Entity nearest;
    private boolean enabled = true;
    private java.util.function.Function<InteractableConsoleComponent, String> promptExtra;
    private Interaction interaction;
    /** The interaction's prompt as of the last update, when it was nearer than any console; null otherwise. */
    private String using;
    private float nearestDist2;

    public InteractPromptSystem(Label prompt, ConsoleOpener opener) {
        super(DEFAULT_PRIORITY);
        this.prompt = prompt;
        this.opener = opener;
        prompt.setVisible(false);
    }

    public void setInteractor(InteractorProvider interactor) {
        this.interactor = interactor;
    }

    /** Disables prompts, e.g. while a console window is already open. */
    /**
     * What the game adds after "Press [E] for the ... panel" for a console, such as the key that sits
     * you at it; null adds nothing.
     */
    public void setPromptExtra(java.util.function.Function<InteractableConsoleComponent, String> extra) {
        promptExtra = extra;
    }

    /** What E uses besides consoles; null for nothing. */
    public void setInteraction(Interaction interaction) {
        this.interaction = interaction;
    }

    public void setPromptsEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void addedToEngine(Engine engine) {
        consoles = engine.getEntitiesFor(CONSOLES);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        consoles = null;
        nearest = null;
    }

    @Override
    public void update(float deltaTime) {
        nearest = enabled ? findNearest() : null;
        using = enabled && interaction != null ? interaction.prompt() : null;
        if (using != null && nearest != null && interaction.distance2() > nearestDist2) using = null;
        if (using != null) {
            nearest = null;
            String text = "Press [E] to " + using;
            if (!prompt.textEquals(text)) prompt.setText(text);
            prompt.setVisible(true);
            return;
        }
        if (nearest == null) {
            prompt.setVisible(false);
            return;
        }
        InteractableConsoleComponent c = MAPPER.get(nearest);
        String more = promptExtra == null ? null : promptExtra.apply(c);
        String text = "Press [E] for the " + c.title + " panel" + (more == null ? "" : more);
        if (!prompt.textEquals(text)) prompt.setText(text);
        prompt.setVisible(true);
    }

    /** The console currently in reach, or null. */
    public Entity getNearest() {
        return nearest;
    }

    /** Opens the console in reach, or uses the {@link Interaction} whose prompt shows. Returns false if there is neither. */
    public boolean interact() {
        if (using != null && interaction != null && interaction.prompt() != null) {
            interaction.act();
            return true;
        }
        if (nearest == null) return false;
        opener.open(nearest, MAPPER.get(nearest));
        return true;
    }

    private Entity findNearest() {
        if (interactor == null || consoles == null) return null;
        Entity ship = interactor.getAvatarShip();
        if (ship == null || !interactor.getAvatarTile(avatar)) return null;
        Entity best = null;
        float bestDist2 = INTERACT_RADIUS * INTERACT_RADIUS;
        for (Entity e : consoles) {
            InteractableConsoleComponent c = MAPPER.get(e);
            if (!c.isOn(ship)) continue;
            float d2 = avatar.dst2(c.tileX + 0.5f, c.tileY + 0.5f);
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = e;
            }
        }
        nearestDist2 = bestDist2;
        return best;
    }
}
