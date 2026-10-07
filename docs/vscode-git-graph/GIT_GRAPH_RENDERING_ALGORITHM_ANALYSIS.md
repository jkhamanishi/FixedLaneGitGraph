# Git Graph Rendering Algorithm Analysis
## vscode-git-graph Repository

### Overview
The vscode-git-graph extension renders Git commit history as an interactive visual graph with branches, merge commits, and commits arranged in lanes. The rendering algorithm is implemented in TypeScript in the `web/graph.ts` file and orchestrated by the main UI logic in `web/main.ts`.

---

## 1. Core Data Structures

### 1.1 Vertex (Represents a Commit)
```typescript
class Vertex {
  id: number                    // Commit index
  isStash: boolean              // Whether this is a stash commit
  x: number                     // Current lane position (horizontal)
  children: Vertex[]            // Direct child commits
  parents: Vertex[]             // Parent commits (1 for normal, 2+ for merges)
  nextParent: number            // Index of next parent to process
  onBranch: Branch | null       // Which branch this vertex belongs to
  isCommitted: boolean          // Whether changes are committed
  isCurrent: boolean            // Whether this is the checked-out commit
  nextX: number                 // Next available x position on this vertex
  connections: UnavailablePoint[] // Tracking points used for connections
}
```

**Key Methods:**
- `addChild(vertex)` / `addParent(vertex)` - Build the commit graph DAG
- `addToBranch(branch, x)` - Assign vertex to a branch at position x
- `getNextParent()` - Retrieve the next parent to process
- `getPoint()` / `getNextPoint()` - Get coordinates for drawing

### 1.2 Branch (Represents a Git Branch Path)
```typescript
class Branch {
  colour: number                // Color index for the branch
  end: number                   // Y-coordinate where branch terminates
  lines: Line[]                 // Segments connecting vertices on this branch
  numUncommitted: number        // Count of uncommitted change segments
}
```

**Key Methods:**
- `addLine(p1, p2, isCommitted, lockedFirst)` - Add a line segment
- `draw(svg, config, expandAt)` - Render all lines of this branch to SVG

**Line Coordinate System:**
- A `Line` connects two `Point` objects: `{x: number, y: number}`
- X-coordinate: Lane position (0, 1, 2, ... for different branches)
- Y-coordinate: Commit index (0 = first/newest commit)
- `lockedFirst`: Controls how diagonal transitions are rendered (angular vs. curved)

### 1.3 Graph (Main Rendering Engine)
```typescript
class Graph {
  vertices: Vertex[]            // All commits in order
  branches: Branch[]            // All branches being tracked
  availableColours: number[]    // Color allocation tracking
  commitLookup: {}              // Maps commit hash → vertex index
  expandedCommitIndex: number   // Index of expanded commit (if any)
}
```

---

## 2. Graph Loading & Initialization

### 2.1 loadCommits() - Build the DAG Structure
This is the first phase: constructing the directed acyclic graph from commit data.

```
loadCommits(commits, commitHead, commitLookup, onlyFollowFirstParent)
├── Create vertex for each commit
├── Create NULL_VERTEX for out-of-view parents
├── For each commit:
│   └── For each parent:
│       ├── If parent is in visible commits → link vertices
│       ├── Else if (not onlyFollowFirstParent || j===0) → link to NULL_VERTEX
│       └── Add child reference to parent
├── Mark uncommitted changes
├── Mark current (checked-out) commit
└── For each unprocessed vertex:
    └── Call determinePath(i) to assign lanes and branches
```

**Key Insights:**
- The algorithm creates a complete DAG of all commits
- Commits outside the visible range are represented by a single NULL_VERTEX
- The `onlyFollowFirstParent` flag filters merge commits (useful for seeing linear history)

---

## 3. Path Determination Algorithm (Core Lane Assignment)

### 3.1 determinePath() - The Main Lane Algorithm
This is the heart of the graph layout. It determines which branch a commit belongs to and where it should be positioned.

```
determinePath(startAt: number)
  vertex = vertices[startAt]
  parentVertex = vertex.getNextParent()
  lastPoint = vertex's current position or next available position
  
  IF (vertex is merge) AND (both vertices on branches already) THEN
    // CASE 1: Merge between two existing branches
    Create path from vertex through all vertices to parent
    Find existing point connecting to parent
    Register this point as used
    
  ELSE
    // CASE 2: Normal branch continuation
    Create new Branch with an available color
    Assign vertex to this branch at current position
    
    For each subsequent vertex until we reach the parent:
      Get next available point on this vertex
      Draw line from lastPoint to this new point
      Register point as used
      
      If we've reached the parent:
        Move parent to this branch
        Advance parentVertex
        If parent already on branch → stop
    
    Record branch end position
    Add branch to graph's branch list
    Mark color as used up to this vertex
```

### 3.2 Key Algorithm Details

**Color Allocation:**
```typescript
getAvailableColour(startAt: number): number {
  for (let i = 0; i < availableColours.length; i++) {
    if (startAt > availableColours[i]) {
      return i;  // Color is "free" after this point
    }
  }
  // No free color, allocate new one
  availableColours.push(0);
  return availableColours.length - 1;
}
```
- Branches reuse colors once they're "done" (terminated)
- A color is available once all commits using it are in the past

**Unavailable Points Tracking:**
```typescript
vertex.registerUnavailablePoint(x, connectsToVertex, onBranch)
  if (x === nextX) {
    nextX = x + 1;  // Move available position right
    connections[x] = {connectsTo: connectsToVertex, onBranch};
  }
```
- Each vertex tracks which x-positions are "taken" by incoming connections
- This prevents different branches from using the same lane at the same commit

**Merge Handling:**
- When a merge commit connects two branches, the algorithm:
  1. Follows the parent's branch lane
  2. Draws a connection line through intermediate commits
  3. Registers each intermediate point as "connects to parent on this branch"
  4. Avoids creating new branches when possible

---

## 4. Rendering Phase

### 4.1 render() - Main Rendering Function
```
render(expandedCommit)
  Create SVG group element
  
  For each branch:
    branch.draw(group, config, expandedCommitIndex)
  
  For each vertex:
    vertex.draw(group, config, expandOffset, listeners)
  
  Update SVG dimensions
  Update gradient mask for fade effect
```

### 4.2 Branch.draw() - Convert Logic Coordinates to SVG Paths

**Step 1: Convert to Pixel Coordinates**
```
For each line in branch:
  x1, y1 = point1.x * grid.x + offsetX, point1.y * grid.y + offsetY
  x2, y2 = point2.x * grid.x + offsetX, point2.y * grid.y + offsetY
  
  Handle expanded commits:
    - If line crosses expansion → split into multiple segments
    - Adjust y-coordinates to account for expanded commit details panel
```

**Step 2: Simplify Vertical Lines**
```
Collapse consecutive vertical lines:
  If line1 ends where line2 starts (vertically)
    → Merge into single line to reduce SVG size
```

**Step 3: Generate SVG Paths**
```
For each line:
  If vertical (x1 === x2):
    Add straight line: "L x2,y2"
    
  Else (diagonal transition):
    If Angular style:
      "L x2,(y2 - d)" + "L x2,y2"  // Two line segments
      
    Else (Curved style):
      "C x1,(y1+d) x2,(y2-d) x2,y2"  // Cubic Bézier curve
      
      Where d = grid.y * 0.8 (or 0.38 for angular)
```

**SVG Path Commands:**
- `M x,y` - Move to point
- `L x,y` - Line to point
- `C x1,y1 x2,y2 x,y` - Cubic Bézier curve

### 4.3 Vertex.draw() - Render Commit Circles
```
Draw circle at (x * grid.x + offsetX, id * grid.y + offsetY)
  
  If current (checked-out):
    Draw hollow circle with colored stroke
    Radius: 4
    
  Else:
    Draw filled circle with branch color
    Radius: 4
  
  If stash commit:
    Draw outer circle (radius 4.5)
    Draw inner circle (radius 2)
    Creates "stash" visual indicator
```

---

## 5. Coordinate Systems

### 5.1 Logical Coordinates (During Layout)
- **X-axis**: Lane number (0, 1, 2, ...)
- **Y-axis**: Commit index (0 = newest, increasing downward)
- **Point format**: `{ x: number, y: number }`

### 5.2 Pixel Coordinates (During Rendering)
```
pixel.x = logicalX * grid.x + grid.offsetX
pixel.y = logicalY * grid.y + grid.offsetY

grid.x   = horizontal spacing between lanes (typically ~25px)
grid.y   = vertical spacing between commits (typically ~25px)
offsetX  = left margin (typically ~10px)
offsetY  = top margin (typically ~10px)
```

### 5.3 SVG Canvas Coordinates
- Direct pixel coordinates in SVG namespace
- Rendered as `<path>` elements with stroke style

---

## 6. Special Features

### 6.1 Expanded Commits
When a commit is expanded to show details:
```
For each line crossing the expansion:
  If locked to first point:
    - Keep transition at original y
    - Extend vertical line over expansion
    
  Else (locked to second point):
    - Transition moves to after expansion
    - Adjust y-coordinates for expanded height
```

### 6.2 Merge Edge Routing
Long merge edges (edges spanning many commits) are identified and handled:
- Follow parent's branch lane when possible
- Create connection points at each intermediate commit
- Prevents "spaghetti" crossing patterns

### 6.3 Graph Styles

**Angular Style:**
- Uses two line segments for transitions
- More geometric appearance
- Smaller transition height (d = 0.38 * grid.y)

**Curved Style:**
- Uses cubic Bézier curves
- Smoother appearance
- Larger transition height (d = 0.8 * grid.y)

### 6.4 Uncommitted Changes Representation
```
Uncommitted line:
  Stroke color: #808080 (gray)
  Stroke dasharray: "2px" (dotted pattern)
  
Committed line:
  Stroke color: branch color (from config.colours array)
```

---

## 7. Algorithm Complexity Analysis

### Time Complexity
```
loadCommits():     O(C × P) where C = commits, P = avg parents
determinePath():   O(C²) worst case (for each commit, scan to parent)
render():          O(C × L) where L = avg lines per branch
drawing():         O(L) where L = total line segments
```

### Space Complexity
```
O(C + E) where:
  C = number of commits
  E = number of edges (parent-child relationships)
```

### Optimization Techniques
1. **Color reuse** - Reduces branch width as they complete
2. **Line simplification** - Merges consecutive vertical segments
3. **SVG batching** - Groups lines into paths for efficiency
4. **Lazy rendering** - Only renders visible commits

---

## 8. Integration Flow

### Complete Rendering Pipeline

```
1. Data Source (Git)
   ↓
2. loadCommits()
   - Parse commits and parent relationships
   - Create vertex/edge graph
   ↓
3. determinePath() [called for each unprocessed vertex]
   - Assign vertices to branches
   - Determine lane positions (x-coordinates)
   - Create line segments
   ↓
4. render()
   - Convert logical coords → pixel coords
   - Generate SVG paths
   - Add vertex circles
   ↓
5. SVG Rendering
   - Browser renders `<path>` and `<circle>` elements
   - User sees git graph visualization
```

### User Interactions
- **Hover**: Vertex hover listener shows commit tooltip
- **Click**: Selects commit for details/comparison
- **Scroll**: Triggers responsive updates

---

## 9. Configuration Impact

### Grid Settings
```typescript
grid: {
  x: number,           // Horizontal spacing (lane width)
  y: number,           // Vertical spacing (commit height)
  offsetX: number,     // Left margin
  offsetY: number,     // Top margin
  expandY: number      // Height added for expanded commit
}
```

### Color Settings
```typescript
colours: string[]      // Array of branch colors
                      // Reused cyclically if more branches than colors
```

### Style Settings
- **Angular**: Geometric transitions with 2-segment lines
- **Curved**: Smooth transitions with Bézier curves

---

## 10. Key Insights & Optimizations

### Why This Algorithm Works Well

1. **Single Pass Layout**: `determinePath()` processes commits sequentially, making decisions based only on what's visible in the current window

2. **Lazy Color Assignment**: Colors are reused as branches end, keeping the graph width minimal

3. **Intelligent Merge Handling**: Merges between existing branches don't create unnecessary lane shifts

4. **Hierarchical Structure**: Branches encapsulate their own rendering logic, making code maintainable

### Potential Improvement Areas

1. **Lane Minimization**: The algorithm doesn't guarantee the minimal number of lanes (this is NP-hard for general DAGs)

2. **Ancestry-based Layout**: Could group related branches more intelligently

3. **Edge Bundling**: Long edges could be bundled to reduce visual clutter

---

## 11. Comparison with Other Git Graph Tools

| Feature | vscode-git-graph | Typical Impl |
|---------|------------------|-------------|
| **Lane Algorithm** | Sequential DAG traversal | Force-directed/Sugiyama layering |
| **Merge Handling** | Branches through intermediates | Edge routing post-layout |
| **Color Reuse** | Aggressive (improves width) | Sometimes not reused |
| **Interactivity** | Real-time tooltips | Static SVG |
| **Customization** | Angular/curved styles | Less options |

---

## 12. Summary

The vscode-git-graph rendering algorithm is an elegant solution that:

1. **Builds a DAG** of all commits in a single pass
2. **Assigns lanes** using an online algorithm that processes commits sequentially
3. **Tracks branch paths** through merges without creating visual confusion
4. **Renders efficiently** by converting logical coordinates to SVG paths
5. **Supports interactions** with vertex-level tracking and tooltips

The algorithm prioritizes **visual clarity** and **responsiveness** over achieving the theoretical optimal lane count, making it practical for real-world usage in large repositories.

---

## References

- **Vertex Class**: Lines 165-332 in `web/graph.ts`
- **Branch Class**: Lines 38-160 in `web/graph.ts`
- **Graph Class**: Lines 337-773 in `web/graph.ts`
- **Path Determination**: Lines 705-763 in `web/graph.ts`
- **Rendering**: Lines 442-462 and 75-147 in `web/graph.ts`
- **Main Integration**: Lines 1-100+ in `web/main.ts`

