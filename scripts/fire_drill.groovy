// Starts fires aboard the selected ship (or the player's), to watch the crew put them out.
//   fire_drill       (one fire)
//   fire_drill 4     (four)
def ship = selected?.ship ?: player
def n = args ? args[0] as int : 1
n.times { println fire(ship) }
