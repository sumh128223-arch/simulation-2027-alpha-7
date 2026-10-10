package frc.robot.subsystem.vision;

import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.linalg.VecBuilder;
import org.wpilib.math.geometry.*;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import frc.lib.dashboard.Alert;
import frc.lib.interfaces.VirtualSubsystem;
import frc.lib.math.GeomUtil;
import frc.rebuilt.Field;
import frc.robot.RobotContainer;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.experimental.ExtensionMethod;
import org.littletonrobotics.junction.Logger;
import org.photonvision.simulation.SimCameraProperties;
import org.photonvision.simulation.VisionSystemSim;

@ExtensionMethod({GeomUtil.class})
public class ApriltagVision extends VirtualSubsystem {

  public record VisionObservation(double timestamp, Pose2d pose, Matrix<N3, N1> stdDevs) {}

  public record SingleTagVisionObservation(
      double timestamp, Pose2d pose, int id, double distance) {}

  private static SimCameraProperties OV9281_1280_800() {
    var prop = new SimCameraProperties();
    prop.setCalibration(1280, 800, Rotation2d.fromDegrees(75.0));
    prop.setCalibError(0.37, 0.06);
    prop.setFPS(45.0);
    prop.setAvgLatencyMs(14);
    prop.setLatencyStdDevMs(5);
    return prop;
  }

  @RequiredArgsConstructor
  public enum CameraId {
    FRONT("main"),
    BACK("sub");

    private final String name;

    @Override
    public String toString() {
      return name;
    }
  }

  private final ApriltagVisionIO frontIO;
  private final ApriltagVisionIO backIO;

  private final ApriltagVisionIOInputsAutoLogged frontInputs =
      new ApriltagVisionIOInputsAutoLogged();
  private final ApriltagVisionIOInputsAutoLogged backInputs =
      new ApriltagVisionIOInputsAutoLogged();

  private final List<VisionObservation> allGoodVisionObservations = new ArrayList<>();
  private final HashMap<CameraId, Map<Integer, SingleTagVisionObservation>>
      allSingleTagVisionObservations = new HashMap<>();
  private final List<Pose3d> allGoodRobotInField = new ArrayList<>();
  private final List<Pose2d> allGoodRobotInField2d = new ArrayList<>();
  private final List<Pose3d> allBadRobotInField = new ArrayList<>();
  private final List<Pose3d> allUsedTagInField = new ArrayList<>();

  /** Frame counter for throttling heavy visualization logs. */
  private int vizLogCounter = 0;

  private static final int VIZ_LOG_INTERVAL = 5;

  private final Alert frontOfflineAlert =
      new Alert(CameraId.FRONT.toString() + " offline!", Alert.AlertType.WARNING);
  private final Alert backOfflineAlert =
      new Alert(CameraId.BACK.toString() + " offline!", Alert.AlertType.WARNING);

  private ApriltagVision(ApriltagVisionIO frontIO, ApriltagVisionIO backIO) {
    this.frontIO = frontIO;
    this.backIO = backIO;

    // Pre-warm AdvantageKit struct serialization for Pose3d/Pose2d arrays so that
    // the first real vision frame doesn't cause a one-shot latency spike on RIO1.
    Logger.recordOutput("ApriltagVision/AllBadRobotInField", new Pose3d[] {});
    Logger.recordOutput("ApriltagVision/AllGoodRobotInField", new Pose3d[] {});
    Logger.recordOutput("ApriltagVision/AllGoodRobotInField2d", new Pose2d[] {});
    Logger.recordOutput("ApriltagVision/AllUsedTagInField", new Pose3d[] {});
  }

  public static ApriltagVision createReal() {
    return new ApriltagVision(
        new ApriltagVisionIOPhoton(CameraId.FRONT.toString()),
        new ApriltagVisionIOPhoton(CameraId.BACK.toString()));
  }

  public static ApriltagVision createSim(Supplier<Pose2d> poseSupplier) {
    var visionSystemSim = new VisionSystemSim("apriltag");
    visionSystemSim.addAprilTags(Field.APRILTAG_LAYOUT.getLayout());
    return new ApriltagVision(
        new ApriltagVisionIOPhotonSim(
            CameraId.FRONT.toString(),
            OV9281_1280_800(),
            ApriltagVisionConfig.FRONT.toTransform3d(),
            visionSystemSim,
            poseSupplier),
        new ApriltagVisionIOPhotonSim(
            CameraId.BACK.toString(),
            OV9281_1280_800(),
            ApriltagVisionConfig.BACK.toTransform3d(),
            visionSystemSim,
            poseSupplier));
  }

  public static ApriltagVision createIO() {
    return new ApriltagVision(new ApriltagVisionIO() {}, new ApriltagVisionIO() {});
  }

  @Override
  public void periodic() {
    updateInputs();

    frontOfflineAlert.set(!frontInputs.connected);
    backOfflineAlert.set(!backInputs.connected);

    allGoodVisionObservations.clear();
    allSingleTagVisionObservations.clear();
    allGoodRobotInField.clear();
    allGoodRobotInField2d.clear();
    allBadRobotInField.clear();
    allUsedTagInField.clear();

    allSingleTagVisionObservations.put(CameraId.FRONT, new HashMap<>());
    allSingleTagVisionObservations.put(CameraId.BACK, new HashMap<>());

    updateRobotInField(CameraId.FRONT, frontInputs, ApriltagVisionConfig.FRONT, true);
    updateRobotInField(CameraId.BACK, backInputs, ApriltagVisionConfig.BACK, true);

    if (anyCameraHasUpdate()) {
      // Throttle heavy Pose3d[] visualization logs to reduce serialization overhead on RIO1
      if (++vizLogCounter >= VIZ_LOG_INTERVAL) {
        vizLogCounter = 0;
        Logger.recordOutput(
            "ApriltagVision/AllBadRobotInField", allBadRobotInField.toArray(Pose3d[]::new));
        Logger.recordOutput(
            "ApriltagVision/AllGoodRobotInField", allGoodRobotInField.toArray(Pose3d[]::new));
        Logger.recordOutput(
            "ApriltagVision/AllGoodRobotInField2d", allGoodRobotInField2d.toArray(Pose2d[]::new));
        Logger.recordOutput(
            "ApriltagVision/AllUsedTagInField", allUsedTagInField.toArray(Pose3d[]::new));
      }

      var odometry = RobotContainer.getOdometry();

      for (var singleTagPoses : allSingleTagVisionObservations.entrySet()) {
        singleTagPoses
            .getValue()
            .values()
            .forEach(pose -> odometry.addSingleTagObservation(singleTagPoses.getKey(), pose));
      }

      allGoodVisionObservations.stream()
          .sorted(Comparator.comparingDouble(VisionObservation::timestamp))
          .forEach(odometry::addVisionObservation);
    }
  }

  private void updateInputs() {
    frontIO.updateInputs(frontInputs);
    backIO.updateInputs(backInputs);

    Logger.processInputs("ApriltagVision/" + CameraId.FRONT.toString(), frontInputs);
    Logger.processInputs("ApriltagVision/" + CameraId.BACK.toString(), backInputs);
  }

  private void updateRobotInField(
      CameraId cameraId,
      ApriltagVisionIOInputsAutoLogged inputs,
      Pose3d cameraInRobot,
      boolean isReefCamera) {
    if (!(inputs.connected
        && inputs.hasUpdate
        && inputs.hasTargets
        && inputs.poseObservations.length != 0
        && inputs.ids.length != 0
        && inputs.txTyObservations.length != 0)) {
      return;
    }

    for (final var poseObservation : inputs.poseObservations) {
      if (poseObservation.ambiguity() > ApriltagVisionConfig.MAX_ALLOWABLE_AMBIGUITY) {
        continue;
      }

      final var robotInField =
          poseObservation.cameraInField().transformBy(cameraInRobot.toTransform3d().inverse());

      if (robotInField.getX() < -ApriltagVisionConfig.FIELD_BORDER_THRESHOLD_METER
          || robotInField.getX() > Field.LENGTH + ApriltagVisionConfig.FIELD_BORDER_THRESHOLD_METER
          || robotInField.getY() < -ApriltagVisionConfig.FIELD_BORDER_THRESHOLD_METER
          || robotInField.getY() > Field.WIDTH + ApriltagVisionConfig.FIELD_BORDER_THRESHOLD_METER
          || robotInField.getZ() > ApriltagVisionConfig.ROBOT_POSE_Z_THRESHOLD_METER
          || robotInField.getZ() < -ApriltagVisionConfig.ROBOT_POSE_Z_THRESHOLD_METER) {
        allBadRobotInField.add(robotInField);
        continue;
      }

      final var xyStdDev =
          ApriltagVisionConfig.XY_STD_DEV_COEFF
              * Math.pow(poseObservation.avgDistance(), 2)
              / poseObservation.tagCount();

      final var thetaStdDev =
          poseObservation.tagCount() > 1
              ? ApriltagVisionConfig.THETA_STD_DEV_COEFF
                  * Math.pow(poseObservation.avgDistance(), 2)
                  / poseObservation.tagCount()
              : Double.POSITIVE_INFINITY;

      final var robotInField2d = robotInField.toPose2d();
      allGoodVisionObservations.add(
          new VisionObservation(
              poseObservation.timestamp(),
              robotInField2d,
              VecBuilder.fill(xyStdDev, xyStdDev, thetaStdDev)));
      allGoodRobotInField.add(robotInField);
      allGoodRobotInField2d.add(robotInField2d);
    }

    for (final var id : inputs.ids) {
      Field.APRILTAG_LAYOUT.getLayout().getTagPose(id).ifPresent(allUsedTagInField::add);
    }

    if (!isReefCamera) {
      return;
    }

    final var odometry = RobotContainer.getOdometry();
    final var wheeledPose = odometry.getWheeledPose();
    final var estimatedPose = odometry.getEstimatedPose();

    for (final var observation : inputs.txTyObservations) {
      final var oldWheeledPose = odometry.getWheeledPoseByTimestamp(observation.timestamp());

      if (oldWheeledPose.isEmpty()) {
        continue;
      }

      final var tagInFieldOpt = Field.APRILTAG_LAYOUT.getLayout().getTagPose(observation.id());
      if (tagInFieldOpt.isEmpty()) {
        continue;
      }

      final var robotRotation =
          estimatedPose
              .transformBy(new Transform2d(wheeledPose, oldWheeledPose.get()))
              .getRotation();

      final var camToTagTranslation =
          new Pose3d(
                  Translation3d.ZERO, new Rotation3d(0, observation.tyRad(), -observation.txRad()))
              .transformBy(
                  new Transform3d(
                      new Translation3d(observation.distance(), 0, 0), Rotation3d.ZERO))
              .getTranslation()
              .rotateBy(new Rotation3d(0, cameraInRobot.getRotation().getY(), 0))
              .toTranslation2d();

      final var camToTagAngle = camToTagTranslation.getAngle();
      if (camToTagAngle.isEmpty()) {
        continue;
      }

      final var camToTagRotation =
          robotRotation.plus(
              cameraInRobot.toPose2d().getRotation().plus(camToTagAngle.get()));

      final var tagInField = tagInFieldOpt.get();
      final var cameraInFieldTranslation =
          new Pose2d(tagInField.toPose2d().getTranslation(), camToTagRotation.plus(Rotation2d.PI))
              .transformBy(GeomUtil.toTransform2d(camToTagTranslation.getNorm(), 0.0))
              .getTranslation();
      var robotInField =
          new Pose2d(
                  cameraInFieldTranslation,
                  robotRotation.plus(cameraInRobot.toPose2d().getRotation()))
              .transformBy(new Transform2d(cameraInRobot.toPose2d(), Pose2d.ZERO));
      robotInField = new Pose2d(robotInField.getTranslation(), robotRotation);

      var singleTagVisionObservations = allSingleTagVisionObservations.get(cameraId);
      if (!singleTagVisionObservations.containsKey(observation.id())
          || observation.timestamp()
              > singleTagVisionObservations.get(observation.id()).timestamp()) {
        singleTagVisionObservations.put(
            observation.id(),
            new SingleTagVisionObservation(
                observation.timestamp(), robotInField, observation.id(), observation.distance()));
      }
    }
  }

  private boolean singleCameraHasTagById(ApriltagVisionIOInputsAutoLogged inputs, int id) {
    if (!inputs.connected || inputs.ids == null) {
      return false;
    }
    return Arrays.stream(inputs.ids).anyMatch(seemedId -> seemedId == id);
  }

  private boolean anyCameraHasUpdate() {
    return frontInputs.hasUpdate || backInputs.hasUpdate;
  }
}
