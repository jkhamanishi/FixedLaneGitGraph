package jkhamanishi.git.graph

import java.lang.reflect.Method

class CommitMap(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method) {

    private val logger = ConsoleLogger("CommitMap")

    data class ChildRelation(val childIndex: Int, val parentPosition: Int)

    private val nodeParents = computeNodeParents(visibleGraph, nodesCount, getNodeMethod)
    private val parentToChildren = computeChildRelation(nodesCount)

    private fun computeNodeParents(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method): HashMap<Int, List<Int>> {
        val map = HashMap<Int, List<Int>>()
        for (i in 0 until nodesCount) {
            val node = getNodeMethod.invoke(visibleGraph, i) ?: continue
            val getAdjacentMethod =
                node.javaClass.methods.firstOrNull { it.name == "getAdjacentRows" }?.apply { isAccessible = true }

            val rawAdjacent = if (getAdjacentMethod != null) {
                try {
                    val paramTypes = getAdjacentMethod.parameterTypes
                    val result = when {
                        paramTypes.isEmpty() -> getAdjacentMethod.invoke(node)
                        paramTypes.size == 1 && (paramTypes[0] == Boolean::class.java || paramTypes[0] == Boolean::class.javaPrimitiveType) -> {
                            getAdjacentMethod.invoke(node, true)
                        }

                        paramTypes.size == 1 -> {
                            getAdjacentMethod.invoke(node, i)
                        }

                        else -> null
                    }
                    result as? List<*> ?: emptyList<Any>()
                } catch (e: Exception) {
                    logger.warn("Failed to invoke getAdjacentRows for index $i: ${e.javaClass.simpleName} - ${e.message}")
                    emptyList<Any>()
                }
            } else {
                emptyList<Any>()
            }

            val parentIndices = rawAdjacent.mapNotNull { p ->
                when (p) {
                    is Int -> p
                    else -> {
                        val m = p?.javaClass?.methods?.firstOrNull {
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