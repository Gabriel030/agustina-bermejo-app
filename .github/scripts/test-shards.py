#!/usr/bin/env python3
"""Reparto determinista de las clases de test del backend en shards por tiempo medido.

Uso:
  test-shards.py select <indice> <cantidad>   imprime el valor de -Dtest para el shard
  test-shards.py verify <cantidad>            falla si la union de shards != todas las clases o hay duplicados
  test-shards.py update <log> [<log> ...]     regenera timings.txt desde logs de surefire ("Time elapsed")
  test-shards.py update-xml <dir> [<dir> ...] [--out archivo]
                                              regenera timings.txt desde TEST-*.xml de surefire (busca recursivo)
  test-shards.py list <indice> [<cantidad>]   lista las clases del shard (cantidad por defecto 8, como la CI)
  test-shards.py run <indice> [<cantidad>]    corre el shard en local con el semaforo (backend/: ./mvnw test -Dtest=...)
  test-shards.py accumulate <dir> [<dir> ...] --out <dir> [--history <dir>]
                                              suma las medidas de esta corrida (TEST-*.xml) al historial de las ultimas
                                              WINDOW corridas y escribe samples.json + timings.txt (promedio por clase)
  test-shards.py use <dir>                    copia el promedio de <dir>/samples.json como timings.txt si hay al menos
                                              MIN_HISTORY corridas; si no, se queda con el timings.txt versionado

Las clases que no figuran en timings.txt reciben como peso el promedio de las medidas
(minimo 1 s) y se reparten igual, de modo que ninguna queda afuera.
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEST_ROOT = os.path.join(ROOT, 'backend', 'src', 'test', 'java')
TIMINGS = os.path.join(ROOT, '.github', 'test-shards', 'timings.txt')
DEFAULT_WEIGHT = 1.0
WINDOW = 8
MIN_HISTORY = 3
NAME = re.compile(r'(^Test.*|.*Test|.*Tests|.*TestCase|.*IT|.*Properties)\.java$')
ABSTRACT = re.compile(r'^\s*(public\s+)?abstract\s+class\s', re.M)
CONTEXT_COST = 12.0
SPRING_TEST = re.compile(r'@SpringBootTest\b')
CLASS_DECL = re.compile(r'^(?:public\s+)?(?:abstract\s+)?(?:final\s+)?class\s+(\w+)(?:\s+extends\s+(\w+))?', re.M)
CONTEXT_ANNOTATION = re.compile(
    r'^@(SpringBootTest|ActiveProfiles|TestPropertySource|Import|DirtiesContext|ContextConfiguration|'
    r'AutoConfigure\w+|SharedMockBeans|SharedSpyBeans|SharedPropertyOverrides|TestConfiguration|MockitoBean|MockitoSpyBean)\b'
    r'(\((?:[^()]|\([^()]*\))*\))?', re.M | re.S)
FIELD_OVERRIDE = re.compile(
    r'@(MockitoBean|MockitoSpyBean)(?:\([^)]*\))?\s+(?:private\s+|protected\s+|public\s+)?(?:final\s+)?([\w<>.,? ]+?)\s+\w+\s*;')


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


def read_sources():
    sources = {}
    for base, _, files in os.walk(TEST_ROOT):
        for name in files:
            if name.endswith('.java'):
                with open(os.path.join(base, name), encoding='utf-8') as handle:
                    sources[name[:-len('.java')]] = handle.read()
    return sources


def own_context_parts(source):
    head = source[:CLASS_DECL.search(source).start()] if CLASS_DECL.search(source) else source
    parts = [re.sub(r'\s+', ' ', m.group(0)) for m in CONTEXT_ANNOTATION.finditer(head)]
    parts += ['%s:%s' % (m.group(1), re.sub(r'\s+', ' ', m.group(2))) for m in FIELD_OVERRIDE.finditer(source)]
    return parts


def context_signature(simple_name, sources, seen=()):
    source = sources.get(simple_name)
    if source is None or simple_name in seen:
        return None, False
    parts = own_context_parts(source)
    spring = bool(SPRING_TEST.search(source))
    declaration = CLASS_DECL.search(source)
    if declaration and declaration.group(2):
        parent_parts, parent_spring = context_signature(declaration.group(2), sources, seen + (simple_name,))
        if parent_parts is not None:
            parts = parent_parts + parts
        spring = spring or parent_spring
    return parts, spring


def context_of(name, sources):
    parts, spring = context_signature(name.rsplit('/', 1)[-1], sources)
    if not spring:
        return None
    return '|'.join(sorted(set(parts or [])))


def parse_timings(path):
    timings = {}
    with open(path, encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if line and not line.startswith('#'):
                seconds, name = line.split(None, 1)
                timings[name] = float(seconds)
    return timings


def load_timings():
    return parse_timings(TIMINGS) if os.path.exists(TIMINGS) else {}


def default_weight(timings):
    if not timings:
        return DEFAULT_WEIGHT
    return max(DEFAULT_WEIGHT, sum(timings.values()) / len(timings))


def assign(count, contexts=None):
    classes = discover()
    timings = load_timings()
    fallback = default_weight(timings)
    if contexts is None:
        sources = read_sources()
        contexts = {c: context_of(c, sources) for c in classes}
    weight = {c: timings.get(c, fallback) for c in classes}
    census = {}
    for c in classes:
        if contexts.get(c) is not None:
            census[contexts[c]] = census.get(contexts[c], 0) + 1
    common = max(census, key=lambda k: (census[k], k)) if census else None
    groups = {}
    singles = []
    for c in classes:
        context = contexts.get(c)
        if context is None or context == common:
            singles.append(c)
        else:
            groups.setdefault(context, []).append(c)
    units = [(sum(weight[c] for c in members) + CONTEXT_COST, sorted(members)) for members in groups.values()]
    units.sort(key=lambda u: (-u[0], u[1][0]))
    known = sorted((c for c in singles if c in timings), key=lambda c: (-weight[c], c))
    unknown = sorted(c for c in singles if c not in timings)
    shards = [[0.0, []] for _ in range(count)]
    for unit_weight, members in units:
        lightest = min(range(count), key=lambda i: (shards[i][0], i))
        shards[lightest][0] += unit_weight
        shards[lightest][1].extend(members)
    for c in known + unknown:
        lightest = min(range(count), key=lambda i: (shards[i][0], i))
        shards[lightest][0] += weight[c]
        shards[lightest][1].append(c)
    return classes, shards


def context_starts(names, contexts):
    return len({contexts[n] for n in names if contexts.get(n) is not None})


def select(index, count):
    print(','.join(shard_classes(index, count)))


def verify(count):
    sources = read_sources()
    classes = discover()
    contexts = {c: context_of(c, sources) for c in classes}
    classes, shards = assign(count, contexts)
    assigned = [name for _, names in shards for name in names]
    duplicated = sorted({n for n in assigned if assigned.count(n) > 1})
    missing = sorted(set(classes) - set(assigned))
    extra = sorted(set(assigned) - set(classes))
    for i, (weight, names) in enumerate(shards):
        print('shard %d: %d classes, ~%ds, %d spring contexts' % (i, len(names), round(weight), context_starts(names, contexts)))
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


def body_seconds(suite):
    cases = [float(case.get('time', '0') or 0) for case in suite.iter('testcase')]
    return sum(cases) if cases else float(suite.get('time', '0') or 0)


def measure_xml(directories):
    import glob
    import xml.etree.ElementTree as ET
    totals = {}
    for directory in directories:
        for path in glob.glob(os.path.join(directory, '**', 'TEST-*.xml'), recursive=True):
            try:
                suite = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            top = suite.get('name', '').split('$')[0].replace('.', '/')
            totals[top] = totals.get(top, 0.0) + body_seconds(suite)
    return totals


def write_timings(path, timings):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        for name in sorted(timings):
            handle.write('%.2f %s\n' % (timings[name], name))


def update_xml(args):
    out = TIMINGS
    if '--out' in args:
        at = args.index('--out')
        out = args[at + 1]
        args = args[:at] + args[at + 2:]
    classes = set(discover())
    totals = measure_xml(args)
    write_timings(out, {name: totals[name] for name in totals if name in classes})


def averages(samples):
    return {name: sum(values) / len(values) for name, values in samples.items() if values}


def parse_history(path):
    try:
        with open(path, encoding='utf-8') as handle:
            data = json.load(handle)
        runs = data['runs']
        samples = data['samples']
        if isinstance(runs, bool) or not isinstance(runs, int) or not isinstance(samples, dict) or not samples:
            raise ValueError('unexpected shape')
        for values in samples.values():
            if not isinstance(values, list) or not values:
                raise ValueError('unexpected shape')
            for value in values:
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    raise ValueError('unexpected shape')
    except (OSError, ValueError, KeyError, TypeError) as error:
        print('history ignored (%s): %s' % (path, error))
        return None
    return data


def read_history(directory):
    if directory:
        history = os.path.join(directory, 'samples.json')
        if os.path.exists(history):
            data = parse_history(history)
            if data is not None:
                return data
        else:
            legacy = os.path.join(directory, 'timings.txt')
            try:
                if os.path.exists(legacy) and os.path.getsize(legacy) > 0:
                    return {'runs': 1, 'samples': {k: [v] for k, v in parse_timings(legacy).items()}}
            except (OSError, ValueError) as error:
                print('legacy timings ignored (%s): %s' % (legacy, error))
    return {'runs': 1, 'samples': {k: [v] for k, v in load_timings().items()}}


def merge_run(history, measured, classes, window=WINDOW):
    samples = {}
    for name in classes:
        values = list(history.get('samples', {}).get(name, []))
        if name in measured:
            values.append(measured[name])
        if values:
            samples[name] = [round(v, 2) for v in values[-window:]]
    return {'runs': min(window, history.get('runs', 0) + 1), 'samples': samples}


def accumulate(args):
    at = args.index('--out')
    out = args[at + 1]
    args = args[:at] + args[at + 2:]
    history_dir = None
    if '--history' in args:
        at = args.index('--history')
        history_dir = args[at + 1]
        args = args[:at] + args[at + 2:]
    merged = merge_run(read_history(history_dir), measure_xml(args), set(discover()))
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, 'samples.json'), 'w', encoding='utf-8') as handle:
        json.dump(merged, handle, sort_keys=True)
    write_timings(os.path.join(out, 'timings.txt'), averages(merged['samples']))
    print('history: %d run(s), %d classes' % (merged['runs'], len(merged['samples'])))


def shard_classes(index, count):
    classes, shards = assign(count)
    return sorted(shards[index][1])


def list_shard(args):
    count = int(args[1]) if len(args) > 1 else 8
    for name in shard_classes(int(args[0]), count):
        print(name.replace('/', '.'))


def run_shard(args):
    import subprocess
    index = int(args[0])
    count = int(args[1]) if len(args) > 1 else 8
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


def use(directory):
    history = os.path.join(directory, 'samples.json')
    if not os.path.exists(history):
        print('baseline ignored: it has no run history, the versioned timings.txt is used')
        return
    data = parse_history(history)
    if data is None:
        print('baseline ignored: corrupt or unexpected history, the versioned timings.txt is used')
        return
    runs = data['runs']
    if runs < MIN_HISTORY:
        print('baseline ignored: %d run(s) of history, %d needed, the versioned timings.txt is used' % (runs, MIN_HISTORY))
        return
    write_timings(TIMINGS, averages(data['samples']))
    print('baseline used: average of the last %d run(s)' % runs)


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
    elif command == 'accumulate':
        accumulate(sys.argv[2:])
    elif command == 'use':
        use(sys.argv[2])
    elif command == 'update':
        update(sys.argv[2:])
    else:
        sys.exit('comando desconocido: ' + command)
