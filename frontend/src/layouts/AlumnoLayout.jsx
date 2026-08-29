import { Home, BookOpen, GraduationCap, LayoutGrid, Settings } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

// Notas, Asistencia y Mesas de examen dejaron de ser ítems de nav de primer
// nivel (Épica 2 Fase 2, pedido explícito del usuario: "menos botones en el
// menu, y queda mas acoplado a la materia") — ahora son pestañas dentro de
// Mis carreras (ver AlumnoMisCarreras.jsx), siempre en el contexto de la
// carrera elegida, en vez de pantallas sueltas sin esa referencia.
const ITEMS = [
  { to: '/alumno/inicio', label: 'Inicio', icon: Home },
  { to: '/alumno/mis-cursos', label: 'Mis cursos', icon: BookOpen },
  { to: '/alumno/mis-carreras', label: 'Mis carreras', icon: GraduationCap },
  // Ruta compartida fuera de RUTAS_POR_ROL (Épica 7 Fase 2): un Alumno ya
  // matriculado también puede sumarse a un curso suelto del catálogo.
  { to: '/catalogo', label: 'Catálogo', icon: LayoutGrid },
  // "Opciones" se separa visualmente del resto (Sidebar/MobileMenu lo agrupan
  // junto a "Cerrar sesión" por label) — pedido explícito del usuario.
  { to: '/alumno/opciones', label: 'Opciones', icon: Settings },
];

export function AlumnoLayout() {
  return <RoleShell titulo="Alumno" items={ITEMS} />;
}
