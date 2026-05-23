package com.chaomixian.vflow.core.workflow.module.integration

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class ColorOSShortcutsModule : BaseAppShortcutModule() {
    companion object {
        private const val ACTION_MEMORY = "memory"
        private const val ACTION_ASSISTANT = "assistant"
        private const val ACTION_RECORD = "record"
    }

    override val id = "vflow.shizuku.coloros_shortcuts"
    override val metadata = ActionMetadata(
        name = "ColorOS",
        nameStringRes = R.string.module_vflow_shizuku_coloros_shortcuts_name,
        description = "执行ColorOS系统相关的一些快捷操作。",
        descriptionStringRes = R.string.module_vflow_shizuku_coloros_shortcuts_desc,
        iconRes = R.drawable.rounded_adb_24,
        category = "应用集成",
        categoryId = ModuleCategories.APP_INTEGRATION
    )

    private val actions = mapOf(
        ACTION_MEMORY to "am start-foreground-service -p \"com.coloros.colordirectservice\" --ei \"triggerType\" 1",
        ACTION_ASSISTANT to "am start -n com.heytap.speechassist/com.heytap.speechassist.business.lockscreen.FloatSpeechActivity",
        ACTION_RECORD to "am start -n com.coloros.soundrecorder/oplus.multimedia.soundrecorder.slidebar.TransparentActivity"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "action",
            "操作",
            ParameterType.ENUM,
            ACTION_MEMORY,
            options = actions.keys.toList(),
            optionsStringRes = listOf(
                R.string.option_vflow_shizuku_coloros_shortcuts_action_memory,
                R.string.option_vflow_shizuku_coloros_shortcuts_action_assistant,
                R.string.option_vflow_shizuku_coloros_shortcuts_action_record
            ),
            legacyValueMap = mapOf(
                "小布记忆" to ACTION_MEMORY,
                "Xiaobu Memory" to ACTION_MEMORY,
                "小布助手" to ACTION_ASSISTANT,
                "Xiaobu Assistant" to ACTION_ASSISTANT,
                "开始录音" to ACTION_RECORD,
                "开始录制" to ACTION_RECORD,
                "Start Recording" to ACTION_RECORD
            ),
            nameStringRes = R.string.param_vflow_shizuku_coloros_shortcuts_action_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_coloros), actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val actionInput = getInputs().first { it.id == "action" }
        val rawAction = context.getVariableAsString("action", "")
        val action = actionInput.normalizeEnumValue(rawAction) ?: rawAction
        if (action.isEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_operit_param_error),
                appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_no_action)
            )
        }
        val command = actions[action] ?: return ExecutionResult.Failure(
            appContext.getString(R.string.error_vflow_interaction_operit_param_error),
            appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_invalid)
        )
        return executeCommand(context, command, onProgress)
    }
}
