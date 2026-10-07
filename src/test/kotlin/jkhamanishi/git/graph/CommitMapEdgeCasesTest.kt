package jkhamanishi.git.graph

import org.junit.Assert.assertEquals
import org.junit.Test

class CommitMapEdgeCasesTest {

    @Test
    fun `zero arg adjacent rows method is used when it is the only option`() {
        val visibleGraph = ZeroArgVisibleGraph(
            listOf(
                ZeroArgNode(intArrayOf(1, 2)),
                ZeroArgNode(intArrayOf(2)),
                ZeroArgNode(intArrayOf())
            )
        )

        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            ZeroArgVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(listOf(1, 2), commitMap.getParents(0))
        assertEquals(listOf(2), commitMap.getParents(1))
        assertEquals(emptyList<Int>(), commitMap.getParents(2))
    }

    @Test
    fun `failing boolean overload falls back to int overload`() {
        val visibleGraph = FallbackVisibleGraph(
            listOf(
                FallbackNode(listOf(1)),
                FallbackNode(listOf(2)),
                FallbackNode(emptyList())
            )
        )

        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FallbackVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(listOf(1), commitMap.getParents(0))
        assertEquals(listOf(2), commitMap.getParents(1))
        assertEquals(emptyList<Int>(), commitMap.getParents(2))
    }

    @Test
    fun `parent like objects are converted and unsupported values are ignored`() {
        val visibleGraph = ObjectParentVisibleGraph(
            listOf(
                ObjectParentNode(listOf(ParentRef(2), UnsupportedRef("ignored"))),
                ObjectParentNode(emptyList()),
                ObjectParentNode(emptyList())
            )
        )

        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            ObjectParentVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(listOf(2), commitMap.getParents(0))
        assertEquals(emptyList<Int>(), commitMap.getParents(1))
        assertEquals(emptyList<Int>(), commitMap.getParents(2))
    }

    @Test
    fun `null nodes are skipped while building parent map`() {
        val visibleGraph = NullableVisibleGraph(listOf(null, NullableNode(listOf(0))))

        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            NullableVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(emptyList<Int>(), commitMap.getParents(0))
        assertEquals(listOf(0), commitMap.getParents(1))
    }

    @Suppress("unused")
    private class ZeroArgNode(private val parents: IntArray) {
        fun getAdjacentRows(): IntArray = parents
    }

    @Suppress("unused")
    private class ZeroArgVisibleGraph(private val nodes: List<ZeroArgNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): ZeroArgNode = nodes[index]
    }

    @Suppress("unused")
    private class FallbackNode(private val intParents: List<Int>) {
        fun getAdjacentRows(down: Boolean): List<Int> {
            if (down) {
                throw IllegalStateException("boom")
            }
            return emptyList()
        }

        fun getAdjacentRows(index: Int): List<Int> = if (index >= 0) intParents else emptyList()
    }

    @Suppress("unused")
    private class FallbackVisibleGraph(private val nodes: List<FallbackNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FallbackNode = nodes[index]
    }

    @Suppress("unused")
    private class ObjectParentNode(private val parents: List<Any>) {
        fun getAdjacentRows(): List<Any> = parents
    }

    @Suppress("unused")
    private class ObjectParentVisibleGraph(private val nodes: List<ObjectParentNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): ObjectParentNode = nodes[index]
    }

    @Suppress("unused")
    private data class ParentRef(private val nodeIndex: Int) {
        fun getNodeIndex(): Int = nodeIndex
    }

    private data class UnsupportedRef(private val value: String)

    @Suppress("unused")
    private class NullableNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    @Suppress("unused")
    private class NullableVisibleGraph(private val nodes: List<NullableNode?>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): NullableNode? = nodes[index]
    }
}


