// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.SignalLogger;
import org.wpilib.net.WebServer;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.system.Filesystem;
import org.wpilib.system.RobotController;
import org.wpilib.system.Threads;
import org.wpilib.system.Timer;
import org.wpilib.smartdashboard.Field2d;
import org.wpilib.telemetry.Telemetry;
import org.wpilib.telemetry.TelemetryRegistry;
import org.wpilib.backend.NetworkTablesTelemetryBackend;
import org.wpilib.networktables.NetworkTableInstance;
import frc.lib.dashboard.Alert;
import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;
import frc.lib.interfaces.VirtualSubsystem;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.littletonrobotics.junction.LogFileUtil;
import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.networktables.NT4Publisher;
import org.littletonrobotics.junction.wpilog.WPILOGReader;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;

public class Robot extends LoggedRobot {
  private Command autonomousCommand;
  private boolean autonomousHasPrinted;
  private double autonomousStartTime;

  private final RobotContainer robotContainer;
  private final Field2d field2d = new Field2d();

  public Robot() {
    TelemetryRegistry.registerBackend(
        "/SmartDashboard", new NetworkTablesTelemetryBackend(NetworkTableInstance.getDefault(), ""));
    CommandScheduler.getInstance().getActiveButtonLoop().clear();

    // configure CTRE
    SignalLogger.enableAutoLogging(false);
    SignalLogger.stop();

    // configure AKit
    loggerInit();

    robotContainer = new RobotContainer();
    WebServer.start(5800, Filesystem.getDeployDirectory().getPath());
  }

  private final void loggerInit() {
    switch (Constants.MODE) {
      case REAL -> {
        Logger.addDataReceiver(new WPILOGWriter("/home/systemcore/logs"));
        Logger.addDataReceiver(new NT4Publisher());
      }

      case SIM -> Logger.addDataReceiver(new NT4Publisher());
      case REPLAY -> {
        setUseTiming(false); // Run as fast as possible
        var logPath = LogFileUtil.findReplayLog();
        Logger.setReplaySource(new WPILOGReader(logPath));
        Logger.addDataReceiver(new WPILOGWriter(LogFileUtil.addPathSuffix(logPath, "_sim")));
      }
    }
    Logger.start();

    Map<String, Integer> commandCounts = new HashMap<>();
    BiConsumer<Command, Boolean> logCommandFunction =
        (Command command, Boolean active) -> {
          String name = command.getName();
          int count = commandCounts.getOrDefault(name, 0) + (active ? 1 : -1);
          commandCounts.put(name, count);
          Logger.recordOutput(
              "CommandsUnique/" + name + "_" + Integer.toHexString(command.hashCode()), active);
          Logger.recordOutput("CommandsAll/" + name, count > 0);
        };
    CommandScheduler.getInstance()
        .onCommandInitialize(
            (Command command) -> {
              System.out.println(commandPrintHelper(command.getName()));
              logCommandFunction.accept(command, true);
            });

    CommandScheduler.getInstance()
        .onCommandFinish(
            (Command command) -> {
              System.out.println(
                  "\u001B[32m" + commandPrintHelper(command.getName()) + "\u001B[0m");
              logCommandFunction.accept(command, false);
            });

    CommandScheduler.getInstance()
        .onCommandInterrupt(
            (Command command) -> {
              System.out.println(
                  "\u001B[31m" + commandPrintHelper(command.getName()) + "\u001B[0m");
              logCommandFunction.accept(command, false);
            });
  }

  @Override
  public void robotPeriodic() {
    Threads.setCurrentThreadPriority(99);

    if (autonomousCommand != null) {
      if (!autonomousCommand.isScheduled() && !autonomousHasPrinted) {
        if (RobotState.isAutonomousEnabled()) {
          System.out.printf(
              "*** Auto finished in %.2f secs ***%n",
              Timer.getTimestamp() - autonomousStartTime);
        } else {
          System.out.printf(
              "*** Auto cancelled in %.2f secs ***%n",
              Timer.getTimestamp() - autonomousStartTime);
        }
        autonomousHasPrinted = true;
      }
    }

    VirtualSubsystem.periodicAll();
    CommandScheduler.getInstance().run();
    robotContainer.logSimulationControls();
    Alert.publishAll();

    field2d.setRobotPose(RobotContainer.getOdometry().getEstimatedPose());
    Telemetry.log("/SmartDashboard/Field", field2d);
    Telemetry.log("/SmartDashboard/Voltage", RobotController.getBatteryVoltage());

    Threads.setCurrentThreadPriority(10);
  }

  @Override
  public void disabledInit() {}

  @Override
  public void disabledPeriodic() {}

  @Override
  public void disabledExit() {}

  @Override
  public void autonomousInit() {
    autonomousCommand = robotContainer.getAutonomousCommand();

    if (autonomousCommand != null) {
      autonomousHasPrinted = false;
      autonomousStartTime = Timer.getTimestamp();
      CommandScheduler.getInstance().schedule(autonomousCommand);
    }
  }

  @Override
  public void autonomousPeriodic() {}

  @Override
  public void autonomousExit() {}

  @Override
  public void teleopInit() {
    if (autonomousCommand != null) {
      autonomousCommand.cancel();
    }
  }

  @Override
  public void teleopPeriodic() {}

  @Override
  public void teleopExit() {}

  @Override
  public void utilityInit() {
    CommandScheduler.getInstance().cancelAll();
  }

  @Override
  public void utilityPeriodic() {}

  @Override
  public void utilityExit() {}

  private String commandPrintHelper(String name) {
    switch (name.split("/").length) {
      case 2:
        {
          String subsystem = name.split("/")[0];
          String command = name.split("/")[1];
          StringBuilder sb = new StringBuilder("$ [");
          sb.append(subsystem);
          sb.append("] ");
          sb.append(command);
          return sb.toString();
        }
      case 3:
        {
          String subsystem = name.split("/")[0];
          String command = name.split("/")[1];
          String subcommand = name.split("/")[2];
          StringBuilder sb = new StringBuilder("$ [");
          sb.append(subsystem);
          sb.append("] ");
          sb.append(command);
          sb.append(" => ");
          sb.append(subcommand);
          return sb.toString();
        }
      default:
        return "# " + name;
    }
  }
}
