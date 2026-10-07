# Debugging Tools for FixedLaneGitGraph

This directory contains utilities for analyzing and debugging the lane assignment algorithm.

## analyze_git_topology.py

Python script for analyzing git commit topology in real repositories.

### Purpose
When investigating lane assignment issues, it's helpful to understand the exact git topology:
- Which commits are merges
- Parent-child relationships
- Whether a branch is linear or has multiple branches

### Usage

Analyze specific commits:
```bash
python3 scripts/analyze_git_topology.py \
  --repo /path/to/repo \
  --commits 8757de96 acfd48a5 5fc2937b
```

Analyze backwards path from a commit:
```bash
python3 scripts/analyze_git_topology.py \
  --repo /path/to/repo \
  --path 8757de96 \
  --depth 15
```

Analyze merge chain:
```bash
python3 scripts/analyze_git_topology.py \
  --repo /path/to/repo \
  --chain 41b90eb6 \
  --depth 30
```

### Example Output

```
=== Commit Topology ===

8757de96 [MERGE] Merge pull request #877 from conetecdataservices/3.2.3_review_changes
  ├─ first parent: 5928fe04
  ├─ second (2) parent: acfd48a5
  Children:
    ├─ 41b90eb6 (first parent) Merge branch 'develop/eLog' into 3.2.4
    ├─ 6060a6d1 (secondary (2)) Merge branch '3.2.4' into develop/eLog
```

## LaneSimulator.kt

Kotlin test utility for simulating lane assignment on custom commit topologies without needing a real git repository.

### Purpose
When testing or debugging lane assignment logic, you can create a specific commit topology and verify the algorithm handles it correctly.

### Basic Usage

```kotlin
val sim = LaneSimulator.create()
sim.addCommit("c1", emptyList(), "Root commit")
sim.addCommit("c2", listOf("c1"), "Linear continuation")
sim.addCommit("merge", listOf("c2", "branch"), "Merge PR")
sim.setProcessingOrder(listOf("c1", "c2", "merge"))
sim.simulate()
println(sim.report())

assertEquals(0, sim.getLane("c1"))
assertEquals(0, sim.getLane("c2"))
assertEquals(0, sim.getLane("merge"))
```

### With Test Helper

```kotlin
LaneSimulationTest("Linear history with merge")
    .commit("main1", emptyList(), "Main line")
    .commit("main2", listOf("main1"), "Main line continues")
    .commit("branch", listOf(), "Branch start")
    .commit("merge", listOf("main2", "branch"), "Merge branch into main")
    .order("main1", "main2", "branch", "merge")
    .assertLane("main1", 0)
    .assertLane("main2", 0)
    .assertLane("branch", 1)
    .assertLane("merge", 0)
    .run()
```

### Output

```
=== Lane Assignment Report ===

[ ] c1 lane 0: Root commit
   parents: 

[ ] c2 lane 0: Linear continuation
   parents: c1

[M] merge lane 0: Merge PR
   parents: c2, branch

✓ c1 has lane 0
✓ c2 has lane 0
✓ merge has lane 0

3 passed, 0 failed
```

## When to Use These Tools

### analyze_git_topology.py
- Investigating lane shifts in a real repository
- Understanding commit history around problematic merges
- Debugging why a commit got assigned to an unexpected lane
- Verifying the algorithm handles a complex topology correctly

### LaneSimulator.kt
- Adding tests for specific topology patterns
- Regression testing after algorithm changes
- Creating minimal test cases for bug reports
- Understanding how the algorithm handles edge cases

## Examples from Recent Work

### Branch Shift Issue When a Merge Child Appears Before the First-Parent Continuation

**Problem**: A branch can shift lanes when one commit has both:
- a normal first-parent continuation, and
- a merge child where that same commit is only the second parent.

This showed up in ConeSkan at commits like `1231df78` and `3f3f5d07`, and the shift propagated to descendants such as `8e41460c` and `c8dd5eaf`.

**Investigation**:
```bash
python3 scripts/analyze_git_topology.py \
  --repo /path/to/ConeSkan \
  --chain 8757de96 \
  --depth 50
```

**Testing the Fix**:
```kotlin
LaneSimulationTest("Prefer first-parent continuation over merge child")
    .commit("main-tail", listOf("merge"), "Main tail")
    .commit("merge", listOf("main-parent", "branch"), "Merge commit")
    .commit("branch-continuation", listOf("branch"), "Branch continues")
    .commit("branch", listOf("branch-ancestor"), "Commit with two children")
    .commit("branch-ancestor", emptyList(), "Older branch ancestor")
    .commit("main-parent", listOf("main-root"), "Mainline parent")
    .commit("main-root", emptyList(), "Older mainline ancestor")
    .order("main-tail", "merge", "branch-continuation", "branch", "branch-ancestor", "main-parent", "main-root")
    .assertLane("main-tail", 0)
    .assertLane("merge", 0)
    .assertLane("branch-continuation", 1)
    .assertLane("branch", 1)
    .assertLane("branch-ancestor", 1)
    .run()
```

**Root Cause**:
- The algorithm used the first child by visible index as the "primary" child.
- In the failing topologies, that first child was the merge child where the current commit was a **secondary** parent.
- That incorrectly triggered Rule 4 and moved the whole branch to a new rightmost lane.

**Fix**:
- Prefer a child relation with `parentPosition == 0` whenever one exists.
- Use that same preferred child when deciding which sibling lanes can be freed.

## Future Enhancements

- [ ] Interactive topology visualization (ASCII graph)
- [ ] Export topology to DOT format for Graphviz rendering
- [ ] Automatic comparison between expected and actual lane assignments
- [ ] Integration with CI/CD for regression testing
- [ ] Performance profiling for large repositories
