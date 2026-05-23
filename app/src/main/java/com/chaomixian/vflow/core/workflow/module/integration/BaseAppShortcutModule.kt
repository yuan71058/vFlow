package com.chaomixian.vflow.core.workflow.module.integration

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager

abstract class BaseAppShortcutModule : BaseModule() {
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    protected suspend fun executeCommand(
        context: ExecutionContext,
        command: String,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_shizuku_alipay_shortcuts_executing)))
        val result = ShellManager.execShellCommand(context.applicationContext, command, ShellManager.ShellMode.AUTO)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure(appContext.getString(R.string.error_vflow_shizuku_shell_command_failed), result)
        } else {
            ExecutionResult.Success(mapOf("result" to VString(result)))
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "result",
            "命令输出",
            VTypeRegistry.STRING.id,
            nameStringRes = R.string.output_vflow_shizuku_alipay_shortcuts_result_name
        )
    )
}
