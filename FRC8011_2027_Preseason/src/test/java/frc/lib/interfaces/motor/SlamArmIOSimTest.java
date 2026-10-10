package frc.lib.interfaces.motor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SlamArmIOSimTest {
  @Test
  void deployRetractAndStopHoldTheirEndpoints() {
    var sim = new SlamArmIOSim(0.0, 1.7, 1.7, true);
    var inputs = new SlamArmIO.SlamArmIOInputs();
    sim.updateInputs(inputs);
    assertEquals(1.7, inputs.positionRad);
    sim.setCurrent(-5.0);
    sim.updateInputs(inputs);
    assertEquals(0.0, inputs.positionRad);
    sim.setCurrent(0.0);
    sim.stop();
    sim.updateInputs(inputs);
    assertEquals(0.0, inputs.positionRad);
    sim.setCurrent(10.0);
    sim.stop();
    sim.updateInputs(inputs);
    assertEquals(1.7, inputs.positionRad);
  }
}
