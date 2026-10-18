package com.ringsofiron.interiorgrid;

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
                json.writeObjectEnd();
            }
        }
        json.writeArrayEnd();

        writeEdges(json, "edgeH", grid.edgeH);
        writeEdges(json, "edgeV", grid.edgeV);

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
            json.writeObjectEnd();
        }
        json.writeArrayEnd();

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

        // Pieces first: placing one resets its tiles, which the tile state below then restores
        for (JsonValue p = data.get("pieces").child; p != null; p = p.next) {
            HullPiece piece = new HullPiece(HullPiece.Shape.valueOf(p.getString("shape")), p.getInt("x"), p.getInt("y"),
                    p.getInt("width"), p.getInt("height"), p.getInt("rotation"),
                    WeightClass.valueOf(p.getString("weight")), p.getString("material"));
            grid.placeHullPiece(piece);
            piece.integrity = p.getFloat("integrity");
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
        }

        readEdges(data.get("edgeH"), grid.edgeH);
        readEdges(data.get("edgeV"), grid.edgeV);

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

    private static void readEdges(JsonValue list, EdgeWall[][] edges) {
        for (JsonValue e = list.child; e != null; e = e.next) {
            EdgeWall wall = new EdgeWall(WeightClass.valueOf(e.getString("weight")), e.getString("material"), e.getBoolean("bulkhead"));
            wall.integrity = e.getFloat("integrity");
            wall.bulkheadSealed = e.getBoolean("sealed");
            edges[e.getInt("y")][e.getInt("x")] = wall;
        }
    }
}
