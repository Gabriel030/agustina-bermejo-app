import json
import os
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'detect-changes.sh')

GH_SHIM = r'''#!/usr/bin/env bash
case "$*" in
  *"--event push"*)
    [ "${GH_FAIL_LIST:-}" = "1" ] && exit 1
    echo "${GH_LAST_PUSH_SHA:-}";;
  *"--event pull_request"*)
    echo "${GH_PR_RUN:-}";;
  *"run download"*)
    target="${@: -1}"
    mkdir -p "$target"
    echo "{\"tree\":\"${GH_RECORDED_TREE}\",\"pr\":\"${GH_RECORDED_PR:-7}\",\"backend\":\"${GH_BACKEND:-true}\",\"mutation\":\"${GH_MUTATION:-false}\",\"frontend\":\"${GH_FRONTEND:-true}\"}" > "$target/tested-tree.json";;
esac
'''


def git(cwd, *args):
    return subprocess.run(['git', *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def commit_files(cwd, files, message):
    for name, content in files.items():
        path = os.path.join(cwd, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as handle:
            handle.write(content)
    git(cwd, 'add', '-A')
    git(cwd, 'commit', '-qm', message)
    return git(cwd, 'rev-parse', 'HEAD')


class DetectChangesTest(unittest.TestCase):

    def setUp(self):
        self.root = tempfile.mkdtemp()
        self.repo = os.path.join(self.root, 'repo')
        self.bin = os.path.join(self.root, 'bin')
        os.makedirs(self.repo)
        os.makedirs(self.bin)
        git(self.repo, 'init', '-q')
        git(self.repo, 'config', 'user.email', 'ci@example.com')
        git(self.repo, 'config', 'user.name', 'ci')
        git(self.repo, 'checkout', '-q', '-b', 'develop')
        self.base = commit_files(self.repo, {'backend/A.java': 'a', 'frontend/a.js': 'a', 'frontend/package-lock.json': '{}'}, 'base')
        shim = os.path.join(self.bin, 'gh')
        with open(shim, 'w', encoding='utf-8') as handle:
            handle.write(GH_SHIM)
        os.chmod(shim, 0o755)

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def merge_frontend_pull_request(self):
        git(self.repo, 'checkout', '-q', '-b', 'pr')
        commit_files(self.repo, {'frontend/a.js': 'b'}, 'frontend change')
        git(self.repo, 'checkout', '-q', 'develop')
        commit_files(self.repo, {'docs/x.md': 'x'}, 'docs')
        git(self.repo, 'merge', '-q', '--no-ff', 'pr', '-m', 'Merge pull request #7')
        return git(self.repo, 'rev-parse', 'HEAD^{tree}')

    def run_script(self, **env):
        output = os.path.join(self.root, 'output')
        open(output, 'w').close()
        full = dict(os.environ)
        full.update({
            'PATH': self.bin + os.pathsep + full['PATH'],
            'GITHUB_OUTPUT': output,
            'GITHUB_REPOSITORY': 'owner/repo',
            'RUNNER_TEMP': os.path.join(self.root, 'tmp'),
        })
        full.update(env)
        subprocess.run(['bash', SCRIPT], cwd=self.repo, env=full, check=True, capture_output=True, text=True)
        with open(output, encoding='utf-8') as handle:
            return dict(line.rstrip('\n').split('=', 1) for line in handle if '=' in line)

    def push(self, **env):
        return self.run_script(GITHUB_EVENT_NAME='push', GITHUB_REF='refs/heads/develop', **env)

    def test_push_reuses_the_areas_the_pull_request_ran_when_the_tree_matches(self):
        tree = self.merge_frontend_pull_request()
        result = self.push(GH_LAST_PUSH_SHA=self.base, GH_PR_RUN='4242', GH_RECORDED_TREE=tree, GH_BACKEND='false')
        self.assertEqual('true', result['reuse_frontend'])
        self.assertEqual('false', result['reuse_backend'])
        self.assertEqual('4242', result['reuse_run'])
        self.assertEqual('7', result['reuse_pr'])

    def test_push_runs_everything_when_the_tree_differs(self):
        self.merge_frontend_pull_request()
        result = self.push(GH_LAST_PUSH_SHA=self.base, GH_PR_RUN='4242', GH_RECORDED_TREE='deadbeef')
        self.assertEqual('false', result['reuse_frontend'])
        self.assertEqual('', result['reuse_run'])

    def test_push_rejects_a_record_with_a_non_numeric_pull_request(self):
        tree = self.merge_frontend_pull_request()
        result = self.push(GH_LAST_PUSH_SHA=self.base, GH_PR_RUN='4242', GH_RECORDED_TREE=tree, GH_RECORDED_PR='x; rm -rf')
        self.assertEqual('false', result['reuse_frontend'])

    def test_push_skips_backend_when_only_the_frontend_changed_since_the_last_green_push(self):
        self.merge_frontend_pull_request()
        result = self.push(GH_LAST_PUSH_SHA=self.base)
        self.assertEqual('false', result['relevant'])
        self.assertEqual('true', result['frontend'])
        self.assertEqual('false', result['lockfile'])

    def test_push_runs_everything_when_the_last_green_push_cannot_be_listed(self):
        commit_files(self.repo, {'docs/x.md': 'x'}, 'docs')
        result = self.push(GH_FAIL_LIST='1')
        self.assertEqual('true', result['relevant'])
        self.assertEqual('true', result['frontend'])
        self.assertEqual('true', result['lockfile'])

    def test_push_runs_everything_when_the_last_green_push_is_not_in_the_history(self):
        commit_files(self.repo, {'docs/x.md': 'x'}, 'docs')
        result = self.push(GH_LAST_PUSH_SHA='1' * 40)
        self.assertEqual('true', result['relevant'])

    def test_push_runs_everything_after_a_force_push_that_dropped_the_last_green_commit(self):
        old = commit_files(self.repo, {'backend/B.java': 'b'}, 'dropped')
        git(self.repo, 'reset', '-q', '--hard', self.base)
        git(self.repo, 'reflog', 'expire', '--expire=now', '--all')
        git(self.repo, 'gc', '-q', '--prune=now')
        commit_files(self.repo, {'docs/x.md': 'x'}, 'replacement')
        result = self.push(GH_LAST_PUSH_SHA=old)
        self.assertEqual('true', result['relevant'])

    def test_push_to_master_always_runs_everything(self):
        self.merge_frontend_pull_request()
        result = self.run_script(GITHUB_EVENT_NAME='push', GITHUB_REF='refs/heads/master', GH_LAST_PUSH_SHA=self.base)
        self.assertEqual('true', result['relevant'])
        self.assertEqual('true', result['mutation'])
        self.assertEqual('false', result['reuse_backend'])

    def test_pull_request_records_the_tested_tree_and_the_areas_it_runs(self):
        commit_files(self.repo, {'frontend/a.js': 'c'}, 'frontend only')
        out = os.path.join(self.root, 'tested')
        result = self.run_script(GITHUB_EVENT_NAME='pull_request', GITHUB_REF='refs/pull/9/merge', PR_BASE_SHA=self.base, PR_NUMBER='9', TESTED_TREE_DIR=out)
        self.assertEqual('false', result['relevant'])
        with open(os.path.join(out, 'tested-tree.json'), encoding='utf-8') as handle:
            record = json.load(handle)
        self.assertEqual(git(self.repo, 'rev-parse', 'HEAD^{tree}'), record['tree'])
        self.assertEqual('9', record['pr'])
        self.assertEqual('false', record['backend'])
        self.assertEqual('true', record['frontend'])

    def test_a_change_list_above_64_kilobytes_still_detects_the_backend_change(self):
        files = {'backend/Z.java': 'z'}
        for index in range(1500):
            files['docs/' + ('long-directory-name-' * 3) + str(index) + '.md'] = 'x'
        commit_files(self.repo, files, 'many files')
        result = self.run_script(GITHUB_EVENT_NAME='pull_request', GITHUB_REF='refs/pull/9/merge', PR_BASE_SHA=self.base, PR_NUMBER='9')
        self.assertEqual('true', result['relevant'])

    def test_a_change_list_above_64_kilobytes_detects_the_lockfile_and_frontend(self):
        files = {'frontend/package-lock.json': '{"a":1}', 'frontend/b.js': 'b'}
        for index in range(1500):
            files['docs/' + ('long-directory-name-' * 3) + str(index) + '.md'] = 'x'
        commit_files(self.repo, files, 'many files')
        result = self.run_script(GITHUB_EVENT_NAME='pull_request', GITHUB_REF='refs/pull/9/merge', PR_BASE_SHA=self.base, PR_NUMBER='9')
        self.assertEqual('true', result['lockfile'])
        self.assertEqual('true', result['frontend'])


if __name__ == '__main__':
    unittest.main()
