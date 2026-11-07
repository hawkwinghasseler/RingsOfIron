package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.uihud.systems.InteractPromptSystem.Interaction;

/** Several things [E] uses where the avatar stands, as one: the nearest of them with a prompt. */
public final class Interactions {

    private Interactions() {}

    /** The nearer of {@code all} that has something to use, each time it is asked. */
    public static Interaction nearer(Interaction... all) {
        return new Interaction() {
            private Interaction pick() {
                Interaction best = null;
                float bestD = Float.MAX_VALUE;
                for (Interaction i : all) {
                    if (i.prompt() == null) continue;
                    float d = i.distance2();
                    if (best == null || d < bestD) {
                        best = i;
                        bestD = d;
                    }
                }
                return best;
            }

            @Override
            public String prompt() {
                Interaction i = pick();
                return i == null ? null : i.prompt();
            }

            @Override
            public float distance2() {
                Interaction i = pick();
                return i == null ? Float.MAX_VALUE : i.distance2();
            }

            @Override
            public void act() {
                Interaction i = pick();
                if (i != null) i.act();
            }
        };
    }
}
