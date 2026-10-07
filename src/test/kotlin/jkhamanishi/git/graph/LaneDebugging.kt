package jkhamanishi.git.graph

/**
 * Documentation and examples for debugging lane assignment issues.
 *
 * See DEBUGGING.md for detailed instructions on using the analysis tools.
 *
 * Key concepts:
 * - Lane assignment happens in topological order (parent before child)
 * - Commits with multiple children must prefer a first-parent continuation over a secondary-parent merge child
 * - Rule 4: Secondary parents of merge commits get rightmost lanes only when that merge edge is the chosen continuation
 * - Linear histories are preserved even when merging as secondary parents
 * - Lanes are freed up only for non-merge, non-secondary-parent children
 *
 * When debugging lane shifts:
 * 1. Use scripts/analyze_git_topology.py to understand the git history
 * 2. Identify which commits shifted lanes
 * 3. Check if they're secondary parents or part of long linear histories
 * 4. Verify the algorithm handles the topology correctly
 */
object LaneDebugging {
    /**
     * Example: The acfd48a5 branch shift issue
     *
     * Topology:
     *   1231df78 --- a69c20de
     *        \
     *         1e153278 (merge child where 1231df78 is the second parent)
     *
     * Similar ConeSkan examples also appear at 3f3f5d07 / 4720645a / 8fc6e8df
     *
     * Issue: the branch and its ancestors shifted lanes even though a first-parent continuation existed
     *
     * Root Cause: the algorithm picked the first child by visible index, which could be a
     * secondary-parent merge child instead of the first-parent continuation.
     *
     * Fix: prefer the first-parent child when choosing the lane-driving continuation, and
     * use the same preferred child when deciding which sibling lanes may be freed.
     */

    /**
     * To debug a similar issue in the future:
     *
     * 1. Identify the problematic commit range with git log:
     *    git log --oneline <commit1>..<commit2>
     *
     * 2. Analyze the topology:
     *    python3 scripts/analyze_git_topology.py \
     *      --repo /path/to/repo \
     *      --commits <hash1> <hash2> <merge_hash> \
     *      --path <merge_hash> --depth 50
     *
     * 3. Look for:
     *    - Which commits are marked as merge commits (M)
     *    - Which commit is the secondary parent
     *    - Whether the secondary parent is part of a long linear history
     *
     * 4. The fix should:
     *    - Preserve lane stability for linear histories
     *    - Only apply special rules (like Rule 4) at appropriate times
     *    - Never free lanes of secondary-parent children
     */
}

