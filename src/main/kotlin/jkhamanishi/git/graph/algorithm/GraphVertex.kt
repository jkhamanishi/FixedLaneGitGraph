package jkhamanishi.git.graph.algorithm

internal data class GraphVertex(
    val id: Int,
    val children: MutableList<GraphVertex> = mutableListOf(),
    val parents: MutableList<GraphVertex> = mutableListOf(),
) {
    var x: Int = 0 // Current lane position
    var nextX: Int = 0 // Next available x position
    var onBranch: GraphBranch? = null
    var nextParent: Int = 0
    val connections: MutableList<GraphUnavailablePoint> = mutableListOf()

    fun getNextParent(): GraphVertex? {
        return if (nextParent < parents.size) parents[nextParent] else null
    }

    fun registerParentProcessed() {
        nextParent++
    }

    fun isMerge(): Boolean = parents.size > 1

    fun registerUnavailablePoint(x: Int, connectsToVertex: GraphVertex?, onBranch: GraphBranch) {
        if (x == nextX) {
            nextX = x + 1
            connections.add(GraphUnavailablePoint(connectsToVertex, onBranch))
        }
    }

    fun getPointConnectingTo(vertex: GraphVertex?, onBranch: GraphBranch): Int? {
        for (i in connections.indices) {
            if (connections[i].connectsTo === vertex && connections[i].onBranch === onBranch) {
                return i
            }
        }
        return null
    }
}

