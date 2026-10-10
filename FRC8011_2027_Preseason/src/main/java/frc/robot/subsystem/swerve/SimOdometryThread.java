package frc.robot.subsystem.swerve;

import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.system.Timer;
import frc.robot.Constants;
import frc.robot.Odometry.WheeledObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;
import org.littletonrobotics.junction.Logger;

public class SimOdometryThread implements AutoCloseable {
  private final Thread thread;
  private volatile boolean isRunning = false;
  private final List<Supplier<Double>> signals = new ArrayList<>(9);
  private final ArrayBlockingQueue<WheeledObservation> odometryCachedWheeledObservationQueue =
      new ArrayBlockingQueue<>(20);
  private final Timer timer = new Timer();

  SimOdometryThread(
      Supplier<Double> fl_drive_signal,
      Supplier<Double> fl_steer_signal,
      Supplier<Double> bl_drive_signal,
      Supplier<Double> bl_steer_signal,
      Supplier<Double> br_drive_signal,
      Supplier<Double> br_steer_signal,
      Supplier<Double> fr_drive_signal,
      Supplier<Double> fr_steer_signal) {
    thread = new Thread(this::run);
    thread.setName("SimOdometryThread");
    thread.setDaemon(true);

    signals.add(0, fl_drive_signal);
    signals.add(1, fl_steer_signal);
    signals.add(2, bl_drive_signal);
    signals.add(3, bl_steer_signal);
    signals.add(4, br_drive_signal);
    signals.add(5, br_steer_signal);
    signals.add(6, fr_drive_signal);
    signals.add(7, fr_steer_signal);
  }

  synchronized ArrayBlockingQueue<WheeledObservation> start() {
    if (!isRunning) {
      timer.start();
      isRunning = true;
      thread.start();
    }

    return odometryCachedWheeledObservationQueue;
  }

  private void run() {
    while (isRunning) {
      if (timer.hasElapsed(Constants.LOOP_PERIOD_SEC)) {
        odometryCachedWheeledObservationQueue.offer(
            new WheeledObservation(
                Logger.getTimestamp() / 1.0e9,
                new SwerveModulePosition[] {
                  signalValue2SwerveModulePosition(signals.get(0).get(), signals.get(1).get()),
                  signalValue2SwerveModulePosition(signals.get(2).get(), signals.get(3).get()),
                  signalValue2SwerveModulePosition(signals.get(4).get(), signals.get(5).get()),
                  signalValue2SwerveModulePosition(signals.get(6).get(), signals.get(7).get()),
                },
                null));
        timer.reset();
      }
      LockSupport.parkNanos(1_000_000);
    }

    isRunning = false;
  }

  @Override
  public void close() {
    isRunning = false;
    thread.interrupt();
  }

  private SwerveModulePosition signalValue2SwerveModulePosition(
      double rawDrivePosition, double rawSteerPosition) {
    return new SwerveModulePosition(
        rawDrivePosition * SwerveConfig.WHEEL_RADIUS_METER,
        Rotation2d.fromRadians(rawSteerPosition));
  }
}
