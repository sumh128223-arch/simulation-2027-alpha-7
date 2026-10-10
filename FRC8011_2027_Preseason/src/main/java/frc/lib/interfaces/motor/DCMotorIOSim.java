package frc.lib.interfaces.motor;

import org.wpilib.math.controller.ProfiledPIDController;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N2;
import org.wpilib.math.system.LinearSystem;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.system.Models;
import org.wpilib.math.trajectory.TrapezoidProfile;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.simulation.DCMotorSim;
import frc.lib.math.GainsUtil.Gains;
import frc.lib.math.UnitConverter;

public class DCMotorIOSim implements DCMotorIO {
  private final DCMotorSim sim;
  private final ProfiledPIDController pid =
      new ProfiledPIDController(
          0,
          0,
          0,
          new TrapezoidProfile.Constraints(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
  private final SlewRateLimiter voltageLimiter = new SlewRateLimiter(1000);

  private UnitConverter ratioConverter = UnitConverter.identity();
  private UnitConverter positionConvertor = UnitConverter.identity();

  private double minRawPosition = -Double.MAX_VALUE;
  private double maxRawPosition = Double.MAX_VALUE;

  @Override
  public void updateInputs(DCMotorIOInputs inputs) {
    if (RobotState.isDisabled()) {
      stop();
    }

    sim.update(0.02);

    inputs.connected = true;

    if (sim.getAngularPosition() < minRawPosition) {
      sim.setState(minRawPosition, 0);
    } else if (sim.getAngularPosition() > maxRawPosition) {
      sim.setState(maxRawPosition, 0);
    }

    // rotor, radians
    inputs.rawPosition = sim.getAngularPosition();
    inputs.rawVelocity = sim.getAngularVelocity();
    inputs.rawAcceleration = sim.getAngularAcceleration();
    inputs.rawUnit = ratioConverter.getFromUnits();

    // mechanism, applied units
    inputs.appliedPosition = positionConvertor.applyAsDouble(inputs.rawPosition);
    inputs.appliedVelocity = ratioConverter.applyAsDouble(inputs.rawVelocity);
    inputs.appliedAcceleration = ratioConverter.applyAsDouble(inputs.rawAcceleration);
    inputs.appliedUnit = ratioConverter.getToUnits();

    inputs.outputVoltageVolts = sim.getInputVoltage();
    inputs.supplyCurrentAmps = sim.getCurrentDraw();
    inputs.statorCurrentAmps = sim.getCurrentDraw();
    inputs.temperatureCelsius = 25.0; // Simulated temperature
  }

  @Override
  public void setPidsg(double kp, double ki, double kd, double ks, double kg) {
    pid.setPID(kp, ki, kd);
  }

  @Override
  public void setPid(double kp, double ki, double kd) {
    pid.setPID(kp, ki, kd);
  }

  @Override
  public void setMotionConstraints(double maxRawVelocity, double maxRawAcceleration) {
    pid.setConstraints(new TrapezoidProfile.Constraints(maxRawVelocity, maxRawAcceleration));
  }

  public void setAppliedPositionConstraints(double minAppliedPosition, double maxAppliedPosition) {
    minRawPosition = positionConvertor.convertInverse(minAppliedPosition);
    maxRawPosition = positionConvertor.convertInverse(maxAppliedPosition);
  }

  @Override
  public void setRotationContinuous(boolean isContinuous) {
    if (isContinuous) {
      pid.enableContinuousInput(-Math.PI, Math.PI);
    } else {
      pid.disableContinuousInput();
    }
  }

  @Override
  public void setUnitConvertor(UnitConverter ratioConverter, UnitConverter... offsetConverter) {
    this.ratioConverter = ratioConverter;
    if (offsetConverter.length > 0) {
      this.positionConvertor = ratioConverter.andThen(offsetConverter[0]);
    } else {
      this.positionConvertor = ratioConverter;
    }
  }

  @Override
  public void setAppliedPositionF(
      double position, double velocity, double acceleration, double feedforward) {
    double pidOutput =
        pid.calculate(sim.getAngularPosition(), positionConvertor.convertInverse(position));
    setVoltage(pidOutput + feedforward);
  }

  @Override
  public void setAppliedVelocityF(double velocity, double acceleration, double feedforward) {
    double pidOutput =
        pid.calculate(sim.getAngularVelocity(), ratioConverter.convertInverse(velocity));
    setVoltage(pidOutput + feedforward);
  }

  @Override
  public void setVoltage(double volts) {
    if (RobotState.isEnabled()) {
      sim.setInputVoltage(voltageLimiter.calculate(volts));
    } else {
      voltageLimiter.reset(0);
      sim.setInputVoltage(0);
    }
  }

  @Override
  public void setCurrent(double amps) {
    double resistance = 0.1; // Simulated motor resistance
    double kv = 0.01; // Simulated back-EMF constant
    double voltage = amps * resistance + sim.getAngularVelocity() * kv;
    setVoltage(voltage);
  }

  @Override
  public void resetRawPosition(double position) {
    sim.setAngle(position);
  }

  @Override
  public void resetAppliedPosition(double position) {
    sim.setAngle(positionConvertor.convertInverse(position));
  }

  @Override
  public double getVoltage() {
    return sim.getInputVoltage();
  }

  @Override
  public double getCurrent() {
    return sim.getCurrentDraw();
  }

  @Override
  public double getAppliedPosition() {
    return positionConvertor.applyAsDouble(sim.getAngularPosition());
  }

  @Override
  public double getAppliedVelocity() {
    return ratioConverter.applyAsDouble(sim.getAngularVelocity());
  }

  @Override
  public double getAppliedAcceleration() {
    return ratioConverter.applyAsDouble(sim.getAngularAcceleration());
  }

  @Override
  public double getRawPosition() {
    return sim.getAngularPosition();
  }

  @Override
  public double getRawVelocity() {
    return sim.getAngularVelocity();
  }

  @Override
  public double getRawAcceleration() {
    return sim.getAngularAcceleration();
  }

  @Override
  public int getDeviceID() {
    return 0; // Simulation doesn't have device IDs
  }

  public DCMotorIOSim(
      DCMotor motor,
      double JKgMetersSquared,
      double gearing,
      UnitConverter ratioConverter,
      UnitConverter offsetConverter,
      Gains gains) {
    sim =
        new DCMotorSim(Models.singleJointedArmFromPhysicalConstants(motor, JKgMetersSquared, gearing), motor);

    setUnitConvertor(ratioConverter, offsetConverter);
    setGains(gains);
  }

  public DCMotorIOSim(
      DCMotor motor,
      double JKgMetersSquared,
      double gearing,
      UnitConverter ratioConverter,
      Gains gains) {
    this(
        motor,
        JKgMetersSquared,
        gearing,
        ratioConverter,
        UnitConverter.identity()
            .withUnits(ratioConverter.getToUnits(), ratioConverter.getToUnits()),
        gains);
  }

  public DCMotorIOSim(
      LinearSystem<N2, N1, N2> system,
      DCMotor motor,
      UnitConverter ratioConverter,
      UnitConverter offsetConverter,
      Gains gains) {
    sim = new DCMotorSim(system, motor);
    setUnitConvertor(ratioConverter, offsetConverter);
    setGains(gains);
  }

  public DCMotorIOSim(
      LinearSystem<N2, N1, N2> system, DCMotor motor, UnitConverter ratioConverter, Gains gains) {
    this(
        system,
        motor,
        ratioConverter,
        UnitConverter.identity()
            .withUnits(ratioConverter.getToUnits(), ratioConverter.getToUnits()),
        gains);
  }

  public DCMotorIOSim(
      DCMotor motor,
      double JKgMetersSquared,
      double gearing,
      UnitConverter ratioConverter,
      UnitConverter offsetConverter,
      Gains gains,
      double minAppliedPosition,
      double maxAppliedPosition) {
    this(motor, JKgMetersSquared, gearing, ratioConverter, offsetConverter, gains);
    setAppliedPositionConstraints(minAppliedPosition, maxAppliedPosition);
  }

  public DCMotorIOSim(
      LinearSystem<N2, N1, N2> system,
      DCMotor motor,
      UnitConverter ratioConverter,
      UnitConverter offsetConverter,
      Gains gains,
      double minAppliedPosition,
      double maxAppliedPosition) {
    this(system, motor, ratioConverter, offsetConverter, gains);
    setAppliedPositionConstraints(minAppliedPosition, maxAppliedPosition);
  }

  public DCMotorIOSim() {
    sim =
        new DCMotorSim(
            Models.singleJointedArmFromPhysicalConstants(DCMotor.getNEO(1), 0.0001, 1), DCMotor.getNEO(1));
  }
}
