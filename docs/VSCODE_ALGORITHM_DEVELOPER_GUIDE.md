# VsCode-Style Graph Algorithm - Developer Guide
## FixedLaneGitGraph Plugin

### Quick Start

The plugin now uses the vscode-git-graph sequential path determination algorithm for lane assignment. This guide explains how it works and how to maintain/extend it.

---

## Architecture Overview

### Data Flow

```
1. CommitLaneCalculator.computeNodeLanes()
   ├─ Creates CommitMap from IntelliJ's visibleGraph
   │  (extracts commit parents via reflection)
   │
   └─ Calls VsCodeGraphLayouter.computeNodeLanes()
      ├─ Creates Vertex DAG from CommitMap
      │  (each commit becomes a Vertex)
      │
      ├─ Iterates through vertices
      │  └─ For each unprocessed vertex:
      │     └─ Call determinePath()
      │
      └─ Returns HashMap<Int, Int>
         (commit index → lane position)
```

### Core Data Structures

#### Vertex
Represents a single commit in the graph:
```kotlin
data class Vertex(
    val id: Int,                    // Commit index
    val children: MutableList<Vertex> = mutableListOf(),
    val parents: MutableList<Vertex> = mutableListOf(),
) {
    var x: Int = 0                  // Current lane position
    var nextX: Int = 0              // Next available x position
    var onBranch: Branch? = null    // Current branch assignment
    var nextParent: Int = 0         // Parent processing index
    val connections: MutableList<UnavailablePoint> = mutableListOf()
    // Methods: getNextParent(), registerParentProcessed(), isMerge(), etc.
}
```

#### Branch
Represents a visual branch path:
```kotlin
class Branch(val colour: Int) {
    val lines: MutableList<Line> = mutableListOf()  // Path segments
    var end: Int = 0                                // Where branch ends
    
    fun addLine(p1: Point, p2: Point, lockedFirst: Boolean)
}
```

#### Line
A segment connecting two points:
```kotlin
data class Line(
    val p1: Point,              // Start point
    val p2: Point,              // End point
    val lockedFirst: Boolean    // Connection style
)
```

---

## Algorithm Deep Dive

### Phase 1: DAG Construction

```kotlin
// Build vertices
for (i in 0 until nodesCount) {
    graph.vertices.add(Vertex(i))
}

// Build parent-child relationships
for (i in 0 until nodesCount) {
    val parents = commitMap.getParents(i)
    for (parentIdx in parents) {
        // Link vertices bidirectionally
        graph.vertices[i].parents.add(parentIdx)
        graph.vertices[parentIdx].children.add(i)
    }
}
```

### Phase 2: Sequential Path Determination

The `determinePath(graph, startIndex, nodeToLane)` function:

**Entry Point**: Called for each unprocessed vertex

**Case 1: Merge Between Existing Branches**
```
IF vertex is merge commit AND
   vertex already on branch AND
   parent already on branch
THEN
    Follow parent's branch through intermediates
    Route edge using existing lanes
    Avoid creating new branch
```

**Case 2: Normal Branch Continuation**
```
ELSE
    Create new Branch with available color
    Assign vertex to this branch
    
    FOR each subsequent vertex until parent reached:
        Get next available point
        Add line segment
        Mark position as used
        If parent found: advance to parent
    
    Record branch end
    Add to graph's branch list
```

### Phase 3: Color (Lane) Reuse

```kotlin
private fun getAvailableColour(startAt: Int): Int {
    // Find first color not in use after startAt index
    for (i in availableColours.indices) {
        if (startAt > availableColours[i]) {
            return i  // Color is available
        }
    }
    // No available color, allocate new one
    availableColours.add(0)
    return availableColours.size - 1
}
```

**Key Insight**: Colors are "freed" as branches end. A branch's color is available once all commits using it are in the past (higher indices = older commits).

---

## Coordinate Systems

### Logical Coordinates (Algorithm Space)
Used during lane assignment:
- **X-axis**: Lane number (0, 1, 2, ...)
- **Y-axis**: Commit index (0 = newest)
- **Structure**: `Point(x: Int, y: Int)`

### Pixel Coordinates (Rendering Space)
Used by IntelliJ renderer:
```
pixelX = logicalX * gridX + offsetX
pixelY = logicalY * gridY + offsetY
```

This conversion happens in PersistentLayoutManager before rendering.

---

## Merge Handling Strategy

### Primary Parent (First Parent)

When a merge commit has a primary parent:
1. Merge follows the primary parent's lane
2. Creates visual continuity on the main line
3. No new lane needed

### Secondary Parent (Second, Third, ... Parents)

When a merge commits has secondary parents:
1. Secondary parent gets routed on a different lane
2. Merge edge connects from secondary parent through intermediate commits
3. Each intermediate commit tracks "connects to parent on this branch"

### Unavailable Points Tracking

```kotlin
fun registerUnavailablePoint(x: Int, connectsToVertex: Vertex?, onBranch: Branch) {
    if (x == nextX) {  // Only if this is the "next" position
        nextX = x + 1   // Move to next available position
        connections[x] = UnavailablePoint(connectsToVertex, onBranch)
    }
}
```

This prevents different branches from using the same lane at the same commit.

---

## Common Patterns & Examples

### Pattern 1: Linear History
```
A ← B ← C ← D
```
All commits on lane 0. Branches process in single pass.

### Pattern 2: Simple Branch
```
    ┌─ B ─ C
A ┤
    └─ D ─ E
```
- Main line (A→B→C): lane 0
- Branch (A→D→E): lane 1
- Colors allocated: 2

### Pattern 3: Merge
```
    ┌─ B ─ C
A ┤       │
    └─ D ─┘ E
```
- Primary path (A→B→C): lane 0
- Merge E: lane 0 (follows primary)
- Secondary path D: lane 1
- Merge edge D→E routes through lane 1

### Pattern 4: Complex Merges
```
    ┌─ B ──┐
A ┤  ┌─ D ─┼─ E
    └─ C ──┘
```
Multiple merges handled with same logic:
- Each merge follows its primary parent's lane
- Secondary parents get new lanes
- Edges routed through intermediates

---

## Extending the Algorithm

### Adding a New Merge Routing Strategy

1. **Modify `determinePath()`**: Add condition for your strategy
2. **Update lane allocation**: Adjust `getAvailableColour()` if needed
3. **Test edge cases**: Add test cases for new strategy
4. **Update documentation**: Explain the new behavior

### Example: Prefer Secondary Parents

To route secondary parents on main lane instead:
```kotlin
if (parentVertex != null && !parentVertex.isNotOnBranch()) {
    // Check if secondary parent would be better
    val secondaryParent = vertex.parents.getOrNull(1)
    if (secondaryParent != null && shouldFollowSecondary(vertex, secondaryParent)) {
        // Route through secondary parent instead
        determinePath(graph, secondary, nodeToLane)
    }
}
```

### Adding Edge Bundling

To bundle parallel edges:
```kotlin
// In Branch.addLine():
if (shouldBundleEdges(lastPoint, currentPoint)) {
    // Combine with existing edge instead of creating new line
    mergeLineWithExisting(lastPoint, currentPoint)
} else {
    lines.add(Line(lastPoint, currentPoint, lockedFirst))
}
```

---

## Testing Guide

### Test Structure

Tests verify **algorithm invariants** rather than specific lane numbers:

```kotlin
// Good: Tests invariant
val lanes = CommitLaneCalculator.computeNodeLanes(...)
assert(lanes.size == expectedCommitCount)  // All commits assigned

// Bad: Tests specific numbers (too fragile)
assertEquals(0, lanes[0])
assertEquals(1, lanes[1])
```

### Common Test Patterns

```kotlin
// Pattern 1: Verify all commits have lanes
for (i in 0 until nodeCount) {
    assert(lanes.containsKey(i))
}

// Pattern 2: Verify lane validity
for (lane in lanes.values) {
    assert(lane >= 0)
    assert(lane < reasonableMaxLanes)
}

// Pattern 3: Verify separation when expected
assert(lanes[merge] == lanes[primaryParent])
assert(lanes[secondaryParent] != lanes[primaryParent])
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests CommitLaneCalculatorTest

# Run with details
./gradlew test --info
```

---

## Performance Characteristics

### Time Complexity
- **DAG Construction**: O(C × P) where C = commits, P = avg parents
- **Path Determination**: O(C²) worst case
- **Color Allocation**: O(C)
- **Total**: O(C²) for large repos

### Space Complexity
- **Vertices**: O(C)
- **Branches**: O(lanes) = O(min(C, depth))
- **Lane assignments**: O(C)
- **Total**: O(C + E) where E = edges

### Optimization Tips

For very large repositories (>10k commits):

1. **Limit processed range**: Only process visible commits
2. **Cache results**: Reuse lane assignments between renders
3. **Lazy evaluation**: Calculate lanes on-demand per viewport

---

## Debugging Tips

### Enable Detailed Logging

```kotlin
// In VsCodeGraphLayouter:
private val logger = ConsoleLogger("VsCodeGraphLayouter")

// Add logging to determinePath:
logger.info("Processing vertex $startAt")
logger.info("Merge detected: following primary parent")
logger.info("Assigned lane ${vertex.x} to vertex $startAt")
```

### Visualize Lane Assignments

```kotlin
// Add test output:
println("Lane assignments:")
for ((commitIdx, lane) in nodeToLane) {
    println("  Commit $commitIdx → Lane $lane")
}
println("Total lanes used: ${nodeToLane.values.maxOrNull()}")
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Commits missing lane assignments | Unprocessed vertices | Verify all vertices have `nextParent` or `onBranch` set |
| Overlapping lanes | Unavailable point not registered | Check `registerUnavailablePoint()` logic |
| Incorrect merge routing | Parent not found | Verify DAG construction and parent links |
| Performance issues | O(C²) complexity | Implement viewport-based processing |

---

## Migration from Old Algorithm

If reverting is needed:

1. **Revert CommitLaneCalculator.kt**: Restore original 5-rule implementation
2. **Revert CommitLaneCalculatorTest.kt**: Restore original test assertions
3. **Remove VsCodeGraphLayouter.kt**: Delete the new file
4. **Rebuild**: `./gradlew build`

All other components remain unchanged and compatible with both algorithms.

---

## Related Files

- **Algorithm Analysis**: `GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md`
- **Integration Points**: `PersistentLayoutManager.kt`
- **Merge Edge Routing**: `MergeEdgeRouter.kt`
- **Tests**: `CommitLaneCalculatorTest.kt`
- **Reflection Setup**: `CommitMap.kt`

---

## FAQ

**Q: Why use sequential processing instead of force-directed layout?**
A: Sequential processing is O(C²) vs O(C² × iterations) for force-directed, simpler logic, and proven in vscode-git-graph.

**Q: Can I customize lane assignment behavior?**
A: Yes! Modify `determinePath()` or create a new layouter by copying VsCodeGraphLayouter and customizing the logic.

**Q: What if a commit has >2 parents?**
A: The algorithm treats it as a merge with multiple secondary parents. Each gets routed on separate lanes.

**Q: How do I add new graph styles (angular vs curved)?**
A: Styling is done in rendering layer (IntelliJ), not in lane assignment. Modify path generation in rendering code.

**Q: Is the algorithm suitable for very large repositories?**
A: Yes, but may need optimization for real-time responsiveness. Consider viewport-based processing or caching.

---

## Support & Contribution

For questions or improvements:
1. Review the algorithm analysis document
2. Check existing tests for patterns
3. Profile before optimizing
4. Add tests for any new features

