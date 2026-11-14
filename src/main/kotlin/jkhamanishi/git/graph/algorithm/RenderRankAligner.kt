package jkhamanishi.git.graph.algorithm

/**
 * Aligns lane values with the column the IntelliJ VCS log actually draws.
 *
 * IntelliJ's PrintElementGeneratorImpl draws a commit at its rank among the elements of its row
 * (the commit plus the edges passing through the row), sorted by layout index. Empty lanes are
 * therefore compacted away. This aligner iterates lane := rank until each commit's rank equals its
 * lane, keeping the iteration with the fewest mismatches.
 *
 * The long-edge thresholds match the IDE's "show long edges" mode, which the plugin enables.
 */
internal object RenderRankAligner {

    /** IntelliJ thresholds with "show long edges" enabled. */
    const val SHOW_LONG_EDGES_SIZE = 1000
    const val SHOW_LONG_EDGES_PART = 250

    /** IntelliJ thresholds with "show long edges" disabled (the IDE default). */
    const val HIDE_LONG_EDGES_SIZE = 30
    const val HIDE_LONG_EDGES_PART = 1

    private const val MAX_ITERATIONS = 50

    /**
     * @param lanes initial lane per node index
     * @param edges normal edges as (childIndex, parentIndex) with childIndex < parentIndex
     * @param longEdgeSize IntelliJ long-edge size threshold for the active mode
     * @param visiblePartSize IntelliJ visible-part threshold for the active mode
     */
    fun align(
        lanes: IntArray,
        edges: List<Pair<Int, Int>>,
        longEdgeSize: Int = SHOW_LONG_EDGES_SIZE,
        visiblePartSize: Int = SHOW_LONG_EDGES_PART,
    ): IntArray {
        val sortedEdges = edges.sortedBy { it.first }
        var current = lanes.copyOf()
        var best = current.copyOf()
        var bestMismatches = Int.MAX_VALUE

        var remaining = MAX_ITERATIONS
        while (remaining-- >= 0) {
            val ranks = computeRanks(current, sortedEdges, longEdgeSize, visiblePartSize)
            var mismatches = 0
            for (i in current.indices) if (ranks[i] != current[i]) mismatches++
            if (mismatches < bestMismatches) {
                bestMismatches = mismatches
                best = current.copyOf()
            }
            if (mismatches == 0) break
            current = ranks
        }
        return best
    }

    private fun computeRanks(
        lanes: IntArray,
        sortedEdges: List<Pair<Int, Int>>,
        longEdgeSize: Int,
        visiblePartSize: Int,
    ): IntArray {
        val n = lanes.size
        val ranks = IntArray(n)
        val active = ArrayList<Pair<Int, Int>>()
        var pointer = 0

        for (row in 0 until n) {
            while (pointer < sortedEdges.size && sortedEdges[pointer].first < row) {
                val edge = sortedEdges[pointer]
                if (edge.second > row) active.add(edge)
                pointer++
            }
            active.removeAll { it.second <= row }

            var count = 0
            for ((up, down) in active) {
                val visible = (down - up) < longEdgeSize || minOf(row - up, down - row) <= visiblePartSize
                if (!visible) continue
                val keyLane = maxOf(lanes[up], lanes[down])
                if (keyLane < lanes[row] || (keyLane == lanes[row] && up < row)) count++
            }
            ranks[row] = count
        }
        return ranks
    }
}