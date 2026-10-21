// Fills the selected ship (or the player's) with one of each role, for watching who does what.
//   crew_up
def ship = selected?.ship ?: player
select(ship)
['engineer', 'marine', 'medic', 'crewman'].collect { role -> spawn(role) }
