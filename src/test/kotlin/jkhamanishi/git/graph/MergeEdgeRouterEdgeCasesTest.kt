package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphNode
import jkhamanishi.git.graph.rendering.MergeEdgeRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeEdgeRouterEdgeCasesTest {

    @Test
    fun `edge keys are direction agnostic`() {
        assertEquals(
            MergeEdgeRouter.EdgeKey.of(1, 3),
            MergeEdgeRouter.EdgeKey.of(3, 1)
        )
    }

    @Test
    fun `collect long second parent edges ignores first parent and short edges`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(3, 1, 4)),
                FakeNode(emptyList()),
                FakeNode(emptyList()),
                FakeNode(emptyList()),
                FakeNode(emptyList())
            )
        )
        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        val routedEdges = MergeEdgeRouter.collectLongSecondParentEdges(commitMap, visibleGraph.getVisibleNodesCount())

        assertEquals(setOf(MergeEdgeRouter.EdgeKey.of(0, 4)), routedEdges)
    }

    @Test
    fun `compare delegates to fallback when routing priority matches`() {
        val regularEdge = GraphEdge.createNormalEdge(1, 2, GraphEdgeType.USUAL)
        val node = GraphNode(3)

        val result = MergeEdgeRouter.compare(regularEdge, node, emptySet()) { _, _ -> 7 }

        assertEquals(7, result)
    }

    @Test
    fun `compare keeps regular element ahead of routed merge edge`() {
        val routedEdges = setOf(MergeEdgeRouter.EdgeKey.of(0, 3))
        val node = GraphNode(1)
        val mergeEdge = GraphEdge.createNormalEdge(0, 3, GraphEdgeType.USUAL)

        val result = MergeEdgeRouter.compare(node, mergeEdge, routedEdges) { _, _ -> 99 }

        assertTrue(result < 0)
    }

    @Suppress("unused")
    private data class FakeNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    @Suppress("unused")
    private class FakeVisibleGraph(private val nodes: List<FakeNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeNode = nodes[index]
    }
}

