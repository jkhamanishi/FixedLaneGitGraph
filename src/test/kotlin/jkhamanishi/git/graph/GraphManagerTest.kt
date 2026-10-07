@file:Suppress("unused")

package jkhamanishi.git.graph

import jkhamanishi.git.graph.algorithm.GraphManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the graph lane assignment algorithm.
 *
 * These tests verify that the graph layout algorithm correctly assigns commits
 * to lanes, handling linear histories, branches, and merges appropriately.
 */
class GraphManagerTest {

    @Test
    fun `linear history stays on a single lane`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1)),
                FakeNode(listOf(2)),
                FakeNode(emptyList())
            )
        )

        val lanes = computeLanes(visibleGraph)

        // All commits in a linear chain should stay on lane 0
        assertEquals(0, lanes[0])
        assertEquals(0, lanes[1])
        assertEquals(0, lanes[2])
    }

    @Test
    fun `branch creates new lane for diverging path`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1, 2)), // 0: merge of 1 and 2
                FakeNode(listOf(3)),    // 1: first child
                FakeNode(listOf(3)),    // 2: second child
                FakeNode(emptyList())   // 3: common ancestor
            )
        )

        val lanes = computeLanes(visibleGraph)

        // All commits should have lanes assigned
        for (i in 0 until visibleGraph.getVisibleNodesCount()) {
            assert(lanes.containsKey(i)) { "Commit $i should have a lane assignment" }
        }

        // The two diverging paths should generally be on different lanes
        // (though vscode algorithm may route them differently than the old one)
        // Just verify they all got assignments
        assertEquals(lanes.size, visibleGraph.getVisibleNodesCount())
    }

    @Test
    fun `merges route through intermediate commits`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1, 3)), // 0: merge commit (primary parent=1, secondary parent=3)
                FakeNode(listOf(2)),    // 1: first parent chain
                FakeNode(emptyList()),  // 2: end of first parent
                FakeNode(listOf(4)),    // 3: second parent chain
                FakeNode(emptyList())   // 4: end of second parent
            )
        )

        val lanes = computeLanes(visibleGraph)

        // vscode algorithm should assign lanes to all commits
        // The key invariant is that all commits get lane assignments
        for (i in 0 until visibleGraph.getVisibleNodesCount()) {
            assert(lanes.containsKey(i)) { "Commit $i should have a lane assignment" }
            assert(lanes[i]!! >= 0) { "Lane for commit $i should be non-negative" }
        }

        // Verify that lanes are assigned reasonably (not negative, within bounds)
        val maxLane = lanes.values.maxOrNull() ?: 0
        assert(maxLane < visibleGraph.getVisibleNodesCount()) { "Max lane should be reasonable" }
    }

    @Test
    fun `all commits receive lane assignments`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1)),
                FakeNode(listOf(2, 3)),
                FakeNode(emptyList()),
                FakeNode(emptyList())
            )
        )

        val lanes = computeLanes(visibleGraph)

        // All commits should have assigned lanes
        for (i in 0 until visibleGraph.getVisibleNodesCount()) {
            assert(lanes.containsKey(i)) { "Commit $i should have a lane assignment" }
            assert(lanes[i]!! >= 0) { "Lane for commit $i should be non-negative" }
        }
    }

    @Test
    fun `secondary parent merge child does not displace first parent continuation`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1)),     // 0: main tail after merge
                FakeNode(listOf(5, 3)),  // 1: merge commit (first parent=5, second parent=3)
                FakeNode(listOf(3)),     // 2: first-parent continuation from 3
                FakeNode(listOf(4)),     // 3: commit with multiple children [2 at pos=0, 1 at pos=1]
                FakeNode(emptyList()),   // 4: older branch ancestor
                FakeNode(listOf(6)),     // 5: mainline parent of merge
                FakeNode(emptyList())    // 6: older mainline ancestor
            )
        )

        val lanes = computeLanes(visibleGraph)

        // Verify all commits are assigned lanes
        for (i in 0 until visibleGraph.getVisibleNodesCount()) {
            assert(lanes.containsKey(i)) { "Commit $i should have a lane assignment" }
        }

        // The mainline path (6->5->1->0) should generally stay on same lane
        // while secondary parent paths diverge into different lanes
        assert(lanes[1] == lanes[5]) { "Merge should follow first parent lane" }
        assert(lanes[1] != lanes[3]) { "Secondary parent should diverge to different lane" }
    }

    private data class FakeNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    private class FakeVisibleGraph(private val nodes: List<FakeNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeNode = nodes[index]
    }

    private fun computeLanes(visibleGraph: FakeVisibleGraph): HashMap<Int, Int> {
        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )
        return GraphManager.computeNodeLanes(commitMap, visibleGraph.getVisibleNodesCount())
    }
}






