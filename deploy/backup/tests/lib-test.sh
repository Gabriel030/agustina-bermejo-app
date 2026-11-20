#!/usr/bin/env bash
failures=0
checks=0

use_scripts_as_opt_backup() {
  if [ -e /opt/backup ] && [ ! -L /opt/backup ]; then
    echo "/opt/backup existe y no es un symlink: no se pisa" >&2
    exit 2
  fi
  mkdir -p /opt 2> /dev/null || sudo mkdir -p /opt
  ln -sfn "${scripts:?}" /opt/backup 2> /dev/null || sudo ln -sfn "$scripts" /opt/backup
}

check() {
  local name="$1" expected="$2" actual="$3"
  checks=$((checks + 1))
  if [ "$expected" = "$actual" ]; then
    echo "ok   $name"
  else
    failures=$((failures + 1))
    echo "FAIL $name"
    echo "  esperado: $expected"
    echo "  obtenido: $actual"
  fi
}

finish() {
  echo "$checks chequeos, $failures fallas"
  [ "$failures" -eq 0 ]
}
