package jkhamanishi.git.graph

import jkhamanishi.git.graph.algorithm.Graph
import jkhamanishi.git.graph.algorithm.GraphBranch
import jkhamanishi.git.graph.algorithm.GraphVertex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphStructureTest {

    @Test
    fun `graph reuses freed colours before allocating new ones`() {
        val graphWithFirstColourFree = Graph()
        graphWithFirstColourFree.availableColours += listOf(0, 3)

        assertEquals(0, graphWithFirstColourFree.getAvailableColour(1))
        assertEquals(0, graphWithFirstColourFree.getAvailableColour(3))

        val graphWithOnlySecondColourFree = Graph()
        graphWithOnlySecondColourFree.availableColours += listOf(10, 3)

        assertEquals(1, graphWithOnlySecondColourFree.getAvailableColour(4))

        val graphWithoutReusableColours = Graph()
        graphWithoutReusableColours.availableColours += listOf(5, 6)

        assertEquals(2, graphWithoutReusableColours.getAvailableColour(4))
    }

    @Test
    fun `vertex tracks parents and merge state`() {
        val firstParent = GraphVertex(1)
        val secondParent = GraphVertex(2)
        val vertex = GraphVertex(0, parents = mutableListOf(firstParent, secondParent))

        assertTrue(vertex.isMerge())
        assertEquals(firstParent, vertex.getNextParent())

        vertex.registerParentProcessed()
        assertEquals(secondParent, vertex.getNextParent())

        vertex.registerParentProcessed()
        assertEquals(null, vertex.getNextParent())
    }

    @Test
    fun `vertex only reserves sequential points and can look them up by connection`() {
        val firstParent = GraphVertex(1)
        val secondParent = GraphVertex(2)
        val firstBranch = GraphBranch(0)
        val secondBranch = GraphBranch(1)
        val vertex = GraphVertex(0)

        vertex.registerUnavailablePoint(0, firstParent, firstBranch)
        vertex.registerUnavailablePoint(2, secondParent, secondBranch)
        vertex.registerUnavailablePoint(1, secondParent, secondBranch)

        assertEquals(2, vertex.nextX)
        assertEquals(2, vertex.connections.size)
        assertEquals(0, vertex.getPointConnectingTo(firstParent, firstBranch))
        assertEquals(1, vertex.getPointConnectingTo(secondParent, secondBranch))
        assertEquals(null, vertex.getPointConnectingTo(GraphVertex(9), firstBranch))
        assertFalse(vertex.connections.any { it.connectsTo == secondParent && it.onBranch == secondBranch && vertex.connections.indexOf(it) == 2 })
    }
}



