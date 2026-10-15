#!/usr/bin/env python3
"""Reparto determinista de las clases de test del backend en shards por tiempo medido.

Uso:
  test-shards.py select <indice> <cantidad>   imprime el valor de -Dtest para el shard
  test-shards.py verify <cantidad>            falla si la union de shards != todas las clases o hay duplicados
  test-shards.py update <log> [<log> ...]     regenera timings.txt desde logs de surefire ("Time elapsed")
  test-shards.py update-xml <dir> [<dir> ...] [--out archivo]
                                              regenera timings.txt desde TEST-*.xml de surefire (busca recursivo)
  test-shards.py list <indice> [<cantidad>]   lista las clases del shard (cantidad por defecto 5, como la CI)
  test-shards.py run <indice> [<cantidad>]    corre el shard en local con el semaforo (backend/: ./mvnw test -Dtest=...)
  test-shards.py use <archivo>                copia <archivo> como timings.txt (tiempos medidos en develop)

Las clases que no figuran en timings.txt reciben como peso el promedio de las medidas
(minimo 1 s) y se reparten igual, de modo que ninguna queda afuera.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEST_ROOT = os.path.join(ROOT, 'backend', 'src', 'test', 'java')
TIMINGS = os.path.join(ROOT, '.github', 'test-shards', 'timings.txt')
DEFAULT_WEIGHT = 1.0
NAME = re.compile(r'(^Test.*|.*Test|.*Tests|.*TestCase|.*IT|.*Properties)\.java$')
ABSTRACT = re.compile(r'^\s*(public\s+)?abstract\s+class\s', re.M)


def discover():
    found = []
    for base, _, files in os.walk(TEST_ROOT):
        for name in files:
            if not NAME.match(name):
                continue
            path = os.path.join(base, name)
            with open(path, encoding='utf-8') as handle:
                if ABSTRACT.search(handle.read()):
                    continue
            rel = os.path.relpath(path, TEST_ROOT)[:-len('.java')]
            found.append(rel.replace(os.sep, '/'))
    return sorted(found)


def load_timings():
    timings = {}
    if os.path.exists(TIMINGS):
        with open(TIMINGS, encoding='utf-8') as handle:
            for line in handle:
                line = line.strip()
                if line and not line.startswith('#'):
                    seconds, name = line.split(None, 1)
                    timings[name] = float(seconds)
    return timings


def default_weight(timings):
    if not timings:
        return DEFAULT_WEIGHT
    return max(DEFAULT_WEIGHT, sum(timings.values()) / len(timings))


def assign(count):
    classes = discover()
    timings = load_timings()
    fallback = default_weight(timings)
    weighted = [(timings.get(c, fallback), c) for c in classes]
    known = sorted((w for w in weighted if w[1] in timings), key=lambda w: (-w[0], w[1]))
    unknown = sorted((w for w in weighted if w[1] not in timings), key=lambda w: w[1])
    shards = [[0.0, []] for _ in range(count)]
    for weight, name in known + unknown:
        lightest = min(range(count), key=lambda i: (shards[i][0], i))
        shards[lightest][0] += weight
        shards[lightest][1].append(name)
    return classes, shards


def select(index, count):
    _, shards = assign(count)
    print(','.join(sorted(shards[index][1])))


def verify(count):
    classes, shards = assign(count)
    assigned = [name for _, names in shards for name in names]
    duplicated = sorted({n for n in assigned if assigned.count(n) > 1})
    missing = sorted(set(classes) - set(assigned))
    extra = sorted(set(assigned) - set(classes))
    for i, (weight, names) in enumerate(shards):
        print('shard %d: %d classes, ~%ds' % (i, len(names), round(weight)))
    unlisted = sorted(set(classes) - set(load_timings()))
    print('classes: %d, without timing: %d' % (len(classes), len(unlisted)))
    if duplicated or missing or extra:
        print('duplicated=%s missing=%s extra=%s' % (duplicated, missing, extra))
        sys.exit(1)


def update(logs):
    classes = set(discover())
    totals = {}
    pattern = re.compile(r'Time elapsed: ([0-9.]+) s.* in (\S+)')
    for log in logs:
        with open(log, encoding='utf-8', errors='ignore') as handle:
            for line in handle:
                match = pattern.search(line)
                if match:
                    top = match.group(2).split('$')[0].replace('.', '/')
                    totals[top] = totals.get(top, 0.0) + float(match.group(1))
    os.makedirs(os.path.dirname(TIMINGS), exist_ok=True)
    with open(TIMINGS, 'w', encoding='utf-8') as out:
        for name in sorted(totals):
            if name in classes:
                out.write('%.2f %s\n' % (totals[name], name))


def update_xml(args):
    import glob
    import xml.etree.ElementTree as ET
    out = TIMINGS
    if '--out' in args:
        at = args.index('--out')
        out = args[at + 1]
        args = args[:at] + args[at + 2:]
    classes = set(discover())
    totals = {}
    for directory in args:
        for path in glob.glob(os.path.join(directory, '**', 'TEST-*.xml'), recursive=True):
            try:
                suite = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            top = suite.get('name', '').split('$')[0].replace('.', '/')
            totals[top] = totals.get(top, 0.0) + float(suite.get('time', '0') or 0)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as handle:
        for name in sorted(totals):
            if name in classes:
                handle.write('%.2f %s\n' % (totals[name], name))


def shard_classes(index, count):
    _, shards = assign(count)
    return sorted(shards[index][1])


def list_shard(args):
    count = int(args[1]) if len(args) > 1 else 5
    for name in shard_classes(int(args[0]), count):
        print(name.replace('/', '.'))


def run_shard(args):
    import subprocess
    index = int(args[0])
    count = int(args[1]) if len(args) > 1 else 5
    selection = ','.join(shard_classes(index, count))
    slot = os.path.expanduser('~/.claude/epica18/ci-slot.sh')
    owner = 'shard-%d' % index
    guarded = os.path.exists(slot)
    if guarded:
        subprocess.run(['zsh', slot, 'acquire', owner], check=True)
    try:
        result = subprocess.run(
            ['./mvnw', 'test', '-Dtest=' + selection, '-Dsurefire.failIfNoSpecifiedTests=false'],
            cwd=os.path.join(ROOT, 'backend'))
    finally:
        if guarded:
            subprocess.run(['zsh', slot, 'release', owner])
    sys.exit(result.returncode)


def use(path):
    import shutil
    if os.path.getsize(path) > 0:
        shutil.copyfile(path, TIMINGS)


if __name__ == '__main__':
    command = sys.argv[1]
    if command == 'select':
        select(int(sys.argv[2]), int(sys.argv[3]))
    elif command == 'verify':
        verify(int(sys.argv[2]))
    elif command == 'update-xml':
        update_xml(sys.argv[2:])
    elif command == 'list':
        list_shard(sys.argv[2:])
    elif command == 'run':
        run_shard(sys.argv[2:])
    elif command == 'use':
        use(sys.argv[2])
    elif command == 'update':
        update(sys.argv[2:])
    else:
        sys.exit('comando desconocido: ' + command)
