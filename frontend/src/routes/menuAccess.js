import { roleRoutesFor } from '@/routes/featureRoutes';
import { isBillingPath } from '@/routes/moduleGating';

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

function slugOf(role, item) {
  const prefix = `/${role}/`;
  return item.to.startsWith(prefix) ? item.to.slice(prefix.length) : null;
}

export function visibleMenuItems(role, items, { billingEnabled = true } = {}) {
  return items.filter((item) => {
    const slug = slugOf(role, item);
    if (slug === null) return true;
    if (!hasRealScreen(role, slug)) return false;
    return billingEnabled || !isBillingPath(role, slug);
  });
}

export function menuNeedsModules(role, items) {
  return items.some((item) => {
    const slug = slugOf(role, item);
    return slug !== null && isBillingPath(role, slug);
  });
}
