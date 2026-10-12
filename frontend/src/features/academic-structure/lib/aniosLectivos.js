export const MESES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

export function descripcionMatriculas(template) {
  const primero = MESES[(template.firstEnrollmentFeeMonth ?? 2) - 1];
  if (template.enrollmentFeeCount === 2) {
    return `2 matrículas por año (${primero} y ${MESES[template.secondEnrollmentFeeMonth - 1]})`;
  }
  return `1 matrícula por año (${primero})`;
}
