import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).parent / "coverage-usecase-summary.py"
spec = importlib.util.spec_from_file_location("coverage_usecase_summary", SCRIPT)
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)

HEADER = "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED"


def row(package, name, line_missed, line_covered, branch_missed=0, branch_covered=0):
    return f"backend,{package},{name},0,0,{branch_missed},{branch_covered},{line_missed},{line_covered},0,0,0,0"


class UsecaseSummaryTest(unittest.TestCase):
    def read(self, rows):
        with tempfile.NamedTemporaryFile("w", suffix=".csv", delete=False) as handle:
            handle.write("\n".join([HEADER] + rows) + "\n")
        return summary.read_usecase_services(handle.name)

    def test_only_services_under_usecase_are_counted(self):
        services = self.read([
            row("com.red_escuela.backend.attendance.usecase.mark", "MarkAttendanceService", 2, 8),
            row("com.red_escuela.backend.attendance.usecase.mark", "MarkAttendanceRequest", 1, 1),
            row("com.red_escuela.backend.attendance.service", "AttendanceRiskService", 1, 1),
        ])
        self.assertEqual(list(services), [("com.red_escuela.backend.attendance.usecase.mark", "MarkAttendanceService")])

    def test_inner_classes_are_folded_into_their_service(self):
        services = self.read([
            row("com.red_escuela.backend.enrollment.usecase", "EnrollService", 1, 3),
            row("com.red_escuela.backend.enrollment.usecase", "EnrollService$Helper", 1, 1),
        ])
        entry = services[("com.red_escuela.backend.enrollment.usecase", "EnrollService")]
        self.assertEqual((entry["lines_missed"], entry["lines_covered"]), (2, 4))

    def test_module_summary_counts_services_that_never_ran(self):
        services = self.read([
            row("com.red_escuela.backend.billing.usecase", "AService", 5, 0),
            row("com.red_escuela.backend.billing.usecase", "BService", 0, 5),
        ])
        billing = summary.summarize_by_module(services)["billing"]
        self.assertEqual((billing["classes"], billing["never_executed"]), (2, 1))
        self.assertEqual(summary.module_row("billing", billing), "| billing | 2 | 1 | 50.0% | n/a |")


if __name__ == "__main__":
    unittest.main()
