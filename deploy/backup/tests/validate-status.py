import json
import re
import sys

from jsonschema import Draft202012Validator, FormatChecker

schema = json.load(open(sys.argv[1]))
document = json.load(open(sys.argv[2]))
validator = Draft202012Validator(schema, format_checker=FormatChecker())
errors = [f"{'/'.join(map(str, e.absolute_path)) or '<root>'}: {e.message}" for e in validator.iter_errors(document)]

DATE_TIME = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")


def check_dates(node, path=""):
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ("generatedAt", "takenAt", "ranAt", "at", "lastCompleteBackupAt") and value is not None:
                if not isinstance(value, str) or not DATE_TIME.match(value):
                    errors.append(f"{path}/{key}: fecha invalida {value!r}")
            check_dates(value, f"{path}/{key}")
    elif isinstance(node, list):
        for index, item in enumerate(node):
            check_dates(item, f"{path}/{index}")


check_dates(document)
if errors:
    print("\n".join(errors))
    sys.exit(1)
