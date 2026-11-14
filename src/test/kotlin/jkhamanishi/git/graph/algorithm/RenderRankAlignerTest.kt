package jkhamanishi.git.graph.algorithm

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RenderRankAlignerTest {

    @Test
    fun `empty lanes left of a passing edge are compacted so drawn column equals lane`() {
        // Row 0 (A, lane 2) -> row 2 (C, lane 2) passes through row 1 (B, lane 0).
        // IntelliJ draws A at rank 0 (no edge to its left), so the lane must become 0.
        // B then sits right of the passing edge, so its lane becomes 1.
        val lanes = intArrayOf(2, 0, 2)
        val edges = listOf(0 to 2)

        val aligned = RenderRankAligner.align(lanes, edges)

        assertArrayEquals(intArrayOf(0, 1, 0), aligned)
    }

    @Test
    fun `already rank-consistent lanes are returned unchanged`() {
        val lanes = intArrayOf(0, 1, 0)
        val edges = listOf(0 to 2)

        assertArrayEquals(lanes, RenderRankAligner.align(lanes, edges))
    }
}
