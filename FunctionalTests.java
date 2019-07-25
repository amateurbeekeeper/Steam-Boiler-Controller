package steam.boiler.tests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static steam.boiler.tests.TestUtils.LEVEL_FAILURE_DETECTION;

import static steam.boiler.tests.TestUtils.MODE_degraded;
import static steam.boiler.tests.TestUtils.MODE_emergencystop;
import static steam.boiler.tests.TestUtils.MODE_initialisation;
import static steam.boiler.tests.TestUtils.MODE_normal;
import static steam.boiler.tests.TestUtils.MODE_rescue;
import static steam.boiler.tests.TestUtils.PROGRAM_READY;
import static steam.boiler.tests.TestUtils.PUMP_CONTROL_FAILURE_DETECTION;
import static steam.boiler.tests.TestUtils.PUMP_FAILURE_DETECTION;
import static steam.boiler.tests.TestUtils.STEAM_FAILURE_DETECTION;
import static steam.boiler.tests.TestUtils.atleast;
import static steam.boiler.tests.TestUtils.clockForWithout;
import static steam.boiler.tests.TestUtils.clockOnceExpecting;
import static steam.boiler.tests.TestUtils.clockUntil;
import static steam.boiler.tests.TestUtils.exactly;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import steam.boiler.core.MySteamBoilerController;
import steam.boiler.model.LevelSensorModels;
import steam.boiler.model.PhysicalUnits;
import steam.boiler.model.PumpControllerModels;
import steam.boiler.model.PumpModels;
import steam.boiler.model.SteamSensorModels;
import steam.boiler.util.SteamBoilerCharacteristics;

/**
 * These tests are designed to test the functional requirements of the steam boiler system.
 * Specifically, to ensure that it operates correctly under perfect conditions. That is, where
 * hardware failures do not occur.
 *
 * @author David J. Pearce
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FunctionalTests {

  // =====================================================================
  // Initialisation
  // =====================================================================

  /**
   * Check controller produces expected MODE_m message.
   */
  @Test
  public void test_initialisation_01() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // FIRST
    clockOnceExpecting(controller, model, exactly(MODE_initialisation));
    // DONE
  }

  /**
   * Check steam boiler goes straight into READY state. This requires that the amount of water in
   * the boiler is enough already (otherwise, we'd expect it to try and fill it up, or lower it).
   */
  @Test
  public void test_initialisation_02() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // Pump in enough water to ensure can go straight to ready
    double midpoint = average(config.getMinimalNormalLevel(), config.getMaximalNormalLevel());
    model.getBoiler().pumpInWater(midpoint);
    // FIRST
    model.setMode(PhysicalUnits.Mode.WAITING);
    clockOnceExpecting(controller, model, atleast(MODE_initialisation, PROGRAM_READY));
    // DONE
  }

  /**
   * Check when steam boiler takes time producing ready message. That is, the program controller
   * must be able to handle a longer handshake. In principle, this could be arbitrarily long. Again,
   * we need enough water in the boiler to start with to ensure that the controller can go straight
   * to ready.
   */
  @Test
  public void test_initialisation_03() {
    // FIXME: could be merged into above with loop
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // Pump in enough water to ensure can go straight to ready
    double midpoint = average(config.getMinimalNormalLevel(), config.getMaximalNormalLevel());
    model.getBoiler().pumpInWater(midpoint);
    // FIRST
    clockOnceExpecting(controller, model, atleast(MODE_initialisation));
    //
    model.setMode(PhysicalUnits.Mode.WAITING);
    clockOnceExpecting(controller, model, atleast(MODE_initialisation, PROGRAM_READY));
    // DONE
  }

  /**
   * Check steam boiler sends PROGRAM_READY and enters NORMAL mode. Again, need enough water in the
   * boiler.
   */
  @Test
  public void test_initialisation_04() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // Pump in enough water to ensure can go straight to ready
    double midpoint = average(config.getMinimalNormalLevel(), config.getMaximalNormalLevel());
    model.getBoiler().pumpInWater(midpoint);
    // FIRST
    model.setMode(PhysicalUnits.Mode.WAITING);
    clockOnceExpecting(controller, model, exactly(MODE_initialisation, PROGRAM_READY));
    // SECOND
    clockOnceExpecting(controller, model, atleast(MODE_normal));
    // DONE
  }

  /**
   * Check steam boiler opens valve to drain water before closing it and entering READY state.
   */
  @Test
  public void test_initialisation_05() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // Set water level above normal maximum
    model.getBoiler().pumpInWater(config.getMaximalLimitLevel());
    // Wait at most 60s for controller to get to READY state
    model.setMode(PhysicalUnits.Mode.WAITING);
    clockUntil(60, controller, model, atleast(PROGRAM_READY));
    // At this point, level should be within normal bounds
    assertTrue(model.getBoiler().getWaterLevel() <= config.getMaximalNormalLevel());
    assertTrue(model.getBoiler().getWaterLevel() >= config.getMinimalNormalLevel());
    // DONE
  }

  /**
   * Check steam boiler starts pump to fill water up to minimum level before entering READY state.
   */
  @Test
  public void test_initialisation_06() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // Wait at most 60s for controller to get to READY state
    model.setMode(PhysicalUnits.Mode.WAITING);
    clockUntil(60, controller, model, atleast(PROGRAM_READY));
    // At this point, level should be within normal bounds
    assertTrue(model.getBoiler().getWaterLevel() <= config.getMaximalNormalLevel());
    assertTrue(model.getBoiler().getWaterLevel() >= config.getMinimalNormalLevel());
  }

  // CHECK: different boiler characteristics; e.g. very aggressive pumps; very tight limits.
  // CHECK physical failure during initialisation
  // CHECK initialisation for multiple pumps

  // =====================================================================
  // Normal
  // =====================================================================

  /**
   * Check that steam boiler operates correctly in normal mode over a range of times with three
   * pumps. Observe that, with fewer than three pumps at with the default characteristics, the
   * system cannot maintain the water level.
   */
  @Test
  public void test_normal_operation_01() {
    // Explore various time frames for correct operation
    for (int t = 20; t != 560; ++t) {
      test_normal_operation(t, 3);
    }
  }

  /**
   * Check that steam boiler operates correctly in normal mode over a range of times with four
   * pumps. Observe that, with fewer than three pumps at with the default characteristics, the
   * system cannot maintain the water level.
   */
  @Test
  public void test_normal_operation_02() {
    // Explore various time frames for correct operation
    for (int t = 20; t != 560; ++t) {
      test_normal_operation(t, 4);
    }
  }

  /**
   * Check that steam boiler operates correctly in normal mode over a range of times with five
   * pumps. Observe that, with fewer than three pumps at with the default characteristics, the
   * system cannot maintain the water level.
   */
  @Test
  public void test_normal_operation_03() {
    // Explore various time frames for correct operation
    for (int t = 20; t != 560; ++t) {
      test_normal_operation(t, 5);
    }
  }

  /**
   * Check that steam boiler operates correctly in normal mode over a range of times with six pumps.
   * Observe that, with fewer than three pumps at with the default characteristics, the system
   * cannot maintain the water level.
   */
  @Test
  public void test_normal_operation_04() {

    // Explore various time frames for correct operation
    for (int t = 20; t != 560; ++t) {
      test_normal_operation(t, 6);
    }
  }

  /**
   * Operate the steam boiler system for a given amount of time, and with a given number of pumps.
   * Since the system it otherwise ideal, we're expecting the water level to be held within the
   * normal range without problem.
   *
   * @param time
   *          The time (in s) to operate the boiler before checking the levels.
   * @param numberOfPumps
   *          The number of pumps to use in this configuration.
   * @param config
   *          The boiler characteristics to use for this run.
   */
  private void test_normal_operation(int time, int numberOfPumps) {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    config = config.setNumberOfPumps(numberOfPumps, config.getPumpCapacity(0));
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(time, controller, model, atleast(MODE_emergencystop));
    // In an ideal setting, we expect the system to keep the level within the normal range at
    // all times. Therefore, check water level is indeed within normal range.
    if (model.getBoiler().getWaterLevel() > config.getMaximalLimitLevel()) {
      fail("Water level above limit maximum (after " + time + "s with " + numberOfPumps
          + " pumps)");
    }
    if (model.getBoiler().getWaterLevel() < config.getMinimalLimitLevel()) {
      fail("Water level below limit minimum (after " + time + "s with " + numberOfPumps
          + " pumps)");
    }
  }

  // =====================================================================
  // Degraded
  // =====================================================================

  /**
   * Check controller enters degraded mode after obvious steam sensor failure. This has to be done
   * after initialisation as well, since otherwise it would emergency stop.
   */
  @Test
  public void test_degraded_operation_01() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Now, break the level sensor in an obvious fashion.
    model.setSteamSensor(new SteamSensorModels.StuckNegativeOne(model));
    //
    clockOnceExpecting(controller, model, atleast(MODE_degraded, STEAM_FAILURE_DETECTION));
  }

  /**
   * Check controller enters degraded mode after obvious steam sensor failure. This has to be done
   * after initialisation as well, since otherwise it would emergency stop.
   */
  @Test
  public void test_degraded_operation_02() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Now, break the level sensor in an obvious fashion.
    model.setSteamSensor(new SteamSensorModels.Stuck(model,config.getCapacity()));
    //
    clockOnceExpecting(controller, model, atleast(MODE_degraded, STEAM_FAILURE_DETECTION));
  }

  /**
   * Check controller enters degraded mode after obvious pump failure. This is difficult to simulate
   * properly, since we don't know the order in which actually use pumps. Thus, we could break a
   * pump which isn't used by the controller and, hence, isn't detected. To work around this, we
   * force a scenario where all pumps are required for normal operation. Thus, when one breaks, it
   * must eventually be detected.
   */
  @Test
  public void test_degraded_operation_03() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Configure only two pumps with combined capacity of 8L, which is not enough when steam is at
    // full exhaust.
    config = config.setNumberOfPumps(2, config.getPumpCapacity(0));
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.getBoiler().pumpInWater(250);
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. This is tricky, as we don't want to allow an
    // emergency stop to happen. Difference between min normal and min limit is 100L. Using both
    // pumps fully puts in 8L/s, whilst 10L/s is taken out in exhaust. Therefore, 2L/s drop in water
    // level and to lose 100L would take 50s.
    clockForWithout(25, controller, model, atleast(MODE_emergencystop));
    // Now, break the pump by fixing it closed.
    model.setPump(0, new PumpModels.StuckClosed(0, 0, model));
    // System should notice this relatively quickly and respond.
    clockUntil(60, controller, model, atleast(MODE_degraded, PUMP_FAILURE_DETECTION(0)));
  }

  /**
   * Check controller enters degraded mode after obvious pump controller failure. This is difficult
   * to simulate properly, since we don't know the order in which actually use pumps. Thus, we could
   * break a pump which isn't used by the controller and, hence, isn't detected. To work around
   * this, we force a scenario where all pumps are required for normal operation. Thus, when one
   * breaks, it must eventually be detected.
   */
  @Test
  public void test_degraded_operation_04() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Configure only two pumps with combined capacity of 8L, which is not enough when steam is at
    // full exhaust.
    config = config.setNumberOfPumps(2, config.getPumpCapacity(0));
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.getBoiler().pumpInWater(250);
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. This is tricky, as we don't want to allow an
    // emergency stop to happen. Difference between min normal and min limit is 100L. Using both
    // pumps fully puts in 8L/s, whilst 10L/s is taken out in exhaust. Therefore, 2L/s drop in water
    // level and to lose 100L would take 50s.
    clockForWithout(25, controller, model, atleast(MODE_emergencystop));
    // Now, break the pump controller by fixing it closed.
    model.setPumpController(0, new PumpControllerModels.StuckOff(0, model));
    // System should notice this relatively quickly and respond.
    clockUntil(60, controller, model, atleast(MODE_degraded, PUMP_CONTROL_FAILURE_DETECTION(0)));
  }

  /**
   * Check controller enters degraded mode after obvious pump controller failure. This is difficult
   * to simulate properly, since we don't know the order in which actually use pumps. Thus, we could
   * break a pump which isn't used by the controller and, hence, isn't detected. To work around
   * this, we force a scenario where all pumps are required for normal operation. Thus, when one
   * breaks, it must eventually be detected.
   */
  @Test
  public void test_degraded_operation_05() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Configure only three pumps with combined capacity of 12L, which is just enough when steam is
    // at full exhaust. Thus, all pumps must eventually be in use.
    config = config.setNumberOfPumps(3, config.getPumpCapacity(0));
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time.
    clockForWithout(25, controller, model, atleast(MODE_emergencystop));
    // Now, break the pump controller by fixing it closed.
    model.setPumpController(0, new PumpControllerModels.StuckOff(0, model));
    // At this point, we can't tell how long it will be before the system realises the pump
    // controller is broken. For example, if the pump is currently off then there will be no
    // discrepancy between the controller and the pump. Eventually, the pump will come back on and
    // then the failure will be detected.
    clockUntil(120, controller, model, atleast(MODE_degraded));
  }

  /**
   * Check controller is still able to operate correctly in the face of an obvious pump failure.
   * This is only possible in scenarios where this is surplus pump capacity. For example, by default
   * pumps have a capacity of 4L/s and the maximum steam exhaust is 10L/s. Therefore, three pumps
   * are needed for correct operation and any additional pumps provide redundancy. In principle,
   * when there are five or more pumps we could also check correct operation in the case of multiple
   * pump failures.
   */
  @Test
  public void test_degraded_operation_06() {
    // Explore various time frames and pump combinations for correct operation
    for (int time = 20; time != 560; ++time) {
      for (int numberOfPumps = 4; numberOfPumps <= 6; numberOfPumps++) {
        for (int failingPump = 0; failingPump < numberOfPumps; ++failingPump) {
          SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
          // Configure the given number of pumps
          config = config.setNumberOfPumps(numberOfPumps, config.getPumpCapacity(0));
          MySteamBoilerController controller = new MySteamBoilerController(config);
          PhysicalUnits model = new PhysicalUnits.Template(config).construct();
          //
          test_degraded_operation(controller, config, model, time, failingPump);
        }
      }
    }
  }

  /**
   * Check controller recognizes move back from degraded to normal mode, after a component repair.
   */
  @Test
  public void test_degraded_operation_07() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    // Configure the given number of pumps
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    // 60 seconds with a pump failure should push us into degraded mode
    test_degraded_operation(controller, config, model, 60, 0);
    // Fix the broken pump
    model.setPump(0, new PumpModels.Ideal(0, config.getPumpCapacity(0), model));
    // Signal to physical units it's been repaired
    model.setPumpStatus(0, PhysicalUnits.ComponentStatus.REPAIRED);
    // System should immediately notice and put us back into normal mode
    clockOnceExpecting(controller, model, atleast(MODE_normal));
  }

  private void test_degraded_operation(MySteamBoilerController controller,
      SteamBoilerCharacteristics config, PhysicalUnits model, int time, int... failingPumps) {
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Configure the broken pumps, which are failing from the outset.
    for (int i = 0; i != failingPumps.length; ++i) {
      int id = failingPumps[i];
      model.setPump(id, new PumpModels.StuckClosed(id, 0.0, model));
    }
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(time, controller, model, atleast(MODE_emergencystop));
    // Even in this degraded setting, we expect the system to keep the level within the normal range
    // at all times. Therefore, check water level is indeed within normal range.
    if (model.getBoiler().getWaterLevel() > config.getMaximalLimitLevel()) {
      fail("Water level above limit maximum (after " + time + "s with " + config.getNumberOfPumps()
          + " pumps)");
    }
    if (model.getBoiler().getWaterLevel() < config.getMinimalLimitLevel()) {
      fail("Water level below limit minimum (after " + time + "s with " + config.getNumberOfPumps()
          + " pumps)");
    }
  }

  // =====================================================================
  // Rescue
  // =====================================================================

  /**
   * Check controller enters rescue mode after obvious level sensor failure. This has to be done
   * after initialisation as well, since otherwise it would emergency stop.
   */
  @Test
  public void test_rescue_mode_01() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Now, break the level sensor in an obvious fashion.
    model.setLevelSensor(new LevelSensorModels.StuckNegativeOne(model));
    //
    clockOnceExpecting(controller, model, atleast(MODE_rescue, LEVEL_FAILURE_DETECTION));
  }

  /**
   * Check controller enters degraded mode after obvious level sensor failure. This has to be done
   * after initialisation as well, since otherwise it would emergency stop.
   */
  @Test
  public void test_rescue_mode_02() {
    SteamBoilerCharacteristics config = SteamBoilerCharacteristics.DEFAULT;
    MySteamBoilerController controller = new MySteamBoilerController(config);
    PhysicalUnits model = new PhysicalUnits.Template(config).construct();
    model.setMode(PhysicalUnits.Mode.WAITING);
    // Clock system for a given amount of time. We're not expecting anything to go
    // wrong during this time.
    clockForWithout(240, controller, model, atleast(MODE_emergencystop));
    // Now, break the level sensor in an obvious fashion.
    model.setLevelSensor(new LevelSensorModels.Stuck(model,config.getCapacity()));
    //
    clockOnceExpecting(controller, model, atleast(MODE_rescue, LEVEL_FAILURE_DETECTION));
  }

  // NOTE: Seems like there are most test we could consider here. For example, moving back to normal
  // or degraded more. Likewise, running the system in rescue more for some amount of time to check
  // that it manages to keep within the minimal/maximal limit levels, etc.

  // =====================================================================
  // Helpers
  // =====================================================================

  /**
   * Compute the average (mean) of a sequence of values.
   *
   * @param values
   *          An array of values to be averaged.
   * @return The mean of the given values.
   */
  public static double average(double... values) {
    double total = 0;
    for (int i = 0; i != values.length; ++i) {
      total += values[i];
    }
    return total / values.length;
  }
}
