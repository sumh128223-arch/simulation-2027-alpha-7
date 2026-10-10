package frc.lib.dashboard;

import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;

public record AllianceValue<T>(T blue, T red) {
  public T get() {
    var alliance = MatchState.getAlliance();
    return get(alliance.isPresent() && alliance.get() == Alliance.BLUE);
  }

  public T get(Alliance alliance) {
    return get(alliance == Alliance.BLUE);
  }

  public T get(boolean isBlueAlliance) {
    return isBlueAlliance ? blue : red;
  }
}
