# FRC8011 仿真与 3D 可视化

此副本包含已验证的机器人代码、两个 GLB 模型、AdvantageScope 布局、回归测试及详细操作说明。没有携带个人 Git 历史、本机 JDK 配置、构建缓存、旧模型或应用偏好文件。

## 新电脑准备

本流程面向 Windows，需先安装：

- WPILib 2027_alpha7（包括 WPILib VS Code 与 Java 25）。
- AdvantageScope (WPILib) 2027_alpha5，本版本已验证。
- Xbox 手柄，用于驾驶与 LT / RT 操作。

首次构建需要联网下载 Gradle 与项目依赖。这个源码包不包含 WPILib 安装包或离线依赖缓存；解压并不代表可以在未安装环境的电脑上直接运行。

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
3. AdvantageScope 导入 `ascope/layout-8011-live.json`，执行 `File → Connect to Simulator`。
4. 3D Field 中 Robot 与 Robot Component 均选择 **8011 GLB 2027**，场地为 FRC:2026 Field。
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
| `docs/` | 操作、迁移和坐标原理文档 |
| `scripts/configure-simulation.ps1` | 新电脑本地配置脚本 |

模型只在最终资源目录保存一份，不再复制根目录原始 GLB 或旧 Robot 目录。脚本和文档通过相对位置工作，不依赖作者电脑的用户名、盘符或克隆位置。

## 检查与打包

```powershell
.\gradlew.bat test --console=plain
```

此发布副本已在独立目录重新构建并运行测试；模型显示与操作此前在原开发环境中验证。构建生成的 `.gradle/`、`build/`、本地 `gradle.properties` 不属于分发内容。

未来上传 GitHub 时，应上传这个目录的源码内容。最大模型约 96 MiB：可以通过 Git 推送，但超过网页单文件上传限制；具体规则见 [GitHub 文件大小说明](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)。本次只生成本地分发包，没有执行上传。

许可见 [WPILib-License.md](WPILib-License.md)，模型与其他第三方资源的使用遵循团队已有授权。
