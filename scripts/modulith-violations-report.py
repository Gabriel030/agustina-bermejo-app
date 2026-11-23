#!/usr/bin/env python3
import re
import sys
from collections import Counter, defaultdict

LAYERS = ["shared", "identity", "academicstructure", "enrollment", "attendance", "evaluation",
          "billing", "graduation", "announcement", "notification"]
RANK = {module: index for index, module in enumerate(LAYERS)}
TYPES = ["entity", "repository", "service", "exception", "dto", "usecase"]
BASE = "com.red_escuela.backend."

SOURCE = re.compile(r"^Module '(\w+)'")
NON_EXPOSED = re.compile(r"non-exposed type " + re.escape(BASE) + r"(\w+)\.([\w.]+) within module '(\w+)'")
VIA = re.compile(r"via " + re.escape(BASE) + r"(\w+)\.([\w.$]+) -> " + re.escape(BASE) + r"(\w+)\.([\w.$]+)\.")
FILE = re.compile(r"in \((\w+)\.java")
SLICE = re.compile(r"Slice (\w+)")


def kind_of(path):
    for kind in TYPES:
        if kind in path.split("."):
            return kind
    first = path.split(".")[0]
    return first if first[:1].islower() else "other"


def parse(lines):
    violations, cycles = [], []
    for line in lines:
        if line.startswith("Cycle detected"):
            head = line.split("| 1.")[0]
            cycles.append(SLICE.findall(head))
            continue
        source = SOURCE.match(line)
        if not source:
            continue
        module = source.group(1)
        match = NON_EXPOSED.search(line)
        if match:
            target_module, path = match.group(3), match.group(2)
            file = FILE.search(line)
            source_class = file.group(1) if file else "?"
            violations.append((module, source_class, target_module, kind_of(path), path.split(".")[-1]))
            continue
        match = VIA.search(line)
        if match:
            source_class = match.group(2).split(".")[-1].split("$")[0]
            violations.append((module, source_class, match.group(3), kind_of(match.group(4)),
                               match.group(4).split(".")[-1]))
    return violations, cycles


def is_upward(source, target):
    return source in RANK and target in RANK and RANK[source] < RANK[target]


def table(headers, rows):
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    out += ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join(out)


def main(path):
    with open(path, encoding="utf-8") as handle:
        violations, cycles = parse(handle.read().splitlines())
    print("## Totales\n")
    print(f"- Violaciones de módulo (mensajes): {len(violations)}")
    print(f"- Ciclos entre módulos (mensajes Cycle detected): {len(cycles)}")
    print(f"- Clases origen distintas: {len({(v[0], v[1]) for v in violations})}")
    print(f"- Pares (clase origen, tipo destino) distintos: {len({(v[0], v[1], v[2], v[4]) for v in violations})}")
    print(f"- Hacia arriba: {sum(1 for v in violations if is_upward(v[0], v[2]))}")
    print("\n## Por tipo de destino\n")
    by_kind = Counter(v[3] for v in violations)
    print(table(["tipo", "cantidad"], sorted(by_kind.items(), key=lambda item: -item[1])))
    print("\n## Matriz origen -> destino\n")
    matrix = defaultdict(Counter)
    for module, _, target, kind, _ in violations:
        matrix[(module, target)][kind] += 1
    rows = []
    for (module, target), kinds in sorted(matrix.items(), key=lambda item: -sum(item[1].values())):
        detail = ", ".join(f"{kind} {count}" for kind, count in kinds.most_common())
        rows.append([module, target, sum(kinds.values()), "arriba" if is_upward(module, target) else "abajo", detail])
    print(table(["origen", "destino", "cantidad", "sentido", "desglose por tipo"], rows))
    print("\n## Top 20 clases origen\n")
    per_class = Counter((v[0], v[1]) for v in violations)
    print(table(["módulo", "clase", "cantidad"], [[m, c, n] for (m, c), n in per_class.most_common(20)]))
    print("\n## Ciclos por par de módulos\n")
    pairs = Counter()
    for cycle in cycles:
        edges = {tuple(sorted((cycle[i], cycle[i + 1]))) for i in range(len(cycle) - 1)}
        pairs.update(edges)
    print(table(["par", "ciclos que lo atraviesan"], [[f"{a}<->{b}", n] for (a, b), n in pairs.most_common()]))
    print("\n## Dependencias hacia arriba (origen, clase, destino, tipo, cantidad)\n")
    upward = Counter((v[0], v[1], v[2], v[3]) for v in violations if is_upward(v[0], v[2]))
    print(table(["módulo origen", "clase origen", "módulo destino", "tipo destino", "cantidad"],
                [[a, b, c, d, n] for (a, b, c, d), n in sorted(upward.items(), key=lambda item: (item[0][0], item[0][2], -item[1]))]))
    print("\n## Tipos destino hacia arriba (para agrupar procesos)\n")
    types = Counter((v[0], v[2], v[4]) for v in violations if is_upward(v[0], v[2]))
    print(table(["origen", "destino", "tipo", "cantidad"], [[a, b, c, n] for (a, b, c), n in sorted(types.items(), key=lambda item: (item[0][0], item[0][1], -item[1]))]))


if __name__ == "__main__":
    main(sys.argv[1])
