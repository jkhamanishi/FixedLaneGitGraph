# Pipeline and Algorithm Flow

This document describes the current end-to-end flow used by `FixedLaneGitGraph`.

It focuses on the implementation that exists in this repository today, not on the earlier migration
history.

## Overview

The plugin does not replace IntelliJ's Git Log UI. Instead, it hooks into the existing rendering
pipeline and overrides the lane lookup used by the graph renderer.

High-level flow:

```text
Git / Version Control tool window opens
    -> PluginInitializer attaches
    -> GraphRenderingInitializer finds the log table
    -> PersistentLayoutManager inspects VisiblePack / VisibleGraph
    -> CommitMap extracts parent relationships
    -> GraphManager computes fixed lanes
    -> MergeEdgeRouter adjusts long secondary-parent edges
    -> IntelliJ asks for lane positions through the injected proxy
    -> User sees a more stable graph
```

## Runtime entry points

### 1. `PluginInitializer`

File:

- `../src/main/kotlin/jkhamanishi/git/graph/PluginInitializer.kt`

Responsibilities:

- show a startup notification
- listen for the Git or Version Control tool window
- wait until the log table is actually present
- hand control to `GraphRenderingInitializer`

### 2. `GraphRenderingInitializer`

File:

- `../src/main/kotlin/jkhamanishi/git/graph/rendering/GraphRenderingInitializer.kt`

Responsibilities:

- walk the Swing component tree
- locate IntelliJ's Git Log table
- enable long-edge handling
- delegate layout injection to `PersistentLayoutManager`

## Extracting the visible commit graph

### 3. `PersistentLayoutManager`

File:

- `../src/main/kotlin/jkhamanishi/git/graph/rendering/PersistentLayoutManager.kt`

This object performs the most IntelliJ-specific work.

It:

- extracts the current `VisiblePack`
- finds the underlying `VisibleGraph`
- resolves a node-count method and a node getter method via reflection
- builds a `CommitMap`
- computes custom lane assignments
- injects a proxy around IntelliJ's layout getter
- wraps the element comparator so merge-edge routing can be customized consistently

The key idea is that IntelliJ still owns the UI. The plugin only overrides the lane lookup and some
ordering behavior used during rendering.

## Commit relationship extraction

### 4. `CommitMap`

File:

- `../src/main/kotlin/jkhamanishi/git/graph/CommitMap.kt`

`CommitMap` converts IntelliJ's internal visible graph into a simple parent map:

```text
commit index -> list of parent commit indices
```

Important details:

- rows are processed from `0 until nodesCount`
- visible rows are ordered from newer to older commits
- parent rows are discovered through `getAdjacentRows(...)`
- reflection is used because the exact row/node APIs are not stable across implementations
- parents outside the visible range are allowed and handled later by the algorithm

Example:

```text
0 -> [1, 2]
1 -> [3]
2 -> [3]
3 -> []
```

## Lane assignment algorithm

### 5. `GraphManager`

File:

- `../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphManager.kt`

`GraphManager.computeNodeLanes(commitMap, nodesCount)` returns:

```kotlin
HashMap<Int, Int> // node index -> lane
```

The implementation is inspired by vscode-git-graph, but adapted to the current Kotlin codebase and
to IntelliJ's rendering model.

### Phase 1: build the internal graph

Supporting classes:

- `Graph`
- `GraphVertex`
- `GraphBranch`
- `GraphPoint`
- `GraphLine`
- `GraphUnavailablePoint`

The algorithm first creates a `GraphVertex` for each visible commit, then links parents and
children.

Parents outside the visible range are represented by a sentinel vertex with ID `-1`.

### Phase 2: assign paths sequentially

The main loop walks visible commits from newest to oldest:

```kotlin
var i = 0
while (i < graph.vertices.size) {
    val vertex = graph.vertices[i]
    if (vertex.getNextParent() != null || vertex.onBranch == null) {
        determinePath(graph, i)
    } else {
        i++
    }
}
```

There are two important cases inside `determinePath(...)`:

1. **Merge onto an existing branch**
   - if the current vertex and the target parent are already on branches
   - reuse the parent's branch
   - route the merge edge through intermediate rows

2. **Normal branch continuation**
   - allocate or reuse a branch colour index
   - assign the current vertex a lane
   - keep walking until the relevant parent is reached

### Phase 3: prevent collisions

Each `GraphVertex` tracks unavailable x-positions at that row.

This is why `GraphVertex.nextX` and `registerUnavailablePoint(...)` matter: they keep two distinct
connections from occupying the same lane position on the same row.

### Phase 4: extract lane assignments

Once branch membership is known, the final lane map is pulled out of each vertex:

```text
vertex.id -> vertex.x
```

## Merge routing

### 6. `MergeEdgeRouter`

File:

- `../src/main/kotlin/jkhamanishi/git/graph/rendering/MergeEdgeRouter.kt`

`MergeEdgeRouter` is separate from lane assignment.

Its job is to identify long secondary-parent merge edges and influence the comparator behavior used
when graph elements are ordered for rendering.

This keeps the visual result cleaner when merge edges span many rows.

## Rendering integration

Once `PersistentLayoutManager` has the `nodeToLane` map, it installs a proxy getter around the
existing layout getter.

Conceptually:

```text
IntelliJ renderer asks: lane for node N?
    -> proxy checks custom cache
    -> custom lane returned when present
    -> original value used as fallback otherwise
```

This lets the plugin customize graph positioning without replacing IntelliJ's table or repaint logic.

## Core invariants

These are the properties the tests and implementation rely on:

- every visible node should receive a non-negative lane
- merge commits should generally remain on the first-parent path
- secondary-parent paths should diverge onto separate lanes when needed
- lane collisions at the same row must be avoided
- visible-range truncation must not break lane assignment

## Practical debugging order

If something looks wrong in the UI, check these layers in order:

1. `PluginInitializer` found the tool window and scheduled initialization
2. `GraphRenderingInitializer.findGitLogTable(...)` located the correct table
3. `PersistentLayoutManager` resolved `VisiblePack`, `VisibleGraph`, node count, and node getter
4. `CommitMap` extracted the expected parent indices
5. `GraphManager.computeNodeLanes(...)` produced lanes for every visible node
6. `MergeEdgeRouter` did not reorder edges unexpectedly

## Related documents

- [`README.md`](./README.md)
- [`vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md`](vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md)
- [`ALGORITHM_DEVELOPER_GUIDE.md`](./ALGORITHM_DEVELOPER_GUIDE.md)

