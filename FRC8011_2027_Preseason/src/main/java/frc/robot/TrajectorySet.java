package frc.robot;

import choreo.Choreo;
import choreo.trajectory.SwerveSample;
import choreo.trajectory.Trajectory;
import frc.lib.dashboard.AllianceValue;

public class TrajectorySet {
  public final AllianceValue<Trajectory<SwerveSample>> rightback2outpost =
      loadTrajectory("rightback2outpost");
  public final AllianceValue<Trajectory<SwerveSample>> outpost2depot =
      loadTrajectory("outpost2depot");
  public final AllianceValue<Trajectory<SwerveSample>> depot2leftShoot =
      loadTrajectory("depot2leftShoot");
  public final AllianceValue<Trajectory<SwerveSample>> leftShoot_close =
      loadTrajectory("leftShoot_close");
  public final AllianceValue<Trajectory<SwerveSample>> leftShoot_mid =
      loadTrajectory("leftShoot_mid");
  public final AllianceValue<Trajectory<SwerveSample>> leftShoot_far =
      loadTrajectory("leftShoot_far");

  public final AllianceValue<Trajectory<SwerveSample>> leftFront2leftShoot_far =
      loadTrajectory("leftFront2leftShoot_far");
  public final AllianceValue<Trajectory<SwerveSample>> leftShoot2Depot =
      loadTrajectory("leftShoot2Depot");

  public final AllianceValue<Trajectory<SwerveSample>> rightFront2rightShoot_far =
      loadTrajectory("rightFront2rightShoot_far");
  public final AllianceValue<Trajectory<SwerveSample>> rightShoot_close =
      loadTrajectory("rightShoot_close");
  public final AllianceValue<Trajectory<SwerveSample>> outpost2rightShoot =
      loadTrajectory("outpost2rightShoot");
  public final AllianceValue<Trajectory<SwerveSample>> rightShoot2outpost =
      loadTrajectory("rightShoot2outpost");
  public final AllianceValue<Trajectory<SwerveSample>> newPath = loadTrajectory("NewPath");

  public TrajectorySet() {}

  private AllianceValue<Trajectory<SwerveSample>> loadTrajectory(String name) {
    var trajectory = Choreo.loadTrajectory(name);
    if (trajectory.isEmpty()) {
      throw new NullPointerException("[TrajectoryLoader]: " + name + ".traj not found");
    }

    @SuppressWarnings("unchecked")
    var blue = (Trajectory<SwerveSample>) trajectory.get();
    System.out.println("[TrajectoryLoader]: " + name + ".traj loaded");

    return new AllianceValue<>(blue, blue.flipped());
  }

  public static Trajectory<SwerveSample> mirrorTrajectoryByLeftRight(
      Trajectory<SwerveSample> original) {
    // TODO:
    return original.flipped();
  }
}
