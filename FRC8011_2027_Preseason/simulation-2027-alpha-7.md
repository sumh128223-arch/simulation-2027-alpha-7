# FRC8011：WPILib 2027 Alpha7 仿真与可视化

更新日期：2026-10-10。以下为本项目适配 Alpha7 的改进，不代表修改了 WPILib 官方软件。

## Alpha7 改进总览

| 范围 | 改进及用途 |
|---|---|
| 构建与 API | 固定 GradleRIO `2027.0.0-alpha-7`、Java 25、Lombok 1.18.42；迁移 Telemetry、告警与自动命令选择接口，补齐视觉及桌面依赖 |
| 底盘与定位 | 保留优化/限速返回值，修正日志时间单位、里程计线程启动顺序与等待逻辑 |
| 场地 | 将十份 AprilTag 场地 JSON 迁移到新 Fields 格式，保留标签位姿和场地尺寸 |
| 手柄 | 使用 `CommandNiDsXboxController`，驾驶端口为 **0**；摇杆驾驶、LT 吸球、RT 射球通过类方法绑定 |
| 3D 模型 | 接入主体与 intake 两个 GLB，修正主体朝向、根坐标与铰链位置，移除旧 hopper 组件 |
| 吸球仿真 | SIM 模式松开 LT 收起；展开时保持展开电流；零电流及停止保持角度，修复 0°/99°端点反复切换 |
| 禁用状态 | Disabled 时停止机构输出，拾球和新增射球动作检查 Enabled |
| 实时诊断 | 每周期发布 Enabled、LT、RT、机构角度、pivot 目标和 roller 目标，区分输入、命令与显示问题 |
| 新版布局 | Component 紧跟 Robot 作为子项，轮组类型更新为 `swerveModuleVelocities`；预置 3D 场地、LT/RT/角度折线图和六项数值表 |
| 新电脑迁移 | 提供本机 Java 配置脚本、手动离线缓存配置说明及独立 U 盘软件分发包 |

## 版本与验证情况

项目依赖是 **WPILib / GradleRIO alpha7、Java 25**。但 WPILib 安装目录名不等于 AdvantageScope 的内部版本：本机 `2027_alpha7` 自带 AdvantageScope **27.0.0-alpha-6**，旧布局记录 **27.0.0-alpha-4**。

新布局 `layout-8011-alpha7.json` 按本机配套软件的实际控制器格式生成，修正机构子项结构，没有虚写 alpha7 版本号；尚未通过软件菜单 Export Layout 导出，也未完成新电脑的实时机构显示及吸球/射球验证。

2026-10-10 使用独立 Gradle 缓存及本机安装的 WPILib Maven 仓库，以 `--offline` 完成源码编译、JAR 打包、Release 原生依赖提取及仿真启动文件生成，**7 项测试全部通过**。此前模型显示与操作在旧版可视化环境验证；新电脑已能驾驶，吸球/射球和实时诊断数据问题仍在排查，不能宣称已由新布局解决。真实硬件尚未验证；intake 使用简化端点模型，不代表真实机械动力学。

## 当前手动启动入口

1. 用 WPILib alpha7 VS Code 打开本目录（包含 `build.gradle`）。首次按完整文档第 2.1 节配置 Java 25、本机 `gradle.properties` 和离线缓存。
2. Build Robot Code，再 Simulate Robot Code，选择 GUI 和 Driver Station。
3. 自定义资源目录选 **`ascope/assets`**。
4. 导入 **`ascope/layout-8011-alpha7.json`**；Preferences 使用普通 **NetworkTables 4** 数据源，然后 Connect to Simulator。本布局绑定 `NT:/AdvantageKit/...`，专用 AdvantageKit 数据源的字段前缀不同。
5. Robot 选 **8011 GLB 2027**；Component 紧跟 Robot，绑定 `Visualization/Components`，使用父 Robot 的模型。场地选 **FRC:2026 Field**。
6. 把实际 Xbox 手柄放在 DS 的 **0 号位置**，选择 Teleop 并 Enabled。LT 展开吸球、松开收起，RT 射球。拾球要求机构靠近拾取侧的球，射球还受命令条件控制。

layout 负责界面和数据绑定，`Robot_8011` 负责模型与机构配置，Java 代码负责控制和发布数据。导入布局不会启动仿真，也不会补上缺失的实时数据。

## GitHub 替换说明

同步源码、依赖声明、模型资源、布局和文档；仅替换说明文件不会更新实际功能。本地 `offline-kit` 是独立 U 盘软件包，不是 GitHub 源码的必备内容。不要上传本机 `gradle.properties`、`.gradle/`、`build/` 或软件安装镜像/缓存。只下载源码并不意味着能在未安装环境的电脑上离线运行。

- [项目 README](README.md)
- [完整手动操作与排错文档](docs/simulation-2027-alpha-7.md)
- [Alpha7 代码迁移记录](docs/migration-2027-alpha-7.md)
- [新版布局说明](ascope/layout-8011-alpha7说明.md)
