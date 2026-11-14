import importlib.util
import os
import shutil
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))

spec = importlib.util.spec_from_file_location('check_env_example', os.path.join(HERE, 'check-env-example.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def write(root, name, content):
    path = os.path.join(root, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        handle.write(content)


class CheckEnvExampleTest(unittest.TestCase):

    def setUp(self):
        self.root = tempfile.mkdtemp()
        write(self.root, 'backend/src/main/resources/application.properties', 'a=${JWT_SECRET}\nb=${DB_URL:x}\n')
        write(self.root, 'frontend/src/App.jsx', 'const a = import.meta.env.VITE_GOOGLE_CLIENT_ID; import.meta.env.DEV')
        write(self.root, 'docker-compose.prod.yml', 'x: ${SITE_ADDRESS:-:80}\ny: ${POSTGRES_PASSWORD:?obligatoria}\n')

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_repositoryEnvExampleIsUpToDate(self):
        missing, stale = checker.check(REPO)
        self.assertEqual([], missing)
        self.assertEqual([], stale)

    def test_reportsVariableMissingFromEnvExample(self):
        write(self.root, 'deploy/.env.example', 'JWT_SECRET=x\n# DB_URL=y\nVITE_GOOGLE_CLIENT_ID=\nSITE_ADDRESS=a\n')
        missing, stale = checker.check(self.root)
        self.assertEqual(['POSTGRES_PASSWORD'], missing)
        self.assertEqual([], stale)

    def test_reportsStaleVariableNoFileReadsAnymore(self):
        write(self.root, 'deploy/.env.example',
              'JWT_SECRET=x\n# DB_URL=y\nVITE_GOOGLE_CLIENT_ID=\nSITE_ADDRESS=a\nPOSTGRES_PASSWORD=p\nOLD_VAR=1\n')
        missing, stale = checker.check(self.root)
        self.assertEqual([], missing)
        self.assertEqual(['OLD_VAR'], stale)

    def test_builtInViteVariablesAreIgnored(self):
        self.assertNotIn('DEV', checker.frontend_variables(self.root))


if __name__ == '__main__':
    unittest.main()
