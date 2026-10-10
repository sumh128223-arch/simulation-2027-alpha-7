package frc.robot;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.command2.CommandScheduler;
import org.littletonrobotics.junction.Logger;
import frc.robot.subsystem.intake.Intake;
import frc.robot.subsystem.intake.IntakeGoal.SlamArmGoal;

class RobotSimulationTest {
  @Test
  void startsAndRunsRobotLifecycleInSimulation() {
    assertTrue(HAL.initialize());
    try (var robot = new Robot()) {
      robot.disabledInit();
      var disabledIntake = Intake.createSim();
      disabledIntake.setPivotGoal(SlamArmGoal.DEPLOY);
      disabledIntake.periodic();
      double disabledAngle = disabledIntake.getPivotPositionRad();
      for (int i = 0; i < 5; i++) {
        robot.robotPeriodic();
        robot.disabledPeriodic();
        disabledIntake.periodic();
        assertEquals(disabledAngle, disabledIntake.getPivotPositionRad());
      }
      robot.disabledExit();
      robot.autonomousInit();
      robot.robotPeriodic();
      robot.autonomousPeriodic();
      robot.autonomousExit();
      robot.teleopInit();
      robot.robotPeriodic();
      robot.teleopPeriodic();
      robot.teleopExit();
      robot.utilityInit();
      robot.utilityPeriodic();
      robot.utilityExit();
      assertTrue(Double.isFinite(RobotContainer.getOdometry().getEstimatedPose().getX()));
    } finally {
      CommandScheduler.getInstance().cancelAll();
      Logger.end();
    }
  }
}
