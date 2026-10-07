package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.RowInfo
import java.lang.reflect.Method

class CommitMap(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method) {

    private val logger = ConsoleLogger("CommitMap")

    data class ChildRelation(val childIndex: Int, val parentPosition: Int)

    private val nodeParents = computeNodeParents(visibleGraph, nodesCount, getNodeMethod)
    private val parentToChildren = computeChildRelation(nodesCount)

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

    private fun computeChildRelation(nodesCount: Int): HashMap<Int, MutableList<ChildRelation>> {
        val map = HashMap<Int, MutableList<ChildRelation>>()
        for (c in 0 until nodesCount) {
            val parents = nodeParents[c] ?: emptyList()
            parents.forEachIndexed { pos, p ->
                map.getOrPut(p) { mutableListOf() }
                    .add(ChildRelation(childIndex = c, parentPosition = pos))
            }
        }
        return map
    }

    fun getChildren(i: Int): List<ChildRelation> {
        return parentToChildren[i] ?: emptyList()
    }

    fun getParents(index: Int): List<Int> {
        return nodeParents[index] ?: emptyList()
    }
}