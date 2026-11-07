import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parent / "migrate_module.py"
BASE_DIR = "backend/src/main/java/com/red_escuela/backend"
PACKAGE = "com.red_escuela.backend"


def write(repo, relative, content):
    path = repo / BASE_DIR / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class MigrateModuleTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.repo = Path(self.directory.name)
        subprocess.run(["git", "init", "-q"], cwd=self.repo, check=True)
        (self.repo / "backend/src/test/resources/archunit-frozen").mkdir(parents=True)
        write(self.repo, "demo/service/Alpha.java", f"package {PACKAGE}.demo.service;\n\nimport {PACKAGE}.demo.service.Beta;\n\nclass Alpha {{\n    Beta beta;\n    Gamma gamma;\n}}\n")
        write(self.repo, "demo/service/Beta.java", f"package {PACKAGE}.demo.service;\n\nclass Beta {{\n    Gamma gamma;\n}}\n")
        write(self.repo, "demo/service/Gamma.java", f"package {PACKAGE}.demo.service;\n\nclass Gamma {{\n}}\n")
        write(self.repo, "demo/service/Delta.java", f"package {PACKAGE}.demo.service;\n\nclass Delta {{\n    Beta beta;\n    Gamma gamma;\n}}\n")
        mapping = self.repo / "demo.map"
        mapping.write_text("package demo.service -> demo.specialist\nmove demo.service.Beta -> demo.policy\nmove demo.service.Gamma -> demo.domain\n", encoding="utf-8")
        subprocess.run(["git", "add", "-A"], cwd=self.repo, check=True)
        subprocess.run([sys.executable, str(TOOL), "--module", "demo", "--map", str(mapping), "--repo", str(self.repo), "--skip-verify"], check=True, capture_output=True, text=True)

    def tearDown(self):
        self.directory.cleanup()

    def read(self, relative):
        return (self.repo / BASE_DIR / relative).read_text(encoding="utf-8")

    def test_each_moved_class_keeps_its_own_destination_package(self):
        self.assertIn(f"package {PACKAGE}.demo.policy;", self.read("demo/policy/Beta.java"))
        self.assertIn(f"package {PACKAGE}.demo.domain;", self.read("demo/domain/Gamma.java"))
        self.assertIn(f"package {PACKAGE}.demo.specialist;", self.read("demo/specialist/Alpha.java"))
        self.assertIn(f"package {PACKAGE}.demo.specialist;", self.read("demo/specialist/Delta.java"))

    def test_no_import_of_a_class_in_the_same_package(self):
        beta = self.read("demo/policy/Beta.java")
        alpha = self.read("demo/specialist/Alpha.java")
        delta = self.read("demo/specialist/Delta.java")
        self.assertNotIn(f"import {PACKAGE}.demo.policy.Beta;", beta)
        self.assertEqual(1, alpha.count(f"import {PACKAGE}.demo.policy.Beta;"))
        self.assertIn(f"import {PACKAGE}.demo.domain.Gamma;", alpha)
        self.assertIn(f"import {PACKAGE}.demo.domain.Gamma;", delta)
        self.assertIn(f"import {PACKAGE}.demo.policy.Beta;", delta)


if __name__ == "__main__":
    unittest.main()
