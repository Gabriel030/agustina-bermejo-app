# Exclusiones de mutation testing (PIT)

Lista versionada de los mutantes equivalentes excluidos del perfil Maven `mutation` (D-172). Umbral: 90 % de mutation score en los paquetes de dominio puro. Cada exclusión se revisa en el PR; nunca se baja el umbral. Un mutante sobreviviente que no es equivalente se mata con un test, no se excluye.

| Clase | Línea | Operador | Motivo de equivalencia |
|---|---|---|---|

Sin exclusiones al 2026-10-06: el perfil `mutation` mata el 100 % de los mutantes generados (97 de 97) en `shared.domain..` y `shared.domain.policy..`.
