import json, os, sys

SCREEN_PREFIX = "screen:"
POOL_PREFIX = "pool:"


def select_budget(budget, scope):
    if scope == "screen":
        return {name: limits for name, limits in budget.items() if name.startswith(SCREEN_PREFIX)}
    if scope == "pool":
        return {name: limits for name, limits in budget.items() if name.startswith(POOL_PREFIX)}
    return {name: limits for name, limits in budget.items() if not name.startswith((SCREEN_PREFIX, POOL_PREFIX))}


def check(latest, budget):
    failures = []
    for name, limits in budget.items():
        row = latest.get(name)
        if row is None:
            failures.append(f"{name}: no measured")
            continue
        for key, limit in limits.items():
            if key not in row:
                failures.append(f"{name}: {key} no measured")
            elif row[key] > limit:
                failures.append(f"{name}: {key}={row[key]} > {limit}")
    return failures


def main():
    scope = os.environ.get("PERF_SCOPE", "endpoint")
    default_latest = {"screen": "perf/out/screens.json", "pool": "perf/out/pool.json"}.get(scope, "perf/out/latest.json")
    latest = json.load(open(os.environ.get("PERF_LATEST", default_latest)))
    budget = select_budget(json.load(open(os.environ.get("PERF_BUDGET", "perf/budget.json"))), scope)
    failures = check(latest, budget)
    print("\n".join(failures) if failures else "OK")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
