import { roleRoutesFor } from '@/routes/featureRoutes';

const PAGE_LABELS = {
  inicio: 'Inicio',
  'mis-cursos': 'Mis cursos',
  'mis-carreras': 'Mis carreras',
  pagos: 'Pagos',
  opciones: 'Opciones',
  'mis-comisiones': 'Mis comisiones',
  'tomar-asistencia': 'Tomar asistencia',
  alumnos: 'Alumnos',
  personal: 'Personal',
  'oferta-academica': 'Oferta académica',
  'mesas-de-examen': 'Mesas de examen',
  'solicitudes-de-asistencia': 'Solicitudes de asistencia',
  periodos: 'Períodos',
  facturacion: 'Facturación',
  descuentos: 'Descuentos',
  'recordatorios-de-pago': 'Recordatorios de pago',
  'corridas-de-facturacion': 'Corridas de facturación',
  'emision-mensual': 'Emisión mensual',
  aprobaciones: 'Aprobaciones',
  financiero: 'Financiero',
  constancias: 'Constancias',
  'gestion-academica': 'Banco de materias',
  feriados: 'Feriados',
  'esquemas-evaluacion': 'Esquemas de evaluación',
  'primer-director': 'Primer director',
};

export function pageLabel(slug) {
  if (PAGE_LABELS[slug]) return PAGE_LABELS[slug];
  const words = String(slug).replace(/-/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function hasRealScreen(role, slug) {
  return roleRoutesFor(role).some((route) => route.path === slug);
}

export function firstRealPage(role, pages) {
  return pages.find((slug) => hasRealScreen(role, slug)) ?? null;
}

export function visibleMenuItems(role, items) {
  const prefix = `/${role}/`;
  return items.filter((item) => !item.to.startsWith(prefix) || hasRealScreen(role, item.to.slice(prefix.length)));
}
