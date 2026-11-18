import json
import os
import subprocess
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCRIPT = os.path.join(ROOT, 'scripts', 'cell-board.sh')

CONFIG = {
    'board_label': 'cell:board',
    'board_title': 'Tablero de la célula',
    'follow_up_issues': [964, 965],
    'stale_minutes': 30,
    'merged_window_hours': 2,
}

NOW = '2026-11-18T02:00:00Z'


def comment(issue, at, body):
    return {'issue': issue, 'created_at': at, 'body': body, 'url': f'https://x/{issue}/{at}'}


class CellBoardTest(unittest.TestCase):
    def build(self, comments, pulls=None, merged=None, follow=None):
        directory = tempfile.mkdtemp()
        files = {
            'config.json': CONFIG,
            'pulls.json': pulls or [],
            'merged.json': merged or [],
            'comments.json': comments,
            'follow_issues.json': follow or [],
        }
        for name, content in files.items():
            with open(os.path.join(directory, name), 'w') as handle:
                json.dump(content, handle)
        return directory

    def run_script(self, command, directory):
        result = subprocess.run(
            [SCRIPT, command, directory],
            capture_output=True, text=True, env={**os.environ, 'NOW': NOW},
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_render_starts_with_hidden_marker(self):
        out = self.run_script('render', self.build([]))
        self.assertTrue(out.startswith('<!-- cell-board -->'))

    def test_open_pulls_table_shows_merge_state_and_ci(self):
        pulls = [
            {'number': 5, 'title': 'a|b', 'url': 'u5', 'draft': False, 'mergeable_state': 'clean', 'ci': 'verde'},
            {'number': 6, 'title': 'c', 'url': 'u6', 'draft': True, 'mergeable_state': 'dirty', 'ci': 'rojo'},
        ]
        out = self.run_script('render', self.build([], pulls=pulls))
        self.assertIn('| [#5](u5) | a\\|b | clean | verde |', out)
        self.assertIn('| [#6](u6) | c (borrador) | dirty | rojo |', out)

    def test_worker_request_is_pending_until_later_done_for_same_number(self):
        comments = [
            comment(10, '2026-11-18T00:10:00Z', 'NECESITO WORKER #7 para x'),
            comment(10, '2026-11-18T00:20:00Z', 'NECESITO WORKER #9'),
            comment(10, '2026-11-18T00:30:00Z', 'CORREGIDO #7'),
        ]
        directory = self.build(comments)
        out = self.run_script('render', directory)
        section = out.split('### 2.')[1].split('### 3.')[0]
        self.assertNotIn('#7', section)
        self.assertIn('#9', section)
        self.assertEqual(self.run_script('count-pending-workers', directory).strip(), '1')

    def test_done_before_request_does_not_resolve_it(self):
        comments = [
            comment(10, '2026-11-18T00:10:00Z', 'CORREGIDO #7'),
            comment(10, '2026-11-18T00:20:00Z', 'NECESITO WORKER #7'),
        ]
        self.assertEqual(self.run_script('count-pending-workers', self.build(comments)).strip(), '1')

    def test_no_pending_workers_when_all_resolved(self):
        comments = [
            comment(10, '2026-11-18T00:10:00Z', 'NECESITO WORKER #7'),
            comment(11, '2026-11-18T00:20:00Z', 'CORREGIDO #7'),
        ]
        self.assertEqual(self.run_script('count-pending-workers', self.build(comments)).strip(), '0')

    def test_fix_request_pending_until_done(self):
        comments = [
            comment(10, '2026-11-18T00:10:00Z', 'CORREGIR #3\ndetalle'),
            comment(10, '2026-11-18T00:15:00Z', 'CORREGIR #4'),
            comment(10, '2026-11-18T00:40:00Z', 'CORREGIDO #3'),
        ]
        out = self.run_script('render', self.build(comments))
        section = out.split('### 3.')[1].split('### 4.')[0]
        self.assertNotIn('#3', section)
        self.assertIn('#4', section)

    def test_markers_inside_prose_or_board_comment_are_ignored(self):
        comments = [
            comment(10, '2026-11-18T00:10:00Z', 'dije que NECESITO WORKER pero en prosa'),
            comment(10, '2026-11-18T00:11:00Z', '<!-- cell-board -->\nNECESITO WORKER #1'),
        ]
        self.assertEqual(self.run_script('count-pending-workers', self.build(comments)).strip(), '0')

    def test_worker_request_without_number_uses_issue_number(self):
        comments = [comment(42, '2026-11-18T00:10:00Z', 'NECESITO WORKER')]
        out = self.run_script('render', self.build(comments))
        self.assertIn('| #42 |', out)

    def test_merged_table_lists_entries(self):
        merged = [{'number': 8, 'title': 'feat', 'url': 'u8', 'merged_at': '2026-11-18T01:30:00Z'}]
        out = self.run_script('render', self.build([], merged=merged))
        self.assertIn('| [#8](u8) | feat | 2026-11-18T01:30:00Z |', out)

    def test_stale_follow_up_issue_is_listed_and_active_one_is_not(self):
        follow = [
            {'number': 964, 'title': 'Lider', 'created_at': '2026-11-18T00:00:00Z'},
            {'number': 965, 'title': 'Otro', 'created_at': '2026-11-18T00:00:00Z'},
        ]
        comments = [
            comment(964, '2026-11-18T01:00:00Z', 'hola'),
            comment(965, '2026-11-18T01:50:00Z', 'activo'),
        ]
        out = self.run_script('render', self.build(comments, follow=follow))
        section = out.split('### 5.')[1]
        self.assertIn('#964', section)
        self.assertIn('1 h 0 min', section)
        self.assertNotIn('#965', section)

    def test_follow_up_without_comments_falls_back_to_creation_time(self):
        follow = [{'number': 964, 'title': 'Lider', 'created_at': '2026-11-18T00:00:00Z'}]
        out = self.run_script('render', self.build([], follow=follow))
        self.assertIn('| #964 | Lider | 2026-11-18T00:00:00Z | 2 h 0 min |', out)


if __name__ == '__main__':
    unittest.main()
