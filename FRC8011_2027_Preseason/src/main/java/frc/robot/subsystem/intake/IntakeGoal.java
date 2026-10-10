package frc.robot.subsystem.intake;

public class IntakeGoal {
  public enum SlamArmGoal {
    DEPLOY,
    RETRACT,
    IDLE,
    STOP
  }

  public enum RollerGoal {
    INTAKE,
    OUTTAKE,
    IDLE
  }

  public enum SlamState {
    DEPLOYED,
    RETRACTED,
    MOVING,
    STOP
  }
}
