package frc.robot.subsystem.shooter;

import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;

public class ShooterConfig {
  // ----- Flywheel Tunable -----
  protected static final LoggedTunableNumber lowerFlywheelKp =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/LowerFlywheel/Kp");
  protected static final LoggedTunableNumber lowerFlywheelKd =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/LowerFlywheel/Kd");
  protected static final LoggedTunableNumber lowerFlywheelKs =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/LowerFlywheel/Ks");
  protected static final LoggedTunableNumber lowerFlywheelFf =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/LowerFlywheel/Ff");

  protected static final LoggedTunableNumber upperFlywheelKp =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/UpperFlywheel/Kp");
  protected static final LoggedTunableNumber upperFlywheelKd =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/UpperFlywheel/Kd");
  protected static final LoggedTunableNumber upperFlywheelKs =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/UpperFlywheel/Ks");
  protected static final LoggedTunableNumber upperFlywheelFf =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/UpperFlywheel/Ff");

  protected static final LoggedTunableNumber launchLowerFeedVolts =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/Launch/LowerFeed/Volts", -8.0);
  protected static final LoggedTunableNumber launchUpperFeedVolts =
      new LoggedTunableNumber(Constants.DebugGroup.SHOOTER, "Shooter/Launch/UpperFeed/Volts", 7.5);

  protected static final LoggedTunableNumber outtakeLowerFeedVolts =
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER, "Shooter/Outtake/LowerFeed/Volts", -1.0);
  protected static final LoggedTunableNumber outtakeUpperFeedVolts =
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER, "Shooter/Outtake/UpperFeed/Volts", -1.0);

  // ----- Geometry -----
  protected static final double LOWER_FLYWHEEL_REDUCTION = 24.0 / 16.0;
  protected static final double UPPER_FLYWHEEL_REDUCTION = 18.0 / 18.0;

  // ----- Gains -----
  static FlywheelGains getLowerFlywheelGains() {
    return switch (Constants.MODE) {
      case REAL -> new FlywheelGains(0.4, 0.0, 2.2, 0.0);
      case SIM, REPLAY -> new FlywheelGains(0.5, 0.0, 0.0, 0.0);
    };
  }

  static FlywheelGains getUpperFlywheelGains() {
    return switch (Constants.MODE) {
      case REAL -> new FlywheelGains(0.4, 0.0, 0.0, 0.0);
      case SIM, REPLAY -> new FlywheelGains(0.5, 0.0, 0.0, 0.0);
    };
  }

  // ----- TalonFX Configs -----

  /** Lower flywheel master config. The follower is inverted (opposite direction on same shaft). */
  static TalonFXConfiguration getLowerFlywheelTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Coast;
    config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;

    var gains = getLowerFlywheelGains();
    config.Slot0 =
        new Slot0Configs()
            .withKP(gains.kp())
            .withKD(gains.kd())
            .withKS(gains.ks())
            .withKV(gains.ff());

    config.CurrentLimits.StatorCurrentLimit = 60.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    config.Feedback.SensorToMechanismRatio = LOWER_FLYWHEEL_REDUCTION;

    return config;
  }

  static TalonFXConfiguration getUpperFlywheelTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Coast;
    config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

    var gains = getUpperFlywheelGains();
    config.Slot0 =
        new Slot0Configs()
            .withKP(gains.kp())
            .withKD(gains.kd())
            .withKS(gains.ks())
            .withKV(gains.ff());

    config.CurrentLimits.StatorCurrentLimit = 60.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    config.Feedback.SensorToMechanismRatio = UPPER_FLYWHEEL_REDUCTION;

    return config;
  }

  static TalonFXConfiguration getFeedTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

    config.CurrentLimits.StatorCurrentLimit = 80.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    return config;
  }

  record FlywheelGains(double kp, double kd, double ks, double ff) {}
}
