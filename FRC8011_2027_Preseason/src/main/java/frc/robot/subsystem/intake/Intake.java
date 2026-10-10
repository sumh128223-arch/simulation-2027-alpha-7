package frc.robot.subsystem.intake;

import org.wpilib.math.filter.Debouncer;
import org.wpilib.math.filter.Debouncer.DebounceType;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.util.Units;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.command2.Command;
import org.wpilib.command2.Command.InterruptionBehavior;
import org.wpilib.command2.Commands;
import org.wpilib.command2.SubsystemBase;
import frc.lib.dashboard.Alert;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.lib.interfaces.motor.GenericRollerIO;
import frc.lib.interfaces.motor.GenericRollerIOInputsAutoLogged;
import frc.lib.interfaces.motor.GenericRollerIOKraken;
import frc.lib.interfaces.motor.GenericRollerIOSim;
import frc.lib.interfaces.motor.SlamArmIO;
import frc.lib.interfaces.motor.SlamArmIOInputsAutoLogged;
import frc.lib.interfaces.motor.SlamArmIOKraken;
import frc.lib.interfaces.motor.SlamArmIOSim;
import frc.robot.Constants.Ports;
import frc.robot.Constants;
import frc.robot.subsystem.intake.IntakeGoal.RollerGoal;
import frc.robot.subsystem.intake.IntakeGoal.SlamArmGoal;
import frc.robot.subsystem.intake.IntakeGoal.SlamState;
import lombok.Getter;
import lombok.Setter;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

public class Intake extends SubsystemBase {

  @Getter @Setter @AutoLogOutput private SlamArmGoal pivotGoal = SlamArmGoal.RETRACT;
  @Getter @Setter @AutoLogOutput private RollerGoal rollerGoal = RollerGoal.IDLE;
  @Getter @AutoLogOutput private SlamState slamState = SlamState.MOVING;

  private boolean first_time = true;

  private Debouncer slamDebouncer =
      new Debouncer(IntakeConfig.slamVelocityDebounceTime.get(), DebounceType.RISING);

  private final SlamArmIO pivotIO;
  private final GenericRollerIO rollerIO;

  private final SlamArmIOInputsAutoLogged pivotInputs = new SlamArmIOInputsAutoLogged();
  private final GenericRollerIOInputsAutoLogged rollerInputs =
      new GenericRollerIOInputsAutoLogged();

  private final Alert pivotOfflineAlert =
      new Alert("Intake pivot motor offline!", Alert.AlertType.WARNING);
  private final Alert rollerOfflineAlert =
      new Alert("Intake roller motor offline!", Alert.AlertType.WARNING);

  private Intake(SlamArmIO pivotIO, GenericRollerIO rollerIO) {
    this.pivotIO = pivotIO;
    this.rollerIO = rollerIO;
  }

  // ----- Factory Methods -----

  public static Intake createReal() {
    var io =
        new SlamArmIOKraken(
            "IntakePivot", Ports.Can.INTAKE_PIVOT, IntakeConfig.getPivotTalonConfig());
    io.setDegrees(IntakeConfig.INIT_ANGLE_DEGREE);
    return new Intake(
        io,
        // new SlamArmIO() {},
        new GenericRollerIOKraken(
            "IntakeRoller", Ports.Can.INTAKE_ROLLER, IntakeConfig.getRollerTalonConfig(), 50.0));
  }

  public static Intake createSim() {
    return new Intake(
        new SlamArmIOSim(
            Units.degreesToRadians(IntakeConfig.MIN_ANGLE_DEGREE),
            Units.degreesToRadians(IntakeConfig.MAX_ANGLE_DEGREE),
            Units.degreesToRadians(IntakeConfig.INIT_ANGLE_DEGREE),
            true),
        new GenericRollerIOSim(DCMotor.getKrakenX60Foc(1), IntakeConfig.PIVOT_REDUCTION, 0.01));
  }

  public static Intake createIO() {
    return new Intake(new SlamArmIO() {}, new GenericRollerIO() {});
  }

  // ----- Periodic -----

  @Override
  public void periodic() {
    pivotIO.updateInputs(pivotInputs);
    Logger.processInputs("Intake/Pivot", pivotInputs);
    pivotOfflineAlert.set(!pivotInputs.connected);

    rollerIO.updateInputs(rollerInputs);
    Logger.processInputs("Intake/Roller", rollerInputs);
    rollerOfflineAlert.set(!rollerInputs.connected);

    LoggedTunableNumber.ifChanged(
        hashCode(),
        () ->
            slamDebouncer =
                new Debouncer(IntakeConfig.slamVelocityDebounceTime.get(), DebounceType.RISING),
        IntakeConfig.slamVelocityDebounceTime);

    if (RobotState.isDisabled()) {
      rollerGoal = RollerGoal.IDLE;
      pivotIO.stop();
      rollerIO.stop();
      slamState = SlamState.MOVING;
      slamDebouncer.calculate(false);
      Logger.recordOutput("Intake/Pivot/Degrees", Units.radiansToDegrees(pivotInputs.positionRad));
      return;
    }

    double rollerVolts = 0.0;
    switch (rollerGoal) {
      case INTAKE -> rollerVolts = IntakeConfig.rollerIntakeVolts.get();
      case OUTTAKE -> rollerVolts = IntakeConfig.rollerOuttakeVolts.get();
      case IDLE -> rollerVolts = 0.0;
    }
    rollerIO.setVoltage(rollerVolts);

    boolean isSettled =
        Math.abs(pivotInputs.velRadPerSec) < IntakeConfig.slamSettleVelocityRadPerSec.get();

    Logger.recordOutput("Intake/Pivot/Degrees", Units.radiansToDegrees(pivotInputs.positionRad));
    switch (pivotGoal) {
      case DEPLOY -> {
        if (Constants.MODE == Constants.Mode.SIM) {
          // The endpoint sim cannot model the real arm's current reversal at impact.
          pivotIO.setCurrent(IntakeConfig.slamDeployCurrentAmp.get());
        } else if (Units.radiansToDegrees(pivotInputs.positionRad) < 70.0) {
          pivotIO.setCurrent(1.2);
          if (first_time) {
            pivotIO.setCurrent(10.0);
            first_time = false;
          }
        } else {
          if (first_time) {
            pivotIO.setCurrent(-7.0);
          } else {
            pivotIO.setCurrent(IntakeConfig.slamDeployCurrentAmp.get());
          }
        }
        slamState = slamDebouncer.calculate(isSettled) ? SlamState.DEPLOYED : SlamState.MOVING;
      }
      case RETRACT -> {
        if (Units.radiansToDegrees(pivotInputs.positionRad) > 65.0) {
          // pivotIO.setCurrent(0.0);
          pivotIO.stop();
        } else {
          pivotIO.setCurrent(IntakeConfig.slamRetractCurrentAmp.get());
        }
        slamState = slamDebouncer.calculate(isSettled) ? SlamState.RETRACTED : SlamState.MOVING;
      }
      case IDLE -> {
        pivotIO.stop();
        slamState = SlamState.MOVING;
      }
      case STOP -> {
        pivotIO.stop();
        slamState = SlamState.STOP;
      }
    }

  }

  // ----- Convenience Commands -----

  public Command injectCommand() {
    return Commands.runOnce(
            () -> {
              setRollerGoal(RollerGoal.INTAKE);
            })
        .withName("Intake/Inject");
  }

  public Command ejectCommand() {
    return Commands.runOnce(
            () -> {
              setRollerGoal(RollerGoal.OUTTAKE);
            })
        .withName("Intake/Eject");
  }

  public Command idleCommand() {
    return Commands.runOnce(
            () -> {
              setPivotGoal(SlamArmGoal.RETRACT);
              setRollerGoal(RollerGoal.IDLE);
            })
        .withName("Intake/Idle");
  }

  // ----- Pivot Only Commands -----

  /** 1. 单纯向下 (砸平) */
  public Command pivotDeployCommand() {
    return Commands.runOnce(() -> setPivotGoal(SlamArmGoal.DEPLOY)).withName("Intake/PivotDeploy");
  }

  /** 2. 单纯向上 (收起) */
  public Command pivotRetractCommand() {
    return Commands.runOnce(() -> setPivotGoal(SlamArmGoal.RETRACT))
        .withName("Intake/PivotRetract");
  }

  /** 3. 略微向上（不到底）然后再向下 (6328风格颠勺/抖动) */
  public Command pivotBumpCommand() {
    var cmd =
        Commands.repeatingSequence(
                Commands.runOnce(() -> setPivotGoal(SlamArmGoal.RETRACT)),
                Commands.waitSeconds(IntakeConfig.slamBumpTimeSecs.get()),
                Commands.runOnce(() -> setPivotGoal(SlamArmGoal.DEPLOY)),
                Commands.waitSeconds(IntakeConfig.slamBumpRestTimeSecs.get()))
            .withName("Intake/PivotBump");
    cmd.addRequirements(this);
    return cmd.withInterruptBehavior(InterruptionBehavior.CANCEL_SELF);
  }

  public void stop() {
    pivotIO.stop();
    rollerIO.stop();
  }

  public double getPivotPositionRad() {
    return pivotInputs.positionRad;
  }
}
