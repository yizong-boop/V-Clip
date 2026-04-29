# macOS Apple Silicon 上 Carbon 全局热键失效排查与修复

## 概述

V-Clip 是一个基于 Kotlin Multiplatform + Compose Desktop 的 macOS 剪贴板管理器，通过 Carbon Framework 的 `RegisterEventHotKey` API 注册全局热键（默认 `Option + V`），实现系统级快捷键唤起悬浮窗口。

在老款 Intel Mac 上功能正常，迁移到 Apple Silicon (M 系列芯片) Mac 后，快捷键完全失效——按 `Option + V` 无任何响应。

**根本原因**：JNA 5.17.0 在 arm64 架构上传递 8 字节 C 结构体（`EventHotKeyID`）时，寄存器布局与 arm64 AAPCS (ARM Architecture Procedure Call Standard) 不一致，导致 Carbon Framework 收到错误的结构体数据，热键事件 ID 无法匹配，事件在应用层被静默丢弃。

**同时发现**：新版 macOS (Sonoma/Sequoia) 要求进程显式调用 `[NSApplication sharedApplication]` 并设置 `activationPolicy` 才能正常接收 Carbon 事件。

---

## 环境信息

| 项目 | 旧电脑 | 新电脑 |
|------|--------|--------|
| 芯片架构 | Intel x86_64 | Apple Silicon arm64 |
| macOS 版本 | 较旧（推测 Ventura 或更早） | 较新（Sonoma 或 Sequoia） |
| JDK | 17 | 17 (Homebrew, `/opt/homebrew/`) |
| JNA 版本 | 5.17.0 | 5.17.0 |
| Kotlin | 2.3.20 | 2.3.20 |
| Compose Multiplatform | 1.10.3 | 1.10.3 |

---

## 排查过程

### 第一层：验证热键是否被按下（排除键盘硬件问题）

**假设**：键盘或系统层面的问题导致按键未被识别。

**验证方法**：在 `MacGlobalHotkeyManager.handleCarbonEvent()` 入口处添加日志。

```kotlin
private fun handleCarbonEvent(eventRef: Pointer?): Int {
    println("V-Clip: Carbon event callback fired.")
    // ...
}
```

**结果**：日志打印了，Carbon 事件回调**确实被触发**。
```
V-Clip: Carbon event callback fired.
V-Clip: Carbon event callback fired.
V-Clip: Carbon event callback fired.
```

**结论**：热键注册成功（`RegisterEventHotKey` 未报错），Carbon Framework 正确接收了按键事件并回调了事件处理器。问题在下游。

---

### 第二层：追踪事件链路（定位信号丢失点）

**假设**：Carbon 回调触发了，但事件在 Kotlin 协程 / SharedFlow 传递链中丢失。

**验证方法**：在事件链路的关键节点添加日志：

```
Carbon callback → SharedFlow.tryEmit → collector (ViewModel) → 
    SharedFlow.emit(ShowWindow) → collector (main.kt) → Window.show()
```

```kotlin
// MacGlobalHotkeyManager.kt
mutableActivations.tryEmit(HotkeyActivation(...))
println("V-Clip: tryEmit result = $emitted")

// MainViewModel.kt
globalHotkeyManager.activations.collect {
    println("V-Clip: Hotkey activation received, emitting ShowWindow")
    mutableEffects.emit(MainEffect.ShowWindow(...))
}

// main.kt
viewModel.effects.collect { effect ->
    println("V-Clip: Effect in main.kt: ${effect::class.simpleName}")
}
```

**结果**：Carbon 回调触发了，但 `Hotkey activation received` **从未打印**。事件在 Carbon 回调到 SharedFlow 这一步断裂。

```
V-Clip: Carbon event callback fired.     ← ✓ Carbon 收到了
V-Clip: Hotkey activation received       ← ✗ 从未出现
```

**结论**：事件在 `handleCarbonEvent()` 内部被丢弃。

---

### 第三层：深挖 `handleCarbonEvent` 内部逻辑

**假设**：`tryEmit` 失败（buffer 满或无订阅者），或事件被条件分支跳过。

**验证方法**：细化 `handleCarbonEvent` 内部的日志，追踪每个分支：

```kotlin
val hotkeyId = CarbonBridge.readHotkeyId(eventRef)
println("V-Clip: Carbon event — hotkeyId.id=${hotkeyId.id}, registeredHotkeyId=$registeredHotkeyId")

val hotkey = synchronized(lock) {
    if (hotkeyId.id != registeredHotkeyId) {
        println("V-Clip: ID mismatch, ignoring (got ${hotkeyId.id}, expected $registeredHotkeyId)")
        null
    } else {
        registeredHotkey
    }
} ?: return CarbonBridge.noErr()
```

**结果**：找到了！ID 不匹配。

```
V-Clip: Carbon event — hotkeyId.id=12, registeredHotkeyId=1
V-Clip: Carbon event — ID mismatch, ignoring (got 12, expected 1)
```

**关键发现**：注册热键时我们传入 `id=1`，但 Carbon 事件返回的 `id=12`。ID 不匹配导致事件被 `return CarbonBridge.noErr()` 静默丢弃。

---

### 第四层：根因分析 —— JNA arm64 结构体传值 ABI 问题

#### 数据结构

Carbon 的 `EventHotKeyID` 是一个 8 字节 C 结构体：

```c
struct EventHotKeyID {
    OSType signature;  // UInt32, 4 bytes
    UInt32 id;         // UInt32, 4 bytes
};
```

在 Kotlin/JNA 侧的定义：

```kotlin
@Structure.FieldOrder("signature", "id")
internal class EventHotKeyID : Structure() {
    @JvmField var signature: Int = 0  // 4 bytes
    @JvmField var id: Int = 0         // 4 bytes
}
```

注册热键时，此结构体以**值传递**方式传给 Carbon：

```kotlin
// CarbonLibrary interface (JNA)
fun RegisterEventHotKey(
    inHotKeyCode: Int,
    inHotKeyModifiers: Int,
    inHotKeyID: EventHotKeyID,  // ← 按值传递，8 字节结构体
    inTarget: Pointer,
    inOptions: Int,
    outRef: PointerByReference,
): Int
```

#### arm64 AAPCS 传参约定

arm64 AAPCS 规定，**8 字节及以下的结构体**通过单个通用寄存器传递（如同一个 64-bit 整数）：

```
Register bits 0–31  ← struct bytes 0–3  (signature, 小端序)
Register bits 32–63 ← struct bytes 4–7  (id, 小端序)
```

#### JNA 在 arm64 上的 Bug

JNA 5.17.0 在 arm64 上处理按值传递的结构体时，使用了**逐字段展开**（field-by-field expansion）的方式，将两个 4 字节字段分别放入两个 32 位参数槽位，而非打包为一个 64 位寄存器值。这导致 Carbon 框架在寄存器中读取到的数据布局与预期不同：

| | 预期布局（AAPCS） | JNA 实际传递 |
|---|---|---|
| 寄存器低 32 位 | signature (0x4D43424D) | signature |
| 寄存器高 32 位 | id (0x00000001) | 下一个参数或未定义值 |

Carbon 收到的是字段错位的数据，存储的 `id` 实际是其他值（这里是 `12`），而非我们传入的 `1`。后续热键事件触发时，Carbon 返回它存储的 `id=12`，与我们内存中记录的 `registeredHotkeyId=1` 不匹配，事件被丢弃。

#### 为什么 Intel Mac 不受影响

x86_64 的 System V AMD64 ABI 对 8 字节结构体有不同的处理方式——通常通过栈传递或直接放入一个 64 位寄存器。JNA 在 x86_64 上的结构体传值实现是正确的，因此没有此问题。这是 arm64 专属的 JNA bug。

---

### 第五层：修复前还发现了另一个问题

在排查初期，尝试在 `main()` 函数（`application {}` 块之前）调用 `bootstrapMacApp()` 来初始化 NSApplication：

```
FAILURE: Build failed with exception.
Process finished with non-zero exit value 133
```

**Exit code 133 = 128 + 5 = SIGTRAP**。原因是：在 Compose Desktop 初始化 NSApplication 之前调用 `[NSApplication sharedApplication]` 触发了 ObjC Runtime 与 AWT 的初始化冲突。

**修复**：将 `bootstrapMacApp()` 移入 `DisposableEffect(Unit)` 内部，确保在 Compose Desktop 完成 NSApplication 初始化之后才设置 `activationPolicy`。

---

## 修复方案

### 方案一（采纳）：用 Java 原生类型替代 JNA Structure 传值

**核心思路**：将 `EventHotKeyID` 的两个 `Int` 字段手动打包为一个 `Long`，直接按原始类型传递给 JNA，完全绕开 JNA 的结构体传值机制。

#### 1. 打包函数

```kotlin
/**
 * 将 EventHotKeyID (signature + id) 打包为单个 Long。
 * arm64 AAPCS: struct ≤ 8 bytes → 单个寄存器
 * 低 32 位 = signature，高 32 位 = id（小端序）
 */
private fun packEventHotKeyId(signature: Int, id: Int): Long =
    (id.toLong() shl 32) or (signature.toLong() and 0xFFFFFFFFL)
```

#### 2. 修改 JNA 接口签名

```kotlin
// 修改前（有问题）
fun RegisterEventHotKey(
    inHotKeyCode: Int,
    inHotKeyModifiers: Int,
    inHotKeyID: EventHotKeyID,  // ← JNA Structure by-value
    // ...
): Int

// 修改后（修复）
fun RegisterEventHotKey(
    inHotKeyCode: Int,
    inHotKeyModifiers: Int,
    inHotKeyID: Long,            // ← 直接传 Long
    // ...
): Int
```

#### 3. 读取端也避开 Structure.read()

读取热键事件参数时，不使用 `JNA Structure.read()`，而是手动分配 8 字节原始内存，用 `Memory.getLong(0)` 读取后再解包：

```kotlin
fun readHotkeyId(eventRef: Pointer): EventHotKeyID {
    val buffer = Memory(8)
    carbon.GetEventParameter(
        eventRef, kEventParamDirectObject, typeEventHotKeyID,
        null, 8, null, buffer,
    )
    return unpackEventHotKeyId(buffer.getLong(0))
}

private fun unpackEventHotKeyId(packed: Long): EventHotKeyID =
    EventHotKeyID().apply {
        signature = (packed and 0xFFFFFFFFL).toInt()
        id = (packed shr 32).toInt()
    }
```

#### 4. NSApplication 初始化（次要修复）

```kotlin
private fun bootstrapMacApp() {
    runCatching {
        val appClass = ObjcRuntime.getClass("NSApplication")
        val sharedApp = ObjcRuntime.sendPointer(appClass, "sharedApplication")
        if (sharedApp != null) {
            ObjcRuntime.sendVoid(sharedApp, "setActivationPolicy:", 1L)
        }
    }
}
```

**注意**：此调用**必须**在 Compose Desktop 的 `application {}` 块内部执行（不能在此之前），以避免与 AWT 的 NSApplication 初始化冲突。

---

### 方案二（未采纳）：升级 JNA 版本

理论上，更新到修复了 arm64 struct-by-value 问题的 JNA 版本可以解决。但：
- 不确定哪个 JNA 版本彻底修复了此问题
- 升级可能引入其他兼容性问题
- 方案一更可控，不依赖第三方库的修复节奏

### 方案三（未采纳）：改用 CGEvent API

使用 `CGEvent.tapCreate` 替代 Carbon `RegisterEventHotKey`。但：
- 需要 Accessibility 权限，用户体验差
- Carbon API 虽然标记为 deprecated，但至今仍然可用
- 改动范围大，风险高

---

## 验证结果

修复后日志：

```
V-Clip: Carbon event callback fired.
V-Clip: Carbon event — hotkeyId.id=1, registeredHotkeyId=1  ← ID 匹配！
V-Clip: Hotkey activation received, emitting ShowWindow
V-Clip: tryEmit result = true
V-Clip: Effect in main.kt: ShowWindow
V-Clip: ShowWindow triggered, requesting foreground...
V-Clip: spotlightWindowState.isVisible = true
V-Clip: Window focused after 0 attempts
```

整个事件链路全部贯通，悬浮窗口正常弹出。

---

## 知识点总结

1. **JNA 在 arm64 上传递小结构体（≤ 8 bytes）存在已知的 ABI 兼容性问题**。当结构体按值传递给 native 函数时，JNA 可能以不符合 arm64 AAPCS 的方式布局参数。解决方案是将结构体手动打包为同宽度的原始类型（`Long` 替代 2×`Int`），读取端同理。

2. **arm64 与 x86_64 的调用约定完全不同**。arm64 将 8 字节内的小结构体通过单个寄存器传递，而 x86_64 的 System V ABI 有自己的一套分类规则（`INTEGER` class 结构体可拆分为多个寄存器或通过栈传递）。从 Intel Mac 迁移到 Apple Silicon Mac 时，所有通过 JNA 按值传递结构体的地方都需要审查。

3. **macOS 版本升级会收紧安全策略**。新版 macOS (Sonoma+) 要求进程显式设置 `NSApplicationActivationPolicy` 才能接收 Carbon 事件。即使 Carbon API 未被禁用，系统也可能在后台静默过滤事件。

4. **Carbon Framework 虽然已被标记为 deprecated 多年，但至今（macOS 15）仍可工作**。不过未来 macOS 版本可能彻底移除 Carbon，届时需要迁移到 `CGEvent` API。

5. **排查跨语言、跨架构 bug 时，逐层添加诊断日志是最有效的方法**。从用户可见的症状（快捷键无效）逐层深入到具体的位偏移问题，每一层都有明确的假设和验证方法。

6. **`MutableSharedFlow.tryEmit` 不保证事件投递成功**。当 buffer 满或没有活跃订阅者时，`tryEmit` 返回 `false` 而不抛出异常。关键事件应使用 `emit`（suspend 版本）或检查 `tryEmit` 的返回值。

---

## 相关文件变更

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `CarbonBridge.kt` | 修改 | `RegisterEventHotKey` 参数从 `EventHotKeyID` 改为 `Long`；`readHotkeyId` 改用 `Memory` 直接读取；新增 `packEventHotKeyId` / `unpackEventHotKeyId` |
| `main.kt` | 新增 | 添加 `bootstrapMacApp()` 函数，在 Composition 内部初始化 NSApplication activationPolicy |
| `MainViewModel.kt` | 无实质变更 | 快捷键定义恢复为 `Option + V` (keyCode=9, OPTION) |
| `MacGlobalHotkeyManager.kt` | 无实质变更 | 仅添加/移除了调试日志 |
