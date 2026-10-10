package frc.lib.utils;

import com.ctre.phoenix6.StatusCode;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import java.util.function.Supplier;

public class Phoenix6Helper {
  public static void checkErrorAndRetry(String deviceName, Supplier<StatusCode> configFunction) {
    checkErrorAndRetry(deviceName, configFunction, 5);
  }

  public static void checkErrorAndRetry(
      String deviceName, Supplier<StatusCode> configFunction, int numTries) {
    StatusCode code = configFunction.get();

    var tries = 0;
    while (code != StatusCode.OK && tries < numTries) {
      DriverStationErrors.reportWarning(
          deviceName + " -> Retrying phoenix 6 device config " + code.getName(), false);
      code = configFunction.get();
      tries++;
    }

    if (code != StatusCode.OK) {
      DriverStationErrors.reportError(
          deviceName + " -> Failed to config phoenix 6 device after " + numTries + " attempts",
          false);
    }
  }
}
