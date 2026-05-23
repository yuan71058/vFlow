package com.chaomixian.vflow.core.workflow.module.integration

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.AiModuleRiskLevel
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.ModuleCategories
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.directToolMetadata
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.AccessibilityService as VFlowAccessibilityService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class IThomeCheckInModule : BaseModule() {
    companion object {
        private const val PACKAGE_NAME = "com.ruanmei.ithome"
        private const val MAIN_ACTIVITY = "com.ruanmei.ithome.ui.MainActivity"
        private const val BOTTOM_ME_ID = "com.ruanmei.ithome:id/bottom_me"
        private const val CHECK_IN_ID = "com.ruanmei.ithome:id/ll_me_checkIn"
        private const val WAIT_TIMEOUT_MS = 8_000L
        private const val WAIT_INTERVAL_MS = 300L
    }

    override val id = "vflow.integration.ithome_check_in"
    override val metadata = ActionMetadata(
        name = "IT之家签到",
        nameStringRes = R.string.module_vflow_integration_ithome_check_in_name,
        description = "打开 IT之家并完成每日签到。",
        descriptionStringRes = R.string.module_vflow_integration_ithome_check_in_desc,
        iconRes = R.drawable.ic_ithome,
        category = "应用集成",
        categoryId = ModuleCategories.APP_INTEGRATION
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Launch IT Home, then tap the Me tab and daily check-in button.",
        workflowStepDescription = "Complete IT Home daily check-in.",
        requiredInputIds = emptySet(),
    )

    override fun getRequiredPermissions(step: ActionStep?): List<com.chaomixian.vflow.permissions.Permission> {
        return listOf(PermissionManager.ACCESSIBILITY)
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_integration_ithome_check_in_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_integration_ithome_check_in)
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val service = context.services.get(VFlowAccessibilityService::class)
            ?: return ExecutionResult.Failure("无障碍服务未运行", "IT之家签到需要无障碍服务点击页面控件。")

        onProgress(ProgressUpdate("正在启动 IT之家..."))
        val launchResult = launchIThome(context.applicationContext)
        if (launchResult != null) return launchResult

        var checkInClicked = waitAndClickByViewId(
            service = service,
            viewId = CHECK_IN_ID,
            label = "签到",
            timeoutMs = 600L,
            onProgress = onProgress
        )

        if (!checkInClicked) {
            val meClicked = waitAndClickByViewId(service, BOTTOM_ME_ID, "我的", onProgress = onProgress)
            if (!meClicked) {
                return ExecutionResult.Failure("点击失败", "未找到 IT之家底部“我的”入口。")
            }
            checkInClicked = waitAndClickByViewId(service, CHECK_IN_ID, "签到", onProgress = onProgress)
        }

        if (!checkInClicked) {
            return ExecutionResult.Failure("点击失败", "未找到 IT之家签到入口。")
        }

        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }

    private fun launchIThome(appContext: Context): ExecutionResult? {
        return try {
            val intent = Intent()
                .setComponent(ComponentName(PACKAGE_NAME, MAIN_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            appContext.startActivity(intent)
            null
        } catch (e: Exception) {
            ExecutionResult.Failure("启动 IT之家失败", e.localizedMessage ?: "无法启动 $MAIN_ACTIVITY")
        }
    }

    private suspend fun waitAndClickByViewId(
        service: VFlowAccessibilityService,
        viewId: String,
        label: String,
        timeoutMs: Long = WAIT_TIMEOUT_MS,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val node = findNodeByViewId(service, viewId)
            if (node != null) {
                onProgress(ProgressUpdate("正在点击 IT之家$label"))
                val success = clickNode(service, node)
                node.recycle()
                if (success) return true
            }
            delay(WAIT_INTERVAL_MS)
        }
        return false
    }

    private fun findNodeByViewId(service: VFlowAccessibilityService, viewId: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val nodeToReturn = nodes?.firstOrNull { it.isVisibleToUser && it.isEnabled }
            ?: nodes?.firstOrNull()
        nodes?.filter { it != nodeToReturn }?.forEach { it.recycle() }
        return nodeToReturn
    }

    private suspend fun clickNode(
        service: VFlowAccessibilityService,
        node: AccessibilityNodeInfo
    ): Boolean {
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.centerX() < 0 || bounds.centerY() < 0) return false

        val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100L))
            .build()
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                deferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                deferred.complete(false)
            }
        }, null)
        return deferred.await()
    }
}
