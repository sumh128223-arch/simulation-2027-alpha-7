package frc.robot.subsystem.intake;

import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.signals.GravityTypeValue;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;

public class IntakeConfig {
  // ----- Arm Geometry -----
  protected static final double MIN_ANGLE_DEGREE = 0.0;
  protected static final double INIT_ANGLE_DEGREE = 99.0;
  protected static final double MAX_ANGLE_DEGREE = 99.0;
  protected static final double PIVOT_REDUCTION = (36.0 / 15.0) * (60.0 / 18.0) * (60.0 / 8.0);

  // ----- Tunable Defaults -----
  protected static final LoggedTunableNumber slamSettleVelocityRadPerSec =
      new LoggedTunableNumber(
          Constants.DebugGroup.INTAKE, "Intake/SlamArm/SettleVelocityRadPerSec", 5.0);
  protected static final LoggedTunableNumber slamVelocityDebounceTime =
      new LoggedTunableNumber(Constants.DebugGroup.INTAKE, "Intake/SlamArm/DebounceTime", 0.1);
  protected static final LoggedTunableNumber slamBumpTimeSecs =
      new LoggedTunableNumber(Constants.DebugGroup.INTAKE, "Intake/SlamArm/BumpTimeSecs", 0.25);
  protected static final LoggedTunableNumber slamBumpRestTimeSecs =
      new LoggedTunableNumber(Constants.DebugGroup.INTAKE, "Intake/SlamArm/BumpRestTimeSecs", 0.25);

  protected static final LoggedTunableNumber slamDeployCurrentAmp =
      new LoggedTunableNumber(Constants.DebugGroup.INTAKE, "Intake/SlamArm/DeployCurrentAmp", -5.0);
  protected static final LoggedTunableNumber slamRetractCurrentAmp =
      new LoggedTunableNumber(
          Constants.DebugGroup.INTAKE, "Intake/SlamArm/RetractCurrentAmp", 10.0);

  protected static final LoggedTunableNumber rollerIntakeVolts =
      new LoggedTunableNumber(Constants.DebugGroup.INTAKE, "Intake/Roller/IntakeVolts", -8.0);
  protected static final LoggedTunableNumber rollerOuttakeVolts =
      new LoggedTunableNumber(Constants.DebugGroup.INTAKE, "Intake/Roller/OuttakeVolts", 4.0);

  // ----- Gains -----
  static Gains getPivotGains() {
    return switch (Constants.MODE) {
      case REAL -> new Gains(130.0, 50.0, 0.0, 0.0);
      case SIM, REPLAY -> new Gains(20.0, 0.4, 0.0, 0.0);
    };
  }

  // ----- TalonFX Configs -----
  static TalonFXConfiguration getPivotTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

    var gains = getPivotGains();
    config.Slot0 =
        new Slot0Configs()
            .withKP(gains.kp())
            .withKD(gains.kd())
            .withKS(gains.ks())
            .withKG(gains.kg())
            .withGravityType(GravityTypeValue.Arm_Cosine);

    config.TorqueCurrent.PeakForwardTorqueCurrent = 150.0;
    config.TorqueCurrent.PeakReverseTorqueCurrent = -150.0;

    config.CurrentLimits.StatorCurrentLimit = 60.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    config.Feedback.SensorToMechanismRatio = PIVOT_REDUCTION;
    config.ClosedLoopGeneral.ContinuousWrap = false;

    return config;
  }

  static TalonFXConfiguration getRollerTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Coast;
    config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

    config.CurrentLimits.StatorCurrentLimit = 60.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    return config;
  }

  record Gains(double kp, double kd, double ks, double kg) {}
}
