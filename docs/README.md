# Documentation

This folder contains the project-specific documentation for `FixedLaneGitGraph`.

## Layout

- `README.md` - this index
- `PIPELINE_AND_ALGORITHM_FLOW.md` - end-to-end plugin and rendering flow
- `ALGORITHM_DEVELOPER_GUIDE.md` - maintainer guide for the current algorithm implementation
- `vscode-git-graph/` - notes specifically about the external inspiration and related analysis

## Start here

If you want the complete rendering and algorithm flow, begin with:

- [`PIPELINE_AND_ALGORITHM_FLOW.md`](PIPELINE_AND_ALGORITHM_FLOW.md)

If you want a short orientation first, use:

- [`vscode-git-graph/README.md`](./vscode-git-graph/README.md)

## Which doc to read

### Understanding the full system

- [`PIPELINE_AND_ALGORITHM_FLOW.md`](PIPELINE_AND_ALGORITHM_FLOW.md)

### Modifying the lane algorithm

- [`ALGORITHM_DEVELOPER_GUIDE.md`](./ALGORITHM_DEVELOPER_GUIDE.md)
- [`vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md`](./vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md)

### Quick troubleshooting and commands

- [`vscode-git-graph/README.md`](./vscode-git-graph/README.md)
- [`ALGORITHM_DEVELOPER_GUIDE.md`](./ALGORITHM_DEVELOPER_GUIDE.md)

## Current implementation anchors

These docs are aligned with the current Kotlin implementation, especially:

- `src/main/kotlin/jkhamanishi/git/graph/PluginInitializer.kt`
- `src/main/kotlin/jkhamanishi/git/graph/CommitMap.kt`
- `src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphManager.kt`
- `src/main/kotlin/jkhamanishi/git/graph/algorithm/Graph.kt`
- `src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphVertex.kt`
- `src/main/kotlin/jkhamanishi/git/graph/algorithm/GraphBranch.kt`
- `src/main/kotlin/jkhamanishi/git/graph/rendering/PersistentLayoutManager.kt`
- `src/main/kotlin/jkhamanishi/git/graph/rendering/MergeEdgeRouter.kt`

## Cleanup notes

The old migration-era summary was removed because it described files and class names that are no
longer present. The current layout keeps plugin-specific implementation docs at the root of `docs/`
while keeping vscode-git-graph-specific analysis in its own subfolder.

