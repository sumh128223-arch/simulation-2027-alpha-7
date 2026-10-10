package frc.robot;

import static org.junit.jupiter.api.Assertions.*;

import frc.robot.subsystem.intake.Intake;
import frc.robot.subsystem.intake.IntakeGoal.SlamArmGoal;
import org.junit.jupiter.api.Test;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.button.CommandNiDsXboxController;
import org.wpilib.driverstation.NiDsXboxController;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.hardware.hal.RobotMode;
import org.wpilib.simulation.DriverStationSim;

class IntakeControlsSimulationTest {
  @Test
  void leftTriggerDeploysAndReleaseRetractsWithoutDisabledOscillation() {
    assertTrue(HAL.initialize());
    var scheduler = CommandScheduler.getInstance();
    scheduler.cancelAll();
    scheduler.getDefaultButtonLoop().clear();
    DriverStationSim.resetData();
    DriverStationSim.setDsAttached(true);
    DriverStationSim.setEnabled(true);
    DriverStationSim.setRobotMode(RobotMode.TELEOPERATED);
    DriverStationSim.setJoystickAxesMaximumIndex(0, 5);
    DriverStationSim.setJoystickAxesAvailable(0, 6);
    var intake = Intake.createSim();
    var driver = new CommandNiDsXboxController(0);
    driver.leftTrigger().whileTrue(RobotContainer.MyCommand.intakeMacroCommand(intake));
    try {
      DriverStationSim.setJoystickAxis(0, NiDsXboxController.Axis.LEFT_TRIGGER.value, 0.0);
      DriverStationSim.notifyNewData();
      scheduler.run();
      DriverStationSim.setJoystickAxis(0, NiDsXboxController.Axis.LEFT_TRIGGER.value, 1.0);
      DriverStationSim.notifyNewData();
      scheduler.run();
      assertEquals(SlamArmGoal.DEPLOY, intake.getPivotGoal());
      for (int i = 0; i < 5; i++) scheduler.run();
      assertEquals(0.0, intake.getPivotPositionRad(), 1e-6);
      DriverStationSim.setJoystickAxis(0, NiDsXboxController.Axis.LEFT_TRIGGER.value, 0.0);
      DriverStationSim.notifyNewData();
      for (int i = 0; i < 5; i++) scheduler.run();
      assertEquals(SlamArmGoal.RETRACT, intake.getPivotGoal());
      assertEquals(Math.toRadians(99), intake.getPivotPositionRad(), 1e-6);
      DriverStationSim.setEnabled(false);
      DriverStationSim.notifyNewData();
      intake.setPivotGoal(SlamArmGoal.DEPLOY);
      for (int i = 0; i < 5; i++) scheduler.run();
      assertEquals(Math.toRadians(99), intake.getPivotPositionRad(), 1e-6);
    } finally {
      scheduler.cancelAll();
      scheduler.getDefaultButtonLoop().clear();
      scheduler.unregisterSubsystem(intake);
      DriverStationSim.resetData();
      DriverStationSim.notifyNewData();
    }
  }
}
