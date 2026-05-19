package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay
import kotlin.random.Random

// 文件：DelayModule.kt
// 描述：定义了在工作流中暂停执行一段时间的延迟模块。

/**
 * 延迟模块。
 * 用于在工作流执行过程中暂停指定的毫秒数。
 */
class DelayModule : BaseModule() {
    companion object {
        private const val INPUT_DURATION = "duration"
        private const val INPUT_RANDOM_ENABLED = "randomOffsetEnabled"
        private const val INPUT_MAX_OFFSET = "maxOffset"
    }

    // 模块的唯一ID
    override val id = "vflow.device.delay"

    // 模块的元数据
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_device_delay_name,
        descriptionStringRes = R.string.module_vflow_device_delay_desc,
        name = "延迟",                      // Fallback
        description = "暂停工作流一段时间", // Fallback
        iconRes = R.drawable.rounded_avg_time_24,
        category = "应用与系统",
        categoryId = "device"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.LOW,
        directToolDescription = "Pause workflow execution for a number of milliseconds.",
        workflowStepDescription = "Pause for a number of milliseconds.",
        inputHints = mapOf(
            "duration" to "Delay duration in milliseconds. Use positive integers.",
            "randomOffsetEnabled" to "Enable a random offset around the base duration.",
            "maxOffset" to "Maximum random offset in milliseconds. Final delay is clamped to zero or above.",
        ),
        requiredInputIds = setOf("duration"),
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = INPUT_DURATION,
            name = "延迟时间",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 1000L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            nameStringRes = R.string.param_vflow_device_delay_duration_name
        ),
        InputDefinition(
            id = INPUT_RANDOM_ENABLED,
            name = "随机延迟",  // Fallback
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            inputStyle = InputStyle.SWITCH,
            nameStringRes = R.string.param_vflow_device_delay_random_enabled_name
        ),
        InputDefinition(
            id = INPUT_MAX_OFFSET,
            name = "最大偏移量",  // Fallback
            staticType = ParameterType.NUMBER,
            defaultValue = 0L,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            visibility = InputVisibility.whenTrue(INPUT_RANDOM_ENABLED),
            nameStringRes = R.string.param_vflow_device_delay_max_offset_name
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_device_delay_success_name
        )
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如："延迟 [1000] 毫秒"
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val durationPill = PillUtil.createPillFromParam(
            step.parameters[INPUT_DURATION],
            inputs.find { it.id == INPUT_DURATION }
        )
        val randomEnabled = step.parameters[INPUT_RANDOM_ENABLED] as? Boolean ?: false
        val maxOffsetPill = if (randomEnabled) {
            PillUtil.createPillFromParam(
                step.parameters[INPUT_MAX_OFFSET],
                inputs.find { it.id == INPUT_MAX_OFFSET }
            )
        } else {
            null
        }

        val summaryPrefix = context.getString(R.string.summary_vflow_device_delay_prefix)
        val summarySuffix = context.getString(R.string.summary_vflow_device_delay_suffix)
        return if (randomEnabled && maxOffsetPill != null) {
            PillUtil.buildSpannable(
                context,
                "$summaryPrefix ",
                durationPill,
                " $summarySuffix ",
                context.getString(R.string.summary_vflow_device_delay_random_prefix),
                " ",
                maxOffsetPill,
                " $summarySuffix"
            )
        } else {
            PillUtil.buildSpannable(
                context,
                "$summaryPrefix ",
                durationPill,
                " $summarySuffix"
            )
        }
    }

    /**
     * 验证模块参数的有效性。
     * 确保延迟时间不为负数。
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        validateNonNegativeNumberInput(step.parameters[INPUT_DURATION])?.let { return it }

        val randomEnabled = step.parameters[INPUT_RANDOM_ENABLED] as? Boolean ?: false
        if (randomEnabled) {
            validateNonNegativeNumberInput(step.parameters[INPUT_MAX_OFFSET])?.let { return it }
        }
        return ValidationResult(true)
    }

    /**
     * 执行延迟操作的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取延迟时间
        val duration = context.getVariableAsLong(INPUT_DURATION)

        if (duration == null) {
            val rawValue = context.getVariable(INPUT_DURATION)
            val rawValueStr = when (rawValue) {
                is VString -> rawValue.raw
                is VNull -> "空值"
                is VNumber -> rawValue.raw.toString()
                else -> rawValue.toString()
            }
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_delay_parameter_error),
                "无法将 '$rawValueStr' 解析为有效的延迟时间。"
            )
        }

        // 检查延迟时间是否为负
        if (duration < 0) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_delay_parameter_error),
                appContext.getString(R.string.error_vflow_device_delay_negative)
            )
        }

        val randomEnabled = context.getVariableAsBoolean(INPUT_RANDOM_ENABLED) ?: false
        val maxOffset = if (randomEnabled) {
            context.getVariableAsLong(INPUT_MAX_OFFSET) ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_delay_parameter_error),
                appContext.getString(R.string.error_vflow_device_delay_invalid_max_offset)
            )
        } else {
            0L
        }

        if (maxOffset < 0) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_device_delay_parameter_error),
                appContext.getString(R.string.error_vflow_device_delay_negative)
            )
        }

        val finalDuration = if (randomEnabled && maxOffset > 0) {
            (duration + Random.nextLong(from = -maxOffset, until = maxOffset + 1)).coerceAtLeast(0L)
        } else {
            duration
        }

        // 如果延迟时间大于0，则执行实际的协程延迟
        if (finalDuration > 0) {
            onProgress(ProgressUpdate(String.format(appContext.getString(R.string.msg_vflow_device_delay_delaying), finalDuration)))
            delay(finalDuration)
        }
        // 返回成功结果
        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }

    private fun validateNonNegativeNumberInput(value: Any?): ValidationResult? {
        if (value is String) {
            try {
                if (!value.isMagicVariable() && !value.isNamedVariable()) {
                    if (value.toLong() < 0) {
                        return ValidationResult(false, delayNegativeValidationText())
                    }
                }
            } catch (_: Exception) {
                if (!value.isMagicVariable() && !value.isNamedVariable()) {
                    return ValidationResult(false, delayInvalidFormatText())
                }
            }
        } else if (value is Number && value.toLong() < 0) {
            return ValidationResult(false, delayNegativeValidationText())
        }
        return null
    }

    private fun delayNegativeValidationText(): String = getStringOrFallback(
        R.string.error_vflow_device_delay_negative_validation,
        "延迟时间不能为负数"
    )

    private fun delayInvalidFormatText(): String = getStringOrFallback(
        R.string.error_vflow_device_delay_invalid_format,
        "无效的数字格式"
    )

    private fun getStringOrFallback(resId: Int, fallback: String): String {
        return runCatching { appContext.getString(resId) }.getOrDefault(fallback)
    }
}
