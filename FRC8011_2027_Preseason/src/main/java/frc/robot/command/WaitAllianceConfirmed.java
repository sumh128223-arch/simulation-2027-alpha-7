package frc.robot.command;

import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.command2.Command;

public class WaitAllianceConfirmed extends Command {
  private static final int MAX_ALLIANCE_CONFIRM_COUNT = 2;

  private Alliance lastAlliance = null;
  private int allianceConfirmCount = 0;

  @Override
  public void execute() {
    var alliance = MatchState.getAlliance();

    if (alliance.isEmpty()) {
      allianceConfirmCount = 0;
      return;
    }

    if (lastAlliance != null) {
      if (lastAlliance == alliance.get()) {
        allianceConfirmCount++;
      } else {
        allianceConfirmCount = 0;
      }
    } else {
      allianceConfirmCount = 0;
    }

    lastAlliance = alliance.get();
  }

  @Override
  public boolean isFinished() {
    return allianceConfirmCount >= MAX_ALLIANCE_CONFIRM_COUNT;
  }
}
