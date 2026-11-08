#!/usr/bin/env python3
import argparse
import re
import subprocess
import sys
from pathlib import Path

BASE = "com.red_escuela.backend"
TEXT_SUFFIXES = {".java", ".txt", ".rules", ".puml", ".xml", ".properties", ".yml", ".yaml", ".js", ".py", ".feature", ".json", ".md", ".factories", ".sh", ".kts", ".gradle"}
SCAN_ROOTS = ["backend/src", "backend/pom.xml", "backend/dev-test", "docs/architecture", ".github/scripts"]
SKIP_DIR_NAMES = {"target", "node_modules", ".git"}
FROZEN_STORE = "backend/src/test/resources/archunit-frozen"
VERIFY_TESTS = "ArchitectureTest,QualityRulesTest,ModulePatternRulesTest,ModularityTest,ViolationSnapshotTest,DocsIndexTest,SourceFileNameMatchesTypeTest,SurefireDiscoversEveryTestClassTest,OpenApiSnapshotTest"


def parse_map(path):
    packages, classes, moves = [], [], []
    for number, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"(package|class|move)\s+(\S+)\s*->\s*(\S+)", line)
        if not match:
            sys.exit(f"{path}:{number}: formato invalido: {raw!r} (esperado: 'package a.b -> a.c', 'class Viejo -> Nuevo' o 'move a.b.Clase -> a.c')")
        kind, old, new = match.groups()
        {"package": packages, "class": classes, "move": moves}[kind].append((old, new))
    return packages, classes, moves


def run(command, cwd, check=True):
    return subprocess.run(command, cwd=cwd, check=check, text=True, capture_output=True)


def scan_files(repo):
    for root in SCAN_ROOTS:
        base = repo / root
        if base.is_file():
            yield base
        elif base.is_dir():
            for path in base.rglob("*"):
                if path.is_file() and path.name != "stored.rules" and path.suffix in TEXT_SUFFIXES and not (set(path.parts) & SKIP_DIR_NAMES):
                    yield path
    frozen = repo / FROZEN_STORE
    if frozen.is_dir():
        for path in frozen.iterdir():
            if path.is_file() and path.suffix == "" and path.name != "stored.rules":
                yield path


def package_regex(old):
    return re.compile(r"(?<![A-Za-z0-9_.])" + re.escape(BASE + "." + old) + r"(?![A-Za-z0-9_])")


def class_regex(old):
    return re.compile(r"(?:(?<![A-Za-z0-9_.])|(?<=[a-z0-9_]\.))" + re.escape(old) + r"(?P<suffix>(?:Test|IT|Tests)?)(?![A-Za-z0-9_])")


def move_regex(old):
    return re.compile(r"(?<![A-Za-z0-9_.])" + re.escape(BASE + "." + old) + r"(?![A-Za-z0-9_])")


def split_move(old, new):
    old_package, _, simple = old.rpartition(".")
    return old_package, simple, new


def rewrite_text(text, packages, classes, moves=()):
    for old, new in moves:
        old_package, simple, new_package = split_move(old, new)
        text = move_regex(old).sub(BASE + "." + new_package + "." + simple, text)
    for old, new in packages:
        text = package_regex(old).sub(BASE + "." + new, text)
    for old, new in classes:
        text = class_regex(old).sub(lambda match, new=new: new + match.group('suffix'), text)
    return text


def frozen_line_count(repo):
    frozen = repo / FROZEN_STORE
    return sum(len(p.read_text(encoding="utf-8", errors="replace").splitlines()) for p in frozen.iterdir() if p.is_file() and p.name != "stored.rules")


def plan_class_moves(repo, class_moves, class_names):
    result = []
    for source_root in ("backend/src/main/java", "backend/src/test/java"):
        for old, new in class_moves:
            old_package, simple, new_package = split_move(old, new)
            old_dir = repo / source_root / BASE.replace(".", "/") / old_package.replace(".", "/")
            target_dir = repo / source_root / BASE.replace(".", "/") / new_package.replace(".", "/")
            if not old_dir.is_dir():
                continue
            for path in old_dir.iterdir():
                if path.is_file() and (path.stem == simple or path.stem in (simple + "Test", simple + "IT", simple + "Tests")):
                    result.append((path, target_dir / path.name, new_package))
    return result


def add_missing_imports(class_moves, original_packages):
    for old, new in class_moves:
        old_package, simple, new_package = split_move(old, new)
        fqn = BASE + "." + new_package + "." + simple
        for path, package in original_packages.items():
            if package != BASE + "." + old_package or not path.exists() or path.suffix != ".java":
                continue
            text = path.read_text(encoding="utf-8")
            declared = re.search(r"^package\s+([\w.]+);", text, flags=re.M)
            if declared and declared.group(1) == BASE + "." + new_package:
                continue
            uses = re.search(r"(?<![A-Za-z0-9_.])" + simple + r"(?![A-Za-z0-9_])", text)
            if uses and ("import " + fqn + ";") not in text and not path.stem.startswith(simple):
                line = "import " + fqn + ";\n"
                if re.search(r"^import ", text, flags=re.M):
                    text = re.sub(r"^import ", lambda _: line + "import ", text, count=1, flags=re.M)
                else:
                    text = re.sub(r"^(package\s+[\w.]+;\n)", lambda match: match.group(1) + "\n" + line, text, count=1, flags=re.M)
                path.write_text(text, encoding="utf-8")


def plan_moves(repo, packages, classes):
    moves = []
    class_map = dict(classes)
    for source_root in ("backend/src/main/java", "backend/src/test/java"):
        for old, new in packages:
            old_dir = repo / source_root / BASE.replace(".", "/") / old.replace(".", "/")
            if not old_dir.is_dir():
                continue
            for path in old_dir.iterdir():
                if not path.is_file():
                    continue
                name = path.stem
                suffix = path.suffix
                for old_class, new_class in class_map.items():
                    if name == old_class or name.startswith(old_class) and name[len(old_class):] in ("Test", "IT", "Tests"):
                        name = new_class + name[len(old_class):]
                        break
                target_dir = repo / source_root / BASE.replace(".", "/") / new.replace(".", "/")
                moves.append((path, target_dir / (name + suffix)))
    planned = {source for source, _ in moves}
    for source_root in ("backend/src/main/java", "backend/src/test/java"):
        for old_class, new_class in classes:
            for path in (repo / source_root).rglob("*.java"):
                name = path.stem
                if path in planned or not (name == old_class or name.startswith(old_class) and name[len(old_class):] in ("Test", "IT", "Tests")):
                    continue
                moves.append((path, path.with_name(new_class + name[len(old_class):] + path.suffix)))
                planned.add(path)
    return moves


def main():
    parser = argparse.ArgumentParser(description="Mueve y renombra paquetes y clases de un modulo reescribiendo codigo, tests, tiendas congeladas y referencias.")
    parser.add_argument("--module", required=True)
    parser.add_argument("--map", required=True, help="archivo con lineas 'package a.b -> a.c' y 'class Viejo -> Nuevo'")
    parser.add_argument("--repo", default=".")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--skip-verify", action="store_true")
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    packages, classes, class_moves = parse_map(args.map)
    for old, _ in packages:
        if not old.startswith(args.module + "."):
            sys.exit(f"el paquete {old} no pertenece al modulo {args.module}")

    moves = plan_moves(repo, packages, classes)
    explicit_plan = plan_class_moves(repo, class_moves, classes)
    explicit = [(source, target) for source, target, _ in explicit_plan]
    explicit_package = {source: package for source, _, package in explicit_plan}
    explicit_sources = set(explicit_package)
    moves = [move for move in moves if move[0] not in explicit_sources] + explicit
    original_packages = {}
    for source_root in ("backend/src/main/java", "backend/src/test/java"):
        for old, _ in class_moves:
            old_dir = repo / source_root / BASE.replace(".", "/") / split_move(old, "")[0].replace(".", "/")
            if old_dir.is_dir():
                for path in old_dir.glob("*.java"):
                    original_packages[path] = BASE + "." + split_move(old, "")[0]
    changed = []
    for path in scan_files(repo):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        rewritten = rewrite_text(text, packages, classes, class_moves)
        if rewritten != text:
            changed.append((path, rewritten))

    before = frozen_line_count(repo)
    print(f"modulo: {args.module}")
    print(f"archivos a mover: {len(moves)}")
    for source, target in moves:
        print(f"  git mv {source.relative_to(repo)} -> {target.relative_to(repo)}")
    print(f"archivos con texto a reescribir: {len(changed)}")
    for path, _ in changed:
        print(f"  rewrite {path.relative_to(repo)}")
    print(f"lineas congeladas antes: {before}")
    if args.dry_run:
        print("dry-run: no se modifico nada")
        return

    for source, target in moves:
        target.parent.mkdir(parents=True, exist_ok=True)
        run(["git", "mv", str(source), str(target)], repo)
    moved = dict(moves)
    to_write = [path for path, _ in changed] + [source for source in explicit_sources if source not in {path for path, _ in changed}]
    for path in to_write:
        actual = moved.get(path, path)
        text = actual.read_text(encoding="utf-8")
        if path in explicit_sources:
            text = re.sub(r"^package\s+[\w.]+;", "package " + BASE + "." + explicit_package[path] + ";", text, count=1, flags=re.M)
        actual.write_text(rewrite_text(text, packages, classes, class_moves), encoding="utf-8")
    renamed_originals = {moved.get(path, path): package for path, package in original_packages.items()}
    add_missing_imports(class_moves, renamed_originals)

    after = frozen_line_count(repo)
    print(f"lineas congeladas despues: {after}")
    if after > before:
        sys.exit(f"ERROR: las tiendas congeladas crecieron ({before} -> {after})")
    if args.skip_verify:
        return

    backend = repo / "backend"
    compile_result = run(["./mvnw", "-q", "-o", "-DskipTests", "compile", "test-compile"], backend, check=False)
    if compile_result.returncode != 0:
        print(compile_result.stdout[-3000:])
        sys.exit("ERROR: no compila")
    print("compila")
    test_result = run(["./mvnw", "-q", "-o", "test", f"-Dtest={VERIFY_TESTS}", "-Dsurefire.failIfNoSpecifiedTests=false",
                       "-Darchunit.freeze.store.default.allowStoreUpdate=false"], backend, check=False)
    if test_result.returncode != 0:
        print((test_result.stdout + test_result.stderr)[-4000:])
        sys.exit("ERROR: fallan ArchUnit/Modulith (revisar lineas congeladas nuevas)")
    status = run(["git", "status", "--porcelain", FROZEN_STORE], repo).stdout
    added = [line for line in status.splitlines() if line.startswith("??") or line.startswith("A")]
    if added:
        sys.exit("ERROR: aparecieron tiendas congeladas nuevas: " + ", ".join(added))
    print("ArchUnit y Modulith sin lineas congeladas nuevas")


if __name__ == "__main__":
    main()
