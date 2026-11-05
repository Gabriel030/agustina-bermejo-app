import json, os, sys

latest = json.load(open(os.environ.get("PERF_LATEST", "perf/out/latest.json")))
budget = json.load(open(os.environ.get("PERF_BUDGET", "perf/budget.json")))
failures = []
for endpoint, limits in budget.items():
    row = latest.get(endpoint)
    if row is None:
        failures.append(f"{endpoint}: no measured")
        continue
    for key, limit in limits.items():
        if row[key] > limit:
            failures.append(f"{endpoint}: {key}={row[key]} > {limit}")
print("\n".join(failures) if failures else "OK")
sys.exit(1 if failures else 0)
