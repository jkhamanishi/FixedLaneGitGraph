# Fixed Lane Git Graph

`Fixed Lane Git Graph` is an IntelliJ IDEA plugin that makes the Git Log graph easier to follow by keeping branch lanes as stable as possible instead of letting commits jump between columns as history gets more complex.

The plugin works by intercepting IntelliJ's Git Log rendering pipeline, extracting commit relationships from the visible graph, and applying a sequential path-determination layout inspired by `vscode-git-graph`.

## Why this plugin exists

Git histories with frequent merges, long-lived branches, or stacked topic branches can become hard to read when the graph reflows aggressively. The goal of this plugin is to make the visual structure more predictable so you can:

- trace branch flow more quickly
- understand merge structure with less mental overhead
- keep long-running lanes visually consistent
- use IntelliJ's existing Git Log UI without changing your workflow

## What it does

- Recomputes Git Log lane placement with a fixed-lane strategy
- Preserves stable branch paths across the visible history
- Improves readability for merge-heavy repositories
- Routes long secondary-parent merge edges more cleanly
- Activates automatically when the Git or Version Control tool window becomes visible
- Integrates with the existing IntelliJ Git Log table instead of replacing it

## How it works

At a high level, the plugin follows this pipeline:

1. Detect the Git Log UI when the Git / Version Control tool window opens
2. Locate IntelliJ's visible graph and commit table
3. Extract parent relationships into `CommitMap`
4. Run the lane assignment algorithm in `GraphManager`
5. Inject the computed lane mapping back into IntelliJ's rendering logic
6. Adjust merge-edge routing so long second-parent edges stay visually readable

The current implementation uses a sequential path-determination model documented in
`docs/PIPELINE_AND_ALGORITHM_FLOW.md` and related design notes in the `docs/`
directory.

## Usage

There is no separate tool window or settings page.

After the plugin is installed:

1. Open a project with Git history
2. Open the Git or Version Control tool window
3. Navigate to the Log view
4. The plugin attaches automatically and customizes the graph rendering

## Installation

### Build locally

```bash
./gradlew build
```

The plugin artifact is produced in `build/libs/`.

### Install into IntelliJ IDEA

1. Open **Settings / Preferences**
2. Go to **Plugins**
3. Choose **Install Plugin from Disk...**
4. Select the built JAR from `build/libs/`
5. Restart the IDE

## Development

### Run tests

```bash
./gradlew test
```

### Launch a sandbox IDE with the plugin

```bash
./gradlew runIde
```

### Build the plugin package

```bash
./gradlew build
```

## Project structure

- `src/main/kotlin/jkhamanishi/git/graph/` - plugin lifecycle and commit graph extraction
- `src/main/kotlin/jkhamanishi/git/graph/algorithm/` - lane assignment algorithm and graph model
- `src/main/kotlin/jkhamanishi/git/graph/rendering/` - rendering hooks, lane injection, and merge-edge routing
- `src/main/resources/META-INF/plugin.xml` - plugin manifest and marketplace metadata
- `docs/` - architecture, algorithm, and maintenance documentation

## Documentation guide

The repository already includes deeper internal documentation:

- `docs/PIPELINE_AND_ALGORITHM_FLOW.md` - complete end-to-end rendering and
  algorithm flow
- `docs/vscode-git-graph/GIT_GRAPH_RENDERING_ALGORITHM_ANALYSIS.md` - algorithm analysis and
  rationale
- `docs/ALGORITHM_DEVELOPER_GUIDE.md` - implementation and maintenance guide

## Technical notes and limitations

- The plugin is built against IntelliJ IDEA `2025.3.5`.
- It depends on IntelliJ's VCS and `Git4Idea` functionality.
- Parts of the implementation rely on reflection and internal VCS graph structures.
- Because of that, future IntelliJ platform changes may require plugin updates even if the public Git Log behavior looks similar.

## Repository status

This project contains both the plugin implementation and internal documentation for the graph layout algorithm, including the recent shift to a `vscode-git-graph`-inspired approach for lane assignment.

If you are working on the renderer itself, start with `docs/README.md` and then read
`docs/PIPELINE_AND_ALGORITHM_FLOW.md`.
