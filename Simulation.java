package steam.boiler.core;

import steam.boiler.simulator.SimulationCharacteristicsDialog;
import steam.boiler.util.SteamBoilerCharacteristics;

/**
 * Provides a simple way to fire up the simulation interface using a given
 * controller.
 *
 * @author David J. Pearce
 *
 */
public class Simulation {
  /**
   * Simulation.
   * 
   * @param args
   *        Arguments.
   */
  public static void main(String[] args) {
    // Begin the simulation by opening the characteristics selection dialog.
    new SimulationCharacteristicsDialog((SteamBoilerCharacteristics cs) -> {
      return new MySteamBoilerController(cs);
    });
  }
}
