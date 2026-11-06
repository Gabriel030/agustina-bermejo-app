import { BellRing, BookMarked, BookOpen, CalendarCheck, CalendarClock, CalendarDays, CalendarRange, CheckCircle2, ClipboardCheck, ClipboardList, CreditCard, FileCheck2, FileText, GraduationCap, Home, LayoutGrid, Receipt, Settings, ShieldCheck, Tag, UserCog, UserPlus, Users, BarChart2, Puzzle, SlidersHorizontal } from 'lucide-react';

const inicio = { slug: 'inicio', label: 'Inicio', icon: Home };
const alumnos = { slug: 'alumnos', label: 'Alumnos', icon: Users };
const personal = { slug: 'personal', label: 'Personal', icon: UserCog };
const solicitudes = { slug: 'solicitudes-de-asistencia', label: 'Solicitudes de asistencia', icon: CalendarCheck };
const periodos = { slug: 'periodos', label: 'Períodos', icon: CalendarRange };
const academicManagement = { slug: 'gestion-academica', label: 'Gestión académica', icon: BookOpen };
const billingItems = [
  { slug: 'facturacion', label: 'Facturación', icon: Receipt },
  { slug: 'descuentos', label: 'Descuentos', icon: Tag },
  { slug: 'recordatorios-de-pago', label: 'Recordatorios de pago', icon: BellRing },
  { slug: 'corridas-de-facturacion', label: 'Corridas de facturación', icon: Receipt, hidden: true },
  { slug: 'emision-mensual', label: 'Emisión mensual', icon: CalendarClock },
];
const [invoicing, discounts, reminders, runs, monthlyRun] = billingItems;
const financial = { slug: 'financiero', label: 'Financiero', icon: BarChart2 };
const certificates = { slug: 'constancias', label: 'Constancias', icon: FileText };
const graduationVerification = { slug: 'egresos-por-verificar', label: 'Egresos por verificar', icon: ShieldCheck };
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
  ],
  preceptor: [
    inicio,
    alumnos,
    { slug: 'oferta-academica', label: 'Oferta académica', icon: BookMarked },
    { slug: 'mesas-de-examen', label: 'Mesas de examen', icon: FileCheck2 },
    solicitudes,
    periodos,
  ],
  administrativo: [
    inicio,
    alumnos,
    personal,
    invoicing,
    discounts,
    reminders,
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
    personal,
    approvals,
    academicManagement,
    { slug: 'esquemas-evaluacion', label: 'Esquemas de evaluación', icon: ClipboardCheck },
    { slug: 'ajustes-del-instituto', label: 'Ajustes del instituto', icon: SlidersHorizontal },
    invoicing,
    discounts,
    reminders,
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

export function pageLabels() {
  return Object.fromEntries(
    Object.values(ROLE_MENU).flat().filter((entry) => entry.slug).map((entry) => [entry.slug, entry.label]),
  );
}
