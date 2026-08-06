package jkhamanishi.git.graph

import java.util.concurrent.ConcurrentHashMap

object PersistentLayoutManager {

    private val logger = ConsoleLogger("PersistentLayoutManager")
    private val nodeLaneMap = ConcurrentHashMap<Int, Int>()
    private var nextLane = 0

    fun assignLane(nodeIndex: Int, lane: Int) {
        nodeLaneMap[nodeIndex] = lane
        if (lane >= nextLane) {
            nextLane = lane + 1
        }
    }

    fun getLaneForNode(nodeIndex: Int, fallbackRawLayout: Int): Int {
        return nodeLaneMap.computeIfAbsent(nodeIndex) {
            val assigned = nextLane++
            logger.debug("Assigned fallback lane $assigned for nodeIndex $nodeIndex")
            assigned
        }
    }

    fun clear() {
        nodeLaneMap.clear()
        nextLane = 0
        logger.debug("Persistent layout manager cleared.")
    }
}