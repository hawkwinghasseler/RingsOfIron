# Nasa-punk word list

The style guide's promised table (section 4): how every word on a Nasa-punk instrument is written,
so that a player who learns a word once can read it on every screen. It covers the caps on
instruments: labels, codes, lenses, keys, tabs, tags and states. Prose (callouts, messages, the
log, hovers, briefings) is sentence case and spells words in full; only its units after a number
follow this table ("aft starboard gun shorted, back in 37 s").

Written from the variations' screens in round 2, checked against all of them in round 3, and kept
in step with the specs since.

## Rules

1. **One name for each thing.** A page, station, system, datum or state is written the same way on
   every screen. A page's soft key, its tab, its page head and anything that links to it carry one
   name: DAMAGE on the weapons seat's MFD key and on the reactor console's tab.
2. **Whole words unless a field can't hold one.** A short form is used only where a fixed field
   needs it (a P2 code column, a lens, a soft key, a label column), and only from this table.
3. **One short form for each word.** A word with a short form here takes it in every label, code,
   lens and tag. In a phrase that reads as words (a state, a callout's caps, a hint's verb) the
   word stays whole: `RNG 3.4 KM` but `OUT OF RANGE`; `MSL` in a threat row but
   `1 MISSILE INBOUND`.
4. **Nothing invented on a screen.** A new short form is added here first, with the field that
   forces it.
5. **Units** follow the value 4 units after it, in `dim` at 12, in the form below. Thousands take
   a thin space (`7 672 M/S`), never a comma.
6. **Keys** carry the key's own name: SPACE, TAB, ESC, ENTER (ENT on a cap too small for it), F1,
   W/A/S/D.
7. **Spelling follows the game's own text**, which is American: DEFENSE, point defense.

## Units, after a number

| Written | Means | Example |
|---|---|---|
| M, KM | metres, kilometres | `RNG 3.4 KM` |
| M/S, KM/S | speed | `CLOSING 12 M/S`, `7.67 KM/S` |
| KG | kilograms | `EMERG O2 52 KG` |
| MW | megawatts | `0 OF 3.0 MW` |
| KPA | kilopascals | `102 KPA` |
| % | percent, with no space | `88%`, `HEALTH 24%` |
| ° | degrees | `COURSE 220°` |
| S | seconds, for a duration | `SHOT 1.3 S`, `TOF 1.1 S` |
| D, H | days and hours, for a time left over a day, with no space | `1D 10H LEFT` |
| CR | credits | `3 400 CR` |
| M³ | cubic metres, for volume: a hold, a cargo lot, a crate (the game writes m3) | `HOLD 18 OF 40 M³` |
| × | a multiple, with the sign | warp `1×`, a camera's `×40`, a repeat `×3` |

## Time

| Form | Means | Example |
|---|---|---|
| `DAY n · HH:MM:SS` | The game clock: days since the campaign began, then the time. It replaces the guide's first MET clock, which was the same datum in other dress | `DAY 0 · 01:12:40` |
| `T−M:SS`, `T−H:MM:SS` | A countdown to an event, with a true minus (U+2212) | `T−12:04`, `T−1:10:00` |
| `M:SS` | The same countdown where a label or column already says what it counts to | `IMPACT 0:03`, `BACK IN 0:37`, P2's `3:40` |
| a bar | A window under 10 s that runs out (an armed guarded order) | X3's tow, armed |

Every countdown is M:SS or H:MM:SS, so a column of them scans: never `21 S` beside `3:40`.
Durations (a shot's flight, a cooldown) are seconds with `S`.
A callout carries no countdown (`aft starboard gun shorted, resets itself.`): a number spoken once
goes stale while the line holds, so the time stays on the instrument that counts it.
An impact time counts the missile's burn, so it runs at the clock's speed (the specs ask for it as
new data; the game's own figure, a coasting estimate, runs fast).

## Flight

| Written | Means | Never |
|---|---|---|
| ΔV | delta-v | DV, DELTA-V |
| PROP | propellant | FUEL (a cargo, not the ship's propellant) |
| AP, PE | apoapsis, periapsis | |
| ALT | altitude | |
| NAV | navigation: the nav target, its page and its station | |
| RCS | the reaction control thrusters (`MAIN` is the main engine) | THR |
| CPA | closest approach | CLOSEST |
| ATT | attitude, in `ATT HOLD` | |
| ORBIT, TARGET, MANUAL | whole words | ORB, TGT, MAN |

## Combat

| Written | Means | Never |
|---|---|---|
| RNG | range, as a label | |
| CLOSING / OPENING | range rate, by its sign | RR |
| REL | speed relative to own ship | |
| BRG | bearing, as a label | |
| MSL | missile, in a row or a label | |
| PD | point defense, as a label (`PD AUTO`, `PD ON`) | |
| TOF | a shot's time of flight | |
| IMPACT | time to impact | TTI |
| HIT | hit chance: `HIT 81%` | |

## Ship

| Written | Means | Never |
|---|---|---|
| FWD, STBD | forward, starboard (AFT and PORT are whole words): `FWD PORT`, `AFT STBD` | |
| HULL | hull integrity | STRUC |
| POWER | whole, on every key, lens and code | PWR |
| LIFE SUP | life support | LIFE |
| ATMOS | the atmosphere page | ATM |
| O2, N2 | oxygen, nitrogen | |
| EMERG | emergency: `EMERG O2` | EMERGENCY on an instrument |
| CRIT | critical: `CREW CRIT` | |
| SYS | system, in a count: `1 SYS` (the page is SYSTEMS; P2's code is `SYSTEM DOWN`) | |
| PRESS | pressure, in a code | |
| APPR | approach, in a code: `FAST APPR` (the helm order is APPROACH) | |
| SALV | salvage | |
| SHIELD, SHIELDS | whole words | SHLD |
| DAMAGE | whole: `HULL DAMAGE` fits P2's code column at 13 bold | DMG |
| REACTOR | whole | RCTR |
| CLEARED | dock clearance granted | CLR |
| MASTER WARN, MASTER CAUT | the two master lenses | MSTR, MASTER WARNING |

## Names

| Kind | Written |
|---|---|
| Pages (on keys, tabs and heads alike) | NAV, PLAN, HELM, DOCK, SENSORS, WEAPONS, DEFENSE, DAMAGE, POWER, ATMOS, SYSTEMS, TANKS, SHIELDS, COMMS, CARGO, CREW, CONTRACTS, FLIGHT |
| Stations (board slots, ticker tags, console plates) | HELM, NAV, TAC, ENG, COMMS, CMD |
| Message sources (feed tags) | HELM, GUNNERY, ENG, MEDICAL, CREW, STATION, SPONSOR, COMMS, LOG, BOARDING, SHIELDS |
| Mounts | NOSE, FWD PORT, FWD STBD, AFT PORT, AFT STBD |
| MFD function keys | PIN, FOLD, RTN (return), DCLTR (declutter), TFC (traffic), XFR (transfer), CTR (centre), BRT (brightness), XTIE (crosstie), RNG+ and RNG− (scope range) |
| Cameras | OPT n (an optical camera), then what it shows: `OPT 1  TARGET` |

On a 5-key MFD the page names keep their whole spelling: PIN and FOLD are short function keys and
take 0.55 of a page key's room each, which gives SENSORS, WEAPONS and DEFENSE 66 units apiece.

## P2 codes in this style

The context spec's codes, written to these rules. Each fits P2's code column, which version 6 sets at
11 characters (104 units at 13 bold; SYSTEM DOWN, the longest, is 98):

`FIRE`, `BREACH`, `VACUUM`, `CREW CRIT`, `CREW AIR`, `BOARDERS`, `LATCHED ON`, `NO POWER`,
`EMERG O2`, `SYSTEM DOWN`, `PRESS LOW`, `SMOKE`, `HULL DAMAGE`, `POWER LOW`, `SHIELD DOWN`,
`SHIELD WEAK`, `O2 LOW`, `PROP LOW`, `PLAN SHORT`, `CREW OUT`, `NO HELM`, `FAST APPR`,
`DOCK STRAIN`.

Version 6 of the context spec took these spellings: DMG, SHLD, STRN and SYS became whole words, and
the four "low" codes take one order, the thing first (PROP LOW, POWER LOW, O2 LOW, PRESS LOW).

## Words in these documents, not on screen

DU (display unit), MFD (multifunction display), PFD (primary flight display), FMA (flight mode
annunciator), EICAS (the engine indicating and crew alerting list P2 is drawn as), DCM (the suit's
chest display), CCTV (a camera inset), P/N (a part number, on a unit's plate). EVA is both: a lamp
on screen and a word here.
