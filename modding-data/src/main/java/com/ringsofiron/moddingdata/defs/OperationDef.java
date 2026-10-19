package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * A short chain of linked contracts a faction offers once the player holds a rank with the
 * sponsor; the last step pays a reward of its own. {@code operation.<id>}.
 * <pre>
 * [operation.clear_the_lanes]
 * name = "Clear the Lanes"
 * text = "Pirates are working the depot run."   # a line for the mission board
 * giver = "patrol"                 # faction id; empty means the sponsor
 * rank = "lieutenant"              # sponsor rank that unlocks it
 * reward = 12000.0                 # credits when the last step is done, on top of each step's own pay
 * standing = 10.0                  # standing with the giver then
 * steps = ["patrol", "recon", "bounty"]
 * </pre>
 * A step is a contract type: {@code patrol}, {@code recon} (find a pirate where it lurks),
 * {@code bounty} (on the pirate a recon found, else any the giver is at war with), {@code escort},
 * {@code convoy}, {@code delivery}, {@code intel} (find a pirate and follow it home to the pirates'
 * base; skipped once the base is known) or {@code raid} (on the base, once found).
 */
public class OperationDef {
    public String id;
    public String name;
    public String text = "";
    public String giver = "";
    public String rank;
    public double reward;
    public float standing;
    public List<String> steps = new ArrayList<>();
}
