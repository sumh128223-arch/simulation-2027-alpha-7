package frc.robot.subsystem.indexer;

import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;
import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;

public class IndexerConfig {

  // ----- Tunable Anti-Jam Parameters -----
  protected static final LoggedTunableNumber jamCurrentAmpThreshold =
      new LoggedTunableNumber(
          Constants.DebugGroup.INDEXER, "Indexer/Jam/CurrentThresholdAmps", 10.0);
  protected static final LoggedTunableNumber jamDetectionTimeSecs =
      new LoggedTunableNumber(Constants.DebugGroup.INDEXER, "Indexer/Jam/DetectionTimeSecs", 0.1);
  protected static final LoggedTunableNumber jamReverseVolts =
      new LoggedTunableNumber(Constants.DebugGroup.INDEXER, "Indexer/Jam/ReverseVolts", 4.0);
  protected static final LoggedTunableNumber jamReverseTimeSecs =
      new LoggedTunableNumber(Constants.DebugGroup.INDEXER, "Indexer/Jam/ReverseTimeSecs", 1.0);

  // ----- TalonFX Config -----
  static TalonFXConfiguration getRollerTalonConfig() {
    var config = new TalonFXConfiguration();

    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

    config.CurrentLimits.StatorCurrentLimit = 60.0;
    config.CurrentLimits.StatorCurrentLimitEnable = true;

    return config;
  }
}
