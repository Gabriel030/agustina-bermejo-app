#!/usr/bin/env bash
[ "$(uname)" = "Linux" ] || exit 0

TOOLCHAINS="$HOME/.m2/toolchains.xml"

if [ ! -f "$TOOLCHAINS" ]; then
  JDK_HOME=""
  for candidate in /usr/lib/jvm/*21*; do
    if [ -d "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      JDK_HOME="$candidate"
      break
    fi
  done
  if [ -n "$JDK_HOME" ]; then
    mkdir -p "$HOME/.m2" 2>/dev/null
    cat > "$TOOLCHAINS" 2>/dev/null <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
    <toolchain>
        <type>jdk</type>
        <provides>
            <version>21</version>
        </provides>
        <configuration>
            <jdkHome>$JDK_HOME</jdkHome>
        </configuration>
    </toolchain>
</toolchains>
EOF
    echo "bootstrap: toolchains.xml creado con $JDK_HOME" >&2
  else
    echo "bootstrap: JDK 21 no encontrado en /usr/lib/jvm" >&2
  fi
fi

if command -v docker >/dev/null 2>&1; then
  if ! docker info >/dev/null 2>&1; then
    echo "bootstrap: iniciando dockerd" >&2
    (sudo dockerd > /tmp/dockerd.log 2>&1 &) 2>/dev/null
    waited=0
    while [ "$waited" -lt 30 ]; do
      if docker info >/dev/null 2>&1; then
        echo "bootstrap: docker listo (${waited}s)" >&2
        break
      fi
      sleep 1
      waited=$((waited + 1))
    done
    if ! docker info >/dev/null 2>&1; then
      echo "bootstrap: docker no respondió en 30s (ver /tmp/dockerd.log)" >&2
    fi
  fi
else
  echo "bootstrap: docker no instalado" >&2
fi

exit 0
