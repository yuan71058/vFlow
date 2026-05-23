package com.chaomixian.vflow.core.workflow.module.system

import android.app.NotificationManager
import android.service.notification.Condition
import com.chaomixian.vflow.ui.main.MainActivity
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Test

class DoNotDisturbModuleTest {

    private val module = DoNotDisturbModule()

    @Test
    fun `module exposes on off and toggle actions`() {
        val actionInput = module.getInputs().single { it.id == "action" }

        assertEquals("vflow.system.do_not_disturb", module.id)
        assertEquals(ParameterType.ENUM, actionInput.staticType)
        assertEquals(DoNotDisturbModule.ACTION_TOGGLE, actionInput.defaultValue)
        assertEquals(
            listOf(
                DoNotDisturbModule.ACTION_TOGGLE,
                DoNotDisturbModule.ACTION_ON,
                DoNotDisturbModule.ACTION_OFF
            ),
            actionInput.options
        )
    }

    @Test
    fun `module requires notification policy access`() {
        assertEquals(
            listOf(PermissionManager.NOTIFICATION_POLICY),
            module.requiredPermissions
        )
    }

    @Test
    fun `toggle activates vflow rule when current rule state is false`() {
        assertEquals(
            Condition.STATE_TRUE,
            module.resolveTargetState(
                Condition.STATE_FALSE,
                DoNotDisturbModule.ACTION_TOGGLE
            )
        )
    }

    @Test
    fun `toggle deactivates vflow rule when current rule state is true`() {
        assertEquals(
            Condition.STATE_FALSE,
            module.resolveTargetState(
                Condition.STATE_TRUE,
                DoNotDisturbModule.ACTION_TOGGLE
            )
        )
    }

    @Test
    fun `legacy on action blocks all interruptions`() {
        assertEquals(
            NotificationManager.INTERRUPTION_FILTER_NONE,
            module.resolveLegacyTargetFilter(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                DoNotDisturbModule.ACTION_ON
            )
        )
    }

    @Test
    fun `legacy off action allows all interruptions`() {
        assertEquals(
            NotificationManager.INTERRUPTION_FILTER_ALL,
            module.resolveLegacyTargetFilter(
                NotificationManager.INTERRUPTION_FILTER_NONE,
                DoNotDisturbModule.ACTION_OFF
            )
        )
    }

    @Test
    fun `legacy toggle activates dnd from all interruptions`() {
        assertEquals(
            NotificationManager.INTERRUPTION_FILTER_NONE,
            module.resolveLegacyTargetFilter(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                DoNotDisturbModule.ACTION_TOGGLE
            )
        )
    }

    @Test
    fun `legacy toggle deactivates dnd from blocked interruptions`() {
        assertEquals(
            NotificationManager.INTERRUPTION_FILTER_ALL,
            module.resolveLegacyTargetFilter(
                NotificationManager.INTERRUPTION_FILTER_NONE,
                DoNotDisturbModule.ACTION_TOGGLE
            )
        )
    }

    @Test
    fun `automatic zen rule configuration activity opens main activity`() {
        assertEquals(
            MainActivity::class.java.name,
            module.ruleConfigurationActivityClassName()
        )
    }
}
