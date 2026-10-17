package com.ringsofiron.coreengine.components;

import com.badlogic.ashley.core.Component;

public class TransformComponent implements Component {
    public float x;
    public float y;
    public float rotation;

    public TransformComponent() {
        this(0, 0, 0);
    }

    public TransformComponent(float x, float y, float rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
    }
}
