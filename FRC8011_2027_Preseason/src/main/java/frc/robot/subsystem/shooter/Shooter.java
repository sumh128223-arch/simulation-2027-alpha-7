package frc.robot.subsystem.shooter;

import org.wpilib.math.system.DCMotor;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import frc.lib.dashboard.Alert;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.lib.interfaces.motor.GenericFlywheelIO;
import frc.lib.interfaces.motor.GenericFlywheelIOInputsAutoLogged;
import frc.lib.interfaces.motor.GenericFlywheelIOKraken;
import frc.lib.interfaces.motor.GenericFlywheelIOSim;
import frc.lib.interfaces.motor.GenericRollerIO;
import frc.lib.interfaces.motor.GenericRollerIOInputsAutoLogged;
import frc.lib.interfaces.motor.GenericRollerIOKraken;
import frc.lib.interfaces.motor.GenericRollerIOSim;
import frc.rebuilt.Field;
import frc.robot.Constants.Ports;
import frc.robot.RobotContainer;
import lombok.Getter;
import lombok.Setter;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

public class Shooter extends SubsystemBase {
  static {
    final var lowerGains = ShooterConfig.getLowerFlywheelGains();
    ShooterConfig.lowerFlywheelKp.initDefault(lowerGains.kp());
    ShooterConfig.lowerFlywheelKd.initDefault(lowerGains.kd());
    ShooterConfig.lowerFlywheelKs.initDefault(lowerGains.ks());

    final var upperGains = ShooterConfig.getUpperFlywheelGains();
    ShooterConfig.upperFlywheelKp.initDefault(upperGains.kp());
    ShooterConfig.upperFlywheelKd.initDefault(upperGains.kd());
    ShooterConfig.upperFlywheelKs.initDefault(upperGains.ks());
  }

  @Setter @Getter @AutoLogOutput private ShooterGoal goal = ShooterGoal.STOP;
  @Setter @Getter @AutoLogOutput private boolean feedEnabled = false;

  // ----- IO -----
  private final GenericFlywheelIO lowerFlywheelIO;
  private final GenericFlywheelIO upperFlywheelIO;
  private final GenericRollerIO lowerFeederIO;
  private final GenericRollerIO upperFeederIO;

  private final GenericFlywheelIOInputsAutoLogged lowerFlywheelInputs =
      new GenericFlywheelIOInputsAutoLogged();
  private final GenericFlywheelIOInputsAutoLogged upperFlywheelInputs =
      new GenericFlywheelIOInputsAutoLogged();
  private final GenericRollerIOInputsAutoLogged lowerFeederInputs =
      new GenericRollerIOInputsAutoLogged();
  private final GenericRollerIOInputsAutoLogged upperFeederInputs =
      new GenericRollerIOInputsAutoLogged();

  // ----- Alerts -----
  private final Alert lowerFlywheelOfflineAlert =
      new Alert("Shooter lower flywheel motor offline!", Alert.AlertType.WARNING);
  private final Alert upperFlywheelOfflineAlert =
      new Alert("Shooter upper flywheel motor offline!", Alert.AlertType.WARNING);
  private final Alert lowerFeederOfflineAlert =
      new Alert("Shooter lower feeder motor offline!", Alert.AlertType.WARNING);
  private final Alert upperFeederOfflineAlert =
      new Alert("Shooter upper feeder motor offline!", Alert.AlertType.WARNING);

  private Shooter(
      GenericFlywheelIO lowerFlywheelIO,
      GenericFlywheelIO upperFlywheelIO,
      GenericRollerIO lowerFeederIO,
      GenericRollerIO upperFeederIO) {
    this.lowerFlywheelIO = lowerFlywheelIO;
    this.upperFlywheelIO = upperFlywheelIO;
    this.lowerFeederIO = lowerFeederIO;
    this.upperFeederIO = upperFeederIO;
  }

  // ----- Factory Methods -----

  public static Shooter createReal() {
    return new Shooter(
        new GenericFlywheelIOKraken(
                "ShooterLower",
                Ports.Can.SHOOTER_LOWER_FLYWHEEL_FOLLOWER,
                ShooterConfig.getLowerFlywheelTalonConfig())
            .withFollower(Ports.Can.SHOOTER_LOWER_FLYWHEEL, true),
        new GenericFlywheelIOKraken(
            "ShooterUpper",
            Ports.Can.SHOOTER_UPPER_FLYWHEEL,
            ShooterConfig.getUpperFlywheelTalonConfig()),
        new GenericRollerIOKraken(
            "ShooterLowerFeeder",
            Ports.Can.SHOOTER_LOWER_FEEDER,
            ShooterConfig.getFeedTalonConfig(),
            50.0),
        new GenericRollerIOKraken(
            "ShooterUpperFeeder",
            Ports.Can.SHOOTER_UPPER_FEEDER,
            ShooterConfig.getFeedTalonConfig(),
            50.0));
  }

  public static Shooter createSim() {
    return new Shooter(
        new GenericFlywheelIOSim(DCMotor.getKrakenX60Foc(2), 1.0, 0.004),
        new GenericFlywheelIOSim(DCMotor.getKrakenX60Foc(1), 1.0, 0.002),
        new GenericRollerIOSim(DCMotor.getKrakenX60Foc(1), 1.0, 0.001),
        new GenericRollerIOSim(DCMotor.getKrakenX60Foc(1), 1.0, 0.001));
  }

  public static Shooter createIO() {
    return new Shooter(
        new GenericFlywheelIO() {},
        new GenericFlywheelIO() {},
        new GenericRollerIO() {},
        new GenericRollerIO() {});
  }

  // ----- Periodic -----

  @Override
  public void periodic() {
    lowerFlywheelIO.updateInputs(lowerFlywheelInputs);
    Logger.processInputs("Shooter/LowerFlywheel", lowerFlywheelInputs);
    lowerFlywheelOfflineAlert.set(!lowerFlywheelInputs.connected);

    upperFlywheelIO.updateInputs(upperFlywheelInputs);
    Logger.processInputs("Shooter/UpperFlywheel", upperFlywheelInputs);
    upperFlywheelOfflineAlert.set(!upperFlywheelInputs.connected);

    lowerFeederIO.updateInputs(lowerFeederInputs);
    Logger.processInputs("Shooter/LowerFeeder", lowerFeederInputs);
    lowerFeederOfflineAlert.set(!lowerFeederInputs.connected);

    upperFeederIO.updateInputs(upperFeederInputs);
    Logger.processInputs("Shooter/UpperFeeder", upperFeederInputs);
    upperFeederOfflineAlert.set(!upperFeederInputs.connected);

    LoggedTunableNumber.ifChanged(
        hashCode(),
        () ->
            lowerFlywheelIO.setPdf(
                ShooterConfig.lowerFlywheelKp.get(),
                ShooterConfig.lowerFlywheelKd.get(),
                ShooterConfig.lowerFlywheelKs.get()),
        ShooterConfig.lowerFlywheelKp,
        ShooterConfig.lowerFlywheelKd,
        ShooterConfig.lowerFlywheelKs);

    LoggedTunableNumber.ifChanged(
        hashCode() + 1,
        () ->
            upperFlywheelIO.setPdf(
                ShooterConfig.upperFlywheelKp.get(),
                ShooterConfig.upperFlywheelKd.get(),
                ShooterConfig.upperFlywheelKs.get()),
        ShooterConfig.upperFlywheelKp,
        ShooterConfig.upperFlywheelKd,
        ShooterConfig.upperFlywheelKs);

    if (goal == ShooterGoal.STOP) {
      lowerFeederIO.stop();
      upperFeederIO.stop();
      lowerFlywheelIO.stop();
      upperFlywheelIO.stop();
      return;
    }

    final var estimatedPose = RobotContainer.getOdometry().getEstimatedPose();
    final double distanceToHubMeters =
        estimatedPose.getTranslation().getDistance(Field.Hub.getCenter());
    final double distanceToOwnWallMeters = getDistanceToOwnAllianceWallMeters(estimatedPose.getX());
    Logger.recordOutput("Shooter/DistanceToHubMeters", distanceToHubMeters);
    Logger.recordOutput("Shooter/DistanceToOwnWallMeters", distanceToOwnWallMeters);

    lowerFlywheelIO.setVel(
        goal.getLowerFlywheelRadPerSec(), ShooterConfig.lowerFlywheelFf.getAsDouble());
    upperFlywheelIO.setVel(
        goal.getUpperFlywheelRadPerSec(), ShooterConfig.upperFlywheelFf.getAsDouble());

    final boolean isLaunchLikeGoal =
        goal == ShooterGoal.MANUAL_LAUNCH
            || goal == ShooterGoal.TOWER_LAUNCH
            || goal == ShooterGoal.TRENCH_LAUNCH;
    final boolean feedAllowed = isLaunchLikeGoal ? feedEnabled : true;
    lowerFeederIO.setVoltage(feedAllowed ? getLowerFeederVolts() : 0.0);
    upperFeederIO.setVoltage(feedAllowed ? getUpperFeederVolts() : 0.0);
  }

  private static double getDistanceToOwnAllianceWallMeters(double fieldXMeters) {
    final boolean isRed =
        MatchState.getAlliance().isPresent()
            && MatchState.getAlliance().get() == Alliance.RED;
    return isRed ? Field.LENGTH - fieldXMeters : fieldXMeters;
  }

  private double getLowerFeederVolts() {
    if (goal == ShooterGoal.OUTTAKE) {
      return ShooterConfig.outtakeLowerFeedVolts.get();
    }
    if (goal == ShooterGoal.STOP) {
      return 0.0;
    }
    return ShooterConfig.launchLowerFeedVolts.get();
  }

  private double getUpperFeederVolts() {
    if (goal == ShooterGoal.OUTTAKE) {
      return ShooterConfig.outtakeUpperFeedVolts.get();
    }
    if (goal == ShooterGoal.STOP) {
      return 0.0;
    }
    return ShooterConfig.launchUpperFeedVolts.get();
  }

  // ----- State Queries -----

  @AutoLogOutput(key = "Shooter/LowerAtGoal")
  public boolean isLowerAtGoal() {
    return true;
  }

  @AutoLogOutput(key = "Shooter/UpperAtGoal")
  public boolean isUpperAtGoal() {
    return true;
  }

  @AutoLogOutput(key = "Shooter/AtGoal")
  public boolean atGoal() {
    return isLowerAtGoal() && isUpperAtGoal();
  }

  // ----- Convenience Commands -----

  public Command spinUpCommand(ShooterGoal shootGoal) {
    return runOnce(
            () -> {
              setGoal(shootGoal);
              setFeedEnabled(false);
            })
        .withName("Shooter/SpinUp/" + shootGoal.name());
  }

  public Command launchCommand(ShooterGoal shootGoal) {
    return runOnce(
            () -> {
              setGoal(shootGoal);
              setFeedEnabled(true);
            })
        .withName("Shooter/Launch/" + shootGoal.name());
  }

  public Command stopCommand() {
    return runOnce(
            () -> {
              setGoal(ShooterGoal.STOP);
              setFeedEnabled(false);
            })
        .withName("Shooter/Stop");
  }

  public void stop() {
    goal = ShooterGoal.STOP;
    lowerFlywheelIO.stop();
    upperFlywheelIO.stop();
    lowerFeederIO.stop();
    upperFeederIO.stop();
  }
}
