package frc.lib.interfaces.motor;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.controller.ProfiledPIDController;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.system.Models;
import org.wpilib.math.trajectory.TrapezoidProfile;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.simulation.FlywheelSim;
import frc.robot.Constants;

public class GenericFlywheelIOSim implements GenericFlywheelIO {
  static final TrapezoidProfile.Constraints UNLIMITED =
      new TrapezoidProfile.Constraints(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

  private final FlywheelSim sim;
  private final ProfiledPIDController pid =
      new ProfiledPIDController(0.0, 0.0, 0.0, UNLIMITED, Constants.LOOP_PERIOD_SEC);
  private final SlewRateLimiter voltageLimiter = new SlewRateLimiter(2.5);

  private double appliedVoltageVolt = 0.0;

  public GenericFlywheelIOSim(DCMotor motor, double reduction, double moiJKgMeter2) {
    sim =
        new FlywheelSim(Models.flywheelFromPhysicalConstants(motor, moiJKgMeter2, reduction), motor);
  }

  @Override
  public void updateInputs(GenericFlywheelIOInputs inputs) {
    if (RobotState.isDisabled()) {
      stop();
    }

    if (!RobotState.isDisabled()) {
      setVoltage(voltageLimiter.calculate(appliedVoltageVolt));
    } else {
      voltageLimiter.reset(appliedVoltageVolt);
    }

    sim.update(Constants.LOOP_PERIOD_SEC);

    inputs.connected = true;

    inputs.velRadPerSec = sim.getAngularVelocity();
    inputs.outputVoltageVolt = appliedVoltageVolt;
    inputs.supplyCurrentAmp = Math.abs(sim.getCurrentDraw());
  }

  @Override
  public void setPdf(double kp, double kd, double kf) {
    pid.setPID(kp, 0.0, kd);
  }

  @Override
  public void setVoltage(double voltageVolt) {
    appliedVoltageVolt = Math.clamp(voltageVolt, -12.0, 12.0);
    sim.setInputVoltage(appliedVoltageVolt);
  }

  @Override
  public void setVel(double velRadPerSec) {
    pid.setConstraints(UNLIMITED);
    setVoltage(pid.calculate(sim.getAngularVelocity(), velRadPerSec));
  }

  @Override
  public void setVel(double velRadPerSec, double feedforwardVolt) {
    pid.setConstraints(UNLIMITED);
    setVoltage(pid.calculate(sim.getAngularVelocity(), velRadPerSec) + feedforwardVolt);
  }

  @Override
  public void setVel(double velRadPerSec, double accelRadPerSec2, double feedforwardVolt) {
    pid.setConstraints(new TrapezoidProfile.Constraints(Double.POSITIVE_INFINITY, accelRadPerSec2));
    setVoltage(pid.calculate(sim.getAngularVelocity(), velRadPerSec) + feedforwardVolt);
  }

  // TODO
  @Override
  public void setCurrent(double currentAmp) {
    setVoltage(currentAmp);
  }

  @Override
  public void stop() {
    setVoltage(0.0);
  }
}
