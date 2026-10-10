package frc.lib.interfaces.sensor.digital;

public class DigitalInputRio implements DigitalInput {
  private final org.wpilib.hardware.discrete.DigitalInput input;

  public DigitalInputRio(int channel) {
    this.input = new org.wpilib.hardware.discrete.DigitalInput(channel);
  }

  @Override
  public boolean getAsBoolean() {
    return input.get();
  }
}
