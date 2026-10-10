package frc.robot;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.framework.RobotBase;
import frc.lib.interfaces.CanId;
import java.util.function.BooleanSupplier;

public class Constants {
  public static final double LOOP_PERIOD_SEC = 0.02;

  public static BooleanSupplier IS_LIVE_DEBUG = () -> true;

  public static final Mode MODE =
      RobotBase.isReal() ? Mode.REAL : RobotBase.isSimulation() ? Mode.SIM : Mode.REPLAY;

  public static final boolean ENABLE_SINGLE_TAG_POSE = false;

  public enum Mode {
    REAL,
    SIM,
    REPLAY
  }

  public final class Misc {
    public static final Pose2d LEFT_TRENCH_SHOOT = new Pose2d(3.3, 7.1, new Rotation2d(-1.05));
    public static final Pose2d RIGHT_TRENCH_SHOOT = new Pose2d(3.3, 0.9, new Rotation2d(1.05));
    public static final Pose2d LEFT_TOWER_SHOOT = new Pose2d(0.9, 4.8, new Rotation2d(-0.28));
    public static final Pose2d RIGHT_TOWER_SHOOT = new Pose2d(1.25, 2.3, new Rotation2d(0.55));
  }

  public final class AscopeAssets {}

  public final class DebugGroup {
    public static final String SWERVE = "Swerve";
    public static final String ODOMETRY = "Odometry";
    public static final String INTAKE = "Intake";
    public static final String INDEXER = "Indexer";
    public static final String SHOOTER = "Shooter";
  }

  public final class Ports {
    public static final class Can {
      public static final String RIO_BUS = "";
      public static final String CHASSIS_CANIVORE_BUS = "chassis";
      public static final String SUPERSTRUCTURE_CANIVORE_BUS = "super";

      // IMU
      public static final CanId CHASSIS_PIGEON = new CanId(0, CHASSIS_CANIVORE_BUS);

      // Swerve
      public static final CanId FL_DRIVE_MOTOR = new CanId(1, CHASSIS_CANIVORE_BUS);
      public static final CanId FL_STEER_MOTOR = new CanId(2, CHASSIS_CANIVORE_BUS);
      public static final CanId FL_STEER_SENSOR = new CanId(3, CHASSIS_CANIVORE_BUS);

      public static final CanId BL_DRIVE_MOTOR = new CanId(7, CHASSIS_CANIVORE_BUS);
      public static final CanId BL_STEER_MOTOR = new CanId(8, CHASSIS_CANIVORE_BUS);
      public static final CanId BL_STEER_SENSOR = new CanId(9, CHASSIS_CANIVORE_BUS);

      public static final CanId BR_DRIVE_MOTOR = new CanId(4, CHASSIS_CANIVORE_BUS);
      public static final CanId BR_STEER_MOTOR = new CanId(5, CHASSIS_CANIVORE_BUS);
      public static final CanId BR_STEER_SENSOR = new CanId(6, CHASSIS_CANIVORE_BUS);

      public static final CanId FR_DRIVE_MOTOR = new CanId(10, CHASSIS_CANIVORE_BUS);
      public static final CanId FR_STEER_MOTOR = new CanId(11, CHASSIS_CANIVORE_BUS);
      public static final CanId FR_STEER_SENSOR = new CanId(12, CHASSIS_CANIVORE_BUS);

      // Intake
      public static final CanId INTAKE_PIVOT = new CanId(51, RIO_BUS);
      public static final CanId INTAKE_ROLLER = new CanId(20, RIO_BUS);

      // Indexer
      public static final CanId INDEXER = new CanId(53, RIO_BUS);

      // Shooter
      public static final CanId SHOOTER_LOWER_FLYWHEEL = new CanId(22, RIO_BUS);
      public static final CanId SHOOTER_LOWER_FLYWHEEL_FOLLOWER = new CanId(57, RIO_BUS);
      public static final CanId SHOOTER_UPPER_FLYWHEEL = new CanId(30, RIO_BUS);
      public static final CanId SHOOTER_LOWER_FEEDER = new CanId(15, RIO_BUS);
      public static final CanId SHOOTER_UPPER_FEEDER = new CanId(23, RIO_BUS);
    }

    public static final class Digital {}

    public static final class Joystick {
      public static final int DRIVER = 0;
    }
  }
}
