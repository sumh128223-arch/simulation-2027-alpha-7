package frc.robot.subsystem.swerve.command;

import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.command2.Command;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;
import frc.robot.RobotContainer;
import frc.robot.subsystem.swerve.Swerve;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import lombok.Getter;
import org.littletonrobotics.junction.Logger;

public class ProceedToPoint extends Command {
  // Tunable PID参数
  private static final LoggedTunableNumber TRANSLATION_KP =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/TranslationKp", 2.0);
  private static final LoggedTunableNumber TRANSLATION_KD =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/TranslationKd", 0.1);
  private static final LoggedTunableNumber TRANSLATION_TOLERANCE_METER =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/TranslationToleranceMeter", 0.15);
  private static final LoggedTunableNumber MAX_TRANSLATION_VEL =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/MaxTranslationVelMeterPerSec", 3.0);
  private DoubleSupplier maxTranslationVelSupplier = () -> 0.0;

  private static final LoggedTunableNumber ROTATION_KP =
      new LoggedTunableNumber(Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/RotationKp", 4.0);
  private static final LoggedTunableNumber ROTATION_KD =
      new LoggedTunableNumber(Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/RotationKd", 0.1);
  private static final LoggedTunableNumber ROTATION_TOLERANCE_DEGREE =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/RotationToleranceDegree", 7.0);
  private static final LoggedTunableNumber MAX_ROTATION_VEL_DEG =
      new LoggedTunableNumber(
          Constants.DebugGroup.SWERVE, "Swerve/ProceedToPoint/MaxRotationDegreePerSec", 360.0);
  private DoubleSupplier maxRotationVelDegSupplier = () -> 0.0;

  private final Swerve swerve;
  private final Supplier<Pose2d> goalPoseSupplier;
  private final PIDController xController;
  private final PIDController yController;
  private final PIDController rotationController;

  @Getter private boolean hasDone = false;
  private double translationErrorMeter = Double.POSITIVE_INFINITY;
  private boolean hasHeadingAtGoal = false;

  public ProceedToPoint(Swerve swerve, Supplier<Pose2d> goalPoseSupplier) {
    this.swerve = swerve;
    this.goalPoseSupplier = goalPoseSupplier;

    xController = new PIDController(TRANSLATION_KP.get(), 0.0, TRANSLATION_KD.get());
    yController = new PIDController(TRANSLATION_KP.get(), 0.0, TRANSLATION_KD.get());
    rotationController = new PIDController(ROTATION_KP.get(), 0.0, ROTATION_KD.get());
    rotationController.enableContinuousInput(-Math.PI, Math.PI);

    addRequirements(swerve);
  }

  public ProceedToPoint(
      Swerve swerve,
      Supplier<Pose2d> goalPoseSupplier,
      DoubleSupplier maxTranslationVelSupplier,
      DoubleSupplier maxRotationVelDegSupplier) {
    this(swerve, goalPoseSupplier);
    this.maxTranslationVelSupplier = maxTranslationVelSupplier;
    this.maxRotationVelDegSupplier = maxRotationVelDegSupplier;
  }

  @Override
  public void execute() {
    // 若参数有变动，动态更新PID参数
    xController.setP(TRANSLATION_KP.get());
    xController.setD(TRANSLATION_KD.get());
    yController.setP(TRANSLATION_KP.get());
    yController.setD(TRANSLATION_KD.get());
    rotationController.setP(ROTATION_KP.get());
    rotationController.setD(ROTATION_KD.get());

    var goalPose = goalPoseSupplier.get();
    Logger.recordOutput("Swerve/ProceedToPoint/GoalPose", goalPose);
    var currentPose = RobotContainer.getOdometry().getEstimatedPose();

    // 位置PID
    double xFeedback = xController.calculate(currentPose.getX(), goalPose.getX());
    double yFeedback = yController.calculate(currentPose.getY(), goalPose.getY());

    // 角度PID（-pi~pi）
    double currentAngle = currentPose.getRotation().getRadians();
    double goalAngle = goalPose.getRotation().getRadians();
    double rotationFeedback = rotationController.calculate(currentAngle, goalAngle);

    translationErrorMeter = currentPose.getTranslation().getDistance(goalPose.getTranslation());
    double rotationErrorDegree = Math.toDegrees(currentAngle - goalAngle);
    // 归一化到[-180,180]
    rotationErrorDegree = ((rotationErrorDegree + 180) % 360 + 360) % 360 - 180;

    hasHeadingAtGoal = Math.abs(rotationErrorDegree) <= ROTATION_TOLERANCE_DEGREE.get();
    hasDone = translationErrorMeter <= TRANSLATION_TOLERANCE_METER.get() && hasHeadingAtGoal;
    Logger.recordOutput("Swerve/ProceedToPoint/HasRotationAtGoal", hasHeadingAtGoal);
    Logger.recordOutput(
        "Swerve/ProceedToPoint/HasTranslationAtGoal",
        translationErrorMeter <= TRANSLATION_TOLERANCE_METER.get());
    Logger.recordOutput("Swerve/ProceedToPoint/HasDone", hasDone);

    if (!hasDone) {
      // 限制最大速度
      double maxTranslationVel =
          maxTranslationVelSupplier.getAsDouble() > 0.0
              ? maxTranslationVelSupplier.getAsDouble()
              : MAX_TRANSLATION_VEL.get();
      double maxRotationVelDeg =
          maxRotationVelDegSupplier.getAsDouble() > 0.0
              ? maxRotationVelDegSupplier.getAsDouble()
              : MAX_ROTATION_VEL_DEG.get();
      double vx = Math.max(-maxTranslationVel, Math.min(maxTranslationVel, xFeedback));
      double vy = Math.max(-maxTranslationVel, Math.min(maxTranslationVel, yFeedback));
      double maxOmega = Math.toRadians(maxRotationVelDeg);
      double omega = Math.max(-maxOmega, Math.min(maxOmega, rotationFeedback));

      Logger.recordOutput("Swerve/ProceedToPoint/vx", vx);
      Logger.recordOutput("Swerve/ProceedToPoint/vy", vy);
      Logger.recordOutput("Swerve/ProceedToPoint/omega", omega);

      ChassisVelocities speeds =
          new ChassisVelocities(vx, vy, omega).toRobotRelative(currentPose.getRotation());
      swerve.setGoalVel(speeds, false);
    } else {
      swerve.stop();
    }
  }

  @Override
  public boolean isFinished() {
    return hasDone;
  }

  @Override
  public void end(boolean interrupted) {
    swerve.stop();
  }

  public boolean hasHeadingAtGoal() {
    return hasHeadingAtGoal;
  }

  public boolean hasDistanceWithin(double toleranceMeter) {
    return Math.abs(translationErrorMeter) <= toleranceMeter;
  }
}
