// Heals everyone aboard the selected ship (or the player's), and brings back the dead.
//   heal_crew
def ship = selected?.ship ?: player
aboard(ship).each { revive(it) }
"Everyone aboard ${ship == player ? 'your ship' : 'the selected ship'} is on their feet"
