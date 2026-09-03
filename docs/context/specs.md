# Specs de producto y roadmap — dónde viven

Todo lo que es **producto** (qué pantallas hay, qué flujo sigue una feature, qué
épica bloquea a cuál, criterios de aceptación) vive en Obsidian, no en este repo:

```
~/Documentos No SYNC/Obsidian-vault/agustina-bermejo-app/
  Roadmap backend/
    Indice.md                                    ← backend ya implementado, Fases 1-4
    Spec - JWT y login.md                          ← Fase 1
    Fase N - <nombre>.md                           ← índice de cada fase (2, 3, 4)
    <nombre de dominio>/                           ← Gestión de personas, Estructura
      Spec [funcional|técnica] - <nombre>.md          académica, Inscripciones académicas,
                                                       Evaluación y asistencia, Procesos
                                                       administrativos y financieros
  Roadmap frontend/
    Indice.md                                    ← punto de entrada del roadmap, nada
                                                     implementado todavía
    Documento maestro - Stack y sistema de diseño.md
    Deuda técnica y gaps de QA.md                 ← gaps reales encontrados en el
                                                     backfill de QA, transversal a los
                                                     dos roadmaps
    Diseño UI - Vidrio Líquido/
      Diseño UI - Vidrio Líquido.md                ← sistema de diseño, tokens, reglas
      Pantallas/*.md                                ← una nota por mockup, con preview
    Épica N - <nombre>/
      Épica N - <nombre>.md                        ← índice de la épica
      Fase M - <nombre>/
        Spec - <nombre>.md                         ← spec técnica ejecutable
```

**Backend vs. frontend, no confundir**: `Roadmap backend/` documenta lo que
ya está implementado (referencia, no lista de tareas) — cada spec ahí tiene
además una sección "Estado de QA (backfill)" con la cobertura de Karate/
Bruno y links a la deuda técnica relacionada. `Roadmap frontend/` es diseño
de producto, nada implementado.

**Antes de implementar una fase**, leer su `Spec - <nombre>.md` completa — trae
contexto verificado, decisiones tomadas con motivo, alcance explícito (qué entra
y qué no) y checklist de verificación. Para escribir una spec nueva (una épica o
fase que todavía no tiene su carpeta), usar la skill `spec-writing` en vez de
crear el archivo a mano — la skill sabe crear la carpeta correcta, validar si ya
existe un proyecto padre en el vault, y exige las secciones obligatorias
(criterios de aceptación, observabilidad, tests, patrones de implementación).

Si una spec vieja de Obsidian contradice el código real del repo, el código gana
— avisar al usuario de la discrepancia en vez de implementar a ciegas lo que dice
la spec (ya pasó una vez: una spec asumía que `GET /me` existía y no era así).
