package com.chaomixian.vflow.core.workflow.module.integration

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class WeChatShortcutsModule : BaseAppShortcutModule() {
    companion object {
        private const val ACTION_PAY = "pay"
        private const val ACTION_SCAN = "scan"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_LAUNCHER_ACTIVITY = "com.tencent.mm.ui.LauncherUI"
        private const val WECHAT_SHORTCUT_ACTION = "com.tencent.mm.ui.ShortCutDispatchAction"
        private const val WECHAT_SHORTCUT_TYPE_EXTRA = "LauncherUI.Shortcut.LaunchType"
        private const val LAUNCH_TYPE_OFFLINE_WALLET = "launch_type_offline_wallet"
        private const val LAUNCH_TYPE_SCAN_QRCODE = "launch_type_scan_qrcode"
    }

    override val id = "vflow.shizuku.wechat_shortcuts"
    override val metadata = ActionMetadata(
        name = "微信",
        nameStringRes = R.string.module_vflow_shizuku_wechat_shortcuts_name,
        description = "快速打开微信的扫一扫、付款码等。",
        descriptionStringRes = R.string.module_vflow_shizuku_wechat_shortcuts_desc,
        iconRes = R.drawable.ic_wechat,
        category = "应用集成",
        categoryId = ModuleCategories.APP_INTEGRATION
    )

    private val launchTypes = mapOf(
        ACTION_PAY to LAUNCH_TYPE_OFFLINE_WALLET,
        ACTION_SCAN to LAUNCH_TYPE_SCAN_QRCODE
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> = emptyList()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "action",
            "操作",
            ParameterType.ENUM,
            ACTION_PAY,
            options = launchTypes.keys.toList(),
            optionsStringRes = listOf(
                R.string.option_vflow_shizuku_wechat_shortcuts_action_pay,
                R.string.option_vflow_shizuku_wechat_shortcuts_action_scan
            ),
            legacyValueMap = mapOf(
                "微信支付" to ACTION_PAY,
                "付款码" to ACTION_PAY,
                "Payment Code" to ACTION_PAY,
                "扫一扫" to ACTION_SCAN,
                "Scan" to ACTION_SCAN
            ),
            nameStringRes = R.string.param_vflow_shizuku_wechat_shortcuts_action_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(
            step.parameters["action"],
            getInputs().find { it.id == "action" },
            isModuleOption = true
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_wechat), actionPill)
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
        val launchType = launchTypes[action] ?: return ExecutionResult.Failure(
            appContext.getString(R.string.error_vflow_interaction_operit_param_error),
            appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_invalid)
        )

        return try {
            onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_shizuku_alipay_shortcuts_executing)))
            val intent = Intent(WECHAT_SHORTCUT_ACTION).apply {
                setClassName(WECHAT_PACKAGE, WECHAT_LAUNCHER_ACTIVITY)
                putExtra(WECHAT_SHORTCUT_TYPE_EXTRA, launchType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.applicationContext.startActivity(intent)
            ExecutionResult.Success(mapOf("result" to VString("OK")))
        } catch (e: Exception) {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_shizuku_shell_command_failed),
                e.message ?: e.javaClass.simpleName
            )
        }
    }
}
