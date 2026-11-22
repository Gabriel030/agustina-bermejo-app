import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "check_budget.py")
BUDGET = {
    "/students": {"p95": 200, "queries": 3},
    "screen:/director/financiero": {"requests": 20, "duplicates": 0},
}


def run_check(scope, latest):
    with tempfile.TemporaryDirectory() as directory:
        latest_path = os.path.join(directory, "latest.json")
        budget_path = os.path.join(directory, "budget.json")
        json.dump(latest, open(latest_path, "w"))
        json.dump(BUDGET, open(budget_path, "w"))
        env = dict(os.environ, PERF_SCOPE=scope, PERF_LATEST=latest_path, PERF_BUDGET=budget_path)
        return subprocess.run([sys.executable, SCRIPT], env=env, capture_output=True, text=True)


class CheckBudgetTest(unittest.TestCase):
    def test_screen_within_budget_passes(self):
        result = run_check("screen", {"screen:/director/financiero": {"requests": 17, "duplicates": 0}})
        self.assertEqual(result.returncode, 0, result.stdout)

    def test_screen_with_duplicate_request_fails(self):
        result = run_check("screen", {"screen:/director/financiero": {"requests": 17, "duplicates": 1}})
        self.assertEqual(result.returncode, 1)
        self.assertIn("duplicates=1 > 0", result.stdout)

    def test_screen_over_request_budget_fails(self):
        result = run_check("screen", {"screen:/director/financiero": {"requests": 21, "duplicates": 0}})
        self.assertEqual(result.returncode, 1)
        self.assertIn("requests=21 > 20", result.stdout)

    def test_unmeasured_screen_fails(self):
        result = run_check("screen", {})
        self.assertEqual(result.returncode, 1)
        self.assertIn("no measured", result.stdout)

    def test_endpoint_scope_ignores_screen_budgets(self):
        result = run_check("endpoint", {"/students": {"p95": 100, "queries": 3}})
        self.assertEqual(result.returncode, 0, result.stdout)

    def test_endpoint_over_budget_fails(self):
        result = run_check("endpoint", {"/students": {"p95": 300, "queries": 3}})
        self.assertEqual(result.returncode, 1)


if __name__ == "__main__":
    unittest.main()
