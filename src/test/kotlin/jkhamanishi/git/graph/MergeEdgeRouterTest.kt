package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeEdgeRouterTest {

    @Test
    fun `collects only long second-parent merge edges`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1, 3)),
                FakeNode(listOf(2)),
                FakeNode(emptyList()),
                FakeNode(listOf(4)),
                FakeNode(emptyList())
            )
        )
        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        val routedEdges = MergeEdgeRouter.collectLongSecondParentEdges(commitMap, visibleGraph.getVisibleNodesCount())

        assertEquals(setOf(MergeEdgeRouter.EdgeKey.of(0, 3)), routedEdges)
    }

    @Test
    fun `compare pushes routed merge edge after regular graph elements`() {
        val routedEdges = setOf(MergeEdgeRouter.EdgeKey.of(0, 3))
        val mergeEdge = GraphEdge.createNormalEdge(0, 3, GraphEdgeType.USUAL)
        val node = GraphNode(1)

        val result = MergeEdgeRouter.compare(mergeEdge, node, routedEdges) { _, _ -> -1 }

        assertEquals(1, result)
    }

    @Test
    fun `outside routing check ignores unrelated edges`() {
        val routedEdges = setOf(MergeEdgeRouter.EdgeKey.of(0, 3))
        val routedEdge = GraphEdge.createNormalEdge(0, 3, GraphEdgeType.USUAL)
        val regularEdge = GraphEdge.createNormalEdge(1, 2, GraphEdgeType.USUAL)

        assertTrue(MergeEdgeRouter.isOutsideRoutedEdge(routedEdge, routedEdges))
        assertFalse(MergeEdgeRouter.isOutsideRoutedEdge(regularEdge, routedEdges))
    }

    private data class FakeNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    private class FakeVisibleGraph(private val nodes: List<FakeNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeNode = nodes[index]
    }
}

