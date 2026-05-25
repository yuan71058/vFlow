package com.chaomixian.vflow.core.execution

import android.content.ContextWrapper
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Stack

class VariableResolverTest {

    @Test
    fun `resolveValue uses canonical variable as list index`() {
        val context = createContext(
            stepOutputs = mutableMapOf(
                "step1" to mapOf(
                    "items" to VList(listOf(VString("zero"), VString("one"), VString("two")))
                )
            ),
            namedVariables = mutableMapOf("index" to VNumber(1))
        )

        val result = VariableResolver.resolveValue("{{step1.items.{{vars.index}}}}", context)

        assertEquals("one", result)
    }

    @Test
    fun `resolveValue uses canonical variable as negative list index`() {
        val context = createContext(
            stepOutputs = mutableMapOf(
                "step1" to mapOf(
                    "items" to VList(listOf(VString("zero"), VString("one"), VString("two")))
                )
            ),
            namedVariables = mutableMapOf("index" to VNumber(-1))
        )

        val result = VariableResolver.resolveValue("{{step1.items.{{vars.index}}}}", context)

        assertEquals("two", result)
    }

    @Test
    fun `resolveValue uses canonical variable as string index`() {
        val context = createContext(
            stepOutputs = mutableMapOf("step1" to mapOf("text" to VString("abc"))),
            namedVariables = mutableMapOf("index" to VNumber(2))
        )

        val result = VariableResolver.resolveValue("{{step1.text.{{vars.index}}}}", context)

        assertEquals("c", result)
    }

    @Test
    fun `resolveSingleVariableReference uses step output as dynamic string index`() {
        val context = createContext(
            stepOutputs = mutableMapOf(
                "indexStep" to mapOf("variable" to VNumber(0)),
                "textStep" to mapOf("variable" to VString("abcd"))
            )
        )

        val result = VariableResolver.resolveSingleVariableReference(
            "{{textStep.variable.{{indexStep.variable}}}}",
            context
        )

        assertEquals("a", result?.asString())
    }

    @Test
    fun `complexity helpers use nested template parser`() {
        assertTrue(VariableResolver.hasVariableReference("{{aaa.{{bbb}}}}"))
        assertFalse(VariableResolver.isComplex("{{aaa.{{bbb}}}}"))
        assertTrue(VariableResolver.isComplex("prefix {{aaa.{{bbb}}}}"))
        assertTrue(VariableResolver.isComplex("{{aaa}}{{bbb}}"))
    }

    private fun createContext(
        stepOutputs: MutableMap<String, Map<String, com.chaomixian.vflow.core.types.VObject>> = mutableMapOf(),
        namedVariables: MutableMap<String, com.chaomixian.vflow.core.types.VObject> = mutableMapOf()
    ): ExecutionContext {
        return ExecutionContext(
            applicationContext = ContextWrapper(null),
            variables = mutableMapOf(),
            magicVariables = mutableMapOf(),
            services = ExecutionServices(),
            allSteps = emptyList<ActionStep>(),
            currentStepIndex = 0,
            stepOutputs = stepOutputs,
            loopStack = Stack(),
            namedVariables = namedVariables,
            workDir = File("build/test-workdir")
        )
    }
}
