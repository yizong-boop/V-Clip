package com.example.macclipboardmanager.macos

import com.example.macclipboardmanager.macos.foundation.CocoaInterop
import com.example.macclipboardmanager.macos.foundation.MacMainThreadDispatcher
import com.example.macclipboardmanager.macos.objc.ObjcRuntime
import com.example.macclipboardmanager.theme.AppThemePreference
import com.sun.jna.Pointer
import com.sun.jna.Structure
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.util.WeakHashMap

internal class MacVisualEffectWindowStyler {
    private val visualEffectViews = WeakHashMap<Window, Pointer>()
    private val originalContentViews = WeakHashMap<Window, Pointer>()

    fun apply(window: Window, theme: AppThemePreference): Boolean {
        if (!MacPlatform.isMacOs()) {
            return true
        }

        var ok = true
        MacMainThreadDispatcher.dispatchSync {
            runCatching {
                CocoaInterop.autoreleasePool {
                    when (theme) {
                        AppThemePreference.FrostedGlass -> {
                            if (!install(window)) ok = false
                        }
                        AppThemePreference.Solid -> remove(window)
                    }
                }
            }.onFailure { error ->
                System.err.println("Unable to apply macOS visual effect window style: ${error.message}")
                ok = false
            }
        }
        return ok
    }

    fun dispose(window: Window) {
        if (!MacPlatform.isMacOs()) {
            return
        }

        // Keep the visual effect installed while the Compose window is merely
        // hidden. Toggling the NSWindow back to a "no effect" state during hide
        // can briefly expose the transparent clear-color window before Swing
        // processes the visibility change.
    }

    fun release(window: Window) {
        if (!MacPlatform.isMacOs()) {
            return
        }

        MacMainThreadDispatcher.dispatchSync {
            runCatching {
                CocoaInterop.autoreleasePool {
                    remove(window)
                }
            }
        }
    }

    private fun install(window: Window): Boolean {
        val nsWindow = window.nativeNsWindow()
        if (nsWindow == null) {
            System.err.println("VisualEffect install failed: native NSWindow not ready (SkiaLayer init pending?)")
            return false
        }

        val originalContentView = originalContentViews[window]
            ?: (ObjcRuntime.sendPointer(nsWindow, "contentView") ?: run {
                System.err.println("VisualEffect install failed: NSWindow has no contentView")
                return false
            }).also { originalView ->
                originalContentViews[window] = originalView
            }
        val hostView = ObjcRuntime.sendPointer(originalContentView, "superview")
        if (hostView == null) {
            System.err.println("VisualEffect install failed: contentView has no superview")
            return false
        }

        val existingView = visualEffectViews[window]
        val visualEffectView = if (existingView != null) {
            existingView.also {
                ObjcRuntime.sendVoid(it, "setFrame:", window.nsRect())
                ObjcRuntime.sendVoid(it, "setHidden:", ObjcBoolFalse)
            }
        } else {
            createVisualEffectView(window).also { view ->
                ObjcRuntime.sendVoid(
                    hostView,
                    "addSubview:positioned:relativeTo:",
                    view,
                    NSWindowBelow,
                    originalContentView,
                )
                visualEffectViews[window] = view
            }
        }

        ObjcRuntime.sendVoid(originalContentView, "setFrame:", window.nsRect())
        ObjcRuntime.sendVoid(originalContentView, "setAutoresizingMask:", NSViewWidthSizable or NSViewHeightSizable)
        configureRoundedLayer(originalContentView)

        // Configure transparent window LAST, after the visual effect view is in
        // the hierarchy. If install fails early (SkiaLayer not ready, etc.), the
        // NSWindow keeps its default opaque background instead of clearColor,
        // preventing a fully transparent window.
        configureTransparentWindow(nsWindow)
        return true
    }

    private fun remove(window: Window) {
        val originalContentView = originalContentViews.remove(window)
        visualEffectViews.remove(window)?.let { visualEffectView ->
            ObjcRuntime.sendVoid(visualEffectView, "removeFromSuperview")
        }
        if (originalContentView != null) {
            ObjcRuntime.sendVoid(originalContentView, "setFrame:", window.nsRect())
            unconfigureRoundedLayer(originalContentView)
        }
        window.nativeNsWindow()?.let { nsWindow ->
            ObjcRuntime.sendVoid(nsWindow, "setOpaque:", ObjcBoolTrue)
        }
    }

    private fun createVisualEffectView(window: Window): Pointer {
        val allocatedView = requireNotNull(
            ObjcRuntime.sendPointer(nsVisualEffectViewClass, "alloc"),
        ) {
            "Unable to allocate NSVisualEffectView."
        }
        val visualEffectView = requireNotNull(
            ObjcRuntime.sendPointer(allocatedView, "initWithFrame:", window.nsRect()),
        ) {
            "Unable to initialize NSVisualEffectView."
        }

        ObjcRuntime.sendVoid(visualEffectView, "setMaterial:", NSVisualEffectMaterialPopover)
        ObjcRuntime.sendVoid(visualEffectView, "setBlendingMode:", NSVisualEffectBlendingModeBehindWindow)
        ObjcRuntime.sendVoid(visualEffectView, "setState:", NSVisualEffectStateActive)
        ObjcRuntime.sendVoid(visualEffectView, "setAutoresizingMask:", NSViewWidthSizable or NSViewHeightSizable)
        configureRoundedLayer(visualEffectView)
        return visualEffectView
    }

    private fun configureTransparentWindow(nsWindow: Pointer) {
        ObjcRuntime.sendVoid(nsWindow, "setOpaque:", ObjcBoolFalse)
        ObjcRuntime.sendVoid(nsWindow, "setHasShadow:", ObjcBoolTrue)
        ObjcRuntime.sendPointer(nsColorClass, "clearColor")?.let { clearColor ->
            ObjcRuntime.sendVoid(nsWindow, "setBackgroundColor:", clearColor)
        }
    }

    private fun configureRoundedLayer(nsView: Pointer) {
        ObjcRuntime.sendVoid(nsView, "setWantsLayer:", ObjcBoolTrue)
        val layer = ObjcRuntime.sendPointer(nsView, "layer") ?: return
        ObjcRuntime.sendVoid(layer, "setCornerRadius:", CornerRadius)
        ObjcRuntime.sendVoid(layer, "setMasksToBounds:", ObjcBoolTrue)
    }

    private fun unconfigureRoundedLayer(nsView: Pointer) {
        val layer = ObjcRuntime.sendPointer(nsView, "layer") ?: return
        ObjcRuntime.sendVoid(layer, "setMasksToBounds:", ObjcBoolFalse)
    }

    private fun Window.nativeNsWindow(): Pointer? {
        val nativeWindowHandle = findSkiaLayer(this)?.windowHandle ?: return null
        if (nativeWindowHandle == 0L) {
            return null
        }
        return Pointer(nativeWindowHandle)
    }

    private fun findSkiaLayer(component: Component): SkiaLayer? {
        if (component is SkiaLayer) {
            return component
        }
        if (component !is Container) {
            return null
        }
        return component.components.firstNotNullOfOrNull(::findSkiaLayer)
    }

    private fun Window.nsRect(): NSRect.ByValue =
        NSRect.ByValue(
            origin = NSPoint(x = 0.0, y = 0.0),
            size = NSSize(width = width.toDouble(), height = height.toDouble()),
        )

    private companion object {
        private val nsVisualEffectViewClass: Pointer by lazy {
            ObjcRuntime.getClass("NSVisualEffectView")
        }
        private val nsColorClass: Pointer by lazy {
            ObjcRuntime.getClass("NSColor")
        }

        private const val NSVisualEffectMaterialPopover = 6L
        private const val NSVisualEffectBlendingModeBehindWindow = 0L
        private const val NSVisualEffectStateActive = 1L
        private const val NSViewWidthSizable = 2L
        private const val NSViewHeightSizable = 16L
        private const val NSWindowAbove = 1L
        private const val NSWindowBelow = -1L
        private const val CornerRadius = 28.0
        private const val ObjcBoolTrue: Byte = 1
        private const val ObjcBoolFalse: Byte = 0
    }
}

internal open class NSPoint(
    @JvmField var x: Double = 0.0,
    @JvmField var y: Double = 0.0,
) : Structure() {
    override fun getFieldOrder(): List<String> = listOf("x", "y")

    class ByValue(
        x: Double = 0.0,
        y: Double = 0.0,
    ) : NSPoint(x, y), Structure.ByValue
}

internal open class NSSize(
    @JvmField var width: Double = 0.0,
    @JvmField var height: Double = 0.0,
) : Structure() {
    override fun getFieldOrder(): List<String> = listOf("width", "height")

    class ByValue(
        width: Double = 0.0,
        height: Double = 0.0,
    ) : NSSize(width, height), Structure.ByValue
}

internal open class NSRect(
    @JvmField var origin: NSPoint.ByValue = NSPoint.ByValue(),
    @JvmField var size: NSSize.ByValue = NSSize.ByValue(),
) : Structure() {
    override fun getFieldOrder(): List<String> = listOf("origin", "size")

    class ByValue(
        origin: NSPoint = NSPoint(),
        size: NSSize = NSSize(),
    ) : NSRect(
        origin = NSPoint.ByValue(origin.x, origin.y),
        size = NSSize.ByValue(size.width, size.height),
    ), Structure.ByValue
}
