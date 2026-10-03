const DIAS_SEMANA = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
const NOMBRES_MES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

function aClaveDia(fechaIso) {
  return fechaIso ? fechaIso.slice(0, 10) : null;
}

function aClaveMes(fechaIso) {
  return fechaIso ? fechaIso.slice(0, 7) : null;
}

export function construirEventosDelCalendario({ termStartDate, termEndDate, tp1DueDate, tp2DueDate, tp3DueDate,
  exam1Date, exam2Date, exam3Date, exam4Date, finalCall1Date, finalCall2Date }) {
  const eventos = [];
  if (termStartDate) eventos.push({ fecha: aClaveDia(termStartDate), tono: 'success', etiqueta: 'Apertura' });
  if (termEndDate) eventos.push({ fecha: aClaveDia(termEndDate), tono: 'success', etiqueta: 'Cierre' });
  [tp1DueDate, tp2DueDate, tp3DueDate].forEach((fecha, i) => {
    if (fecha) eventos.push({ fecha: aClaveDia(fecha), tono: 'warning', etiqueta: `TP${i + 1}` });
  });
  [exam1Date, exam2Date, exam3Date, exam4Date].forEach((fecha, i) => {
    if (fecha) eventos.push({ fecha: aClaveDia(fecha), tono: 'accent', etiqueta: `Parcial ${i + 1}` });
  });
  [finalCall1Date, finalCall2Date].forEach((fecha, i) => {
    if (fecha) eventos.push({ fecha: aClaveDia(fecha), tono: 'accent2', etiqueta: `Llamado ${i + 1}` });
  });
  return eventos;
}

export function mesesAMostrar(eventos) {
  const claves = [...new Set(eventos.map((e) => aClaveMes(e.fecha)))].sort();
  return claves.map((clave) => {
    const [anio, mes] = clave.split('-').map(Number);
    return { anio, mes: mes - 1, clave };
  });
}

export function mesesDelRango(fechaInicioIso, fechaFinIso) {
  if (!fechaInicioIso || !fechaFinIso) return [];
  const inicio = new Date(aClaveDia(fechaInicioIso));
  const fin = new Date(aClaveDia(fechaFinIso));
  const meses = [];
  let cursor = new Date(inicio.getFullYear(), inicio.getMonth(), 1);
  const limite = new Date(fin.getFullYear(), fin.getMonth(), 1);
  while (cursor <= limite) {
    const anio = cursor.getFullYear();
    const mes = cursor.getMonth();
    meses.push({ anio, mes, clave: `${anio}-${String(mes + 1).padStart(2, '0')}` });
    cursor = new Date(anio, mes + 1, 1);
  }
  return meses;
}

export function indiceDelMesActual(meses) {
  const hoy = new Date();
  const claveHoy = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`;
  const indice = meses.findIndex((m) => m.clave === claveHoy);
  return indice === -1 ? 0 : indice;
}

export function construirGrillaDeMes(anio, mes) {
  const primerDia = new Date(anio, mes, 1);
  const cantidadDias = new Date(anio, mes + 1, 0).getDate();
  const offset = (primerDia.getDay() + 6) % 7;
  const celdas = Array.from({ length: offset }, () => null);
  for (let dia = 1; dia <= cantidadDias; dia++) {
    celdas.push(dia);
  }
  return { nombre: NOMBRES_MES[mes], celdas };
}

export const NOMBRES_DIA_SEMANA = DIAS_SEMANA;

export function eventosDelDia(eventos, anio, mes, dia) {
  const clave = `${anio}-${String(mes + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
  return eventos.filter((e) => e.fecha === clave);
}

export function esFeriado(feriados, anio, mes, dia) {
  const clave = `${anio}-${String(mes + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
  return feriados?.some((f) => f.date === clave);
}

export function localIsoDate(date = new Date()) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
