# Algorithm Developer Guide

This guide is for maintainers who need to change the current lane assignment behavior in
`FixedLaneGitGraph`.

It documents the implementation that exists now in Kotlin.

## Quick orientation

If you are new to the code, start in this order:

1. `PIPELINE_AND_ALGORITHM_FLOW.md`
2. `../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphManager.kt`
3. `../src/test/kotlin/jkhamanishi/git/graph/GraphManagerTest.kt`

## Main classes to know

### Graph extraction and integration

- `../src/main/kotlin/jkhamanishi/git/graph/CommitMap.kt`
- `../src/main/kotlin/jkhamanishi/git/graph/rendering/PersistentLayoutManager.kt`
- `../src/main/kotlin/jkhamanishi/git/graph/rendering/MergeEdgeRouter.kt`

### Algorithm model

- `../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphManager.kt`
- `../src/main/kotlin/jkhamanishi/git/graph/algorithm/Graph.kt`
- `../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphVertex.kt`
- `../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphBranch.kt`
- `../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphDataTypes.kt`

### Tests

- `../src/test/kotlin/jkhamanishi/git/graph/GraphManagerTest.kt`
- `../src/test/kotlin/jkhamanishi/git/graph/MergeEdgeRouterTest.kt`
- `../src/test/kotlin/jkhamanishi/git/graph/MergeEdgeRouterEdgeCasesTest.kt`
- `../src/test/kotlin/jkhamanishi/git/graph/CommitMapEdgeCasesTest.kt`

## Mental model

The algorithm is easiest to understand if you think in three layers:

1. `CommitMap` translates IntelliJ's visible graph into parent indices.
2. `GraphManager` translates those parent indices into stable lane assignments.
3. `PersistentLayoutManager` injects those lanes back into IntelliJ's renderer.

`GraphManager` is where you change layout behavior.

## Important structures

### `GraphVertex`

`GraphVertex` is the row-level state holder.

Fields worth understanding before editing the algorithm:

- `x` - the chosen lane
- `nextX` - the next unused lane candidate for that row
- `onBranch` - which `GraphBranch` owns the vertex right now
- `nextParent` - which parent still needs to be processed
- `connections` - previously reserved row positions

### `GraphBranch`

`GraphBranch` represents a visual path through the visible history. It stores the path segments that
were chosen while traversing parents.

### `Graph`

`Graph` mainly owns shared collections and colour reuse state.

The `availableColours` list is central to width control because it decides when a lane identifier can
be reused.

## Where behavior lives

### Lane assignment entry point

`GraphManager.computeNodeLanes(commitMap, nodesCount)` is the public entry point used by the plugin.

It performs three steps:

1. create internal vertices
2. wire parent-child relationships
3. walk the graph and assign lanes through `determinePath(...)`

### Branch and merge logic

The private `determinePath(...)` method controls:

- when a new branch is created
- when an existing parent branch is reused
- how lane reservations are recorded on intermediate rows
- when branch colours become reusable

If lane behavior looks wrong, this is usually the first method to inspect.

## Safe modification workflow

When changing the algorithm, use this sequence:

1. update or add tests first when possible
2. change `GraphManager.determinePath(...)` or supporting model code
3. run focused tests for the algorithm
4. run the full test suite
5. open the sandbox IDE and inspect real repositories if the visual impact is non-trivial

## What to test

Prefer invariant-based assertions over brittle exact-lane snapshots.

Good invariants include:

- every visible node gets a lane
- every lane is non-negative
- the merge commit follows the first-parent lane when expected
- the secondary-parent path diverges when expected
- parents outside the visible range do not break assignment

Some existing tests do assert specific lanes for simple shapes, which is acceptable when the behavior
is intentional and stable.

## Typical changes

### Change lane reuse behavior

Inspect:

- `Graph.getAvailableColour(...)`
- the logic in `GraphManager` that sets `branch.end`

### Change merge behavior

Inspect:

- `GraphManager.determinePath(...)`
- `GraphVertex.getPointConnectingTo(...)`
- `MergeEdgeRouter`

### Change collision handling

Inspect:

- `GraphVertex.registerUnavailablePoint(...)`
- `GraphVertex.nextX`
- the points chosen for intermediate rows inside `determinePath(...)`

## Debugging tips

### 1. Verify the data shape first

Before changing layout logic, confirm that `CommitMap` extracted the expected parent indices. A wrong
graph model will make every later step look broken.

### 2. Print lanes from a test

It is often fastest to add temporary output in a focused test:

```kotlin
println(lanes.toSortedMap())
```

### 3. Check merge-edge routing separately

Not every rendering oddity is a lane assignment bug. Some are edge-ordering or routing issues handled
by `MergeEdgeRouter` and the comparator proxy in `PersistentLayoutManager`.

### 4. Remember the visible-range boundary

Some parents are intentionally represented as out-of-range sentinels. Make sure a change still behaves
correctly when the full history is not visible.

## Performance notes

- DAG construction is linear in visible commits plus visible edges.
- Path determination can degrade toward quadratic behavior in the visible range.
- For large repositories, visible-window size matters more than total repository size because the
  algorithm runs on the visible graph slice IntelliJ exposes.

## Commands

```bash
./gradlew test
./gradlew test --tests jkhamanishi.git.graph.GraphManagerTest
./gradlew runIde
```

## Troubleshooting checklist

| Symptom | Check first |
|---|---|
| Plugin loads but graph looks unchanged | `PluginInitializer` and `GraphRenderingInitializer` logging |
| Lanes look wrong | `CommitMap` parent extraction and `GraphManager.determinePath(...)` |
| Merge edges overlap badly | `MergeEdgeRouter` and comparator proxy wiring |
| Some visible commits have no lane | tests covering out-of-range parents and merge edge cases |
| Behavior differs after IDE upgrade | reflection targets in `PersistentLayoutManager` |

## Related docs

- [`README.md`](./README.md)
- [`PIPELINE_AND_ALGORITHM_FLOW.md`](./PIPELINE_AND_ALGORITHM_FLOW.md)
- [`vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md`](./vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md)
