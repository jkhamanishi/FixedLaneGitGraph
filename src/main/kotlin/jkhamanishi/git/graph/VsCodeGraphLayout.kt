@file:Suppress("UnstableApiUsage")

package jkhamanishi.git.graph

import java.lang.reflect.Method

/**
 * Graph layout algorithm inspired by vscode-git-graph
 * Uses a sequential path determination algorithm to assign commits to lanes
 */
object VsCodeGraphLayout {

    private data class Vertex(
        val id: Int,
        val children: MutableList<Vertex> = mutableListOf(),
        val parents: MutableList<Vertex> = mutableListOf(),
    ) {
        var x: Int = 0 // Current lane position
        var nextX: Int = 0 // Next available x position
        var onBranch: Branch? = null
        var nextParent: Int = 0
        val connections: MutableList<UnavailablePoint> = mutableListOf()

        fun getNextParent(): Vertex? {
            return if (nextParent < parents.size) parents[nextParent] else null
        }

        fun registerParentProcessed() {
            nextParent++
        }

        fun isMerge(): Boolean = parents.size > 1

        fun registerUnavailablePoint(x: Int, connectsToVertex: Vertex?, onBranch: Branch) {
            if (x == nextX) {
                nextX = x + 1
                connections.add(UnavailablePoint(connectsToVertex, onBranch))
            }
        }

        fun getPointConnectingTo(vertex: Vertex?, onBranch: Branch): Int? {
            for (i in connections.indices) {
                if (connections[i].connectsTo === vertex && connections[i].onBranch === onBranch) {
                    return i
                }
            }
            return null
        }
    }

    private data class UnavailablePoint(
        val connectsTo: Vertex?,
        val onBranch: Branch
    )

    private data class Point(val x: Int, val y: Int)

    private data class Line(
        val p1: Point,
        val p2: Point,
        val lockedFirst: Boolean
    )

    private class Branch(val colour: Int) {
        val lines: MutableList<Line> = mutableListOf()
        var end: Int = 0

        fun addLine(p1: Point, p2: Point, lockedFirst: Boolean) {
            lines.add(Line(p1, p2, lockedFirst))
        }
    }

    private class Graph {
        val vertices: MutableList<Vertex> = mutableListOf()
        val branches: MutableList<Branch> = mutableListOf()
        val availableColours: MutableList<Int> = mutableListOf()

        fun getAvailableColour(startAt: Int): Int {
            for (i in availableColours.indices) {
                if (startAt > availableColours[i]) {
                    return i
                }
            }
            availableColours.add(0)
            return availableColours.size - 1
        }
    }

    fun computeNodeLanes(commitMap: CommitMap, nodesCount: Int): HashMap<Int, Int> {
        val graph = Graph()
        val nodeToLane = HashMap<Int, Int>()
        val nullVertex = Vertex(NULL_VERTEX_ID)

        // Build vertex graph
        for (i in 0 until nodesCount) {
            graph.vertices.add(Vertex(i))
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
                determinePath(graph, i, nodeToLane)
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

    private fun determinePath(graph: Graph, startAt: Int, nodeToLane: HashMap<Int, Int>) {
        var i = startAt
        var vertex = graph.vertices[startAt]
        var parentVertex = vertex.getNextParent()
        var lastPoint: Point = if (vertex.onBranch == null) {
            Point(vertex.nextX, vertex.id)
        } else {
            Point(vertex.x, vertex.id)
        }

        if (parentVertex != null && parentVertex.id != NULL_VERTEX_ID &&
            vertex.isMerge() && vertex.onBranch != null && parentVertex.onBranch != null
        ) {
            // CASE 1: Merge between two existing branches
            var foundPointToParent = false
            val parentBranch = parentVertex.onBranch!!
            i = startAt + 1
            while (i < graph.vertices.size) {
                val curVertex = graph.vertices[i]
                var curPoint: Int? = curVertex.getPointConnectingTo(parentVertex, parentBranch)
                val curPointCoord = if (curPoint != null) {
                    foundPointToParent = true
                    Point(curPoint, curVertex.id)
                } else {
                    Point(curVertex.nextX, curVertex.id)
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
            val branch = Branch(graph.getAvailableColour(startAt))
            vertex.onBranch = branch
            vertex.x = lastPoint.x
            vertex.registerUnavailablePoint(lastPoint.x, vertex, branch)

            i = startAt + 1
            while (i < graph.vertices.size) {
                val curVertex = graph.vertices[i]
                val curPoint = if (parentVertex === curVertex && curVertex.onBranch != null) {
                    Point(curVertex.x, curVertex.id)
                } else {
                    Point(curVertex.nextX, curVertex.id)
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

