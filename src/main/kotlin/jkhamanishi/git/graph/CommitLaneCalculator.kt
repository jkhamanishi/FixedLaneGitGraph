@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import java.lang.reflect.Method

/**
 * Lane calculation orchestrator using the vscode-git-graph algorithm
 *
 * This replaces the previous 5-rule heuristic-based approach with a sequential
 * path determination algorithm inspired by vscode-git-graph extension.
 *
 * The algorithm:
 * 1. Builds a complete DAG of commits with parent-child relationships
 * 2. Assigns commits to branches using sequential path determination
 * 3. Reuses colors (lanes) aggressively as branches complete
 * 4. Handles merges by routing edges through intermediate commits
 */
object CommitLaneCalculator {

fun computeNodeLanes(visibleGraph: Any, nodesCount: Int, getNodeMethod: Method): HashMap<Int, Int> {
    val commitMap = CommitMap(visibleGraph, nodesCount, getNodeMethod)
    return VsCodeGraphLayout.computeNodeLanes(commitMap, nodesCount)
}
}