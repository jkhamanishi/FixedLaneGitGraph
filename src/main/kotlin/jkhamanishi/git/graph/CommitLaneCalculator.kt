@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import java.lang.reflect.Method

object CommitLaneCalculator {

    private val logger = ConsoleLogger("CommitLaneCalculator")

    const val MAX_LANES = 5

    data class ChildRelation(val childIndex: Int, val parentPosition: Int)

    fun computeNodeParents(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method): HashMap<Int, List<Int>> {
        val nodeParents = HashMap<Int, List<Int>>()

        for (i in 0 until nodesCount) {
            val node = getNodeMethod.invoke(visibleGraph, i) ?: continue
            val getAdjacentMethod = node.javaClass.methods.firstOrNull { it.name == "getAdjacentRows" }?.apply { isAccessible = true }

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
            nodeParents[i] = parentIndices
        }
        return nodeParents
    }

    fun computeNodeLanes(nodesCount: Int, nodeParents: HashMap<Int, List<Int>>): HashMap<Int, Int> {
        val parentToChildren = HashMap<Int, MutableList<ChildRelation>>()

        for (c in 0 until nodesCount) {
            val parents = nodeParents[c] ?: emptyList()
            parents.forEachIndexed { pos, p ->
                parentToChildren.getOrPut(p) { mutableListOf() }
                    .add(ChildRelation(childIndex = c, parentPosition = pos))
            }
        }

        val nodeToLane = HashMap<Int, Int>()
        var laneCounter = 0

        fun acquireLane(): Int {
            val current = laneCounter
            if (laneCounter < MAX_LANES) {
                laneCounter++
            }
            return current.coerceAtMost(MAX_LANES)
        }

        for (i in 0 until nodesCount) {
            val children = parentToChildren[i] ?: emptyList()

            if (children.isEmpty()) {
                nodeToLane[i] = acquireLane()
            } else {
                val primaryChild = children[0]
                val childIdx = primaryChild.childIndex
                val parentPos = primaryChild.parentPosition
                val childParents = nodeParents[childIdx] ?: emptyList()
                val isChildMerge = childParents.size > 1
                val childLane = nodeToLane[childIdx] ?: acquireLane()

                if (isChildMerge) {
                    if (parentPos == 0) {
                        nodeToLane[i] = childLane
                    } else {
                        nodeToLane[i] = acquireLane()
                    }
                } else {
                    nodeToLane[i] = childLane
                }
            }
        }
        return nodeToLane
    }
}