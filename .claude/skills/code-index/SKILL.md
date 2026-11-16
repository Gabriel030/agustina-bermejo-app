---
name: code-index
description: Usar el índice de código (MCP codebase-memory, proyecto agustina-develop) para ubicar símbolos, llamadores, impacto de un cambio y esqueletos de archivos sin leer módulos enteros. Solo en la Mac de Gabriel. Trigger "quién llama a", "impacto", "dónde se usa", "outline".
---

# code-index

Índice del grafo de código de `origin/develop`, proyecto `agustina-develop`, servido por el MCP local `codebase-memory` (`mcp__codebase-memory__*`). Apunta a un worktree fijo `<repo-principal>/.claude/worktrees/index-develop` que `scripts/update-code-index.sh` deja en `origin/develop` (lo corre el hook `SessionStart` y el líder tras cada `gh pr merge`); el índice se refresca solo cuando cambian los archivos de esa carpeta. Existe solo en la Mac de Gabriel.

## Antes de confiar
1. `index_status` del proyecto `agustina-develop`: debe estar listo y reciente.
2. Refleja develop, no la rama del worker: los cambios propios se miran con `git diff`.
3. Si el MCP no está disponible , usar grep/Glob.

## Cuándo usarlo
- Quién llama a X / qué llama X: `trace_path` (direcciones inbound/outbound).
- Buscar un símbolo (clase, método, componente): `search_graph`.
- Esqueleto de un archivo sin leerlo entero: `get_file_outline`; el código de un símbolo: `get_code_snippet`.
- Impacto de un cambio antes de tocarlo: `trace_path` inbound sobre el símbolo, más `detect_changes` para medir un diff.
- Preguntas multi-salto (ej. controllers que terminan en un repositorio de otro módulo): `query_graph`.

## Cuándo NO
- Texto literal (mensajes, códigos de error, D-xxx, strings de SQL): grep.
- Migraciones SQL: se parsean mal; leer el archivo.
- Estado de la propia rama: `git diff` / `git status`.

## Ejemplos
- `search_graph` con project `agustina-develop` y name_pattern `CreateSection.*` para ubicar el use case.
- `trace_path` con function_name `createSection`, direction `inbound` para ver llamadores antes de cambiar su firma.
- `get_file_outline` sobre el service para ver métodos sin leer 500 líneas.
