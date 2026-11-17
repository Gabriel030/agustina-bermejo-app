import json, os, sys
import measure as m

RUNS = int(os.environ.get("PERF_RUNS", "20"))

def snapshot():
    out = m.psql("select queryid, calls, total_exec_time from pg_stat_statements where query not ilike '%pg_stat_statements%'")
    snap = {}
    for line in out.splitlines():
        q, c, t = line.split("|")
        snap[q] = (int(c), float(t))
    return snap

def main():
    m.psql("select pg_stat_statements_reset()")
    tokens = {}
    before = snapshot()
    origin = {}
    def attribute(label):
        nonlocal before
        after = snapshot()
        for q, (c, t) in after.items():
            dc, dt = c - before.get(q, (0, 0))[0], t - before.get(q, (0, 0))[1]
            if dc > 0:
                origin.setdefault(q, {})[label] = origin.get(q, {}).get(label, 0) + dt
        before = after
    for user in sorted(set(m.USERS.values())):
        tokens[user], _ = m.login(user)
    attribute("POST /auth/login")
    for role, name, path in m.ENDPOINTS:
        for _ in range(RUNS):
            m.get(path, tokens[m.USERS[role]])
        attribute(path)
    json.dump({q: max(v, key=v.get) for q, v in origin.items()}, open("perf/out/origin.json", "w"), indent=1)

main()
