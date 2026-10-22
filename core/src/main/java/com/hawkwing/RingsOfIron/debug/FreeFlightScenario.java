package com.hawkwing.RingsOfIron.debug;

/**
 * The designed start as {@link com.hawkwing.RingsOfIron.game.GameScenario#start} leaves it, before the career docks
 * the player at the sponsor's home: the corvette free in orbit near Highport, the raider and traffic about. The
 * plainest scenario, and the one to copy.
 */
final class FreeFlightScenario implements DebugScenario {
    @Override
    public void setUp(ScenarioSetup setup) {
        setup.exteriorView();
        setup.notice("Free flight: undocked by Highport. F3 for the debug menu");
    }
}
