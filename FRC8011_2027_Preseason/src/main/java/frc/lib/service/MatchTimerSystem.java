package frc.lib.service;

import org.wpilib.networktables.NetworkTableInstance;
import org.wpilib.networktables.StringPublisher;
import org.wpilib.driverstation.MatchState;
import org.wpilib.driverstation.RobotState;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchType;
import org.wpilib.driverstation.DriverStationErrors;
import org.wpilib.driverstation.Alliance;
import frc.lib.interfaces.VirtualSubsystem;
import java.util.Optional;
import org.littletonrobotics.junction.Logger;

public class MatchTimerSystem extends VirtualSubsystem {

  private final StringPublisher globalTimeStrPub;
  private final StringPublisher hubTimeStrPub;

  private final org.wpilib.networktables.DoublePublisher globalTimeNumPub;
  private final org.wpilib.networktables.DoublePublisher hubTimeNumPub;

  public MatchTimerSystem() {
    var table = NetworkTableInstance.getDefault().getTable("SmartDashboard/Match Timer");

    // 继续保留供人阅读的 HTML 格式
    globalTimeStrPub = table.getStringTopic("Global Match Time (Str)").publish();
    hubTimeStrPub = table.getStringTopic("Hub Target Time (Str)").publish();

    // 以纯数字形式发布，供小部件 (Widget) 调用或条件判断逻辑使用
    globalTimeNumPub = table.getDoubleTopic("Global Match Time (Num)").publish();
    hubTimeNumPub = table.getDoubleTopic("Hub Target Time (Num)").publish();
  }

  @Override
  public void periodic() {
    double matchTime = MatchState.getMatchTime(); // 当前阶段剩余时间 (Auto 20s, Teleop 140s)
    if (matchTime < 0) {
      matchTime = 0;
    }

    boolean isAuto = RobotState.isAutonomousEnabled();

    // 全局时间为了方便，如果是电控阶段，则显示 matchTime；如果在自动阶段，加上 140s (假定2:20电控)
    double globalTime = matchTime;
    if (isAuto) {
      globalTime += 140.0;
    }

    // 格式化全局时间
    globalTimeStrPub.set(formatGlobalTime(globalTime));
    globalTimeNumPub.set(globalTime);

    // 解析比赛数据：哪个联盟的Hub最初是不激活的
    String gameData = MatchState.getGameData().orElse("");
    if (gameData == null || gameData.isEmpty()) {
      hubTimeStrPub.set("Waiting for FMS...");
      return;
    }

    boolean redInactiveFirst = false;
    switch (gameData.charAt(0)) {
      case 'R' -> redInactiveFirst = true;
      case 'B' -> redInactiveFirst = false;
      default -> {
        hubTimeStrPub.set("Invalid GameData");
        return;
      }
    }

    Logger.recordOutput("MatchTimer/GlobalTime", globalTime);
    Logger.recordOutput("MatchTimer/GameData", gameData);

    // 我们当前的联盟
    Optional<Alliance> currentAlliance = MatchState.getAlliance();
    if (currentAlliance.isEmpty()) {
      hubTimeStrPub.set("Waiting for Alliance...");
      return;
    }

    // Shift 1 为我们的联盟激活：如果是红队则红队不被限制，如果是蓝队则要求红队被限制
    boolean shift1Active =
        switch (currentAlliance.get()) {
          case RED -> !redInactiveFirst;
          case BLUE -> redInactiveFirst;
        };

    if (isAuto) {
      // 自动阶段通常默认某个状态或不显示
      hubTimeStrPub.set("<font color=\"gray\">Auto Phase</font>");
      return;
    }

    // 根据提供的图片：电控 2:20 (140s)
    // TRANSITION SHIFT: 140~130s
    // SHIFT 1: 130~105s
    // SHIFT 2: 105~80s
    // SHIFT 3: 80~55s
    // SHIFT 4: 55~30s
    // END GAME: 30~0s

    double timeRemainingInPeriod;
    boolean isOurHubActive = false;

    if (matchTime > 130) {
      // TRANSITION SHIFT
      if (shift1Active) {
        // 对于我们的联盟，Transition 也是 ALL，如果后续 Shift 1 也是我们的，
        // 则倒计时延长 Shift 1 的时间 (+25秒)
        timeRemainingInPeriod = (matchTime - 130) + 25.0;
      } else {
        // 如果 Shift 1 不属于我们，那么作为双方共用的区间仅仅只剩 Transition 这一段了
        timeRemainingInPeriod = matchTime - 130;
      }
    } else if (matchTime > 105) {
      // SHIFT 1
      isOurHubActive = shift1Active;
      timeRemainingInPeriod = matchTime - 105;
    } else if (matchTime > 80) {
      // SHIFT 2
      isOurHubActive = !shift1Active;
      timeRemainingInPeriod = matchTime - 80;
    } else if (matchTime > 55) {
      // SHIFT 3
      isOurHubActive = shift1Active;
      timeRemainingInPeriod = matchTime - 55;
    } else if (matchTime > 30) {
      // SHIFT 4
      isOurHubActive = !shift1Active;
      // Shift 4 结束后直接进入双方都能用的 End game(30s长度)
      timeRemainingInPeriod = (matchTime - 30) + 30.0;
    } else {
      // END GAME
      timeRemainingInPeriod = matchTime; // 直到结束
    }

    // 如果对方激活，加入红色标记 (Dashboard等支持简单HTML可将其渲染为红色)
    if (!isOurHubActive) {
      String hubFormattedTime = formatHubTime(timeRemainingInPeriod, true);
      hubTimeStrPub.set("<font color=\"red\">" + hubFormattedTime + "</font>");
      hubTimeNumPub.set(timeRemainingInPeriod);
    } else {
      String hubFormattedTime = formatHubTime(timeRemainingInPeriod, false);
      hubTimeStrPub.set("<font color=\"green\">" + hubFormattedTime + "</font>");
      hubTimeNumPub.set(timeRemainingInPeriod);
    }
  }

  private String formatGlobalTime(double timeSeconds) {
    int minutes = (int) (timeSeconds / 60);
    int seconds = (int) (timeSeconds % 60);
    return String.format("%02d:%02d", minutes, seconds);
  }

  private String formatHubTime(double timeSeconds, boolean isNegative) {
    int minutes = (int) (timeSeconds / 60);
    int seconds = (int) (timeSeconds % 60);
    if (isNegative) {
      return String.format("-%02d:%02d", minutes, seconds);
    } else {
      return String.format("%02d:%02d", minutes, seconds);
    }
  }
}
