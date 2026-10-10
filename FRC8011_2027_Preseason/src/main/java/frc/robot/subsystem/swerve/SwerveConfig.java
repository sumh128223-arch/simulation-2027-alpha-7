package frc.robot.subsystem.swerve;

import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.util.Units;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;
import frc.robot.Constants.DebugGroup;

public class SwerveConfig {
  protected static final LoggedTunableNumber maxTiltAccelXMeterPerSecPerLoop =
      new LoggedTunableNumber(DebugGroup.SWERVE, "Swerve/MaxTiltAccelXMeterPerSecPerLoop", 80.0);
  protected static final LoggedTunableNumber maxTiltAccelYMeterPerSecPerLoop =
      new LoggedTunableNumber(DebugGroup.SWERVE, "Swerve/MaxTiltAccelYMeterPerSecPerLoop", 70.0);
  protected static final LoggedTunableNumber maxSkidAccelMeterPerSecPerLoop =
      new LoggedTunableNumber(
          DebugGroup.SWERVE,
          "Swerve/MaxSkidAccelMeterPerSecPerLoop",
          SwerveConfig.MAX_TRANSLATION_VEL_METER_PER_SEC * 2.0 / Constants.LOOP_PERIOD_SEC);

  static final String FL_MODULE_NAME = "FL";
  static final String BL_MODULE_NAME = "BL";
  static final String BR_MODULE_NAME = "BR";
  static final String FR_MODULE_NAME = "FR";

  static final double ODOMETRY_FREQUENCY_HZ = 250.0;

  static final boolean ENABLE_TRAJECTORY_FF = true;

  public static final double WHEELBASE_LENGTH_METER = 0.559350;
  public static final double WHEELBASE_WIDTH_METER = 0.559350;
  public static final double WHEELBASE_DIAGONAL_METER =
      Math.hypot(WHEELBASE_LENGTH_METER, WHEELBASE_WIDTH_METER);

  public static final double MAX_TRANSLATION_VEL_METER_PER_SEC = Units.feetToMeters(22.80);
  public static final double MAX_ANGULAR_VEL_RAD_PER_SEC =
      MAX_TRANSLATION_VEL_METER_PER_SEC / (WHEELBASE_DIAGONAL_METER / 2.0);

  public static final SwerveDriveKinematics SWERVE_KINEMATICS =
      new SwerveDriveKinematics(
          new Translation2d(WHEELBASE_LENGTH_METER / 2.0, WHEELBASE_WIDTH_METER / 2.0),
          new Translation2d(-WHEELBASE_LENGTH_METER / 2.0, WHEELBASE_WIDTH_METER / 2.0),
          new Translation2d(-WHEELBASE_LENGTH_METER / 2.0, -WHEELBASE_WIDTH_METER / 2.0),
          new Translation2d(WHEELBASE_LENGTH_METER / 2.0, -WHEELBASE_WIDTH_METER / 2.0));

  /**
   * If the Pigeon (or other gyro) is not mounted at the robot center, set the translation from
   * robot center to the gyro here (meters). Positive X is forward, positive Y is to the left
   * (standard WPILib robot coordinates).
   *
   * <p>Default is (0, 0) meaning gyro is at center. Fill in the measured offset for your robot when
   * known.
   */
  public static final Translation2d PIGEON_OFFSET = new Translation2d(0.0, 0.0);

  static final double WHEEL_RADIUS_METER = 0.0508;
  // 先全部设置为0.0，然后在AScope中读取SteerAbsPosition数值val，(pi - val) / 2 / pi就是对应的offset
  static final ModuleConfig FL_MODULE_CONFIG =
      new ModuleConfig(
          getX2iDriveTalonConfig(),
          getX2iSteerTalonNoEncoderConfig(),
          getCancoderConfig(0.214)); // FIXME: updated
  static final ModuleConfig BL_MODULE_CONFIG =
      new ModuleConfig(
          getX2iDriveTalonConfig(),
          getX2iSteerTalonNoEncoderConfig(),
          getCancoderConfig(0.97)); // FIXME: updated
  static final ModuleConfig BR_MODULE_CONFIG =
      new ModuleConfig(
          getX2iDriveTalonConfig(),
          getX2iSteerTalonNoEncoderConfig(),
          getCancoderConfig(0.622)); // FIXME: updated
  static final ModuleConfig FR_MODULE_CONFIG =
      new ModuleConfig(
          getX2iDriveTalonConfig(),
          getX2iSteerTalonNoEncoderConfig(),
          getCancoderConfig(0.035)); // FIXME: updated

  // https://docs.wcproducts.com/welcome/gearboxes/wcp-swerve-x/general-info/ratio-options
  static final double DRIVE_REDUCTION = 4.59; // seems correct
  static final double STEER_REDUCTION = 13.3714; // seems correct

  static final double DRIVE_FF_KT =
      DCMotor.getKrakenX60Foc(1).withReduction(DRIVE_REDUCTION).Kt;

  record ModuleConfig(
      TalonFXConfiguration driveTalonConfig,
      TalonFXConfiguration steerTalonConfig,
      CANcoderConfiguration cancoderConfig) {}

  static Gains getDriveGains() {
    return switch (Constants.MODE) {
      case REAL -> new Gains(7.0, 0.0, 0.0);
      case SIM, REPLAY -> new Gains(0.3, 0.0, 0.0);
    };
  }

  static Gains getSteerGains() {
    return switch (Constants.MODE) {
      case REAL -> new Gains(2000.0, 50.0, 0.0);
      case SIM, REPLAY -> new Gains(10.0, 0.0, 0.0);
    };
  }

  record Gains(double kp, double kd, double ks) {}

  static TalonFXConfiguration getX2iDriveTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;

    var gains = getDriveGains();
    config.Slot0 = new Slot0Configs().withKP(gains.kp()).withKD(gains.kd()).withKS(gains.ks());

    config.TorqueCurrent.PeakForwardTorqueCurrent = 120.0;
    config.TorqueCurrent.PeakReverseTorqueCurrent = -120.0;

    config.CurrentLimits.SupplyCurrentLimit = 40.0;
    config.CurrentLimits.SupplyCurrentLimitEnable = false;
    config.CurrentLimits.StatorCurrentLimit = 200.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    config.Feedback.SensorToMechanismRatio = DRIVE_REDUCTION;

    return config;
  }

  static TalonFXConfiguration getX2iSteerTalonNoEncoderConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;

    var gains = getSteerGains();
    config.Slot0 = new Slot0Configs().withKP(gains.kp()).withKD(gains.kd()).withKS(gains.ks());
    config.TorqueCurrent.PeakForwardTorqueCurrent = 80.0;
    config.TorqueCurrent.PeakReverseTorqueCurrent = -80.0;

    config.CurrentLimits.SupplyCurrentLimit = 40.0;
    config.CurrentLimits.SupplyCurrentLimitEnable = false;
    config.CurrentLimits.StatorCurrentLimit = 100.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    config.Feedback.RotorToSensorRatio = STEER_REDUCTION;

    config.ClosedLoopGeneral.ContinuousWrap = true;

    return config;
  }

  private static CANcoderConfiguration getCancoderConfig(double magnetOffset) {
    var config = new CANcoderConfiguration();
    config.MagnetSensor.MagnetOffset = magnetOffset;

    return config;
  }
}
