---
name: cell-design
description: Del ticket al mockup aprobado (solo líder técnico). Fase de diseño de los tickets tipo B (cambia una pantalla) y C (pantalla nueva): mockup con componentes reales, ux-advisor, Artifact y OK de Gabriel. Usar cuando el plan marque tipo B o C, o "cell-design", "armá el mockup".
---

# cell-design (solo líder técnico)

Entrada: plan en `docs/cell/plans/<issue>.md` con tipo B o C. Salida: mockup aprobado por Gabriel, con link al Artifact y estado del OK anotados en la sección Diseño del plan. Tipo A no pasa por acá.

## Pasos
1. Protocolo previo de `docs/context/frontend.md` (catálogo `src/components/` y `ui/`, 1-2 pantallas hermanas en `src/features/*/pages/`, tokens) y `docs/context/components.md`. Del ticket sacá: quién usa la pantalla, qué decide en ella y qué datos necesita.
2. Armar el mockup con componentes reales de la app en el playground:
   - Entradas previstas: `frontend/mockups.html`, `frontend/vite.mockups.config.js` y `frontend/src/mockups/` (una carpeta por ticket).
   - Estado verificado en `origin/develop` el 24-nov: NO existen. Primera vez: prepararlos en un PR propio y chico (HTML de entrada que monte `src/mockups/<ticket>/`, config de Vite con alias `@` y los tokens de dirección B, script `npm run mockups`) y mergearlo antes de usar esta skill; hasta entonces, Artifact armado con la skill `mockup-direccion-b` (skill local, fuera del repo) o, si no está disponible, HTML a mano con los valores de `frontend/src/theme/tokens.css`. No usar sistemas de diseño anteriores (Vidrio Líquido).
   - Dirección B (`frontend/src/theme/tokens.css`, único diseño): colores, sombras y radios solo por tokens; nada de hex ni `oklch(...)`.
3. El mockup incluye SIEMPRE: estados cargando, vacío, error y sin permiso; ancho móvil; claro y oscuro.
4. Reglas de UI: poco texto (cada card una pregunta, título de 2-4 palabras, dato grande, ícono; explicaciones en tooltip); pantallas de cambio en 2 pasos (Cambios y Confirmar con tabla Hoy / Nuevo / Diferencia); montos con la tipografía del cuerpo + `tabular-nums`, nunca `font-mono`, 2 decimales; tablas densas con TanStack Table v8; gráficos con Recharts, animados y con "Ver como tabla".
5. Revisión con el subagente `ux-advisor` antes de mostrar (obligatoria en tipo C; en B si el cambio es más que cosmético). Incorporar hallazgos o explicar por qué se descartan; repetir tras cada iteración importante.
6. Publicar como Artifact (nunca un `.html` suelto) y pedir el OK a Gabriel con `AskUserQuestion`: opciones Aprobado / Aprobado con cambios (cuáles) / Rehacer, con link al Artifact y la recomendación del líder. Un solo cuestionario por ronda.
7. Registrar en el plan (sección Diseño): link del Artifact, veredicto de `ux-advisor` y estado del OK con fecha.

## Gate
Sin OK explícito de Gabriel no arrancan el contrato (`cell-contract`) ni el frontend. El backend que no depende de la pantalla (dominio, migraciones, reglas, tests) puede arrancar antes del OK si el plan lo declara.
