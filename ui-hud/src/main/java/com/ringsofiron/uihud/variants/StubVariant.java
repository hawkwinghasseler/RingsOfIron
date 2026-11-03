package com.ringsofiron.uihud.variants;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stands in for a combination that is not built yet: the classic HUD, with a small tag at the top
 * saying which combination is picked. Each combination's own PR replaces its class's body.
 */
public abstract class StubVariant implements HudVariant {
    private final HudVariants.Style style;
    private final HudVariants.Spec spec;

    protected StubVariant(HudVariants.Style style, HudVariants.Spec spec) {
        this.style = style;
        this.spec = spec;
    }

    @Override
    public void attach(HudVariantHost host) {
        Table row = new Table();
        row.setFillParent(true);
        row.setTouchable(Touchable.disabled);
        row.top().padTop(4f);
        row.add(new Label(style.label + " / " + spec.label + ": not built yet, showing the classic HUD", host.skin(), "dim"));
        host.layer().addActor(row);
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public Set<ClassicPart> keeps() {
        return EnumSet.allOf(ClassicPart.class);
    }
}
