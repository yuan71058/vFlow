// 文件: main/java/com/chaomixian/vflow/core/workflow/module/core/CoreVolumeModuleUIProvider.kt
package com.chaomixian.vflow.core.workflow.module.core

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.services.VFlowCoreBridge
import com.chaomixian.vflow.ui.workflow_editor.PillRenderer
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音量控制模块的自定义 UI 提供者。
 * 提供调音台式的界面，使用 Material 3 Segmented Buttons 支持同时控制多个音频流。
 */
class CoreVolumeModuleUIProvider : ModuleUIProvider {

    // 音频流的最大音量（实际值）
    private val streamMaxVolumes = mapOf(
        "music" to 160,
        "notification" to 16,
        "ring" to 16,
        "system" to 16,
        "alarm" to 16
    )

    /**
     * 将实际音量值转换为百分比（0-100）
     */
    private fun actualVolumeToPercent(stream: String, actualVolume: Int): Int {
        val maxVolume = streamMaxVolumes[stream] ?: 100
        return if (maxVolume > 0) (actualVolume * 100 / maxVolume) else 0
    }

    data class StreamConfig(
        val streamType: Int,
        val streamName: String,
        val btnKeep: MaterialButton,
        val btnSet: MaterialButton,
        val btnMute: MaterialButton,
        val btnUnmute: MaterialButton,
        val sliderContainer: View,
        val slider: Slider,
        val valueText: TextView,
        val currentText: TextView
    )

    class ViewHolder(view: View) : CustomEditorViewHolder(view) {
        val musicConfig: StreamConfig
        val notificationConfig: StreamConfig
        val ringConfig: StreamConfig
        val systemConfig: StreamConfig
        val alarmConfig: StreamConfig
        val refreshButton: Button
        var onMagicVariableRequested: ((String) -> Unit)? = null
        var currentParameters: Map<String, Any?> = emptyMap()

        init {
            musicConfig = StreamConfig(
                streamType = 3, streamName = "music",
                btnKeep = view.findViewById(R.id.btn_music_keep),
                btnSet = view.findViewById(R.id.btn_music_set),
                btnMute = view.findViewById(R.id.btn_music_mute),
                btnUnmute = view.findViewById(R.id.btn_music_unmute),
                sliderContainer = view.findViewById(R.id.container_music_slider),
                slider = view.findViewById(R.id.slider_music),
                valueText = view.findViewById(R.id.tv_music_value),
                currentText = view.findViewById(R.id.tv_music_current)
            )
            notificationConfig = StreamConfig(
                streamType = 5, streamName = "notification",
                btnKeep = view.findViewById(R.id.btn_notification_keep),
                btnSet = view.findViewById(R.id.btn_notification_set),
                btnMute = view.findViewById(R.id.btn_notification_mute),
                btnUnmute = view.findViewById(R.id.btn_notification_unmute),
                sliderContainer = view.findViewById(R.id.container_notification_slider),
                slider = view.findViewById(R.id.slider_notification),
                valueText = view.findViewById(R.id.tv_notification_value),
                currentText = view.findViewById(R.id.tv_notification_current)
            )
            ringConfig = StreamConfig(
                streamType = 2, streamName = "ring",
                btnKeep = view.findViewById(R.id.btn_ring_keep),
                btnSet = view.findViewById(R.id.btn_ring_set),
                btnMute = view.findViewById(R.id.btn_ring_mute),
                btnUnmute = view.findViewById(R.id.btn_ring_unmute),
                sliderContainer = view.findViewById(R.id.container_ring_slider),
                slider = view.findViewById(R.id.slider_ring),
                valueText = view.findViewById(R.id.tv_ring_value),
                currentText = view.findViewById(R.id.tv_ring_current)
            )
            systemConfig = StreamConfig(
                streamType = 1, streamName = "system",
                btnKeep = view.findViewById(R.id.btn_system_keep),
                btnSet = view.findViewById(R.id.btn_system_set),
                btnMute = view.findViewById(R.id.btn_system_mute),
                btnUnmute = view.findViewById(R.id.btn_system_unmute),
                sliderContainer = view.findViewById(R.id.container_system_slider),
                slider = view.findViewById(R.id.slider_system),
                valueText = view.findViewById(R.id.tv_system_value),
                currentText = view.findViewById(R.id.tv_system_current)
            )
            alarmConfig = StreamConfig(
                streamType = 4, streamName = "alarm",
                btnKeep = view.findViewById(R.id.btn_alarm_keep),
                btnSet = view.findViewById(R.id.btn_alarm_set),
                btnMute = view.findViewById(R.id.btn_alarm_mute),
                btnUnmute = view.findViewById(R.id.btn_alarm_unmute),
                sliderContainer = view.findViewById(R.id.container_alarm_slider),
                slider = view.findViewById(R.id.slider_alarm),
                valueText = view.findViewById(R.id.tv_alarm_value),
                currentText = view.findViewById(R.id.tv_alarm_current)
            )
            refreshButton = view.findViewById(R.id.btn_refresh_volumes)
        }
    }

    override fun getHandledInputIds(): Set<String> = setOf(
        "music_action", "music_value",
        "notification_action", "notification_value",
        "ring_action", "ring_value",
        "system_action", "system_value",
        "alarm_action", "alarm_value"
    )

    override fun createPreview(
        context: Context, parent: ViewGroup, step: ActionStep, allSteps: List<ActionStep>,
        onStartActivityForResult: ((android.content.Intent, (resultCode: Int, data: android.content.Intent?) -> Unit) -> Unit)?
    ): View? = null

    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((String) -> Unit)?,
        allSteps: List<ActionStep>?,
        onStartActivityForResult: ((android.content.Intent, (Int, android.content.Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.partial_volume_editor, parent, false)
        val holder = ViewHolder(view)
        holder.onMagicVariableRequested = onMagicVariableRequested
        holder.currentParameters = currentParameters
        val inputsById = CoreVolumeModule().getInputs().associateBy { it.id }
        val dp = context.resources.displayMetrics.density

        val configs = listOf(
            holder.musicConfig,
            holder.notificationConfig,
            holder.ringConfig,
            holder.systemConfig,
            holder.alarmConfig
        )

        // 为每个音频流设置监听器
        for (config in configs) {
            val paramActionId = "${config.streamName}_action"
            val paramValueId = "${config.streamName}_value"
            val inputDef = inputsById[paramValueId]

            // 恢复操作类型
            val rawAction = currentParameters[paramActionId] as? String ?: CoreVolumeModule.ACTION_KEEP
            val action = inputsById[paramActionId]?.normalizeEnumValue(rawAction) ?: rawAction
            when (action) {
                CoreVolumeModule.ACTION_SET -> config.btnSet.isChecked = true
                CoreVolumeModule.ACTION_MUTE -> config.btnMute.isChecked = true
                CoreVolumeModule.ACTION_UNMUTE -> config.btnUnmute.isChecked = true
                else -> config.btnKeep.isChecked = true
            }

            // 检查是否为魔法变量/命名变量引用
            val rawValue = currentParameters[paramValueId]?.toString()
            val isVariable = rawValue.isMagicVariable() || rawValue.isNamedVariable()

            // 重建滑块容器：水平布局 [valueArea(weight=1)] [magicButton(右侧)]
            val container = config.sliderContainer as LinearLayout
            container.removeAllViews()
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL

            // valueArea：左侧，占据剩余空间
            val valueArea = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(valueArea)

            // 魔法变量按钮：右侧，固定宽度，样式与标准编辑器一致
            if (inputDef?.acceptsMagicVariable == true) {
                val magicBtn = ImageButton(context).apply {
                    setImageResource(R.drawable.rounded_dataset_24)
                    val tv = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
                    setBackgroundResource(tv.resourceId)
                    setColorFilter(com.google.android.material.color.MaterialColors.getColor(this, android.R.attr.colorPrimary))
                    layoutParams = LinearLayout.LayoutParams(
                        (48 * dp).toInt(), (48 * dp).toInt()
                    )
                    setOnClickListener { onMagicVariableRequested?.invoke(paramValueId) }
                }
                container.addView(magicBtn)
            }

            if (isVariable) {
                // 变量模式：药丸显示变量名称，点击可重新选择
                val pill = LayoutInflater.from(context).inflate(R.layout.magic_variable_pill, valueArea, false)
                val displayName = PillRenderer.resolveDisplayName(context, rawValue!!, allSteps ?: emptyList())
                pill.findViewById<TextView>(R.id.pill_text).text = displayName
                pill.setOnClickListener { onMagicVariableRequested?.invoke(paramValueId) }
                valueArea.addView(pill)
            } else {
                // 静态模式：Slider + 数值标签
                val slider = Slider(context).apply {
                    valueFrom = 0f; valueTo = 100f; stepSize = 1f
                    value = ((currentParameters[paramValueId] as? Number)?.toInt() ?: 50).toFloat()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val valueLabel = TextView(context).apply {
                    text = slider.value.toInt().toString()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER }
                }
                valueArea.addView(slider)
                valueArea.addView(valueLabel)

                slider.addOnChangeListener { _, value, _ ->
                    valueLabel.text = value.toInt().toString()
                    onParametersChanged()
                }
            }

            // 初始化滑块显示状态
            updateSliderVisibility(config)

            // 监听按钮组变化
            val toggleGroup = config.btnKeep.parent as MaterialButtonToggleGroup
            toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    config.sliderContainer.isVisible = (checkedId == config.btnSet.id)
                    onParametersChanged()
                }
            }
        }

        // 刷新按钮：读取当前音量
        holder.refreshButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val volumes = withContext(Dispatchers.IO) {
                        VFlowCoreBridge.getAllVolumes()
                    }
                    if (volumes != null) {
                        // 将实际音量转换为百分比显示
                        val musicPercent = actualVolumeToPercent("music", volumes.musicCurrent)
                        holder.musicConfig.currentText.text = "${musicPercent}%"

                        val notificationPercent = actualVolumeToPercent("notification", volumes.notificationCurrent)
                        holder.notificationConfig.currentText.text = "${notificationPercent}%"

                        val ringPercent = actualVolumeToPercent("ring", volumes.ringCurrent)
                        holder.ringConfig.currentText.text = "${ringPercent}%"

                        val systemPercent = actualVolumeToPercent("system", volumes.systemCurrent)
                        holder.systemConfig.currentText.text = "${systemPercent}%"

                        val alarmPercent = actualVolumeToPercent("alarm", volumes.alarmCurrent)
                        holder.alarmConfig.currentText.text = "${alarmPercent}%"
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
        }

        // 自动加载一次当前音量
        holder.refreshButton.performClick()

        return holder
    }

    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        val h = holder as ViewHolder
        val params = mutableMapOf<String, Any?>()

        val configs = listOf(
            h.musicConfig,
            h.notificationConfig,
            h.ringConfig,
            h.systemConfig,
            h.alarmConfig
        )

        for (config in configs) {
            val streamName = config.streamName
            val actionParamId = "${streamName}_action"
            val valueParamId = "${streamName}_value"

            val action = when {
                config.btnSet.isChecked -> CoreVolumeModule.ACTION_SET
                config.btnMute.isChecked -> CoreVolumeModule.ACTION_MUTE
                config.btnUnmute.isChecked -> CoreVolumeModule.ACTION_UNMUTE
                else -> CoreVolumeModule.ACTION_KEEP
            }

            params[actionParamId] = action

            // 根据 valueArea 内容读取值
            val container = config.sliderContainer as LinearLayout
            // 子 View 结构: [valueArea, magicButton] — valueArea 是第一个子 View
            val valueArea = container.getChildAt(0) as? LinearLayout
            val firstChild = valueArea?.getChildAt(0)
            when (firstChild) {
                is Slider -> params[valueParamId] = firstChild.value.toInt()
                is LinearLayout -> {
                    // 药丸模式：保持原始变量引用
                    val original = h.currentParameters[valueParamId]
                    if (original != null) params[valueParamId] = original
                }
            }
        }

        return params
    }

    private fun updateSliderVisibility(config: StreamConfig) {
        config.sliderContainer.isVisible = config.btnSet.isChecked
    }

    private fun getStreamName(streamType: Int): String {
        return when (streamType) {
            3 -> "music"
            5 -> "notification"
            2 -> "ring"
            1 -> "system"
            4 -> "alarm"
            else -> "unknown"
        }
    }
}
