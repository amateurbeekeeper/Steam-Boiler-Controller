package steam.boiler.tests;

import static org.junit.Assert.fail;

import java.util.Arrays;

import steam.boiler.core.MySteamBoilerController;
import steam.boiler.model.PhysicalUnits;
import steam.boiler.tests.TestUtils.MailboxMatcher;
import steam.boiler.util.Mailbox;
import steam.boiler.util.UnboundedMailbox;
import steam.boiler.util.Mailbox.Message;
import steam.boiler.util.Mailbox.MessageKind;
import steam.boiler.util.Mailbox.Mode;

public class TestUtils {

  // ========================================================================
  // Response Matchers
  // ========================================================================

  /**
   * Match MODE_initialisation messages.
   */
  public static MessageMatcher MODE_initialisation = new ConcreteMessageMatcher(
      Mailbox.MessageKind.MODE_m, Mode.INITIALISATION);

  /**
   * Match MODE_normal messages.
   */
  public static MessageMatcher MODE_normal = new ConcreteMessageMatcher(Mailbox.MessageKind.MODE_m,
      Mode.NORMAL);

  /**
   * Match MODE_degraded messages.
   */
  public static MessageMatcher MODE_degraded = new ConcreteMessageMatcher(
      Mailbox.MessageKind.MODE_m, Mode.DEGRADED);

  /**
   * Match MODE_degraded messages.
   */
  public static MessageMatcher MODE_rescue = new ConcreteMessageMatcher(Mailbox.MessageKind.MODE_m,
      Mode.RESCUE);

  /**
   * Match MODE_emergencystop messages.
   */
  public static MessageMatcher MODE_emergencystop = new ConcreteMessageMatcher(
      Mailbox.MessageKind.MODE_m, Mode.EMERGENCY_STOP);

  /**
   * Match PROGRAM_READY messages.
   */
  public static MessageMatcher PROGRAM_READY = new ConcreteMessageMatcher(
      MessageKind.PROGRAM_READY);

  /**
   * Match VALVE messages.
   */
  public static MessageMatcher VALVE = new ConcreteMessageMatcher(MessageKind.VALVE);

  /**
   * Match LEVEL_FAILURE_DETECTION messages.
   */
  public static MessageMatcher LEVEL_FAILURE_DETECTION = new ConcreteMessageMatcher(
      MessageKind.LEVEL_FAILURE_DETECTION);

  /**
   * Match STEAM_FAILURE_DETECTION messages.
   */
  public static MessageMatcher STEAM_FAILURE_DETECTION = new ConcreteMessageMatcher(
      MessageKind.STEAM_FAILURE_DETECTION);

  /**
   * Match PUMP_FAILURE_DETECTION_n messages.
   */
  public static MessageMatcher PUMP_FAILURE_DETECTION(int n) {
    return PUMP_FAILURE_DETECTION(new IntegerParameterMatcher(n));
  }

  /**
   * Match PUMP_FAILURE_DETECTION_n messages.
   */
  public static MessageMatcher PUMP_FAILURE_DETECTION(ParameterMatcher matcher) {
    return new ConcreteMessageMatcher(MessageKind.PUMP_FAILURE_DETECTION_n, matcher);
  }

  /**
   * Match PUMP_CONTROL_FAILURE_DETECTION_n messages.
   */
  public static MessageMatcher PUMP_CONTROL_FAILURE_DETECTION(int n) {
    return PUMP_CONTROL_FAILURE_DETECTION(new IntegerParameterMatcher(n));
  }

  /**
   * Match PUMP_CONTROL_FAILURE_DETECTION_n messages.
   */
  public static MessageMatcher PUMP_CONTROL_FAILURE_DETECTION(ParameterMatcher matcher) {
    return new ConcreteMessageMatcher(MessageKind.PUMP_CONTROL_FAILURE_DETECTION_n, matcher);
  }

  /**
   * Return a given PUMP message.
   *
   * @param n
   *          Concrete PUMP paremeter to match.
   * @return
   */
  public static MessageMatcher OpenPump(int n) {
    return OpenPump(new IntegerParameterMatcher(n));
  }

  /**
   * Return a given PUMP message.
   *
   * @param matcher
   *          Matcher for PUMP_n parameter.
   * @return
   */
  public static MessageMatcher OpenPump(ParameterMatcher matcher) {
    return new ConcreteMessageMatcher(MessageKind.OPEN_PUMP_n, matcher);
  }

  /**
   * Return a given PUMP message.
   *
   * @param n
   *          Concrete PUMP paremeter to match.
   * @return
   */
  public static MessageMatcher ClosePump(int n) {
    return ClosePump(new IntegerParameterMatcher(n));
  }

  /**
   * Return a given PUMP message.
   *
   * @param matcher
   *          Matcher for PUMP_n parameter.
   * @return
   */
  public static MessageMatcher ClosePump(ParameterMatcher matcher) {
    return new ConcreteMessageMatcher(MessageKind.CLOSE_PUMP_n, matcher);
  }

  /**
   * Clock the system exactly once and check for a set of expected messages.
   *
   * @param controller
   *          The controller under test.
   * @param model
   *          The state of the PhysicalUnits, from which the set of input messages passed to the
   *          controller is determined.
   * @param responses
   *          The set of response matchers which are assumed to be out-of-order.
   */
  public static void clockOnceExpecting(MySteamBoilerController controller, PhysicalUnits model,
      MailboxMatcher matcher) {
    Mailbox input = new UnboundedMailbox(100);
    Mailbox output = new UnboundedMailbox(100);
    // Generation messages for controller from model
    model.transmit(input);
    // Clock controller to process incoming messages and return responses.
    controller.clock(input, output);
    // Check the response messages
    if (!matcher.matches(output)) {
      fail("did not expect to receive " + output + ", expected " + matcher);
    }
    // Apply message to model from controller
    model.receive(output);
  }

  /**
   * Clock the system until a given even has occurred. A maximum timeout is given in microseconds.
   * If this expires, then the test is failed.
   *
   * @param timeout
   *          The maximum amount of time (in seconds) to wait for the event in question. This helps
   *          to prevent tests which loop forever.
   * @param controller
   *          The controller under test.
   * @param physicalUnits
   *          The model of the physical units being manipulated.
   * @param matcher
   *          The matcher used for the event in question
   */
  public static void clockUntil(int timeout, MySteamBoilerController controller,
      PhysicalUnits physicalUnits, MailboxMatcher matcher) {
    final int granularity = 100; // ms
    int totalElapsed = 0; // ms
    // Convert timeout into microseconds
    timeout = timeout * 1000;
    //
    while (totalElapsed < timeout) {
      Mailbox received = clock(granularity, totalElapsed, controller, physicalUnits);
      if (received != null) {
        // We received something back from controller, there see whether we have matched our event.
        if (matcher.matches(received)) {
          return;
        }
      }
      totalElapsed += granularity;
    }
    // If we get here, then the event wasn't matched within the required timeframe.
    fail("timeout occurred");
  }

  /**
   * Clock the system for a given amount of time, whilst ensuring a particular event does not happen
   * (e.g. emergency stop).
   *
   * @param time
   *          The amount of time (in seconds) to clock the system for.
   * @param controller
   *          The controller under test.
   * @param physicalUnits
   *          The model of the physical units being manipulated.
   * @param matcher
   *          The matcher used for the event in question which we want to avoid.
   */
  public static void clockForWithout(int time, MySteamBoilerController controller,
      PhysicalUnits physicalUnits, MailboxMatcher matcher) {
    final int granularity = 100; // ms
    int totalElapsed = 0; // ms
    // Convert timeout into microseconds
    time = time * 1000;
    //
    while (totalElapsed < time) {
      Mailbox received = clock(granularity, totalElapsed, controller, physicalUnits);
      if (received != null) {
        // We received something back from controller, there see whether we have matched our event.
        if (matcher.matches(received)) {
          // If we've matched this event, then that's bad news.
          fail("bad event happened after " + totalElapsed + "ms (" + received + ")");
        }
      }
      totalElapsed += granularity;
    }
    // If we get here, then the given event obviously didn't happen so we're done.
  }

  /**
   * Clock the combined system for a given amount of time. This sends and receives messages between
   * the two components when the total time elapsed is a multiple of five seconds. Messages received
   * from the controller are return (when available) so they can be inspected for certain events.
   *
   * @param elapsed
   *          The elapsed time (in microseconds) since the last clock.
   * @param totalElapsed
   *          The total amount of elapsed time (in microseconds) since the beginning of the system.
   *          The is used to determine when to send/receive messages from the controller and
   *          physical units.
   * @param controller
   *          The controller under test.
   * @param physicalUnits
   *          The model of the physical units being manipulated.
   * @return Any messages received from the controller, or null if this wasn't a transmission cycle.
   */
  public static Mailbox clock(int elapsed, int totalElapsed, MySteamBoilerController controller,
      PhysicalUnits physicalUnits) {
    physicalUnits.clock(elapsed);
    // After every five seconds has elapsed we allow the controller and physical units to
    // synchronise (i.e. transmit messages between them).
    if ((totalElapsed % 5000) == 0) {
      Mailbox input = new UnboundedMailbox(100);
      Mailbox output = new UnboundedMailbox(100);
      // Generation messages for controller from model
      physicalUnits.transmit(input);
      // Clock controller to process incoming messages and return responses.
      controller.clock(input, output);
      // Apply message to model from controller
      physicalUnits.receive(output);
      // return messages received from controller
      return output;
    } else {
      // Nothing to return
      return null;
    }
  }

  /**
   * A mailbox match provides a way to match concrete messages without having to explicitly provide
   * all the details. For example, suppose we wanted to match any possible LEVEL_v message (e.g.
   * LEVEL(0.0), LEVEL(1.0), etc). Then we'd use a matcher which could be imagined as LEVEL(?); that
   * is, the matcher matches any message of LEVEL_v kind with arbitrary double parameter.
   *
   * @author David J. Pearce
   *
   */
  public static interface MailboxMatcher {
    /**
     * Determine whether a given message is match by this message.
     *
     * @param m
     *          the message to be checked.
     * @return true if the mailbox matches
     */
    public boolean matches(Mailbox m);
  }

  /**
   * A message match provides a way to match concrete messages without having to explicitly provide
   * all the details. For example, suppose we wanted to match any possible LEVEL_v message (e.g.
   * LEVEL(0.0), LEVEL(1.0), etc). Then we'd use a matcher which could be imagined as LEVEL(?); that
   * is, the matcher matches any message of LEVEL_v kind with arbitrary double parameter.
   *
   * @author David J. Pearce
   *
   */
  public static interface MessageMatcher {
    /**
     * Determine whether a given message is match by this message.
     *
     * @param m
     *          the message to be checked.
     * @return The index of the matching message in the mailbox, or a negative number if no match.
     */
    public int match(Mailbox m);
  }

  /**
   * Construct a mailbox matcher which requires every message to be matched by exactly one matcher.
   *
   * @param matchers The set of matches
   * @return
   */
  public static MailboxMatcher exactly(final MessageMatcher... matchers) {
    return new MailboxMatcher() {

      @Override
      public boolean matches(Mailbox mailbox) {
        if (mailbox.size() != matchers.length) {
          return false;
        } else {
          boolean[] matches = new boolean[mailbox.size()];
          for (int j = 0; j != matchers.length; ++j) {
            int m = matchers[j].match(mailbox);
            if (m >= 0) {
              matches[m] = true;
            } else {
              return false;
            }
          }
          // Sanity check matches
          for (int i = 0; i != matches.length; ++i) {
            if (!matches[i]) {
              return false;
            }
          }
          return true;
        }
      }

      @Override
      public String toString() {
        return "exactly" + Arrays.toString(matchers);
      }
    };
  }

  /**
   * Construct a mailbox matcher which requires every matcher to match something.
   *
   * @param matchers The set of matches
   * @return
   */
  public static MailboxMatcher atleast(final MessageMatcher... matchers) {
    return new MailboxMatcher() {

      @Override
      public boolean matches(Mailbox mailbox) {
        for (int j = 0; j != matchers.length; ++j) {
          int m = matchers[j].match(mailbox);
          if (m < 0) {
            return false;
          }
        }
        return true;
      }

      @Override
      public String toString() {
        return "atleast" + Arrays.toString(matchers);
      }
    };
  }

  /**
   * A concrete message matcher messages of a given kind. For example, it could be used to match any
   * kind of <code>LEVEL_v</code> message.
   *
   * @author David J. Pearce
   *
   */
  private static class ConcreteMessageMatcher implements MailboxMatcher,MessageMatcher {
    private final MessageKind kind;
    private final ParameterMatcher parameter;

    public ConcreteMessageMatcher(MessageKind kind) {
      this.kind = kind;
      this.parameter = null;
    }

    public ConcreteMessageMatcher(MessageKind kind, ParameterMatcher parameter) {
      this.kind = kind;
      this.parameter = parameter;
    }

    public ConcreteMessageMatcher(MessageKind kind, Mode modeParameter) {
      this.kind = kind;
      this.parameter = new ModeParameterMatcher(modeParameter);
    }

    public ConcreteMessageMatcher(MessageKind kind, int integerParameter) {
      this.kind = kind;
      this.parameter = new IntegerParameterMatcher(integerParameter);
    }

    public ConcreteMessageMatcher(MessageKind kind, boolean booleanParameter) {
      this.kind = kind;
      this.parameter = new BooleanParameterMatcher(booleanParameter);
    }

    public ConcreteMessageMatcher(MessageKind kind, double doubleParameter) {
      this.kind = kind;
      this.parameter = new DoubleParameterMatcher(doubleParameter);
    }

    @Override
    public boolean matches(Mailbox m) {
      return match(m) >= 0;
    }

    @Override
    public int match(Mailbox m) {
      for (int i = 0; i != m.size(); ++i) {
        if (matches(m.read(i))) {
          return i;
        }
      }
      return -1;
    }

    private boolean matches(Message m) {
      if (m.getKind() == kind) {
        switch (kind) {
          case PROGRAM_READY:
          case PHYSICAL_UNITS_READY:
          case VALVE:
          case LEVEL_FAILURE_DETECTION:
          case STEAM_FAILURE_DETECTION:
          case LEVEL_REPAIRED_ACKNOWLEDGEMENT:
          case STEAM_REPAIRED_ACKNOWLEDGEMENT:
          case STOP:
          case STEAM_BOILER_WAITING:
          case LEVEL_REPAIRED:
          case STEAM_REPAIRED:
          case LEVEL_FAILURE_ACKNOWLEDGEMENT:
          case STEAM_OUTCOME_FAILURE_ACKNOWLEDGEMENT:
            return true;
          case MODE_m:
            return parameter.matches(m.getModeParameter());
          case OPEN_PUMP_n:
          case CLOSE_PUMP_n:
          case PUMP_FAILURE_DETECTION_n:
          case PUMP_CONTROL_FAILURE_DETECTION_n:
          case PUMP_REPAIRED_ACKNOWLEDGEMENT_n:
          case PUMP_CONTROL_REPAIRED_ACKNOWLEDGEMENT_n:
          case PUMP_REPAIRED_n:
          case PUMP_CONTROL_REPAIRED_n:
          case PUMP_FAILURE_ACKNOWLEDGEMENT_n:
            return parameter.matches(m.getIntegerParameter());
          case LEVEL_v:
          case STEAM_v:
            return parameter.matches(m.getDoubleParameter());
          case PUMP_STATE_n_b:
          case PUMP_CONTROL_STATE_n_b:
            return parameter.matches(m.getIntegerParameter(), m.getBooleanParameter());
          default:
            throw new IllegalArgumentException("invalid message kind");
        }
      }
      return false;
    }

    @Override
    public String toString() {
      String r = kind.toString();
      if (parameter != null) {
        r += "(" + parameter + ")";
      }
      return r;
    }
  }

  /**
   * Provides a way of matching a parameter value.
   *
   * @author David J. Pearce
   *
   */
  public abstract static class ParameterMatcher {
    /**
     * Attempt to match a mode parameter.
     *
     * @param m
     *          mode parameter
     * @return
     */
    public boolean matches(Mode m) {
      return false;
    }

    /**
     * Attempt to match an integer parameter.
     *
     * @param n
     *          integer parameter
     * @return
     */
    public boolean matches(int n) {
      return false;
    }

    /**
     * Attempt to match a boolean parameter.
     *
     * @param b
     *          boolean parameter
     * @return
     */
    public boolean matches(boolean b) {
      return false;
    }

    /**
     * Attempt to match a double parameter.
     *
     * @param v
     *          double parameter
     * @return
     */
    public boolean matches(double v) {
      return false;
    }

    /**
     * Attempt to match an integer and boolean parameter pair.
     *
     * @param b
     *          boolean parameter
     * @return
     */
    public boolean matches(int n, boolean b) {
      return false;
    }
  }

  /**
   * Match any parameter value. This can be thought of as a wildcard.
   */
  public static final ParameterMatcher ANY = new ParameterMatcher() {
    @Override
    public boolean matches(Mode m) {
      return true;
    }

    @Override
    public boolean matches(int n) {
      return true;
    }

    @Override
    public boolean matches(boolean b) {
      return true;
    }

    @Override
    public boolean matches(double v) {
      return true;
    }

    @Override
    public boolean matches(int n, boolean b) {
      return true;
    }

    @Override
    public String toString() {
      return "?";
    }
  };

  /**
   * Match a mode parameter exactly.
   *
   * @author David J. Pearce
   *
   */
  private static class ModeParameterMatcher extends ParameterMatcher {
    private final Mode value;

    public ModeParameterMatcher(Mode value) {
      this.value = value;
    }

    @Override
    public boolean matches(Mode value) {
      return this.value.equals(value);
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }

  /**
   * Match an integer parameter exactly.
   *
   * @author David J. Pearce
   *
   */
  private static class IntegerParameterMatcher extends ParameterMatcher {
    private final int value;

    public IntegerParameterMatcher(int value) {
      this.value = value;
    }

    @Override
    public boolean matches(int value) {
      return this.value == value;
    }

    @Override
    public String toString() {
      return Integer.toString(value);
    }
  }

  /**
   * Match a double parameter exactly.
   *
   * @author David J. Pearce
   *
   */
  private static class DoubleParameterMatcher extends ParameterMatcher {
    private final double value;

    public DoubleParameterMatcher(double value) {
      this.value = value;
    }

    @Override
    public boolean matches(double value) {
      return this.value == value;
    }

    @Override
    public String toString() {
      return Double.toString(value);
    }
  }

  /**
   * Match a boolean parameter exactly.
   *
   * @author David J. Pearce
   *
   */
  private static class BooleanParameterMatcher extends ParameterMatcher {
    private final boolean value;

    public BooleanParameterMatcher(boolean value) {
      this.value = value;
    }

    @Override
    public boolean matches(boolean value) {
      return this.value == value;
    }

    @Override
    public String toString() {
      return Boolean.toString(value);
    }
  }
}
