import json
import os
import subprocess
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCRIPT = os.path.join(ROOT, 'scripts', 'epic-board.sh')

CONFIG = {
    'board_label': 'cell:epic-board',
    'board_title': 'Tablero de épicas',
    'baseline_date': '2026-11-14',
    'seed_date': '2026-11-24',
    'since': '2026-11-14T00:00:00Z',
    'ignored_head_prefixes': [],
    'epics': [
        {'id': 'E23', 'name': 'Billing', 'total': 10, 'done_at_baseline': 4, 'done_at_seed': 4, 'next_step': 'Cobranzas'},
        {'id': 'E24', 'name': 'E24', 'total': 8, 'done_at_baseline': 0, 'done_at_seed': 0, 'total_estimated': True, 'next_step': ''},
        {'id': 'E30', 'name': 'E30', 'total': None, 'done_at_baseline': None, 'done_at_seed': 0, 'next_step': ''},
    ],
}

NOW = '2026-11-24T12:00:00Z'


def pr(number, labels, title='feat', merged_at='2026-11-20T10:00:00Z'):
    return {'number': number, 'title': title, 'url': f'u{number}', 'merged_at': merged_at, 'labels': labels}


class EpicBoardTest(unittest.TestCase):
    def render(self, merged, config=CONFIG):
        directory = tempfile.mkdtemp()
        with open(os.path.join(directory, 'config.json'), 'w') as handle:
            json.dump(config, handle)
        with open(os.path.join(directory, 'merged.json'), 'w') as handle:
            json.dump(merged, handle)
        result = subprocess.run(
            [SCRIPT, 'render', directory],
            capture_output=True, text=True, env={**os.environ, 'NOW': NOW},
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def row(self, out, prefix):
        return [line for line in out.splitlines() if line.startswith(prefix)][0]

    def test_render_starts_with_hidden_marker(self):
        self.assertTrue(self.render([]).startswith('<!-- epic-board -->'))

    def test_baseline_only_without_merged_prs(self):
        row = self.row(self.render([]), '| E23 Billing')
        self.assertIn('| 40 % | 40 % | 4/10 | — | Cobranzas |', row)

    def test_labelled_pr_adds_one_package(self):
        row = self.row(self.render([pr(7, ['epic:E23'])]), '| E23 Billing')
        self.assertIn('| 40 % | 50 % | 5/10 | [#7](u7) | Cobranzas |', row)

    def test_duplicated_pr_number_counts_once(self):
        row = self.row(self.render([pr(7, ['epic:E23']), pr(7, ['epic:E23'])]), '| E23 Billing')
        self.assertIn('| 50 % | 5/10 |', row)

    def test_label_match_is_case_insensitive(self):
        row = self.row(self.render([pr(7, ['epic:e23'])]), '| E23 Billing')
        self.assertIn('5/10', row)

    def test_pr_without_epic_label_is_listed_and_not_counted(self):
        out = self.render([pr(9, ['role:backend-4'], title='a|b')])
        section = out.split('### 2.')[1].split('### 3.')[0]
        self.assertIn('| [#9](u9) | `a\\|b` | 2026-11-20T10:00:00Z |', section)
        self.assertIn('| 40 % | 4/10 |', self.row(out, '| E23 Billing'))

    def test_labelled_pr_is_not_in_without_epic_section(self):
        out = self.render([pr(7, ['epic:E23'])])
        section = out.split('### 2.')[1].split('### 3.')[0]
        self.assertIn('_Ninguno._', section)

    def test_unknown_epic_is_reported_and_not_counted(self):
        out = self.render([pr(11, ['epic:E99'])])
        section = out.split('### 3.')[1]
        self.assertIn('| [#11](u11) | `epic:E99` | `feat` |', section)
        self.assertIn('| 40 % | 4/10 |', self.row(out, '| E23 Billing'))
        self.assertIn('_Ninguno._', out.split('### 2.')[1].split('### 3.')[0])

    def test_pr_with_two_epics_counts_in_both(self):
        out = self.render([pr(7, ['epic:E23', 'epic:E24'])])
        self.assertIn('5/10', self.row(out, '| E23 Billing'))
        self.assertIn('1/8 (est.)', self.row(out, '| E24'))

    def test_epic_without_total_shows_dash_and_question_mark(self):
        row = self.row(self.render([pr(7, ['epic:E30'])]), '| E30')
        self.assertIn('| — | — | 1/? | [#7](u7) | — |', row)

    def test_percentage_is_capped_at_one_hundred(self):
        merged = [pr(n, ['epic:E24']) for n in range(1, 12)]
        self.assertIn('| 0 % | 100 % | 11/8 (est.) |', self.row(self.render(merged), '| E24'))

    def test_last_prs_are_three_most_recent_first(self):
        merged = [
            pr(1, ['epic:E24'], merged_at='2026-11-15T00:00:00Z'),
            pr(2, ['epic:E24'], merged_at='2026-11-16T00:00:00Z'),
            pr(3, ['epic:E24'], merged_at='2026-11-17T00:00:00Z'),
            pr(4, ['epic:E24'], merged_at='2026-11-18T00:00:00Z'),
        ]
        self.assertIn('[#4](u4), [#3](u3), [#2](u2) |', self.row(self.render(merged), '| E24'))

    def test_seed_progress_is_the_starting_point_for_today(self):
        config = json.loads(json.dumps(CONFIG))
        config['epics'][0]['done_at_seed'] = 6
        row = self.row(self.render([pr(7, ['epic:E23'])], config), '| E23 Billing')
        self.assertIn('| 40 % | 70 % | 7/10 |', row)

    def test_unknown_baseline_with_known_total_shows_dash(self):
        config = json.loads(json.dumps(CONFIG))
        config['epics'][0]['done_at_baseline'] = None
        row = self.row(self.render([], config), '| E23 Billing')
        self.assertIn('| — | 40 % | 4/10 |', row)

    def test_unknown_epic_label_with_backticks_and_mentions_is_neutralised(self):
        out = self.render([pr(12, ['epic:x` @alguien'])])
        row = self.row(out, '| [#12]')
        self.assertIn("| `epic:x' @alguien` |", row)
        self.assertEqual(row.count('`'), 4)

    def test_title_with_mentions_and_backticks_is_neutralised(self):
        out = self.render([pr(9, [], title='@octocat `bad` ![i](http://e.com/i.png)\nnext')])
        row = self.row(out, '| [#9]')
        self.assertIn("`@octocat 'bad' ![i](http://e.com/i.png) next`", row)
        self.assertEqual(row.count('`'), 2)


if __name__ == '__main__':
    unittest.main()
