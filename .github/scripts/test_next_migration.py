import os
import subprocess
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCRIPT = os.path.join(ROOT, 'scripts', 'next-migration.sh')
MIGRATIONS = 'backend/src/main/resources/db/migration'


def git(cwd, *args):
    return subprocess.run(['git', *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


class NextMigrationTest(unittest.TestCase):
    def build(self, develop, master, working=(), create_master=True):
        directory = tempfile.mkdtemp()
        git(directory, 'init', '-q')
        git(directory, 'config', 'user.email', 't@t')
        git(directory, 'config', 'user.name', 't')
        os.makedirs(os.path.join(directory, MIGRATIONS))
        refs = [('origin/develop', develop)]
        if create_master:
            refs.append(('origin/master', master))
        for ref, names in refs:
            git(directory, 'rm', '-rq', '--ignore-unmatch', MIGRATIONS)
            os.makedirs(os.path.join(directory, MIGRATIONS), exist_ok=True)
            for name in names:
                with open(os.path.join(directory, MIGRATIONS, name), 'w') as handle:
                    handle.write('select 1;')
            git(directory, 'add', '-A')
            git(directory, 'commit', '-qm', ref, '--allow-empty')
            git(directory, 'update-ref', 'refs/remotes/' + ref, 'HEAD')
        for name in working:
            with open(os.path.join(directory, MIGRATIONS, name), 'w') as handle:
                handle.write('select 1;')
        return directory

    def run_script(self, directory, *args):
        env = dict(os.environ, NEXT_MIGRATION_ROOT=directory)
        return subprocess.run(['bash', SCRIPT, *args], capture_output=True, text=True, env=env)

    def test_prints_max_plus_one_across_refs(self):
        directory = self.build(['V1__a.sql', 'V5__b.sql'], ['V1__a.sql', 'V7__c.sql'])
        result = self.run_script(directory)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout.strip(), '8')

    def test_missing_ref_warns_and_uses_other(self):
        directory = self.build(['V1__a.sql', 'V5__b.sql'], [], create_master=False)
        result = self.run_script(directory)
        self.assertEqual(result.stdout.strip(), '6')
        self.assertIn('origin/master no existe', result.stderr)

    def test_duplicate_within_ref_warns(self):
        directory = self.build(['V1__a.sql', 'V2__b.sql', 'V2__c.sql'], ['V1__a.sql'])
        result = self.run_script(directory)
        self.assertEqual(result.stdout.strip(), '3')
        self.assertIn('V2 duplicado dentro de origin/develop', result.stderr)

    def test_working_tree_duplicate_warns(self):
        directory = self.build(['V1__a.sql', 'V2__b.sql'], ['V1__a.sql'], working=['V2__mine.sql'])
        result = self.run_script(directory)
        self.assertEqual(result.stdout.strip(), '3')
        self.assertIn('V2__mine.sql usa V2', result.stderr)

    def test_check_accepts_next_number(self):
        directory = self.build(['V1__a.sql', 'V2__b.sql'], ['V1__a.sql'])
        self.assertEqual(self.run_script(directory, '--check', '3').returncode, 0)

    def test_check_rejects_existing_and_gap(self):
        directory = self.build(['V1__a.sql', 'V2__b.sql'], ['V1__a.sql'])
        self.assertEqual(self.run_script(directory, '--check', '2').returncode, 1)
        self.assertEqual(self.run_script(directory, '--check', '5').returncode, 1)


if __name__ == '__main__':
    unittest.main()
