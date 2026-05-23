package com.chaomixian.vflow.core.workflow.module.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class ClashMetaModule : BaseModule() {
    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_TOGGLE = "toggle"

        private const val PACKAGE_NAME = "com.github.metacubex.clash.meta"
        private const val CONTROL_ACTIVITY = "com.github.kr328.clash.ExternalControlActivity"
        private const val START_ACTION = "com.github.metacubex.clash.meta.action.START_CLASH"
        private const val STOP_ACTION = "com.github.metacubex.clash.meta.action.STOP_CLASH"
        private const val TOGGLE_ACTION = "com.github.metacubex.clash.meta.action.TOGGLE_CLASH"
    }

    override val id = "vflow.integration.clash_meta"
    override val metadata = ActionMetadata(
        name = "Clash Meta",
        nameStringRes = R.string.module_vflow_integration_clash_meta_name,
        description = "通过 Clash Meta for Android 的控制 Intent 启动、停止或切换代理状态。",
        descriptionStringRes = R.string.module_vflow_integration_clash_meta_desc,
        iconRes = R.drawable.ic_clash_meta,
        category = "应用集成",
        categoryId = ModuleCategories.APP_INTEGRATION
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Control Clash Meta for Android by sending its start, stop, or toggle control intent.",
        workflowStepDescription = "Send a Clash Meta for Android control intent.",
        inputHints = mapOf(
            "action" to "Canonical values are start, stop, or toggle.",
        ),
        requiredInputIds = setOf("action"),
    )

    private val actionOptions = listOf(ACTION_START, ACTION_STOP, ACTION_TOGGLE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "action",
            name = "操作",
            nameStringRes = R.string.param_vflow_integration_clash_meta_action_name,
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_TOGGLE,
            options = actionOptions,
            optionsStringRes = listOf(
                R.string.option_vflow_integration_clash_meta_action_start,
                R.string.option_vflow_integration_clash_meta_action_stop,
                R.string.option_vflow_integration_clash_meta_action_toggle
            ),
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_integration_clash_meta_success_name),
        OutputDefinition("action", "Action", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_integration_clash_meta_action_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_integration_clash_meta_prefix),
            actionPill
        )
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val actionInput = getInputs().first { it.id == "action" }
        val rawAction = step.parameters["action"] as? String ?: ACTION_TOGGLE
        val action = actionInput.normalizeEnumValue(rawAction) ?: rawAction
        return if (action in actionOptions) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "无效的 Clash Meta 操作: $action")
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val actionInput = getInputs().first { it.id == "action" }
        val rawAction = context.getVariableAsString("action", ACTION_TOGGLE)
        val action = actionInput.normalizeEnumValue(rawAction) ?: rawAction
        val intentAction = when (action) {
            ACTION_START -> START_ACTION
            ACTION_STOP -> STOP_ACTION
            ACTION_TOGGLE -> TOGGLE_ACTION
            else -> return ExecutionResult.Failure("参数错误", "无效的 Clash Meta 操作: $action")
        }

        return try {
            onProgress(ProgressUpdate("正在发送 Clash Meta $action 控制 Intent..."))
            val intent = Intent(intentAction)
                .setComponent(ComponentName(PACKAGE_NAME, CONTROL_ACTIVITY))
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.applicationContext.startActivity(intent)
            ExecutionResult.Success(
                mapOf(
                    "success" to VBoolean(true),
                    "action" to VString(intentAction)
                )
            )
        } catch (e: Exception) {
            ExecutionResult.Failure("Clash Meta 调用失败", "发送控制 Intent 失败: ${e.message}")
        }
    }
}
