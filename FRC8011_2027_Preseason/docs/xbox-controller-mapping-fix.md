# Xbox 手柄映射修复说明（WPILib 2027 Alpha 7）

> 本文说明 **"为什么 LT/RT 在仿真里错乱"** 的根因、原理，以及**正确改法**。
> 结论一句话：**不要用 `getHID().getRawAxis()` 手动换轴号，而是把 driver 从 `CommandXboxController`
> 换成 `CommandNiDsXboxController`，然后直接调用类自带的 trigger 方法。**

---

## 1. 根因：2027 有两个 Xbox 类，轴序号完全不同

WPILib 2027 Alpha 7 里有两个 Xbox 手柄类，各自内置了一套**轴序号枚举**，直接抄自它们的源码：

### 1.1 `XboxController`（SDL 顺序）— `org.wpilib.driverstation.XboxController`

```java
public enum Axis {
  LEFT_X(0), LEFT_Y(1), RIGHT_X(2), RIGHT_Y(3), LEFT_TRIGGER(4), RIGHT_TRIGGER(5)
}
```

扳机被放到**最后**（LT=4、RT=5），右摇杆在前（RX=2、RY=3）。这是 SDL 游戏手柄 API 的顺序。

### 1.2 `NiDsXboxController`（经典 / NI-DS 顺序）— `org.wpilib.driverstation.NiDsXboxController`

```java
public enum Axis {
  LEFT_X(0), RIGHT_X(4), LEFT_Y(1), RIGHT_Y(5), LEFT_TRIGGER(2), RIGHT_TRIGGER(3)
}
```

扳机在**中间**（LT=2、RT=3），右摇杆在后（RX=4、RY=5）。这是老 NI Driver Station 上报的经典顺序。

> **关键**：`XboxController` 类源码注释里明确写了——`NiDsXboxController` 才是 "through the official NI DS"
> 的顺序；而 **2027 Alpha 7 的仿真（Sim）上报的正是这套经典顺序**。所以仿真里用 `XboxController`（SDL）去读，就整体错位。

---

## 2. 症状回顾

| 物理操作 | 仿真上报轴号（经典） | `XboxController` 读到的方法 | 现象 |
|---|---|---|---|
| 左摇杆 X / Y | 0 / 1 | `getLeftX()` / `getLeftY()` | ✅ 正常 |
| **左扳机 LT** | 2 | `getRightX()`（SDL 里 RIGHT_X=2） | ❌ **转圈** |
| **右扳机 RT** | 3 | `getRightY()`（SDL 里 RIGHT_Y=3） | ❌ **无反应** |
| 右摇杆 X | 4 | `getLeftTrigger()`（SDL 里 LT=4） | ⚠️ 误触发吸球 |
| 右摇杆 Y | 5 | `getRightTrigger()`（SDL 里 RT=5） | ⚠️ 误触发射球 |

---

## 3. 原理：为什么"直接用类自带的 trigger 方法"才是正解

两个类的 getter **方法名一样，但底层读的轴号不一样**，因为它们的 `Axis` 枚举值不同：

```java
// XboxController（SDL）：RIGHT_X 枚举 = 2，所以 getRightX() 读轴 2
public double getRightX() { return MathUtil.applyDeadband(getAxis(Axis.RIGHT_X), m_rightXDeadband); }

// NiDsXboxController（经典）：RIGHT_X 枚举 = 4，所以 getRightX() 读轴 4
public double getRightX() { return m_hid.getRawAxis(Axis.RIGHT_X.value); }
```

所以：

- **对的做法**：调 `driver.getRightX()` / `driver.leftTrigger()` / `driver.rightTrigger()` —— 让类内部的 `Axis` 枚举替你决定读哪根轴。**前提是选对类**。
- **错的做法**：`driver.getHID().getRawAxis(2/3/4)` —— 这是**绕过枚举、手填裸轴号**。等于把 `NiDsXboxController` 里已经写好的枚举映射，用魔数又手抄了一遍，既难看又不通用。

> 换句话说：**"直接 getTrigger、不走 getHID" 这个方向是对的**。但注意——必须是在**正确的类**上调用：
> `CommandXboxController` 的 `getLeftTrigger()` 底层还是 SDL 轴 4，仿真里轴 4 其实是右摇杆 X，
> 所以**光换方法名不换类，照样错**。

---

## 4. 之前的错误改法（已废弃）

一开始我为了"修"这个问题，手动读原始轴：

```java
// ❌ 错误：手填魔数，绕过枚举
() -> -driver.getHID().getRawAxis(4) * (...)   // 旋转
new Trigger(() -> driver.getHID().getRawAxis(2) > 0.5)  // LT 吸球
new Trigger(() -> driver.getHID().getRawAxis(3) > 0.5)  // RT 射球
```

错在三点：

1. **用魔数重抄了 `NiDsXboxController` 的枚举**，而不是直接用框架提供好的类。
2. **只修了轴，没修按键**——两个类的按键编号也不一样（见第 7 节），LB/RB/view/dpad 都会错位。
3. **换了轴号但没换类**，属于打补丁而不是用正解。

---

## 5. 正确改法（已应用到 `RobotContainer.java`）

### 5.1 换类

```java
// 改前
import org.wpilib.command2.button.CommandXboxController;
CommandXboxController driver = new CommandXboxController(Ports.Joystick.DRIVER);

// 改后
import org.wpilib.command2.button.CommandNiDsXboxController;
CommandNiDsXboxController driver = new CommandNiDsXboxController(Ports.Joystick.DRIVER);
```

### 5.2 旋转（右摇杆 X）

```java
// 改前（魔数）
() -> -driver.getHID().getRawAxis(4) * (driver.getController().getLeftBumperButton() ? 0.5 : 0.9)

// 改后（类自带 getter，经典轴 4）
() -> -driver.getRightX() * (driver.getNiDsXboxController().getLeftBumperButton() ? 0.5 : 0.9)
```

### 5.3 吸球（LT）

```java
// 改前（魔数）
new Trigger(() -> driver.getHID().getRawAxis(2) > 0.5).whileTrue(...)

// 改后（直接 trigger 方法，经典轴 2，自带 0.5 阈值）
driver.leftTrigger().whileTrue(MyCommand.intakeMacroCommand(intake));
```

### 5.4 射球（RT）

```java
// 改前（魔数）
new Trigger(() -> driver.getHID().getRawAxis(3) > 0.5).whileTrue(...)

// 改后（直接 trigger 方法，经典轴 3，自带 0.5 阈值）
driver.rightTrigger().whileTrue(MyCommand.shootMacroCommand(...));
```

### 5.5 按键方法也要跟着改（否则按键还是错位）

因为 `NiDsXboxController` 的按键枚举和 `XboxController` 不一样：

| 原代码（`CommandXboxController`） | 改后（`CommandNiDsXboxController`） | 说明 |
|---|---|---|
| `driver.getController().getLeftBumperButton()` | `driver.getNiDsXboxController().getLeftBumperButton()` | 底层对象方法名不同 |
| `driver.view()` | `driver.back()` | NiDs 叫 BACK（编号 6） |
| `driver.dpadUp()/Down()/Left()/Right()` | `driver.getHID().povUp()/povDown()/povLeft()/povRight()` | NiDs 没有 dpad 方法，走 POV 帽 |

> 十字键那四个是唯一还需要 `getHID()` 的地方——因为 `CommandNiDsXboxController` **没有** `dpad*()`
> 方法，只能用底层 `CommandGenericHID` 的 `pov*()`（POV 帽 = 十字键，同一回事）。这和扳机不一样。

### 5.6 方法签名

`joystickRumblerCommand`、`shootMacroCommand` 两个方法里参数类型 `CommandXboxController` → `CommandNiDsXboxController`。

---

## 6. 完整对照（改前 → 改后）

```java
// ===== 旋转 =====
// 前：() -> -driver.getHID().getRawAxis(4) * (driver.getController().getLeftBumperButton() ? 0.5 : 0.9)
// 后：() -> -driver.getRightX() * (driver.getNiDsXboxController().getLeftBumperButton() ? 0.5 : 0.9)

// ===== LT 吸球 =====
// 前：new Trigger(() -> driver.getHID().getRawAxis(2) > 0.5).whileTrue(...)
// 后：driver.leftTrigger().whileTrue(MyCommand.intakeMacroCommand(intake));

// ===== RT 射球 =====
// 前：new Trigger(() -> driver.getHID().getRawAxis(3) > 0.5).whileTrue(...)
// 后：driver.rightTrigger().whileTrue(MyCommand.shootMacroCommand(...));
```

---

## 7. 两份源码的轴 / 按键编号对照

### 轴（Axis）

| 轴 | `XboxController`（SDL） | `NiDsXboxController`（经典） |
|---|---|---|
| 左摇杆 X | `LEFT_X(0)` | `LEFT_X(0)` |
| 左摇杆 Y | `LEFT_Y(1)` | `LEFT_Y(1)` |
| 左扳机 LT | `LEFT_TRIGGER(4)` | `LEFT_TRIGGER(2)` |
| 右扳机 RT | `RIGHT_TRIGGER(5)` | `RIGHT_TRIGGER(3)` |
| 右摇杆 X | `RIGHT_X(2)` | `RIGHT_X(4)` |
| 右摇杆 Y | `RIGHT_Y(3)` | `RIGHT_Y(5)` |

### 按键（Button）

| 按键 | `XboxController`（SDL） | `NiDsXboxController`（经典） |
|---|---|---|
| A / B / X / Y | 0 / 1 / 2 / 3 | 0 / 1 / 2 / 3 |
| 左肩键 LB | `LEFT_BUMPER(9)` | `LEFT_BUMPER(4)` |
| 右肩键 RB | `RIGHT_BUMPER(10)` | `RIGHT_BUMPER(5)` |
| view / back | `VIEW(4)` | `BACK(6)` |
| menu / start | `MENU(6)` | `START(7)` |
| 左/右摇杆按下 | 7 / 8 | 8 / 9 |
| 十字键 | `DPAD_UP..RIGHT(11..14)` | （无，走 POV 帽） |

---

## 8. 重新去试（仿真步骤）

1. WPILib VS Code → `Ctrl+Shift+P` → **WPILib: Simulate Robot Code**（弹 Glass GUI + Driver Station）。
2. Advantage Scope → `Connect to Simulator`。
3. Glass 的 Driver Station 里把 Xbox 手柄指派到 `Joystick 0`，点 **Enable** 进 Teleop。
4. 验证：
   - **左摇杆** → 平移；**右摇杆 X** → 原地旋转。
   - **LT** → 吸球宏 `Macro/FullIntake` 触发。
   - **RT** → 射球宏 `Macro/AutoShoot` 触发。
   - **LB** → 慢速模式；**back** → 重置位姿；**十字键** → 固定点射球。

> 可同时开一个 NT4 客户端连 `localhost:5810`，观察 `/AdvantageKit/RealOutputs/CommandsAll/Macro/FullIntake`
> 和 `Macro/AutoShoot` 是否在按 LT/RT 时变 `true`。

---

## 9. 注意事项

1. **`NiDsXboxController` 没有死区**：它的 `getRightX()` 是裸 `getRawAxis(4)`，不带死区；
   而 `XboxController.getRightX()` 有 0.1 死区。若仿真里摇杆轻微漂移导致自转，需在旋转 lambda 里手动加死区。
2. **十字键走 POV 帽**：`CommandNiDsXboxController` 没有 `dpad*()`，只能用 `getHID().povUp()/...`。
3. **真车**：真车走 NI Driver Station，也是经典顺序，所以这个改法**真车和仿真一致**，不需要像之前那样"真车前还原"。

---

## 10. 相关文件

- 改动文件：`src/main/java/frc/robot/RobotContainer.java`
- 完整仿真流程：`docs/simulation-2027-alpha-7.md`
- WPILib 源码（供对照）：
  - `NiDsXboxController.java` → `org/wpilib/driverstation/NiDsXboxController.java`
  - `XboxController.java` → `org/wpilib/driverstation/XboxController.java`
  - `CommandNiDsXboxController.java` → `org/wpilib/command2/button/CommandNiDsXboxController.java`
