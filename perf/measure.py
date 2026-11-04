import json, os, statistics, subprocess, sys, time, urllib.request, urllib.error

BASE = os.environ.get("PERF_BASE", "http://localhost:8461")
CONTAINER = os.environ.get("PERF_PG", "perf-a0-pg")
RUNS = int(os.environ.get("PERF_RUNS", "50"))
PASSWORD = "Test1234"
SECTION = 14011
ENROLLMENT = 20001
STUDENT = 20001

ENDPOINTS = [
    ("director", "financial-dashboard", "/financial-dashboard"),
    ("director", "financial-dashboard/debtors", "/financial-dashboard/debtors"),
    ("director", "staff list", "/staff"),
    ("director", "students list", "/students"),
    ("director", "terms", "/terms"),
    ("director", "programs", "/programs"),
    ("director", "billing-overview", f"/students/{STUDENT}/billing-overview"),
    ("preceptor", "absence-report", f"/program-sections/{SECTION}/attendance/absence-report"),
    ("preceptor", "academic-offering-cycles", "/academic-offering-cycles"),
    ("preceptor", "classroom availability", "/terms/1/classrooms/availability?dayOfWeek=MONDAY&startTime=18:00&endTime=20:00"),
    ("preceptor", "student program-enrollments", f"/students/{STUDENT}/program-enrollments"),
    ("preceptor", "enrollment progress", f"/program-enrollments/{ENROLLMENT}/progress"),
    ("preceptor", "attendance-summary", f"/program-enrollments/{ENROLLMENT}/attendance-summary"),
    ("preceptor", "enrollment section-enrollments", f"/program-enrollments/{ENROLLMENT}/section-enrollments"),
    ("administrativo", "enrollment invoices", f"/program-enrollments/{ENROLLMENT}/invoices"),
    ("docente", "roster (teacher)", f"/program-sections/{SECTION}/roster"),
    ("docente", "grade-grid (teacher)", f"/program-sections/{SECTION}/grade-grid"),
    ("docente", "absence-report (teacher)", f"/program-sections/{SECTION}/attendance/absence-report"),
    ("alumno", "me program-enrollments", "/me/program-enrollments"),
    ("alumno", "me enrollment progress", f"/me/program-enrollments/{ENROLLMENT}/progress"),
    ("alumno", "me section-enrollments", "/me/section-enrollments"),
    ("alumno", "me enrollment invoices", f"/me/program-enrollments/{ENROLLMENT}/invoices"),
    ("alumno", "me grade-items", f"/me/section-enrollments/{100011}/grade-items"),
    ("alumno", "me attendance", f"/me/section-enrollments/{100011}/attendance"),
]
ONLY = [x for x in os.environ.get("PERF_ONLY", "").split(",") if x]
if ONLY:
    ENDPOINTS = [e for e in ENDPOINTS if e[1] in ONLY]
USERS = {"administrativo": "administrativo.demo", "director": "director.demo", "preceptor": "preceptor.demo", "docente": "perf662", "alumno": "perf1"}


def login(username):
    req = urllib.request.Request(BASE + "/auth/login", data=json.dumps({"username": username, "password": PASSWORD}).encode(), headers={"Content-Type": "application/json"})
    t = time.perf_counter()
    with urllib.request.urlopen(req) as r:
        body = json.load(r)
    return body["accessToken"], (time.perf_counter() - t) * 1000


def get(path, token):
    req = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + token})
    t = time.perf_counter()
    try:
        with urllib.request.urlopen(req) as r:
            data = r.read()
            return r.status, len(data), (time.perf_counter() - t) * 1000
    except urllib.error.HTTPError as e:
        data = e.read()
        return e.code, len(data), (time.perf_counter() - t) * 1000


def psql(sql):
    out = subprocess.run(["docker", "exec", CONTAINER, "psql", "-U", "postgres", "-d", "perf", "-Atc", sql], capture_output=True, text=True)
    return out.stdout.strip()


def pct(values, p):
    s = sorted(values)
    return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]


def query_count(path, token):
    psql("select pg_stat_statements_reset()")
    time.sleep(0.3)
    get(path, token)
    time.sleep(0.3)
    return int(psql("select coalesce(sum(calls),0) from pg_stat_statements where query not ilike '%pg_stat_statements%'") or 0)


def main():
    tokens = {}
    login_times = []
    for role, user in USERS.items():
        tokens[role], ms = login(user)
        login_times.append(ms)
    results = [{"role": "all", "name": "POST /auth/login", "status": 200, "size": 0, "p50": pct(login_times, 50), "p95": pct(login_times, 95), "p99": pct(login_times, 99), "queries": None}]
    for role, name, path in ENDPOINTS:
        token = tokens[role]
        for _ in range(3):
            get(path, token)
        times, status, size = [], 0, 0
        for _ in range(RUNS):
            status, size, ms = get(path, token)
            times.append(ms)
        results.append({"role": role, "name": name, "path": path, "status": status, "size": size, "p50": pct(times, 50), "p95": pct(times, 95), "p99": pct(times, 99), "queries": query_count(path, token)})
        print(json.dumps(results[-1]), flush=True)
    json.dump(results, open(os.environ.get("PERF_OUT", "results.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
