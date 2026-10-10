package frc.lib.interfaces;

import com.ctre.phoenix6.CANBus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public record CanId(int id, CANBus bus) {
  private static final Map<String, CANBus> buses = new ConcurrentHashMap<>();

  public CanId(int id, String busName) {
    this(id, buses.computeIfAbsent(busName, CANBus::new));
  }
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof CanId canId)) {
      return false;
    }

    return id == canId.id && bus.equals(canId.bus);
  }
}
