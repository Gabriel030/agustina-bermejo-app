#!/usr/bin/env python3
"""Reporte informativo de tiempos de la suite de tests del backend y tope de contextos de Spring.

Uso:
  test-report.py shard-meta --shard N --reports DIR --meta-out FILE
      escribe la duracion del shard (SHARD_END - SHARD_START) y la suma de las JVM; nunca falla
  test-report.py aggregate --shards DIR --out DIR [--baseline FILE] [--changed FILE] [--max-contexts N]
      une los shards, escribe report.md y metrics.json y falla si hay mas contextos de Spring que el tope

Tope (cantidad): --max-contexts 24
"""
import argparse
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEST_ROOT = os.path.join(ROOT, 'backend', 'src', 'test', 'java')
MARKER = '<!-- test-time-report -->'
SLOW = re.compile(r'@SlowTest\s*\(\s*reason\s*=\s*"((?:[^"\\]|\\.)*)"')

DEFAULT_MAX_CONTEXTS = 24


def add_limits(parser):
    parser.add_argument('--max-contexts', type=int, default=DEFAULT_MAX_CONTEXTS)
    parser.add_argument('--test-root', default=TEST_ROOT)
    parser.add_argument('--enforcement', choices=['block', 'warn'], default='block')


def class_times(reports):
    totals = {}
    for path in sorted(glob.glob(os.path.join(reports, 'TEST-*.xml'))):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        name = suite.get('name', '').split('$')[0].replace('.', '/')
        if name:
            totals[name] = totals.get(name, 0.0) + float(suite.get('time', '0') or 0)
    return totals


def flaky_tests(reports):
    found = []
    for path in sorted(glob.glob(os.path.join(reports, 'TEST-*.xml'))):
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter('testcase'):
            if case.find('flakyFailure') is not None or case.find('flakyError') is not None:
                found.append('%s.%s' % (case.get('classname', suite.get('name', '')), case.get('name', '')))
    return found


def slow_reason(test_root, name):
    path = os.path.join(test_root, name + '.java')
    if not os.path.exists(path):
        return None
    with open(path, encoding='utf-8') as handle:
        match = SLOW.search(handle.read())
    return match.group(1) if match else None


def short(name):
    return name.rsplit('/', 1)[-1]


def read_contexts(path):
    found = {}
    if os.path.exists(path):
        with open(path, encoding='utf-8') as handle:
            for line in handle:
                parts = line.rstrip('\n').split('\t')
                if parts[0]:
                    found.setdefault(parts[0], parts[1] if len(parts) > 1 else '')
    return found


def shard_meta(args):
    times = class_times(args.reports)
    start = int(os.environ.get('SHARD_START', '0') or 0)
    end = int(os.environ.get('SHARD_END', '0') or 0)
    seconds = sum(times.values())
    wall = end - start if start and end >= start else None
    print('shard %d: %d classes, %.0fs of test time summed over the JVMs, %s' % (
        args.shard, len(times), seconds, '%.0fs of wall clock' % wall if wall is not None else 'wall clock unavailable'))
    os.makedirs(os.path.dirname(os.path.abspath(args.meta_out)), exist_ok=True)
    with open(args.meta_out, 'w', encoding='utf-8') as out:
        json.dump({'shard': args.shard, 'start': start, 'end': end, 'test_seconds': seconds}, out)
    return 0


def load_shards(directory):
    shards = []
    for path in sorted(glob.glob(os.path.join(directory, '*'))):
        if not os.path.isdir(path):
            continue
        meta = {}
        meta_path = os.path.join(path, 'meta.json')
        if os.path.exists(meta_path):
            with open(meta_path, encoding='utf-8') as handle:
                meta = json.load(handle)
        shards.append({
            'dir': path,
            'meta': meta,
            'times': class_times(os.path.join(path, 'reports')),
            'flaky': flaky_tests(os.path.join(path, 'reports')),
            'contexts': read_contexts(os.path.join(path, 'contexts.txt')),
        })
    return shards


def fmt_delta(value, previous, unit):
    if previous is None:
        return 'n/a'
    diff = value - previous
    return '%+.0f%s' % (diff, unit) if unit == 's' else '%+d' % diff


def clock(seconds):
    seconds = int(round(seconds))
    return '%d:%02d' % (seconds // 60, seconds % 60)


def aggregate(args):
    shards = load_shards(args.shards)
    times = {}
    contexts = {}
    for shard in shards:
        for name, seconds in shard['times'].items():
            times[name] = times.get(name, 0.0) + seconds
        for fingerprint, owner in shard['contexts'].items():
            contexts.setdefault(fingerprint, owner)
    starts = [s['meta'].get('start', 0) for s in shards if s['meta'].get('start')]
    ends = [s['meta'].get('end', 0) for s in shards if s['meta'].get('end')]
    wall = (max(ends) - min(starts)) if starts and ends else None
    shard_seconds = [s['meta'].get('test_seconds', sum(s['times'].values())) for s in shards]

    baseline = {}
    if args.baseline and os.path.exists(args.baseline):
        with open(args.baseline, encoding='utf-8') as handle:
            baseline = json.load(handle)

    changed = set()
    if args.changed and os.path.exists(args.changed):
        with open(args.changed, encoding='utf-8') as handle:
            for line in handle:
                line = line.strip()
                prefix = 'backend/src/test/java/'
                if line.startswith(prefix) and line.endswith('.java'):
                    changed.add(line[len(prefix):-len('.java')])

    slow = {}
    for name, seconds in times.items():
        reason = slow_reason(args.test_root, name)
        if reason is not None:
            slow[name] = (seconds, reason)

    flaky = sorted({name for shard in shards for name in shard['flaky']})

    lines = [MARKER, '### Tiempo de tests del backend', '']
    lines.append('| Métrica | Valor | Δ vs última corrida de develop |')
    lines.append('|---|---|---|')
    wall_text = clock(wall) if wall is not None else 'n/a'
    lines.append('| Tiempo de pared (%d shards en paralelo) | %s | %s |' % (
        len(shards), wall_text, fmt_delta(wall, baseline.get('wall_seconds'), 's') if wall is not None else 'n/a'))
    total = sum(times.values())
    lines.append('| Tiempo de tests sumado | %s | %s |' % (clock(total), fmt_delta(total, baseline.get('total_seconds'), 's')))
    lines.append('| Contextos de Spring distintos | %d / %d | %s |' % (
        len(contexts), args.max_contexts, fmt_delta(len(contexts), baseline.get('contexts'), '')))
    lines.append('')
    if shards:
        for index, shard in enumerate(shards):
            meta = shard['meta']
            span = meta.get('end', 0) - meta.get('start', 0) if meta.get('start') and meta.get('end') else None
            lines.append('- Shard %d: %s de reloj, %s de tests sumados (JVM)' % (
                index, clock(span) if span is not None else 'n/a', clock(shard_seconds[index])))
        lines.append('')

    touched = {n: t for n, t in times.items() if n in changed}
    pool, title = (touched, 'Top 5 clases más lentas tocadas por el PR') if touched else (times, 'Top 5 clases más lentas (el PR no toca clases de test)')
    lines.append('**%s**' % title)
    lines.append('')
    lines.append('| Clase | Tiempo | Base develop |')
    lines.append('|---|---|---|')
    base_classes = baseline.get('classes', {})
    for name, seconds in sorted(pool.items(), key=lambda item: -item[1])[:5]:
        before = base_classes.get(name)
        lines.append('| `%s` | %.1fs | %s |' % (short(name), seconds, '%.1fs' % before if before is not None else 'nueva'))
    lines.append('')
    lines.append('**Clases @SlowTest (%d)**' % len(slow))
    lines.append('')
    if slow:
        lines.append('| Clase | Tiempo | Motivo |')
        lines.append('|---|---|---|')
        for name, (seconds, reason) in sorted(slow.items(), key=lambda item: -item[1][0]):
            lines.append('| `%s` | %.1fs | %s |' % (short(name), seconds, reason))
    else:
        lines.append('Ninguna.')
    lines.append('')
    failures = []
    if len(contexts) > args.max_contexts:
        failures.append('%d distinct Spring contexts, above the limit of %d' % (len(contexts), args.max_contexts))
        lines.append('> **Tope de contextos superado:** %d > %d. Reutilizá una configuración existente (`@SharedMockBeans`, mismas propiedades) en lugar de crear un contexto nuevo.' % (len(contexts), args.max_contexts))
    if flaky:
        lines.append('> **Tests flaky (fallaron y pasaron en el reintento): %d.** No los ignores, estabilizalos:' % len(flaky))
        lines.extend('> - `%s`' % name for name in flaky)
        lines.append('')

    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, 'report.md'), 'w', encoding='utf-8') as handle:
        handle.write('\n'.join(lines) + '\n')
    with open(os.path.join(args.out, 'metrics.json'), 'w', encoding='utf-8') as handle:
        json.dump({
            'wall_seconds': wall,
            'total_seconds': total,
            'contexts': len(contexts),
            'classes': {k: round(v, 2) for k, v in times.items()},
        }, handle, sort_keys=True)
    print('\n'.join(lines))
    return report_failures(failures, args.enforcement)


def report_failures(failures, enforcement):
    level = 'error' if enforcement == 'block' else 'warning'
    for failure in failures:
        print('::' + level + '::BUDGET EXCEEDED: ' + failure)
    return 1 if failures and enforcement == 'block' else 0


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='command', required=True)
    meta = sub.add_parser('shard-meta')
    meta.add_argument('--shard', type=int, required=True)
    meta.add_argument('--reports', required=True)
    meta.add_argument('--meta-out', required=True)
    agg = sub.add_parser('aggregate')
    agg.add_argument('--shards', required=True)
    agg.add_argument('--out', required=True)
    agg.add_argument('--baseline')
    agg.add_argument('--changed')
    add_limits(agg)
    args = parser.parse_args()
    sys.exit(shard_meta(args) if args.command == 'shard-meta' else aggregate(args))


if __name__ == '__main__':
    main()
