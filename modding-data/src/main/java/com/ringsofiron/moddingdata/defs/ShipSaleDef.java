package com.ringsofiron.moddingdata.defs;

/**
 * A ship a faction's yards sell. {@code ship_sale.<id>}. Every sale is also held to the player's
 * rank ladder: a rank flies no chassis larger than its {@code max_chassis}.
 * <pre>
 * [ship_sale.patrol_gunship]
 * ship = "gunship_heavy"           # the ship id in ships.toml
 * seller = "patrol"                # the faction that sells it, at its shipyards
 * price = 360000.0                 # credits, before the trade-in of the ship the player flies
 * rank = "lieutenant_commander"    # rank on the sponsor's ladder needed to buy it (optional)
 * standing = 0.0                   # standing with the seller needed to buy it
 * </pre>
 */
public class ShipSaleDef {
    public String id;
    public String ship;
    public String seller;
    public double price;
    public String rank;
    public float standing;
}
