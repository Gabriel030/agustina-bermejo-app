import json, os, subprocess, sys, time, urllib.parse, urllib.request, urllib.error
from datetime import date, datetime, timedelta

BASE = os.environ.get("PERF_BASE", "http://localhost:8461")
CONTAINER = os.environ.get("PERF_PG", "perf-a0-pg")
PASSWORD = "Test1234"
HERE = os.path.dirname(os.path.abspath(__file__))
PARAMS = {
    "students": int(os.environ.get("LOAD_STUDENTS", "2000")),
    "graded_students": int(os.environ.get("LOAD_GRADED_STUDENTS", "500")),
    "enroll_sections": int(os.environ.get("LOAD_ENROLL_SECTIONS", "40")),
    "grade_sections": int(os.environ.get("LOAD_GRADE_SECTIONS", "20")),
    "teachers": int(os.environ.get("LOAD_TEACHERS", "10")),
    "capacity": int(os.environ.get("LOAD_CAPACITY", "60")),
    "term_id": int(os.environ.get("LOAD_TERM_ID", "1")),
    "program_id": int(os.environ.get("LOAD_PROGRAM_ID", "1")),
    "exam_boards": int(os.environ.get("LOAD_EXAM_BOARDS", "8")),
}
HTTP_TIMEOUT_SECONDS = 60
SCHEME_NAME = "Esquema carga perf: 2 parciales y final"


def call(method, path, token=None, body=None, tolerate=()):
    for attempt in range(6):
        outcome = send(method, path, token, body, tolerate)
        if outcome is not RATE_LIMITED:
            return outcome
        time.sleep(10 * (attempt + 1))
    sys.exit(f"{method} {path} -> 429 persistente")


RATE_LIMITED = object()


def send(method, path, token, body, tolerate):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        if error.code == 429:
            return RATE_LIMITED
        if error.code in tolerate:
            return None
        sys.exit(f"{method} {path} -> {error.code}: {error.read().decode()[:300]}")


def login(username):
    return call("POST", "/auth/login", body={"username": username, "password": PASSWORD})["accessToken"]


def psql(sql, variables=None):
    command = ["docker", "exec", "-i", CONTAINER, "psql", "-U", "postgres", "-d", "perf", "-tA", "-v", "ON_ERROR_STOP=1"]
    for key, value in (variables or {}).items():
        command += ["-v", f"{key}={value}"]
    result = subprocess.run(command, input=sql, capture_output=True, text=True, timeout=1800)
    if result.returncode != 0:
        sys.exit(result.stderr)
    return result.stdout.strip()


def scalar(sql):
    value = psql(sql)
    return int(value) if value else None


def ensure_evaluation_scheme(token):
    existing = scalar(f"select id from evaluation_scheme where name = '{SCHEME_NAME}' order by id limit 1")
    if existing:
        return existing
    created = call("POST", "/evaluation-schemes", token, {
        "name": SCHEME_NAME, "examCountExpected": 2, "tpCountExpected": 3, "hasFinal": True, "finalWeight": 0.5,
        "aggregationMethod": "SIMPLE_AVERAGE", "minRegularGrade": 4, "minApprovalGrade": 6})
    return created["id"]


def ensure_open_enrollment_window(token, term_id):
    call("POST", f"/terms/{term_id}/enrollment/open", token, tolerate=(400, 409))
    if scalar(f"select count(*) from term where id = {term_id} and enrollment_override = 'OPEN'") != 1:
        sys.exit(f"term {term_id} sin ventana de inscripcion abierta")


def ensure_billing_template(token, program_id, term_id):
    template_id = scalar(f"select id from program_year_billing_template where program_id = {program_id} and term_id = {term_id}")
    if not template_id:
        template_id = call("POST", f"/programs/{program_id}/year-billing-templates", token,
                           {"termId": term_id, "recurringFeeInvoicesCount": 10})["id"]
    cohort_id = scalar(f"select id from program_cohort where template_id = {template_id} order by id limit 1")
    if not cohort_id:
        today = date.today()
        cohort_id = call("POST", f"/year-billing-templates/{template_id}/cohorts", token, {
            "intake": "FIRST", "admissionStart": str(today - timedelta(days=60)),
            "admissionEnd": str(today + timedelta(days=300)), "cursadaStart": str(today - timedelta(days=30))})["id"]
    option_id = scalar(f"select id from payment_plan_option where program_id = {program_id} and cadence = 'MONTHLY' order by id limit 1")
    if not option_id:
        option_id = call("POST", f"/programs/{program_id}/payment-plan-options", token,
                         {"description": "Mensual carga perf", "cadence": "MONTHLY", "upfrontWindowEnabled": False})["id"]
    return template_id, cohort_id, option_id


def ensure_exam_boards(token, term_id, wanted):
    existing = scalar("select count(*) from exam_board where classroom like 'LOAD-%'")
    now = datetime.now().replace(microsecond=0, hour=10, minute=0, second=0)
    for index in range(existing, wanted):
        for offset in range(30 + index, 30 + index + 15):
            exam_date = now + timedelta(days=offset)
            if exam_date.weekday() >= 5:
                continue
            created = call("POST", "/exam-boards", token, {
                "subjectId": 1 + (index % 16), "termId": term_id, "examDate": exam_date.isoformat(),
                "registrationStart": (now - timedelta(days=1)).isoformat(), "registrationEnd": (now + timedelta(days=20)).isoformat(),
                "classroom": f"LOAD-{index + 1}"}, tolerate=(400,))
            if created:
                break
        else:
            sys.exit("sin fecha de mesa valida")


def validate_parameters():
    problems = []
    host = urllib.parse.urlparse(BASE).hostname
    if host not in ("localhost", "127.0.0.1") and os.environ.get("LOAD_CONFIRM") != "1":
        problems.append(f"PERF_BASE={BASE} no es local; definir LOAD_CONFIRM=1 para sembrar contra otro host")
    if PARAMS["students"] < 1 or PARAMS["graded_students"] < 1:
        problems.append("LOAD_STUDENTS y LOAD_GRADED_STUDENTS deben ser >= 1")
    if PARAMS["students"] + PARAMS["graded_students"] > 9999:
        problems.append("LOAD_STUDENTS + LOAD_GRADED_STUDENTS debe ser <= 9999")
    if PARAMS["grade_sections"] < 3:
        problems.append("LOAD_GRADE_SECTIONS debe ser >= 3")
    if PARAMS["enroll_sections"] < 1:
        problems.append("LOAD_ENROLL_SECTIONS debe ser >= 1")
    if not 1 <= PARAMS["teachers"] <= 999:
        problems.append("LOAD_TEACHERS debe estar entre 1 y 999")
    if PARAMS["enroll_sections"] + PARAMS["grade_sections"] > 99999:
        problems.append("la suma de comisiones debe ser <= 99999")
    if problems:
        sys.exit("parametros invalidos:\n" + "\n".join(problems))


def main():
    validate_parameters()
    director = login("director.demo")
    term_id = PARAMS["term_id"]
    scheme_id = ensure_evaluation_scheme(director)
    ensure_open_enrollment_window(director, term_id)
    template_id, cohort_id, option_id = ensure_billing_template(director, PARAMS["program_id"], term_id)
    ensure_exam_boards(login("preceptor.demo"), term_id, PARAMS["exam_boards"])
    variables = {**{k: v for k, v in PARAMS.items() if k != "exam_boards"},
                 "scheme_id": scheme_id, "template_id": template_id, "cohort_id": cohort_id, "monthly_option_id": option_id}
    with open(os.path.join(HERE, "seed-load.sql")) as script:
        psql(script.read(), variables)
    with open(os.path.join(HERE, "seed-load-counts.sql")) as counts:
        print(psql(counts.read(), variables))


main()
