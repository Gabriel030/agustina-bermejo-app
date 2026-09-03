// Columnas fijas de notas (decisión de producto, Épica 2 Fase 2: "columnas
// fijas" en vez de una grilla con cantidad de columnas variable por materia).
// Un GradeItem que no encaja en ninguna de estas casillas cae en `extras` —
// nunca se pierde, pero tampoco rompe el ancho fijo de la grilla.
//
// Parcial 1-4: EvaluationScheme.examCountExpected distingue cuatrimestral (2)
// de anual (hasta 4, ver Spec - Docente, configuración de evaluación y
// mesas) — la grilla siempre muestra las 4 columnas para que el ancho sea
// uniforme entre materias cuatrimestrales y anuales; las cuatrimestrales
// muestran "—" en Parcial 3/4. TP 1/2/3: EvaluationScheme.tpCountExpected es
// configurable por materia (el docente puede pedir 1, 2 o 3 TPs) — mismo
// criterio. Final 1/2: hasta 2 llamados (Subject.maxFinalExamAttempts) — si
// el primero se desaprueba, el segundo llamado es un GradeItem FINAL con
// sequenceNumber=2, no un reemplazo del primero.
export const COLUMNAS_NOTAS = [
  { key: 'parcial1', label: 'Parcial 1', match: (g) => g.type === 'EXAM' && g.sequenceNumber === 1 },
  { key: 'parcial2', label: 'Parcial 2', match: (g) => g.type === 'EXAM' && g.sequenceNumber === 2 },
  { key: 'parcial3', label: 'Parcial 3', match: (g) => g.type === 'EXAM' && g.sequenceNumber === 3 },
  { key: 'parcial4', label: 'Parcial 4', match: (g) => g.type === 'EXAM' && g.sequenceNumber === 4 },
  { key: 'tp1', label: 'TP 1', match: (g) => g.type === 'TP' && g.sequenceNumber === 1 },
  { key: 'tp2', label: 'TP 2', match: (g) => g.type === 'TP' && g.sequenceNumber === 2 },
  { key: 'tp3', label: 'TP 3', match: (g) => g.type === 'TP' && g.sequenceNumber === 3 },
  { key: 'final1', label: 'Final 1', match: (g) => g.type === 'FINAL' && g.sequenceNumber === 1 },
  { key: 'final2', label: 'Final 2', match: (g) => g.type === 'FINAL' && g.sequenceNumber === 2 },
];

// "Vía" por la que una materia quedó aprobada — espejo del enum de backend
// FinalGradeType (PROMOTION/FINAL_EXAM), mostrado como calificador junto al
// pill "Aprobada" en vez de inventar un estado nuevo (ver SectionEnrollmentResponse.finalGradeType).
export const FINAL_GRADE_TYPE_LABEL = { PROMOTION: 'por promoción', FINAL_EXAM: 'por examen final' };

// Espejo del enum de backend CurricularTerm (FIRST/SECOND/ANNUAL) — compartido por Plan de
// estudio (columna Tipología) y Mesas de examen (para aclarar de qué año/cuatrimestre es la
// materia que se está por rendir).
export const TIPOLOGIA_LABEL = { FIRST: 'Cuatrimestral (1°)', SECOND: 'Cuatrimestral (2°)', ANNUAL: 'Anual' };

// Opciones del filtro de estado (EstadoColumnFilter.jsx, embebido en el header
// de la columna "Estado" de TabMaterias.jsx/TabNotas.jsx).
export const TABS_ESTADO = [
  { id: 'todas', label: 'Todas' },
  { id: 'en-curso', label: 'En curso' },
  { id: 'regulares', label: 'Regulares' },
  { id: 'aprobadas', label: 'Aprobadas' },
  { id: 'bloqueadas', label: 'Bloqueadas' },
];

// Mismo criterio que BADGE_ESTADO en AlumnoMisCarreras.jsx (incluye WAITLISTED:
// una materia en lista de espera todavía cuenta como "en curso" para el alumno,
// no tiene sentido mandarla a "aprobadas" ni esconderla). Vive acá porque
// TabNotas.jsx también lo necesita para el filtro de estado.
//
// REGULAR NO entra acá — pedido explícito del usuario: "regular" significa que
// el alumno YA TERMINÓ de cursar y quedó en condición de rendir el final, no
// que esté cursando ahora mismo. Tratarla como "en curso" es incorrecto (no
// tiene clases a las que asistir, no ocupa un horario — ver InscripcionMaterias,
// horariosComprometidos) y además, cursada != aprobada, así que tampoco puede
// caer en "aprobada". Es su propio estado ('regular', pendiente de rendir).
export const ESTADOS_EN_CURSO = new Set(['IN_PROGRESS', 'WAITLISTED', 'ENROLLED']);

// Cruza una materia del plan de estudio (ProgramSubjectResponse) contra la
// inscripción real del alumno (o su ausencia) para clasificarla en la grilla
// de Notas — decisión de UX (Épica 2 Fase 2): la grilla muestra el plan
// COMPLETO, no solo lo que el alumno ya cursó, para que "bloqueada" tenga
// sentido (una materia que nunca se cursó porque no cumple correlativas).
//
// El motivo de bloqueo es MOCK por ahora (heurística por año curricular, no
// consulta GET /program-subjects/{id}/prerequisites que sí existe en el
// backend) — pedido explícito del usuario: "poné más información mockeada
// para ver cómo queda" antes de cablear el fetch real de correlativas acá.
export function clasificarMateriaDelPlan(programSubject, inscripcion) {
  if (inscripcion?.status === 'APPROVED') {
    return { estado: 'aprobada', inscripcion };
  }
  if (inscripcion?.status === 'REGULAR') {
    return { estado: 'regular', inscripcion };
  }
  if (inscripcion && ESTADOS_EN_CURSO.has(inscripcion.status)) {
    return { estado: 'en-curso', inscripcion };
  }
  const anio = programSubject.curricularYear ?? 1;
  if (anio > 1) {
    return { estado: 'bloqueada', motivoBloqueo: `Requiere materias de ${anio - 1}° año` };
  }
  return { estado: 'disponible' };
}

// Orden/agrupamiento por default de la grilla de materias (TabMaterias.jsx y
// TabNotas.jsx): agrupado por año curricular (1° año primero), y DENTRO de cada
// año, en curso primero (qué estoy haciendo ahora), después aprobadas (qué ya
// logré), después disponibles, bloqueadas al final — pedido explícito del
// usuario ("ordenamiento agrupado en año"). Las filas sin año (curricularYear
// null) van al final, agrupadas aparte.
export const ORDEN_ESTADO = { 'en-curso': 0, regular: 1, aprobada: 2, disponible: 3, bloqueada: 4 };

export function ordenarFilasDeMaterias(a, b) {
  const porAnio = (a.curricularYear ?? Infinity) - (b.curricularYear ?? Infinity);
  if (porAnio !== 0) return porAnio;
  const porEstado = ORDEN_ESTADO[a.estado] - ORDEN_ESTADO[b.estado];
  if (porEstado !== 0) return porEstado;
  return a.subjectName.localeCompare(b.subjectName);
}

// Años curriculares presentes en las filas, para poblar el filtro de la
// columna "Año" — únicos, ordenados, sin los "sin año" (esos quedan fuera del
// filtro por año, se ven solo con "Todos").
export function aniosDisponibles(filas) {
  return [...new Set(filas.map((f) => f.curricularYear).filter((anio) => anio != null))].sort((a, b) => a - b);
}

export function mapearNotasAColumnas(gradeItems) {
  const items = gradeItems ?? [];
  const asignados = new Set();
  const columnas = {};
  for (const columna of COLUMNAS_NOTAS) {
    const item = items.find((g) => columna.match(g) && !asignados.has(g.id));
    columnas[columna.key] = item ? item.score : null;
    if (item) asignados.add(item.id);
  }
  const extras = items.filter((g) => !asignados.has(g.id));
  return { columnas, extras };
}
