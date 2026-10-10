package frc.lib.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.wpilib.backend.NetworkTablesTelemetryBackend;
import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.telemetry.TelemetryRegistry;

class DashboardMigrationTest {
  @Test
  void alertsPublishActivationTextUpdatesAndClearing() {
    var inst = NetworkTableInstance.getDefault();
    TelemetryRegistry.registerBackend("/SmartDashboard", new NetworkTablesTelemetryBackend(inst, ""));
    var alert = new Alert("MigrationTest", "offline", Alert.AlertType.INFO);
    try (var infos = inst.getStringArrayTopic("/SmartDashboard/MigrationTest/infos").subscribe(new String[0])) {
      alert.set(true);
      Alert.publishAll();
      assertArrayEquals(new String[] {"offline"}, infos.get());
      alert.setText("connected");
      Alert.publishAll();
      assertArrayEquals(new String[] {"connected"}, infos.get());
      alert.set(false);
      Alert.publishAll();
      assertArrayEquals(new String[0], infos.get());
    }
  }
}
