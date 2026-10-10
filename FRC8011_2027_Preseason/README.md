# FRC8011 仿真与 3D 可视化

本项目将机器人代码迁移到 WPILib / GradleRIO **2027.0.0-alpha-7** 与 **Java 25**，包含机器人源码、两个 GLB 模型、AdvantageScope 布局、回归测试及手动操作说明。

## Alpha7 项目改进（2026-10-10）

| 范围 | 改动 |
|---|---|
| API 与构建 | 迁移 Telemetry、告警和自动命令选择协议；固定 Java 25、GradleRIO alpha7、Lombok 1.18.42，补齐桌面/视觉依赖 |
| 底盘、定位与场地 | 修正优化/限速返回值使用、日志时间单位及里程计线程；迁移十份 AprilTag 场地 JSON |
| 手柄 | 使用 `CommandNiDsXboxController`，驾驶端口 0，绑定摇杆、LT 吸球及 RT 射球 |
| 模型 | 接入主体及 intake GLB，修正根坐标、朝向及铰链，移除旧 hopper |
| 机构仿真 | SIM 模式松开 LT 收起，修复端点振荡，停止时保持角度；REAL 分支保留原控制逻辑 |
| 禁用与诊断 | Disabled 停止机构输出，拾球/新增射球检查使能；每周期发布输入、角度和目标状态 |
| 新版布局 | Component 调整为 Robot 子项，更新轮组类型，预置 LT/RT/角度折线图与状态表 |

这些是项目适配和修复，不是 WPILib 官方软件修改。代码明细见 [迁移记录](docs/migration-2027-alpha-7.md)，模型及机构改动见 [完整操作文档](docs/simulation-2027-alpha-7.md)。

**版本与验证：**本机 WPILib alpha7 配套 AdvantageScope 内部版本实际为 `27.0.0-alpha-6`，旧布局记录 `27.0.0-alpha-4`。新布局按实际控制器格式生成，尚未通过软件菜单导出或完成新电脑实时显示验证。2026-10-10 离线编译、JAR 打包、原生依赖提取和仿真启动文件生成成功，7 项测试通过。此前模型操作在旧版可视化环境验证；新电脑已能驾驶，吸球/射球及实时数据问题仍在排查，不能宣称新布局已解决。真实硬件尚未验证，intake 仍是端点模型。

## 新电脑准备

本流程面向 Windows，需先安装：

- WPILib 2027_alpha7（包括 WPILib VS Code 与 Java 25）。
- WPILib alpha7 配套 AdvantageScope；通过 About 核对内部版本，本机配套软件为 `27.0.0-alpha-6`。
- Xbox 手柄，用于驾驶与 LT / RT 操作。

本地 U 盘分发另有 `offline-kit`，包含安装镜像、编辑器与 AdvantageScope、Gradle 和依赖缓存。GitHub 源码不自动包含这些软件；只下载源码时需自行准备环境和依赖。手动离线配置见完整操作文档第 2.1 节。

## 一次配置

解压后进入包含本 README 与 build.gradle 的目录，在 PowerShell 执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\configure-simulation.ps1
```

脚本检测 WPILib 默认安装位置中的 Java 25，并在本地生成已忽略的 `gradle.properties`。如果安装在其他位置：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\configure-simulation.ps1 -WpilibHome "<自己的WPILib目录>/2027_alpha7"
```

可以先使用 `-CheckOnly` 检查环境，不写文件。尖括号内容是占位符，必须替换成实际路径。

AdvantageScope 中选择自定义资源父目录 **`ascope/assets`**。如果菜单无法设置，先运行一次 AdvantageScope 后完全退出，再执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\configure-simulation.ps1 -ConfigureAdvantageScope
```

这项显式选项会备份该电脑的应用偏好文件，并只更新资源路径。不要在程序运行时修改偏好文件。

## 启动

1. 用 2027_alpha7 WPILib VS Code 打开当前目录。
2. 运行 `WPILib: Build Robot Code`，再运行 `WPILib: Simulate Robot Code`，选择 GUI 和 Driver Station。
3. AdvantageScope 使用普通 NetworkTables 4 数据源，导入 `ascope/layout-8011-alpha7.json`，执行 `File → Connect to Simulator`。
4. Robot 选择 **8011 GLB 2027**，Component 紧跟 Robot，绑定 `Visualization/Components`，使用父 Robot 的模型；场地为 FRC:2026 Field。
5. 仿真 GUI 将手柄分配到端口 0，启用 Teleop；按 LT 展开吸球，松开收起，RT 射球。

完整步骤、模型转轴校准和代码改动原因见 [仿真操作说明](docs/simulation-2027-alpha-7.md)。

## 目录

| 目录 / 文件 | 用途 |
|---|---|
| `src/` | 机器人代码、部署场地/轨迹资源和测试 |
| `vendordeps/`、`gradle/` | 固定版本依赖说明与 Gradle Wrapper |
| `.wpilib/`、`.vscode/` | 可移植项目设置 |
| `ascope/assets/Robot_8011/` | 两个新模型与校准配置 |
| `ascope/layout-8011-live.json` | 可视化布局与 LT/角度诊断图 |
| `ascope/layout-8011-alpha7.json` | 配套软件格式的新布局，修正机构子项，预置诊断图和状态表 |
| `ascope/layout-8011-alpha7说明.md` | 新布局的版本、数据源和验证说明 |
| `docs/` | 操作、迁移和坐标原理文档 |
| `scripts/configure-simulation.ps1` | 新电脑本地配置脚本 |

模型只在最终资源目录保存一份，不再复制根目录原始 GLB 或旧 Robot 目录。脚本和文档通过相对位置工作，不依赖作者电脑的用户名、盘符或克隆位置。

## 检查与打包

```powershell
.\gradlew.bat test --console=plain
```

此发布副本已在独立目录重新构建并运行测试；模型显示与操作此前在原开发环境中验证。构建生成的 `.gradle/`、`build/`、本地 `gradle.properties` 不属于分发内容。

替换 GitHub 仓库时，同步源码、依赖声明、模型、布局与文档；仅替换 README 不会更新功能。不要上传本机 `gradle.properties`、`.gradle/`、`build/` 或离线安装镜像/缓存。模型文件较大，上传前检查仓库的大文件管理方式。本次只更新本地文件，没有执行上传。

许可见 [WPILib-License.md](WPILib-License.md)，模型与其他第三方资源的使用遵循团队已有授权。

## 本次上传目录

本目录根部直接包含 `build.gradle`，可整体作为 GitHub 仓库根目录。改进总览见 [总说明](simulation-2027-alpha-7.md)。已排除安装包、离线缓存、构建输出、本机 Java 配置和运行时状态文件。旧 `layout-8011-live.json` 只供历史对照及迁移脚本输入；当前优先导入 `layout-8011-alpha7.json`。

`scripts/migrate-ascope-layout.py` 是本次布局生成工具，依赖生成电脑的本地 AdvantageScope 安装；使用已经生成的布局不需要运行它，也不要求安装 Python。
