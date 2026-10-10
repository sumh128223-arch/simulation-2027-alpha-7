package frc.rebuilt;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.system.Filesystem;
import java.io.IOException;
import java.nio.file.Path;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class Field {
  public static final double LENGTH = 16.518;
  public static final double WIDTH = 8.043;

  public static final class Outpost {
    public static final double Y = .665988;
    public static final double X = 0.65;
  }

  public static class Hub {
    public static final double CENTER_X = 4.03 + 1.19 / 2.0;
    public static final double CENTER_Y = WIDTH / 2.0;
    public static final double MIN_X = 4.02844;

    public static Translation2d getCenter() {
      return AllianceFlipUtil.apply(new Translation2d(CENTER_X, CENTER_Y));
    }
  }

  public static final class Trench {
    public static final double Y = .659638;
  }

  public static final class DEPOT {
    public static final double Y = WIDTH / 2.0 + 1.928622;
    public static final double min_Y = Y - 0.3048;
    public static final double max_Y = Y + 0.3048;
    public static final double max_X = 0.6858;
  }

  // AprilTag configuration
  private static final class FieldConfiguration {
    private static final FieldType TYPE = FieldType.ANDYMARK;
  }

  public static final FieldType FIELD_TYPE = FieldConfiguration.TYPE;

  @Getter
  @RequiredArgsConstructor
  public enum FieldType {
    ANDYMARK("andymark"),
    WELDED("welded");

    private final String jsonFolder;
  }

  public static final AprilTagLayoutType APRILTAG_LAYOUT = AprilTagLayoutType.OFFICIAL;
  public static final int APRILTAG_COUNT = 32;

  @Getter
  public enum AprilTagLayoutType {
    OFFICIAL("2026-official"),
    NONE("2026-none"),
    HUB("2026-hub"),
    OUTPOST("2026-outpost"),
    TOWER("2026-tower");

    AprilTagLayoutType(String name) {
      try {
        layout =
            org.wpilib.fields.Field.loadFromFile(
                Path.of(
                    Filesystem.getDeployDirectory().getPath(),
                    "apriltags",
                    FieldConfiguration.TYPE.getJsonFolder(),
                    name + ".json"));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      layoutString = layout.getJson();
    }

    private final org.wpilib.fields.Field layout;
    private final String layoutString;
  }

  public class AllianceFlipUtil {
    public static double mirrorX(double x) {
      return LENGTH - x;
    }

    public static double applyX(double x) {
      return shouldFlip() ? mirrorX(x) : x;
    }

    public static double mirrorY(double y) {
      return WIDTH - y;
    }

    public static double applyY(double y) {
      return shouldFlip() ? mirrorY(y) : y;
    }

    public static Translation2d mirror(Translation2d translation) {
      return new Translation2d(mirrorX(translation.getX()), mirrorY(translation.getY()));
    }

    public static Translation2d apply(Translation2d translation) {
      return new Translation2d(applyX(translation.getX()), applyY(translation.getY()));
    }

    public static Rotation2d mirror(Rotation2d rotation) {
      return rotation.rotateBy(Rotation2d.PI);
    }

    public static Rotation2d apply(Rotation2d rotation) {
      return shouldFlip() ? rotation.rotateBy(Rotation2d.PI) : rotation;
    }

    public static Pose2d mirror(Pose2d pose) {
      return new Pose2d(mirror(pose.getTranslation()), mirror(pose.getRotation()));
    }

    public static Pose2d apply(Pose2d pose) {
      return shouldFlip()
          ? new Pose2d(apply(pose.getTranslation()), apply(pose.getRotation()))
          : pose;
    }

    public static boolean shouldFlip() {
      return MatchState.getAlliance().isPresent()
          && MatchState.getAlliance().get() == Alliance.RED;
    }

    public static boolean inBlueHalf(Pose2d pose) {
      return pose.getX() < Field.LENGTH / 2.0;
    }

    public static boolean inRedHalf(Pose2d pose) {
      return pose.getX() > Field.LENGTH / 2.0;
    }
  }

  public static class StartingGamePieces {
    public static final org.wpilib.math.geometry.Pose3d[] pickableGamePiece;

    static {
      java.util.List<org.wpilib.math.geometry.Pose3d> pieces = new java.util.ArrayList<>();

      // Center large grid (6 columns on left, 6 on right, split Y into 15 and 15)
      double centerX = LENGTH / 2.0;
      double centerY = WIDTH / 2.0;
      double spacing = 0.155; // approx spacing
      int centerColsX = 12;
      int centerRowsY = 30;

      for (int i = 0; i < centerColsX; i++) {
        for (int j = 0; j < centerRowsY; j++) {
          double xOffset = (i - (centerColsX - 1) / 2.0) * spacing;
          double yOffset = (j - (centerRowsY - 1) / 2.0) * spacing;

          double x = centerX + xOffset;
          double y = centerY + yOffset;

          pieces.add(
              new org.wpilib.math.geometry.Pose3d(
                  x, y, 0.075, new org.wpilib.math.geometry.Rotation3d()));
        }
      }

      // Top left grid (Red)
      int sideColsX = 4;
      int sideRowsY = 6;
      double redSideX = spacing / 2.0;
      double redSideY = 5.6;
      for (int i = 0; i < sideColsX; i++) {
        for (int j = 0; j < sideRowsY; j++) {
          double x = redSideX + i * spacing;
          double y = redSideY + j * spacing;
          pieces.add(
              new org.wpilib.math.geometry.Pose3d(
                  x, y, spacing / 2.0, new org.wpilib.math.geometry.Rotation3d()));
        }
      }

      // Bottom right grid (Blue)
      double blueSideX = LENGTH - redSideX;
      double blueSideY = WIDTH - redSideY;
      for (int i = 0; i < sideColsX; i++) {
        for (int j = 0; j < sideRowsY; j++) {
          double x = blueSideX - i * spacing;
          double y = blueSideY - j * spacing;
          pieces.add(
              new org.wpilib.math.geometry.Pose3d(
                  x, y, spacing / 2.0, new org.wpilib.math.geometry.Rotation3d()));
        }
      }

      pickableGamePiece = pieces.toArray(new org.wpilib.math.geometry.Pose3d[0]);
    }
  }
}
