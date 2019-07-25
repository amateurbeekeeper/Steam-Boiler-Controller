package steam.boiler.tests;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import steam.boiler.core.MySteamBoilerController;
import steam.boiler.model.LevelSensorModels;
import steam.boiler.model.PhysicalUnits;
import steam.boiler.model.PumpControllerModels;
import steam.boiler.model.PumpModels;
import steam.boiler.model.SteamBoilerModels;
import steam.boiler.model.SteamSensorModels;
import steam.boiler.util.SteamBoilerCharacteristics;

import static steam.boiler.tests.TestUtils.*;

import java.util.function.Function;

/**
 * These tests are designed to test the functional requirements of the steam boiler system.
 * Specifically, to ensure that it operates correctly under perfect conditions. That is, where
 * hardware failures do not occur.
 *
 * @author David J. Pearce
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SafetyTests {

  // =============================================================================
  // INITIALISATION
  // =============================================================================

  /**
   * Check steam boiler goes into emergency stop when steam level non-zero during initialisation.
   */
  @Test
  public void safetytest_01() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Break steam sensor
    model.setSteamSensor(new SteamSensorModels.StuckNegativeOne(model));
    // FIRST
    clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
    // DONE
  }

  /**
   * Check steam boiler goes into emergency stop during initialisation on failure
   * of water level detection unit, as detected by a negative reading.
   */
  @Test
  public void safetytest_02() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Break level sensor
    model.setLevelSensor(new LevelSensorModels.StuckNegativeOne(model));
    // FIRST
    clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
    // DONE
  }

  /**
   * Check steam boiler goes into emergency stop during initialisation on failure
   * of water level detection unit as detected by a reading which exceeds
   * capacity.
   */
  @Test
  public void safetytest_03() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Break level sensor
    model.setLevelSensor(new LevelSensorModels.Stuck(model, config.getCapacity() + 10));
    // FIRST
    clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
    // DONE
  }

  /**
   * Check emergency stop at *any time* for level sensor transmission failure.
   */
  @Test
  public void safetytest_04() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Check various time frames before transmission failure
    for (int t = 0; t != 120; ++t) {
      MySteamBoilerController controller = new MySteamBoilerController(config);
      PhysicalUnits model = new PhysicalUnits.Template(config).construct();
      model.setMode(PhysicalUnits.Mode.WAITING);
      // Clock system for a given amount of time. We're not expecting anything to go
      // wrong during this time.
      clockForWithout(t, controller, model, atleast(MODE_emergencystop));
      // Configure the level sensor to fail
      model.setLevelSensor(new LevelSensorModels.TxFailure(model));
      // FIRST
      clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
      // DONE
    }
  }

  /**
   * Check emergency stop at *any time* for steam sensor transmission failure.
   */
  @Test
  public void safetytest_05() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Check various time frames before transmission failure
    for (int t = 0; t != 120; ++t) {
      MySteamBoilerController controller = new MySteamBoilerController(config);
      PhysicalUnits model = new PhysicalUnits.Template(config).construct();
      model.setMode(PhysicalUnits.Mode.WAITING);
      // Clock system for a given amount of time. We're not expecting anything to go
      // wrong during this time.
      clockForWithout(t, controller, model, atleast(MODE_emergencystop));
      // Configure the steam sensor to fail
      model.setSteamSensor(new SteamSensorModels.TxFailure(model));
      // FIRST
      clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
      // DONE
    }
  }


  /**
   * Check emergency stop at *any time* for pump transmission failure.
   */
  @Test
  public void safetytest_06() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Check various time frames before transmission failure
    for (int t = 0; t != 120; ++t) {
      // Try each pump individually
      for (int i = 0; i != config.getNumberOfPumps(); ++i) {
        MySteamBoilerController controller = new MySteamBoilerController(config);
        PhysicalUnits model = new PhysicalUnits.Template(config).construct();
        model.setMode(PhysicalUnits.Mode.WAITING);
        // Clock system for a given amount of time. We're not expecting anything to go
        // wrong during this time.
        clockForWithout(t, controller, model, atleast(MODE_emergencystop));
        // Configure the steam sensor to fail
        model.setPump(i, new PumpModels.TxFailureAll(i, 0.0, model));
        // FIRST
        clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
        // DONE
      }
    }
  }

  /**
   * Check emergency stop at *any time* for controller transmission failure.
   */
  @Test
  public void safetytest_07() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Check various time frames before transmission failure
    for (int t = 0; t != 120; ++t) {
      // Try each pump in turn
      for (int i = 0; i != config.getNumberOfPumps(); ++i) {
        MySteamBoilerController controller = new MySteamBoilerController(config);
        PhysicalUnits model = new PhysicalUnits.Template(config).construct();
        model.setMode(PhysicalUnits.Mode.WAITING);
        // Clock system for a given amount of time. We're not expecting anything to go
        // wrong during this time.
        clockForWithout(t, controller, model, atleast(MODE_emergencystop));
        // Configure the pump to fail
        model.setPumpController(i, new PumpControllerModels.TxFailure(i, model));
        // FIRST
        clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
        // DONE
      }
    }
  }

  /**
   * Check emergency stop when water falls to minimum limit level. This is difficult to simulate
   * properly. This case is done by setting an evacuation rate above the maximum pump capacity and
   * forcing the valve open. This has to be done after initialisation as well, since otherwise it
   * would emergency stop for a different reason.
   */
  @Test
  public void safetytest_08() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    Function<Integer, Double> conversionModel = (Integer elapsed) -> SteamBoilerModels
        .linearSteamConversionModel(elapsed, 60000, config.getMaximualSteamRate());
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Under ideal conditions, should get here without problems. Now, break the steam boiler! With
    // four pumps at default 4L/s, that's a maximum filling of 16L/s. Therefore, evacuation needs to
    // be more than that.
    model.setBoiler(
        new SteamBoilerModels.ValveStuck(true, config.getCapacity(), 20.0, conversionModel));
    // At this point, we have to wait for the emergency stop event. This can take some time as the
    // boiler will have to empty. With max 500L at 20L/s, that requires a conservative worst-case of
    // 25s.
    clockUntil(25, controller, model, atleast(MODE_emergencystop));
  }

  /**
   * Check emergency stop when water falls to minimum limit level. This is difficult to simulate
   * properly. This case is done by breaking all pumps so that water cannot be refilled. This has to
   * be done after initialisation as well, since otherwise it would emergency stop for a different
   * reason.
   */
  @Test
  public void safetytest_09() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Break all pumps by forcing them closed.
    for (int i = 0; i != config.getNumberOfPumps(); ++i) {
      model.setPump(i, new PumpModels.StuckClosed(i, config.getPumpCapacity(i), model));
    }
    // At this point, we have to wait for the emergency stop event. This will take a long time as
    // the boiler will have to empty by exhausting steam only. At a maximum exhaust of 10L/s it will
    // take 50s to empty the entire boiler (assuming it was full, which it could not be)/
    clockUntil(50, controller, model, atleast(MODE_emergencystop));
  }

  /**
   * Check emergency stop when water rises above maximum limit level. This is difficult to simulate
   * properly. This case is done by having an aggressive pump capacity and locking the only pump
   * into the on position. In turn, this forces the system into degraded mode but it will not
   * emergency stop until the limit is reached. This has to be done after initialisation as well,
   * since otherwise it would emergency stop for a different reason.
   */
  @Test
  public void safetytest_10() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Require only one pump
    config = config.setNumberOfPumps(1, config.getPumpCapacity(0));
    config = config.setPumpCapacity(0, 20);
    //
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Under ideal conditions, should get here without problems. Now, we break the pump by forcing
    // it on and with an aggressive capacity.
    model.setPump(0, new PumpModels.SticksOpen(0, 20, model));
    model.getPump(0).open(); // now is stuck open
    // At this point, we have to wait for the emergency stop event. This can take some time as the
    // boiler will have to fill up. For a max limit of 400L at 20L/s this would take around 20s.
    // Note, however, that steam exhaust affects this calculation. That said, we also know we must
    // be above minimum normal limit.
    clockUntil(30, controller, model, atleast(MODE_emergencystop));
  }

  /**
   * Check emergency stop reached after simultaneous failure of both level and steam sensor. This
   * has to be done after initialisation as well, since otherwise it would emergency stop for a
   * different reason.
   */
  @Test
  public void safetytest_11() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Under ideal conditions, should get here without problems. Now, break both level and steam
    // sensor.
    model.setLevelSensor(new LevelSensorModels.StuckNegativeOne(model));
    model.setSteamSensor(new SteamSensorModels.StuckNegativeOne(model));
    // We should now immediately enter emergency stop!
    clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
  }


  /**
   * Check emergency stop reached after a staggered failure of both level and steam sensor. This has
   * to be done after initialisation as well, since otherwise it would emergency stop for a
   * different reason.
   */
  @Test
  public void safetytest_12() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Under ideal conditions, should get here without problems. Now, break the steam sensor forcing
    // the system into degraded mode.  It should be able to carry on for a while though like this.
    model.setSteamSensor(new SteamSensorModels.StuckNegativeOne(model));
    // Continue clocking the system. It should continue working.
    clockForWithout(120, controller, model, atleast(MODE_emergencystop));
    // Finally, break the level sensor at which point it should emergency stop.
    model.setLevelSensor(new LevelSensorModels.StuckNegativeOne(model));
    // We should now immediately enter emergency stop!
    clockOnceExpecting(controller, model, atleast(MODE_emergencystop));
  }

  // ======================================================================
  // Rescue Mode
  // ======================================================================

  // Potentially could add some tests here?
}
