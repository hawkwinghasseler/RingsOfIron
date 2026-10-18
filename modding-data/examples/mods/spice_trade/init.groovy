// Runs once at startup with: engine, factory, defs, assets, hooks, mod, log.

// Data can be defined from code too; it merges exactly like a data file.
def spice = defs.get("commodity", "spice", com.ringsofiron.moddingdata.defs.CommodityDef)
defs.define("commodity", "spice_contraband", [
        name           : "Contraband Spice",
        category       : "luxury",
        base_price     : SpiceMath.smugglerPrice(spice.basePrice),
        mass_per_unit  : spice.massPerUnit,
        volume_per_unit: spice.volumePerUnit,
        illegal        : true,
], mod.id)

// A ship variant built from the base corvette's fields.
def base = defs.entry("ship", "corvette_patrol").raw()
defs.define("ship", "corvette_smuggler", base + [name: "Smuggler Corvette", max_thrust: 3.0e6, hardpoints: []], mod.id)

// Called for every spawned entity.
factory.addSpawnListener { entity, entry ->
    if (entry.id == "corvette_smuggler") log.info("A smuggler has appeared")
}

hooks.onWorldReady { log.info("${mod.name} ready: ${defs.ids('commodity').size()} commodities") }
