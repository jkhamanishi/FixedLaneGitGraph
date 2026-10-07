package jkhamanishi.git.graph

import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.RowInfo
import com.intellij.vcs.log.graph.RowType
import org.junit.Assert.assertEquals
import org.junit.Test

class CommitMapTest {

    @Test
    fun `row info nodes use down adjacent rows as parents`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeRowInfoNode(commit = 0, downRows = listOf(1), upRows = emptyList()),
                FakeRowInfoNode(commit = 1, downRows = listOf(2), upRows = listOf(0)),
                FakeRowInfoNode(commit = 2, downRows = emptyList(), upRows = listOf(1))
            )
        )

        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(listOf(1), commitMap.getParents(0))
        assertEquals(listOf(2), commitMap.getParents(1))
        assertEquals(emptyList<Int>(), commitMap.getParents(2))
    }

    @Test
    fun `reflection fallback prefers boolean adjacent rows overload over int overload`() {
        val visibleGraph = OverloadedVisibleGraph(
            listOf(
                OverloadedNode(booleanRows = listOf(1), intRows = listOf(99)),
                OverloadedNode(booleanRows = listOf(2), intRows = listOf(98)),
                OverloadedNode(booleanRows = emptyList(), intRows = listOf(97))
            )
        )

        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            OverloadedVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        assertEquals(listOf(1), commitMap.getParents(0))
        assertEquals(listOf(2), commitMap.getParents(1))
        assertEquals(emptyList<Int>(), commitMap.getParents(2))
    }

    @Suppress("unused")
    private class FakeRowInfoNode(
        private val commit: Int,
        private val downRows: List<Int>,
        private val upRows: List<Int>
    ) : RowInfo<Int> {
        override fun getCommit(): Int = commit
        override fun getOneOfHeads(): Int = commit
        override fun getPrintElements(): Collection<PrintElement> = emptyList()
        override fun getRowType(): RowType = RowType.NORMAL
        override fun getAdjacentRows(down: Boolean): List<Int> = if (down) downRows else upRows
    }

    @Suppress("unused")
    private class FakeVisibleGraph(private val nodes: List<FakeRowInfoNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeRowInfoNode = nodes[index]
    }

    @Suppress("unused")
    private class OverloadedNode(
        private val booleanRows: List<Int>,
        private val intRows: List<Int>
    ) {
        fun getAdjacentRows(down: Boolean): List<Int> = if (down) booleanRows else emptyList()
        fun getAdjacentRows(index: Int): List<Int> = if (index >= 0) intRows else emptyList()
    }

    @Suppress("unused")
    private class OverloadedVisibleGraph(private val nodes: List<OverloadedNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): OverloadedNode = nodes[index]
    }
}

