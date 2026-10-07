@file:Suppress("unused")

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
    fun `commit with no children uses outermost lane instead of reusing an inner lane`() {
        val laneManager = LaneManager().apply {
            acquireLane()
            acquireLane()
            acquireLane()
            freeUpLane(1)
        }

        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(emptyList())
            )
        )
        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        val lane = CommitLaneCalculator.getNodeLane(
            currentIndex = 0,
            children = emptyList(),
            laneManager = laneManager,
            commitMap = commitMap,
            nodeToLane = hashMapOf()
        )

        assertEquals(3, lane)
    }

    @Test
    fun `lanes from secondary-parent paths are not freed up`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(emptyList()),  // 0: no parents
                FakeNode(listOf(0)),    // 1: has parent 0 at position 0 (primary)
                FakeNode(listOf(3, 0)), // 2: has parents [3, 0], so 0 is at position 1 (secondary)
                FakeNode(emptyList())   // 3: no parents
            )
        )
        val commitMap = CommitMap(
            visibleGraph,
            visibleGraph.getVisibleNodesCount(),
            FakeVisibleGraph::class.java.getMethod("getNode", Int::class.javaPrimitiveType)
        )

        val laneManager = LaneManager()
        val nodeToLane = hashMapOf<Int, Int>()

        nodeToLane[0] = laneManager.acquireLane()
        nodeToLane[1] = laneManager.acquireLane()
        nodeToLane[2] = laneManager.acquireLane()

        val children = commitMap.getChildren(0)
        CommitLaneCalculator.freeUpLanes(children, laneManager, nodeToLane, 0, commitMap)

        val secondaryChildLane = nodeToLane[2]
        assertEquals(2, secondaryChildLane)
        // Lane 2 should still be occupied (not freed)
        assertEquals(3, laneManager.acquireLane())
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

    @Test
    fun `secondary-parent merge child does not displace first-parent continuation`() {
        val visibleGraph = FakeVisibleGraph(
            listOf(
                FakeNode(listOf(1)),     // 0: main tail after merge
                FakeNode(listOf(5, 3)),  // 1: merge commit, first parent 5 and second parent 3
                FakeNode(listOf(3)),     // 2: first-parent continuation from 3
                FakeNode(listOf(4)),     // 3: problematic commit with children [1(pos=1), 2(pos=0)]
                FakeNode(emptyList()),   // 4: older branch ancestor
                FakeNode(listOf(6)),     // 5: mainline parent of the merge
                FakeNode(emptyList())    // 6: older mainline ancestor
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
        assertEquals(1, lanes[3])
        assertEquals(1, lanes[4])
        assertEquals(0, lanes[5])
        assertEquals(0, lanes[6])
    }

    private data class FakeNode(private val parents: List<Int>) {
        fun getAdjacentRows(): List<Int> = parents
    }

    private class FakeVisibleGraph(private val nodes: List<FakeNode>) {
        fun getVisibleNodesCount(): Int = nodes.size
        fun getNode(index: Int): FakeNode = nodes[index]
    }
}






