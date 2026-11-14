package jkhamanishi.git.graph.rendering

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode

/**
 * Consistent total order for the elements of one row (nodes and edges).
 *
 * Each element maps to a lexicographic key: [lane, rank, nodeIndex or upIndex, downIndex].
 * A node sorts by its lane. An edge sorts by the larger lane of its two endpoints, and an edge
 * sorts before the node on a tie. This avoids the contract violations the IDE comparator hit
 * with the plugin's lane values.
 */
internal object RowElementOrder {

    private const val EDGE_RANK = 0
    private const val NODE_RANK = 1

    fun compare(first: GraphElement, second: GraphElement, laneOf: (Int) -> Int): Int {
        val firstKey = sortKey(first, laneOf)
        val secondKey = sortKey(second, laneOf)
        for (i in firstKey.indices) {
            if (firstKey[i] != secondKey[i]) {
                return firstKey[i].compareTo(secondKey[i])
            }
        }
        return 0
    }

    private fun sortKey(element: GraphElement, laneOf: (Int) -> Int): IntArray {
        return when (element) {
            is GraphNode -> {
                val nodeIndex = element.nodeIndex
                intArrayOf(laneOf(nodeIndex), NODE_RANK, nodeIndex, 0)
            }
            is GraphEdge -> {
                val up = element.upNodeIndex
                val down = element.downNodeIndex
                if (up != null && down != null) {
                    intArrayOf(maxOf(laneOf(up), laneOf(down)), EDGE_RANK, up, down)
                } else {
                    // Dangling edge: key it by the endpoint that exists.
                    val anchor = up ?: down ?: Int.MAX_VALUE
                    intArrayOf(laneOf(anchor), EDGE_RANK, anchor, -1)
                }
            }
            else -> intArrayOf(Int.MAX_VALUE, NODE_RANK, Int.MAX_VALUE, 0)
        }
    }
}

