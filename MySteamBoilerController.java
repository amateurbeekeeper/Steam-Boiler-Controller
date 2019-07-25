package steam.boiler.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import org.eclipse.jdt.annotation.NonNull;

import steam.boiler.model.SteamBoilerController;
import steam.boiler.util.Mailbox;
import steam.boiler.util.Mailbox.Message;
import steam.boiler.util.Mailbox.MessageKind;
import steam.boiler.util.SteamBoilerCharacteristics;

/**
 * Victoria University of Wellington School of Engineering and Computer Science.
 * SWEN326: Safety-Critical Systems Assignment 1 Due: Monday 1st April @ 23:59
 * 
 * @author jamesrowles
 *
 */
public class MySteamBoilerController implements SteamBoilerController {

  /**
   * Captures the various modes in which the controller can operate.
   *
   * @author David J. Pearce
   *
   */
  @SuppressWarnings("javadoc")
  private enum State {
    WAITING, READY, NORMAL, DEGRADED, RESCUE, EMERGENCY_STOP
  }

  /**
   * Records the configuration characteristics for the given boiler problem.
   */
  private final SteamBoilerCharacteristics config;

  /**
   * Identifies the current mode in which the controller is operating.
   */
  private State mode = State.WAITING;

  /**
   * Physical unit failures.
   */
  private List<Message> failures;

  /**
   * Number of pumps on at current cycle.
   */
  private int pumps;

  /**
   * Expected pump states.
   */
  private boolean[] pumpExpectedState;

  /**
   * Expected pump controller states.
   */
  private boolean[] controllerExpectedState;

  /**
   * The expected water level.
   */
  private double expectedWaterLevel;

  /**
   * Construct a steam boiler controller for a given set of characteristics.
   *
   * @param configuration
   *          The boiler characteristics to be used.
   */
  public MySteamBoilerController(SteamBoilerCharacteristics configuration) {
    config = configuration;
    pumps = 0;
    expectedWaterLevel = 0.0;
    failures = new ArrayList<>();
    pumpExpectedState = new boolean[this.config.getNumberOfPumps()];
    controllerExpectedState = new boolean[this.config.getNumberOfPumps()];
  }

  /**
   * This message is displayed in the simulation window, and enables a limited
   * form of debug output. The content of the message has no material effect on
   * the system, and can be whatever is desired. In principle, however, it should
   * display a useful message indicating the current state of the controller.
   *
   * @return string of the current mode
   */
  @Override
  public String getStatusMessage() {
    return mode.toString();
  }

  /**
   * Process a clock signal which occurs every 5 seconds. This requires reading
   * the set of incoming messages from the physical units and producing a set of
   * output messages which are sent back to them.
   *
   * @param incoming
   *          The set of incoming messages from the physical units.
   * @param outgoing
   *          Messages generated during the execution of this method should be
   *          written here.
   */
  @Override
  public void clock(Mailbox incoming, Mailbox outgoing) {
    if (transmissionFailure(incoming)) {
      this.mode = State.EMERGENCY_STOP;
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.EMERGENCY_STOP));

    } else if (mode == State.WAITING || mode == State.READY) {
      init(incoming, outgoing);
    } else if (mode == State.NORMAL || mode == State.DEGRADED) {

      predictPumps(incoming, outgoing);
      failureAnalysis(incoming);

      if (failures.contains(new Message(MessageKind.LEVEL_FAILURE_DETECTION))) {
        this.mode = State.RESCUE;
        outgoing.send(new Message(MessageKind.LEVEL_FAILURE_DETECTION));
        outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.RESCUE));

      } else {

        if (failures.size() > 0 || this.mode == State.DEGRADED) {
          outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.DEGRADED));
          this.mode = State.DEGRADED;

          resolveObservations(incoming, outgoing);

          if (failures.size() == 0) {
            this.mode = State.NORMAL;
          }

        } else if (this.mode == State.NORMAL) {
          this.mode = State.NORMAL;
          outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.NORMAL));

        }

        activatePumps(pumps, outgoing);
      }

    } else if (this.mode == State.RESCUE) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.RESCUE));

    } else if (this.mode == State.EMERGENCY_STOP) {
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.EMERGENCY_STOP));
    }

  }

  /**
   * Initalises the steam boiler.
   * 
   * @param outgoing
   *          The mailbox to send to.
   * @param incoming
   *          The mailbox to search through.
   */
  public void init(Mailbox incoming, Mailbox outgoing) {
    Message[] pumpStateMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Message steamMessage = extractOnlyMatch(MessageKind.STEAM_v, incoming);
    Message physUnits = extractOnlyMatch(MessageKind.PHYSICAL_UNITS_READY, incoming);
    Message waitMessage = (extractOnlyMatch(MessageKind.STEAM_BOILER_WAITING, incoming));
    double level = extractOnlyMatch(MessageKind.LEVEL_v, incoming).getDoubleParameter();

    outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.INITIALISATION));

    if (!(waitMessage != null || mode == State.READY)) {
      return;
    }

    if (steamMessage.getDoubleParameter() != 0.0) {
      this.mode = State.EMERGENCY_STOP;
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.EMERGENCY_STOP));

    } else if (!validLevelReading(level)) {
      this.mode = State.EMERGENCY_STOP;
      outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.EMERGENCY_STOP));

    } else if (level > config.getMaximalNormalLevel()) {
      outgoing.send(new Message(MessageKind.VALVE));

    } else if (level <= config.getMinimalNormalLevel()) {
      activatePumps(config.getNumberOfPumps(), outgoing);

    } else if (level <= config.getMaximalNormalLevel() && level >= config.getMinimalNormalLevel()) {
      if (physUnits == null) {
        this.mode = State.READY;

        for (int i = 0; i < config.getNumberOfPumps(); i++) {
          if (pumpStateMessages[i].getBooleanParameter() == true) {
            outgoing.send(new Message(MessageKind.CLOSE_PUMP_n, i));
          }
        }

        outgoing.send(new Message(MessageKind.PROGRAM_READY));

      } else {
        failureAnalysis(incoming);

        if (failures.size() > 0) {
          failures.clear();
          this.mode = State.DEGRADED;
          outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.DEGRADED));
        } else {
          this.mode = State.NORMAL;
          outgoing.send(new Message(MessageKind.MODE_m, Mailbox.Mode.NORMAL));
        }

      }

    }
  }

  /**
   * Detecs which physical unit has failed (if any) and adds it to the
   * observations list to be monitored until repaired.
   * 
   * @param incoming
   *          The mailbox to search through.
   */
  private void failureAnalysis(Mailbox incoming) {
    Message[] pumpStateMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Message[] pumpControlMessages = extractAllMatches(MessageKind.PUMP_CONTROL_STATE_n_b, incoming);
    Message levelMessage = extractOnlyMatch(MessageKind.LEVEL_v, incoming);
    Message steamMessage = extractOnlyMatch(MessageKind.STEAM_v, incoming);

    if (!validSteamReading(steamMessage)) {
      failures.add(new Message(MessageKind.STEAM_FAILURE_DETECTION));
      return;
    }

    for (int i = 0; i < config.getNumberOfPumps(); i++) {
      boolean c = validController(pumpControlMessages[i]);
      boolean p = validPump(pumpStateMessages[i]);
      boolean l = validLevel(levelMessage);

      if (p && c && l) {


        return;
      } else if (p && c && !l) { // WATER LVL

        failures.add(new Message(MessageKind.LEVEL_FAILURE_DETECTION));
        return;
      } else if (p && !c && l) { // CONTROLLER i

        failures.add(new Message(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n, i));
        return;
      } else if (p && !c && !l) {

        return;
      } else if (!p && c && l) { // PUMP i

        failures.add(new Message(MessageKind.PUMP_FAILURE_DETECTION_n, i));
        return;
      } else if (!p && c && !l) {

        return;
      } else if (!p && !c && l) { // PUMP i

        failures.add(new Message(MessageKind.PUMP_FAILURE_DETECTION_n, i));

        return;
      } else if (!p && !c && !l) {

        return;
      }

    }

  }

  /**
   * To check whether or not the water level is valid.
   * 
   * @param levelMessage
   *          the message containing the water level
   * @return boolean whether or not the water level is valid
   */
  private boolean validLevel(Message levelMessage) {
    assert levelMessage != null;

    double level = levelMessage.getDoubleParameter();

    if (!validLevelReading(level)) {
      return false;
    }

    if (!(level <= expectedWaterLevel - 5 || level >= expectedWaterLevel + 5)) {
      return false;
    }

    return true;

  }

  /**
   * To check whether or not the controller is valid.
   * 
   * @param controllerMessage
   *          the message containing the controller state
   * @return boolean whether or not the water level is valid
   */
  private boolean validController(Message controllerMessage) {
    assert controllerMessage != null;

    int id = controllerMessage.getIntegerParameter();

    if (controllerExpectedState[id] == controllerMessage.getBooleanParameter()) {
      return true;
    }

    return true;
  }

  /**
   * To check whether or not the pump is valid.
   * 
   * @param pumpMessage
   *          the message containing the controller state.
   * @return boolean whether or not the water level is valid.
   */
  private boolean validPump(Message pumpMessage) {
    assert pumpMessage != null;

    int id = pumpMessage.getIntegerParameter();

    if (pumpExpectedState[id] == pumpMessage.getBooleanParameter()) {
      return true;
    }

    return false;

  }

  /**
   * Calculate the water level from number of pumps turned on.
   * 
   * @param level
   *          Physical unit
   * @param steam
   *          Physical unit
   * @param i
   *          Pump no.
   * @return double Water level
   */
  public double calc(double level, double steam, int i) {

    return (level + (5 * config.getPumpCapacity(i) * i) - (5 * steam));
  }

  /**
   * Predict the number of pumps needed to maintain the water level.
   * 
   * @param incoming
   *          The mailbox to search through.
   * @param outgoing
   *          The mailbox to send to.
   */
  private void predictPumps(Mailbox incoming, Mailbox outgoing) {
    Message levelMessage = extractOnlyMatch(MessageKind.LEVEL_v, incoming);
    Message steamMessage = extractOnlyMatch(MessageKind.STEAM_v, incoming);

    double level = levelMessage.getDoubleParameter();
    double steam = steamMessage.getDoubleParameter();
    double target = config.getCapacity() / 2;
    expectedWaterLevel = 0.0;

    for (int i = 0; i < config.getNumberOfPumps(); i++) {
      if ((target - expectedWaterLevel) < 0 || (target - (calc(level, steam, i))) < 0) {
        break;
      }

      int c = Double.compare((target - expectedWaterLevel), (target - (calc(level, steam, i))));

      if (c > 0) {
        expectedWaterLevel = (calc(level, steam, i));
        pumps = i;
      }
    }
    Arrays.fill(pumpExpectedState, Boolean.FALSE);
    Arrays.fill(controllerExpectedState, Boolean.FALSE);

    for (int i = 0; i <= pumps; i++) {
      pumpExpectedState[i] = true;
      controllerExpectedState[i] = true;

    }
  }

  /**
   * Activate a given number (n) of pumps.
   * 
   * @param n
   *          Number of pumps to activate
   * @param outgoing
   *          Mailbox to send to.
   */
  private void activatePumps(int n, Mailbox outgoing) {
    assert outgoing != null;

    for (int i = 0; i < config.getNumberOfPumps(); i++) {
      if (i <= n) {
        outgoing.send(new Message(MessageKind.OPEN_PUMP_n, i));
      } else {
        outgoing.send(new Message(MessageKind.CLOSE_PUMP_n, i));
      }
    }
  }

  /**
   * To resolve physical unit failures.
   * 
   * @param incoming
   *          The mailbox to search through.
   * @param outgoing
   *          The mailbox to send to.
   */
  private void resolveObservations(Mailbox incoming, Mailbox outgoing) {
    assert incoming != null;
    assert outgoing != null;

    for (int i = 0; i < failures.size(); i++) {

      outgoing.send(failures.get(i));

      if (resolve(failures.get(i), incoming, outgoing) == null) {
        continue;
      }

      Message message = resolve(failures.get(i), incoming, outgoing);

      if (message != null && message.toString().contains("REPAIRED_ACKNOWLEDGEMENT")) {
        System.out.println("contained REPAIRED_ACKNOWLEDGEMENT" + message);
        failures.remove(failures.get(i));
      }

    }
  }

  /**
   * Resolve a failure message.
   * 
   * @param message
   *          Message to resolve.
   * @param incoming
   *          The mailbox to search through.
   * @param outgoing
   *          The mailbox to send to.
   * @return Message
   */
  private Message resolve(Message message, Mailbox incoming, Mailbox outgoing) {
    assert incoming != null;
    assert outgoing != null;
    assert message != null;

    if (message.toString().contains("LEVEL")) {
      if (!checkMatch(MessageKind.LEVEL_FAILURE_ACKNOWLEDGEMENT, incoming)) {
        return (new Message(MessageKind.LEVEL_FAILURE_DETECTION));

      }

      if (checkMatch(MessageKind.LEVEL_REPAIRED, incoming)) {
        return (new Message(MessageKind.LEVEL_REPAIRED_ACKNOWLEDGEMENT));
      }

    } else if (message.toString().contains("STEAM")) {
      if (!checkMatch(MessageKind.STEAM_OUTCOME_FAILURE_ACKNOWLEDGEMENT, incoming)
          && message.equals(new Message(MessageKind.STEAM_FAILURE_DETECTION))) {
        return new Message(MessageKind.STEAM_FAILURE_DETECTION);

      }

      if (checkMatch(MessageKind.STEAM_REPAIRED, incoming)) {
        return new Message(MessageKind.STEAM_REPAIRED_ACKNOWLEDGEMENT);
      }

    } else if (message.toString().contains("_CONTROL_")) {
      int i = message.getIntegerParameter();

      if (checkMatch(MessageKind.PUMP_CONTROL_FAILURE_ACKNOWLEDGEMENT_n, incoming)
          && message.equals(new Message(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n, i))) {
        return new Message(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n, i);

      }
      if (checkMatch(MessageKind.PUMP_CONTROL_REPAIRED_n, incoming)) {
        return new Message(MessageKind.PUMP_CONTROL_REPAIRED_ACKNOWLEDGEMENT_n, i);
      }

    } else if (message.toString().contains("PUMP_")) {
      int i = message.getIntegerParameter();

      if (checkMatch(MessageKind.PUMP_FAILURE_ACKNOWLEDGEMENT_n, incoming)
          && message.equals(new Message(MessageKind.PUMP_FAILURE_DETECTION_n, i))) {
        return new Message(MessageKind.PUMP_FAILURE_DETECTION_n, i);

      }
      if (checkMatch(MessageKind.PUMP_REPAIRED_n, incoming)) {
        return new Message(MessageKind.PUMP_FAILURE_ACKNOWLEDGEMENT_n, i);
      }

    }

    return null; // ERROR
  }

  /**
   * ok.
   * 
   * @param kind
   *          Message Kind
   * @param incoming
   *          The mailbox to search through.
   * 
   * @return if matched or not
   */
  private boolean checkMatch(MessageKind kind, Mailbox incoming) {
    assert kind != null;
    assert incoming != null;

    Message match = null;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        if (match == null) {
          return true;
        } else {
          // This indicates that we matched more than one message of the given kind.
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks whether the water level physical unit is valid.
   * 
   * @param level
   *          The level physical unit
   * @return boolean Whehter or not it is valid
   */
  private boolean validLevelReading(double level) {
    if (level > config.getCapacity() || level < 0.0) {
      return false;
    }

    return true;
  }

  /**
   * Checks whether the steam physical unit is valid.
   * 
   * @param steamMessage
   *          The steam physical unit
   * @return boolean Whether or not the steam reading was valid
   */
  private boolean validSteamReading(Message steamMessage) {
    if (steamMessage.getDoubleParameter() <= config.getMaximualSteamRate()
        && steamMessage.getDoubleParameter() >= 0.0) {
      return true;
    }

    return false;
  }

  /**
   * Check whether there was a transmission failure. This is indicated in several
   * ways. Firstly, when one of the required messages is missing. Secondly, when
   * the values returned in the messages are nonsensical.
   *
   * @param incoming
   *          The mailbox to search through.
   * @return boolean Whether or not there was a transmission failure.
   */
  private boolean transmissionFailure(Mailbox incoming) {
    Message[] pumpStateMessages = extractAllMatches(MessageKind.PUMP_STATE_n_b, incoming);
    Message[] pumpControlMessages = extractAllMatches(MessageKind.PUMP_CONTROL_STATE_n_b, incoming);
    Message levelMessage = extractOnlyMatch(MessageKind.LEVEL_v, incoming);
    Message steamMessage = extractOnlyMatch(MessageKind.STEAM_v, incoming);

    // Check level readings
    if (levelMessage == null) {
      // Nonsense or missing level reading
      return true;
    } else if (steamMessage == null) {
      // Nonsense or missing steam reading
      return true;
    } else if (pumpStateMessages.length != config.getNumberOfPumps()) {
      // Nonsense pump state readings
      return true;
    } else if (pumpControlMessages.length != config.getNumberOfPumps()) {
      // Nonsense pump control state readings
      return true;
    }
    // Done
    return false;
  }

  /**
   * Find and extract a message of a given kind in a mailbox. This must the only
   * match in the mailbox, else <code>null</code> is returned.
   *
   * @param kind
   *          The kind of message to look for.
   * @param incoming
   *          The mailbox to search through.
   * @return The matching message, or <code>null</code> if there was not exactly
   *         one match.
   */
  private static Message extractOnlyMatch(MessageKind kind, Mailbox incoming) {
    Message match = null;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        if (match == null) {
          match = ith;
        } else {
          // This indicates that we matched more than one message of the given kind.
          return null;
        }
      }
    }
    return match;
  }

  /**
   * Find and extract all messages of a given kind.
   *
   * @param kind
   *          The kind of message to look for.
   * @param incoming
   *          The mailbox to search through.
   * @return The array of matches, which can empty if there were none.
   */
  private static Message[] extractAllMatches(MessageKind kind, Mailbox incoming) {
    int count = 0;
    // Count the number of matches
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        count = count + 1;
      }
    }
    // Now, construct resulting array
    Message[] matches = new Message[count];
    int index = 0;
    for (int i = 0; i != incoming.size(); ++i) {
      Message ith = incoming.read(i);
      if (ith.getKind() == kind) {
        matches[index++] = ith;
      }
    }
    return matches;
  }
}
