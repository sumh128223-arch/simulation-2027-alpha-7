package frc.lib.dashboard;

import org.wpilib.telemetry.TelemetryLoggable;
import org.wpilib.telemetry.TelemetryTable;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.system.Timer;
import org.wpilib.telemetry.Telemetry;
import java.util.*;
import java.util.function.Predicate;

/** Class for managing persistent alerts to be sent over NetworkTables. */
public class Alert {
  private static final Map<String, AlertGroup> groups = new HashMap<>();

  private final AlertType type;
  private boolean active = false;
  private double activeStartTime = 0.0;
  private String text;

  /**
   * Creates a new Alert in the default group - "Alerts". If this is the first to be instantiated,
   * the appropriate entries will be added to NetworkTables.
   *
   * @param text Text to be displayed when the alert is active.
   * @param type Alert level specifying urgency.
   */
  public Alert(String text, AlertType type) {
    this("Alerts", text, type);
  }

  /**
   * Creates a new Alert. If this is the first to be instantiated in its group, the appropriate
   * entries will be added to NetworkTables.
   *
   * @param group Group identifier, also used as NetworkTables title
   * @param text Text to be displayed when the alert is active.
   * @param type Alert level specifying urgency.
   */
  public Alert(String group, String text, AlertType type) {
    if (!groups.containsKey(group)) {
      groups.put(group, new AlertGroup());
      Telemetry.log("/SmartDashboard/" + group, groups.get(group));
    }

    this.text = text;
    this.type = type;
    groups.get(group).alerts.add(this);
  }

  /**
   * Sets whether the alert should currently be displayed. When activated, the alert text will also
   * be sent to the console.
   */
  public void set(boolean active) {
    if (active && !this.active) {
      activeStartTime = Timer.getTimestamp();
      switch (type) {
        case ERROR:
          DriverStationErrors.reportError(text, false);
          break;
        case WARNING:
          DriverStationErrors.reportWarning(text, false);
          break;
        case INFO:
          System.out.println(text);
          break;
      }
    }
    this.active = active;
  }

  /** Updates current alert text. */
  public void setText(String text) {
    if (active && !text.equals(this.text)) {
      switch (type) {
        case ERROR:
          DriverStationErrors.reportError(text, false);
          break;
        case WARNING:
          DriverStationErrors.reportWarning(text, false);
          break;
        case INFO:
          System.out.println(text);
          break;
      }
    }
    this.text = text;
  }

  /** Publishes the current alert groups once per robot cycle. */
  public static void publishAll() {
    groups.forEach((group, alerts) -> Telemetry.log("/SmartDashboard/" + group, alerts));
  }

  private static class AlertGroup implements TelemetryLoggable {
    public final List<Alert> alerts = new ArrayList<>();

    public String[] getStrings(AlertType type) {
      Predicate<Alert> activeFilter = (Alert x) -> x.type == type && x.active;
      Comparator<Alert> timeSorter =
          (Alert a1, Alert a2) -> Double.compare(a2.activeStartTime, a1.activeStartTime);
      return alerts.stream()
          .filter(activeFilter)
          .sorted(timeSorter)
          .map((Alert a) -> a.text)
          .toArray(String[]::new);
    }

    @Override
    public void logTo(TelemetryTable table) {
      table.log("errors", getStrings(AlertType.ERROR));
      table.log("warnings", getStrings(AlertType.WARNING));
      table.log("infos", getStrings(AlertType.INFO));
    }

    @Override
    public String getTelemetryType() {
      return "Alerts";
    }

  }

  /** Represents an alert's level of urgency. */
  public enum AlertType {
    /**
     * High priority alert - displayed first on the dashboard with a red "X" symbol. Use this type
     * for problems which will seriously affect the robot's functionality and thus require immediate
     * attention.
     */
    ERROR,

    /**
     * Medium priority alert - displayed second on the dashboard with a yellow "!" symbol. Use this
     * type for problems which could affect the robot's functionality but do not necessarily require
     * immediate attention.
     */
    WARNING,

    /**
     * Low priority alert - displayed last on the dashboard with a green "i" symbol. Use this type
     * for problems which are unlikely to affect the robot's functionality, or any other alerts
     * which do not fall under "ERROR" or "WARNING".
     */
    INFO
  }
}
