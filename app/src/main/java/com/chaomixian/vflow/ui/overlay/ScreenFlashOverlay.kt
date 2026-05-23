package com.chaomixian.vflow.ui.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 全屏闪烁覆盖层。
 * 在指定颜色上执行 N 次（淡入 → 持续 → 淡出）动画，用于视觉提示。
 */
class ScreenFlashOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRoot: FrameLayout? = null
    private var pendingRunnable: Runnable? = null
    private var isDismissing = false

    /**
     * 显示闪烁覆盖层并等待动画完成。
     * @param colorInt 颜色值（ARGB，alpha 由颜色值本身决定）
     * @param fadeInMs 淡入时长（毫秒）
     * @param holdMs 每次闪烁的持续时长（毫秒）
     * @param fadeOutMs 淡出时长（毫秒）
     * @param flashCount 闪烁次数
     * @param intervalMs 闪烁间隔（毫秒），仅在多次闪烁时生效
     */
    suspend fun show(
        colorInt: Int,
        fadeInMs: Long,
        holdMs: Long,
        fadeOutMs: Long,
        flashCount: Int,
        intervalMs: Long = 0
    ): Unit = withContext(Dispatchers.Main) {
        val targetAlpha = ((colorInt ushr 24) and 0xFF) / 255f
        val count = flashCount.coerceAtLeast(1)

        suspendCancellableCoroutine { continuation ->
            // 创建全屏覆盖层
            val overlay = FrameLayout(context).apply {
                setBackgroundColor(colorInt)
                alpha = 0f
            }
            overlayRoot = overlay

            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val params = WindowManager.LayoutParams(
                metrics.widthPixels,
                metrics.heightPixels,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            try {
                windowManager.addView(overlay, params)
            } catch (_: Exception) {
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }

            // 取消时清理
            continuation.invokeOnCancellation {
                pendingRunnable?.let { overlay.removeCallbacks(it) }
                dismiss()
            }

            // 启动 N 次闪烁循环
            runFlashCycle(overlay, targetAlpha, fadeInMs, holdMs, fadeOutMs, intervalMs, count, 0) {
                dismiss()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /**
     * 递归执行闪烁循环。
     */
    private fun runFlashCycle(
        overlay: FrameLayout,
        targetAlpha: Float,
        fadeInMs: Long,
        holdMs: Long,
        fadeOutMs: Long,
        intervalMs: Long,
        totalCycles: Int,
        currentCycle: Int,
        onComplete: () -> Unit
    ) {
        if (currentCycle >= totalCycles) {
            onComplete()
            return
        }

        // 每个循环新建 fadeIn 和 fadeOut，避免 listener 累积
        val fadeIn = ValueAnimator.ofFloat(0f, targetAlpha).apply {
            duration = fadeInMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { overlay.alpha = it.animatedValue as Float }
        }

        val fadeOut = ValueAnimator.ofFloat(targetAlpha, 0f).apply {
            duration = fadeOutMs
            interpolator = AccelerateInterpolator()
            addUpdateListener { overlay.alpha = it.animatedValue as Float }
        }

        fadeIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 淡入结束 → 持续 → 淡出
                pendingRunnable = Runnable {
                    fadeOut.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // 确保 alpha 归零；背景保留到全部循环结束，否则后续循环没有可见内容。
                            overlay.alpha = 0f
                            // 淡出结束 → 间隔 → 下一次循环
                            if (intervalMs > 0 && currentCycle + 1 < totalCycles) {
                                pendingRunnable = Runnable {
                                    runFlashCycle(
                                        overlay, targetAlpha, fadeInMs, holdMs, fadeOutMs,
                                        intervalMs, totalCycles, currentCycle + 1, onComplete
                                    )
                                }
                                overlay.postDelayed(pendingRunnable, intervalMs)
                            } else {
                                runFlashCycle(
                                    overlay, targetAlpha, fadeInMs, holdMs, fadeOutMs,
                                    intervalMs, totalCycles, currentCycle + 1, onComplete
                                )
                            }
                        }
                    })
                    fadeOut.start()
                }
                overlay.postDelayed(pendingRunnable, holdMs)
            }
        })

        fadeIn.start()
    }

    private fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        try {
            pendingRunnable?.let { overlayRoot?.removeCallbacks(it) }
            overlayRoot?.alpha = 0f
            // 直到最终销毁时再移除背景，避免多次闪烁时后续循环不可见。
            overlayRoot?.background = null
            overlayRoot?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        } finally {
            overlayRoot = null
            pendingRunnable = null
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
}
