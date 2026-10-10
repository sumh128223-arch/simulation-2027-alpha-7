package frc.robot.subsystem.shooter;

import frc.lib.dashboard.LoggedTunableNumber;
import frc.robot.Constants;
import java.util.function.DoubleSupplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ShooterGoal {
  STOP(() -> 0.0, () -> 0.0),

  MANUAL_LAUNCH(
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER,
          "Shooter/Launch/LowerFlywheel/TargetVelDegPerSec",
          -17000.0),
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER,
          "Shooter/Launch/UpperFlywheel/TargetVelDegPerSec",
          -10000.0)),

  TOWER_LAUNCH(
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER, "Shooter/Tower/LowerFlywheel/TargetVelDegPerSec", -17000.0),
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER, "Shooter/Tower/UpperFlywheel/TargetVelDegPerSec", -9600.0)),

  TRENCH_LAUNCH(
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER,
          "Shooter/Trench/LowerFlywheel/TargetVelDegPerSec",
          -17000.0),
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER,
          "Shooter/Trench/UpperFlywheel/TargetVelDegPerSec",
          -8800.0)),

  OUTTAKE(
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER, "Shooter/Outtake/LowerFlywheel/TargetVelDegPerSec", 400.0),
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER, "Shooter/Outtake/UpperFlywheel/TargetVelDegPerSec", 400.0)),

  FEEDONLY(() -> -12000.0, () -> -6000.0),

  DELIEVER(
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER,
          "Shooter/Deliver/LowerFlywheel/TargetVelDegPerSec",
          -16000.0),
      new LoggedTunableNumber(
          Constants.DebugGroup.SHOOTER,
          "Shooter/Deliver/UpperFlywheel/TargetVelDegPerSec",
          -16000.0));

  // AUTO and DELIVER now use simple LoggedTunableNumber suppliers (no distance curve)

  private final DoubleSupplier lowerFlywheelDegPerSecFromDistance;
  private final DoubleSupplier upperFlywheelDegPerSecFromDistance;

  public double getLowerFlywheelRadPerSec() {
    return Math.toRadians(lowerFlywheelDegPerSecFromDistance.getAsDouble());
  }

  public double getUpperFlywheelRadPerSec() {
    return Math.toRadians(upperFlywheelDegPerSecFromDistance.getAsDouble());
  }
}
