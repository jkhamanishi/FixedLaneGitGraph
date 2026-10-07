#!/usr/bin/env python3
"""
Analyze and visualize git commit topology.

This script helps debug lane assignment issues by showing:
- Parent-child relationships between commits
- Which commits are merge commits
- Linear vs branching history
- Secondary vs primary parent relationships

Usage:
    python3 analyze_git_topology.py --repo /path/to/repo --commits hash1 hash2 hash3
    python3 analyze_git_topology.py --repo /path/to/repo --range hash1..hash2
"""

import subprocess
import sys
import argparse
from typing import Dict, List, Tuple, Set, Optional


class GitTopologyAnalyzer:
    def __init__(self, repo_path: str):
        self.repo_path = repo_path
        self.commits: Dict[str, Tuple[List[str], str]] = {}  # hash -> (parents, subject)
        self.load_all_commits()

    def load_all_commits(self):
        """Load all commits with their parents and subjects."""
        try:
            result = subprocess.run(
                ['git', 'rev-list', '--all', '--parents', '--format=%s'],
                cwd=self.repo_path,
                capture_output=True,
                text=True,
                check=True
            )

            lines = result.stdout.strip().split('\n')
            i = 0
            while i < len(lines):
                if lines[i].startswith('commit '):
                    parts = lines[i][7:].split()  # Skip "commit "
                    commit_hash = parts[0]
                    parents = parts[1:] if len(parts) > 1 else []

                    # Next line is the subject
                    i += 1
                    subject = lines[i] if i < len(lines) else ""

                    self.commits[commit_hash] = (parents, subject)
                i += 1
        except subprocess.CalledProcessError as e:
            print(f"Error loading git commits: {e}", file=sys.stderr)
            sys.exit(1)

    def get_parents(self, commit_hash: str) -> List[str]:
        """Get parent hashes for a commit."""
        return self.commits.get(commit_hash, ([], ""))[0]

    def get_subject(self, commit_hash: str) -> str:
        """Get commit subject."""
        return self.commits.get(commit_hash, ([], ""))[1]

    def is_merge(self, commit_hash: str) -> bool:
        """Check if commit is a merge."""
        return len(self.get_parents(commit_hash)) > 1

    def get_children(self, commit_hash: str) -> List[Tuple[str, int]]:
        """Get children of a commit as (hash, parent_position) tuples."""
        children = []
        for other_hash, (parents, _) in self.commits.items():
            if commit_hash in parents:
                pos = parents.index(commit_hash)
                children.append((other_hash, pos))
        return children

    def analyze_commit(self, commit_hash: str, depth: int = 0) -> None:
        """Print detailed analysis of a commit."""
        if commit_hash not in self.commits:
            print(f"Commit {commit_hash[:8]} not found")
            return

        indent = "  " * depth
        parents = self.get_parents(commit_hash)
        subject = self.get_subject(commit_hash)
        is_merge = len(parents) > 1

        print(f"{indent}{commit_hash[:8]} {'[MERGE]' if is_merge else '[normal]'} {subject}")

        for i, parent in enumerate(parents):
            pos_label = "first" if i == 0 else f"second ({i+1})"
            print(f"{indent}  ├─ {pos_label} parent: {parent[:8]}")

    def analyze_path(self, start_hash: str, max_depth: int = 20) -> None:
        """Analyze a linear path from a commit backwards through parents."""
        print("\n=== Path Analysis (backwards) ===\n")
        current = start_hash
        depth = 0
        visited = set()

        while current and depth < max_depth:
            if current in visited:
                print(f"  (cycle detected)")
                break
            visited.add(current)

            self.analyze_commit(current, depth)

            parents = self.get_parents(current)
            if parents:
                current = parents[0]
                depth += 1
            else:
                break

    def analyze_topology(self, commits: List[str]) -> None:
        """Analyze the topology of specified commits."""
        print("\n=== Commit Topology ===\n")

        for commit_hash in commits:
            self.analyze_commit(commit_hash)

            children = self.get_children(commit_hash)
            if children:
                print(f"  Children:")
                for child_hash, pos in children:
                    is_first = pos == 0
                    pos_label = "first parent" if is_first else f"secondary ({pos+1})"
                    subject = self.get_subject(child_hash)
                    print(f"    ├─ {child_hash[:8]} ({pos_label}) {subject}")
            print()

    def find_merge_chain(self, start_hash: str, max_depth: int = 50) -> None:
        """Find and visualize a chain of merge commits."""
        print("\n=== Merge Chain Analysis ===\n")

        current = start_hash
        depth = 0
        visited = set()
        path = []

        while current and depth < max_depth:
            if current in visited:
                break
            visited.add(current)

            parents = self.get_parents(current)
            is_merge = len(parents) > 1
            subject = self.get_subject(current)

            path.append((current, is_merge, subject))

            if parents:
                current = parents[0]
                depth += 1
            else:
                break

        # Print the path
        for i, (commit_hash, is_merge, subject) in enumerate(path):
            connector = "↓" if i < len(path) - 1 else "•"
            marker = "[M]" if is_merge else "[ ]"
            print(f"{marker} {commit_hash[:8]} {subject}")
            if i < len(path) - 1:
                print(f"  {connector}")


def main():
    parser = argparse.ArgumentParser(
        description="Analyze git commit topology for lane assignment debugging"
    )
    parser.add_argument(
        "--repo",
        required=True,
        help="Path to git repository"
    )
    parser.add_argument(
        "--commits",
        nargs="+",
        help="Specific commit hashes to analyze"
    )
    parser.add_argument(
        "--path",
        help="Analyze backwards path from a commit hash"
    )
    parser.add_argument(
        "--chain",
        help="Analyze merge chain starting from a commit hash"
    )
    parser.add_argument(
        "--depth",
        type=int,
        default=20,
        help="Maximum depth for path/chain analysis (default: 20)"
    )

    args = parser.parse_args()

    analyzer = GitTopologyAnalyzer(args.repo)

    if args.commits:
        analyzer.analyze_topology(args.commits)

    if args.path:
        analyzer.analyze_path(args.path, args.depth)

    if args.chain:
        analyzer.find_merge_chain(args.chain, args.depth)

    if not args.commits and not args.path and not args.chain:
        print("Please specify --commits, --path, or --chain")
        parser.print_help()


if __name__ == "__main__":
    main()

