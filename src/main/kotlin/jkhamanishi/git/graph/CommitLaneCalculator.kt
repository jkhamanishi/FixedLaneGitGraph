@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import java.lang.reflect.Method

object CommitLaneCalculator {

    fun computeNodeLanes(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method): HashMap<Int, Int> {
        val commitMap = CommitMap(visibleGraph, nodesCount, getNodeMethod)
        val laneManager = LaneManager()
        val nodeToLane = HashMap<Int, Int>()

        for (i in 0 until nodesCount) {
            val children = commitMap.getChildren(i)
            nodeToLane[i] = getNodeLane(i, children, laneManager, commitMap, nodeToLane)
            freeUpLanes(children, laneManager, nodeToLane, i, commitMap)
        }

        return nodeToLane
    }

    fun getNodeLane(
        currentIndex: Int,
        children: List<CommitMap.ChildRelation>,
        laneManager: LaneManager,
        commitMap: CommitMap,
        nodeToLane: HashMap<Int, Int>
    ): Int {

        // Rule 1: If the current commit has no child commit,
        // assign the outermost lane.
        if (children.isEmpty()) {
            return laneManager.acquireRightmostLane()
        }

        val primaryChild = children[0]
        val childIndex = primaryChild.childIndex
        val parentPos = primaryChild.parentPosition
        val childParents = commitMap.getParents(childIndex)
        val isChildMerge = childParents.size > 1
        val childLane = nodeToLane[childIndex] ?: laneManager.acquireLane()

        // Check if the current commit is a merge commit
        val currentParents = commitMap.getParents(currentIndex)
        val isCurrentMerge = currentParents.size > 1

        // Rule 2: If the current commit is a merge commit and is the first parent
        // of at least one of its children, place it on the leftmost lane
        // to show it's on the primary development line.
        if (isCurrentMerge && isChildMerge && parentPos == 0) {
            return laneManager.acquireLeftmostLane()
        }

        // Rule 3: If the child commit is a merge commit and
        // has the current commit as the first parent,
        // put it in the same lane.
        if (isChildMerge && parentPos == 0) {
            return childLane
        }

        // Rule 4: If the child commit is a merge commit and
        // has the current commit as the second parent,
        // assign a new rightmost lane (not a reused interior lane).
        if (isChildMerge && parentPos >= 1) {
            return laneManager.acquireRightmostLane()
        }

        // Rule 5: If the child commit is not a merge commit,
        // make the current commit the same lane as the child commit.
        return childLane
    }

    fun freeUpLanes(
        children: List<CommitMap.ChildRelation>,
        laneManager: LaneManager,
        nodeToLane: HashMap<Int, Int>,
        currentIndex: Int,
        commitMap: CommitMap
    ) {
        if (children.size <= 1) {
            return
        }

        val currentLane = nodeToLane[currentIndex]

        // If there are multiple children, make any lane not used by the primary path available
        for (i in 1 until children.size) {
            val child = children[i]
            val childLane = nodeToLane[child.childIndex]
            val isChildMerge = commitMap.getParents(child.childIndex).size > 1

            if (childLane != null && childLane != currentLane && !isChildMerge) {
                laneManager.freeUpLane(childLane)
            }
        }
    }
}