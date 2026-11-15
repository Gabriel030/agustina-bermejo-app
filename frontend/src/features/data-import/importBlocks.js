export const IMPORT_BLOCKS = [
  { entityType: 'PERSONS', label: 'Personas', description: 'Alumnos y docentes. El mail es obligatorio y no puede repetirse.' },
  { entityType: 'PROGRAMS', label: 'Carreras', description: 'Carreras con su precio. Son estructura: se conservan si deshacés la importación.' },
  { entityType: 'SUBJECTS', label: 'Materias', description: 'Materias del instituto. Son estructura: se conservan si deshacés la importación.' },
  { entityType: 'CURRICULUM', label: 'Plan de estudios', description: 'Qué materias tiene cada carrera. Es estructura: se conserva si deshacés la importación.' },
  { entityType: 'PREREQUISITES', label: 'Correlativas', description: 'Qué materias hay que aprobar antes de cursar otra. Es estructura: se conserva si deshacés la importación.' },
  { entityType: 'TERMS', label: 'Períodos lectivos', description: 'Años y cuatrimestres con sus fechas.' },
  { entityType: 'SECTIONS', label: 'Comisiones', description: 'Comisiones de cada período, con su materia y su docente.' },
  { entityType: 'PROGRAM_ENROLLMENTS', label: 'Inscripciones a carrera', description: 'Qué alumno está inscripto en qué carrera.' },
  { entityType: 'SECTION_ENROLLMENTS', label: 'Cursadas', description: 'Quién cursó qué comisión, con la condición final.' },
  { entityType: 'GRADE_ITEMS', label: 'Notas por examen y parcial', description: 'Las notas de cada examen y parcial de las cursadas.' },
  { entityType: 'ATTENDANCES', label: 'Asistencia por clase', description: 'La asistencia de cada alumno en cada clase.' },
  { entityType: 'GRADUATIONS', label: 'Egresos', description: 'Quién egresó de qué carrera, con su fecha y número de registro. La inscripción debe estar cargada como completada.' },
];

export function blockLabel(entityType) {
  return IMPORT_BLOCKS.find((block) => block.entityType === entityType)?.label ?? entityType;
}
