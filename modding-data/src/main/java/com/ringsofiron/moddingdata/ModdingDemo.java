package com.ringsofiron.moddingdata;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.moddingdata.defs.CommodityDef;
import com.ringsofiron.moddingdata.defs.ShipDef;

import java.nio.file.Paths;

/**
 * Headless check of the whole pipeline: loads base data and mods, spawns every ship and
 * prints what happened. Args: [assetsDir] [modsDir].
 */
public class ModdingDemo {

    public static void main(String[] args) {
        String assetsDir = args.length > 0 ? args[0] : "assets";
        String modsDir = args.length > 1 ? args[1] : "mods";

        GameEngine engine = new GameEngine();
        ModContext ctx = new ModdingBootstrap(Paths.get(assetsDir), Paths.get(modsDir)).load(engine);

        System.out.println("Mods (load order): " + ctx.mods);
        for (String type : ctx.defs.types()) {
            System.out.println("  " + type + ": " + ctx.defs.ids(type));
        }
        System.out.println("Assets by kind: " + ctx.assets.countsByKind());

        for (CommodityDef c : ctx.defs.all(CommodityDef.class)) {
            System.out.printf("  commodity %-12s %8.1f cr  (%s)%n", c.id, c.basePrice,
                    ctx.defs.entry("commodity", c.id).lastSource());
        }
        for (String shipId : ctx.defs.ids("ship")) {
            ShipDef ship = ctx.defs.get("ship", shipId, ShipDef.class);
            Entity e = ctx.factory.spawnShip(shipId, 0, 0, 0);
            System.out.println("Spawned " + ship.name + " (" + shipId + ") uuid="
                    + e.getComponent(UuidComponent.class).id + " components=" + e.getComponents().size());
        }
        ctx.hooks.fireWorldReady();
        engine.update(1f / 60f);

        System.out.println("\nLoad report:\n" + ctx.report);
        if (ctx.report.hasErrors()) System.exit(1);
    }
}
