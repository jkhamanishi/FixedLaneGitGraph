package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement

object MergeEdgeRouter {

    data class EdgeKey(val firstNodeIndex: Int, val secondNodeIndex: Int) {
        companion object {
            fun of(firstNodeIndex: Int, secondNodeIndex: Int): EdgeKey {
                return if (firstNodeIndex <= secondNodeIndex) {
                    EdgeKey(firstNodeIndex, secondNodeIndex)
                } else {
                    EdgeKey(secondNodeIndex, firstNodeIndex)
                }
            }
        }
    }

    fun collectLongSecondParentEdges(commitMap: CommitMap, nodesCount: Int): Set<EdgeKey> {
        val longEdges = linkedSetOf<EdgeKey>()

        for (childIndex in 0 until nodesCount) {
            val parents = commitMap.getParents(childIndex)
            parents.forEachIndexed { parentPosition, parentIndex ->
                if (parentPosition >= 1 && kotlin.math.abs(parentIndex - childIndex) > 1) {
                    longEdges += EdgeKey.of(childIndex, parentIndex)
                }
            }
        }

        return longEdges
    }

    fun isOutsideRoutedEdge(element: GraphElement, longSecondParentEdges: Set<EdgeKey>): Boolean {
        val edge = element as? GraphEdge ?: return false
        val edgeKey = edge.getStableKey() ?: return false
        return edgeKey in longSecondParentEdges
    }

    fun compare(
        first: GraphElement,
        second: GraphElement,
        longSecondParentEdges: Set<EdgeKey>,
        fallback: (GraphElement, GraphElement) -> Int
    ): Int {
        val firstIsOutsideRouted = isOutsideRoutedEdge(first, longSecondParentEdges)
        val secondIsOutsideRouted = isOutsideRoutedEdge(second, longSecondParentEdges)

        if (firstIsOutsideRouted != secondIsOutsideRouted) {
            return if (firstIsOutsideRouted) 1 else -1
        }

        return fallback(first, second)
    }

    private fun GraphEdge.getStableKey(): EdgeKey? {
        val upNodeIndex = upNodeIndex ?: return null
        val downNodeIndex = downNodeIndex ?: return null
        return EdgeKey.of(upNodeIndex, downNodeIndex)
    }
}

