#!/bin/bash
# Harness init — verifica entorno antes de arrancar una feature.
# Correlo desde la raíz del proyecto: ./.claude/harness/scripts/init.sh

set -e
PASS=0
FAIL=0

check() {
  local label="$1"
  local cmd="$2"
  if eval "$cmd" > /dev/null 2>&1; then
    echo "✅ $label"
    PASS=$((PASS+1))
  else
    echo "❌ $label"
    FAIL=$((FAIL+1))
  fi
}

warn() {
  echo "⚠️  $1"
}

echo "=== Harness init check ==="

# Docker (Testcontainers lo necesita para tests de backend)
check "Docker running" "docker info"

# Backend compile
check "Backend compiles" "cd backend && ./mvnw compile -q 2>/dev/null"

# Último número de migración Flyway (informativo)
LAST_MIGRATION=$(ls backend/src/main/resources/db/migration/V*.sql 2>/dev/null | sort -V | tail -1)
if [ -n "$LAST_MIGRATION" ]; then
  echo "✅ Última migración: $(basename $LAST_MIGRATION)"
  PASS=$((PASS+1))
else
  warn "No se encontraron migraciones Flyway"
fi

# Frontend deps
check "Frontend deps instaladas" "[ -d frontend/node_modules ]"

# Frontend build rápido (lint)
check "Frontend lint" "cd frontend && npm run lint --silent 2>/dev/null"

# Working tree
DIRTY=$(git status --short 2>/dev/null | grep -v "^??")
if [ -z "$DIRTY" ]; then
  echo "✅ Working tree limpio"
  PASS=$((PASS+1))
else
  warn "Hay cambios sin commitear — considerá hacer stash antes de arrancar"
  git status --short | grep -v "^??" | head -10
fi

# Rama actual
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ "$BRANCH" = "develop" ] || [ "$BRANCH" = "master" ]; then
  echo "❌ Estás parado en '$BRANCH' — creá una rama feature/ antes de tocar código"
  FAIL=$((FAIL+1))
else
  echo "✅ Rama: $BRANCH"
  PASS=$((PASS+1))
fi

echo ""
echo "=== Resultado: $PASS ok, $FAIL errores ==="

if [ "$FAIL" -gt 0 ]; then
  echo "Resolvé los errores antes de arrancar."
  exit 1
fi

echo "Entorno listo para trabajar."
