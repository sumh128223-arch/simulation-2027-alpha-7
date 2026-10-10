package frc.robot.subsystem.swerve.command;

import org.wpilib.math.util.MathUtil;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.util.Units;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.command2.Command;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.rebuilt.Field;
import frc.robot.Constants;
import frc.robot.RobotContainer;
import frc.robot.subsystem.swerve.Swerve;
import frc.robot.subsystem.swerve.SwerveConfig;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;

public class TeleopController extends Command {
  private static final LoggedTunableNumber translationDeadband =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/TranslationDeadband", 0.12);
  private static final LoggedTunableNumber maxTranslationScalar =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/MaxTranslationScalar", 1.0);
  private static final LoggedTunableNumber rotationDeadband =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/RotationDeadband", 0.08);
  private static final LoggedTunableNumber maxRotationScalar =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/MaxRotationScalar", 0.8);

  private static final LoggedTunableNumber headingMaintainerKp =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/HeadingMaintainer/Kp", 5.0);
  private static final LoggedTunableNumber headingMaintainerKd =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/HeadingMaintainer/Kd", 0.0);
  private static final LoggedTunableNumber headingMaintainerToleranceDegree =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE,
          "Swerve/TeleopController/HeadingMaintainer/ToleranceDegree",
          5.0);
  private static final LoggedTunableNumber headingMaintainerMinOutputDegreePerSec =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE,
          "Swerve/TeleopController/HeadingMaintainer/MinOutputDegreePerSec",
          1.0);
  private static final LoggedTunableNumber headingMaintainerMaxOutputDegreePerSec =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE,
          "Swerve/TeleopController/HeadingMaintainer/MaxOutputDegreePerSec",
          360.0);

  private static final LoggedTunableNumber aimToShakeCompensation =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/TeleopController/AimToShakeCompensation", 0.25);

  private final Swerve swerve;
  private final Supplier<Double> xSupplier;
  private final Supplier<Double> ySupplier;
  private final Supplier<Double> omegaSupplier;

  private boolean aimToHub = false;
  private boolean aimToAngle = false;

  private Rotation2d targetAimAngle = new Rotation2d();
  private final PIDController headingMaintainer;

  private int shakingCount = 0;
  private boolean aimToShake = false;

  public TeleopController(
      Swerve swerve,
      Supplier<Double> xSupplier,
      Supplier<Double> ySupplier,
      Supplier<Double> omegaSupplier) {
    this.swerve = swerve;
    this.xSupplier = xSupplier;
    this.ySupplier = ySupplier;
    this.omegaSupplier = omegaSupplier;

    headingMaintainer =
        new PIDController(
            headingMaintainerKp.get(), 0.0, headingMaintainerKd.get(), Constants.LOOP_PERIOD_SEC);
    headingMaintainer.enableContinuousInput(-Math.PI, Math.PI);
    headingMaintainer.setTolerance(Units.degreesToRadians(headingMaintainerToleranceDegree.get()));

    addRequirements(swerve);
  }

  @Override
  public void execute() {
    Logger.recordOutput("Swerve/TeleopController/TranslationX", xSupplier.get());
    Logger.recordOutput("Swerve/TeleopController/TranslationY", ySupplier.get());
    Logger.recordOutput("Swerve/TeleopController/Rotation", omegaSupplier.get());

    var translation = calcTranslation();
    var rotation = calcRotation();

    headingMaintainer.setPID(headingMaintainerKp.get(), 0.0, headingMaintainerKd.get());
    headingMaintainer.setTolerance(Units.degreesToRadians(headingMaintainerToleranceDegree.get()));

    var yawVelRadPerSec = RobotContainer.getOdometry().getRobotCentricVel().dtheta;
    var yawRad = RobotContainer.getOdometry().getEstimatedPose().getRotation().getRadians();

    // if (EqualsUtil.epsilonEquals(rotation, 0.0, 0.0)
    // && EqualsUtil.epsilonEquals(
    // yawVelRadPerSec,
    // 0.0,
    // Units.degreesToRadians(headingMaintainerMinEnableVelDegreePerSec.get()))) {
    // } else {
    // headingMaintainer.reset();
    // }

    if (MatchState.getAlliance().isPresent()
        && MatchState.getAlliance().get() == Alliance.RED) {
      translation = translation.rotateBy(Rotation2d.fromRadians(Math.PI));
    }

    var headingMaintainerMaxOutputRadPerSec =
        Units.degreesToRadians(headingMaintainerMaxOutputDegreePerSec.get());

    double targetAngleRad = 0.0;
    boolean useMaintainer = false;

    if (aimToHub) {
      targetAngleRad =
          Field.Hub.getCenter()
              .minus(RobotContainer.getOdometry().getEstimatedPose().getTranslation())
              .getAngle().orElse(RobotContainer.getOdometry().getEstimatedPose().getRotation())
              .getRadians();
      useMaintainer = true;
    } else if (aimToAngle) {
      targetAngleRad = frc.rebuilt.Field.AllianceFlipUtil.apply(targetAimAngle).getRadians();
      useMaintainer = true;
    }

    double shakingCompen = 0.0;
    if (aimToShake) {
      shakingCount++;
      if (shakingCount % 14 < 7) {
        shakingCompen = aimToShakeCompensation.get();
      } else {
        shakingCompen = -aimToShakeCompensation.get();
      }
    }
    Logger.recordOutput("Swerve/TeleopController/AimToShake", aimToShake);
    Logger.recordOutput("Swerve/TeleopController/ShakingCount", shakingCount);
    Logger.recordOutput("Swerve/TeleopController/ShakingCompensation", shakingCompen);

    var headingMaintainerOutput =
        useMaintainer
            ? Math.clamp(
                MathUtil.applyDeadband(
                    headingMaintainer.calculate(yawRad - targetAngleRad),
                    Units.degreesToRadians(headingMaintainerMinOutputDegreePerSec.get())),
                -headingMaintainerMaxOutputRadPerSec,
                headingMaintainerMaxOutputRadPerSec)
            : 0.0;
    Logger.recordOutput(
        "Swerve/TeleopController/HeadingMaintainer/Output", headingMaintainerOutput);
    Logger.recordOutput("Swerve/TeleopController/HeadingMaintainer/AimToHub", aimToHub);

    ChassisVelocities speeds =
        new ChassisVelocities(
            translation.getX() * SwerveConfig.MAX_TRANSLATION_VEL_METER_PER_SEC,
            translation.getY() * SwerveConfig.MAX_TRANSLATION_VEL_METER_PER_SEC,
            (rotation + shakingCompen) * SwerveConfig.MAX_ANGULAR_VEL_RAD_PER_SEC
                + headingMaintainerOutput).toRobotRelative(RobotContainer.getOdometry().getEstimatedPose().getRotation());

    swerve.setGoalVel(speeds, false);
  }

  private Translation2d calcTranslation() {
    double x = xSupplier.get();
    double y = ySupplier.get();
    var magnitude =
        MathUtil.applyDeadband(Math.min(Math.hypot(x, y), 1.0), translationDeadband.get())
            * maxTranslationScalar.get();
    var direction = Math.hypot(x, y) < 1e-16 ? new Rotation2d() : new Rotation2d(x, y);

    return new Translation2d(magnitude * magnitude, direction);
  }

  private double calcRotation() {
    double omega = omegaSupplier.get();
    var magnitude = MathUtil.applyDeadband(omega, rotationDeadband.get()) * maxRotationScalar.get();

    return Math.copySign(magnitude * magnitude, magnitude);
  }

  @Override
  public void end(boolean interrupted) {
    swerve.stop();
  }

  public void toggleAimToHub() {
    aimToHub = !aimToHub;
    this.aimToAngle = false;
  }

  public void setAimToHub(boolean aimToHub) {
    this.aimToHub = aimToHub;
    this.aimToAngle = false;
  }

  public boolean isAimToHubAtGoal() {
    if (!aimToHub) {
      return true;
    }
    var currentPose = RobotContainer.getOdometry().getEstimatedPose();
    var targetAngle =
        Field.Hub.getCenter().minus(currentPose.getTranslation()).getAngle().orElse(currentPose.getRotation()).getRadians();
    var angleErrorDegree = Math.toDegrees(currentPose.getRotation().getRadians() - targetAngle);
    // 归一化到[-180,180]
    angleErrorDegree = ((angleErrorDegree + 180) % 360 + 360) % 360 - 180;
    return Math.abs(angleErrorDegree) <= headingMaintainerToleranceDegree.get();
  }

  public void setAimToAngle(Rotation2d angle) {
    this.aimToAngle = true;
    if (aimToAngle) {
      this.targetAimAngle = angle;
    }
    this.aimToHub = false;
  }

  public void resetAimToAngle() {
    this.aimToAngle = false;
    this.targetAimAngle = new Rotation2d();
  }

  public boolean isAimToAngleAtGoal() {
    if (!aimToAngle) {
      return true;
    }
    var currentPose = RobotContainer.getOdometry().getEstimatedPose();
    var targetAngle = frc.rebuilt.Field.AllianceFlipUtil.apply(targetAimAngle).getRadians();
    var angleErrorDegree = Math.toDegrees(currentPose.getRotation().getRadians() - targetAngle);
    // 归一化到[-180,180]
    angleErrorDegree = ((angleErrorDegree + 180) % 360 + 360) % 360 - 180;
    return Math.abs(angleErrorDegree) <= headingMaintainerToleranceDegree.get();
  }

  public void setAimToShake(boolean aimToShake) {
    this.aimToShake = aimToShake;
    this.shakingCount = 0;
  }
}
