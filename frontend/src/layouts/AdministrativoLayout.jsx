import { Home, Users, Receipt, FileText, CalendarCheck, UserCog, BookOpen, BarChart2, CheckCircle2 } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [
  { to: '/administrativo/inicio', label: 'Inicio', icon: Home },
  { to: '/administrativo/alumnos', label: 'Alumnos', icon: Users },
  { to: '/administrativo/personal', label: 'Personal', icon: UserCog },
  { to: '/administrativo/facturacion', label: 'Facturación', icon: Receipt },
  { to: '/administrativo/aprobaciones', label: 'Aprobaciones', icon: CheckCircle2 },
  { to: '/administrativo/financiero', label: 'Financiero', icon: BarChart2 },
  { to: '/administrativo/constancias', label: 'Constancias', icon: FileText },
  { to: '/administrativo/solicitudes-de-asistencia', label: 'Solicitudes de asistencia', icon: CalendarCheck },
  { to: '/administrativo/gestion-academica', label: 'Banco de materias', icon: BookOpen },
];

export function AdministrativoLayout() {
  return <RoleShell titulo="Administrativo" items={ITEMS} />;
}
