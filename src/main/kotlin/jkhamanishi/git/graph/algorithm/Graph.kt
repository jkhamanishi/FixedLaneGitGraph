package jkhamanishi.git.graph.algorithm

internal class Graph {
    val vertices: MutableList<GraphVertex> = mutableListOf()
    val branches: MutableList<GraphBranch> = mutableListOf()
    val availableColours: MutableList<Int> = mutableListOf()

    fun getAvailableColour(startAt: Int): Int {
        for (i in availableColours.indices) {
            if (startAt > availableColours[i]) {
                return i
            }
        }
        availableColours.add(0)
        return availableColours.size - 1
    }
}

