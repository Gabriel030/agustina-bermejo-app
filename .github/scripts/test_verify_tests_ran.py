import importlib.util
import os
import shutil
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'verify-tests-ran.py')
spec = importlib.util.spec_from_file_location('verify_tests_ran', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class VerifyTestsRanTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def report(self, name, tests, skipped=0, failures=0, errors=0):
        path = os.path.join(self.dir, 'TEST-com.x.' + name + '.xml')
        with open(path, 'w', encoding='utf-8') as handle:
            handle.write('<testsuite tests="%d" skipped="%d" failures="%d" errors="%d"/>' % (tests, skipped, failures, errors))

    def test_executed_class_passes(self):
        self.report('DemoSeedIT', 5)
        self.assertEqual(module.check(self.dir, ['DemoSeedIT']), [])

    def test_missing_report_is_reported(self):
        self.assertEqual(len(module.check(self.dir, ['DemoSeedIT'])), 1)

    def test_skipped_tests_are_reported(self):
        self.report('DemoSeedIT', 5, skipped=5)
        self.assertIn('salteados', module.check(self.dir, ['DemoSeedIT'])[0])

    def test_zero_tests_are_reported(self):
        self.report('DemoSeedIT', 0)
        self.assertIn('0 tests', module.check(self.dir, ['DemoSeedIT'])[0])

    def test_failed_tests_are_reported(self):
        self.report('DemoSeedIT', 5, failures=1)
        self.assertIn('fallaron', module.check(self.dir, ['DemoSeedIT'])[0])


if __name__ == '__main__':
    unittest.main()
