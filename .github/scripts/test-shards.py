#!/usr/bin/env python3
"""Reparto determinista de las clases de test del backend en shards por tiempo medido.

Uso:
  test-shards.py select <indice> <cantidad>   imprime el valor de -Dtest para el shard
  test-shards.py verify <cantidad>            falla si la union de shards != todas las clases o hay duplicados
  test-shards.py update <log> [<log> ...]     regenera timings.txt desde logs de surefire ("Time elapsed")

Las clases que no figuran en timings.txt reciben un peso por defecto y se reparten
igual, de modo que ninguna queda afuera.
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


def assign(count):
    classes = discover()
    timings = load_timings()
    weighted = [(timings.get(c, DEFAULT_WEIGHT), c) for c in classes]
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


if __name__ == '__main__':
    command = sys.argv[1]
    if command == 'select':
        select(int(sys.argv[2]), int(sys.argv[3]))
    elif command == 'verify':
        verify(int(sys.argv[2]))
    elif command == 'update':
        update(sys.argv[2:])
    else:
        sys.exit('comando desconocido: ' + command)
