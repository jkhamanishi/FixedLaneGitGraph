package jkhamanishi.git.graph.algorithm

internal data class GraphPoint(val x: Int, val y: Int)

internal data class GraphLine(
    val p1: GraphPoint,
    val p2: GraphPoint,
    val lockedFirst: Boolean
)

internal data class GraphUnavailablePoint(
    val connectsTo: GraphVertex?,
    val onBranch: GraphBranch
)

