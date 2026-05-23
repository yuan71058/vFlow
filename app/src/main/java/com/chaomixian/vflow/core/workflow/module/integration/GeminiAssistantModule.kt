package com.chaomixian.vflow.core.workflow.module.integration

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.workflow.model.ActionStep

class GeminiAssistantModule : BaseAppShortcutModule() {
    override val id = "vflow.shizuku.gemini_shortcut"
    override val metadata = ActionMetadata(
        name = "Gemini 助手",
        nameStringRes = R.string.module_vflow_shizuku_gemini_shortcut_name,
        description = "启动 Google Gemini 语音助理。",
        descriptionStringRes = R.string.module_vflow_shizuku_gemini_shortcut_desc,
        iconRes = R.drawable.ic_gemini,
        category = "应用集成",
        categoryId = ModuleCategories.APP_INTEGRATION
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return context.getString(R.string.summary_vflow_shizuku_gemini)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val command = "am start -a android.intent.action.VOICE_COMMAND -p com.google.android.googlequicksearchbox"
        return executeCommand(context, command, onProgress)
    }
}
