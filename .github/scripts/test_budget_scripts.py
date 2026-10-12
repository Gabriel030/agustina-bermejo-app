import importlib.util
import json
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


budget = load('test_budget', 'test-budget.py')


def write_report(directory, name, seconds):
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, 'TEST-%s.xml' % name), 'w', encoding='utf-8') as handle:
        handle.write('<testsuite name="%s" time="%s" tests="1" errors="0" skipped="0" failures="0"></testsuite>' % (name, seconds))


def write_source(root, name, body):
    path = os.path.join(root, name + '.java')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        handle.write(body)


class Args:
    def __init__(self, **values):
        self.__dict__.update(dict(
            shard=0, meta_out=None, max_class=30.0, max_slow_class=90.0, max_shard=180.0, max_contexts=22,
            baseline=None, changed=None, test_root='', out='', shards='', reports=''))
        self.__dict__.update(values)


class BudgetTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.sources = os.path.join(self.root, 'src')
        self.reports = os.path.join(self.root, 'reports')

    def tearDown(self):
        self.tmp.cleanup()

    def check(self, **values):
        return budget.check_shard(Args(reports=self.reports, test_root=self.sources, **values))

    def test_class_over_the_limit_fails(self):
        write_report(self.reports, 'com.acme.SlowIT', 31.0)
        self.assertEqual(1, self.check())

    def test_class_under_the_limit_passes(self):
        write_report(self.reports, 'com.acme.FastIT', 29.0)
        self.assertEqual(0, self.check())

    def test_low_threshold_makes_the_same_class_fail(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(0, self.check())
        self.assertEqual(1, self.check(max_class=1.0))

    def test_slow_test_class_gets_the_higher_limit(self):
        write_report(self.reports, 'com.acme.LongScenarioIT', 60.0)
        write_source(self.sources, 'com/acme/LongScenarioIT', '@SlowTest(reason = "walks twelve months")\nclass LongScenarioIT {}')
        self.assertEqual(0, self.check())

    def test_slow_test_class_over_its_own_limit_fails(self):
        write_report(self.reports, 'com.acme.LongScenarioIT', 91.0)
        write_source(self.sources, 'com/acme/LongScenarioIT', '@SlowTest(reason = "walks twelve months")\nclass LongScenarioIT {}')
        self.assertEqual(1, self.check())

    def test_shard_over_the_limit_fails(self):
        for index in range(7):
            write_report(self.reports, 'com.acme.Part%dIT' % index, 28.0)
        self.assertEqual(1, self.check())

    def test_nested_class_time_counts_for_the_outer_class(self):
        write_report(self.reports, 'com.acme.OuterIT', 20.0)
        write_report(self.reports, 'com.acme.OuterIT$Inner', 15.0)
        self.assertEqual(1, self.check())

    def test_missing_reports_do_not_fail_the_check(self):
        self.assertEqual(0, self.check())

    def test_aggregate_counts_unique_contexts_and_fails_over_the_cap(self):
        shards = os.path.join(self.root, 'shards')
        for shard in range(2):
            directory = os.path.join(shards, 'shard-%d' % shard)
            write_report(os.path.join(directory, 'reports'), 'com.acme.A%dIT' % shard, 3.0)
            with open(os.path.join(directory, 'contexts.txt'), 'w', encoding='utf-8') as handle:
                for index in range(12):
                    handle.write('hash%d\tcom.acme.A\n' % (index + shard * 6))
            with open(os.path.join(directory, 'meta.json'), 'w', encoding='utf-8') as handle:
                json.dump({'start': 100, 'end': 160 + shard, 'test_seconds': 3.0}, handle)
        out = os.path.join(self.root, 'out')
        status = budget.aggregate(Args(shards=shards, out=out, test_root=self.sources))
        self.assertEqual(0, status)
        with open(os.path.join(out, 'metrics.json'), encoding='utf-8') as handle:
            metrics = json.load(handle)
        self.assertEqual(18, metrics['contexts'])
        self.assertEqual(61, metrics['wall_seconds'])
        self.assertEqual(0, budget.aggregate(Args(shards=shards, out=out, test_root=self.sources, max_contexts=22)))
        self.assertEqual(1, budget.aggregate(Args(shards=shards, out=out, test_root=self.sources, max_contexts=17)))


if __name__ == '__main__':
    unittest.main()
