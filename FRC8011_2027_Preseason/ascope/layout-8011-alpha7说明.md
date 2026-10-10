# WPILib alpha7 配套布局

导入 `layout-8011-alpha7.json`，自定义资源目录仍选本项目的 `ascope/assets`。

本文件依据本机 WPILib `2027_alpha7` 安装中 AdvantageScope 的实际控制器格式生成。该软件内部版本是 `27.0.0-alpha-6`，因此布局版本也使用这个值，没有虚写成 alpha7。旧布局保持原样。当前未通过软件菜单 Export Layout 导出，也未完成新电脑实时显示验证。

迁移修复了旧格式：Component 紧跟 Robot，成为它的子项；使用父 Robot 的 `8011 GLB 2027` 模型，不再单独选择机构模型。轮组显示类型更新为 `swerveModuleVelocities`，并同样放在 Robot 子项区域。保留场地、地面球、车内球、飞行球与轨迹绑定。

预先配置三个页面：3D Field、LT/RT 与机构角度折线图、Controls / Intake 数值表。无需手动拖入字段。

本布局使用普通 NetworkTables 4 数据源对应的 `NT:/AdvantageKit/...` 字段。请在 Preferences 中选择普通 NetworkTables 4，然后 Connect to Simulator。如果选择专门的 AdvantageKit 数据源，字段名会被软件去除 `/AdvantageKit` 前缀，不能直接沿用这些绑定。

如果新电脑 About 显示的实际版本不同，仍可能出现版本提示。可尝试 Continue，但不要通过手改 version 隐藏警告。布局只保存数据绑定，不能恢复没有发布的实时数据或修复手柄代码。
