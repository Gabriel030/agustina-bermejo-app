import importlib.util
import json
import os
import shutil
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


budget = load('test_budget', 'test-budget.py')
shards = load('test_shards', 'test-shards.py')


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
            shard=0, meta_out=None, max_class=30.0, max_slow_class=90.0, max_shard=180.0, max_contexts=22, shard_limit='error', enforcement='block',
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

    def check(self, wall=None, **values):
        saved = {key: os.environ.pop(key, None) for key in ('SHARD_START', 'SHARD_END')}
        if wall is not None:
            os.environ['SHARD_START'] = '1000'
            os.environ['SHARD_END'] = str(1000 + wall)
        try:
            return budget.check_shard(Args(reports=self.reports, test_root=self.sources, **values))
        finally:
            for key in ('SHARD_START', 'SHARD_END'):
                os.environ.pop(key, None)
                if saved[key] is not None:
                    os.environ[key] = saved[key]

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

    def test_shard_clock_over_the_limit_fails(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(1, self.check(wall=181))

    def test_shard_clock_under_the_limit_passes(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(0, self.check(wall=179))

    def test_shard_clock_at_290_seconds_passes_with_the_default_limit(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(0, self.check(wall=290, max_shard=budget.DEFAULT_MAX_SHARD))

    def test_summed_jvm_time_above_the_limit_does_not_fail_when_the_clock_is_under(self):
        for index in range(16):
            write_report(self.reports, 'com.acme.Part%dIT' % index, 30.0)
        self.assertEqual(0, self.check(wall=250, max_shard=budget.DEFAULT_MAX_SHARD))

    def test_shard_clock_over_the_limit_only_warns_when_asked(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(0, self.check(wall=400, shard_limit='warn'))

    def test_class_over_the_limit_still_fails_when_the_shard_limit_only_warns(self):
        write_report(self.reports, 'com.acme.SlowIT', 31.0)
        self.assertEqual(1, self.check(wall=400, shard_limit='warn'))

    def test_shard_clock_exactly_at_the_limit_passes(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(0, self.check(wall=180))

    def test_only_the_shard_start_skips_the_shard_limit(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        os.environ.pop('SHARD_END', None)
        os.environ['SHARD_START'] = '1000'
        try:
            self.assertEqual(0, budget.check_shard(Args(reports=self.reports, test_root=self.sources)))
        finally:
            os.environ.pop('SHARD_START', None)

    def test_shard_limit_flag_defaults_to_error(self):
        parser_args = ['check-shard', '--shard', '0', '--reports', self.reports]
        import sys
        saved = sys.argv
        sys.argv = ['test-budget.py'] + parser_args
        try:
            captured = {}
            original = budget.check_shard
            budget.check_shard = lambda args: captured.setdefault('mode', args.shard_limit) and 0
            with self.assertRaises(SystemExit):
                budget.main()
        finally:
            budget.check_shard = original
            sys.argv = saved
        self.assertEqual('error', captured['mode'])

    def test_missing_clock_skips_the_shard_limit(self):
        write_report(self.reports, 'com.acme.FastIT', 5.0)
        self.assertEqual(0, self.check())

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
        self.assertEqual(0, budget.aggregate(Args(shards=shards, out=out, test_root=self.sources, max_contexts=17, enforcement='warn')))


class ShardTimingsTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()
        self.original = shards.TIMINGS
        shards.TIMINGS = os.path.join(self.root, 'timings.txt')
        with open(shards.TIMINGS, 'w', encoding='utf-8') as handle:
            handle.write('5.00 a/A\n')

    def tearDown(self):
        shards.TIMINGS = self.original
        shutil.rmtree(self.root, ignore_errors=True)

    def write_history(self, runs, samples):
        directory = os.path.join(self.root, 'baseline')
        os.makedirs(directory, exist_ok=True)
        with open(os.path.join(directory, 'samples.json'), 'w', encoding='utf-8') as handle:
            json.dump({'runs': runs, 'samples': samples}, handle)
        return directory

    def test_merge_keeps_only_the_last_window_of_samples(self):
        history = {'runs': 3, 'samples': {'a/A': [1.0, 2.0, 3.0]}}
        merged = shards.merge_run(history, {'a/A': 10.0}, {'a/A'}, window=3)
        self.assertEqual([2.0, 3.0, 10.0], merged['samples']['a/A'])
        self.assertEqual(3, merged['runs'])

    def test_average_smooths_a_single_noisy_run(self):
        history = {'runs': 3, 'samples': {'a/A': [4.0, 5.0, 6.0]}}
        merged = shards.merge_run(history, {'a/A': 29.0}, {'a/A'})
        self.assertEqual(11.0, shards.averages(merged['samples'])['a/A'])

    def test_merge_drops_classes_that_no_longer_exist(self):
        history = {'runs': 1, 'samples': {'a/A': [1.0], 'a/Gone': [9.0]}}
        merged = shards.merge_run(history, {'a/A': 2.0}, {'a/A'})
        self.assertEqual({'a/A'}, set(merged['samples']))

    def test_class_not_measured_keeps_its_history(self):
        history = {'runs': 2, 'samples': {'a/A': [1.0, 3.0]}}
        merged = shards.merge_run(history, {}, {'a/A'})
        self.assertEqual([1.0, 3.0], merged['samples']['a/A'])

    def test_legacy_baseline_without_history_seeds_one_run(self):
        directory = os.path.join(self.root, 'legacy')
        os.makedirs(directory)
        with open(os.path.join(directory, 'timings.txt'), 'w', encoding='utf-8') as handle:
            handle.write('7.00 a/A\n')
        history = shards.read_history(directory)
        self.assertEqual(1, history['runs'])
        self.assertEqual({'a/A': [7.0]}, history['samples'])

    def test_missing_baseline_seeds_from_the_versioned_timings(self):
        history = shards.read_history(os.path.join(self.root, 'absent'))
        self.assertEqual(1, history['runs'])
        self.assertEqual({'a/A': [5.0]}, history['samples'])

    def test_use_ignores_a_baseline_with_too_little_history(self):
        directory = self.write_history(shards.MIN_HISTORY - 1, {'a/A': [90.0, 90.0]})
        shards.use(directory)
        self.assertEqual({'a/A': 5.0}, shards.load_timings())

    def test_use_ignores_a_legacy_single_run_baseline(self):
        directory = os.path.join(self.root, 'legacy')
        os.makedirs(directory)
        with open(os.path.join(directory, 'timings.txt'), 'w', encoding='utf-8') as handle:
            handle.write('90.00 a/A\n')
        shards.use(directory)
        self.assertEqual({'a/A': 5.0}, shards.load_timings())

    def test_use_applies_the_average_once_there_is_enough_history(self):
        directory = self.write_history(shards.MIN_HISTORY, {'a/A': [10.0, 20.0, 30.0]})
        shards.use(directory)
        self.assertEqual({'a/A': 20.0}, shards.load_timings())

    def write_raw(self, content):
        directory = os.path.join(self.root, 'raw')
        os.makedirs(directory, exist_ok=True)
        with open(os.path.join(directory, 'samples.json'), 'w', encoding='utf-8') as handle:
            handle.write(content)
        return directory

    def test_corrupt_history_seeds_from_the_versioned_timings(self):
        history = shards.read_history(self.write_raw('{not json'))
        self.assertEqual(1, history['runs'])
        self.assertEqual({'a/A': [5.0]}, history['samples'])

    def test_history_without_samples_key_seeds_from_the_versioned_timings(self):
        history = shards.read_history(self.write_raw('{"runs": 5}'))
        self.assertEqual(1, history['runs'])
        self.assertEqual({'a/A': [5.0]}, history['samples'])

    def test_use_with_runs_but_no_samples_keeps_the_versioned_timings(self):
        shards.use(self.write_raw('{"runs": 5}'))
        self.assertEqual({'a/A': 5.0}, shards.load_timings())
        self.assertGreater(os.path.getsize(shards.TIMINGS), 0)

    def test_use_with_corrupt_history_keeps_the_versioned_timings(self):
        shards.use(self.write_raw('{not json'))
        self.assertEqual({'a/A': 5.0}, shards.load_timings())

    def test_history_never_exceeds_the_window(self):
        history = {'runs': 1, 'samples': {}}
        for index in range(shards.WINDOW + 4):
            history = shards.merge_run(history, {'a/A': float(index)}, {'a/A'})
        self.assertEqual(shards.WINDOW, len(history['samples']['a/A']))
        self.assertEqual(float(shards.WINDOW + 3), history['samples']['a/A'][-1])
        self.assertEqual(shards.WINDOW, history['runs'])


if __name__ == '__main__':
    unittest.main()
