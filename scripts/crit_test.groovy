// Wounds the selected crew member critically, then hits them again, and prints how long they have
// left after each hit: what a further hit on someone already critical does to their time.
//   crit_test           (hits of 95, then 80)
//   crit_test 95, 75    (the second hit's points)
def first = args ? args[0] : 95
def second = args && args.length > 1 ? args[1] : 80
def c = crew()
heal()
println wound(first)
def before = c.bleedOut
println wound(second)
"Bleed out: ${Math.round(before)} s after the first hit, ${Math.round(c.bleedOut)} s after the second"
