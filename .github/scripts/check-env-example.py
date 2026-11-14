#!/usr/bin/env python3
import glob
import os
import re
import sys

VAR = r'[A-Z][A-Z0-9_]+'
BUILTIN_VITE = {'DEV', 'PROD', 'MODE', 'BASE_URL', 'SSR', 'VITE_APP_VERSION'}


def read(path):
    with open(path, encoding='utf-8') as handle:
        return handle.read()


def backend_variables(root):
    found = set()
    for path in glob.glob(os.path.join(root, 'backend/src/main/resources/application*.properties')):
        found.update(re.findall(r'\$\{(' + VAR + r')[:}]', read(path)))
    return found


def frontend_variables(root):
    found = set()
    for base, _, files in os.walk(os.path.join(root, 'frontend/src')):
        for name in files:
            if not name.endswith(('.js', '.jsx')) or '.test.' in name:
                continue
            found.update(re.findall(r'import\.meta\.env\.(VITE_[A-Z0-9_]+)', read(os.path.join(base, name))))
    return found - BUILTIN_VITE


def compose_variables(root):
    return set(re.findall(r'\$\{(' + VAR + r')[:}?-]', read(os.path.join(root, 'docker-compose.prod.yml'))))


def documented_variables(root):
    text = read(os.path.join(root, 'deploy/.env.example'))
    return set(re.findall(r'^#?\s?(' + VAR + r')=', text, re.MULTILINE))


def check(root):
    expected = backend_variables(root) | frontend_variables(root) | compose_variables(root)
    documented = documented_variables(root)
    missing = sorted(expected - documented)
    stale = sorted(documented - expected)
    return missing, stale


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    missing, stale = check(root)
    for name in missing:
        print(f'deploy/.env.example: falta documentar {name}')
    for name in stale:
        print(f'deploy/.env.example: {name} ya no lo lee ningun archivo (application*.properties, front o compose)')
    if missing or stale:
        return 1
    print('deploy/.env.example esta al dia')
    return 0


if __name__ == '__main__':
    sys.exit(main())
