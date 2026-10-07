package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import jkhamanishi.git.graph.rendering.PersistentLayoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Comparator
import java.util.function.IntUnaryOperator
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

class PersistentLayoutManagerTest {

    @Test
    fun `assign lane clear and default lane lookup work`() {
        PersistentLayoutManager.clear()

        PersistentLayoutManager.assignLane(7, 3)
        assertEquals(3, PersistentLayoutManager.getLaneForNode(7, 99))
        assertEquals(99, PersistentLayoutManager.getLaneForNode(8, 99))

        PersistentLayoutManager.clear()
        assertEquals(99, PersistentLayoutManager.getLaneForNode(7, 99))
    }

    @Test
    fun `apply layout injects getter comparator and clears generator cache`() {
        PersistentLayoutManager.clear()

        val originalGetter = IntUnaryOperator { 100 + it }
        val originalComparator = LayoutAwareComparator(originalGetter) { _, _ -> -1 }
        val cache = FakeCache()
        val generator = FakePrintElementGenerator(originalComparator, cache)
        val visibleGraph = StandardVisibleGraph(
            nodes = listOf(
                FakeNode(listOf(1, 3)),
                FakeNode(listOf(2)),
                FakeNode(emptyList()),
                FakeNode(listOf(4)),
                FakeNode(emptyList())
            ),
            printElementGenerator = generator
        )
        val visiblePack = StandardVisiblePack(visibleGraph)
        val table = JTableWithVisiblePack(visiblePack)

        PersistentLayoutManager.applyLayout(table)

        assertTrue(Proxy.isProxyClass(originalComparator.myLayoutIndexGetter.javaClass))
        assertTrue(Proxy.isProxyClass(generator.elementComparator.javaClass))
        assertTrue(cache.cleared)

        for (nodeIndex in 0 until visibleGraph.getVisibleNodesCount()) {
            val rawLane = 100 + nodeIndex
            val cachedLane = PersistentLayoutManager.getLaneForNode(nodeIndex, rawLane)
            assertEquals(cachedLane, originalComparator.myLayoutIndexGetter.applyAsInt(nodeIndex))
        }

        val mergeEdge = GraphEdge.createNormalEdge(0, 3, GraphEdgeType.USUAL)
        val node = GraphNode(1)
        assertTrue(generator.elementComparator.compare(mergeEdge, node) > 0)
    }

    @Test
    fun `apply layout falls back to generic size and non node getter methods`() {
        PersistentLayoutManager.clear()

        val originalGetter = IntUnaryOperator { 50 + it }
        val originalComparator = LayoutAwareComparator(originalGetter) { _, _ -> 0 }
        val generator = FakePrintElementGenerator(originalComparator, FakeCache())
        val visibleGraph = FallbackVisibleGraph(
            nodes = listOf(
                FallbackNode(listOf(1)),
                FallbackNode(listOf(2)),
                FallbackNode(emptyList())
            ),
            printElementGenerator = generator
        )
        val table = JTableWithVisiblePack(FallbackVisiblePack(visibleGraph))

        PersistentLayoutManager.applyLayout(table)

        for (nodeIndex in 0 until visibleGraph.graphSize()) {
            assertEquals(0, originalComparator.myLayoutIndexGetter.applyAsInt(nodeIndex))
        }
    }

    @Test
    fun `apply layout with invalid graph count clears stale cache before returning`() {
        PersistentLayoutManager.clear()
        PersistentLayoutManager.assignLane(42, 9)

        val originalGetter = IntUnaryOperator { 200 + it }
        val originalComparator = LayoutAwareComparator(originalGetter) { _, _ -> 0 }
        val generator = FakePrintElementGenerator(originalComparator, FakeCache())
        val visibleGraph = ZeroCountVisibleGraph(generator)
        val table = JTableWithVisiblePack(ZeroCountVisiblePack(visibleGraph))

        PersistentLayoutManager.applyLayout(table)

        assertEquals(123, PersistentLayoutManager.getLaneForNode(42, 123))
        assertFalse(Proxy.isProxyClass(originalComparator.myLayoutIndexGetter.javaClass))
    }

    @Suppress("unused")
    data class FakeNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    @Suppress("unused")
    class StandardVisibleGraph(
        private val nodes: List<FakeNode>,
        val printElementGenerator: FakePrintElementGenerator
    ) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeNode = nodes[index]
    }

    @Suppress("unused")
    class StandardVisiblePack(private val visibleGraph: StandardVisibleGraph) {
        fun getVisibleGraph(): StandardVisibleGraph = visibleGraph
    }

    @Suppress("unused")
    data class FallbackNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    @Suppress("unused")
    class FallbackVisibleGraph(
        private val nodes: List<FallbackNode>,
        val printElementGenerator: FakePrintElementGenerator
    ) {
        fun graphSize(): Int = nodes.size
        fun fetch(index: Int): FallbackNode = nodes[index]
    }

    @Suppress("unused")
    class FallbackVisiblePack(private val visibleGraph: FallbackVisibleGraph) {
        fun getVisibleGraph(): FallbackVisibleGraph = visibleGraph
    }

    @Suppress("unused")
    class ZeroCountVisibleGraph(val printElementGenerator: FakePrintElementGenerator) {
        fun getVisibleNodesCount(): Int = 0
        fun getNode(index: Int): Any? = null
    }

    @Suppress("unused")
    class ZeroCountVisiblePack(private val visibleGraph: ZeroCountVisibleGraph) {
        fun getVisibleGraph(): ZeroCountVisibleGraph = visibleGraph
    }

    class JTableWithVisiblePack(visiblePack: Any) : JTable(VisiblePackTableModel(visiblePack))

    @Suppress("unused")
    class VisiblePackTableModel(private val visiblePack: Any) : AbstractTableModel() {
        fun getVisiblePack(): Any = visiblePack
        override fun getRowCount(): Int = 0
        override fun getColumnCount(): Int = 0
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = null
    }

    class LayoutAwareComparator(
        var myLayoutIndexGetter: IntUnaryOperator,
        private val fallback: (GraphElement, GraphElement) -> Int
    ) : Comparator<GraphElement> {
        override fun compare(first: GraphElement, second: GraphElement): Int = fallback(first, second)
    }

    @Suppress("unused")
    class FakePrintElementGenerator(
        var elementComparator: Comparator<GraphElement>,
        val cache: FakeCache
    )

    @Suppress("unused")
    class FakeCache {
        var cleared: Boolean = false
        fun clear() {
            cleared = true
        }
    }
}


