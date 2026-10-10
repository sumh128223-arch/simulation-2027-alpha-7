package frc.robot.subsystem.vision;

import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.util.Units;

class ApriltagVisionConfig {
  // Camera poses relative to robot center
  static final Pose3d FRONT =
      new Pose3d(0.334698, 0.0, 0.276589, new Rotation3d(0.0, Units.degreesToRadians(-20.0), 0.0));
  static final Pose3d BACK =
      new Pose3d(0.01487, 0.0, 0.95679, new Rotation3d(0.0, 0.0, Units.degreesToRadians(180.0)));

  // Vision filtering thresholds
  static final double FIELD_BORDER_THRESHOLD_METER = 0.5;
  static final double ROBOT_POSE_Z_THRESHOLD_METER = 0.5;
  static final double XY_STD_DEV_COEFF = 0.005;
  static final double THETA_STD_DEV_COEFF = 0.01;
  static final double MAX_ALLOWABLE_AMBIGUITY = 0.4;
}
