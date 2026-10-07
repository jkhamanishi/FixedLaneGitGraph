package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.RowInfo
import java.lang.reflect.Method

/**
 * Extracts commit parent-child relationships from the git log graph structure.
 *
 * This class handles reflection-based access to the IntelliJ VCS log API to build
 * a map of which commits are parents/children of each other.
 */
class CommitMap(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method) {

    private val logger = ConsoleLogger("CommitMap")

    private val nodeParents = computeNodeParents(visibleGraph, nodesCount, getNodeMethod)

    private fun extractAdjacentRows(node: Any, rowIndex: Int): List<Any> {
        val rowInfo = node as? RowInfo<*>
        if (rowInfo != null) {
            // Visible graph rows are ordered from newer to older commits, so the "down" rows
            // correspond to parent commits for the current row.
            return rowInfo.getAdjacentRows(true).map { it as Any }
        }

        val adjacentMethods = node.javaClass.methods
            .filter { it.name == "getAdjacentRows" }
            .sortedBy {
                when {
                    it.parameterCount == 0 -> 0
                    it.parameterCount == 1 && (it.parameterTypes[0] == Boolean::class.java || it.parameterTypes[0] == Boolean::class.javaPrimitiveType) -> 1
                    it.parameterCount == 1 && (it.parameterTypes[0] == Int::class.java || it.parameterTypes[0] == Int::class.javaPrimitiveType) -> 2
                    else -> 3
                }
            }

        for (method in adjacentMethods) {
            method.isAccessible = true
            try {
                val result = when {
                    method.parameterCount == 0 -> method.invoke(node)
                    method.parameterCount == 1 && (method.parameterTypes[0] == Boolean::class.java || method.parameterTypes[0] == Boolean::class.javaPrimitiveType) -> {
                        method.invoke(node, java.lang.Boolean.TRUE)
                    }

                    method.parameterCount == 1 && (method.parameterTypes[0] == Int::class.java || method.parameterTypes[0] == Int::class.javaPrimitiveType) -> {
                        method.invoke(node, rowIndex)
                    }

                    else -> continue
                }

                return when (result) {
                    is List<*> -> result.filterNotNull()
                    is Collection<*> -> result.filterNotNull()
                    is Array<*> -> result.filterNotNull()
                    is IntArray -> result.map { it }
                    else -> emptyList()
                }
            } catch (_: IllegalArgumentException) {
                continue
            } catch (e: Exception) {
                logger.warn("Failed to invoke getAdjacentRows for index $rowIndex: ${e.javaClass.simpleName} - ${e.message}")
            }
        }

        return emptyList()
    }

    private fun computeNodeParents(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method): HashMap<Int, List<Int>> {
        val map = HashMap<Int, List<Int>>()
        getNodeMethod.isAccessible = true
        for (i in 0 until nodesCount) {
            val node = getNodeMethod.invoke(visibleGraph, i) ?: continue
            val rawAdjacent = extractAdjacentRows(node, i)

            val parentIndices = rawAdjacent.mapNotNull { p ->
                when (p) {
                    is Int -> p
                    else -> {
                        val m = p.javaClass.methods.firstOrNull {
                            it.name == "getNodeIndex" || it.name == "getIndex" || it.name == "getId" || it.name == "toInt"
                        }?.apply { isAccessible = true }
                        m?.invoke(p) as? Int
                    }
                }
            }
            map[i] = parentIndices
        }
        return map
    }


    fun getParents(index: Int): List<Int> {
        return nodeParents[index] ?: emptyList()
    }
}

