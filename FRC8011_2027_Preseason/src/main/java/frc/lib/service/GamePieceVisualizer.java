package frc.lib.service;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.geometry.Translation3d;
import frc.lib.interfaces.VirtualSubsystem;
import frc.lib.math.GeomUtil;
import frc.rebuilt.Field;
import frc.robot.RobotContainer;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.littletonrobotics.junction.Logger;

@ExtensionMethod({GeomUtil.class})
public class GamePieceVisualizer extends VirtualSubsystem {
  private final String name;

  // FIXME: reset gamepiece when reset() is called
  private final Pose3d[] scorableGamePiecePose;
  @Getter private final Pose3d[] pickableGamePiecePose;
  private final Pose3d[] scoredGamePiecePose;
  @Getter private int hasGamePieceNums = 0;
  private int scoredGamePieceNums = 0;

  private final double pickableMaxDistance;
  private final double scorableMaxDistance;

  private final List<MovingGamePiece> movingGamePieces = new ArrayList<>();

  private final double dx = 0.14;
  private final double dy = 0.14;
  private final double dz = 0.14;
  private final double x0 = 0.0;
  private final double y0 = 0.28;
  private final double z0 = 0.2;
  private final int nx = 5;
  private final int ny = 5;
  private final int nz = 3;
  @Getter private Pose3d[] onboardGamePiecePose = new Pose3d[0];

  private static class MovingGamePiece {
    public Pose3d pose;
    public Translation3d velocity;
    public boolean isShoot;

    public MovingGamePiece(Pose3d pose, Translation3d velocity, boolean isShoot) {
      this.pose = pose;
      this.velocity = velocity;
      this.isShoot = isShoot;
    }
  }

  @Override
  public void periodic() {
    double dt = 0.02; // 20ms standard loop
    Translation3d gravity = new Translation3d(0, 0, -9.81);
    for (int i = movingGamePieces.size() - 1; i >= 0; i--) {
      MovingGamePiece mgp = movingGamePieces.get(i);
      mgp.velocity = mgp.velocity.plus(gravity.times(dt));
      mgp.pose =
          new Pose3d(
              mgp.pose.getTranslation().plus(mgp.velocity.times(dt)), mgp.pose.getRotation());
      if (mgp.pose.getZ() <= 0.0) {
        mgp.pose = new Pose3d(mgp.pose.getX(), mgp.pose.getY(), 0.075, mgp.pose.getRotation());
        if (mgp.isShoot) {
          spawnRandomPickable();
        } else {
          addPickable(
              new Pose3d(
                  mgp.pose.getX() + (Math.random() - 0.3) * 3.0, // Random inject spread X
                  mgp.pose.getY() + (Math.random() - 0.5) * 2.0, // Random inject spread Y
                  0.075,
                  mgp.pose.getRotation()));
        }

        // spawnRandomPickable();
        movingGamePieces.remove(i);
      }
    }

    computeOnboardPoses();

    Logger.recordOutput("Visualization/" + name + "/hasGamePiece", hasGamePieceNums);
    Logger.recordOutput("Visualization/" + name + "/scoredGamePieceNums", scoredGamePieceNums);

    Logger.recordOutput("Visualization/" + name + "/pickableGamePiecePose", pickableGamePiecePose);
    Logger.recordOutput("Visualization/" + name + "/scoredGamePiecePose", scoredGamePiecePose);
    Logger.recordOutput("Visualization/" + name + "/scorableGamePiecePose", scorableGamePiecePose);
    Logger.recordOutput("Visualization/" + name + "/onboardGamePiecePose", onboardGamePiecePose);

    // Log moving pieces individually as arrays
    Logger.recordOutput(
        "Visualization/" + name + "/movingGamePieces",
        movingGamePieces.stream().map(m -> m.pose).toArray(Pose3d[]::new));
  }

  /**
   * Tries to pick a game piece at the specified pose.
   *
   * @param pose The pose of the game piece to pick.
   * @return true if the game piece was successfully picked, false otherwise.
   */
  public boolean tryPick(Pose3d pose) {
    Logger.recordOutput("Visualization/" + name + "/tryPickPose", pose);
    double minDistance = Double.POSITIVE_INFINITY;
    int minDistanceIndex = 0;
    for (int i = 0; i < pickableGamePiecePose.length; i++) {
      double distance = pickableGamePiecePose[i].getDistance(pose);
      if (distance < minDistance) {
        minDistance = distance;
        minDistanceIndex = i;
      }
    }
    if (minDistance < pickableMaxDistance) {
      pickableGamePiecePose[minDistanceIndex] =
          new Pose3d(-0x3f3f3f3f, -0x3f3f3f3f, -0x3f3f3f3f, new Rotation3d());
      hasGamePieceNums++;
      return true;
    }

    return false;
  }

  /**
   * Tries to eject a game piece at the specified pose.
   *
   * @param pose The pose where the game piece should be ejected.
   * @return true if the game piece was successfully ejected, false otherwise.
   */
  public boolean tryEject(Pose3d pose) {
    return tryEject(
        pose,
        new Translation3d(
                pose.getRotation().getX(), pose.getRotation().getY(), pose.getRotation().getZ())
            .times(2.0),
        false);
  }

  /** Tries to eject a game piece at the specified pose with an internal velocity. */
  public boolean tryEject(Pose3d pose, Translation3d initialVelocity, boolean isShoot) {
    if (hasGamePieceNums > 0) {
      hasGamePieceNums--;
      movingGamePieces.add(new MovingGamePiece(pose, initialVelocity, isShoot));
      return true;
    }
    return false;
  }

  private void addPickable(Pose3d pose) {
    for (int i = 0; i < pickableGamePiecePose.length; i++) {
      if (pickableGamePiecePose[i].getZ() < 0) { // arbitrary threshold indicating a picked spot
        pickableGamePiecePose[i] = pose;
        pickableGamePiecePose[i] = new Pose3d(pose.getX(), pose.getY(), 0.075, pose.getRotation());
        return;
      }
    }
  }

  private void spawnRandomPickable() {
    // 2026/2025 field size approximate (~16.5 x 8.0)
    double randomX = Math.random() * Field.LENGTH;
    double randomY = Math.random() * Field.WIDTH;
    addPickable(new Pose3d(randomX, randomY, 0.075, new Rotation3d()));
  }

  /**
   * Tries to score a game piece at the specified pose.
   *
   * @param pose The pose where the game piece should be scored.
   * @return true if the game piece was successfully scored, false otherwise.
   */
  public boolean tryScore(Pose3d pose) {
    Logger.recordOutput("Visualization/" + name + "/tryScorePose", pose);
    if (hasGamePieceNums > 0) {
      double minDistance = Double.POSITIVE_INFINITY;
      int minDistanceIndex = -1;
      double minScoredDistance = Double.POSITIVE_INFINITY;
      int minScoredDistanceIndex = -1;

      // Iterate through all positions
      for (int i = 0; i < scorableGamePiecePose.length; i++) {
        // Check if this position has already been scored
        boolean isScored = false;
        for (int j = 0; j < scoredGamePieceNums; j++) {
          if (scoredGamePiecePose[j].equals(scorableGamePiecePose[i])) {
            isScored = true;
            break;
          }
        }

        double distance = scorableGamePiecePose[i].getDistance(pose);

        // Record the closest distance among unscored positions
        if (!isScored && distance < minDistance) {
          minDistance = distance;
          minDistanceIndex = i;
        }

        // Record the closest distance among scored positions
        if (isScored && distance < minScoredDistance) {
          minScoredDistance = distance;
          minScoredDistanceIndex = i;
        }
      }

      // If an unscored position is found within threshold, score at that position
      if (minDistanceIndex != -1 && minDistance < scorableMaxDistance) {
        scoredGamePiecePose[scoredGamePieceNums] = scorableGamePiecePose[minDistanceIndex];
        scoredGamePieceNums++;
        hasGamePieceNums--;
        spawnRandomPickable();
        return true;
      }
      // If all positions are scored, score at the closest scored position
      else if (minScoredDistanceIndex != -1 && minScoredDistance < scorableMaxDistance) {
        scoredGamePiecePose[scoredGamePieceNums] = scorableGamePiecePose[minScoredDistanceIndex];
        scoredGamePieceNums++;
        hasGamePieceNums--;
        spawnRandomPickable();
        return true;
      }
    }
    return false;
  }

  public GamePieceVisualizer(
      String name,
      double maxPickableDistance,
      double maxScorableDistance,
      Pose3d[] pickableGamePiecePose,
      Pose3d[] scorableGamePiecePose,
      int hasGamePieceNums) {
    this.name = name;
    this.pickableMaxDistance = maxPickableDistance;
    this.scorableMaxDistance = maxScorableDistance;
    this.scorableGamePiecePose = scorableGamePiecePose;
    this.pickableGamePiecePose = pickableGamePiecePose;
    this.scoredGamePiecePose = new Pose3d[pickableGamePiecePose.length];
    for (int i = 0; i < pickableGamePiecePose.length; i++) {
      scoredGamePiecePose[i] = new Pose3d(0x3f3f3f3f, 0x3f3f3f3f, 0x3f3f3f3f, new Rotation3d());
    }
    this.hasGamePieceNums = hasGamePieceNums;
  }

  private void computeOnboardPoses() {
    int capacity = nx * ny * nz;
    int count = Math.min(hasGamePieceNums, capacity);
    if (count <= 0) {
      onboardGamePiecePose = new Pose3d[0];
      return;
    }
    Pose2d robotPose2d = RobotContainer.getOdometry().getEstimatedPose();
    Pose3d robotPose3d = new Pose3d(robotPose2d);
    Pose3d[] result = new Pose3d[count];
    int idx = 0;
    for (int i = 0; i < nx && idx < count; i++) {
      for (int j = 0; j < ny && idx < count; j++) {
        for (int k = 0; k < nz && idx < count; k++) {
          double lx = x0 - i * dx;
          double ly = y0 - j * dy;
          double lz = z0 + k * dz;
          result[idx++] =
              robotPose3d.transformBy(
                  new Transform3d(new Translation3d(lx, ly, lz), new Rotation3d()));
        }
      }
    }
    onboardGamePiecePose = result;
  }
}
