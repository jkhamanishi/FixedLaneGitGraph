package jkhamanishi.git.graph

import org.junit.Assert.assertEquals
import org.junit.Test

class CommitLaneCalculatorTest {

    @Test
    fun `linear history stays on a single lane`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1)),
                FakeNode(listOf(2)),
                FakeNode(emptyList())
            )
        )

        val lanes = CommitLaneCalculator.computeNodeLanes(
            visibleGraph = visibleGraph,
            nodesCount = visibleGraph.getVisibleNodesCount(),
            getNodeMethod = FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(0, lanes[0])
        assertEquals(0, lanes[1])
        assertEquals(0, lanes[2])
    }

    @Test
    fun `merge keeps first parent vertical and assigns the second parent a new lane`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1, 3)),
                FakeNode(listOf(2)),
                FakeNode(emptyList()),
                FakeNode(listOf(4)),
                FakeNode(emptyList())
            )
        )

        val lanes = CommitLaneCalculator.computeNodeLanes(
            visibleGraph = visibleGraph,
            nodesCount = visibleGraph.getVisibleNodesCount(),
            getNodeMethod = FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(0, lanes[0])
        assertEquals(0, lanes[1])
        assertEquals(0, lanes[2])
        assertEquals(1, lanes[3])
        assertEquals(1, lanes[4])
    }

    @Test
    fun `normal branch split still assigns a side lane`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1, 2)),
                FakeNode(listOf(3)),
                FakeNode(listOf(4)),
                FakeNode(emptyList()),
                FakeNode(emptyList())
            )
        )

        val lanes = CommitLaneCalculator.computeNodeLanes(
            visibleGraph = visibleGraph,
            nodesCount = visibleGraph.getVisibleNodesCount(),
            getNodeMethod = FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(0, lanes[0])
        assertEquals(0, lanes[1])
        assertEquals(1, lanes[2])
        assertEquals(0, lanes[3])
        assertEquals(1, lanes[4])
    }

    private data class FakeNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    private class FakeVisibleGraph(private val nodes: List<FakeNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeNode = nodes[index]
    }
}






