package com.ringsofiron.coreengine.components;

import com.badlogic.ashley.core.Component;
import java.util.UUID;

public class UuidComponent implements Component {
    public String id;

    public UuidComponent() {
        this.id = UUID.randomUUID().toString();
    }

    public UuidComponent(String id) {
        this.id = id;
    }
}
