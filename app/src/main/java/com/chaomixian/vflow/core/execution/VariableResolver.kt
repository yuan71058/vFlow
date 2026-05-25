// 文件: main/java/com/chaomixian/vflow/core/execution/VariableResolver.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.parser.TemplateParser
import com.chaomixian.vflow.core.types.parser.TemplateSegment
import com.chaomixian.vflow.core.types.parser.VariablePathParser

/**
 * 变量解析器。
 * 职责：将带有变量占位符的字符串解析为最终值，并提供通用的复杂度判断。
 * 支持对象属性访问 (例如 {{image.width}}) 和类型转换。
 */
object VariableResolver {

    /**
     * 判断文本是否为"复杂"内容（文本 + 变量的混合）。
     */
    fun isComplex(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false

        val segments = TemplateParser(text).parse()
        val variableCount = segments.count { it is TemplateSegment.Variable }

        if (variableCount == 0) return false // 纯文本
        if (variableCount > 1) return true   // 多个变量
        return segments.any { it is TemplateSegment.Text && it.content.trim().isNotEmpty() }
    }

    /**
     * 判断文本是否包含任何变量引用（用于递归解析检测）。
     * 与 isComplex 不同，这个方法对纯变量字符串（如 "{{var}}"）也返回 true。
     */
    fun hasVariableReference(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        return TemplateParser(text).parse().any { it is TemplateSegment.Variable }
    }

    /**
     * 解析文本中的所有变量，返回最终字符串。
     * 适用于: "图片宽度是 {{img.width}} 像素" -> "图片宽度是 1080 像素"
     *
     * 支持递归解析：如果解析出的字符串包含变量引用，会继续解析直到完全展开。
     * 包含循环引用保护：最多递归 10 层。
     */
    fun resolve(text: String, context: ExecutionContext): String {
        return resolve(text, context, maxDepth = 10)
    }

    /**
     * 内部解析方法，支持递归深度控制。
     */
    private fun resolve(text: String, context: ExecutionContext, maxDepth: Int, currentDepth: Int = 0): String {
        if (text.isEmpty() || currentDepth >= maxDepth) return text

        val parser = TemplateParser(text)
        val segments = parser.parse()
        val sb = StringBuilder()

        for (segment in segments) {
            when (segment) {
                is TemplateSegment.Text -> sb.append(segment.content)
                is TemplateSegment.Variable -> {
                    val obj = resolveVariableObject(segment, context)
                    val strValue = obj.asString()
                    // 递归解析：如果结果字符串包含变量引用，继续解析
                    // 使用 hasVariableReference 而不是 isComplex，因为即使是纯变量字符串（如 "{{var}}"）也需要递归解析
                    if (hasVariableReference(strValue)) {
                        sb.append(resolve(strValue, context, maxDepth, currentDepth + 1))
                    } else {
                        sb.append(strValue)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * 解析单个变量引用，返回对象本身。
     * 适用于: 输入参数需要 Image 对象而不是字符串时。
     * 如果文本只包含一个变量且无其他内容 (例如 "{{step1.image}}")，返回该对象；否则返回 String 包装。
     */
    fun resolveValue(text: String, context: ExecutionContext): Any? {
        if (text.isEmpty()) return null

        val parser = TemplateParser(text)
        val segments = parser.parse()

        // 如果只有一个 Variable 类型的片段，直接返回该对象 (保留类型信息)
        if (segments.size == 1 && segments[0] is TemplateSegment.Variable) {
            val segment = segments[0] as TemplateSegment.Variable
            val vObj = resolveVariableObject(segment, context)
            // 返回 VObject 的原始值用于字符串插值
            // 例如：VString("hello").raw -> "hello", VNumber(42).raw -> 42.0
            return vObj.raw ?: vObj.asString()
        }

        // 否则解析为字符串
        return resolve(text, context)
    }

    fun resolveSingleVariableReference(text: String, context: ExecutionContext): VObject? {
        val segments = TemplateParser(text).parse()
        val segment = segments.singleOrNull() as? TemplateSegment.Variable ?: return null
        if (segment.rawExpression != text) return null
        return resolveExistingVariableObject(segment, context)
    }

    /**
     * 核心寻址逻辑：Path -> VObject
     * 所有变量现在直接存储为 VObject，无需再包装
     */
    private fun resolveVariableObject(
        segment: TemplateSegment.Variable,
        context: ExecutionContext
    ): VObject {
        return resolveExistingVariableObject(segment, context)
            ?: VObjectFactory.from("{${segment.rawExpression}}") // 保持原样或返回空
    }

    private fun resolveExistingVariableObject(
        segment: TemplateSegment.Variable,
        context: ExecutionContext
    ): VObject? {
        val path = segment.path
        if (path.isEmpty()) return null

        val rootKey = path[0]

        // 1. 寻找根对象 (Root)
        if (segment.isNamedVariable) {
            // [[varName]] / {{vars.varName}}: 查 namedVariables
            val namedPath = if (path.firstOrNull() == VariablePathParser.NAMED_VARIABLE_NAMESPACE) {
                path.drop(1)
            } else {
                path
            }

            if (namedPath.isNotEmpty()) {
                val raw = context.namedVariables[namedPath[0]]
                val currentObj = VObjectFactory.from(raw)
                if (currentObj !is VNull) {
                    return traverseProperties(currentObj, namedPath, 1, context)
                }
            }
        } else {
            if (path.size >= 2 && path.firstOrNull() == VariablePathParser.GLOBAL_VARIABLE_NAMESPACE) {
                val globalPath = path.drop(1)
                if (globalPath.isNotEmpty()) {
                    val root = context.getGlobalVariable(globalPath[0])
                    if (root !is VNull) {
                        return traverseProperties(root, globalPath, 1, context)
                    }
                }
            }

            // {{stepId.outputId}}: 查 stepOutputs (现在直接返回 VObject)
            // 路径通常是 [stepId, outputId, prop1, prop2...]
            if (path.size >= 2) {
                val stepId = path[0]
                val outputId = path[1]
                val vOutput = context.stepOutputs[stepId]?.get(outputId)
                if (vOutput != null) {
                    // stepOutputs 现在直接存储 VObject，无需转换
                    return traverseProperties(vOutput, path, 2, context)
                }
            }

            // 兼容旧逻辑：如果只是 {{key}} 且在 magicVariables 里有 (例如循环变量 index)
            if (context.magicVariables.containsKey(rootKey)) {
                val currentObj = context.magicVariables[rootKey] ?: VNull
                // 消耗掉第1个节点，从 index 1 开始遍历属性
                return traverseProperties(currentObj, path, 1, context)
            }
        }

        return null
    }

    /**
     * 递归属性访问
     */
    private fun traverseProperties(root: VObject, path: List<String>, startIndex: Int, context: ExecutionContext): VObject {
        var current = root
        for (i in startIndex until path.size) {
            val propName = resolveDynamicPathSegment(path[i], current, context)
            val next = current.getProperty(propName)
            if (next == null || next is VNull) {
                return VNull // 属性链断裂
            }
            current = next
        }
        return current
    }

    private fun resolveDynamicPathSegment(pathSegment: String, current: VObject, context: ExecutionContext): String {
        if (!hasVariableReference(pathSegment)) return pathSegment
        if (!supportsDynamicPathSegment(current)) return pathSegment

        val resolved = resolve(pathSegment, context)
        return resolved.toDoubleOrNull()
            ?.takeIf { !it.isNaN() && it % 1.0 == 0.0 }
            ?.toInt()
            ?.toString()
            ?: resolved
    }

    private fun supportsDynamicPathSegment(current: VObject): Boolean {
        return current.type == VTypeRegistry.LIST || current.type == VTypeRegistry.STRING
    }
}
