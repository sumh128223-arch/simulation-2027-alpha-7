package frc.robot.subsystem.indexer;

import org.wpilib.math.filter.Debouncer;
import org.wpilib.math.filter.Debouncer.DebounceType;
import org.wpilib.math.system.DCMotor;
import org.wpilib.system.Timer;
import org.wpilib.command2.SubsystemBase;
import frc.lib.dashboard.Alert;
import frc.lib.interfaces.motor.GenericRollerIO;
import frc.lib.interfaces.motor.GenericRollerIOInputsAutoLogged;
import frc.lib.interfaces.motor.GenericRollerIOKraken;
import frc.lib.interfaces.motor.GenericRollerIOSim;
import frc.robot.Constants.Ports;
import lombok.Getter;
import lombok.Setter;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

public class Indexer extends SubsystemBase {
  @Setter @Getter @AutoLogOutput private IndexerGoal goal = IndexerGoal.STOP;

  private final GenericRollerIO rollerIO;
  private final GenericRollerIOInputsAutoLogged rollerInputs =
      new GenericRollerIOInputsAutoLogged();

  private final Alert rollerOfflineAlert =
      new Alert("Indexer motor offline!", Alert.AlertType.WARNING);
  private final Alert jamAlert = new Alert("Indexer jammed! Reversing...", Alert.AlertType.WARNING);

  private Debouncer jamDebouncer =
      new Debouncer(IndexerConfig.jamDetectionTimeSecs.get(), DebounceType.RISING);
  private final Timer reverseTimer = new Timer();
  private boolean isJammed = false;

  private double reverseCount = 0.0;

  private Indexer(GenericRollerIO rollerIO) {
    this.rollerIO = rollerIO;
  }

  // ----- Factory Methods -----

  public static Indexer createReal() {
    return new Indexer(
        new GenericRollerIOKraken(
            "Indexer", Ports.Can.INDEXER, IndexerConfig.getRollerTalonConfig(), 50.0));
  }

  public static Indexer createSim() {
    return new Indexer(new GenericRollerIOSim(DCMotor.getKrakenX60Foc(1), 1.0, 0.001));
  }

  public static Indexer createIO() {
    return new Indexer(new GenericRollerIO() {});
  }

  // ----- Periodic -----

  @Override
  public void periodic() {
    rollerIO.updateInputs(rollerInputs);
    Logger.processInputs("Indexer/Roller", rollerInputs);
    rollerOfflineAlert.set(!rollerInputs.connected);

    // Update debouncer if tuning changes
    if (IndexerConfig.jamDetectionTimeSecs.hasChanged(hashCode())) {
      jamDebouncer = new Debouncer(IndexerConfig.jamDetectionTimeSecs.get(), DebounceType.RISING);
    }

    // Jam Detection Logic
    boolean currentOverThreshold =
        Math.abs(rollerInputs.statorCurrentAmp) > IndexerConfig.jamCurrentAmpThreshold.get();
    Logger.recordOutput("Indexer/CurrentOverThreshold", currentOverThreshold);
    Logger.recordOutput("Indexer/Count", reverseTimer.get());

    // Only detect jam when we are actively trying to feed (or outtake) to prevent false positives
    // when stopped
    if (!isJammed && jamDebouncer.calculate(currentOverThreshold && goal != IndexerGoal.STOP)) {
      // isJammed = true;
      reverseCount = 0;
      reverseTimer.restart();
    }

    // Jam Recovery Logic
    if (isJammed) {
      jamAlert.set(true);
      // if (reverseTimer.hasElapsed(IndexerConfig.jamReverseTimeSecs.get())) {
      if (reverseCount > 20) {
        // Recovery complete
        isJammed = false;
        reverseTimer.stop();
        jamAlert.set(false);
      } else {
        // Run reverse while recovery is not complete
        rollerIO.setVoltage(IndexerGoal.JAM_REVERSE.getVolts());
        return; // Skip normal voltage setting
      }
    } else {
      jamAlert.set(false);
      jamDebouncer.calculate(false); // Reset debouncer logic while not jammed
    }
    Logger.recordOutput("Indexer/JamDetected", isJammed);
    rollerIO.setVoltage(goal.getVolts());
  }

  public void stop() {
    rollerIO.stop();
  }
}
