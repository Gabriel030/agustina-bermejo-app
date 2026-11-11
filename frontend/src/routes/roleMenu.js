import { BadgeDollarSign, BellRing, BookMarked, BookOpen, CalendarCheck, CalendarClock, CalendarDays, CalendarRange, CheckCircle2, ClipboardCheck, ClipboardList, CreditCard, FileCheck2, FileText, GraduationCap, Home, LayoutGrid, Receipt, Settings, ShieldCheck, Tag, UserCog, UserPlus, Users, BarChart2, Puzzle, SlidersHorizontal, Wallet, ScrollText } from 'lucide-react';

const inicio = { slug: 'inicio', label: 'Inicio', icon: Home };
const alumnos = { slug: 'alumnos', label: 'Alumnos', icon: Users };
const personal = { slug: 'personal', label: 'Personal', icon: UserCog };
const solicitudes = { slug: 'solicitudes-de-asistencia', label: 'Solicitudes de asistencia', icon: CalendarCheck };
const periodos = { slug: 'periodos', label: 'Períodos', icon: CalendarRange };
const academicManagement = { slug: 'gestion-academica', label: 'Gestión académica', icon: BookOpen };
const billingItems = [
  { slug: 'facturacion', label: 'Facturación', icon: Receipt },
  { slug: 'descuentos', label: 'Descuentos', icon: Tag },
  { slug: 'precios', label: 'Precios', icon: BadgeDollarSign },
  { slug: 'recordatorios-de-pago', label: 'Recordatorios de pago', icon: BellRing },
  { slug: 'corridas-de-facturacion', label: 'Corridas de facturación', icon: Receipt, hidden: true },
  { slug: 'emision-mensual', label: 'Emisión mensual', icon: CalendarClock },
];
const [invoicing, discounts, prices, reminders, runs, monthlyRun] = billingItems;
const paymentMethods = { slug: 'medios-de-pago', label: 'Medios de pago', icon: Wallet };
const annualTerms = { slug: 'terminos-del-pago-anual', label: 'Términos del pago anual', icon: ScrollText };
const financial ={ slug: 'financiero', label: 'Financiero', icon: BarChart2 };
const certificates = { slug: 'constancias', label: 'Constancias', icon: FileText };
const enrollmentApplications = { slug: 'solicitudes-de-ingreso', label: 'Solicitudes de ingreso', icon: UserPlus };
const graduationVerification = { slug: 'egresos-por-verificar', label: 'Egresos por verificar', icon: ShieldCheck };
const academicOffering = { slug: 'oferta-academica', label: 'Oferta académica', icon: BookMarked };
const approvals = { slug: 'aprobaciones', label: 'Aprobaciones', icon: CheckCircle2 };

export const ROLE_MENU = {
  alumno: [
    inicio,
    { slug: 'mis-cursos', label: 'Mis cursos', icon: BookOpen },
    { slug: 'mis-carreras', label: 'Mis carreras', icon: GraduationCap },
    { slug: 'pagos', label: 'Pagos', icon: CreditCard },
    { to: '/catalogo', label: 'Catálogo', icon: LayoutGrid },
    { slug: 'opciones', label: 'Opciones', icon: Settings },
  ],
  docente: [
    inicio,
    { slug: 'mis-comisiones', label: 'Mis comisiones', icon: ClipboardList },
    { slug: 'tomar-asistencia', label: 'Tomar asistencia', icon: CalendarCheck },
    { slug: 'mesas-de-examen', label: 'Mesas de examen', icon: FileCheck2 },
  ],
  preceptor: [
    inicio,
    alumnos,
    enrollmentApplications,
    academicOffering,
    { slug: 'mesas-de-examen', label: 'Mesas de examen', icon: FileCheck2 },
    solicitudes,
    periodos,
  ],
  administrativo: [
    inicio,
    alumnos,
    personal,
    enrollmentApplications,
    { slug: 'mesas-de-examen', label: 'Mesas de examen', icon: FileCheck2 },
    invoicing,
    discounts,
    prices,
    reminders,
    paymentMethods,
    runs,
    monthlyRun,
    approvals,
    graduationVerification,
    financial,
    certificates,
    solicitudes,
    academicManagement,
    periodos,
    { slug: 'feriados', label: 'Feriados', icon: CalendarDays },
  ],
  director: [
    inicio,
    alumnos,
    academicOffering,
    personal,
    approvals,
    academicManagement,
    { slug: 'esquemas-evaluacion', label: 'Esquemas de evaluación', icon: ClipboardCheck },
    { slug: 'mesas-de-examen', label: 'Mesas de examen', icon: FileCheck2 },
    { slug: 'ajustes-del-instituto', label: 'Ajustes del instituto', icon: SlidersHorizontal },
    invoicing,
    discounts,
    prices,
    reminders,
    paymentMethods,
    annualTerms,
    runs,
    monthlyRun,
    financial,
    certificates,
    periodos,
  ],
  platform_admin: [{ ...inicio, hidden: true }, { slug: 'modulos', label: 'Módulos', icon: Puzzle }, { slug: 'primer-director', label: 'Primer director', icon: UserPlus }],
};

export function pagesFor(role) {
  return ROLE_MENU[role].filter((entry) => entry.slug).map((entry) => entry.slug);
}

export function menuItemsFor(role) {
  return ROLE_MENU[role]
    .filter((entry) => !entry.hidden)
    .map(({ slug, to, label, icon }) => ({ to: to ?? `/${role}/${slug}`, label, icon }));
}

const MENU_GROUPS = {
  director: [
    { id: 'main', label: null, slugs: ['inicio', 'aprobaciones'] },
    { id: 'people', label: 'Personas', slugs: ['alumnos', 'personal'] },
    {
      id: 'academic',
      label: 'Académico',
      slugs: ['oferta-academica', 'gestion-academica', 'esquemas-evaluacion', 'mesas-de-examen', 'periodos', 'constancias'],
    },
    {
      id: 'billing',
      label: 'Facturación',
      slugs: [
        'facturacion',
        'descuentos',
        'precios',
        'recordatorios-de-pago',
        'medios-de-pago',
        'terminos-del-pago-anual',
        'emision-mensual',
        'financiero',
      ],
    },
    { id: 'institution', label: 'Institución', slugs: ['ajustes-del-instituto'] },
  ],
};

export function menuGroupSlugs(role) {
  return (MENU_GROUPS[role] ?? []).flatMap((group) => group.slugs);
}

export function groupMenuItems(role, items) {
  const definitions = MENU_GROUPS[role];
  if (!definitions) return null;
  const prefix = `/${role}/`;
  const slugOf = (item) => (item.to.startsWith(prefix) ? item.to.slice(prefix.length) : null);
  const known = new Set(menuGroupSlugs(role));
  return definitions
    .map((group, index) => {
      const own = items.filter((item) => group.slugs.includes(slugOf(item)));
      const unmapped = index === 0 ? items.filter((item) => !known.has(slugOf(item))) : [];
      return { id: group.id, label: group.label, items: [...own, ...unmapped] };
    })
    .filter((group) => group.items.length > 0);
}

export function pageLabels() {
  return Object.fromEntries(
    Object.values(ROLE_MENU).flat().filter((entry) => entry.slug).map((entry) => [entry.slug, entry.label]),
  );
}
