package jkhamanishi.git.graph.algorithm

internal class GraphBranch(val colour: Int) {
    val lines: MutableList<GraphLine> = mutableListOf()
    var end: Int = 0

    fun addLine(p1: GraphPoint, p2: GraphPoint, lockedFirst: Boolean) {
        lines.add(GraphLine(p1, p2, lockedFirst))
    }
}

