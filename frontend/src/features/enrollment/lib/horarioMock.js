// MOCK — el campo real SubjectSection.schedule es texto libre y hoy está
// vacío en los datos (verificado contra la base: sin día ni horario
// estructurado), así que no hay nada real contra qué validar superposición
// todavía. Para poder demostrar la UX de "no te podés anotar en 2 comisiones
// que se pisan en día y horario" (pedido explícito del usuario) se genera un
// horario de ejemplo DETERMINÍSTICO por id de comisión — mismo id, mismo
// horario siempre, para que la demo sea estable entre reloads. Cuando exista
// un campo real estructurado (día + hora inicio/fin) en el backend, este
// archivo se reemplaza sin tocar el resto de InscripcionMaterias.jsx.
const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
const BLOQUES = [
  { inicio: 8 * 60, fin: 10 * 60 },
  { inicio: 10 * 60, fin: 12 * 60 },
  { inicio: 14 * 60, fin: 16 * 60 },
  { inicio: 16 * 60, fin: 18 * 60 },
  { inicio: 18 * 60, fin: 20 * 60 },
];

function minutosALabel(minutos) {
  const horas = String(Math.floor(minutos / 60)).padStart(2, '0');
  const mins = String(minutos % 60).padStart(2, '0');
  return `${horas}:${mins}`;
}

export function horarioMockDeComision(sectionId) {
  const dia = DIAS[sectionId % DIAS.length];
  const bloque = BLOQUES[Math.floor(sectionId / DIAS.length) % BLOQUES.length];
  return { dia, inicio: bloque.inicio, fin: bloque.fin, label: `${dia} ${minutosALabel(bloque.inicio)}–${minutosALabel(bloque.fin)}` };
}

export function seSuperponen(horarioA, horarioB) {
  if (horarioA.dia !== horarioB.dia) return false;
  return horarioA.inicio < horarioB.fin && horarioB.inicio < horarioA.fin;
}
