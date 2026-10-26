# Old saves kept as fixtures

Saves made by earlier builds, gzipped, so tests can check they still load (`SaveSafetyGameTest`).
Don't regenerate them: their point is that they were made by old code.

- `format1-docked-career.json.gz`: format 1 (components keyed by Java class name), saved by main at
  `b4298fb3`, the last build before stable type ids. The designed start, docked at Highport with a
  freighter under way on the rails (the state `FullSaveRoundTripTest` builds), minified.

When the save format changes again, add a save from the last build before the change beside these.
