# V-Clip

<p align="center">
  A lightweight Spotlight-style clipboard manager for macOS, built with Kotlin Multiplatform and Compose Desktop.
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

V-Clip is a macOS clipboard manager that behaves like a quick launcher. Press the global shortcut, search recent clipboard entries, pick one with the keyboard or mouse, and V-Clip pastes it back into the app you were using.

It is focused on a small, fast desktop workflow: clipboard monitoring, searchable history, keyboard-first selection, and automatic paste.

## Highlights

- Spotlight-style floating window with an always-on-top, undecorated macOS overlay.
- Default global shortcut: `Option + V`, implemented through native Carbon hotkeys.
- Searchable clipboard history with newest-first ordering.
- History is persisted to JSON across app restarts.
- Duplicate entries are deduplicated; reused entries are moved back to the top.
- Keyboard navigation with `↑` / `↓`, `Enter`, and minimal-scroll behavior.
- Mouse selection and auto-paste in one action.
- Auto-paste failure handling with timeout protection, cooldown, and an in-window toast.
- Stable focus behavior for the floating window, including delayed blur hiding to avoid accidental close on show.
- Drag support for the undecorated window from the `Clipboard` title area.
- Built with Kotlin Multiplatform, Compose Desktop, Coroutines, StateFlow, Kotlin Serialization, and JNA.

## Current Status

The project currently targets macOS desktop. The codebase separates shared clipboard state from macOS integrations, with focused test coverage around repository behavior, persistence, paste flow, focus/window state, and UI formatting helpers.

## Keyboard Shortcuts

| Shortcut | Action |
| --- | --- |
| `Option + V` | Open the V-Clip window |
| Type in search field | Filter clipboard history |
| `↑` / `↓` | Move selection |
| `Enter` | Confirm the selected item and paste it |
| Mouse click | Confirm the clicked item and paste it |
| `Esc` | Close the window |

## Data Storage

Clipboard history is stored locally at:

```text
~/Library/Application Support/V-Clip/clipboard.json
```

The repository keeps up to 500 items and truncates very long clipboard text to 10,000 characters before storing it.

If the JSON file becomes unreadable, V-Clip backs it up as `clipboard.json.bak` and starts with an empty history.

## Tech Stack

- Kotlin Multiplatform
- Compose Multiplatform for Desktop
- Material 3 Compose components
- Kotlin Coroutines and StateFlow
- Kotlin Serialization JSON
- JNA for macOS native integration
- Carbon global hotkey APIs
- AppleScript/System Events for `Cmd + V` auto-paste

## Project Structure

```text
composeApp/
  src/
    commonMain/   Shared clipboard domain, contracts, and MainViewModel
    commonTest/   Shared repository and ViewModel tests
    jvmMain/      Desktop UI and macOS-specific implementations
    jvmTest/      Persistence, paste, hotkey/window, and UI helper tests
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

```text
System Settings -> Privacy & Security -> Accessibility
```

If clipboard history works but auto-paste does not, check this permission first. V-Clip also shows a toast when auto-paste fails because of permission or timeout issues.

### Build and test

```bash
./gradlew :composeApp:compileKotlinJvm
./gradlew :composeApp:jvmTest
```

### Package DMG

```bash
./gradlew :composeApp:packageDmg
```

## Development Notes

- The app window is intentionally closer to Spotlight than to a traditional persistent app window.
- `commonMain` owns the clipboard history model, filtering, selection, and effect emission.
- `jvmMain` owns the Compose Desktop UI plus macOS clipboard, activation, hotkey, paste, persistence, and window-dragging integrations.
- Auto-paste is guarded against duplicate confirmation events and concurrent `osascript` execution.
- Window focus behavior is isolated in `SpotlightWindowController` so show/blur edge cases can be tested without Compose.

## Roadmap Ideas

- Add a visible clear-history action.
- Add pinning or favorites for entries that should survive ordering changes.
- Add user-configurable shortcuts and history limits.
- Improve packaged app identity and permissions flow for distribution.
- Evaluate a lower-level CoreGraphics paste path to replace the `osascript` subprocess.

</details>

<details>
<summary><b id="中文">中文</b></summary>

## 项目简介

V-Clip 是一个面向 macOS 的剪贴板管理器，交互方式接近 Spotlight。按下全局快捷键后，可以搜索最近复制过的内容，用键盘或鼠标选择条目，并自动粘贴回之前正在使用的应用。

它聚焦在一个轻量、高频的桌面工作流：监听剪贴板、保存可搜索历史、快速选择、自动上屏。

## 功能亮点

- 类 Spotlight 的悬浮窗口，置顶、无标题栏、适合快速唤起。
- 默认全局快捷键：`Option + V`，通过 macOS Carbon 热键实现。
- 支持剪贴板历史搜索，并按最近使用时间排序。
- 历史记录会持久化为 JSON，重启应用后仍可恢复。
- 自动去重；再次复制或选择旧条目时会移动到列表顶部。
- 支持 `↑` / `↓`、`Enter` 的键盘优先操作，并带有最小滚动行为。
- 支持鼠标点击条目并自动粘贴。
- 自动粘贴失败时会显示 Toast，并带有超时保护、失败冷却和并发保护。
- 悬浮窗焦点获取和失焦隐藏更稳定，避免刚显示时被 blur 事件误关。
- 无标题栏窗口可从 `Clipboard` 标题区域拖拽移动。
- 使用 Kotlin Multiplatform、Compose Desktop、Coroutines、StateFlow、Kotlin Serialization 和 JNA 构建。

## 当前状态

当前项目主要面向 macOS 桌面端。代码已经将共享剪贴板状态和 macOS 平台集成拆开，并围绕仓库行为、持久化、粘贴流程、窗口焦点状态和 UI 格式化工具补充了测试。

## 快捷键

| 快捷键 | 作用 |
| --- | --- |
| `Option + V` | 打开 V-Clip 窗口 |
| 在搜索框输入 | 过滤剪贴板历史 |
| `↑` / `↓` | 移动选中项 |
| `Enter` | 确认当前选中项并自动粘贴 |
| 鼠标点击 | 确认被点击条目并自动粘贴 |
| `Esc` | 关闭窗口 |

## 数据存储

剪贴板历史会保存在本地：

```text
~/Library/Application Support/V-Clip/clipboard.json
```

当前最多保留 500 条历史；单条文本超过 10,000 个字符时会被截断后再保存。

如果 JSON 文件损坏或无法解析，V-Clip 会将其备份为 `clipboard.json.bak`，然后以空历史启动。

## 技术栈

- Kotlin Multiplatform
- Compose Multiplatform for Desktop
- Material 3 Compose 组件
- Kotlin Coroutines 与 StateFlow
- Kotlin Serialization JSON
- JNA macOS 原生能力集成
- Carbon 全局快捷键 API
- AppleScript/System Events 实现 `Cmd + V` 自动粘贴

## 项目结构

```text
composeApp/
  src/
    commonMain/   共享剪贴板领域层、接口契约和 MainViewModel
    commonTest/   共享仓库与 ViewModel 测试
    jvmMain/      桌面 UI 与 macOS 平台实现
    jvmTest/      持久化、粘贴、热键/窗口和 UI 工具测试
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

```text
系统设置 -> 隐私与安全性 -> 辅助功能
```

如果剪贴板历史正常，但点击或回车后没有自动上屏，第一时间检查这里。因为权限或超时导致自动粘贴失败时，V-Clip 也会在窗口内显示 Toast。

### 构建与测试

```bash
./gradlew :composeApp:compileKotlinJvm
./gradlew :composeApp:jvmTest
```

### 打包 DMG

```bash
./gradlew :composeApp:packageDmg
```

## 开发说明

- 这个应用更接近 Spotlight，而不是传统常驻主窗口应用。
- `commonMain` 负责剪贴板历史模型、过滤、选择状态和一次性效果。
- `jvmMain` 负责 Compose Desktop UI，以及 macOS 剪贴板、应用激活、热键、粘贴、持久化和窗口拖拽能力。
- 自动粘贴流程会防止重复确认事件和并发 `osascript` 执行。
- 窗口焦点逻辑收敛在 `SpotlightWindowController`，方便在不依赖 Compose 的情况下测试显示和失焦边界。

## 后续可扩展方向

- 增加可见的清空历史入口。
- 支持置顶或收藏不希望被排序变化影响的重要条目。
- 支持自定义快捷键和历史数量上限。
- 完善打包后的应用身份与权限引导，方便分发。
- 评估使用更底层的 CoreGraphics 粘贴路径，替代 `osascript` 子进程方案。

</details>
