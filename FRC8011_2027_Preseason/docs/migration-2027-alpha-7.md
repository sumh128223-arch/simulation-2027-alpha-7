# Rebuilt migration to WPILib 2027 Alpha 7

Target: GradleRIO/WPILib `2027.0.0-alpha-7`, Java 25. Existing Phoenix,
PhotonVision, Choreo, and AdvantageKit alpha versions remain pinned in vendordeps.

## References checked

- [WPILib 2027 changelog](https://docs.wpilib.org/en/latest/docs/yearly-overview/yearly-changelog.html)
- [Alpha 7 release](https://github.com/wpilibsuite/allwpilib/releases/tag/v2027.0.0-alpha-7)
- [Telemetry migration guide at Alpha 7](https://github.com/wpilibsuite/allwpilib/blob/v2027.0.0-alpha-7/telemetry/doc/telemetry.md)
- [Tunables migration guide at Alpha 7](https://github.com/wpilibsuite/allwpilib/blob/v2027.0.0-alpha-7/tunables/doc/tunables.md)
- [Lombok Java 25 support](https://projectlombok.org/changelog)

Replacement signatures and behavior were verified against the installed Alpha 7
WPILib and vendor source JARs.

## Repairs

- Pin Lombok 1.18.42 so annotation processing works with Java 25; add AprilTag
  and CameraServer vendordeps needed for vision and desktop simulation.
- Replace removed SmartDashboard/Sendable APIs with Telemetry, configure its
  NetworkTables backend, and publish current alerts every cycle.
- Replace AdvantageKit LoggedDashboardChooser with LoggedNetworkChooser and
  preserve the `/SmartDashboard/Auto/Command` path. This chooser now uses the
  Selectable protocol (`selected/tune` input, `selected/value` output).
- Share Phoenix CANBus objects by configured bus name. The existing empty bus
  name selects the platform default; on Systemcore Phoenix documents this as
  `can_s2`. Confirm wiring against the configured bus names before deployment.
- Migrate simulation models/accessors, pose/twist transforms, chassis reference
  frame conversion, controller bindings, constants, and command scheduling.
- Keep returned swerve optimization/desaturation values; convert raw AdvantageKit
  nanosecond timestamps to seconds for odometry and vision buffers.
- Convert all ten deployed AprilTag JSON files to the unified Fields schema,
  retaining tag poses and each field variant's dimensions. Avoid circular static
  initialization when the layout enum is accessed before its containing class.
- Fix odometry worker startup ordering; prevent the simulation worker from
  continuously spinning while waiting for the next sample.
- Use the Systemcore log directory and Java 25 in GitHub Actions.

## Reproduce the build on this Windows installation

```powershell
$env:JAVA_HOME = 'C:\Users\Public\wpilib\2027_alpha7\jdk'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat clean build --console=plain
```

The shell's default Java installation was Java 17, so set JAVA_HOME or run from
WPILib VS Code. No machine-specific JDK path is committed into Gradle configuration.

## Validation

Five regression tests cover all ten field layouts and a known tag pose, alert
activation/text updates/clearing over NetworkTables, reverse-drive optimization
and wheel velocity limiting with and without feedforward, odometry sampling and
timestamp units, and desktop robot startup plus disabled/autonomous/teleop/utility
lifecycle callbacks. The simulation test also loads all configured Choreo paths.

A successful desktop build and simulation test do not validate physical hardware,
CAN wiring, real Driver Station input, camera measurements, or robot tuning.
No robot deployment is performed by this migration.
