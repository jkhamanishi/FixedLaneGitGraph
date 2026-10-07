@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph.algorithm

import jkhamanishi.git.graph.CommitMap

/**
 * Graph layout algorithm that assigns commits to lanes
 * Uses a sequential path determination algorithm to assign commits to lanes
 */
object GraphManager {

    fun computeNodeLanes(commitMap: CommitMap, nodesCount: Int): HashMap<Int, Int> {
        val graph = Graph()
        val nodeToLane = HashMap<Int, Int>()
        val nullVertex = GraphVertex(NULL_VERTEX_ID)

        // Build vertex graph
        for (i in 0 until nodesCount) {
            graph.vertices.add(GraphVertex(i))
        }

        // Build parent-child relationships
        for (i in 0 until nodesCount) {
            val parents = commitMap.getParents(i)
            for (parentIdx in parents) {
                if (parentIdx >= 0 && parentIdx < nodesCount) {
                    graph.vertices[i].parents.add(graph.vertices[parentIdx])
                    graph.vertices[parentIdx].children.add(graph.vertices[i])
                } else {
                    // Parent out of visible range
                    graph.vertices[i].parents.add(nullVertex)
                }
            }
        }

        // Process vertices
        var i = 0
        while (i < graph.vertices.size) {
            val vertex = graph.vertices[i]
            if (vertex.getNextParent() != null || vertex.onBranch == null) {
                determinePath(graph, i)
            } else {
                i++
            }
        }

        // Extract lane assignments
        for (vertex in graph.vertices) {
            if (vertex.onBranch != null) {
                nodeToLane[vertex.id] = vertex.x
            }
        }

        return nodeToLane
    }

    private fun determinePath(graph: Graph, startAt: Int) {
        var i = startAt + 1
        var vertex = graph.vertices[startAt]
        var parentVertex = vertex.getNextParent()
        var lastPoint: GraphPoint = if (vertex.onBranch == null) {
            GraphPoint(vertex.nextX, vertex.id)
        } else {
            GraphPoint(vertex.x, vertex.id)
        }

        if (parentVertex != null && parentVertex.id != NULL_VERTEX_ID &&
            vertex.isMerge() && vertex.onBranch != null && parentVertex.onBranch != null
        ) {
            // CASE 1: Merge between two existing branches
            var foundPointToParent = false
            val parentBranch = parentVertex.onBranch!!
            while (i < graph.vertices.size) {
                val curVertex = graph.vertices[i]
                val curPoint: Int? = curVertex.getPointConnectingTo(parentVertex, parentBranch)
                val curPointCoord = if (curPoint != null) {
                    foundPointToParent = true
                    GraphPoint(curPoint, curVertex.id)
                } else {
                    GraphPoint(curVertex.nextX, curVertex.id)
                }

                parentBranch.addLine(lastPoint, curPointCoord, !foundPointToParent && curVertex !== parentVertex)
                curVertex.registerUnavailablePoint(curPointCoord.x, parentVertex, parentBranch)
                lastPoint = curPointCoord

                if (foundPointToParent) {
                    vertex.registerParentProcessed()
                    break
                }
                i++
            }
        } else {
            // CASE 2: Normal branch continuation
            val branch = GraphBranch(graph.getAvailableColour(startAt))
            vertex.onBranch = branch
            vertex.x = lastPoint.x
            vertex.registerUnavailablePoint(lastPoint.x, vertex, branch)

            while (i < graph.vertices.size) {
                val curVertex = graph.vertices[i]
                val curPoint = if (parentVertex === curVertex && curVertex.onBranch != null) {
                    GraphPoint(curVertex.x, curVertex.id)
                } else {
                    GraphPoint(curVertex.nextX, curVertex.id)
                }

                branch.addLine(lastPoint, curPoint, lastPoint.x <= curPoint.x)
                curVertex.registerUnavailablePoint(curPoint.x, parentVertex, branch)
                lastPoint = curPoint

                if (parentVertex === curVertex) {
                    vertex.registerParentProcessed()
                    val parentVertexOnBranch = curVertex.onBranch != null
                    curVertex.onBranch = branch
                    curVertex.x = curPoint.x

                    vertex = parentVertex
                    parentVertex = vertex.getNextParent()
                    if (parentVertex == null || parentVertexOnBranch) {
                        break
                    }
                }
                i++
            }

            if (i == graph.vertices.size && parentVertex != null && parentVertex.id == NULL_VERTEX_ID) {
                vertex.registerParentProcessed()
            }

            branch.end = i
            graph.branches.add(branch)
            if (branch.colour < graph.availableColours.size) {
                graph.availableColours[branch.colour] = i
            }
        }
    }

    private const val NULL_VERTEX_ID = -1
}

