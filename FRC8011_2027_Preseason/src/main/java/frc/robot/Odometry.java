package frc.robot;

import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.util.Nat;
import org.wpilib.math.linalg.VecBuilder;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Transform2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Twist2d;
import org.wpilib.math.interpolation.TimeInterpolatableBuffer;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.system.Timer;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.lib.interfaces.VirtualSubsystem;
import frc.rebuilt.Field;
import frc.robot.subsystem.swerve.SwerveConfig;
import frc.robot.subsystem.vision.ApriltagVision;
import frc.robot.subsystem.vision.ApriltagVision.SingleTagVisionObservation;
import frc.robot.subsystem.vision.ApriltagVision.VisionObservation;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

public class Odometry extends VirtualSubsystem {
  public record WheeledObservation(
      double timestamp, SwerveModulePosition[] wheelPositions, Rotation2d yaw) {}

  private static final LoggedTunableNumber txTyObservationStaleSecs =
      new LoggedTunableNumber(
          Constants.DebugGroup.ODOMETRY, "Odometry/TxTyObservation/StaleSeconds", 0.2);
  private static final LoggedTunableNumber txTyObservationMaxAllowedDistance =
      new LoggedTunableNumber(
          Constants.DebugGroup.ODOMETRY, "Odometry/TxTyObservation/MaxAllowedDistance", 2.0);

  private static final double POSE_BUFFER_SIZE_SEC = 2.0;
  private static final Matrix<N3, N1> WHEELED_STD_DEVS =
      new Matrix<>(VecBuilder.fill(0.003, 0.003, 0.0002));

  @Getter private Pose2d wheeledPose;
  @Getter private Pose2d estimatedPose;
  @Getter @Setter private Twist2d robotCentricVel = new Twist2d();
  @Setter private Twist2d trajectoryVel = new Twist2d();

  private final HashMap<ApriltagVision.CameraId, HashMap<Integer, SingleTagVisionObservation>>
      singleTagPoses = new HashMap<>();
  private final TimeInterpolatableBuffer<Pose2d> poseBuffer =
      TimeInterpolatableBuffer.createBuffer(POSE_BUFFER_SIZE_SEC);
  private final Matrix<N3, N1> qStdDevs = new Matrix<>(Nat.N3(), Nat.N1());

  private final SwerveDriveKinematics kinematics;
  private SwerveModulePosition[] lastWheelPositions =
      new SwerveModulePosition[] {
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition(),
        new SwerveModulePosition()
      };
  private Rotation2d lastGyroYaw = new Rotation2d();

  public Odometry() {
    for (int i = 0; i < 3; ++i) {
      qStdDevs.set(i, 0, Math.pow(WHEELED_STD_DEVS.get(i, 0), 2));
    }
    kinematics = SwerveConfig.SWERVE_KINEMATICS;
    resetPose(new Pose2d(Field.LENGTH / 2.0, Field.WIDTH / 2.0, Rotation2d.fromDegrees(0.0)));
  }

  public Command resetPoseCommand(Supplier<Pose2d> pose) {
    return Commands.runOnce(() -> resetPose(Field.AllianceFlipUtil.apply(pose.get())))
        .ignoringDisable(true);
  }

  public void resetPose(Pose2d initialPose) {
    estimatedPose = initialPose;
    wheeledPose = initialPose;
    poseBuffer.clear();

    singleTagPoses.clear();

    singleTagPoses.put(ApriltagVision.CameraId.FRONT, new HashMap<>());
    singleTagPoses.put(ApriltagVision.CameraId.BACK, new HashMap<>());

    for (var entry : singleTagPoses.entrySet()) {
      var map = entry.getValue();
      for (int i = 1; i <= Field.APRILTAG_COUNT; i++) {
        map.put(
            i, new SingleTagVisionObservation(-999.0, new Pose2d(), i, Double.POSITIVE_INFINITY));
      }
    }
  }

  public void addWheeledObservation(WheeledObservation observation) {
    var wheelPositions = observation.wheelPositions();
    var twist = kinematics.toTwist2d(lastWheelPositions, wheelPositions);
    lastWheelPositions = wheelPositions;
    if (observation.yaw() != null) {
      // Replace wheel-derived dtheta with gyro-observed dtheta. If the gyro
      // isn't mounted at the robot center, rotating that offset produces an
      // apparent translation of the robot center which must be compensated.
      var gyroDelta = observation.yaw().minus(lastGyroYaw).getRadians();

      // Compute the translation caused by rotating the pigeon offset. This is
      // r_rotated - r, where r is the vector from robot center to gyro.
      var offset = frc.robot.subsystem.swerve.SwerveConfig.PIGEON_OFFSET;
      var offsetDelta =
          offset.rotateBy(new org.wpilib.math.geometry.Rotation2d(gyroDelta)).minus(offset);

      // Subtract the offset translation from the wheel-derived translation
      // because the wheel-based twist represents motion of the robot center
      // while the gyro rotation about its offset would otherwise introduce
      // extra translation at the gyro's location.
      var correctedDx = twist.dx - offsetDelta.getX();
      var correctedDy = twist.dy - offsetDelta.getY();

      twist = new Twist2d(correctedDx, correctedDy, gyroDelta);
      lastGyroYaw = observation.yaw();
    }
    wheeledPose = wheeledPose.plus(twist.exp());
    poseBuffer.addSample(observation.timestamp(), wheeledPose);
    estimatedPose = estimatedPose.plus(twist.exp());
  }

  public Optional<Pose2d> getWheeledPoseByTimestamp(double timestamp) {
    return poseBuffer.getSample(timestamp);
  }

  public void addVisionObservation(VisionObservation observation) {
    try {
      if (poseBuffer.getInternalBuffer().lastKey() - POSE_BUFFER_SIZE_SEC
          > observation.timestamp()) {
        return;
      }
    } catch (NoSuchElementException ex) {
      return;
    }

    var sample = poseBuffer.getSample(observation.timestamp());
    if (sample.isEmpty()) {
      return;
    }

    var old2NowWheeledPoseTransform = new Transform2d(sample.get(), wheeledPose);
    var now2OldWheeledPoseTransform = new Transform2d(wheeledPose, sample.get());
    var oldEstimatedPose = estimatedPose.plus(now2OldWheeledPoseTransform);

    var r = new double[3];
    for (int i = 0; i < 3; ++i) {
      r[i] = observation.stdDevs().get(i, 0) * observation.stdDevs().get(i, 0);
    }
    var visionK = new Matrix<>(Nat.N3(), Nat.N3());
    for (int row = 0; row < 3; ++row) {
      double stdDev = qStdDevs.get(row, 0);
      if (stdDev == 0.0) {
        visionK.set(row, row, 0.0);
      } else {
        visionK.set(row, row, stdDev / (stdDev + Math.sqrt(stdDev * r[row])));
      }
    }

    var transform = new Transform2d(oldEstimatedPose, observation.pose());
    var kTimesTransform =
        visionK.times(
            VecBuilder.fill(
                transform.getX(), transform.getY(), transform.getRotation().getRadians()));
    var scaledTransform =
        new Transform2d(
            kTimesTransform.get(0, 0),
            kTimesTransform.get(1, 0),
            Rotation2d.fromRadians(kTimesTransform.get(2, 0)));
    estimatedPose = oldEstimatedPose.plus(scaledTransform).plus(old2NowWheeledPoseTransform);
  }

  public void addSingleTagObservation(
      ApriltagVision.CameraId id, SingleTagVisionObservation observation) {
    var map = singleTagPoses.get(id);

    if (map.get(observation.id()).timestamp() >= observation.timestamp()) {
      return;
    }

    map.put(observation.id(), observation);
  }

  public Optional<Pose2d> getSingleTagPose(ApriltagVision.CameraId cameraId, int tagId) {
    if (!Constants.ENABLE_SINGLE_TAG_POSE) {
      return Optional.of(getEstimatedPose());
    }

    if (!singleTagPoses.containsKey(cameraId) || !singleTagPoses.get(cameraId).containsKey(tagId)) {
      DriverStationErrors.reportError("No tag with id: " + tagId, true);
      return Optional.empty();
    }

    var singleTagPose = singleTagPoses.get(cameraId).get(tagId);

    if (Timer.getTimestamp() - singleTagPose.timestamp() >= txTyObservationStaleSecs.get()
        || singleTagPose.distance() >= txTyObservationMaxAllowedDistance.get()) {
      return Optional.empty();
    }
    var oldWheeledPose = poseBuffer.getSample(singleTagPose.timestamp());

    // Latency compensate
    return oldWheeledPose.map(
        pose -> singleTagPose.pose().plus(new Transform2d(pose, wheeledPose)));
  }

  public void addRobotCentricVel(Twist2d vel) {
    robotCentricVel = vel;
  }

  public void addTrajectoryVel(Twist2d vel) {
    trajectoryVel = vel;
  }

  @AutoLogOutput(key = "Odometry/FieldCentricVel")
  public Twist2d getFieldCentricVel() {
    var fieldCentricTranslationVel =
        new Translation2d(robotCentricVel.dx, robotCentricVel.dy)
            .rotateBy(estimatedPose.getRotation());

    return new Twist2d(
        fieldCentricTranslationVel.getX(),
        fieldCentricTranslationVel.getY(),
        robotCentricVel.dtheta);
  }

  @AutoLogOutput(key = "Odometry/VelNorm")
  public double getLinerVelNorm() {
    var vel = getFieldCentricVel();
    return Math.hypot(vel.dx, vel.dy);
  }

  public Pose2d getPredictedPose(double translationLookaheadSec, double rotationLookaheadSec) {
    var vel = RobotState.isAutonomousEnabled() ? trajectoryVel : robotCentricVel;
    return getEstimatedPose()
        .transformBy(
            new Transform2d(
                vel.dx * translationLookaheadSec,
                vel.dy * translationLookaheadSec,
                Rotation2d.fromRadians(vel.dtheta * rotationLookaheadSec)));
  }

  @Override
  public void periodic() {
    Logger.recordOutput("Odometry/EstimatedPose", estimatedPose);
    Logger.recordOutput("Odometry/WheeledPose", wheeledPose);
  }
}
