# Console scripts

Every `.groovy` file in this folder (and the folders in it) can be run from the developer console,
which opens with **~** in the game. A script in this folder itself runs by typing its name, with
any arguments after it; one in a subfolder runs with `run`:

```
heal_crew                  // scripts/heal_crew.groovy
fire_drill 3               // scripts/fire_drill.groovy, with args[0] = 3
run 'examples/whatever'    // scripts/examples/whatever.groovy
scripts()                  // lists them all
```

A script runs like a line typed into the console: the same commands (`kill()`, `heal()`,
`wound(95)`, `fire()`, `spawn('medic')` ... `help()` lists them), the same variables (`selected`,
`player`, `avatar`, `world`, `game`), and whatever earlier lines set without `def`. Arguments arrive
in `args`. A script can add commands of its own with `console.define(name, usage, help, closure)`;
they stay until the game is closed.

`autoexec.groovy`, if you make one, runs whenever a game starts, so it is the place for commands
you want every time. It is not checked in, so each developer keeps their own.

The game runs from `assets/`, so it finds this folder as `../scripts`. Start the game with
`-Dconsole.scripts=<folder>` to use another one.

Scripts can do anything the game can; run only scripts you trust.
