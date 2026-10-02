package jkhamanishi.git.graph

class LaneManager {
    private val lanes = mutableListOf<Boolean>()

    fun acquireLane(): Int {
        // Find the first available (false) lane index to reuse
        for (i in lanes.indices) {
            if (!lanes[i]) {
                lanes[i] = true
                return i
            }
        }
        // If all existing lanes are occupied, allocate a new one
        lanes.add(true)
        return lanes.size - 1
    }

    fun acquireRightmostLane(): Int {
        lanes.add(true)
        return lanes.size - 1
    }

    fun freeUpLane(lane: Int) {
        if (lane >= 0 && lane < lanes.size) {
            lanes[lane] = false
        }
    }
}