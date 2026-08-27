# Specs de producto y roadmap — dónde viven

Todo lo que es **producto** (qué pantallas hay, qué flujo sigue una feature, qué
épica bloquea a cuál, criterios de aceptación) vive en Obsidian, no en este repo:

```
~/Documentos No SYNC/Obsidian-vault/agustina-bermejo-app/
  Roadmap frontend/
    Indice.md                                    ← punto de entrada del roadmap
    Documento maestro - Stack y sistema de diseño.md
    Épica N - <nombre>/
      Épica N - <nombre>.md                        ← índice de la épica
      Fase M - <nombre>/
        Spec - <nombre>.md                         ← spec técnica ejecutable
  Diseño UI - Vidrio Líquido/
    Diseño UI - Vidrio Líquido.md                  ← sistema de diseño, tokens, reglas
    Pantallas/*.md                                  ← una nota por mockup, con preview embebido
```

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
