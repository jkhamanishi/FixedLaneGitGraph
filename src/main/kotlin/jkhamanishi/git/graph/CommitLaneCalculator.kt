@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import java.lang.reflect.Method

object CommitLaneCalculator {

    private fun bindLane(laneManager: LaneManager, currentIndex: Int, lane: Int): Int {
        laneManager.assignLane(currentIndex, lane)
        return lane
    }

    private fun getPreferredChild(children: List<CommitMap.ChildRelation>): CommitMap.ChildRelation {
        return children.firstOrNull { it.parentPosition == 0 } ?: children.first()
    }

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
            return bindLane(laneManager, currentIndex, laneManager.acquireRightmostLane())
        }

        // When a commit has multiple children, prefer the first-parent continuation.
        // A secondary-parent merge child should not displace the ongoing branch lane.
        val primaryChild = getPreferredChild(children)
        val childIndex = primaryChild.childIndex
        val parentPos = primaryChild.parentPosition
        val childParents = commitMap.getParents(childIndex)
        val isChildMerge = childParents.size > 1

        // Check if the current commit is a merge commit
        val currentParents = commitMap.getParents(currentIndex)
        val isCurrentMerge = currentParents.size > 1

        // Rule 2: If the current commit is a merge commit and is the first parent
        // of at least one of its children, place it on the leftmost lane
        // to show it's on the primary development line.
        if (isCurrentMerge && isChildMerge && parentPos == 0) {
            return bindLane(laneManager, currentIndex, laneManager.acquireLeftmostLane())
        }

        // For non-merge commits in a linear chain, try to reuse the primary parent's lane
        // to keep the chain visually continuous.
        var childLane: Int? = nodeToLane[childIndex]
        if (childLane == null && !isCurrentMerge && currentParents.size == 1) {
            // This is a non-merge commit with one parent. Try to get the parent's lane.
            val primaryParentIndex = currentParents[0]
            childLane = nodeToLane[primaryParentIndex]
        }

        val effectiveChildLane = childLane ?: laneManager.acquireLane()

        // Rule 3: If the child commit is a merge commit and
        // has the current commit as the first parent,
        // put it in the same lane.
        if (isChildMerge && parentPos == 0) {
            return bindLane(laneManager, currentIndex, effectiveChildLane)
        }

        // Rule 4: If the child commit is a merge commit and
        // has the current commit as the second parent,
        // assign a new rightmost lane (not a reused interior lane).
        // However, if the current commit is already on a single-parent chain,
        // keep the chain anchored to that parent's lane so earlier branch commits
        // do not drift onto the merge's side lane.
        if (isChildMerge && parentPos >= 1) {
            val primaryParentLane = currentParents.firstOrNull()?.let { nodeToLane[it] }
            if (currentParents.size == 1 && primaryParentLane != null) {
                return bindLane(laneManager, currentIndex, primaryParentLane)
            }
            if (nodeToLane.containsKey(childIndex)) {
                return bindLane(laneManager, currentIndex, laneManager.acquireRightmostLane())
            }
            return bindLane(laneManager, currentIndex, effectiveChildLane)
        }

        // Rule 5: If the child commit is not a merge commit,
        // make the current commit the same lane as the child commit.
        return bindLane(laneManager, currentIndex, effectiveChildLane)
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
        val preferredChild = getPreferredChild(children)

        // If there are multiple children, make any lane not used by the preferred continuation
        // available. However, never free lanes of secondary-parent children, as they are part
        // of branches that merge in and should maintain visual stability.
        for (child in children) {
            if (child == preferredChild) {
                continue
            }
            val childLane = nodeToLane[child.childIndex]
            val isChildMerge = commitMap.getParents(child.childIndex).size > 1
            val isSecondaryParentPath = child.parentPosition >= 1

            if (childLane != null && childLane != currentLane && !isChildMerge && !isSecondaryParentPath) {
                laneManager.freeUpLane(childLane)
            }
        }
    }
}