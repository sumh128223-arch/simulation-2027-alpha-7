package frc.robot.subsystem.swerve;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.system.Models;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.simulation.DCMotorSim;
import frc.robot.Constants;

public class ModuleIOSim implements ModuleIO {
  private final DCMotorSim driveSim;
  private final DCMotorSim steerSim;

  private final PIDController drivePid;
  private final PIDController steerPid;

  private final SlewRateLimiter driveVoltageLimiter = new SlewRateLimiter(2.5);

  private double driveAppliedVoltageVolt = 0.0;
  private double steerAppliedVoltageVolt = 0.0;

  ModuleIOSim() {
    var driveGains = SwerveConfig.getDriveGains();
    var steerGains = SwerveConfig.getSteerGains();

    drivePid = new PIDController(driveGains.kp(), 0.0, driveGains.kd(), Constants.LOOP_PERIOD_SEC);
    steerPid = new PIDController(steerGains.kp(), 0.0, steerGains.kd(), Constants.LOOP_PERIOD_SEC);

    var driveMotor = DCMotor.getKrakenX60Foc(1);
    var steerMotor = DCMotor.getKrakenX60Foc(1);

    driveSim =
        new DCMotorSim(
            Models.singleJointedArmFromPhysicalConstants(driveMotor, 0.025, SwerveConfig.DRIVE_REDUCTION),
            driveMotor);
    steerSim =
        new DCMotorSim(
            Models.singleJointedArmFromPhysicalConstants(steerMotor, 0.004, SwerveConfig.STEER_REDUCTION),
            steerMotor);

    steerPid.enableContinuousInput(-Math.PI, Math.PI);
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    if (RobotState.isDisabled()) {
      stop();
    }

    if (RobotState.isDisabled()) {
      setDriveVoltage(driveVoltageLimiter.calculate(driveAppliedVoltageVolt));
    } else {
      driveVoltageLimiter.reset(driveAppliedVoltageVolt);
    }

    driveSim.update(Constants.LOOP_PERIOD_SEC);
    steerSim.update(Constants.LOOP_PERIOD_SEC);

    inputs.driveMotorConnected = true;
    inputs.drivePositionRad = driveSim.getAngularPosition();
    inputs.driveVelRadPerSec = driveSim.getAngularVelocity();
    inputs.driveOutputVoltageVolt = driveAppliedVoltageVolt;
    inputs.driveSupplyCurrentAmp = Math.abs(driveSim.getCurrentDraw());

    inputs.steerMotorConnected = true;
    inputs.steerAbsPosition = new Rotation2d(steerSim.getAngularPosition());
    inputs.steerVelRadPerSec = steerSim.getAngularVelocity();
    inputs.steerOutputVoltageVolt = steerAppliedVoltageVolt;
    inputs.steerSupplyCurrentAmp = Math.abs(steerSim.getCurrentDraw());
  }

  private void setDriveVoltage(double voltageVolt) {
    driveAppliedVoltageVolt = Math.clamp(voltageVolt, -12.0, 12.0);
    driveSim.setInputVoltage(driveAppliedVoltageVolt);
  }

  private void setSteerVoltage(double voltageVolt) {
    steerAppliedVoltageVolt = Math.clamp(voltageVolt, -12.0, 12.0);
    steerSim.setInputVoltage(steerAppliedVoltageVolt);
  }

  @Override
  public void setDrivePdf(double kp, double kd, double ks) {
    drivePid.setPID(kp, 0.0, kd);
  }

  @Override
  public void setSteerPdf(double kp, double kd, double ks) {
    steerPid.setPID(kp, 0.0, kd);
  }

  @Override
  public void setDriveVelocity(double velRadPerSec, double torqueAmp) {
    setDriveVoltage(drivePid.calculate(driveSim.getAngularVelocity(), velRadPerSec));
  }

  @Override
  public void setDriveCurrent(double currentAmp) {
    setDriveVoltage(currentAmp);
  }

  @Override
  public void setSteerPosition(double angleRad) {
    setSteerVoltage(steerPid.calculate(steerSim.getAngularPosition(), angleRad));
  }

  @Override
  public void stop() {
    setDriveVoltage(0.0);
    setSteerVoltage(0.0);
  }

  double getDrivePositionRad() {
    return driveSim.getAngularPosition();
  }

  double getDriveVelRadPerSec() {
    return driveSim.getAngularVelocity();
  }

  double getSteerPositionRad() {
    return steerSim.getAngularPosition();
  }
}
