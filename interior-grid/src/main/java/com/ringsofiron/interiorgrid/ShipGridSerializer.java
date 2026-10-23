package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Saves a {@link ShipGridComponent} as plain state and rebuilds it on load.
 *
 * Tiles point at shared objects (their compartment, hull piece and mount), which a
 * field-by-field save would duplicate. This writes each thing once: the tiles' own
 * state, the edge walls, hull pieces and mounts, and each compartment's air keyed by one
 * of its tiles. Loading places the pieces and mounts again, rebuilds the compartments
 * from the layout and pours each room's saved air back in. Airflow and power are
 * recalculated on the next tick.
 */
public class ShipGridSerializer implements Json.Serializer<ShipGridComponent> {

    @Override
    public void write(Json json, ShipGridComponent grid, Class knownType) {
        json.writeObjectStart();
        json.writeValue("width", grid.width);
        json.writeValue("height", grid.height);
        json.writeValue("tileSize", grid.tileSize);
        json.writeValue("originX", grid.gridOriginLocal.x);
        json.writeValue("originY", grid.gridOriginLocal.y);
        if (grid.chassis != null) json.writeValue("chassis", grid.chassis);
        json.writeValue("patchKits", grid.patchKits);
        json.writeValue("medicalStores", grid.medicalStores);

        json.writeArrayStart("tiles");
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                json.writeObjectStart();
                json.writeValue("wall", t.wallType.name());
                json.writeValue("floor", t.floorType.name());
                json.writeValue("weight", t.weightClass.name());
                json.writeValue("material", t.material);
                json.writeValue("covering", t.covering.name());
                json.writeValue("fuel", t.fuel);
                json.writeValue("fire", t.fireIntensity);
                json.writeValue("temperature", t.temperature);
                json.writeValue("integrity", t.integrity);
                json.writeValue("sealed", t.bulkheadSealed);
                if (t.patchedAt > 0f) json.writeValue("patch", t.patchedAt);
                if (t.shorted) json.writeValue("short", true);
                if (t.crushed) json.writeValue("crushed", true);
                json.writeObjectEnd();
            }
        }
        json.writeArrayEnd();

        writeEdges(json, "edgeH", grid.edgeH);
        writeEdges(json, "edgeV", grid.edgeV);
        writeConduits(json, "conduitH", grid.conduitH);
        writeConduits(json, "conduitV", grid.conduitV);
        writePipes(json, "pipes", grid.pipes);
        writePipes(json, "pipeH", grid.pipeH);
        writePipes(json, "pipeV", grid.pipeV);

        json.writeArrayStart("pieces");
        for (HullPiece p : grid.hullPieces) {
            json.writeObjectStart();
            json.writeValue("shape", p.shape.name());
            json.writeValue("x", p.originX);
            json.writeValue("y", p.originY);
            json.writeValue("width", p.baseWidth);
            json.writeValue("height", p.baseHeight);
            json.writeValue("rotation", p.rotation);
            json.writeValue("weight", p.weightClass.name());
            json.writeValue("material", p.material);
            json.writeValue("integrity", p.integrity);
            if (p.patchedAt > 0f) json.writeValue("patch", p.patchedAt);
            if (p.crushed) json.writeValue("crushed", true);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        if (grid.dents != null) json.writeValue("dents", grid.dents);

        json.writeArrayStart("mounts");
        for (MountFootprint m : grid.mounts) {
            json.writeObjectStart();
            json.writeValue("kind", m.kind.name());
            json.writeValue("size", m.size.name());
            json.writeValue("x", m.originX);
            json.writeValue("y", m.originY);
            json.writeValue("rotation", m.rotation);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();

        if (grid.rcsThrusters.size > 0) {
            json.writeArrayStart("rcs");
            for (RcsThruster r : grid.rcsThrusters) {
                json.writeObjectStart();
                json.writeValue("x", r.x);
                json.writeValue("y", r.y);
                json.writeValue("side", r.side.name());
                json.writeValue("kind", r.kind.name());
                if (r.thrust > 0) json.writeValue("thrust", r.thrust);
                json.writeObjectEnd();
            }
            json.writeArrayEnd();
        }

        if (grid.wallLights != null) {
            json.writeArrayStart("wallLights");
            for (WallLight l : grid.wallLights) {
                json.writeObjectStart();
                json.writeValue("x", l.x);
                json.writeValue("y", l.y);
                json.writeValue("side", l.side.name());
                json.writeObjectEnd();
            }
            json.writeArrayEnd();
        }

        Array<Furnishing> furnishings = Furnishings.all(grid);
        if (furnishings.size > 0) {
            json.writeArrayStart("furnishings");
            for (Furnishing f : furnishings) {
                FurnishingType k = f.type;
                json.writeObjectStart();
                json.writeValue("type", k.id);
                json.writeValue("name", k.name);
                json.writeValue("art", k.art);
                json.writeValue("width", k.width);
                json.writeValue("depth", k.depth);
                json.writeValue("fullFuel", k.fuel);
                json.writeValue("price", k.price);
                json.writeValue("use", k.use.name());
                json.writeValue("seats", k.seats);
                json.writeValue("relax", k.relax);
                json.writeValue("verb", k.verb);
                if (k.flat) json.writeValue("flat", true);
                json.writeValue("x", f.tileX);
                json.writeValue("y", f.tileY);
                json.writeValue("ox", f.offsetX);
                json.writeValue("oy", f.offsetY);
                json.writeValue("rotation", f.rotation);
                json.writeValue("fuel", f.fuel);
                json.writeObjectEnd();
            }
            json.writeArrayEnd();
        }

        json.writeArrayStart("compartments");
        for (Compartment c : grid.compartments) {
            if (c.tiles.size == 0) continue;
            json.writeObjectStart();
            json.writeValue("tile", c.tiles.get(0));
            json.writeValue("oxygen", c.oxygen);
            json.writeValue("nitrogen", c.nitrogen);
            json.writeValue("carbonDioxide", c.carbonDioxide);
            json.writeValue("smoke", c.smoke);
            json.writeObjectEnd();
        }
        json.writeArrayEnd();
        json.writeObjectEnd();
    }

    private static void writeEdges(Json json, String name, EdgeWall[][] edges) {
        json.writeArrayStart(name);
        for (int y = 0; y < edges.length; y++) {
            for (int x = 0; x < edges[y].length; x++) {
                EdgeWall e = edges[y][x];
                if (e == null) continue;
                json.writeObjectStart();
                json.writeValue("x", x);
                json.writeValue("y", y);
                json.writeValue("weight", e.weightClass.name());
                json.writeValue("material", e.material);
                json.writeValue("integrity", e.integrity);
                json.writeValue("bulkhead", e.hasBulkhead);
                json.writeValue("sealed", e.bulkheadSealed);
                if (e.patchedAt > 0f) json.writeValue("patch", e.patchedAt);
                json.writeObjectEnd();
            }
        }
        json.writeArrayEnd();
    }

    private static void writeConduits(Json json, String name, EdgeConduit[][] conduits) {
        json.writeArrayStart(name);
        for (int y = 0; y < conduits.length; y++) {
            for (int x = 0; x < conduits[y].length; x++) {
                if (conduits[y][x] == null) continue;
                json.writeObjectStart();
                json.writeValue("x", x);
                json.writeValue("y", y);
                json.writeObjectEnd();
            }
        }
        json.writeArrayEnd();
    }

    @Override
    public ShipGridComponent read(Json json, JsonValue data, Class type) {
        int w = data.getInt("width"), h = data.getInt("height");
        float tileSize = data.getFloat("tileSize");
        ShipGridComponent grid = new ShipGridComponent(w, h, tileSize, data.getFloat("originX"), data.getFloat("originY"));
        grid.chassis = data.getString("chassis", null);
        grid.patchKits = data.getInt("patchKits", LeakPatches.DEFAULT_KITS);
        grid.medicalStores = data.getInt("medicalStores", ShipGridComponent.DEFAULT_MEDICAL_STORES);

        // Pieces first: placing one resets its tiles, which the tile state below then restores
        for (JsonValue p = data.get("pieces").child; p != null; p = p.next) {
            HullPiece piece = new HullPiece(HullPiece.Shape.valueOf(p.getString("shape")), p.getInt("x"), p.getInt("y"),
                    p.getInt("width"), p.getInt("height"), p.getInt("rotation"),
                    WeightClass.valueOf(p.getString("weight")), p.getString("material"));
            grid.placeHullPiece(piece);
            piece.integrity = p.getFloat("integrity");
            piece.patchedAt = p.getFloat("patch", 0f);
            piece.crushed = p.getBoolean("crushed", false);
        }

        int i = 0;
        for (JsonValue v = data.get("tiles").child; v != null; v = v.next, i++) {
            Tile t = grid.tiles[i / w][i % w];
            t.wallType = Tile.WallType.valueOf(v.getString("wall"));
            t.floorType = Tile.FloorType.valueOf(v.getString("floor"));
            t.weightClass = WeightClass.valueOf(v.getString("weight"));
            t.material = v.getString("material");
            t.covering = FloorCovering.valueOf(v.getString("covering"));
            t.fuel = v.getFloat("fuel");
            t.fireIntensity = v.getFloat("fire");
            t.temperature = v.getFloat("temperature");
            t.integrity = v.getFloat("integrity");
            t.bulkheadSealed = v.getBoolean("sealed");
            t.patchedAt = v.getFloat("patch", 0f);
            t.shorted = v.getBoolean("short", false);
            t.crushed = v.getBoolean("crushed", false);
        }
        // Saves from before collision dents have none
        JsonValue dents = data.get("dents");
        if (dents != null && dents.size == 2 * (w + 1) * (h + 1)) grid.dents = dents.asFloatArray();

        readEdges(data.get("edgeH"), grid.edgeH);
        readEdges(data.get("edgeV"), grid.edgeV);
        // Saves from before edge conduits have none
        readConduits(data.get("conduitH"), grid.conduitH);
        readConduits(data.get("conduitV"), grid.conduitV);
        // Saves from before pipes have none
        readPipes(data.get("pipes"), grid.pipes);
        readPipes(data.get("pipeH"), grid.pipeH);
        readPipes(data.get("pipeV"), grid.pipeV);

        // Mounts were valid when saved; damage since then must not refuse them now
        for (JsonValue m = data.get("mounts").child; m != null; m = m.next) {
            MountFootprint mount = new MountFootprint(MountFootprint.MountKind.valueOf(m.getString("kind")),
                    MountFootprint.MountSize.valueOf(m.getString("size")), m.getInt("x"), m.getInt("y"), m.getInt("rotation"));
            for (int y = mount.originY; y < mount.originY + mount.height(); y++) {
                for (int x = mount.originX; x < mount.originX + mount.width(); x++) {
                    Tile t = grid.getTile(x, y);
                    if (t != null) t.mount = mount;
                }
            }
            grid.mounts.add(mount);
        }

        // Saves from before RCS blocks have none
        JsonValue rcs = data.get("rcs");
        for (JsonValue r = rcs == null ? null : rcs.child; r != null; r = r.next) {
            grid.rcsThrusters.add(new RcsThruster(r.getInt("x"), r.getInt("y"), Side.valueOf(r.getString("side")),
                    RcsThruster.Kind.valueOf(r.getString("kind")), r.getDouble("thrust", 0)));
        }

        // Saves from before lights were placed have none, and the game works them out
        JsonValue lights = data.get("wallLights");
        if (lights != null) {
            grid.wallLights = new Array<>();
            for (JsonValue l = lights.child; l != null; l = l.next) {
                grid.wallLights.add(new WallLight(l.getInt("x"), l.getInt("y"), Side.valueOf(l.getString("side"))));
            }
        }

        // Each furnishing keeps the kind it was bought as, so a save loads the same whatever the data says now.
        // Saves from before furnishings have none
        JsonValue furnishings = data.get("furnishings");
        for (JsonValue f = furnishings == null ? null : furnishings.child; f != null; f = f.next) {
            FurnishingType.Use use;
            try {
                use = FurnishingType.Use.valueOf(f.getString("use", "DECOR"));
            } catch (IllegalArgumentException e) {
                use = FurnishingType.Use.DECOR;
            }
            FurnishingType k = new FurnishingType(f.getString("type"), f.getString("name", null), f.getString("art", null),
                    f.getFloat("width", 1f), f.getFloat("depth", 1f), f.getFloat("fullFuel", 0f), f.getDouble("price", 0),
                    use, f.getInt("seats", 1), f.getFloat("relax", 0f), f.getString("verb", null));
            k.flat = f.getBoolean("flat", false);
            Furnishing placed = new Furnishing(k, f.getInt("x"), f.getInt("y"), f.getFloat("ox", 0f), f.getFloat("oy", 0f),
                    f.getFloat("rotation", 0f));
            placed.fuel = f.getFloat("fuel", k.fuel);
            Furnishings.restore(grid, placed);
        }

        CompartmentBuilder.rebuild(grid);
        for (Compartment c : grid.compartments) c.scaleContents(0.0);
        for (JsonValue c = data.get("compartments").child; c != null; c = c.next) {
            int idx = c.getInt("tile");
            Compartment room = grid.tiles[idx / w][idx % w].compartment;
            if (room == null) continue;
            room.oxygen += c.getDouble("oxygen");
            room.nitrogen += c.getDouble("nitrogen");
            room.carbonDioxide += c.getDouble("carbonDioxide");
            room.smoke += c.getDouble("smoke");
        }
        grid.gridDirty = true;
        return grid;
    }

    private static void writePipes(Json json, String name, Pipe[][] pipes) {
        json.writeArrayStart(name);
        for (int y = 0; y < pipes.length; y++) {
            for (int x = 0; x < pipes[y].length; x++) {
                if (pipes[y][x] == null) continue;
                json.writeObjectStart();
                json.writeValue("x", x);
                json.writeValue("y", y);
                json.writeObjectEnd();
            }
        }
        json.writeArrayEnd();
    }

    private static void readPipes(JsonValue list, Pipe[][] pipes) {
        if (list == null) return;
        for (JsonValue p = list.child; p != null; p = p.next) pipes[p.getInt("y")][p.getInt("x")] = new Pipe();
    }

    private static void readConduits(JsonValue list, EdgeConduit[][] conduits) {
        if (list == null) return;
        for (JsonValue c = list.child; c != null; c = c.next) conduits[c.getInt("y")][c.getInt("x")] = new EdgeConduit();
    }

    private static void readEdges(JsonValue list, EdgeWall[][] edges) {
        for (JsonValue e = list.child; e != null; e = e.next) {
            EdgeWall wall = new EdgeWall(WeightClass.valueOf(e.getString("weight")), e.getString("material"), e.getBoolean("bulkhead"));
            wall.integrity = e.getFloat("integrity");
            wall.bulkheadSealed = e.getBoolean("sealed");
            wall.patchedAt = e.getFloat("patch", 0f);
            edges[e.getInt("y")][e.getInt("x")] = wall;
        }
    }
}
