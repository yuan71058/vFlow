package com.chaomixian.vflow.core.workflow.module.integration

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class AlipayShortcutsModule : BaseAppShortcutModule() {
    companion object {
        private const val ACTION_SCAN = "scan"
        private const val ACTION_PAY = "pay"
        private const val ACTION_RECEIVE = "receive"
    }

    override val id = "vflow.shizuku.alipay_shortcuts"
    override val metadata = ActionMetadata(
        name = "支付宝",
        nameStringRes = R.string.module_vflow_shizuku_alipay_shortcuts_name,
        description = "快速打开支付宝的扫一扫、付款码、收款码等。",
        descriptionStringRes = R.string.module_vflow_shizuku_alipay_shortcuts_desc,
        iconRes = R.drawable.ic_alipay,
        category = "应用集成",
        categoryId = ModuleCategories.APP_INTEGRATION
    )

    private val actions = mapOf(
        ACTION_SCAN to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=10000007",
        ACTION_PAY to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=20000056",
        ACTION_RECEIVE to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=20000123"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "action",
            "操作",
            ParameterType.ENUM,
            ACTION_SCAN,
            options = actions.keys.toList(),
            optionsStringRes = listOf(
                R.string.option_vflow_shizuku_alipay_shortcuts_action_scan,
                R.string.option_vflow_shizuku_alipay_shortcuts_action_pay,
                R.string.option_vflow_shizuku_alipay_shortcuts_action_receive
            ),
            legacyValueMap = mapOf(
                "扫一扫" to ACTION_SCAN,
                "Scan" to ACTION_SCAN,
                "付款码" to ACTION_PAY,
                "Payment Code" to ACTION_PAY,
                "收款码" to ACTION_RECEIVE,
                "Collection Code" to ACTION_RECEIVE
            ),
            nameStringRes = R.string.param_vflow_shizuku_alipay_shortcuts_action_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_alipay), actionPill)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (com.chaomixian.vflow.core.module.ProgressUpdate) -> Unit
    ): ExecutionResult {
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
