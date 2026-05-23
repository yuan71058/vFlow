package com.chaomixian.vflow.core.workflow.module.ui.components

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.common.ThemeUtils
import com.google.android.material.button.MaterialButton

/**
 * 屏幕闪烁模块的自定义编辑器 UI 提供者。
 * 提供一个颜色选择器对话框，含 HSV 渐变面板、彩虹色条、透明度调节。
 */
class ScreenFlashUIProvider : ModuleUIProvider {

    override fun getHandledInputIds() = setOf("color")

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((inputId: String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((android.content.Intent, (Int, android.content.Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.editor_screen_flash, parent, false)
        val colorPreview = view.findViewById<View>(R.id.color_preview)
        val colorHexText = view.findViewById<TextView>(R.id.color_hex_text)
        val pickButton = view.findViewById<MaterialButton>(R.id.button_pick_color)

        val holder = ScreenFlashViewHolder(view, colorPreview, colorHexText, pickButton)
        holder.currentColorHex = currentParameters["color"] as? String ?: "#FFFF0000"

        fun updatePreview() {
            val colorInt = try {
                Color.parseColor(holder.currentColorHex)
            } catch (_: Exception) {
                Color.RED
            }
            val gd = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                setColor(colorInt)
            }
            colorPreview.background = gd
            colorHexText.text = holder.currentColorHex
        }

        updatePreview()

        pickButton.setOnClickListener {
            showColorPickerDialog(context, holder.currentColorHex) { newColor ->
                holder.currentColorHex = newColor
                updatePreview()
                onParametersChanged()
            }
        }

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ScreenFlashViewHolder
        return mapOf("color" to h.currentColorHex)
    }

    // ==================== 颜色选择器对话框 ====================

    private fun showColorPickerDialog(
        context: Context,
        initialColor: String,
        onColorSelected: (String) -> Unit
    ) {
        val initialColorInt = try {
            Color.parseColor(initialColor)
        } catch (_: Exception) {
            Color.RED
        }

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColorInt, hsv)
        val initialAlpha = Color.alpha(initialColorInt)

        var currentHue = hsv[0]
        var currentSat = hsv[1]
        var currentVal = hsv[2]
        var currentAlpha = if (initialAlpha == 0) 255 else initialAlpha

        val dp = context.resources.displayMetrics.density
        val pad24 = (24 * dp).toInt()
        val pad12 = (12 * dp).toInt()
        val pad16 = (16 * dp).toInt()

        // 用项目主题 context 创建对话框
        val themedContext = ThemeUtils.createThemedContext(context)

        // 主容器
        val mainLayout = android.widget.LinearLayout(themedContext).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad24, (48 * dp).toInt(), pad24, pad16)
        }

        // 预览色块
        val previewBar = View(themedContext).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (72 * dp).toInt()
            ).apply { bottomMargin = pad12 }
        }

        // 上半部分：渐变面板 + 色条
        val topRow = android.widget.LinearLayout(themedContext).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val svPanel = SVPanelView(themedContext).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val hueBar = HueBarView(themedContext).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams((36 * dp).toInt(), android.widget.LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = pad12
            }
        }

        topRow.addView(svPanel)
        topRow.addView(hueBar)

        // 透明度条
        val alphaSlider = AlphaSliderView(themedContext).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (36 * dp).toInt()
            ).apply { topMargin = pad12 }
        }

        // 底部操作栏
        val bottomBar = android.widget.LinearLayout(themedContext).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad16 }
        }

        val hexButton = MaterialButton(themedContext, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            text = colorToHex(Color.HSVToColor(currentAlpha, floatArrayOf(currentHue, currentSat, currentVal)))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val confirmButton = MaterialButton(themedContext).apply {
            text = themedContext.getString(R.string.label_confirm)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = pad12 }
        }

        bottomBar.addView(hexButton)
        bottomBar.addView(confirmButton)

        mainLayout.addView(previewBar)
        mainLayout.addView(topRow)
        mainLayout.addView(alphaSlider)
        mainLayout.addView(bottomBar)

        // 更新颜色的函数
        fun updateColor() {
            val color = Color.HSVToColor(currentAlpha, floatArrayOf(currentHue, currentSat, currentVal))
            val solidColor = Color.HSVToColor(255, floatArrayOf(currentHue, currentSat, currentVal))

            // 预览色块
            val gd = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(color)
            }
            previewBar.background = gd

            // 渐变面板
            svPanel.setHue(currentHue)

            // 透明度条
            alphaSlider.setBaseColor(solidColor)
            alphaSlider.setAlpha(currentAlpha)

            // hex 按钮同步
            hexButton.text = colorToHex(color)
        }

        // 连接回调
        svPanel.onColorChanged = { sat, value ->
            currentSat = sat
            currentVal = value
            updateColor()
        }

        hueBar.onHueChanged = { hue ->
            currentHue = hue
            updateColor()
        }

        alphaSlider.onAlphaChanged = { alpha ->
            currentAlpha = alpha
            updateColor()
        }

        // 初始化颜色状态
        svPanel.setHue(currentHue)
        svPanel.setPosition(currentSat, currentVal)
        hueBar.setHue(currentHue)
        updateColor()

        val dialog = AlertDialog.Builder(themedContext)
            .setView(mainLayout)
            .create()

        // 圆角背景
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 28 * dp
                setColor(android.graphics.Color.TRANSPARENT)
            }
        )

        confirmButton.setOnClickListener {
            val finalColor = Color.HSVToColor(currentAlpha, floatArrayOf(currentHue, currentSat, currentVal))
            onColorSelected(colorToHex(finalColor))
            dialog.dismiss()
        }

        hexButton.setOnClickListener {
            showHexInputDialog(themedContext, currentHue, currentSat, currentVal, currentAlpha) { hue, sat, value, alpha ->
                currentHue = hue
                currentSat = sat
                currentVal = value
                currentAlpha = alpha
                svPanel.setPosition(currentSat, currentVal)
                hueBar.setHue(currentHue)
                updateColor()
            }
        }

        dialog.show()
    }

    // ==================== hex 输入弹窗 ====================

    private fun showHexInputDialog(
        context: Context,
        hue: Float,
        sat: Float,
        value: Float,
        alpha: Int,
        onColorParsed: (hue: Float, sat: Float, value: Float, alpha: Int) -> Unit
    ) {
        val currentColor = Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))
        val currentHex = colorToHex(currentColor)

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(9))
            setText(currentHex)
            setSelection(currentHex.length)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.label_hex_input)
            .setView(editText)
            .setPositiveButton(R.string.label_confirm) { _, _ ->
                val input = editText.text.toString().trim()
                val hex = if (input.startsWith("#")) input else "#$input"
                try {
                    val colorInt = Color.parseColor(hex)
                    val parsedHsv = FloatArray(3)
                    Color.colorToHSV(colorInt, parsedHsv)
                    val parsedAlpha = Color.alpha(colorInt)
                    onColorParsed(parsedHsv[0], parsedHsv[1], parsedHsv[2], parsedAlpha)
                } catch (_: IllegalArgumentException) {
                    // 无效色值，不操作
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ==================== 工具方法 ====================

    private fun colorToHex(colorInt: Int): String {
        val a = Color.alpha(colorInt)
        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)
        return "#%02X%02X%02X%02X".format(a, r, g, b)
    }

    // ==================== ViewHolder ====================

    class ScreenFlashViewHolder(
        view: View,
        val colorPreview: View,
        val colorHexText: TextView,
        val pickButton: MaterialButton
    ) : CustomEditorViewHolder(view) {
        var currentColorHex: String = "#FF0000"
    }

    // ==================== HSV 渐变面板 ====================

    class SVPanelView(context: Context) : View(context) {

        private var currentHue = 0f
        private var currentSat = 1f
        private var currentVal = 1f
        private var dp = 1f

        var onColorChanged: ((sat: Float, value: Float) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
        }
        private val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
        }
        private val clipPath = Path()
        private var cornerRadius = 0f

        fun setHue(hue: Float) {
            currentHue = hue
            invalidate()
        }

        fun setPosition(sat: Float, value: Float) {
            currentSat = sat
            currentVal = value
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            dp = resources.displayMetrics.density
            cornerRadius = 12f * dp
            blackStrokePaint.strokeWidth = 2f * dp
            whiteStrokePaint.strokeWidth = 2f * dp
            clipPath.reset()
            clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.save()
            canvas.clipPath(clipPath)

            // 第1步：白色 → 纯色水平渐变
            val hueColor = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
            paint.shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)

            // 第2步：透明 → 黑色垂直渐变（叠加）
            paint.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)

            canvas.restore()

            // 指示器圆圈（黑白双层描边）
            val cx = currentSat * w
            val cy = (1f - currentVal) * h
            val radius = 6f * dp
            canvas.drawCircle(cx, cy, radius + 1.5f * dp, blackStrokePaint)
            canvas.drawCircle(cx, cy, radius, whiteStrokePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    currentSat = (event.x / width).coerceIn(0f, 1f)
                    currentVal = 1f - (event.y / height).coerceIn(0f, 1f)
                    onColorChanged?.invoke(currentSat, currentVal)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // ==================== 彩虹色条 ====================

    class HueBarView(context: Context) : View(context) {

        private var currentHue = 0f
        var onHueChanged: ((Float) -> Unit)? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
        }
        private val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
        }
        private val clipPath = Path()
        private var cornerRadius = 0f

        private val hueColors = intArrayOf(
            Color.HSVToColor(floatArrayOf(0f, 1f, 1f)),
            Color.HSVToColor(floatArrayOf(60f, 1f, 1f)),
            Color.HSVToColor(floatArrayOf(120f, 1f, 1f)),
            Color.HSVToColor(floatArrayOf(180f, 1f, 1f)),
            Color.HSVToColor(floatArrayOf(240f, 1f, 1f)),
            Color.HSVToColor(floatArrayOf(300f, 1f, 1f)),
            Color.HSVToColor(floatArrayOf(360f, 1f, 1f))
        )

        fun setHue(hue: Float) {
            currentHue = hue
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val dp = resources.displayMetrics.density
            cornerRadius = 12f * dp
            blackStrokePaint.strokeWidth = 4f * dp
            whiteStrokePaint.strokeWidth = 2f * dp
            clipPath.reset()
            clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            canvas.save()
            canvas.clipPath(clipPath)

            val shader = LinearGradient(0f, 0f, 0f, h, hueColors, null, Shader.TileMode.CLAMP)
            paint.shader = shader
            canvas.drawRect(0f, 0f, w, h, paint)

            canvas.restore()

            // 指示线（黑白双层描边）
            val y = currentHue / 360f * h
            canvas.drawLine(0f, y, w, y, blackStrokePaint)
            canvas.drawLine(0f, y, w, y, whiteStrokePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    currentHue = (event.y / height * 360f).coerceIn(0f, 360f)
                    onHueChanged?.invoke(currentHue)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // ==================== 透明度滑块（棋盘格 + 半透明色 + 指示线） ====================

    class AlphaSliderView(context: Context) : View(context) {

        private var alpha = 255
        private var baseColor = Color.RED
        var onAlphaChanged: ((Int) -> Unit)? = null

        private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
        }
        private val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
        }
        private val clipPath = Path()
        private var cornerRadius = 0f
        private var checkerBitmap: Bitmap? = null
        private var colorShader: LinearGradient? = null

        fun setBaseColor(argb: Int) {
            baseColor = Color.rgb(Color.red(argb), Color.green(argb), Color.blue(argb))
            colorShader = null
            invalidate()
        }

        fun setAlpha(a: Int) {
            alpha = a.coerceIn(0, 255)
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val dp = resources.displayMetrics.density
            cornerRadius = 12f * dp
            blackStrokePaint.strokeWidth = 4f * dp
            whiteStrokePaint.strokeWidth = 2f * dp
            clipPath.reset()
            clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
            rebuildCheckerBitmap(w, h)
            colorShader = null
        }

        private fun rebuildCheckerBitmap(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val checkSize = (8 * resources.displayMetrics.density).toInt().coerceAtLeast(4)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paintLight = Paint().apply { color = Color.WHITE }
            val paintDark = Paint().apply { color = Color.parseColor("#CCCCCC") }
            var row = 0
            while (row * checkSize < h) {
                var col = 0
                while (col * checkSize < w) {
                    val p = if ((row + col) % 2 == 0) paintLight else paintDark
                    val left = col * checkSize.toFloat()
                    val top = row * checkSize.toFloat()
                    val right = (left + checkSize).coerceAtMost(w.toFloat())
                    val bottom = (top + checkSize).coerceAtMost(h.toFloat())
                    canvas.drawRect(left, top, right, bottom, p)
                    col++
                }
                row++
            }
            checkerBitmap?.recycle()
            checkerBitmap = bitmap
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.save()
            canvas.clipPath(clipPath)

            // 1. 棋盘格背景
            checkerBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

            // 2. 透明→不透明渐变覆盖
            if (colorShader == null) {
                val r = Color.red(baseColor)
                val g = Color.green(baseColor)
                val b = Color.blue(baseColor)
                colorShader = LinearGradient(0f, 0f, w, 0f,
                    Color.argb(0, r, g, b), Color.argb(255, r, g, b), Shader.TileMode.CLAMP)
            }
            colorPaint.shader = colorShader
            canvas.drawRect(0f, 0f, w, h, colorPaint)
            colorPaint.shader = null

            canvas.restore()

            // 3. 拖拽指示线（黑白双层描边）
            val thumbX = alpha / 255f * w
            canvas.drawLine(thumbX, 0f, thumbX, h, blackStrokePaint)
            canvas.drawLine(thumbX, 0f, thumbX, h, whiteStrokePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    alpha = (event.x / width * 255f).toInt().coerceIn(0, 255)
                    onAlphaChanged?.invoke(alpha)
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            checkerBitmap?.recycle()
            checkerBitmap = null
        }
    }
}
