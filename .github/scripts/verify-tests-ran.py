#!/usr/bin/env python3
"""Falla si alguna de las clases pedidas no se ejecuto de verdad (sin reporte, 0 tests, o con tests salteados/fallidos).

Uso: verify-tests-ran.py <dir-de-reportes-surefire> <Clase> [<Clase> ...]
Escribe el detalle en $GITHUB_STEP_SUMMARY si existe y emite ::error:: por cada problema.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def check(reports_dir, classes):
    problems = []
    for name in classes:
        files = glob.glob(os.path.join(reports_dir, 'TEST-*.' + name + '.xml'))
        if not files:
            problems.append(name + ': no hay reporte de surefire, la clase no se ejecuto')
            continue
        root = ET.parse(files[0]).getroot()
        tests = int(root.get('tests', '0'))
        skipped = int(root.get('skipped', '0'))
        failed = int(root.get('failures', '0')) + int(root.get('errors', '0'))
        if tests == 0:
            problems.append(name + ': 0 tests ejecutados')
        elif skipped > 0:
            problems.append(name + ': ' + str(skipped) + ' de ' + str(tests) + ' tests salteados (el nivel nightly no se activo)')
        elif failed > 0:
            problems.append(name + ': ' + str(failed) + ' tests fallaron')
    return problems


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    problems = check(argv[1], argv[2:])
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    for problem in problems:
        print('::error::' + problem)
    if summary:
        with open(summary, 'a', encoding='utf-8') as handle:
            if problems:
                handle.write('### Seed demo: los tests nightly NO se ejecutaron bien\n')
                handle.writelines('- ' + problem + '\n' for problem in problems)
            else:
                handle.write('### Seed demo: ' + ', '.join(argv[2:]) + ' ejecutados sin saltear nada\n')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
