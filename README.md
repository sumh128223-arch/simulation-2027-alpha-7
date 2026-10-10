# FRC8011 WPILib 2027 Alpha7 仿真

机器人项目位于 **`FRC8011_2027_Preseason`**。用 WPILib VS Code 打开该目录（包含 `build.gradle`），不要把仓库外层作为 Gradle 项目。

- [项目环境、配置和验证](FRC8011_2027_Preseason/README.md)
- [Alpha7 改进总览](FRC8011_2027_Preseason/simulation-2027-alpha-7.md)
- [完整手动仿真与排错说明](FRC8011_2027_Preseason/docs/simulation-2027-alpha-7.md)
- [配套软件格式的新布局](FRC8011_2027_Preseason/ascope/layout-8011-alpha7.json)
- [布局版本与验证说明](FRC8011_2027_Preseason/ascope/layout-8011-alpha7说明.md)

源码、依赖声明、GLB 模型和布局已整合；不包含软件安装包、Gradle 缓存、本机 Java 配置或构建输出。模型沿用仓库的 Git LFS 配置，克隆时需取得完整 LFS 文件。

本机 WPILib alpha7 自带 AdvantageScope 的内部版本为 **27.0.0-alpha-6**。新布局按该软件控制器格式生成，尚未完成软件菜单导出及目标电脑实时验证。已有离线编译和 7 项回归测试通过；目标电脑能驾驶，吸球/射球及实时诊断问题仍在排查。
