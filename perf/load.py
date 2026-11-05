import json, subprocess, threading, time, sys
import measure as m

def active_conns():
    return int(m.psql("select count(*) from pg_stat_activity where datname='perf' and application_name <> 'psql' and pid <> pg_backend_pid()") or 0)

def run(role_token, path, threads, per_thread):
    times, errors = [], [0]
    lock = threading.Lock()
    def work():
        for _ in range(per_thread):
            status, size, ms = m.get(path, role_token)
            with lock:
                times.append(ms)
                if status >= 400:
                    errors[0] += 1
    peak = [0]
    stop = threading.Event()
    def sampler():
        while not stop.is_set():
            peak[0] = max(peak[0], active_conns())
            time.sleep(0.3)
    s = threading.Thread(target=sampler); s.start()
    t0 = time.perf_counter()
    ts = [threading.Thread(target=work) for _ in range(threads)]
    [t.start() for t in ts]; [t.join() for t in ts]
    elapsed = time.perf_counter() - t0
    stop.set(); s.join()
    return {"path": path, "threads": threads, "requests": len(times), "rps": round(len(times) / elapsed, 1), "p50": round(m.pct(times, 50)), "p95": round(m.pct(times, 95)), "p99": round(m.pct(times, 99)), "errors": errors[0], "peakDbConnections": peak[0]}

if __name__ == "__main__":
    d, _ = m.login("director.demo")
    time.sleep(8)
    a, _ = m.login("perf1")
    time.sleep(8)
    t, _ = m.login("perf662")
    m.psql("select pg_stat_statements_reset()")
    cases = [(d, "/financial-dashboard"), (d, "/financial-dashboard/debtors"), (d, "/students"), (a, "/me/section-enrollments"), (t, "/program-sections/14011/grade-grid"), (t, "/program-sections/14011/attendance/absence-report"), (a, "/me/program-enrollments/20001/invoices")]
    out = []
    for token, path in cases:
        threads, per = (4, 5) if path in ("/financial-dashboard/debtors", "/students") else (20, 10)
        out.append(run(token, path, threads, per))
        print(json.dumps(out[-1]), flush=True)
    json.dump(out, open("/private/tmp/claude-501/load.json", "w"), indent=1)
