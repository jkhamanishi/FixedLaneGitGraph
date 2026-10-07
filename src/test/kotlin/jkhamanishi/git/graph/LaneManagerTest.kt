package jkhamanishi.git.graph

import org.junit.Assert.assertEquals
import org.junit.Test

class LaneManagerTest {

    @Test
    fun `acquireLane reuses the leftmost free lane first`() {
        val laneManager = LaneManager().apply {
            acquireLane() // 0
            acquireLane() // 1
            acquireLane() // 2
            freeUpLane(0)
            freeUpLane(1)
        }

        assertEquals(0, laneManager.acquireLane())
        assertEquals(1, laneManager.acquireLane())
    }
}

