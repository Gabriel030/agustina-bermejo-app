#!/usr/bin/env python3
import argparse
import csv
import sys
from collections import defaultdict

BASE_PACKAGE = "com.red_escuela.backend."
SERVICE_SUFFIX = "Service"
COUNTER_FIELDS = ("lines_missed", "lines_covered", "branches_missed", "branches_covered")


def module_of(package):
    remainder = package[len(BASE_PACKAGE):] if package.startswith(BASE_PACKAGE) else package
    return remainder.split(".")[0]


def is_usecase_package(package):
    return package.startswith(BASE_PACKAGE) and ".usecase" in package + "."


def outer_class_name(class_name):
    return class_name.split("$")[0]


def read_usecase_services(csv_path):
    services = {}
    with open(csv_path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            package = row["PACKAGE"]
            if not is_usecase_package(package):
                continue
            outer = outer_class_name(row["CLASS"])
            if not outer.endswith(SERVICE_SUFFIX):
                continue
            entry = services.setdefault((package, outer), dict.fromkeys(COUNTER_FIELDS, 0))
            entry["lines_missed"] += int(row["LINE_MISSED"])
            entry["lines_covered"] += int(row["LINE_COVERED"])
            entry["branches_missed"] += int(row["BRANCH_MISSED"])
            entry["branches_covered"] += int(row["BRANCH_COVERED"])
    return services


def percentage(covered, missed):
    total = covered + missed
    return None if total == 0 else 100.0 * covered / total


def format_percentage(value):
    return "n/a" if value is None else f"{value:.1f}%"


def is_never_executed(entry):
    return entry["lines_covered"] == 0 and entry["lines_missed"] > 0


def summarize_by_module(services):
    modules = defaultdict(lambda: {"classes": 0, "never_executed": 0, **dict.fromkeys(COUNTER_FIELDS, 0)})
    for (package, _), entry in services.items():
        module = modules[module_of(package)]
        module["classes"] += 1
        module["never_executed"] += 1 if is_never_executed(entry) else 0
        for field in COUNTER_FIELDS:
            module[field] += entry[field]
    return modules


def module_row(name, module):
    line_pct = percentage(module["lines_covered"], module["lines_missed"])
    branch_pct = percentage(module["branches_covered"], module["branches_missed"])
    return (f"| {name} | {module['classes']} | {module['never_executed']} | "
            f"{format_percentage(line_pct)} | {format_percentage(branch_pct)} |")


def render_module_table(modules):
    rows = ["| Module | Services | Never executed | Line coverage | Branch coverage |",
            "|---|---:|---:|---:|---:|"]
    totals = {"classes": 0, "never_executed": 0, **dict.fromkeys(COUNTER_FIELDS, 0)}
    for name in sorted(modules):
        for field in totals:
            totals[field] += modules[name][field]
        rows.append(module_row(name, modules[name]))
    rows.append(module_row("**total**", totals))
    return rows


def render_class_table(services):
    rows = ["| Service | Lines | Line coverage | Branch coverage |", "|---|---:|---:|---:|"]
    ordered = sorted(
        services.items(),
        key=lambda item: (percentage(item[1]["lines_covered"], item[1]["lines_missed"]) or 0.0, item[0]),
    )
    for (package, name), entry in ordered:
        lines = entry["lines_covered"] + entry["lines_missed"]
        line_pct = percentage(entry["lines_covered"], entry["lines_missed"])
        branch_pct = percentage(entry["branches_covered"], entry["branches_missed"])
        rows.append(f"| {module_of(package)}.{name} | {lines} | {format_percentage(line_pct)} | "
                    f"{format_percentage(branch_pct)} |")
    return rows


def main():
    parser = argparse.ArgumentParser(description="Coverage of the usecase *Service classes per module")
    parser.add_argument("csv_report", help="jacoco.csv produced by the report goal")
    parser.add_argument("--classes", action="store_true", help="also list every service, least covered first")
    parser.add_argument("--markdown-out", help="write the output to this file as well")
    arguments = parser.parse_args()

    services = read_usecase_services(arguments.csv_report)
    if not services:
        print("No usecase *Service classes found in the report", file=sys.stderr)
        return 1

    output = ["## Usecase service coverage (JaCoCo)", ""]
    output += render_module_table(summarize_by_module(services))
    if arguments.classes:
        output += ["", "### Per service", ""] + render_class_table(services)
    text = "\n".join(output) + "\n"
    print(text)
    if arguments.markdown_out:
        with open(arguments.markdown_out, "w", encoding="utf-8") as handle:
            handle.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
