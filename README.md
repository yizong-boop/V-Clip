# V-Clip

<p align="center">
  A lightweight macOS clipboard manager built with Kotlin Multiplatform and Compose Desktop.
</p>

<p align="center">
  <a href="https://kotlinlang.org/docs/multiplatform.html">Kotlin Multiplatform</a>
  ·
  <a href="https://www.jetbrains.com/compose-multiplatform/">Compose Multiplatform</a>
  ·
  <a href="#english">English</a>
  ·
  <a href="#中文">中文</a>
</p>

<details open>
<summary><b id="english">English</b></summary>

## Overview

V-Clip is a spotlight-style clipboard manager for macOS. It listens to clipboard changes, keeps a searchable history, and lets you quickly navigate entries with the keyboard in a compact floating window.

## Highlights

- Spotlight-like floating window designed for fast keyboard-first access.
- Clipboard history with automatic ordering by most recent copy time.
- Instant text filtering while typing in the search field.
- Keyboard navigation with `↑` and `↓`, plus minimal-scroll behavior for a smoother list experience.
- One-click paste flow: select an item and V-Clip rewrites the clipboard, returns focus, and sends `Cmd + V`.
- Default global shortcut: `Option + V`.
- Built with Kotlin Multiplatform and Compose Desktop.

## Current Status

This project is currently focused on the macOS desktop target. The core architecture already separates shared state and platform-specific integrations, which keeps future expansion easier.

## Keyboard Shortcuts

| Shortcut | Action |
| --- | --- |
| `Option + V` | Open the V-Clip window |
| `↑` / `↓` | Move selection |
| `Enter` | Confirm the selected item |
| Mouse click | Confirm the clicked item and paste it |
| `Esc` | Close the window |

## Tech Stack

- Kotlin Multiplatform
- Compose Multiplatform for Desktop
- Kotlin Coroutines and StateFlow
- Native macOS integrations for clipboard monitoring, app activation, and global hotkeys

## Project Structure

```text
composeApp/
  src/
    commonMain/   Shared domain and state logic
    commonTest/   Shared tests
    jvmMain/      Desktop and macOS-specific implementation
    jvmTest/      Desktop test code
```

## Run Locally

### Requirements

- macOS
- JDK 17 or newer

### Start the app

```bash
./gradlew :composeApp:run
```

### macOS permission required for auto-paste

V-Clip can automatically paste the selected item back into the previously focused app. For that part to work, macOS must allow the app or IDE process to control your computer through Accessibility.

Grant permission in:

`System Settings -> Privacy & Security -> Accessibility`

If clipboard history works but auto-paste does not, this is the first setting to check.

### Compile only

```bash
./gradlew :composeApp:compileKotlinJvm
```

## Development Notes

- The app window is designed to behave like a quick launcher rather than a traditional persistent main window.
- Shared business logic lives in `commonMain`, while macOS integration code lives in `jvmMain`.
- The selection list has custom minimal-scroll behavior so keyboard navigation feels more precise in the floating UI.

## Roadmap Ideas

- Support pinning or favoriting important entries.
- Add persistence across app restarts via `ClipboardStore`.
- Add packaging and distribution for easier installation.
- Evaluate JNA/CoreGraphics-based paste to replace `osascript` subprocess.

</details>

<details>
<summary><b id="中文">中文</b></summary>

## 项目简介

V-Clip 是一个面向 macOS 的聚焦式剪贴板管理器。它会监听剪贴板变化，维护可搜索的历史记录，并通过一个紧凑的悬浮窗口让你用键盘快速浏览和选择内容。

## 功能亮点

- 类 Spotlight 的悬浮窗口，适合高频键盘操作。
- 自动记录剪贴板历史，并按最近复制时间排序。
- 在搜索框输入时可即时过滤文本内容。
- 支持 `↑` 和 `↓` 键盘导航，并实现了更自然的最小滚动体验。
- 支持一键上屏：确认条目后会重写剪贴板、切回原应用并自动发送 `Cmd + V`。
- 默认全局快捷键：`Option + V`。
- 使用 Kotlin Multiplatform 与 Compose Desktop 构建。

## 当前状态

当前项目主要聚焦于 macOS 桌面端。整体架构已经将共享状态逻辑与平台相关实现分离，后续扩展会更容易一些。

## 快捷键

| 快捷键 | 作用 |
| --- | --- |
| `Option + V` | 打开 V-Clip 窗口 |
| `↑` / `↓` | 移动选中项 |
| `Enter` | 确认当前选中项 |
| 鼠标点击 | 确认被点击条目并自动粘贴 |
| `Esc` | 关闭窗口 |

## 技术栈

- Kotlin Multiplatform
- Compose Multiplatform Desktop
- Kotlin Coroutines 与 StateFlow
- macOS 原生能力集成，包括剪贴板监听、应用激活和全局快捷键

## 项目结构

```text
composeApp/
  src/
    commonMain/   共享领域层与状态逻辑
    commonTest/   共享测试
    jvmMain/      桌面端与 macOS 相关实现
    jvmTest/      桌面端测试
```

## 本地运行

### 环境要求

- macOS
- JDK 17 或更高版本

### 启动应用

```bash
./gradlew :composeApp:run
```

### 自动粘贴所需的 macOS 权限

V-Clip 支持把选中的条目自动粘贴回之前正在输入的应用。要让这部分生效，macOS 需要允许应用或 IDE 进程拥有“辅助功能”权限。

请在这里授权：

`系统设置 -> 隐私与安全性 -> 辅助功能`

如果剪贴板历史正常，但点击或回车后没有自动上屏，第一时间先检查这里。

### 仅编译

```bash
./gradlew :composeApp:compileKotlinJvm
```

## 开发说明

- 这个应用更接近快速启动器，而不是传统的常驻主窗口应用。
- 共享业务逻辑位于 `commonMain`，macOS 集成代码位于 `jvmMain`。
- 列表选择实现了自定义的最小滚动行为，使悬浮窗中的键盘导航更顺滑、更准确。

## 后续可扩展方向

- 支持置顶或收藏重要条目。
- 支持应用重启后的历史持久化，通过 `ClipboardStore` 接口实现。
- 增加打包与分发能力，方便安装使用。
- 评估使用 JNA/CoreGraphics 发送按键，替代 `osascript` 子进程方案。

</details>
