package com.chaomixian.vflow.core.workflow.module.ui.components

import android.content.Context
import android.graphics.Color
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.overlay.ScreenFlashOverlay
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 屏幕闪烁模块。
 * 触发时在屏幕上以全屏覆盖层的形式淡入淡出选定的颜色，用于视觉提示。
 */
class ScreenFlashModule : BaseModule() {

    override val id = "vflow.ui.screen_flash"

    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_ui_screen_flash_name,
        descriptionStringRes = R.string.module_vflow_ui_screen_flash_desc,
        name = "屏幕闪烁",
        description = "在屏幕上显示一个全屏颜色闪烁效果，用于视觉提示。",
        iconRes = R.drawable.ic_screen_flash_24,
        category = "UI 组件",
        categoryId = "ui"
    )

    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    override val uiProvider: ModuleUIProvider = ScreenFlashUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "color",
            name = "颜色",
            staticType = ParameterType.STRING,
            defaultValue = "#FFFF0000",
            acceptsMagicVariable = true,
            acceptsNamedVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            nameStringRes = R.string.param_vflow_ui_screen_flash_color_name
        ),
        InputDefinition(
            id = "fade_in_duration",
            name = "淡入时长(ms)",
            staticType = ParameterType.NUMBER,
            defaultValue = 300.0,
            isFolded = true,
            nameStringRes = R.string.param_vflow_ui_screen_flash_fade_in_name
        ),
        InputDefinition(
            id = "hold_duration",
            name = "持续时长(ms)",
            staticType = ParameterType.NUMBER,
            defaultValue = 500.0,
            isFolded = true,
            nameStringRes = R.string.param_vflow_ui_screen_flash_hold_name
        ),
        InputDefinition(
            id = "fade_out_duration",
            name = "淡出时长(ms)",
            staticType = ParameterType.NUMBER,
            defaultValue = 300.0,
            isFolded = true,
            nameStringRes = R.string.param_vflow_ui_screen_flash_fade_out_name
        ),
        InputDefinition(
            id = "flash_count",
            name = "闪烁次数",
            staticType = ParameterType.NUMBER,
            defaultValue = 1.0,
            nameStringRes = R.string.param_vflow_ui_screen_flash_flash_count_name
        ),
        InputDefinition(
            id = "flash_interval",
            name = "闪烁间隔(ms)",
            staticType = ParameterType.NUMBER,
            defaultValue = 200.0,
            isFolded = true,
            nameStringRes = R.string.param_vflow_ui_screen_flash_interval_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_ui_screen_flash_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val color = step.parameters["color"]?.toString() ?: "#FF0000"
        val colorPill = PillUtil.createPillFromParam(color, getInputs().find { it.id == "color" })
        val count = (step.parameters["flash_count"] as? Number)?.toInt() ?: 1
        val countText = if (count > 1) " ×$count" else ""
        return PillUtil.buildSpannable(context, "屏幕闪烁$countText: ", colorPill)
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val color = step.parameters["color"]?.toString()
        if (!color.isNullOrBlank() && !color.matches(Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$"))) {
            return ValidationResult(
                isValid = false,
                errorMessage = appContext.getString(R.string.error_vflow_ui_screen_flash_invalid_color)
            )
        }
        val flashCount = (step.parameters["flash_count"] as? Number)?.toDouble()
        if (flashCount != null && flashCount < 1) {
            return ValidationResult(
                isValid = false,
                errorMessage = appContext.getString(R.string.error_vflow_ui_screen_flash_invalid_flash_count)
            )
        }
        return ValidationResult(isValid = true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val colorStr = context.getVariable("color").asString() ?: "#FF0000"
        val fadeIn = (context.getVariable("fade_in_duration").asNumber() ?: 300.0).toLong()
        val hold = (context.getVariable("hold_duration").asNumber() ?: 500.0).toLong()
        val fadeOut = (context.getVariable("fade_out_duration").asNumber() ?: 300.0).toLong()
        val flashCount = (context.getVariable("flash_count").asNumber() ?: 1.0).toInt().coerceAtLeast(1)
        val flashInterval = (context.getVariable("flash_interval").asNumber() ?: 0.0).toLong().coerceAtLeast(0)

        val colorInt = try {
            Color.parseColor(colorStr)
        } catch (_: IllegalArgumentException) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_ui_screen_flash_invalid_color),
                "无法解析颜色值: $colorStr"
            )
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_ui_screen_flash_showing)))

        val overlay = ScreenFlashOverlay(context.applicationContext)
        overlay.show(colorInt, fadeIn, hold, fadeOut, flashCount, flashInterval)

        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}
