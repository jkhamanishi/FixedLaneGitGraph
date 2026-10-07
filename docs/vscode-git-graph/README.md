# vscode-git-graph Notes

This folder contains documentation related to the lane-assignment approach used by
`FixedLaneGitGraph`.

The implementation in this repository is not a direct port of the vscode extension UI.
Instead, it adapts the same broad graph-layout strategy to IntelliJ IDEA's Git Log rendering
pipeline.

## What's in this folder

- `GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md` - conceptual analysis of the inspired algorithm
- `README.md` - notes about how this subfolder relates to the rest of the project docs

## When to read which file

- Start with `../PIPELINE_AND_ALGORITHM_FLOW.md` if you want the whole plugin flow.
- Read `../ALGORITHM_DEVELOPER_GUIDE.md` if you need to change the algorithm implementation.
- Read `GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md` if you want the design rationale.

## Handy commands

```bash
./gradlew test
./gradlew build
./gradlew runIde
./gradlew test --tests jkhamanishi.git.graph.GraphManagerTest
```

## Quick troubleshooting entry points

- Graph looks unchanged: check `PluginInitializer` and `GraphRenderingInitializer` behavior first.
- Lanes look wrong: inspect `CommitMap` extraction and `GraphManager.determinePath(...)`.
- Merge edges look messy: inspect `MergeEdgeRouter` and `PersistentLayoutManager` comparator wiring.
- Behavior changed after IDE upgrade: start with reflection access in `PersistentLayoutManager`.

## Current code anchors

The docs in this folder refer primarily to these classes:

- `../../src/main/kotlin/jkhamanishi/git/graph/PluginInitializer.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/CommitMap.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphManager.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/algorithm/Graph.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphVertex.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphBranch.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/rendering/PersistentLayoutManager.kt`
- `../../src/main/kotlin/jkhamanishi/git/graph/rendering/MergeEdgeRouter.kt`

## Notes

The historical migration summary was removed from `docs/` because the migration is complete and
that document no longer reflected the current file layout or symbol names.
