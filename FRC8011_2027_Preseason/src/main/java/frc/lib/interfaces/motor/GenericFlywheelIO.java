package frc.lib.interfaces.motor;

import org.littletonrobotics.junction.AutoLog;

public interface GenericFlywheelIO {
  @AutoLog
  class GenericFlywheelIOInputs {
    public boolean connected;

    public double velRadPerSec;
    public double outputVoltageVolt;
    public double supplyCurrentAmp;
    public double statorCurrentAmp;
    public double tempCelsius;
  }

  default void updateInputs(GenericFlywheelIOInputs inputs) {}

  default void setPdf(double kp, double kd, double kf) {}

  default void setVoltage(double voltageVolt) {}

  default void setVel(double velRadPerSec) {}

  default void setVel(double velRadPerSec, double feedforwardVolt) {}

  default void setVel(double velRadPerSec, double accelRadPerSec2, double feedforwardVolt) {}

  default void setCurrent(double currentAmp) {}

  default void stop() {}
}
