package frc.robot.subsystem.indexer;

import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;
import java.util.function.DoubleSupplier;

public enum IndexerGoal {
  FEED(new LoggedTunableNumber(Constants.DebugGroup.INDEXER, "Indexer/FeedVolts", -7.5)),
  OUTTAKE(new LoggedTunableNumber(Constants.DebugGroup.INDEXER, "Indexer/OuttakeVolts", 2.0)),
  JAM_REVERSE(IndexerConfig.jamReverseVolts),
  STOP(() -> 0.0);

  private final DoubleSupplier volts;

  IndexerGoal(DoubleSupplier volts) {
    this.volts = volts;
  }

  public double getVolts() {
    return volts.getAsDouble();
  }
}
