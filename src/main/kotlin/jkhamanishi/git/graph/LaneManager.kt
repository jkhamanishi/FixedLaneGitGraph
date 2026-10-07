package jkhamanishi.git.graph

class LaneManager {
    private val lanes = mutableListOf<Boolean>()
    private val reservedLanes = linkedSetOf<Int>()

    fun assignLane(nodeIndex: Int, lane: Int): Int {
        ensureLaneExists(lane)
        lanes[lane] = true
        reservedLanes += lane
        return lane
    }

    fun reserveLane(lane: Int): Int {
        ensureLaneExists(lane)
        lanes[lane] = true
        reservedLanes += lane
        return lane
    }

    fun acquireLane(): Int {
        // Find the first available (false) lane index to reuse.
        // Main-line commits naturally consolidate into lower lane numbers,
        // leaving higher lanes for secondary branches.
        for (i in lanes.indices) {
            if (!lanes[i]) {
                lanes[i] = true
                reservedLanes += i
                return i
            }
        }
        // If all existing lanes are occupied, allocate a new one
        lanes.add(true)
        val lane = lanes.size - 1
        reservedLanes += lane
        return lane
    }

    fun acquireRightmostLane(): Int {
        lanes.add(true)
        val lane = lanes.size - 1
        reservedLanes += lane
        return lane
    }

    fun acquireLeftmostLane(): Int {
        // Ensure the leftmost lane (lane 0) is allocated and marked as occupied
        if (lanes.isEmpty()) {
            lanes.add(true)
        } else if (!lanes[0]) {
            lanes[0] = true
        }
        reservedLanes += 0
        return 0
    }

    fun freeUpLane(lane: Int) {
        if (lane >= 0 && lane < lanes.size) {
            lanes[lane] = false
            reservedLanes.remove(lane)
        }
    }

    private fun ensureLaneExists(lane: Int) {
        while (lane >= lanes.size) {
            lanes.add(false)
        }
    }
}