package frc.lib.interfaces;

import org.wpilib.command2.Command;

public abstract class ComposedCommands extends Command {
  protected Command runningCommand;

  @Override
  public void initialize() {
    if (runningCommand != null) {
      runningCommand.initialize();
    }
  }

  @Override
  public void execute() {
    if (runningCommand != null) {
      runningCommand.execute();
    }
  }

  @Override
  public boolean isFinished() {
    return runningCommand != null && runningCommand.isFinished();
  }

  @Override
  public void end(boolean interrupted) {
    if (runningCommand != null) {
      runningCommand.end(interrupted);
    }
  }
}
