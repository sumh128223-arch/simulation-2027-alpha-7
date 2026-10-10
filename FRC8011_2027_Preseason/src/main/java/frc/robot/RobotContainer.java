// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Pose3d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Rotation3d;
import org.wpilib.math.geometry.Transform3d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.geometry.Translation3d;
import org.wpilib.math.util.Units;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.driverstation.GenericHID.RumbleType;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.button.CommandNiDsXboxController;
import org.wpilib.command2.button.Trigger;
import frc.lib.dashboard.BooleanChooser;
import frc.lib.service.CommandSelector;
import frc.lib.service.GamePieceVisualizer;
import frc.lib.service.MatchTimerSystem;
import frc.lib.service.TunableManager;
import frc.lib.service.Visualizer;
import frc.rebuilt.Field;
import frc.rebuilt.Field.AllianceFlipUtil;
import frc.robot.Constants.Misc;
import frc.robot.Constants.Ports;
import frc.robot.command.WaitAllianceConfirmed;
import frc.robot.subsystem.indexer.Indexer;
import frc.robot.subsystem.indexer.IndexerGoal;
import frc.robot.subsystem.intake.Intake;
import frc.robot.subsystem.intake.IntakeGoal.RollerGoal;
import frc.robot.subsystem.intake.IntakeGoal.SlamArmGoal;
import frc.robot.subsystem.shooter.Shooter;
import frc.robot.subsystem.shooter.ShooterGoal;
import frc.robot.subsystem.swerve.Swerve;
import frc.robot.subsystem.swerve.command.FollowTrajectory;
import frc.robot.subsystem.swerve.command.ProceedToPoint;
import frc.robot.subsystem.swerve.command.TeleopController;
import frc.robot.subsystem.vision.ApriltagVision;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.littletonrobotics.junction.Logger;

public class RobotContainer {
  private static final Odometry odometry = new Odometry();
  private static final TrajectorySet trajectorySet = new TrajectorySet();

  public static Odometry getOdometry() {
    return odometry;
  }

  public static TrajectorySet getTrajectorySet() {
    return trajectorySet;
  }

  // driver
  CommandNiDsXboxController driver = new CommandNiDsXboxController(Ports.Joystick.DRIVER);

  // subsystem
  private final Swerve swerve;
  private final Intake intake;
  private final Indexer indexer;
  private final Shooter shooter;
  private TeleopController teleopDrive;

  // service
  private final CommandSelector autoCmdSelector = new CommandSelector("Auto");
  private final MatchTimerSystem matchTimerSystem = new MatchTimerSystem();

  // states
  protected BooleanChooser g_AutoAlign = new BooleanChooser("Config/auto_align", true);
  protected BooleanChooser g_ShouldShake = new BooleanChooser("Config/should_shake", true);

  public RobotContainer() {
    if (Constants.MODE.equals(Constants.Mode.REAL)) {
      swerve = Swerve.createReal();
      // swerve = Swerve.createIO();
      intake = Intake.createReal();
      // intake = Intake.createIO();
      indexer = Indexer.createReal();
      shooter = Shooter.createReal();

      ApriltagVision apriltagVision = ApriltagVision.createReal();
      configureVisualization(new Visualizer());
    } else if (Constants.MODE.equals(Constants.Mode.SIM)) {
      swerve = Swerve.createSim();
      intake = Intake.createSim();
      indexer = Indexer.createSim();
      shooter = Shooter.createSim();

      ApriltagVision apriltagVision =
          ApriltagVision.createSim(() -> RobotContainer.getOdometry().getEstimatedPose());

      Visualizer visualizer = new Visualizer();
      GamePieceVisualizer fuelVisualizer =
          new GamePieceVisualizer(
              "Fuel",
              0.2,
              0x3f3f3f3f,
              frc.rebuilt.Field.StartingGamePieces.pickableGamePiece.clone(),
              new Pose3d[0],
              8);
      configureVisualization(visualizer);
      configureVisualization(fuelVisualizer);
    } else {
      swerve = Swerve.createIO();
      intake = Intake.createIO();
      indexer = Indexer.createIO();
      shooter = Shooter.createIO();
    }

    configureBindings();
    configureAuto(swerve, intake, shooter, indexer);
    configureDebugGroup();

    System.out.println("##########################################");
    System.out.println("# RobotContainer Initialization Complete #");
    System.out.println("#               Mode: " + Constants.MODE + "               #");
    System.out.println("##########################################");
  }

  private void configureBindings() {
    // ----- Drive -----
    // Use CommandNiDsXboxController: it reads the classic NI-DS axis order
    // (0=LX,1=LY,2=LT,3=RT,4=RX,5=RY), which matches what the 2027 alpha7 sim reports.
    // CommandXboxController uses SDL order and mismatches triggers/right-stick in sim.
    teleopDrive =
        new TeleopController(
            swerve,
            () -> -driver.getLeftY() * (driver.getNiDsXboxController().getLeftBumperButton() ? 0.7 : 1.0),
            () -> -driver.getLeftX() * (driver.getNiDsXboxController().getLeftBumperButton() ? 0.7 : 1.0),
            () -> -driver.getRightX() * (driver.getNiDsXboxController().getLeftBumperButton() ? 0.5 : 0.9));
    swerve.setDefaultCommand(teleopDrive);

    driver
        .back()
        .onTrue(
            RobotContainer.getOdometry()
                .resetPoseCommand(
                    () ->
                        new Pose2d(
                            RobotContainer.getOdometry().getEstimatedPose().getTranslation(),
                            Rotation2d.ZERO))
                .alongWith(MyCommand.joystickRumblerCommand(driver, 0.2, RumbleType.LEFT_RUMBLE, RumbleType.RIGHT_RUMBLE))
                .withName("Reset Pose")
                .ignoringDisable(true));

    driver.b().onTrue(MyCommand.joystickRumblerCommand(driver, 1, RumbleType.LEFT_RUMBLE, RumbleType.RIGHT_RUMBLE));

    // ----- Macro Test Bindings -----

    // 1. Full Intake: LT
    driver
        .leftTrigger()
        .whileTrue(MyCommand.intakeMacroCommand(intake));

    // 2. Full Shoot (Spin up 0.2s then Launch + Feed + Intake Bump): RT
    driver
        .rightTrigger()
        .whileTrue(
            MyCommand.shootMacroCommand(
                shooter,
                indexer,
                intake,
                driver,
                teleopDrive,
                () -> 0.2,
                () -> ShooterGoal.MANUAL_LAUNCH,
                // g_AutoAlign,
                () -> false,
                g_ShouldShake));

    // Deliver macro: right bumper
    driver
        .rightBumper()
        .whileTrue(MyCommand.deliverCommand(teleopDrive, intake, shooter, indexer, g_AutoAlign));

    driver.a().onTrue(MyCommand.forceIdleCommand(intake));

    driver
        .x()
        .onTrue(
            Commands.sequence(
                Commands.runOnce(() -> teleopDrive.setAimToAngle(Rotation2d.fromDegrees(-90))),
                Commands.waitUntil(() -> teleopDrive.isAimToAngleAtGoal()),
                Commands.runOnce(() -> teleopDrive.resetAimToAngle())));
    driver
        .b()
        .onTrue(
            Commands.sequence(
                Commands.runOnce(() -> teleopDrive.setAimToAngle(Rotation2d.fromDegrees(90))),
                Commands.waitUntil(() -> teleopDrive.isAimToAngleAtGoal()),
                Commands.runOnce(() -> teleopDrive.resetAimToAngle())));

    // driver
    //     .x()
    //     .whileTrue(
    //         Commands.startEnd(
    //             () -> shooter.setGoal(ShooterGoal.DELIEVER),
    //             () -> shooter.setGoal(ShooterGoal.STOP)));
    // driver
    //     .b()
    //     .whileTrue(
    //         Commands.startEnd(
    //             () -> indexer.setGoal(IndexerGoal.FEED), () ->
    // indexer.setGoal(IndexerGoal.STOP)));

    driver
        .y()
        .whileTrue(
            new ProceedToPoint(
                swerve,
                () ->
                    new Pose2d(
                        odometry.getEstimatedPose().getX(),
                        odometry.getEstimatedPose().getY() < Field.WIDTH / 2.0
                            ? Field.Trench.Y
                            : AllianceFlipUtil.mirrorY(Field.Trench.Y),
                        Math.abs(odometry.getEstimatedPose().getRotation().getDegrees()) < 90.0
                            ? Rotation2d.ZERO
                            : Rotation2d.PI)));
    driver
        .getHID()
        .povUp()
        .whileTrue(
            MyCommand.shootFixedCommand(
                swerve,
                shooter,
                indexer,
                intake,
                teleopDrive,
                () -> 0.2,
                () -> Misc.LEFT_TOWER_SHOOT,
                g_AutoAlign,
                g_ShouldShake));
    driver
        .getHID()
        .povDown()
        .whileTrue(
            MyCommand.shootFixedCommand(
                swerve,
                shooter,
                indexer,
                intake,
                teleopDrive,
                () -> 0.2,
                () -> Misc.RIGHT_TOWER_SHOOT,
                g_AutoAlign,
                g_ShouldShake));
    driver
        .getHID()
        .povLeft()
        .whileTrue(
            MyCommand.shootFixedCommand(
                swerve,
                shooter,
                indexer,
                intake,
                teleopDrive,
                () -> 0.2,
                () -> Misc.LEFT_TRENCH_SHOOT,
                g_AutoAlign,
                g_ShouldShake));
    driver
        .getHID()
        .povRight()
        .whileTrue(
            MyCommand.shootFixedCommand(
                swerve,
                shooter,
                indexer,
                intake,
                teleopDrive,
                () -> 0.2,
                () -> Misc.RIGHT_TRENCH_SHOOT,
                g_AutoAlign,
                g_ShouldShake));
  }

  // ----- High-Level Macro Commands -----

  /** 1. Full intake: Pivot deploy, Roller intake */
  public class MyCommand {

    public static Command joystickRumblerCommand(
        CommandNiDsXboxController controller, double timeoutSecs, RumbleType... rumbleTypes) {
      return Commands.runEnd(
              () -> { for (var type : rumbleTypes) controller.getHID().setRumble(type, 1.0); },
              () -> { for (var type : rumbleTypes) controller.getHID().setRumble(type, 0.0); })
          .withTimeout(timeoutSecs)
          .ignoringDisable(true)
          .withName("Joystick Rumbler");
    }

    public static Command intakeMacroCommand(Intake intake) {
      return Commands.startEnd(
              () -> {
                intake.setPivotGoal(SlamArmGoal.DEPLOY);
                intake.setRollerGoal(RollerGoal.INTAKE);
              },
              () -> {
                intake.setRollerGoal(RollerGoal.IDLE);
                if (Constants.MODE == Constants.Mode.SIM) {
                  intake.setPivotGoal(SlamArmGoal.RETRACT);
                }
              },
              intake)
          .withName("Macro/FullIntake");
    }

    /** 2. Full shoot: Spin up 0.2s, then feed + bump */
    public static Command shootFixedCommand(
        Swerve swerve,
        Shooter shooter,
        Indexer indexer,
        Intake intake,
        TeleopController teleopDrive,
        DoubleSupplier spinUpTimeSupplier,
        Supplier<Pose2d> scorePoseSupplier,
        BooleanSupplier autoAlignSupplier,
        BooleanSupplier shouldShakeSupplier) {
      final ShooterGoal shooterGoal =
          scorePoseSupplier.get() == Misc.LEFT_TOWER_SHOOT
                  || scorePoseSupplier.get() == Misc.RIGHT_TOWER_SHOOT
              ? ShooterGoal.TOWER_LAUNCH
              : ShooterGoal.TRENCH_LAUNCH;
      var proceedToPoint =
          new ProceedToPoint(swerve, () -> AllianceFlipUtil.apply(scorePoseSupplier.get()));
      return Commands.sequence(
              Commands.either(proceedToPoint, Commands.none(), autoAlignSupplier),
              shooter.spinUpCommand(shooterGoal),
              // Commands.waitSeconds(spinUpTimeSupplier.getAsDouble()),
              Commands.waitSeconds(0.3),
              // .alongWith(Commands.either(proceedToPoint, Commands.none(), autoAlignSupplier)),
              Commands.runOnce(() -> teleopDrive.setAimToShake(shouldShakeSupplier.getAsBoolean())),
              shooter.launchCommand(shooterGoal),
              Commands.runOnce(() -> indexer.setGoal(IndexerGoal.FEED)),
              intake.pivotBumpCommand())
          .finallyDo(
              () -> {
                intake.setRollerGoal(RollerGoal.IDLE);
                intake.setPivotGoal(SlamArmGoal.STOP);
                shooter.stop();
                indexer.setGoal(IndexerGoal.STOP);
                teleopDrive.setAimToShake(false);
              })
          .withName("Macro/FixedShoot");
    }

    public static Command shootMacroCommand(
        Shooter shooter,
        Indexer indexer,
        Intake intake,
        CommandNiDsXboxController driver,
        TeleopController teleopDrive,
        DoubleSupplier spinUpTimeSupplier,
        Supplier<ShooterGoal> shootGoalSupplier,
        BooleanSupplier autoAlignSupplier,
        BooleanSupplier shouldShakeSupplier) {
      return Commands.defer(
              () -> {
                final ShooterGoal shootGoal = shootGoalSupplier.get();
                return Commands.sequence(
                        // 1. Spin up for 0.2s
                        shooter.spinUpCommand(shootGoal),
                        Commands.runOnce(
                            () -> {
                              teleopDrive.setAimToShake(shouldShakeSupplier.getAsBoolean());
                              teleopDrive.setAimToHub(autoAlignSupplier.getAsBoolean());
                            }),
                        Commands.parallel(
                            Commands.waitUntil(() -> teleopDrive.isAimToHubAtGoal()),
                            // Commands.waitSeconds(spinUpTimeSupplier.getAsDouble())
                            Commands.waitSeconds(0.3)),
                        // 2. Launch
                        Commands.parallel(
                            joystickRumblerCommand(driver, 0.2, RumbleType.LEFT_RUMBLE, RumbleType.RIGHT_RUMBLE),
                            shooter.launchCommand(shootGoal),
                            smartIndexerCommand(indexer),
                            intake.injectCommand(),
                            intake.pivotBumpCommand()))
                    .finallyDo(
                        () -> {
                          // Reset everything
                          teleopDrive.setAimToShake(false);
                          teleopDrive.setAimToHub(false);
                          intake.setRollerGoal(RollerGoal.IDLE);
                          intake.setPivotGoal(SlamArmGoal.STOP);
                          indexer.setGoal(IndexerGoal.STOP);
                          shooter.setGoal(ShooterGoal.STOP); // <-- Safely stop shooter too
                        });
              },
              Set.of(shooter, indexer, intake))
          .withName("Macro/AutoShoot");
    }

    public static Command deliverCommand(
        TeleopController teleopDrive,
        Intake intake,
        Shooter shooter,
        Indexer indexer,
        BooleanSupplier autoAlignSupplier) {

      return Commands.sequence(
              Commands.runOnce(
                  () -> {
                    if (autoAlignSupplier.getAsBoolean())
                      teleopDrive.setAimToAngle(Rotation2d.fromDegrees(180.0));
                  }),
              shooter.spinUpCommand(ShooterGoal.DELIEVER),
              Commands.waitUntil(() -> teleopDrive.isAimToAngleAtGoal()),
              shooter.launchCommand(ShooterGoal.DELIEVER),
              Commands.runOnce(() -> indexer.setGoal(IndexerGoal.FEED)),
              intake.pivotBumpCommand())
          .finallyDo(
              () -> {
                teleopDrive.resetAimToAngle();
                shooter.setGoal(ShooterGoal.STOP);
                indexer.setGoal(IndexerGoal.STOP);
              })
          .withName("Macro/Deliver");
    }

    public static Command smartIndexerCommand(Indexer indexer) {
      return Commands.runOnce(() -> indexer.setGoal(IndexerGoal.FEED));
    }

    public static Command forceIdleCommand(Intake intake) {
      return Commands.runOnce(
              () -> {
                intake.setPivotGoal(SlamArmGoal.RETRACT);
              })
          .withName("ForceIdle");
    }
  }

  private void configureAuto(Swerve swerve, Intake intake, Shooter shooter, Indexer indexer) {
    new Trigger(() -> RobotState.isAutonomousEnabled())
        .onTrue(autoCmdSelector.stop())
        .onFalse(autoCmdSelector.run());

    // autoCmdSelector.addCommand(
    //     "Wheel Radius Characterization",
    //     new WheelRadiusCharacterization(
    //         swerve,
    //         () -> RobotContainer.getOdometry().getEstimatedPose().getRotation(),
    //         Direction.CLOCKWISE));

    // for (String position : List.of("Close", "Mid", "Far")) {
    //   autoCmdSelector.addCommand(
    //       "人玩+舔球+左出 (" + position + ")",
    //       Commands.defer(
    //           () -> {
    //             System.out.println("Selected Auto Position: " + position);
    //             return Commands.sequence(
    //                 Commands.print("Selected Position: " + position),
    //                 new WaitAllianceConfirmed(),
    //                 odometry.resetPoseCommand(
    //                     () -> new Pose2d(Field.Hub.MIN_X - 0.35, Field.Trench.Y,
    // Rotation2d.ZERO)),
    //                 Commands.runOnce(() -> intake.setPivotGoal(SlamArmGoal.DEPLOY)),
    //                 new FollowTrajectory(swerve, () -> trajectorySet.rightback2outpost.get()),
    //                 Commands.waitSeconds(0.5),
    //                 new FollowTrajectory(swerve, () -> trajectorySet.outpost2depot.get()),
    //                 Commands.sequence(Commands.waitSeconds(1.0), intake.injectCommand()),
    //                 new FollowTrajectory(swerve, () -> trajectorySet.depot2leftShoot.get()),
    //                 MyCommand.shootFixedCommand(
    //                         swerve,
    //                         shooter,
    //                         indexer,
    //                         intake,
    //                         teleopDrive,
    //                         () -> 0.2,
    //                         () -> Misc.LEFT_TRENCH_SHOOT)
    //                     .withTimeout(1.0),
    //                 intake.injectCommand(),
    //                 new FollowTrajectory(
    //                     swerve,
    //                     () -> {
    //                       switch (position) {
    //                         case "Mid":
    //                           return trajectorySet.leftShoot_mid.get();
    //                         case "Far":
    //                           return trajectorySet.leftShoot_far.get();
    //                         case "Close":
    //                         default:
    //                           return trajectorySet.leftShoot_close.get();
    //                       }
    //                     }),
    //                 MyCommand.shootFixedCommand(
    //                         swerve,
    //                         shooter,
    //                         indexer,
    //                         intake,
    //                         teleopDrive,
    //                         () -> 0.2,
    //                         () -> Misc.LEFT_TRENCH_SHOOT)
    //                     .withTimeout(1.0));
    //           },
    //           Set.of(swerve, intake, shooter, indexer)));
    // }

    autoCmdSelector.addCommand(
        "左两cycle+舔球",
        Commands.sequence(
            new WaitAllianceConfirmed(),
            odometry.resetPoseCommand(
                () ->
                    new Pose2d(
                        Field.Hub.MIN_X + 0.35,
                        AllianceFlipUtil.mirrorY(Field.Trench.Y),
                        Rotation2d.fromDegrees(180.0))),
            intake.injectCommand(),
            intake.pivotDeployCommand(),
            // Commands.parallel(
            new FollowTrajectory(swerve, () -> trajectorySet.leftFront2leftShoot_far.get()),
            // Commands.sequence(
            //     intake.injectCommand(),
            //     Commands.waitSeconds(0.3),
            //     Commands.runOnce(() -> intake.setPivotGoal(IntakeGoal.SlamArmGoal.RETRACT)),
            //     Commands.waitSeconds(0.3),
            //     intake.injectCommand())),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.LEFT_TRENCH_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(6.0),
            intake.injectCommand(),
            intake.pivotDeployCommand(),
            new FollowTrajectory(swerve, () -> trajectorySet.leftShoot_close.get()),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.LEFT_TRENCH_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(4.5),
            intake.injectCommand(),
            intake.pivotDeployCommand(),
            new FollowTrajectory(swerve, () -> trajectorySet.leftShoot2Depot.get()),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.LEFT_TOWER_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(6.0)));

    autoCmdSelector.addCommand(
        "右两cycle+人玩",
        Commands.sequence(
            new WaitAllianceConfirmed(),
            odometry.resetPoseCommand(
                () ->
                    new Pose2d(
                        Field.Hub.MIN_X + 0.35, Field.Trench.Y, Rotation2d.fromDegrees(180.0))),
            intake.injectCommand(),
            intake.pivotDeployCommand(),
            // Commands.parallel(
            new FollowTrajectory(swerve, () -> trajectorySet.rightFront2rightShoot_far.get()),
            // Commands.sequence(
            //     intake.injectCommand(),
            //     Commands.waitSeconds(0.3),
            //     Commands.runOnce(() -> intake.setPivotGoal(IntakeGoal.SlamArmGoal.RETRACT)),
            //     Commands.waitSeconds(0.3),
            //     intake.injectCommand())),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.RIGHT_TRENCH_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(6.0),
            intake.injectCommand(),
            intake.pivotDeployCommand(),
            new FollowTrajectory(swerve, () -> trajectorySet.rightShoot_close.get()),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.RIGHT_TRENCH_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(4.5),
            new FollowTrajectory(swerve, () -> trajectorySet.rightShoot2outpost.get()),
            Commands.waitSeconds(0.5),
            new FollowTrajectory(swerve, () -> trajectorySet.outpost2rightShoot.get()),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.RIGHT_TOWER_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(6.0)));
    autoCmdSelector.addCommand(
        "右一cycle+人玩",
        Commands.sequence(
            new WaitAllianceConfirmed(),
            odometry.resetPoseCommand(
                () ->
                    new Pose2d(
                        Field.Hub.MIN_X + 0.35, Field.Trench.Y, Rotation2d.fromDegrees(180.0))),
            // Commands.repeatingSequence(
            //         intake.pivotDeployCommand(),
            //         Commands.waitSeconds(0.3),
            //         intake.pivotRetractCommand(),
            //         Commands.waitSeconds(0.1))
            //     .withDeadline(
            //         Commands.waitSeconds(1.3)
            //         // Commands.waitUntil(
            //         // () -> Units.radiansToDegrees(intake.getPivotPositionRad()) < 20.0)
            //         ),
            // intake.pivotRetractCommand(),
            intake.pivotDeployCommand(),
            Commands.waitSeconds(1.0),
            intake.injectCommand(),
            new FollowTrajectory(swerve, () -> trajectorySet.rightFront2rightShoot_far.get()),
            Commands.waitSeconds(0.05),
            new ProceedToPoint(swerve, () -> AllianceFlipUtil.apply(Misc.RIGHT_TOWER_SHOOT))
                .withTimeout(1.0),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.RIGHT_TOWER_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(4.0),
            new FollowTrajectory(swerve, () -> trajectorySet.rightShoot2outpost.get()),
            new ProceedToPoint(
                    swerve,
                    () ->
                        AllianceFlipUtil.apply(
                            new Pose2d(
                                Field.Outpost.X - 0.35, Field.Outpost.Y - 0.35, new Rotation2d())),
                    () -> 2.5,
                    () -> 180.0)
                .withTimeout(1.5),
            Commands.waitSeconds(1.5),
            new ProceedToPoint(swerve, () -> AllianceFlipUtil.apply(Misc.RIGHT_TOWER_SHOOT))
                .withTimeout(2.0),
            // new FollowTrajectory(swerve, () -> trajectorySet.outpost2rightShoot.get()),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.RIGHT_TOWER_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(6.0)));
    // autoCmdSelector.addCommand(
    //     "左一cycle+地面",
    //     Commands.sequence(
    // new WaitAllianceConfirmed(),
    // odometry.resetPoseCommand(
    //     () ->
    //         new Pose2d(
    //             Field.Hub.MIN_X + 0.35,
    //             AllianceFlipUtil.mirrorY(Field.Trench.Y),
    //             Rotation2d.fromDegrees(180.0))),
    // intake.injectCommand(),
    // new FollowTrajectory(swerve, () -> trajectorySet.leftFront2leftShoot_far.get()),
    // MyCommand.shootFixedCommand(
    //         swerve,
    //         shooter,
    //         indexer,
    //         intake,
    //         teleopDrive,
    //         () -> 0.2,
    //         () -> Misc.LEFT_TRENCH_SHOOT,
    //         () -> false)
    //     .withTimeout(6.0),
    // new FollowTrajectory(swerve, () -> trajectorySet.leftShoot2Depot.get()),
    // MyCommand.shootFixedCommand(
    //         swerve,
    //         shooter,
    //         indexer,
    //         intake,
    //         teleopDrive,
    //         () -> 0.2,
    //         () -> Misc.LEFT_TOWER_SHOOT,
    //         () -> false)
    //     .withTimeout(6.0)));

    autoCmdSelector.addCommand(
        "中出8个",
        Commands.sequence(
            new WaitAllianceConfirmed(),
            odometry.resetPoseCommand(() -> new Pose2d(3.6, 0.66, Rotation2d.k180deg)),
            new FollowTrajectory(swerve, () -> trajectorySet.newPath.get()),
            new ProceedToPoint(swerve, () -> AllianceFlipUtil.apply(Misc.RIGHT_TOWER_SHOOT)),
            MyCommand.shootFixedCommand(
                swerve,
                shooter,
                indexer,
                intake,
                teleopDrive,
                () -> 0.3,
                () -> Misc.RIGHT_TOWER_SHOOT,
                () -> false,
                () -> false)));

    autoCmdSelector.addCommand(
        "舔球直接射",
        Commands.sequence(
            new WaitAllianceConfirmed(),
            odometry.resetPoseCommand(
                () ->
                    new Pose2d(
                        Field.Hub.MIN_X - 0.35, Field.Trench.Y, Rotation2d.fromDegrees(0.0))),
            new FollowTrajectory(swerve, () -> trajectorySet.rightback2outpost.get()),
            intake.injectCommand(),
            Commands.waitSeconds(1.0),
            Commands.runOnce(() -> intake.stop()),
            new FollowTrajectory(swerve, () -> trajectorySet.outpost2rightShoot.get()),
            MyCommand.shootFixedCommand(
                    swerve,
                    shooter,
                    indexer,
                    intake,
                    teleopDrive,
                    () -> 0.2,
                    () -> Misc.RIGHT_TOWER_SHOOT,
                    () -> false,
                    g_ShouldShake)
                .withTimeout(6.0)));
  }

  private void configureDebugGroup() {
    TunableManager debugGroup = new TunableManager("DebugGroup");
    debugGroup.register(Constants.DebugGroup.SWERVE);
    debugGroup.register(Constants.DebugGroup.ODOMETRY);
    debugGroup.register(Constants.DebugGroup.INTAKE);
    debugGroup.register(Constants.DebugGroup.INDEXER);
    debugGroup.register(Constants.DebugGroup.SHOOTER);
    new Trigger(Constants.IS_LIVE_DEBUG).onTrue(debugGroup.run()).onFalse(debugGroup.stop());
  }

  private void configureVisualization(Visualizer visualizer) {
    visualizer.registerVisualizedComponent(
        Visualizer.BASE_FRAME,
        "chassis",
        0,
        // These GLBs already have Z up. Rotate only about Z so the intake faces -X.
        () -> new Transform3d(0.0, 0.0, 0.0, new Rotation3d(0.0, 0.0, -Math.PI / 2.0)));
    visualizer.registerVisualizedComponent(
        "chassis",
        "intake",
        1,
        () ->
            new Transform3d(
                0.0,
                -0.30235,
                0.2075,
                new Rotation3d(
                    // The GLB was exported deployed: zero radians is the file's pose.
                    -intake.getPivotPositionRad(), 0.0, 0.0)));
    // Robot_8011 uses mainrobot.glb (0) and intake.glb (1), with no legacy hopper.
    visualizer.print();
  }

  private void configureVisualization(GamePieceVisualizer fuelVisualizer) {
    CommandScheduler.getInstance().schedule(Commands.run(
            () -> {
              if (RobotState.isEnabled() && intake.getRollerGoal() == RollerGoal.INTAKE) {
                Pose2d robotPose2d = RobotContainer.getOdometry().getEstimatedPose();
                Translation2d robotTrans = robotPose2d.getTranslation();
                Rotation2d robotRot = robotPose2d.getRotation();

                for (Pose3d piece : fuelVisualizer.getPickableGamePiecePose()) {
                  if (fuelVisualizer.getHasGamePieceNums() >= 100) {
                    break;
                  }
                  if (piece.getX() == 0x3f3f3f3f) {
                    continue;
                  }

                  Translation2d relative =
                      piece
                          .getTranslation()
                          .toTranslation2d()
                          .minus(robotTrans)
                          .rotateBy(robotRot.unaryMinus());

                  if (relative.getX() <= 0.0 && relative.getX() >= -0.6) {
                    if (Math.abs(relative.getY()) <= 0.35) { // 0.7m width
                      fuelVisualizer.tryPick(piece);
                    }
                  }
                }
              }
            })
        .ignoringDisable(true));

    CommandScheduler.getInstance().schedule(Commands.sequence(
            Commands.runOnce(
                () -> {
                  Pose2d robotPose2d = RobotContainer.getOdometry().getEstimatedPose();
                  Translation2d robotTrans = robotPose2d.getTranslation();
                  Rotation2d robotRot = robotPose2d.getRotation();

                  // Offset slightly to the front
                  Translation2d frontOffset = new Translation2d(0.5, 0).rotateBy(robotRot);
                  Pose3d startPose =
                      new Pose3d(
                          robotTrans.getX() + frontOffset.getX(),
                          robotTrans.getY() + frontOffset.getY(),
                          0.5,
                          new org.wpilib.math.geometry.Rotation3d());

                  // Calculate velocity vector: 45 degrees up, forward relative to robot
                  double speed = 6.7;
                  double vx = robotRot.getCos() * speed * Math.cos(Math.toRadians(60));
                  double vy = robotRot.getSin() * speed * Math.cos(Math.toRadians(60));
                  double vz = speed * Math.sin(Math.toRadians(60));

                  if (RobotState.isEnabled()
                      && (shooter.getGoal() == ShooterGoal.MANUAL_LAUNCH
                          || shooter.getGoal() == ShooterGoal.DELIEVER
                          || shooter.getGoal() == ShooterGoal.TRENCH_LAUNCH
                          || shooter.getGoal() == ShooterGoal.TOWER_LAUNCH)
                      && shooter.isFeedEnabled()) {
                    fuelVisualizer.tryEject(
                        startPose,
                        new Translation3d(vx, vy, vz),
                        shooter.getGoal() != ShooterGoal.DELIEVER);
                  }
                }),
            Commands.waitSeconds(0.04))
        .repeatedly()
        .ignoringDisable(true));
  }

  public Command getAutonomousCommand() {
    return autoCmdSelector.getCommand();
  }

  public void logSimulationControls() {
    if (Constants.MODE == Constants.Mode.SIM) {
      Logger.recordOutput("Simulation/Enabled", RobotState.isEnabled());
      Logger.recordOutput("Simulation/Controls/LT", driver.getLeftTriggerAxis());
      Logger.recordOutput("Simulation/Controls/RT", driver.getRightTriggerAxis());
      Logger.recordOutput("Simulation/Intake/AngleDeg", Units.radiansToDegrees(intake.getPivotPositionRad()));
      Logger.recordOutput("Simulation/Intake/Goal", intake.getPivotGoal().name());
      Logger.recordOutput("Simulation/Intake/RollerGoal", intake.getRollerGoal().name());
    }
  }
}
