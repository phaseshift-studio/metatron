"""test_runs — the limit bin/test-select flags and bin/hunk-revert-check refuses.

Agents' test runs are targeted classes, never the full suite (Marko's rule), and a revert check runs its classes once
per hunk: a list longer than this is the suite's work.
"""
MOST_CLASSES = 40
