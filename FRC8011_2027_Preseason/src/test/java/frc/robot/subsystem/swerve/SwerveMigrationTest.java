package frc.robot.subsystem.swerve;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import frc.lib.interfaces.sensor.gyro.GyroIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.wpilib.telemetry.TelemetryRegistry;
import org.wpilib.backend.NetworkTablesTelemetryBackend;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.linalg.VecBuilder;

class SwerveMigrationTest {
  @BeforeAll
  static void configureTelemetry() {
    TelemetryRegistry.registerBackend("/SmartDashboard",
        new NetworkTablesTelemetryBackend(NetworkTableInstance.getDefault(), ""));
  }

  private static class CaptureIO implements ModuleIO {
    double velocity;
    double angle;
    @Override public void setDriveVelocity(double velocity, double torque) {
      this.velocity = velocity * SwerveConfig.WHEEL_RADIUS_METER;
    }
    @Override public void setSteerPosition(double angle) { this.angle = angle; }
  }

  @Test
  void reverseDriveOptimizesAndLimitsWheelVelocity() throws Exception {
    verifyReverse(false);
    verifyReverse(true);
  }

  @Test
  void odometryThreadProducesSamplesInSeconds() throws Exception {
    var before = org.littletonrobotics.junction.Logger.getTimestamp() / 1e9;
    try (var thread = new SimOdometryThread(
        () -> 1.0, () -> 0.0, () -> 1.0, () -> 0.0,
        () -> 1.0, () -> 0.0, () -> 1.0, () -> 0.0)) {
      var sample = thread.start().poll(2, java.util.concurrent.TimeUnit.SECONDS);
      assertNotNull(sample, "Odometry must begin sampling after start()");
      var after = org.littletonrobotics.junction.Logger.getTimestamp() / 1e9;
      assertTrue(sample.timestamp() >= before && sample.timestamp() <= after,
          "Sample timestamp must use seconds, matching pose and vision buffers");
      assertEquals(SwerveConfig.WHEEL_RADIUS_METER, sample.wheelPositions()[0].distance, 1e-9);
    }
  }

  private void verifyReverse(boolean feedforward) throws Exception {
    var io = new CaptureIO();
    var constructor = Swerve.class.getDeclaredConstructor(
        ModuleIO.class, ModuleIO.class, ModuleIO.class, ModuleIO.class,
        GyroIO.class, ArrayBlockingQueue.class);
    constructor.setAccessible(true);
    var swerve = constructor.newInstance(io, io, io, io, new GyroIO() {}, new ArrayBlockingQueue<>(20));
    try {
      var goal = new ChassisVelocities(-2 * SwerveConfig.MAX_TRANSLATION_VEL_METER_PER_SEC, 0, 0);
      if (feedforward) {
        swerve.setGoalVelFF(goal, List.of(VecBuilder.fill(0, 0), VecBuilder.fill(0, 0),
            VecBuilder.fill(0, 0), VecBuilder.fill(0, 0)));
      } else {
        swerve.setGoalVel(goal, false);
      }
      assertEquals(-SwerveConfig.MAX_TRANSLATION_VEL_METER_PER_SEC, io.velocity, 1e-6);
      assertEquals(0, io.angle, 1e-6);
    } finally {
      CommandScheduler.getInstance().unregisterSubsystem(swerve);
    }
  }
}
